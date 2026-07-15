package com.wrongbook.app.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.FollowUpChatIds
import com.wrongbook.app.model.ImageRef
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.QuestionAnalysis
import com.wrongbook.app.model.ReviewEvent
import com.wrongbook.app.model.ReviewStatus
import com.wrongbook.app.model.SubjectCatalog
import com.wrongbook.app.model.SyncStatus
import com.wrongbook.app.review.ReviewService
import com.wrongbook.app.utils.ImageFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

data class SyncResult(
    val records: List<Question>,
    val protocolVersion: Int,
    val generation: String?,
    val uploadedCount: Int,
    val downloadedCount: Int
)

data class SyncProgress(
    val message: String,
    val completed: Int = 0,
    val total: Int = 0
)

class SyncException(
    message: String,
    val requiresServerUpgrade: Boolean = false,
    cause: Throwable? = null
) : IOException(message, cause)

class QuestionSyncService(
    private val context: Context,
    private val apiUrl: String,
    private val token: String,
    private val deviceId: String
) {
    private val gson = Gson()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun sync(
        localQuestions: List<Question>,
        onProgress: (SyncProgress) -> Unit = {}
    ): SyncResult = withContext(Dispatchers.IO) {
        if (apiUrl.isBlank()) throw SyncException("SYNC_API_URL 未配置")
        if (token.isBlank()) throw SyncException("SYNC_TOKEN 未配置")

        try {
            val capabilities = probeCapabilities()
            if (capabilities != null) {
                syncV2(localQuestions, capabilities, onProgress)
            } else {
                syncLegacy(localQuestions, onProgress)
            }
        } catch (error: HttpFailure) {
            throw friendlyHttpError(error)
        }
    }

    suspend fun cleanOrphanedImages(allQuestions: List<Question>): Int = withContext(Dispatchers.IO) {
        ImageFileStore.deleteOrphanedFiles(
            context = context,
            retainedRefs = allQuestions.flatMap { it.imageRefs + it.noteImageRefs }
        )
    }

    private fun probeCapabilities(): SyncCapabilities? {
        val request = authorizedRequest().method("OPTIONS", null).build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    throw HttpFailure(response.code, response.body?.string().orEmpty())
                }
                if (!response.isSuccessful) return@use null
                val root = parseRoot(response.body?.string().orEmpty(), "能力探测")
                val versions = intList(root.getAsJsonArray("protocolVersions"))
                val isV2 = int(root, "protocolVersion", 0) == PROTOCOL_V2 || PROTOCOL_V2 in versions
                val capabilities = stringList(root.getAsJsonArray("capabilities"))
                if (!isV2 || CAPABILITY_PUSH_PULL !in capabilities) return@use null
                SyncCapabilities(
                    maxRequestBytes = long(root, "maxRequestBytes", DEFAULT_SERVER_LIMIT),
                    maxDecompressedBytes = long(root, "maxDecompressedBytes", DEFAULT_SERVER_LIMIT),
                    defaultPageLimit = int(root, "defaultPageLimit", DEFAULT_PAGE_LIMIT)
                        .coerceAtLeast(1),
                    maxPageLimit = int(root, "maxPageLimit", DEFAULT_PAGE_LIMIT)
                        .coerceAtLeast(1)
                )
            }
        } catch (error: HttpFailure) {
            throw error
        } catch (error: SyncException) {
            throw error
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun syncV2(
        localQuestions: List<Question>,
        capabilities: SyncCapabilities,
        onProgress: (SyncProgress) -> Unit
    ): SyncResult {
        onProgress(SyncProgress("正在检查本地图片…"))
        val encodedRecords = localQuestions.map { toSyncJson(it, allowUnavailableImages = true) }
        val batches = partitionRecords(
            encodedRecords,
            capabilities.maxRequestBytes,
            capabilities.maxDecompressedBytes
        )
        var uploaded = 0
        var generation: String? = null

        batches.forEachIndexed { index, batch ->
            onProgress(SyncProgress("正在上传第 ${index + 1}/${batches.size} 批…", index, batches.size))
            val requestJson = JsonObject().apply {
                addProperty("protocolVersion", PROTOCOL_V2)
                addProperty("mode", "push")
                addProperty("deviceId", normalizedDeviceId())
                add("records", jsonArray(batch))
            }
            val requestText = gson.toJson(requestJson)
            val compressedBytes = gzip(requestText.toByteArray(Charsets.UTF_8)).size.toLong()
            if (compressedBytes > capabilities.maxRequestBytes) {
                val titles = batch.mapNotNull { nullableString(it, "title") }.take(3).joinToString("、")
                throw SyncException(
                    "题目批次“$titles”压缩后仍有 ${formatBytes(compressedBytes)}，超过 VPS 上限 " +
                        "${formatBytes(capabilities.maxRequestBytes)}；请压缩这些题目的图片"
                )
            }
            val response = postJson(requestJson, gzip = true)
            requireV2Mode(response, "push")
            val accepted = int(response, "acceptedCount", -1)
            val ignored = int(response, "ignoredCount", -1)
            if (accepted != batch.size || ignored != 0) {
                throw SyncException(
                    "服务端拒绝了部分同步数据（本批 ${batch.size} 条，接收 $accepted 条，忽略 $ignored 条），本地数据未替换"
                )
            }
            uploaded += accepted
            generation = opaqueString(response, "generation") ?: generation
        }

        var restartCount = 0
        while (true) {
            try {
                val pulled = pullCompleteSnapshot(capabilities, onProgress)
                generation = pulled.generation ?: generation
                val localById = localQuestions.associateBy(Question::id)
                val materialized = pulled.records.mapIndexed { index, recordJson ->
                    val idHint = nullableString(recordJson, "id") ?: "第 ${index + 1} 条"
                    runCatching {
                        val parsed = fromSyncJson(recordJson)
                        materializeRemoteImagesToLocalFiles(parsed, localById[parsed.id])
                    }.getOrElse { error ->
                        throw SyncException("服务端记录 $idHint 无法读取：${error.message}", cause = error)
                    }
                }
                ensureUniqueQuestionIds(materialized)
                return SyncResult(
                    records = materialized,
                    protocolVersion = PROTOCOL_V2,
                    generation = generation,
                    uploadedCount = uploaded,
                    downloadedCount = materialized.size
                )
            } catch (error: HttpFailure) {
                if (error.code == 409 && error.body.contains("SNAPSHOT_STALE") && restartCount < 2) {
                    restartCount += 1
                    onProgress(SyncProgress("远端数据刚刚变化，正在重新获取完整快照…"))
                    continue
                }
                throw friendlyHttpError(error)
            }
        }
    }

    private fun pullCompleteSnapshot(
        capabilities: SyncCapabilities,
        onProgress: (SyncProgress) -> Unit
    ): PulledSnapshot {
        val records = mutableListOf<JsonObject>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        var generation: String? = null
        var page = 0
        val pageLimit = minOf(
            capabilities.defaultPageLimit,
            capabilities.maxPageLimit,
            CLIENT_PAGE_LIMIT
        ).coerceAtLeast(1)

        while (true) {
            page += 1
            if (page > MAX_PULL_PAGES) throw SyncException("远端分页数量异常，已停止同步")
            onProgress(SyncProgress("正在下载远端快照（第 $page 页）…", page - 1, 0))
            val requestJson = JsonObject().apply {
                addProperty("protocolVersion", PROTOCOL_V2)
                addProperty("mode", "pull")
                addProperty("deviceId", normalizedDeviceId())
                addProperty("limit", pageLimit)
                cursor?.let { addProperty("cursor", it) }
            }
            val response = postJson(requestJson, gzip = true)
            requireV2Mode(response, "pull")
            val pageGeneration = opaqueString(response, "generation")
            if (generation != null && pageGeneration != null && generation != pageGeneration) {
                throw HttpFailure(409, "SNAPSHOT_STALE")
            }
            generation = pageGeneration ?: generation
            val pageRecords = requiredArray(response, "records")
            pageRecords.forEach { element ->
                if (!element.isJsonObject) throw SyncException("服务端快照包含非对象记录")
                records += element.asJsonObject
            }
            val complete = bool(response, "snapshotComplete")
            val nextCursor = opaqueString(response, "nextCursor")
            if (complete) {
                if (nextCursor != null) throw SyncException("服务端完整快照标记与游标矛盾")
                return PulledSnapshot(records, generation)
            }
            if (nextCursor.isNullOrBlank() || !seenCursors.add(nextCursor)) {
                throw SyncException("服务端分页游标无效，未应用不完整快照")
            }
            cursor = nextCursor
        }
    }

    private suspend fun syncLegacy(
        localQuestions: List<Question>,
        onProgress: (SyncProgress) -> Unit
    ): SyncResult {
        onProgress(SyncProgress("当前 VPS 使用兼容同步模式，正在检查本地图片…"))
        val records = localQuestions.map { toSyncJson(it, allowUnavailableImages = false) }
        val requestJson = JsonObject().apply {
            addProperty("deviceId", normalizedDeviceId())
            add("records", jsonArray(records))
        }
        val json = gson.toJson(requestJson)
        val sizeEstimate = LegacyPayloadSizeEstimator.estimate(json, records)
        if (!LegacyPayloadSizeEstimator.isSafe(
                estimate = sizeEstimate,
                maxRequestBytes = LEGACY_SAFE_REQUEST_BYTES,
                maxExpandedBytes = LEGACY_SAFE_EXPANDED_BYTES
            )
        ) {
            throw SyncException(
                "当前 VPS 同步服务仍是 v1：上传约 ${formatBytes(sizeEstimate.requestBytes)}，" +
                    "旧协议复制图片后的回包/存储预计约 ${formatBytes(sizeEstimate.expandedBytes)}。" +
                    "为避免 50MB/120 秒连接重置已停止上传，请先升级 VPS 同步服务。",
                requiresServerUpgrade = true
            )
        }
        onProgress(SyncProgress("正在通过 VPS v1 安全兼容同步…"))
        val response = try {
            postJsonString(json, gzip = false)
        } catch (error: SocketTimeoutException) {
            throw SyncException("同步超时；当前 VPS 仍是 v1，图片较多时需要升级服务端", true, error)
        } catch (error: SocketException) {
            throw SyncException("连接被服务端中断；当前 VPS 仍是 v1，可能触发 50MB/120 秒限制", true, error)
        }
        val remoteArray = requiredArray(response, "records")
        val localById = localQuestions.associateBy(Question::id)
        val remote = remoteArray.mapIndexed { index, element ->
            if (!element.isJsonObject) throw SyncException("服务端第 ${index + 1} 条记录不是对象")
            val record = element.asJsonObject
            val idHint = nullableString(record, "id") ?: "第 ${index + 1} 条"
            runCatching {
                val parsed = fromSyncJson(record)
                materializeRemoteImagesToLocalFiles(parsed, localById[parsed.id])
            }.getOrElse { error ->
                throw SyncException("服务端记录 $idHint 无法读取：${error.message}", cause = error)
            }
        }
        ensureUniqueQuestionIds(remote)
        return SyncResult(
            records = remote,
            protocolVersion = 1,
            generation = null,
            uploadedCount = localQuestions.size,
            downloadedCount = remote.size
        )
    }

    private suspend fun toSyncJson(
        question: Question,
        allowUnavailableImages: Boolean
    ): JsonObject {
        val imageGroup = materializeImageRefs(question.imageRefs, "question")
        val noteImageGroup = materializeImageRefs(question.noteImageRefs, "note")
        if (!allowUnavailableImages && (!imageGroup.complete || !noteImageGroup.complete)) {
            val badIds = (imageGroup.unavailableIds + noteImageGroup.unavailableIds).joinToString()
            throw SyncException(
                "题目“${question.title}”的本地图片无法读取或校验失败（$badIds），已取消同步以避免覆盖 VPS 上的正常图片"
            )
        }

        return JsonObject().apply {
            addProperty("id", question.id)
            addProperty("title", question.title)
            addProperty("questionText", question.questionText)
            addProperty("userAnswer", question.userAnswer)
            addProperty("correctAnswer", question.correctAnswer)
            add("imageRefs", imageGroup.refs)
            addProperty("imageRefsComplete", imageGroup.complete)
            question.imageRefsUpdatedAt?.let { addProperty("imageRefsUpdatedAt", iso(it)) }
            addProperty("category", SubjectCatalog.normalize(question.category))
            addProperty("grade", question.grade)
            addProperty("questionType", question.questionType)
            addProperty("source", question.source)
            addProperty("createdAt", iso(question.createdAt))
            addProperty("updatedAt", iso(question.updatedAt))
            addProperty("contentUpdatedAt", iso(question.contentUpdatedAt))
            addProperty("deleted", question.deleted)
            question.deletedAt?.let { addProperty("deletedAt", iso(it)) }
            question.restoredAt?.let { addProperty("restoredAt", iso(it)) }
            addProperty("syncStatus", question.syncStatus.name.lowercase())
            addProperty("notes", question.notes)
            question.notesUpdatedAt?.let { addProperty("notesUpdatedAt", iso(it)) }
            addProperty("errorCause", question.errorCause)
            add("tags", stringArray(question.tags))
            addProperty("masteryLevel", question.masteryLevel)
            addProperty("reviewCount", question.reviewCount)
            question.lastReviewedAt?.let { addProperty("lastReviewedAt", iso(it)) }
            question.nextReviewAt?.let { addProperty("nextReviewAt", iso(it)) }
            addProperty("reviewStatus", question.reviewStatus.name.lowercase())
            question.reviewUpdatedAt?.let { addProperty("reviewUpdatedAt", iso(it)) }
            add("reviewEvents", reviewEventArray(question))
            add("noteImageRefs", noteImageGroup.refs)
            addProperty("noteImageRefsComplete", noteImageGroup.complete)
            question.noteImagesUpdatedAt?.let {
                addProperty("noteImageRefsUpdatedAt", iso(it))
                addProperty("noteImagesUpdatedAt", iso(it))
            }
            question.analysis?.let { add("analysis", analysisJson(it)) }
            question.analysisContentUpdatedAt?.let { addProperty("analysisContentUpdatedAt", iso(it)) }
            question.detailedExplanation?.let { addProperty("detailedExplanation", it) }
            question.detailedExplanationUpdatedAt?.let { addProperty("detailedExplanationUpdatedAt", iso(it)) }
            question.explanationContentUpdatedAt?.let { addProperty("explanationContentUpdatedAt", iso(it)) }
            question.hint?.let { addProperty("hint", it) }
            question.hintUpdatedAt?.let { addProperty("hintUpdatedAt", iso(it)) }
            question.hintContentUpdatedAt?.let { addProperty("hintContentUpdatedAt", iso(it)) }
            add("followUpChats", followUpArray(question.followUpChats))
            question.followUpContentUpdatedAt?.let { addProperty("followUpContentUpdatedAt", iso(it)) }
            LegacyImageCompatibility.addExplicitClearMarkers(this)
        }
    }

    private suspend fun materializeImageRefs(
        imageRefs: List<ImageRef>,
        kind: String
    ): EncodedImageGroup {
        val encodedById = linkedMapOf<String, JsonObject>()
        val unavailable = mutableListOf<String>()
        imageRefs.forEach { ref ->
            val encoded = runCatching {
                val dataUrl = ImageFileStore.readImageDataUrl(context, ref)
                val actualHash = ImageFileStore.contentHashForDataUrl(dataUrl, ref.mimeType)
                val expectedHash = ImageFileStore.normalizeContentHash(ref.contentHash)
                require(expectedHash == null || expectedHash == actualHash) {
                    "图片 ${ref.id} 的 SHA-256 与记录不一致"
                }
                JsonObject().apply {
                    addProperty("id", canonicalImageId(actualHash))
                    addProperty("storage", "inline")
                    addProperty("kind", kind)
                    addProperty("createdAt", iso(ref.createdAt))
                    ref.mimeType?.let { addProperty("mimeType", it) }
                    addProperty("contentHash", actualHash)
                    addProperty("dataUrl", dataUrl)
                }
            }.getOrElse {
                unavailable += ref.id
                JsonObject().apply {
                    addProperty("id", ref.id)
                    addProperty("kind", kind)
                    addProperty("createdAt", iso(ref.createdAt))
                    ref.mimeType?.let { addProperty("mimeType", it) }
                    ImageFileStore.normalizeContentHash(ref.contentHash)?.let {
                        addProperty("contentHash", it)
                    }
                    addProperty("status", "unavailable")
                }
            }
            encodedById[string(encoded, "id")] = encoded
        }
        val array = JsonArray().apply { encodedById.values.forEach(::add) }
        return EncodedImageGroup(array, unavailable.isEmpty(), unavailable)
    }

    private suspend fun fromSyncJson(json: JsonObject): Question {
        val id = string(json, "id")
        require(id.isNotBlank()) { "缺少 id" }
        val now = System.currentTimeMillis()
        val createdAt = millis(json, "createdAt", now)
        val updatedAt = millis(json, "updatedAt", createdAt)
        val contentUpdatedAt = millis(json, "contentUpdatedAt", updatedAt)
        val legacyFollowUpFallbackAt = nullableMillis(json, "createdAt")
            ?: nullableMillis(json, "updatedAt")
            ?: 0L
        val parsedAnalysis = optionalObject(json, "analysis")?.let(::parseAnalysis)
        val parsedDetailedExplanation = nullableString(json, "detailedExplanation")
        val parsedDetailedExplanationUpdatedAt = nullableMillis(json, "detailedExplanationUpdatedAt")
        val parsedHint = nullableString(json, "hint")
        val parsedHintUpdatedAt = nullableMillis(json, "hintUpdatedAt")
        val parsedFollowUps = followUps(
            questionId = id,
            fallbackCreatedAt = legacyFollowUpFallbackAt,
            array = optionalArray(json, "followUpChats")
        )
        val parsedImageRefs = imageRefs(
            array = optionalArray(json, "imageRefs"),
            kind = "question",
            legacySources = listOfNotNull(nullableString(json, "image"))
        )
        val parsedNoteImageRefs = imageRefs(
            array = optionalArray(json, "noteImageRefs"),
            kind = "note",
            legacySources = stringList(optionalArray(json, "noteImages"))
        )
        val parsedNotes = string(json, "notes")
        val incomingReviewCount = int(json, "reviewCount", 0).coerceAtLeast(0)
        val lastReviewedAt = nullableMillis(json, "lastReviewedAt")
        val reviewUpdatedAt = nullableMillis(json, "reviewUpdatedAt")
            ?: if (incomingReviewCount > 0 || lastReviewedAt != null) lastReviewedAt ?: updatedAt else null
        val parsedReviewEvents = reviewEvents(optionalArray(json, "reviewEvents")).ifEmpty {
            legacyReviewEvents(id, incomingReviewCount, lastReviewedAt ?: reviewUpdatedAt ?: updatedAt)
        }

        return Question(
            id = id,
            title = string(json, "title").ifBlank { "未命名错题" },
            category = SubjectCatalog.normalize(string(json, "category")),
            grade = string(json, "grade"),
            questionType = string(json, "questionType"),
            source = string(json, "source"),
            questionText = string(json, "questionText"),
            userAnswer = string(json, "userAnswer"),
            correctAnswer = string(json, "correctAnswer"),
            notes = parsedNotes,
            errorCause = string(json, "errorCause"),
            tags = stringList(optionalArray(json, "tags")),
            masteryLevel = int(json, "masteryLevel", 0),
            createdAt = createdAt,
            updatedAt = updatedAt,
            deleted = bool(json, "deleted"),
            deletedAt = nullableMillis(json, "deletedAt"),
            restoredAt = nullableMillis(json, "restoredAt"),
            syncStatus = SyncStatus.SYNCED,
            contentUpdatedAt = contentUpdatedAt,
            imageRefsUpdatedAt = nullableMillis(json, "imageRefsUpdatedAt")
                ?: parsedImageRefs.takeIf { it.isNotEmpty() }?.let { contentUpdatedAt },
            reviewCount = ReviewService.successfulReviewCount(parsedReviewEvents),
            lastReviewedAt = lastReviewedAt,
            nextReviewAt = nullableMillis(json, "nextReviewAt"),
            reviewStatus = reviewStatus(string(json, "reviewStatus")),
            notesUpdatedAt = nullableMillis(json, "notesUpdatedAt")
                ?: parsedNotes.takeIf { it.isNotBlank() }?.let { updatedAt },
            noteImagesUpdatedAt = nullableMillis(json, "noteImageRefsUpdatedAt")
                ?: nullableMillis(json, "noteImagesUpdatedAt")
                ?: parsedNoteImageRefs.takeIf { it.isNotEmpty() }?.let { updatedAt },
            reviewUpdatedAt = reviewUpdatedAt,
            reviewEvents = parsedReviewEvents,
            analysis = parsedAnalysis,
            analysisContentUpdatedAt = nullableMillis(json, "analysisContentUpdatedAt")
                ?: parsedAnalysis?.updatedAt
                ?: parsedAnalysis?.let { contentUpdatedAt },
            detailedExplanation = parsedDetailedExplanation,
            detailedExplanationUpdatedAt = parsedDetailedExplanationUpdatedAt,
            explanationContentUpdatedAt = nullableMillis(json, "explanationContentUpdatedAt")
                ?: parsedDetailedExplanationUpdatedAt
                ?: parsedDetailedExplanation?.let { contentUpdatedAt },
            hint = parsedHint,
            hintUpdatedAt = parsedHintUpdatedAt,
            hintContentUpdatedAt = nullableMillis(json, "hintContentUpdatedAt")
                ?: parsedHintUpdatedAt
                ?: parsedHint?.let { contentUpdatedAt },
            followUpChats = parsedFollowUps,
            followUpContentUpdatedAt = nullableMillis(json, "followUpContentUpdatedAt")
                ?: parsedFollowUps.lastOrNull()?.createdAt
                ?: parsedFollowUps.takeIf { it.isNotEmpty() }?.let { contentUpdatedAt },
            imageRefs = parsedImageRefs,
            noteImageRefs = parsedNoteImageRefs
        )
    }

    private suspend fun materializeRemoteImagesToLocalFiles(
        question: Question,
        localQuestion: Question?
    ): Question = question.copy(
        imageRefs = question.imageRefs.map { ref ->
            materializeRemoteImageRef(ref, "question", localQuestion?.imageRefs.orEmpty())
        },
        noteImageRefs = question.noteImageRefs.map { ref ->
            materializeRemoteImageRef(ref, "note", localQuestion?.noteImageRefs.orEmpty())
        }
    )

    private suspend fun materializeRemoteImageRef(
        ref: ImageRef,
        fallbackKind: String,
        localRefs: List<ImageRef>
    ): ImageRef {
        if (!ref.dataUrl.isNullOrBlank()) {
            val actualHash = ImageFileStore.contentHashForDataUrl(ref.dataUrl, ref.mimeType)
            val expectedHash = ImageFileStore.normalizeContentHash(ref.contentHash)
            require(expectedHash == null || expectedHash == actualHash) {
                "图片 ${ref.id} 的 SHA-256 与 dataUrl 不一致"
            }
            return ImageFileStore.importDataUrl(
                context = context,
                dataUrl = ref.dataUrl,
                kind = ref.kind.ifBlank { fallbackKind },
                createdAt = ref.createdAt,
                id = canonicalImageId(actualHash),
                mimeType = ref.mimeType,
                expectedContentHash = actualHash
            )
        }

        val expectedHash = ImageFileStore.normalizeContentHash(ref.contentHash)
        val local = localRefs.firstOrNull { candidate -> candidate.id == ref.id }
        val actualLocalHash = local?.let { candidate ->
            runCatching {
                val dataUrl = ImageFileStore.readImageDataUrl(context, candidate)
                ImageFileStore.contentHashForDataUrl(dataUrl, candidate.mimeType)
            }.getOrNull()
        }
        if (local != null && actualLocalHash != null &&
            (expectedHash == null || expectedHash == actualLocalHash)
        ) {
            return local.copy(
                kind = ref.kind.ifBlank { fallbackKind },
                createdAt = ref.createdAt,
                mimeType = ref.mimeType ?: local.mimeType,
                contentHash = expectedHash ?: actualLocalHash,
                status = null
            )
        }
        return ref.copy(
            storage = "file",
            dataUrl = null,
            uri = null,
            contentHash = expectedHash,
            status = "unavailable"
        )
    }

    private fun imageRefs(
        array: JsonArray?,
        kind: String,
        legacySources: List<String>
    ): List<ImageRef> {
        val refs = array?.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val item = element.asJsonObject
            val dataUrl = nullableString(item, "dataUrl")?.takeIf(String::isNotBlank)
            val uri = nullableString(item, "uri")?.takeIf(String::isNotBlank)
            val status = nullableString(item, "status")
            val declaredHash = ImageFileStore.normalizeContentHash(nullableString(item, "contentHash"))
            val actualHash = dataUrl?.let { ImageFileStore.contentHashForDataUrl(it, nullableString(item, "mimeType")) }
            require(declaredHash == null || actualHash == null || declaredHash == actualHash) {
                "图片 ${string(item, "id")} 的 contentHash 与 dataUrl 不一致"
            }
            val contentHash = actualHash ?: declaredHash
            val id = contentHash?.let(::canonicalImageId)
                ?: string(item, "id").ifBlank { UUID.randomUUID().toString() }
            if (dataUrl == null && uri == null && status != "unavailable") return@mapNotNull null
            ImageRef(
                id = id,
                storage = string(item, "storage").ifBlank { if (dataUrl != null) "inline" else "file" },
                kind = string(item, "kind").ifBlank { kind },
                createdAt = millis(item, "createdAt", System.currentTimeMillis()),
                mimeType = nullableString(item, "mimeType"),
                contentHash = contentHash,
                status = status,
                dataUrl = dataUrl,
                uri = uri
            )
        }.orEmpty().distinctBy(ImageRef::id)
        if (refs.isNotEmpty() || array != null) return refs

        return legacySources.mapNotNull { source ->
            when {
                source.startsWith("data:image/") -> {
                    val hash = ImageFileStore.contentHashForDataUrl(source)
                    ImageRef(
                        id = canonicalImageId(hash),
                        storage = "inline",
                        kind = kind,
                        dataUrl = source,
                        contentHash = hash
                    )
                }
                source.isNotBlank() -> ImageRef(
                    id = UUID.randomUUID().toString(),
                    storage = "file",
                    kind = kind,
                    uri = source
                )
                else -> null
            }
        }
    }

    private fun reviewEventArray(question: Question): JsonArray {
        val events = question.reviewEvents.ifEmpty {
            legacyReviewEvents(
                question.id,
                question.reviewCount,
                question.lastReviewedAt ?: question.reviewUpdatedAt ?: question.updatedAt
            )
        }
        val array = JsonArray()
        events.distinctBy(ReviewEvent::id).forEach { event ->
            array.add(JsonObject().apply {
                addProperty("id", event.id)
                addProperty("kind", event.kind)
                addProperty("reviewedAt", iso(event.reviewedAt))
                event.quality?.let { addProperty("quality", it.coerceIn(0, 3)) }
                event.targetEventId?.let { addProperty("targetEventId", it) }
                event.deviceId?.let { addProperty("deviceId", it) }
            })
        }
        return array
    }

    private fun reviewEvents(array: JsonArray?): List<ReviewEvent> =
        array?.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val item = element.asJsonObject
            val id = string(item, "id")
            val kind = string(item, "kind")
            val reviewedAt = millis(item, "reviewedAt", 0L)
            if (id.isBlank() || reviewedAt <= 0L ||
                (kind != ReviewEvent.KIND_REVIEW && kind != ReviewEvent.KIND_REVERT)
            ) return@mapNotNull null
            ReviewEvent(
                id = id,
                kind = kind,
                reviewedAt = reviewedAt,
                quality = nullableInt(item, "quality")?.coerceIn(0, 3),
                targetEventId = nullableString(item, "targetEventId"),
                deviceId = nullableString(item, "deviceId")
            )
        }.orEmpty().distinctBy(ReviewEvent::id)

    private fun legacyReviewEvents(
        questionId: String,
        count: Int,
        reviewedAt: Long
    ): List<ReviewEvent> = (1..count.coerceAtLeast(0)).map { index ->
        ReviewEvent(
            id = "legacy-review:$questionId:$index",
            kind = ReviewEvent.KIND_REVIEW,
            reviewedAt = reviewedAt,
            quality = 2
        )
    }

    private fun parseAnalysis(json: JsonObject): QuestionAnalysis {
        val cautions = stringList(optionalArray(json, "cautions"))
        val notices = stringList(optionalArray(json, "notices")).ifEmpty { cautions }
        val solutionMethods = stringList(optionalArray(json, "solutionMethods"))
            .ifEmpty { stringList(optionalArray(json, "solution_methods")) }
        val knowledgePoints = stringList(optionalArray(json, "knowledgePoints"))
            .ifEmpty { stringList(optionalArray(json, "knowledge_points")) }
        val commonMistakes = stringList(optionalArray(json, "commonMistakes"))
            .ifEmpty { stringList(optionalArray(json, "common_mistakes")) }
        return QuestionAnalysis(
            difficulty = string(json, "difficulty").ifBlank { "中等" },
            difficultyScore = int(json, "difficultyScore", int(json, "difficulty_score", 3)).coerceIn(1, 5),
            knowledgePoints = knowledgePoints,
            commonMistakes = commonMistakes,
            solutionMethods = solutionMethods,
            cautions = cautions.ifEmpty { notices },
            notices = notices,
            studyAdvice = nullableString(json, "studyAdvice")
                ?: buildStudyAdvice(knowledgePoints, commonMistakes, solutionMethods),
            updatedAt = millis(json, "updatedAt", System.currentTimeMillis()),
            source = string(json, "source").ifBlank { "ai" }
        )
    }

    private fun analysisJson(analysis: QuestionAnalysis): JsonObject = JsonObject().apply {
        addProperty("difficulty", analysis.difficulty)
        addProperty("difficultyScore", analysis.difficultyScore)
        add("knowledgePoints", stringArray(analysis.knowledgePoints))
        add("commonMistakes", stringArray(analysis.commonMistakes))
        add("solutionMethods", stringArray(analysis.solutionMethods))
        add("cautions", stringArray(analysis.cautions.ifEmpty { analysis.notices }))
        add("notices", stringArray(analysis.notices.ifEmpty { analysis.cautions }))
        addProperty("studyAdvice", analysis.studyAdvice)
        addProperty("updatedAt", iso(analysis.updatedAt))
        addProperty("source", analysis.source)
    }

    private fun followUps(
        questionId: String,
        fallbackCreatedAt: Long,
        array: JsonArray?
    ): List<FollowUpChat> =
        array?.mapIndexedNotNull { sourceIndex, element ->
            if (!element.isJsonObject) return@mapIndexedNotNull null
            val item = element.asJsonObject
            val role = string(item, "role")
            val content = string(item, "content")
            if ((role != "user" && role != "assistant") || content.isBlank()) {
                return@mapIndexedNotNull null
            }
            val createdAt = millis(item, "createdAt", fallbackCreatedAt)
            FollowUpChat(
                id = string(item, "id").ifBlank {
                    FollowUpChatIds.legacyId(
                        questionId = questionId,
                        role = role,
                        content = content,
                        createdAt = createdAt,
                        sourceIndex = sourceIndex
                    )
                },
                role = role,
                content = content,
                createdAt = createdAt
            )
        }.orEmpty()

    private fun followUpArray(chats: List<FollowUpChat>): JsonArray = JsonArray().apply {
        chats.distinctBy(FollowUpChat::id).forEach { chat ->
            add(JsonObject().apply {
                addProperty("id", chat.id)
                addProperty("role", chat.role)
                addProperty("content", chat.content)
                addProperty("createdAt", iso(chat.createdAt))
            })
        }
    }

    private fun partitionRecords(
        records: List<JsonObject>,
        maxRequestBytes: Long,
        maxDecompressedBytes: Long
    ): List<List<JsonObject>> {
        if (records.isEmpty()) return listOf(emptyList())
        val targetBytes = minOf(
            CLIENT_BATCH_TARGET_BYTES,
            (maxRequestBytes * 3L / 4L).coerceAtLeast(MIN_BATCH_TARGET_BYTES),
            (maxDecompressedBytes * 3L / 4L).coerceAtLeast(MIN_BATCH_TARGET_BYTES)
        )
        val batches = mutableListOf<MutableList<JsonObject>>()
        var current = mutableListOf<JsonObject>()
        var currentBytes = 256L
        records.forEach { record ->
            val recordBytes = gson.toJson(record).toByteArray(Charsets.UTF_8).size.toLong() + 1L
            if (recordBytes + 256L > maxDecompressedBytes) {
                throw SyncException("单条题目数据 ${formatBytes(recordBytes)} 超过服务端上限，请压缩该题图片")
            }
            if (current.isNotEmpty() && currentBytes + recordBytes > targetBytes) {
                batches += current
                current = mutableListOf()
                currentBytes = 256L
            }
            current += record
            currentBytes += recordBytes
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    private fun postJson(root: JsonObject, gzip: Boolean): JsonObject =
        postJsonString(gson.toJson(root), gzip)

    private fun postJsonString(json: String, gzip: Boolean): JsonObject {
        val raw = json.toByteArray(Charsets.UTF_8)
        val bytes = if (gzip) gzip(raw) else raw
        val requestBuilder = authorizedRequest()
            .post(bytes.toRequestBody(jsonMedia))
            .addHeader("Content-Type", "application/json")
        if (gzip) requestBuilder.addHeader("Content-Encoding", "gzip")
        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw HttpFailure(response.code, body)
                return parseRoot(body, "同步接口")
            }
        } catch (error: HttpFailure) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw error
        } catch (error: SocketException) {
            throw error
        } catch (error: IOException) {
            throw SyncException("无法连接 VPS：${error.message ?: "网络错误"}", cause = error)
        }
    }

    private fun authorizedRequest(): Request.Builder = Request.Builder()
        .url(apiUrl)
        .addHeader("Authorization", "Bearer $token")

    private fun friendlyHttpError(error: HttpFailure): SyncException {
        return SyncException(SyncErrorMapper.message(error.code, error.body))
    }

    private fun requireV2Mode(root: JsonObject, mode: String) {
        if (int(root, "protocolVersion", 0) != PROTOCOL_V2 || string(root, "mode") != mode) {
            throw SyncException("VPS 返回了不兼容的 v2 $mode 响应")
        }
    }

    private fun parseRoot(body: String, source: String): JsonObject =
        runCatching { JsonParser.parseString(body).asJsonObject }
            .getOrElse { throw SyncException("$source 返回了无效 JSON", cause = it) }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    private fun requiredArray(json: JsonObject, name: String): JsonArray =
        optionalArray(json, name) ?: throw SyncException("同步接口未返回有效的 $name 数组")

    private fun optionalArray(json: JsonObject, name: String): JsonArray? =
        json.get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray

    private fun optionalObject(json: JsonObject, name: String): JsonObject? =
        json.get(name)?.takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject

    private fun jsonArray(values: List<JsonObject>): JsonArray = JsonArray().apply { values.forEach(::add) }

    private fun stringArray(values: List<String>): JsonArray = JsonArray().apply {
        values.filter(String::isNotBlank).forEach(::add)
    }

    private fun stringList(array: JsonArray?): List<String> =
        array?.mapNotNull { element ->
            runCatching { element.asString.trim() }.getOrNull()?.takeIf(String::isNotBlank)
        }.orEmpty()

    private fun intList(array: JsonArray?): List<Int> =
        array?.mapNotNull { runCatching { it.asInt }.getOrNull() }.orEmpty()

    private fun string(json: JsonObject, name: String): String = nullableString(json, name).orEmpty()

    private fun nullableString(json: JsonObject, name: String): String? =
        json.get(name)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
            ?.let { runCatching { it.asString.trim() }.getOrNull() }

    private fun opaqueString(json: JsonObject, name: String): String? =
        nullableString(json, name)?.takeIf(String::isNotBlank)

    private fun bool(json: JsonObject, name: String): Boolean =
        json.get(name)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asBoolean }.getOrNull() } == true

    private fun int(json: JsonObject, name: String, default: Int): Int =
        json.get(name)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asInt }.getOrNull() } ?: default

    private fun nullableInt(json: JsonObject, name: String): Int? =
        json.get(name)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asInt }.getOrNull() }

    private fun long(json: JsonObject, name: String, default: Long): Long =
        json.get(name)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asLong }.getOrNull() } ?: default

    private fun millis(json: JsonObject, name: String, default: Long): Long =
        nullableMillis(json, name)?.takeIf { it > 0L } ?: default

    private fun nullableMillis(json: JsonObject, name: String): Long? =
        json.get(name)?.takeIf { !it.isJsonNull }?.let { value ->
            runCatching {
                if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) value.asLong
                else Instant.parse(value.asString).toEpochMilli()
            }.getOrNull()
        }?.takeIf { it > 0L }

    private fun iso(value: Long): String = Instant.ofEpochMilli(value).toString()

    private fun normalizedDeviceId(): String = deviceId.ifBlank { "android-main" }

    private fun canonicalImageId(contentHash: String): String = "img-${contentHash.take(32)}"

    private fun ensureUniqueQuestionIds(questions: List<Question>) {
        val duplicate = questions.groupingBy(Question::id).eachCount().entries
            .firstOrNull { it.value > 1 }?.key
        if (duplicate != null) {
            throw SyncException("服务端完整快照包含重复题目 ID：$duplicate，本地数据未替换")
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun buildStudyAdvice(
        knowledgePoints: List<String>,
        commonMistakes: List<String>,
        solutionMethods: List<String>
    ): String = buildList {
        if (knowledgePoints.isNotEmpty()) add("重点复习：${knowledgePoints.joinToString("、")}")
        if (commonMistakes.isNotEmpty()) add("易错点：${commonMistakes.first()}")
        if (solutionMethods.isNotEmpty()) add("推荐方法：${solutionMethods.joinToString("、")}")
    }.joinToString("。")

    private fun reviewStatus(value: String): ReviewStatus = when (value.lowercase()) {
        "reviewing", "mastered" -> ReviewStatus.REVIEWING
        else -> ReviewStatus.NEW
    }

    private data class EncodedImageGroup(
        val refs: JsonArray,
        val complete: Boolean,
        val unavailableIds: List<String>
    )

    private data class SyncCapabilities(
        val maxRequestBytes: Long,
        val maxDecompressedBytes: Long,
        val defaultPageLimit: Int,
        val maxPageLimit: Int
    )

    private data class PulledSnapshot(
        val records: List<JsonObject>,
        val generation: String?
    )

    private class HttpFailure(val code: Int, val body: String) : IOException()

    companion object {
        private const val PROTOCOL_V2 = 2
        private const val CAPABILITY_PUSH_PULL = "push-pull-v2"
        private const val DEFAULT_SERVER_LIMIT = 50L * 1024L * 1024L
        private const val LEGACY_SAFE_REQUEST_BYTES = 36L * 1024L * 1024L
        private const val LEGACY_SAFE_EXPANDED_BYTES = 42L * 1024L * 1024L
        private const val CLIENT_BATCH_TARGET_BYTES = 16L * 1024L * 1024L
        private const val MIN_BATCH_TARGET_BYTES = 2L * 1024L * 1024L
        private const val DEFAULT_PAGE_LIMIT = 50
        private const val CLIENT_PAGE_LIMIT = 25
        private const val MAX_PULL_PAGES = 10_000
    }
}

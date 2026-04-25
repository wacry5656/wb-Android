package com.wrongbook.app.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.ImageRef
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.QuestionAnalysis
import com.wrongbook.app.model.ReviewStatus
import com.wrongbook.app.model.SubjectCatalog
import com.wrongbook.app.model.SyncStatus
import com.wrongbook.app.utils.ImageFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

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

    suspend fun sync(localQuestions: List<Question>): List<Question> = withContext(Dispatchers.IO) {
        if (apiUrl.isBlank()) error("SYNC_API_URL 未配置")
        if (token.isBlank()) error("SYNC_TOKEN 未配置")

        val records = JsonArray()
        localQuestions.forEach { question ->
            records.add(toSyncJson(question))
        }

        val bodyJson = JsonObject().apply {
            addProperty("deviceId", deviceId.ifBlank { "android-main" })
            add("records", records)
        }

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(bodyJson).toRequestBody(jsonMedia))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            error("同步接口返回错误 ${response.code}: ${body.take(200)}")
        }

        val root = runCatching { JsonParser.parseString(body).asJsonObject }
            .getOrElse { error("同步接口返回了无效 JSON") }
        val remoteRecords = root.getAsJsonArray("records")
            ?: error("同步接口未返回 records")

        remoteRecords.mapNotNull { element ->
            runCatching { fromSyncJson(element.asJsonObject).copy(syncStatus = SyncStatus.SYNCED) }
                .getOrNull()
        }
    }

    private suspend fun toSyncJson(question: Question): JsonObject {
        val imageRefs = materializeImageRefs(question.imageRefs, "question")
        val noteImageRefs = materializeImageRefs(question.noteImageRefs, "note")
        val firstImageDataUrl = dataUrls(imageRefs).firstOrNull()

        return JsonObject().apply {
            addProperty("id", question.id)
            addProperty("title", question.title)
            addProperty("questionText", question.questionText)
            addProperty("userAnswer", question.userAnswer)
            addProperty("correctAnswer", question.correctAnswer)
            addProperty("image", firstImageDataUrl ?: "")
            add("imageRefs", imageRefs)
            addProperty("category", SubjectCatalog.normalize(question.category))
            addProperty("grade", question.grade)
            addProperty("questionType", question.questionType)
            addProperty("source", question.source)
            addProperty("createdAt", iso(question.createdAt))
            addProperty("updatedAt", iso(question.updatedAt))
            addProperty("contentUpdatedAt", iso(question.contentUpdatedAt))
            addProperty("deleted", question.deleted)
            question.deletedAt?.let { addProperty("deletedAt", iso(it)) }
            addProperty("syncStatus", question.syncStatus.name.lowercase())
            addProperty("notes", question.notes)
            addProperty("errorCause", question.errorCause)
            add("tags", stringArray(question.tags))
            addProperty("masteryLevel", question.masteryLevel)
            addProperty("reviewCount", question.reviewCount)
            question.lastReviewedAt?.let { addProperty("lastReviewedAt", iso(it)) }
            question.nextReviewAt?.let { addProperty("nextReviewAt", iso(it)) }
            addProperty("reviewStatus", question.reviewStatus.name.lowercase())
            question.analysis?.let { add("analysis", analysisJson(it)) }
            question.analysisContentUpdatedAt?.let {
                addProperty("analysisContentUpdatedAt", iso(it))
            }
            question.detailedExplanation?.let { addProperty("detailedExplanation", it) }
            question.detailedExplanationUpdatedAt?.let {
                addProperty("detailedExplanationUpdatedAt", iso(it))
            }
            question.explanationContentUpdatedAt?.let {
                addProperty("explanationContentUpdatedAt", iso(it))
            }
            question.hint?.let { addProperty("hint", it) }
            question.hintUpdatedAt?.let { addProperty("hintUpdatedAt", iso(it)) }
            question.hintContentUpdatedAt?.let { addProperty("hintContentUpdatedAt", iso(it)) }
            add("followUpChats", followUpArray(question.followUpChats))
            question.followUpContentUpdatedAt?.let {
                addProperty("followUpContentUpdatedAt", iso(it))
            }
            add("noteImages", stringArray(dataUrls(noteImageRefs)))
            add("noteImageRefs", noteImageRefs)
        }
    }

    private suspend fun materializeImageRefs(imageRefs: List<ImageRef>, kind: String): JsonArray {
        val array = JsonArray()
        imageRefs.forEach { ref ->
            val dataUrl = runCatching { ImageFileStore.readImageDataUrl(context, ref) }.getOrNull()
            if (dataUrl != null) {
                array.add(JsonObject().apply {
                    addProperty("id", ref.id)
                    addProperty("storage", "inline")
                    addProperty("kind", kind)
                    addProperty("createdAt", iso(ref.createdAt))
                    ref.mimeType?.let { addProperty("mimeType", it) }
                    addProperty("dataUrl", dataUrl)
                })
            }
        }
        return array
    }

    private fun fromSyncJson(json: JsonObject): Question {
        val now = System.currentTimeMillis()
        val createdAt = millis(json, "createdAt", now)
        val updatedAt = millis(json, "updatedAt", createdAt)
        val contentUpdatedAt = millis(json, "contentUpdatedAt", updatedAt)
        val parsedAnalysis = json.getAsJsonObject("analysis")?.let(::parseAnalysis)
        val parsedDetailedExplanation = nullableString(json, "detailedExplanation")
        val parsedDetailedExplanationUpdatedAt = nullableMillis(json, "detailedExplanationUpdatedAt")
        val parsedHint = nullableString(json, "hint")
        val parsedHintUpdatedAt = nullableMillis(json, "hintUpdatedAt")
        val parsedFollowUps = followUps(json.getAsJsonArray("followUpChats"))

        return Question(
            id = string(json, "id").ifBlank { UUID.randomUUID().toString() },
            title = string(json, "title").ifBlank { "未命名错题" },
            category = SubjectCatalog.normalize(string(json, "category")),
            grade = string(json, "grade"),
            questionType = string(json, "questionType"),
            source = string(json, "source"),
            questionText = string(json, "questionText"),
            userAnswer = string(json, "userAnswer"),
            correctAnswer = string(json, "correctAnswer"),
            notes = string(json, "notes"),
            errorCause = string(json, "errorCause"),
            tags = stringList(json.getAsJsonArray("tags")),
            masteryLevel = int(json, "masteryLevel", 0),
            createdAt = createdAt,
            updatedAt = updatedAt,
            deleted = bool(json, "deleted"),
            deletedAt = nullableMillis(json, "deletedAt"),
            syncStatus = SyncStatus.SYNCED,
            contentUpdatedAt = contentUpdatedAt,
            reviewCount = int(json, "reviewCount", 0),
            lastReviewedAt = nullableMillis(json, "lastReviewedAt"),
            nextReviewAt = nullableMillis(json, "nextReviewAt"),
            reviewStatus = reviewStatus(string(json, "reviewStatus")),
            analysis = parsedAnalysis,
            analysisContentUpdatedAt =
                nullableMillis(json, "analysisContentUpdatedAt")
                    ?: parsedAnalysis?.updatedAt
                    ?: parsedAnalysis?.let { contentUpdatedAt },
            detailedExplanation = parsedDetailedExplanation,
            detailedExplanationUpdatedAt = parsedDetailedExplanationUpdatedAt,
            explanationContentUpdatedAt =
                nullableMillis(json, "explanationContentUpdatedAt")
                    ?: parsedDetailedExplanationUpdatedAt
                    ?: parsedDetailedExplanation?.let { contentUpdatedAt },
            hint = parsedHint,
            hintUpdatedAt = parsedHintUpdatedAt,
            hintContentUpdatedAt =
                nullableMillis(json, "hintContentUpdatedAt")
                    ?: parsedHintUpdatedAt
                    ?: parsedHint?.let { contentUpdatedAt },
            followUpChats = parsedFollowUps,
            followUpContentUpdatedAt =
                nullableMillis(json, "followUpContentUpdatedAt")
                    ?: parsedFollowUps.lastOrNull()?.createdAt
                    ?: parsedFollowUps.takeIf { it.isNotEmpty() }?.let { contentUpdatedAt },
            imageRefs = imageRefs(
                array = json.getAsJsonArray("imageRefs"),
                kind = "question",
                legacySources = listOfNotNull(nullableString(json, "image"))
            ),
            noteImageRefs = imageRefs(
                array = json.getAsJsonArray("noteImageRefs"),
                kind = "note",
                legacySources = stringList(json.getAsJsonArray("noteImages"))
            )
        )
    }

    private fun parseAnalysis(json: JsonObject): QuestionAnalysis {
        val cautions = stringList(json.getAsJsonArray("cautions"))
        val notices = stringList(json.getAsJsonArray("notices")).ifEmpty { cautions }
        val solutionMethods = stringList(json.getAsJsonArray("solutionMethods"))
            .ifEmpty { stringList(json.getAsJsonArray("solution_methods")) }
        val knowledgePoints = stringList(json.getAsJsonArray("knowledgePoints"))
            .ifEmpty { stringList(json.getAsJsonArray("knowledge_points")) }
        val commonMistakes = stringList(json.getAsJsonArray("commonMistakes"))
            .ifEmpty { stringList(json.getAsJsonArray("common_mistakes")) }

        return QuestionAnalysis(
            difficulty = string(json, "difficulty").ifBlank { "中等" },
            difficultyScore = int(json, "difficultyScore", int(json, "difficulty_score", 3)).coerceIn(1, 5),
            knowledgePoints = knowledgePoints,
            commonMistakes = commonMistakes,
            solutionMethods = solutionMethods,
            cautions = cautions.ifEmpty { notices },
            notices = notices,
            studyAdvice = nullableString(json, "studyAdvice") ?: buildStudyAdvice(knowledgePoints, commonMistakes, solutionMethods),
            updatedAt = millis(json, "updatedAt", System.currentTimeMillis()),
            source = string(json, "source").ifBlank { "ai" }
        )
    }

    private fun analysisJson(analysis: QuestionAnalysis): JsonObject =
        JsonObject().apply {
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

    private fun imageRefs(
        array: JsonArray?,
        kind: String,
        legacySources: List<String> = emptyList()
    ): List<ImageRef> {
        val refs = array?.mapNotNull { element ->
            val item = element.asJsonObject
            val dataUrl = nullableString(item, "dataUrl")
            val uri = nullableString(item, "uri")
            val storage = string(item, "storage").ifBlank {
                if (dataUrl != null) "inline" else "file"
            }

            if (dataUrl == null && uri == null) {
                null
            } else {
                ImageRef(
                    id = string(item, "id").ifBlank { UUID.randomUUID().toString() },
                    storage = storage,
                    kind = string(item, "kind").ifBlank { kind },
                    createdAt = millis(item, "createdAt", System.currentTimeMillis()),
                    mimeType = nullableString(item, "mimeType"),
                    dataUrl = dataUrl,
                    uri = uri
                )
            }
        } ?: emptyList()

        if (refs.isNotEmpty()) {
            return refs
        }

        return legacySources.mapNotNull { source ->
            when {
                source.startsWith("data:image/") -> ImageRef(
                    id = UUID.randomUUID().toString(),
                    storage = "inline",
                    kind = kind,
                    createdAt = System.currentTimeMillis(),
                    dataUrl = source
                )
                source.isNotBlank() -> ImageRef(
                    id = UUID.randomUUID().toString(),
                    storage = "file",
                    kind = kind,
                    createdAt = System.currentTimeMillis(),
                    uri = source
                )
                else -> null
            }
        }
    }

    private fun followUps(array: JsonArray?): List<FollowUpChat> =
        array?.mapNotNull { element ->
            val item = element.asJsonObject
            val role = string(item, "role")
            val content = string(item, "content")
            if ((role != "user" && role != "assistant") || content.isBlank()) {
                null
            } else {
                FollowUpChat(
                    id = string(item, "id").ifBlank { UUID.randomUUID().toString() },
                    role = role,
                    content = content,
                    createdAt = millis(item, "createdAt", System.currentTimeMillis())
                )
            }
        } ?: emptyList()

    private fun followUpArray(chats: List<FollowUpChat>): JsonArray {
        val array = JsonArray()
        chats.forEach { chat ->
            array.add(JsonObject().apply {
                addProperty("id", chat.id)
                addProperty("role", chat.role)
                addProperty("content", chat.content)
                addProperty("createdAt", iso(chat.createdAt))
            })
        }
        return array
    }

    private fun stringArray(values: List<String>): JsonArray {
        val array = JsonArray()
        values.filter { it.isNotBlank() }.forEach(array::add)
        return array
    }

    private fun dataUrls(array: JsonArray): List<String> =
        (0 until array.size()).mapNotNull { index ->
            array[index].asJsonObject.get("dataUrl")?.takeIf { !it.isJsonNull }?.asString
        }

    private fun stringList(array: JsonArray?): List<String> =
        array?.mapNotNull { element ->
            runCatching { element.asString.trim() }.getOrNull()?.takeIf(String::isNotBlank)
        } ?: emptyList()

    private fun string(json: JsonObject, name: String): String =
        nullableString(json, name).orEmpty()

    private fun nullableString(json: JsonObject, name: String): String? =
        json.get(name)?.takeIf { !it.isJsonNull }?.asString?.trim()

    private fun bool(json: JsonObject, name: String): Boolean =
        json.get(name)?.takeIf { !it.isJsonNull }?.asBoolean == true

    private fun int(json: JsonObject, name: String, default: Int): Int =
        json.get(name)?.takeIf { !it.isJsonNull }?.asInt ?: default

    private fun millis(json: JsonObject, name: String, default: Long): Long =
        json.get(name)?.takeIf { !it.isJsonNull }?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asLong else parseMillis(it.asString)
        }?.takeIf { it > 0 } ?: default

    private fun nullableMillis(json: JsonObject, name: String): Long? =
        json.get(name)?.takeIf { !it.isJsonNull }?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asLong else parseMillis(it.asString)
        }?.takeIf { it > 0 }

    private fun parseMillis(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0)

    private fun iso(value: Long): String = Instant.ofEpochMilli(value).toString()

    private fun buildStudyAdvice(
        knowledgePoints: List<String>,
        commonMistakes: List<String>,
        solutionMethods: List<String>
    ): String = buildList {
        if (knowledgePoints.isNotEmpty()) add("重点复习：${knowledgePoints.joinToString("、")}")
        if (commonMistakes.isNotEmpty()) add("易错点：${commonMistakes.first()}")
        if (solutionMethods.isNotEmpty()) add("推荐方法：${solutionMethods.joinToString("、")}")
    }.joinToString("。")

    private fun reviewStatus(value: String): ReviewStatus =
        when (value.lowercase()) {
            "reviewing", "mastered" -> ReviewStatus.REVIEWING
            else -> ReviewStatus.NEW
        }
}

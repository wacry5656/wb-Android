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
import com.wrongbook.app.model.StorageType
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

        val root = JsonParser.parseString(body).asJsonObject
        val remoteRecords = root.getAsJsonArray("records") ?: JsonArray()
        remoteRecords.mapNotNull { element ->
            runCatching { fromSyncJson(element.asJsonObject) }.getOrNull()
        }
    }

    private suspend fun toSyncJson(question: Question): JsonObject {
        val imageRefs = materializeImageRefs(question.imageRefs, "question")
        val noteImageRefs = materializeImageRefs(question.noteImageRefs, "note")
        val firstImageDataUrl = dataUrls(imageRefs).firstOrNull()

        return JsonObject().apply {
            addProperty("id", question.id)
            addProperty("title", question.title)
            addProperty("image", firstImageDataUrl ?: "")
            add("imageRefs", imageRefs)
            addProperty("category", SubjectCatalog.normalize(question.category))
            addProperty("grade", question.grade)
            addProperty("questionType", question.questionType)
            addProperty("source", question.source)
            addProperty("questionText", question.questionText)
            addProperty("userAnswer", question.userAnswer)
            addProperty("correctAnswer", question.correctAnswer)
            addProperty("notes", question.notes)
            addProperty("errorCause", question.errorCause)
            add("tags", stringArray(question.tags))
            addProperty("masteryLevel", question.masteryLevel)
            addProperty("createdAt", iso(question.createdAt))
            addProperty("updatedAt", iso(question.updatedAt))
            addProperty("deleted", question.deleted)
            question.deletedAt?.let { addProperty("deletedAt", iso(it)) }
            addProperty("syncStatus", question.syncStatus.name.lowercase())
            addProperty("contentUpdatedAt", question.contentUpdatedAt)
            addProperty("reviewCount", question.reviewCount)
            question.lastReviewedAt?.let { addProperty("lastReviewedAt", iso(it)) }
            question.nextReviewAt?.let { addProperty("nextReviewAt", iso(it)) }
            addProperty("reviewStatus", question.reviewStatus.name.lowercase())
            question.analysis?.let { add("analysis", analysisJson(it)) }
            question.detailedExplanation?.let { addProperty("detailedExplanation", it) }
            question.explanationContentUpdatedAt?.let { addProperty("detailedExplanationUpdatedAt", iso(it)) }
            question.hint?.let { addProperty("hint", it) }
            question.hintContentUpdatedAt?.let { addProperty("hintUpdatedAt", iso(it)) }
            add("followUpChats", followUpArray(question.followUpChats))
            add("noteImages", stringArray(dataUrls(noteImageRefs)))
            add("noteImageRefs", noteImageRefs)
        }
    }

    private suspend fun materializeImageRefs(imageRefs: List<ImageRef>, kind: String): JsonArray {
        val array = JsonArray()
        imageRefs.forEach { ref ->
            val dataUrl = runCatching { ImageFileStore.readImageDataUrl(context, ref) }.getOrNull()
            array.add(JsonObject().apply {
                addProperty("id", ref.id)
                addProperty("kind", kind)
                addProperty("createdAt", iso(ref.createdAt))
                addProperty("mimeType", ref.mimeType ?: dataUrl?.substringAfter("data:")?.substringBefore(";"))
                if (dataUrl != null) {
                    addProperty("storage", "inline")
                    addProperty("dataUrl", dataUrl)
                } else {
                    addProperty("storage", "file")
                    addProperty("uri", ref.uri)
                }
            })
        }
        return array
    }

    private fun fromSyncJson(json: JsonObject): Question {
        val now = System.currentTimeMillis()
        return Question(
            id = string(json, "id").ifBlank { UUID.randomUUID().toString() },
            title = string(json, "title").ifBlank { "未命名错题" },
            category = SubjectCatalog.normalize(string(json, "category")),
            grade = string(json, "grade"),
            questionType = string(json, "questionType"),
            source = string(json, "source"),
            questionText = nullableString(json, "questionText"),
            userAnswer = nullableString(json, "userAnswer"),
            correctAnswer = nullableString(json, "correctAnswer"),
            notes = nullableString(json, "notes"),
            errorCause = string(json, "errorCause"),
            tags = stringList(json.getAsJsonArray("tags")),
            masteryLevel = int(json, "masteryLevel", 0),
            createdAt = millis(json, "createdAt", now),
            updatedAt = millis(json, "updatedAt", now),
            deleted = bool(json, "deleted"),
            deletedAt = nullableMillis(json, "deletedAt"),
            syncStatus = syncStatus(string(json, "syncStatus")),
            contentUpdatedAt = long(json, "contentUpdatedAt", millis(json, "updatedAt", now)),
            reviewCount = int(json, "reviewCount", 0),
            lastReviewedAt = nullableMillis(json, "lastReviewedAt"),
            nextReviewAt = nullableMillis(json, "nextReviewAt"),
            reviewStatus = reviewStatus(string(json, "reviewStatus")),
            analysis = json.getAsJsonObject("analysis")?.let { parseAnalysis(it) },
            detailedExplanation = nullableString(json, "detailedExplanation"),
            explanationContentUpdatedAt = nullableMillis(json, "detailedExplanationUpdatedAt"),
            hint = nullableString(json, "hint"),
            hintContentUpdatedAt = nullableMillis(json, "hintUpdatedAt"),
            followUpChats = followUps(json.getAsJsonArray("followUpChats")),
            imageRefs = imageRefs(json.getAsJsonArray("imageRefs"), "question"),
            noteImageRefs = imageRefs(json.getAsJsonArray("noteImageRefs"), "note")
        )
    }

    private fun parseAnalysis(json: JsonObject): QuestionAnalysis =
        QuestionAnalysis(
            difficulty = string(json, "difficulty").ifBlank { "中等" },
            difficultyScore = int(json, "difficultyScore", 3).coerceIn(1, 5),
            knowledgePoints = stringList(json.getAsJsonArray("knowledgePoints")),
            commonMistakes = stringList(json.getAsJsonArray("commonMistakes")),
            solutionMethods = stringList(json.getAsJsonArray("solutionMethods")),
            notices = stringList(json.getAsJsonArray("notices")).ifEmpty {
                stringList(json.getAsJsonArray("cautions"))
            },
            updatedAt = millis(json, "updatedAt", System.currentTimeMillis())
        )

    private fun analysisJson(analysis: QuestionAnalysis): JsonObject =
        JsonObject().apply {
            addProperty("difficulty", analysis.difficulty)
            addProperty("difficultyScore", analysis.difficultyScore)
            add("knowledgePoints", stringArray(analysis.knowledgePoints))
            add("commonMistakes", stringArray(analysis.commonMistakes))
            add("solutionMethods", stringArray(analysis.solutionMethods))
            add("notices", stringArray(analysis.notices))
            add("cautions", stringArray(analysis.notices))
            addProperty("updatedAt", iso(analysis.updatedAt))
            addProperty("source", "ai")
        }

    private fun imageRefs(array: JsonArray?, kind: String): List<ImageRef> =
        array?.mapNotNull { element ->
            val item = element.asJsonObject
            val dataUrl = nullableString(item, "dataUrl")
            val uri = dataUrl ?: string(item, "uri")
            if (uri.isBlank()) null else ImageRef(
                id = string(item, "id").ifBlank { UUID.randomUUID().toString() },
                uri = uri,
                storageType = if (dataUrl != null) StorageType.INLINE else StorageType.URI,
                createdAt = millis(item, "createdAt", System.currentTimeMillis()),
                dataUrl = dataUrl,
                storage = string(item, "storage"),
                kind = kind,
                mimeType = nullableString(item, "mimeType")
            )
        } ?: emptyList()

    private fun followUps(array: JsonArray?): List<FollowUpChat> =
        array?.mapNotNull { element ->
            val item = element.asJsonObject
            val role = string(item, "role")
            val content = string(item, "content")
            if ((role != "user" && role != "assistant") || content.isBlank()) null else FollowUpChat(
                id = string(item, "id").ifBlank { UUID.randomUUID().toString() },
                role = role,
                content = content,
                createdAt = millis(item, "createdAt", System.currentTimeMillis())
            )
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
        array?.mapNotNull { it.asString?.trim()?.takeIf(String::isNotBlank) } ?: emptyList()

    private fun string(json: JsonObject, name: String): String =
        nullableString(json, name).orEmpty()

    private fun nullableString(json: JsonObject, name: String): String? =
        json.get(name)?.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotBlank() }

    private fun bool(json: JsonObject, name: String): Boolean =
        json.get(name)?.takeIf { !it.isJsonNull }?.asBoolean == true

    private fun int(json: JsonObject, name: String, default: Int): Int =
        json.get(name)?.takeIf { !it.isJsonNull }?.asInt ?: default

    private fun long(json: JsonObject, name: String, default: Long): Long =
        json.get(name)?.takeIf { !it.isJsonNull }?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asLong else parseMillis(it.asString)
        } ?: default

    private fun millis(json: JsonObject, name: String, default: Long): Long =
        json.get(name)?.takeIf { !it.isJsonNull }?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asLong else parseMillis(it.asString)
        } ?: default

    private fun nullableMillis(json: JsonObject, name: String): Long? =
        json.get(name)?.takeIf { !it.isJsonNull }?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asLong else parseMillis(it.asString)
        }?.takeIf { it > 0 }

    private fun parseMillis(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0)

    private fun iso(value: Long): String = Instant.ofEpochMilli(value).toString()

    private fun syncStatus(value: String): SyncStatus =
        when (value.lowercase()) {
            "synced" -> SyncStatus.SYNCED
            "modified" -> SyncStatus.MODIFIED
            else -> SyncStatus.PENDING
        }

    private fun reviewStatus(value: String): ReviewStatus =
        when (value.lowercase()) {
            "reviewing" -> ReviewStatus.REVIEWING
            "mastered" -> ReviewStatus.MASTERED
            else -> ReviewStatus.NEW
        }
}

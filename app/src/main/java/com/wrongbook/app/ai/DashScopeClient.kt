package com.wrongbook.app.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 封装 DashScope OpenAI-compatible chat completions 接口调用。
 * 只做 HTTP 传输 + 基础解析，不包含业务 prompt 逻辑。
 */
class DashScopeClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) {
    companion object {
        private const val TAG = "DashScopeClient"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)   // AI 生成可能较慢
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ChatMessage(val role: String, val content: Any)

    data class ChatResult(val content: String, val finishReason: String?)

    /**
     * 调用 chat/completions 接口。
     * @param messages 对话消息列表
     * @param temperature 温度（0-2）
     * @return AI 返回的文本内容
     * @throws AiException 包含用户可读的错误信息
     */
    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        temperature: Double = 0.7,
        responseJsonObject: Boolean = false,
        enableThinking: Boolean = false
    ): ChatResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw AiException("DashScope API Key 未配置，请在 local.properties 中设置 DASHSCOPE_API_KEY")
        }

        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        val bodyFields = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
            "temperature" to temperature,
            "enable_thinking" to enableThinking
        )
        if (responseJsonObject) {
            bodyFields["response_format"] = mapOf("type" to "json_object")
        }

        val requestBody = gson.toJson(bodyFields)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA))
            .build()

        Log.d(TAG, "Request -> $url model=$model messages=${messages.size}")

        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw AiException("网络连接失败，请检查网络设置: ${e.message}", e)
        }

        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e(TAG, "HTTP ${response.code}: $body")
            val errorMsg = try {
                val errorJson = JsonParser.parseString(body).asJsonObject
                errorJson.getAsJsonObject("error")?.get("message")?.asString
            } catch (_: Exception) { null }

            throw AiException(
                "AI 接口返回错误 (${response.code}): ${errorMsg ?: body.take(200)}"
            )
        }

        try {
            val json = JsonParser.parseString(body).asJsonObject
            val choice = json.getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?: throw AiException("AI 返回格式异常: 缺少 choices")
            val message = choice.getAsJsonObject("message")
                ?: throw AiException("AI 返回格式异常: 缺少 message")
            val content = message.get("content")?.asString
                ?: throw AiException("AI 返回格式异常: 缺少 content")
            val finishReason = choice.get("finish_reason")?.asString

            Log.d(TAG, "Response OK, content length=${content.length}, finish=$finishReason")
            ChatResult(content = content, finishReason = finishReason)
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: $body", e)
            throw AiException("AI 返回解析失败: ${e.message}", e)
        }
    }
}

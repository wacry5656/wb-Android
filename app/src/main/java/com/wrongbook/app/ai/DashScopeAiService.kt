package com.wrongbook.app.ai

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.wrongbook.app.model.FollowUpChat
import com.wrongbook.app.model.Question
import com.wrongbook.app.model.QuestionAnalysis
import com.wrongbook.app.utils.ImageFileStore
import java.util.UUID

/**
 * 真实 DashScope / Qwen AI 实现。
 * 通过 DashScopeClient 调用 OpenAI-compatible API。
 */
class DashScopeAiService(
    private val client: DashScopeClient,
    private val context: Context
) : AiService {

    companion object {
        private const val TAG = "DashScopeAiService"
    }

    override suspend fun analyze(question: Question): QuestionAnalysis {
        val result = chatWithQuestionImageFallback(
            question = question,
            systemPrompt = AiPrompts.analyzeSystem(),
            userPrompt = AiPrompts.analyzeUser(question),
            temperature = 0.3,
            responseJsonObject = true
        )
        return parseAnalysis(result.content)
    }

    override suspend fun generateDetailedExplanation(question: Question): String {
        val result = chatWithQuestionImageFallback(
            question = question,
            systemPrompt = AiPrompts.explanationSystem(),
            userPrompt = AiPrompts.explanationUser(question),
            temperature = 0.7,
            enableThinking = true
        )
        return result.content
    }

    override suspend fun generateHint(question: Question): String {
        val result = chatWithQuestionImageFallback(
            question = question,
            systemPrompt = AiPrompts.hintSystem(),
            userPrompt = AiPrompts.hintUser(question),
            temperature = 0.7
        )
        return result.content
    }

    override suspend fun followUp(question: Question, userMessage: String): FollowUpChat {
        val result = chatFollowUpWithQuestionImageFallback(
            question = question,
            userMessage = userMessage
        )
        return FollowUpChat(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = result.content,
            createdAt = System.currentTimeMillis()
        )
    }

    private suspend fun chatWithQuestionImageFallback(
        question: Question,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        responseJsonObject: Boolean = false,
        enableThinking: Boolean = false
    ): DashScopeClient.ChatResult {
        val imageDataUrl = firstQuestionImageDataUrl(question)
        if (imageDataUrl != null) {
            try {
                return client.chatCompletion(
                    messages = listOf(
                        DashScopeClient.ChatMessage("system", systemPrompt),
                        DashScopeClient.ChatMessage(
                            "user",
                            imageContent(imageDataUrl, userPrompt)
                        )
                    ),
                    temperature = temperature,
                    responseJsonObject = responseJsonObject,
                    enableThinking = enableThinking
                )
            } catch (e: AiException) {
                Log.w(TAG, "Vision request failed, fallback to text prompt: ${e.message}")
            }
        }

        return client.chatCompletion(
            messages = listOf(
                DashScopeClient.ChatMessage("system", systemPrompt),
                DashScopeClient.ChatMessage("user", userPrompt)
            ),
            temperature = temperature,
            responseJsonObject = responseJsonObject,
            enableThinking = enableThinking
        )
    }

    private suspend fun chatFollowUpWithQuestionImageFallback(
        question: Question,
        userMessage: String
    ): DashScopeClient.ChatResult {
        val imageDataUrl = firstQuestionImageDataUrl(question)
        if (imageDataUrl != null) {
            try {
                val messages = mutableListOf<DashScopeClient.ChatMessage>()
                messages.add(DashScopeClient.ChatMessage("system", AiPrompts.followUpSystem(question)))
                messages.add(
                    DashScopeClient.ChatMessage(
                        "user",
                        imageContent(
                            imageDataUrl,
                            "这是这道错题的图片，请结合图片和下面的题目信息回答学生追问。\n\n${AiPrompts.questionContextForAi(question)}"
                        )
                    )
                )
                messages.add(DashScopeClient.ChatMessage("assistant", "好的，我已经看过题目图片和题目信息。"))
                question.followUpChats.takeLast(20).forEach { chat ->
                    messages.add(DashScopeClient.ChatMessage(chat.role, chat.content))
                }
                messages.add(DashScopeClient.ChatMessage("user", userMessage))

                return client.chatCompletion(
                    messages = messages,
                    temperature = 0.7,
                    enableThinking = true
                )
            } catch (e: AiException) {
                Log.w(TAG, "Vision follow-up failed, fallback to text prompt: ${e.message}")
            }
        }

        return client.chatCompletion(
            messages = AiPrompts.followUpMessages(question, userMessage),
            temperature = 0.7,
            enableThinking = true
        )
    }

    private suspend fun firstQuestionImageDataUrl(question: Question): String? {
        val imageRef = question.imageRefs.firstOrNull() ?: return null
        return try {
            ImageFileStore.readImageDataUrl(context, imageRef)
        } catch (e: Exception) {
            Log.w(TAG, "Read question image failed, fallback to text prompt: ${e.message}")
            null
        }
    }

    private fun imageContent(imageDataUrl: String, text: String): List<Map<String, Any>> =
        listOf(
            mapOf(
                "type" to "image_url",
                "image_url" to mapOf("url" to imageDataUrl)
            ),
            mapOf(
                "type" to "text",
                "text" to text
            )
        )

    /**
     * 解析 AI 返回的分析 JSON。
     * 做了多重兜底：支持 markdown 代码块包裹、字段缺失、格式不规范等。
     */
    private fun parseAnalysis(raw: String): QuestionAnalysis {
        val now = System.currentTimeMillis()

        // 尝试提取 JSON 块（可能被 ```json ... ``` 包裹）
        val jsonStr = extractJsonBlock(raw)

        return try {
            val json = JsonParser.parseString(jsonStr).asJsonObject

            QuestionAnalysis(
                difficulty = normalizeDifficulty(json.get("difficulty")?.asString),
                difficultyScore = normalizeDifficultyScore(
                    json.get("difficultyScore")?.asInt
                        ?: json.get("difficulty_score")?.asInt
                ),
                knowledgePoints = json.getAsJsonArray("knowledgePoints")
                    ?.map { it.asString }
                    ?: json.getAsJsonArray("knowledge_points")?.map { it.asString }
                    ?: emptyList(),
                commonMistakes = json.getAsJsonArray("commonMistakes")
                    ?.map { it.asString }
                    ?: json.getAsJsonArray("common_mistakes")?.map { it.asString }
                    ?: emptyList(),
                solutionMethods = json.getAsJsonArray("solutionMethods")
                    ?.map { it.asString }
                    ?: json.getAsJsonArray("solution_methods")?.map { it.asString }
                    ?: json.getAsJsonArray("recommended_methods")?.map { it.asString }
                    ?: emptyList(),
                notices = json.getAsJsonArray("notices")
                    ?.map { it.asString }
                    ?: json.getAsJsonArray("cautions")?.map { it.asString }
                    ?: json.getAsJsonArray("tips")?.map { it.asString }
                    ?: emptyList(),
                updatedAt = now
            )
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse fallback, raw length=${raw.length}", e)
            // 兜底：如果 JSON 解析失败，保留一个可显示的推荐方法，避免页面空白。
            QuestionAnalysis(
                difficulty = "中等",
                difficultyScore = 3,
                solutionMethods = listOf(raw.take(200)),
                updatedAt = now
            )
        }
    }

    /**
     * 从 AI 回复中提取 JSON 字符串。
     * 处理常见情况：```json {...} ``` 或直接 {...}
     */
    private fun extractJsonBlock(text: String): String {
        // 先尝试提取 ```json ... ``` 代码块
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n?(\\{[\\s\\S]*?})\\s*```")
        codeBlockRegex.find(text)?.let {
            return it.groupValues[1]
        }

        // 直接找第一个 { 到最后一个 }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start != -1 && end > start) {
            return text.substring(start, end + 1)
        }

        return text
    }

    private fun normalizeDifficulty(value: String?): String =
        when (value?.trim()) {
            "简单", "基础", "较易", "容易" -> "简单"
            "困难", "难", "较难", "高难" -> "困难"
            "中等", "一般" -> "中等"
            else -> "中等"
        }

    private fun normalizeDifficultyScore(value: Int?): Int =
        when {
            value == null -> 3
            value < 1 -> 1
            value > 5 -> 5
            else -> value
        }
}

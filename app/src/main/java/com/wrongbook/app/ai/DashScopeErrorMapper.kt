package com.wrongbook.app.ai

internal object DashScopeErrorMapper {
    private val visionUnsupportedCodes = setOf(
        "imagenotsupported",
        "modelnotsupportvision",
        "unsupportedimageinput",
        "unsupportedmediatype",
        "unsupportedmultimodalinput",
        "visionnotsupported"
    )

    private val visionUnsupportedMessages = listOf(
        "does not support image input",
        "doesn't support image input",
        "image input is not supported",
        "image inputs are not supported",
        "multimodal input is not supported",
        "only supports text input",
        "vision input is not supported"
    )

    fun friendlyMessage(
        status: Int,
        errorCode: String?,
        providerMessage: String?,
        rawBody: String
    ): String {
        val diagnostic = buildString {
            append("HTTP ")
            append(status)
            errorCode?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append(" / ")
                append(it.take(80))
            }
        }

        if (isFreeTierOnly(status, errorCode, providerMessage)) {
            return "当前 AI 模型的免费额度已用完。请切换有可用额度的模型，或在 DashScope 控制台关闭“仅使用免费额度”后重试。（$diagnostic）"
        }

        return when (status) {
            401 -> "AI 服务鉴权失败，请检查 DashScope API Key 是否正确、有效且与当前区域匹配。（$diagnostic）"
            403 -> "AI 服务拒绝访问，请检查 API Key 权限、模型权限和账户状态。（$diagnostic）"
            else -> {
                val detail = providerMessage
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: rawBody.trim().take(200).ifEmpty { "未返回错误详情" }
                "AI 接口返回错误（$diagnostic）：$detail"
            }
        }
    }

    fun isVisionUnsupported(
        status: Int?,
        errorCode: String?,
        providerMessage: String?
    ): Boolean {
        if (status !in setOf(400, 415, 422)) return false

        val normalizedCode = errorCode
            ?.lowercase()
            ?.filter { it.isLetterOrDigit() }
        if (normalizedCode != null && normalizedCode in visionUnsupportedCodes) return true

        val message = providerMessage.orEmpty().lowercase()
        return visionUnsupportedMessages.any(message::contains) ||
            message.contains("不支持图片输入") ||
            message.contains("不支持图像输入") ||
            message.contains("不支持视觉输入") ||
            message.contains("不支持多模态输入")
    }

    private fun isFreeTierOnly(
        status: Int,
        errorCode: String?,
        providerMessage: String?
    ): Boolean {
        if (status != 403) return false
        val normalizedCode = errorCode
            ?.lowercase()
            ?.filter { it.isLetterOrDigit() }
            .orEmpty()
        if (normalizedCode.contains("freetieronly")) return true

        val message = providerMessage.orEmpty().lowercase()
        return message.contains("free quota has been exhausted") ||
            message.contains("use free tier only")
    }
}

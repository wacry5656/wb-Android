package com.wrongbook.app.ai

/**
 * AI 层统一异常，携带用户可读的错误信息。
 * UI 层可直接展示 message 而不暴露技术细节。
 */
class AiException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

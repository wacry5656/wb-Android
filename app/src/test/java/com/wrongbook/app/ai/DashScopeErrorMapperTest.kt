package com.wrongbook.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashScopeErrorMapperTest {
    @Test
    fun freeTierExhaustionIsFriendlyAndNeverTriggersTextRetry() {
        val providerMessage =
            "The free quota has been exhausted. Disable the use free tier only mode."
        val message = DashScopeErrorMapper.friendlyMessage(
            status = 403,
            errorCode = "AllocationQuota.FreeTierOnly",
            providerMessage = providerMessage,
            rawBody = ""
        )
        val exception = AiException(
            message = message,
            httpStatus = 403,
            errorCode = "AllocationQuota.FreeTierOnly",
            providerMessage = providerMessage
        )

        assertTrue(message.contains("免费额度已用完"))
        assertFalse(exception.isVisionUnsupported)
    }

    @Test
    fun authenticationFailuresAreFriendlyAndNeverTriggerTextRetry() {
        val message = DashScopeErrorMapper.friendlyMessage(
            status = 401,
            errorCode = "InvalidApiKey",
            providerMessage = "Invalid API-key provided.",
            rawBody = ""
        )
        val exception = AiException(
            message = message,
            httpStatus = 401,
            errorCode = "InvalidApiKey",
            providerMessage = "Invalid API-key provided."
        )

        assertTrue(message.contains("鉴权失败"))
        assertFalse(exception.isVisionUnsupported)
    }

    @Test
    fun explicitUnsupportedImageErrorTriggersOneTextFallback() {
        val exception = AiException(
            message = "unsupported",
            httpStatus = 400,
            errorCode = "ImageNotSupported",
            providerMessage = "This model does not support image input."
        )

        assertTrue(exception.isVisionUnsupported)
    }

    @Test
    fun genericInvalidParameterDoesNotHideTheOriginalFailure() {
        val exception = AiException(
            message = "invalid parameter",
            httpStatus = 400,
            errorCode = "InvalidParameter",
            providerMessage = "temperature is invalid"
        )

        assertFalse(exception.isVisionUnsupported)
    }
}

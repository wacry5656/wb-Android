package com.wrongbook.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashScopeRequestFieldsTest {
    private val messages = listOf(
        DashScopeClient.ChatMessage(role = "user", content = "test")
    )

    @Test
    fun jsonModeExplicitlyDisablesThinking() {
        val fields = buildDashScopeRequestFields(
            model = "qwen3.7-plus",
            messages = messages,
            temperature = 0.3,
            responseJsonObject = true,
            enableThinking = false
        )

        assertEquals(false, fields["enable_thinking"])
        assertEquals(mapOf("type" to "json_object"), fields["response_format"])
    }

    @Test
    fun thinkingModeIsExplicitlyEnabled() {
        val fields = buildDashScopeRequestFields(
            model = "qwen3.7-plus",
            messages = messages,
            temperature = 0.7,
            responseJsonObject = false,
            enableThinking = true
        )

        assertEquals(true, fields["enable_thinking"])
        assertFalse(fields.containsKey("response_format"))
    }

    @Test
    fun requestKeepsModelMessagesAndTemperature() {
        val fields = buildDashScopeRequestFields(
            model = "qwen3.7-plus",
            messages = messages,
            temperature = 0.5,
            responseJsonObject = false,
            enableThinking = false
        )

        assertEquals("qwen3.7-plus", fields["model"])
        assertEquals(0.5, fields["temperature"])
        assertTrue((fields["messages"] as List<*>).isNotEmpty())
    }
}

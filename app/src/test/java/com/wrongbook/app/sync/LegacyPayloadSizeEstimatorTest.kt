package com.wrongbook.app.sync

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPayloadSizeEstimatorTest {
    @Test
    fun `counts every canonical question and note dataUrl as a legacy duplicate`() {
        val first = "data:image/jpeg;base64,AAAA"
        val second = "data:image/png;base64,BBBBBB"
        val third = "data:image/webp;base64,CCCCCCCC"
        val record = JsonObject().apply {
            add("imageRefs", refs(first, second))
            add("noteImageRefs", refs(third))
        }
        val request = JsonObject().apply { add("records", JsonArray().apply { add(record) }) }
        val json = Gson().toJson(request)

        val estimate = LegacyPayloadSizeEstimator.estimate(json, listOf(record))

        assertEquals(json.toByteArray().size.toLong(), estimate.requestBytes)
        assertEquals(
            (first.toByteArray().size + second.toByteArray().size + third.toByteArray().size).toLong(),
            estimate.duplicatedImageDataBytes
        )
        assertEquals(3, estimate.duplicatedFieldCount)
        assertTrue(estimate.expandedBytes > estimate.requestBytes + estimate.duplicatedImageDataBytes)
    }

    @Test
    fun `rejects request that fits upload limit but exceeds expanded v1 limit`() {
        val estimate = LegacyPayloadSizeEstimate(
            requestBytes = 900,
            duplicatedImageDataBytes = 700,
            duplicatedFieldCount = 1,
            expandedBytes = 1_664
        )

        assertFalse(
            LegacyPayloadSizeEstimator.isSafe(
                estimate,
                maxRequestBytes = 1_000,
                maxExpandedBytes = 1_500
            )
        )
        assertTrue(
            LegacyPayloadSizeEstimator.isSafe(
                estimate,
                maxRequestBytes = 1_000,
                maxExpandedBytes = 1_700
            )
        )
    }

    @Test
    fun `text only request adds no duplicate image bytes`() {
        val record = JsonObject().apply {
            add("imageRefs", JsonArray())
            add("noteImageRefs", JsonArray())
        }
        val json = Gson().toJson(record)

        val estimate = LegacyPayloadSizeEstimator.estimate(json, listOf(record))

        assertEquals(0L, estimate.duplicatedImageDataBytes)
        assertEquals(estimate.requestBytes, estimate.expandedBytes)
    }

    private fun refs(vararg dataUrls: String): JsonArray = JsonArray().apply {
        dataUrls.forEach { dataUrl ->
            add(JsonObject().apply { addProperty("dataUrl", dataUrl) })
        }
    }
}

package com.wrongbook.app.sync

import com.google.gson.JsonObject

internal data class LegacyPayloadSizeEstimate(
    val requestBytes: Long,
    val duplicatedImageDataBytes: Long,
    val duplicatedFieldCount: Int,
    val expandedBytes: Long
)

/** Estimates the v1 response/storage expansion caused by legacy image + noteImages mirrors. */
internal object LegacyPayloadSizeEstimator {
    private const val DUPLICATED_FIELD_OVERHEAD_BYTES = 64L

    fun estimate(requestJson: String, records: List<JsonObject>): LegacyPayloadSizeEstimate {
        var duplicatedBytes = 0L
        var duplicatedFields = 0
        for (record in records) {
            for (field in listOf("imageRefs", "noteImageRefs")) {
                val refs = record.get(field)?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
                for (element in refs) {
                    val dataUrl = element.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("dataUrl")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?: continue
                    duplicatedBytes += dataUrl.toByteArray(Charsets.UTF_8).size.toLong()
                    duplicatedFields += 1
                }
            }
        }
        val requestBytes = requestJson.toByteArray(Charsets.UTF_8).size.toLong()
        return LegacyPayloadSizeEstimate(
            requestBytes = requestBytes,
            duplicatedImageDataBytes = duplicatedBytes,
            duplicatedFieldCount = duplicatedFields,
            expandedBytes = requestBytes + duplicatedBytes +
                duplicatedFields * DUPLICATED_FIELD_OVERHEAD_BYTES
        )
    }

    fun isSafe(
        estimate: LegacyPayloadSizeEstimate,
        maxRequestBytes: Long,
        maxExpandedBytes: Long
    ): Boolean = estimate.requestBytes <= maxRequestBytes && estimate.expandedBytes <= maxExpandedBytes
}

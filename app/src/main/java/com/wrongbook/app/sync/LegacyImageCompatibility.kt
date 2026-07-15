package com.wrongbook.app.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Adds explicit legacy clear markers without reintroducing duplicate image payloads. */
internal object LegacyImageCompatibility {

    fun addExplicitClearMarkers(record: JsonObject) {
        if (isCompleteAndEmpty(record, "imageRefs", "imageRefsComplete")) {
            record.addProperty("image", "")
        }
        if (isCompleteAndEmpty(record, "noteImageRefs", "noteImageRefsComplete")) {
            record.add("noteImages", JsonArray())
        }
    }

    private fun isCompleteAndEmpty(
        record: JsonObject,
        refsField: String,
        completeField: String
    ): Boolean {
        val complete = record.get(completeField)
            ?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asBoolean }.getOrDefault(false) }
            ?: false
        val refs = record.get(refsField)?.takeIf { it.isJsonArray }?.asJsonArray
        return complete && refs?.size() == 0
    }
}

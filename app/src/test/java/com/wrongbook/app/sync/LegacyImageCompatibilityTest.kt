package com.wrongbook.app.sync

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyImageCompatibilityTest {

    @Test
    fun `complete empty canonical groups serialize explicit legacy clears`() {
        val record = canonicalRecord(imageComplete = true, noteImagesComplete = true)

        LegacyImageCompatibility.addExplicitClearMarkers(record)
        val serialized = JsonParser.parseString(Gson().toJson(record)).asJsonObject

        assertTrue(serialized.has("image"))
        assertEquals("", serialized.get("image").asString)
        assertTrue(serialized.has("noteImages"))
        assertEquals(0, serialized.getAsJsonArray("noteImages").size())
    }

    @Test
    fun `explicit legacy clears replace stale images in field preserving v1 merge`() {
        val stored = JsonObject().apply {
            addProperty("id", "q1")
            addProperty("image", "data:image/png;base64,stale-question")
            add("noteImages", JsonArray().apply {
                add("data:image/png;base64,stale-note")
            })
        }
        val incoming = canonicalRecord(imageComplete = true, noteImagesComplete = true)
        LegacyImageCompatibility.addExplicitClearMarkers(incoming)

        val merged = legacyFieldPreservingMerge(stored, incoming)

        assertEquals("", merged.get("image").asString)
        assertEquals(0, merged.getAsJsonArray("noteImages").size())
    }

    @Test
    fun `incomplete empty groups do not serialize legacy clears`() {
        val record = canonicalRecord(imageComplete = false, noteImagesComplete = false)

        LegacyImageCompatibility.addExplicitClearMarkers(record)

        assertFalse(record.has("image"))
        assertFalse(record.has("noteImages"))
    }

    private fun canonicalRecord(
        imageComplete: Boolean,
        noteImagesComplete: Boolean
    ): JsonObject = JsonObject().apply {
        addProperty("id", "q1")
        add("imageRefs", JsonArray())
        addProperty("imageRefsComplete", imageComplete)
        add("noteImageRefs", JsonArray())
        addProperty("noteImageRefsComplete", noteImagesComplete)
    }

    private fun legacyFieldPreservingMerge(stored: JsonObject, incoming: JsonObject): JsonObject =
        stored.deepCopy().apply {
            incoming.entrySet().forEach { (name, value) -> add(name, value.deepCopy()) }
        }
}

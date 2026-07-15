package com.wrongbook.app.model

import java.security.MessageDigest

/** Stable identities for legacy follow-up messages that predate the id field. */
object FollowUpChatIds {

    fun ensureStable(
        questionId: String,
        chats: List<FollowUpChat>,
        fallbackCreatedAt: Long
    ): List<FollowUpChat> = chats.mapIndexed { index, chat ->
        val createdAt = chat.createdAt.takeIf { it > 0L } ?: fallbackCreatedAt
        // Gson can hydrate a missing legacy field as null despite Kotlin's non-null declaration.
        val currentId: String? = chat.id
        val id = if (currentId == null || currentId.isBlank()) {
            legacyId(
                questionId = questionId,
                role = chat.role,
                content = chat.content,
                createdAt = createdAt,
                sourceIndex = index
            )
        } else {
            currentId
        }
        if (id == currentId && createdAt == chat.createdAt) chat else chat.copy(
            id = id,
            createdAt = createdAt
        )
    }

    fun legacyId(
        questionId: String,
        role: String,
        content: String,
        createdAt: Long,
        sourceIndex: Int
    ): String {
        val canonical = listOf(
            questionId,
            role,
            content,
            createdAt.toString(),
            sourceIndex.toString()
        ).joinToString(separator = "|") { value ->
            "${value.toByteArray(Charsets.UTF_8).size}:$value"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return "legacy-followup-${digest.toLowerHex()}"
    }

    private fun ByteArray.toLowerHex(): String {
        val alphabet = "0123456789abcdef"
        return buildString(size * 2) {
            this@toLowerHex.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(alphabet[value ushr 4])
                append(alphabet[value and 0x0f])
            }
        }
    }
}

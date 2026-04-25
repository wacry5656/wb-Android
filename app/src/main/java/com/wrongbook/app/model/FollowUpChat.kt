package com.wrongbook.app.model

import java.util.UUID

data class FollowUpChat(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

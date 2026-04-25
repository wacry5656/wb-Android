package com.wrongbook.app.model

object SubjectCatalog {
    val subjects: List<String> = listOf("数学", "物理", "化学", "生物")
    const val defaultSubject: String = "数学"

    fun isSupported(value: String): Boolean = value.trim() in subjects

    fun normalize(value: String): String {
        val trimmed = value.trim()
        return if (isSupported(trimmed)) trimmed else defaultSubject
    }
}

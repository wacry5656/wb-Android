package com.wrongbook.app.model

data class ImageRef(
    val id: String,
    val storage: String = "file",
    val kind: String = "question",
    val createdAt: Long = System.currentTimeMillis(),
    val mimeType: String? = null,
    val dataUrl: String? = null,
    val uri: String? = null
) {
    val displayUri: String
        get() = dataUrl ?: uri.orEmpty()

    val storageType: StorageType
        get() = if (storage == "inline") StorageType.INLINE else StorageType.URI
}

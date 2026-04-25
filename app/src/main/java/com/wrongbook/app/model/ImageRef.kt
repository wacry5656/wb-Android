package com.wrongbook.app.model

data class ImageRef(
    val id: String,
    val uri: String = "",
    val storageType: StorageType = StorageType.URI,
    val createdAt: Long = System.currentTimeMillis(),
    val dataUrl: String? = null,
    val storage: String? = null,
    val kind: String? = null,
    val mimeType: String? = null
) {
    val displayUri: String
        get() = dataUrl ?: uri
}

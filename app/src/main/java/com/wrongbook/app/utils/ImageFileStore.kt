package com.wrongbook.app.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.wrongbook.app.model.ImageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object ImageFileStore {
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    suspend fun importImage(
        context: Context,
        sourceUri: Uri,
        kind: String = "question"
    ): ImageRef =
        withContext(Dispatchers.IO) {
            val target = newImageFile(context)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取图片")
            target.toImageRef(context, kind)
        }

    fun createCameraImageRef(context: Context, kind: String = "question"): ImageRef =
        newImageFile(context).toImageRef(context, kind)

    suspend fun readImageDataUrl(context: Context, imageRef: ImageRef): String =
        withContext(Dispatchers.IO) {
            if (!imageRef.dataUrl.isNullOrBlank()) {
                return@withContext imageRef.dataUrl
            }
            if (imageRef.uri?.startsWith("data:image/") == true) {
                return@withContext imageRef.uri
            }
            val uriValue = imageRef.uri ?: error("图片引用缺少 uri")
            val uri = Uri.parse(uriValue)
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: error("无法读取图片")
            "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }

    private fun newImageFile(context: Context): File {
        val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "wrongbook")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "question_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
    }

    private fun File.toImageRef(context: Context, kind: String): ImageRef {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}$AUTHORITY_SUFFIX",
            this
        )
        return ImageRef(
            id = UUID.randomUUID().toString(),
            storage = "file",
            kind = kind,
            createdAt = System.currentTimeMillis(),
            uri = uri.toString()
        )
    }
}

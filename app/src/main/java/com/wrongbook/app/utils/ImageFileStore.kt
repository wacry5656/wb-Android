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
    private val dataUrlPattern = Regex("^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$")

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

    suspend fun importDataUrl(
        context: Context,
        dataUrl: String,
        kind: String,
        createdAt: Long,
        id: String? = null,
        mimeType: String? = null
    ): ImageRef =
        withContext(Dispatchers.IO) {
            val parsed = parseDataUrl(dataUrl, mimeType)
            val target = newImageFile(
                context = context,
                id = id,
                extension = extensionForMimeType(parsed.mimeType)
            )
            target.outputStream().use { output ->
                output.write(parsed.bytes)
            }
            target.toImageRef(
                context = context,
                kind = kind,
                createdAt = createdAt,
                id = id ?: UUID.randomUUID().toString(),
                mimeType = parsed.mimeType
            )
        }

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

    private fun newImageFile(
        context: Context,
        id: String? = null,
        extension: String = "jpg"
    ): File {
        val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "wrongbook")
        if (!dir.exists()) dir.mkdirs()
        val normalizedId = (id ?: UUID.randomUUID().toString()).replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "image_${normalizedId}_${System.currentTimeMillis()}.$extension")
    }

    private fun File.toImageRef(
        context: Context,
        kind: String,
        createdAt: Long = System.currentTimeMillis(),
        id: String = UUID.randomUUID().toString(),
        mimeType: String? = null
    ): ImageRef {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}$AUTHORITY_SUFFIX",
            this
        )
        return ImageRef(
            id = id,
            storage = "file",
            kind = kind,
            createdAt = createdAt,
            mimeType = mimeType,
            uri = uri.toString()
        )
    }

    private fun parseDataUrl(dataUrl: String, fallbackMimeType: String?): ParsedDataUrl {
        val match = dataUrlPattern.matchEntire(dataUrl.trim()) ?: error("无效的图片 dataUrl")
        val parsedMimeType = fallbackMimeType ?: match.groupValues[1]
        val bytes = Base64.decode(match.groupValues[2], Base64.DEFAULT)
        return ParsedDataUrl(
            mimeType = parsedMimeType,
            bytes = bytes
        )
    }

    private fun extensionForMimeType(mimeType: String): String = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/jpeg", "image/jpg" -> "jpg"
        else -> "bin"
    }

    private data class ParsedDataUrl(
        val mimeType: String,
        val bytes: ByteArray
    )
}

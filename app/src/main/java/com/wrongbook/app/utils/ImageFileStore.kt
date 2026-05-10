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
    private const val MAX_IMAGE_BYTES = 10L * 1024L * 1024L
    private val dataUrlPattern = Regex("^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$")
    private val supportedMimeTypes = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")

    suspend fun importImage(
        context: Context,
        sourceUri: Uri,
        kind: String = "question"
    ): ImageRef =
        withContext(Dispatchers.IO) {
            val mimeType = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
            validateMimeType(mimeType)
            val target = newImageFile(context, extension = extensionForMimeType(mimeType))
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    target.outputStream().use { output -> copyWithSizeLimit(input, output) }
                } ?: error("无法读取图片")
                target.toImageRef(context, kind, mimeType = mimeType)
            } catch (e: Exception) {
                target.delete()
                throw e
            }
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
            try {
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
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        }

    suspend fun readImageDataUrl(context: Context, imageRef: ImageRef): String =
        withContext(Dispatchers.IO) {
            if (!imageRef.dataUrl.isNullOrBlank()) {
                validateDataUrlSize(imageRef.dataUrl, imageRef.mimeType)
                return@withContext imageRef.dataUrl
            }
            if (imageRef.uri?.startsWith("data:image/") == true) {
                validateDataUrlSize(imageRef.uri, imageRef.mimeType)
                return@withContext imageRef.uri
            }
            val uriValue = imageRef.uri ?: error("图片引用缺少 uri")
            val uri = Uri.parse(uriValue)
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            validateMimeType(mimeType)
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: error("无法读取图片")
            validateImageSize(bytes.size.toLong())
            "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }

    suspend fun ensureImageIsSupported(context: Context, imageRef: ImageRef): ImageRef =
        withContext(Dispatchers.IO) {
            if (!imageRef.dataUrl.isNullOrBlank() || imageRef.uri?.startsWith("data:image/") == true) {
                validateDataUrlSize(imageRef.dataUrl ?: imageRef.uri.orEmpty(), imageRef.mimeType)
                return@withContext imageRef
            }

            val uriValue = imageRef.uri ?: error("图片引用缺少 uri")
            val uri = Uri.parse(uriValue)
            val mimeType = context.contentResolver.getType(uri) ?: imageRef.mimeType ?: "image/jpeg"
            validateMimeType(mimeType)
            val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            if (size >= 0) {
                validateImageSize(size)
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    copyWithSizeLimit(input, null)
                } ?: error("无法读取图片")
            }
            imageRef.copy(mimeType = imageRef.mimeType ?: mimeType)
        }

    private fun newImageFile(
        context: Context,
        id: String? = null,
        extension: String = "jpg"
    ): File {
        val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "wrongbook")
        if (!dir.exists()) dir.mkdirs()
        val normalizedId = (id ?: UUID.randomUUID().toString())
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .ifBlank { UUID.randomUUID().toString() }
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
        validateMimeType(parsedMimeType)
        val bytes = Base64.decode(match.groupValues[2], Base64.DEFAULT)
        validateImageSize(bytes.size.toLong())
        return ParsedDataUrl(
            mimeType = parsedMimeType,
            bytes = bytes
        )
    }

    private fun validateDataUrlSize(dataUrl: String, fallbackMimeType: String?) {
        parseDataUrl(dataUrl, fallbackMimeType)
    }

    private fun validateMimeType(mimeType: String) {
        if (mimeType.lowercase() !in supportedMimeTypes) {
            error("仅支持 JPG、PNG 或 WebP 图片")
        }
    }

    private fun validateImageSize(size: Long) {
        if (size > MAX_IMAGE_BYTES) {
            error("图片不能超过 10MB，请压缩后再试")
        }
    }

    private fun copyWithSizeLimit(
        input: java.io.InputStream,
        output: java.io.OutputStream?
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            validateImageSize(total)
            output?.write(buffer, 0, read)
        }
    }

    private fun extensionForMimeType(mimeType: String): String = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/jpeg", "image/jpg" -> "jpg"
        else -> "bin"
    }

fun getFileFromUri(context: Context, uri: String): File? {
        return try {
            val parsed = Uri.parse(uri)
            val filePath = getRealPathFromUri(context, parsed)
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) file else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getRealPathFromUri(context: Context, uri: Uri): String? {
        if (android.content.ContentResolver.SCHEME_FILE == uri.scheme) {
            return uri.path
        }
        val externalFilesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val pathSegments = uri.pathSegments
        val relativePath = pathSegments.lastOrNull() ?: return null
        val fullPath = "${externalFilesDir?.absolutePath}/wrongbook/$relativePath"
        return if (File(fullPath).exists()) fullPath else null
    }

    private data class ParsedDataUrl(
        val mimeType: String,
        val bytes: ByteArray
    )
}

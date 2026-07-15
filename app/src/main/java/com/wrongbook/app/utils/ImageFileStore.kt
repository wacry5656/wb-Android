package com.wrongbook.app.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.wrongbook.app.model.ImageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
            val imageId = UUID.randomUUID().toString()
            val target = newImageFile(
                context,
                id = imageId,
                extension = extensionForMimeType(mimeType)
            )
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    target.outputStream().use { output -> copyWithSizeLimit(input, output) }
                } ?: error("无法读取图片")
                target.toImageRef(
                    context,
                    kind,
                    id = imageId,
                    mimeType = mimeType,
                    contentHash = sha256(target.readBytes())
                )
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        }

    fun createCameraImageRef(context: Context, kind: String = "question"): ImageRef {
        val imageId = UUID.randomUUID().toString()
        return newImageFile(context, id = imageId).toImageRef(context, kind, id = imageId)
    }

    suspend fun importDataUrl(
        context: Context,
        dataUrl: String,
        kind: String,
        createdAt: Long,
        id: String? = null,
        mimeType: String? = null,
        expectedContentHash: String? = null
    ): ImageRef =
        withContext(Dispatchers.IO) {
            val parsed = parseDataUrl(dataUrl, mimeType)
            val contentHash = sha256(parsed.bytes)
            normalizeContentHash(expectedContentHash)?.let { expected ->
                require(expected == contentHash) { "图片内容校验失败（SHA-256 不匹配）" }
            }
            val target = newImageFile(
                context = context,
                id = id,
                extension = extensionForMimeType(parsed.mimeType),
                contentHash = contentHash
            )
            try {
                repairFileIfNeeded(target, parsed.bytes, contentHash)
                target.toImageRef(
                    context = context,
                    kind = kind,
                    createdAt = createdAt,
                    id = id ?: UUID.randomUUID().toString(),
                    mimeType = parsed.mimeType,
                    contentHash = contentHash
                )
            } catch (e: Exception) {
                // The deterministic target may already contain a verified good remote image.
                // Leave it for reuse/garbage collection if URI creation fails.
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
        extension: String = "jpg",
        contentHash: String? = null
    ): File {
        val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "wrongbook")
        if (!dir.exists()) dir.mkdirs()
        val normalizedId = (id ?: UUID.randomUUID().toString())
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .ifBlank { UUID.randomUUID().toString() }
        val uniqueSuffix = contentHash?.take(20) ?: UUID.randomUUID().toString().replace("-", "")
        return File(dir, "image_${normalizedId}_$uniqueSuffix.$extension")
    }

    private fun File.toImageRef(
        context: Context,
        kind: String,
        createdAt: Long = System.currentTimeMillis(),
        id: String = UUID.randomUUID().toString(),
        mimeType: String? = null,
        contentHash: String? = null
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
            contentHash = contentHash,
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

    fun contentHashForDataUrl(dataUrl: String, fallbackMimeType: String? = null): String =
        sha256(parseDataUrl(dataUrl, fallbackMimeType).bytes)

    fun normalizeContentHash(value: String?): String? {
        val normalized = value?.trim()?.lowercase()?.removePrefix("sha256:") ?: return null
        return normalized.takeIf { it.matches(Regex("^[0-9a-f]{64}$")) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    internal fun fileMatchesHash(file: File, expectedHash: String): Boolean =
        file.isFile && runCatching { sha256(file.readBytes()) == expectedHash }.getOrDefault(false)

    internal fun repairFileIfNeeded(file: File, bytes: ByteArray, expectedHash: String): Boolean {
        require(sha256(bytes) == expectedHash) { "待写入图片与 SHA-256 不一致" }
        if (fileMatchesHash(file, expectedHash)) return false
        writeAtomically(file, bytes)
        return true
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            temp.outputStream().use { output -> output.write(bytes) }
            runCatching {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            }.getOrElse {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }

    fun deleteOrphanedFiles(
        context: Context,
        retainedRefs: List<ImageRef>,
        gracePeriodMs: Long = 60L * 60L * 1000L
    ): Int {
        val imageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            ?.let { File(it, "wrongbook") } ?: return 0
        val retainedPaths = retainedRefs.mapNotNull { ref ->
            ref.uri?.let(Uri::parse)?.let { uri -> getRealPathFromUri(context, uri) }
        }.map { File(it).absolutePath }.toSet()
        val cutoff = System.currentTimeMillis() - gracePeriodMs.coerceAtLeast(0L)
        return imageDir.listFiles().orEmpty().count { file ->
            shouldDeleteOrphan(file, retainedPaths, cutoff) && file.delete()
        }
    }

    internal fun shouldDeleteOrphan(
        file: File,
        retainedPaths: Set<String>,
        cutoff: Long
    ): Boolean = file.isFile && file.absolutePath !in retainedPaths && file.lastModified() <= cutoff
}

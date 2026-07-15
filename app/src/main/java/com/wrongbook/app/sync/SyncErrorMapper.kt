package com.wrongbook.app.sync

import com.google.gson.JsonParser

internal object SyncErrorMapper {
    fun message(code: Int, body: String): String {
        if (code == 401 || code == 403) {
            return "VPS 同步密钥无效或无权限，请检查 SYNC_TOKEN"
        }
        if (code == 413) {
            val maxBytes = runCatching {
                JsonParser.parseString(body).asJsonObject
                    .get("maxRequestBytes")?.asLong ?: 0L
            }.getOrDefault(0L)
            val suffix = if (maxBytes > 0L) "（上限 ${formatBytes(maxBytes)}）" else ""
            return "VPS 拒绝了过大的同步批次$suffix，请压缩单题图片后重试"
        }
        return "同步接口返回错误 $code: ${body.take(200)}"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

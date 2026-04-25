package com.wrongbook.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

object ImageUriUtils {

    fun persistReadPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun canRead(context: Context, uri: String): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri.toUri())?.use { true } ?: false
        }.getOrDefault(false)
    }
}

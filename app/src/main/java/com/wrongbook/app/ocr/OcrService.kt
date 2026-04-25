package com.wrongbook.app.ocr

import android.content.Context
import androidx.core.net.toUri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OcrService(private val context: Context) {

    suspend fun recognize(uriString: String): String {
        val image = InputImage.fromFilePath(context, uriString.toUri())
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        return try {
            recognizer.process(image).await().text.trim()
        } finally {
            recognizer.close()
        }
    }
}

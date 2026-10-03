package com.vx.anymaker.core.ml

import android.graphics.Bitmap
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vx.anymaker.core.image.ImageOpException
import kotlinx.coroutines.tasks.await

/** Writing systems the on-device text recognizer reads. */
enum class TextScript { LATIN, DEVANAGARI, CHINESE, JAPANESE, KOREAN }

/** Thin coroutine wrappers over ML Kit that turn its failures into messages users can act on. */
object MlKit {

    suspend fun recognizeText(bitmap: Bitmap, script: TextScript): String {
        val client = when (script) {
            TextScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            TextScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            TextScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            TextScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            TextScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
        return client.use { guard { it.process(InputImage.fromBitmap(bitmap, 0)).await().text } }
    }

    suspend fun scanBarcodes(bitmap: Bitmap): List<Barcode> =
        BarcodeScanning.getClient().use { guard { it.process(InputImage.fromBitmap(bitmap, 0)).await() } }

    /** The photo's main subjects on a transparent background. */
    suspend fun cutOut(bitmap: Bitmap): Bitmap {
        val options = SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        return SubjectSegmentation.getClient(options).use { client ->
            guard { client.process(InputImage.fromBitmap(bitmap, 0)).await().foregroundBitmap }
                ?: throw ImageOpException("No person or object found in this photo.")
        }
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: MlKitException) {
        throw when (e.errorCode) {
            MlKitException.UNAVAILABLE -> ImageOpException(
                "The on-device model is still downloading. Connect to the internet once, wait a moment and try again.", e,
            )
            else -> ImageOpException("Recognition failed: ${e.message}", e)
        }
    }
}

package com.screentextcopier

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Processes screen-captured bitmaps through ML Kit Text Recognition to extract text.
 */
class OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    /**
     * Runs OCR on the provided bitmap.
     *
     * @param bitmap The captured screen image.
     * @param onResult Called with the recognized text (may be empty if nothing found).
     * @param onError Called if OCR processing fails.
     */
    fun processImage(
        bitmap: Bitmap,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                onResult(visionText.text)
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    fun close() {
        recognizer.close()
    }
}

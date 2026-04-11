package com.screentextcopier.model

/**
 * Represents a single item in the clipboard history.
 *
 * @param id Unique identifier for this item.
 * @param text The OCR-recognized text content (null if image-only).
 * @param imagePath File path to the captured screenshot thumbnail (null if text-only).
 * @param timestamp Unix timestamp in milliseconds when this item was captured.
 */
data class ClipboardItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String? = null,
    val imagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Returns a short preview of the text, useful for list display. */
    fun textPreview(maxLength: Int = 120): String {
        return when {
            text.isNullOrBlank() -> "[Image]"
            text.length <= maxLength -> text
            else -> text.take(maxLength) + "…"
        }
    }
}

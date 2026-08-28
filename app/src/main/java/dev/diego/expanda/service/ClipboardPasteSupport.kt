package dev.diego.expanda.service

internal const val CLIPBOARD_PASTE_MARKER = "\u2063\u2062\u2063\u2062"

/**
 * Replaces every occurrence of [CLIPBOARD_PASTE_MARKER] in [text] with
 * [clipboardText] and adjusts [cursor] accordingly.
 */
internal fun substituteClipboardMarkers(
    text: String,
    cursor: Int,
    clipboardText: String,
): Pair<String, Int> {
    if (!text.contains(CLIPBOARD_PASTE_MARKER)) {
        return text to cursor.coerceIn(0, text.length)
    }

    val output = StringBuilder(text.length)
    var sourceIndex = 0
    var adjustedCursor = cursor

    while (true) {
        val markerIndex = text.indexOf(CLIPBOARD_PASTE_MARKER, startIndex = sourceIndex)
        if (markerIndex < 0) break

        output.append(text, sourceIndex, markerIndex)

        val markerEnd = markerIndex + CLIPBOARD_PASTE_MARKER.length
        if (adjustedCursor >= markerEnd) {
            adjustedCursor += clipboardText.length - CLIPBOARD_PASTE_MARKER.length
        }

        output.append(clipboardText)
        sourceIndex = markerEnd
    }

    output.append(text, sourceIndex, text.length)
    return output.toString() to adjustedCursor.coerceIn(0, output.length)
}

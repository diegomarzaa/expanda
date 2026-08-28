package dev.diego.expanda.ui.suggestion

data class SuggestionResizeResult(
    val widthPx: Int,
    val listHeightPx: Int,
)

/** Pure geometry for the popup's bottom-left diagonal resize control. */
object SuggestionResizePolicy {
    fun resize(
        startWidthPx: Int,
        startListHeightPx: Int,
        horizontalDragPx: Int,
        verticalDragPx: Int,
        minWidthPx: Int,
        maxWidthPx: Int,
        minListHeightPx: Int,
        maxListHeightPx: Int,
    ): SuggestionResizeResult = SuggestionResizeResult(
        // The control is on the left: dragging left makes the popup wider.
        widthPx = (startWidthPx - horizontalDragPx).coerceIn(minWidthPx, maxWidthPx),
        // The popup remains bottom-anchored: dragging up reduces it from the top.
        listHeightPx = (startListHeightPx + verticalDragPx).coerceIn(
            minListHeightPx,
            maxListHeightPx,
        ),
    )
}

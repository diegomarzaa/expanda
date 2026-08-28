package dev.diego.expanda.ui.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionResizePolicyTest {
    @Test fun `up-left drag grows width but reduces bottom-anchored height`() {
        val result = SuggestionResizePolicy.resize(
            startWidthPx = 700,
            startListHeightPx = 500,
            horizontalDragPx = -80,
            verticalDragPx = -120,
            minWidthPx = 300,
            maxWidthPx = 1_000,
            minListHeightPx = 240,
            maxListHeightPx = 1_200,
        )

        assertEquals(SuggestionResizeResult(widthPx = 780, listHeightPx = 380), result)
    }

    @Test fun `down-right drag shrinks width and grows height within bounds`() {
        val result = SuggestionResizePolicy.resize(
            startWidthPx = 700,
            startListHeightPx = 500,
            horizontalDragPx = 900,
            verticalDragPx = 900,
            minWidthPx = 300,
            maxWidthPx = 1_000,
            minListHeightPx = 240,
            maxListHeightPx = 1_200,
        )

        assertEquals(SuggestionResizeResult(widthPx = 300, listHeightPx = 1_200), result)
    }

    @Test fun `upward drag clamps height at its minimum`() {
        val result = SuggestionResizePolicy.resize(
            startWidthPx = 700,
            startListHeightPx = 500,
            horizontalDragPx = 0,
            verticalDragPx = -900,
            minWidthPx = 300,
            maxWidthPx = 1_000,
            minListHeightPx = 240,
            maxListHeightPx = 1_200,
        )

        assertEquals(SuggestionResizeResult(widthPx = 700, listHeightPx = 240), result)
    }
}

package dev.diego.expanda.service

import dev.diego.expanda.data.MatchTrigger
import dev.diego.expanda.data.TextMatch
import dev.diego.expanda.engine.ExpansionMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionUndoPolicyTest {
    private val anchor = SuggestionAnchor(
        packageName = "com.editor",
        windowId = 4,
        uniqueId = "message",
        viewId = "com.editor:id/message",
        className = "android.widget.EditText",
        bounds = AnchorGeometry(0, 0, 400, 80),
    )
    private val state = ReversibleExpansion(
        anchor = anchor,
        appliedText = "Before expanded text ",
        appliedCursor = 21,
        restoredText = "Before ;x-basic",
        restoredCursor = 15,
        matchId = 7,
        matchedText = ";x-basic",
    )

    @Test fun `backspace at expansion cursor restores trigger`() {
        assertEquals(
            ExpansionUndoDecision.Restore,
            ExpansionUndoPolicy.backspaceDecision(anchor = anchor, text = "Before expanded text", cursor = 20),
        )
    }

    @Test fun `own accessibility update keeps undo available`() {
        assertEquals(
            ExpansionUndoDecision.Keep,
            ExpansionUndoPolicy.backspaceDecision(anchor = anchor, text = state.appliedText, cursor = state.appliedText.length),
        )
    }

    @Test fun `unrelated edit or editor clears undo`() {
        assertEquals(
            ExpansionUndoDecision.Clear,
            ExpansionUndoPolicy.backspaceDecision(anchor = anchor, text = "Before expanded text!", cursor = 21),
        )
        assertEquals(
            ExpansionUndoDecision.Clear,
            ExpansionUndoPolicy.backspaceDecision(
                anchor = anchor.copy(windowId = 5),
                text = "Before expanded text",
                cursor = 20,
            ),
        )
    }

    @Test fun `resized multiline editor keeps undo armed`() {
        val resized = anchor.copy(
            uniqueId = null,
            viewId = null,
            bounds = AnchorGeometry(0, 0, 400, 240),
        )
        val withoutStableIds = state.copy(anchor = anchor.copy(uniqueId = null, viewId = null))

        assertEquals(
            ExpansionUndoDecision.Restore,
            ExpansionUndoPolicy.backspaceDecision(
                withoutStableIds,
                resized,
                "Before expanded text",
                20,
            ),
        )
    }

    @Test fun `first delimiter after undo is suppressed exactly once`() {
        val match = ExpansionMatch(
            match = TextMatch(
                id = 7,
                triggers = listOf(MatchTrigger(";x-basic")),
                replacements = listOf("expanded text"),
            ),
            replaceFrom = 7,
            replaceTo = 16,
            trailingDelimiter = " ",
            matchedText = ";x-basic",
        )

        assertTrue(ExpansionUndoPolicy.isRestoredText(state, anchor, "Before ;x-basic"))
        assertTrue(ExpansionUndoPolicy.shouldSuppressDelimiterExpansion(state, anchor, "Before ;x-basic ", 16, match))
        assertFalse(ExpansionUndoPolicy.shouldSuppressDelimiterExpansion(state, anchor, "Before ;x-basic,", 16, match))
    }

    private fun ExpansionUndoPolicy.backspaceDecision(
        anchor: SuggestionAnchor,
        text: String,
        cursor: Int,
    ) = backspaceDecision(state, anchor, text, cursor)
}

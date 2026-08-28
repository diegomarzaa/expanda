package dev.diego.expanda.service

import dev.diego.expanda.engine.ExpansionMatch

data class ReversibleExpansion(
    val anchor: SuggestionAnchor,
    val appliedText: String,
    val appliedCursor: Int,
    val restoredText: String,
    val restoredCursor: Int,
    val matchId: Long,
    val matchedText: String,
)

sealed interface ExpansionUndoDecision {
    data object Keep : ExpansionUndoDecision
    data object Clear : ExpansionUndoDecision
    data object Restore : ExpansionUndoDecision
}

/** Pure decisions for the one-step, Espanso-style expansion undo. */
object ExpansionUndoPolicy {
    fun backspaceDecision(
        state: ReversibleExpansion,
        activeAnchor: SuggestionAnchor,
        text: String,
        cursor: Int,
    ): ExpansionUndoDecision {
        if (!sameEditor(state.anchor, activeAnchor)) {
            return ExpansionUndoDecision.Clear
        }
        // Accessibility may report ACTION_SET_TEXT before its selection update.
        if (text == state.appliedText) return ExpansionUndoDecision.Keep
        if (state.appliedCursor !in 1..state.appliedText.length || cursor !in 0 until state.appliedCursor) {
            return ExpansionUndoDecision.Clear
        }
        val deletedImmediatelyBeforeCursor = text == state.appliedText.removeRange(cursor, state.appliedCursor)
        return if (deletedImmediatelyBeforeCursor) ExpansionUndoDecision.Restore else ExpansionUndoDecision.Clear
    }

    fun isRestoredText(
        state: ReversibleExpansion,
        activeAnchor: SuggestionAnchor,
        text: String,
    ): Boolean = sameEditor(state.anchor, activeAnchor) && text == state.restoredText

    fun shouldSuppressDelimiterExpansion(
        state: ReversibleExpansion,
        activeAnchor: SuggestionAnchor,
        text: String,
        cursor: Int,
        match: ExpansionMatch,
    ): Boolean {
        if (!sameEditor(state.anchor, activeAnchor)) return false
        val delimiter = match.trailingDelimiter
        if (delimiter.isEmpty() || cursor != state.restoredCursor + delimiter.length) return false
        if (match.match.id != state.matchId || match.matchedText != state.matchedText) return false
        if (state.restoredCursor !in 0..state.restoredText.length) return false
        val expected = state.restoredText.substring(0, state.restoredCursor) +
            delimiter + state.restoredText.substring(state.restoredCursor)
        return text == expected
    }

    private fun sameEditor(expected: SuggestionAnchor, active: SuggestionAnchor): Boolean {
        if (expected.packageName != active.packageName || expected.windowId != active.windowId) return false
        if (!expected.uniqueId.isNullOrBlank() && !active.uniqueId.isNullOrBlank()) {
            return expected.uniqueId == active.uniqueId
        }
        if (!expected.viewId.isNullOrBlank() && !active.viewId.isNullOrBlank()) {
            return expected.viewId == active.viewId && expected.className == active.className
        }
        // Expansion can resize a multiline editor, so geometry is not stable enough
        // for undo. Exact before/after text matching still prevents cross-field restores.
        return expected.className == active.className
    }
}

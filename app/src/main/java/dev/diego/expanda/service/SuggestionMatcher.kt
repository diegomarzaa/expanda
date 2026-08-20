package dev.diego.expanda.service

/**
 * Pure matching rules shared by the accessibility overlay and JVM tests.
 *
 * Keeping this separate from Android views makes it harder for an overlay refresh
 * to accidentally show a one-character match or highlight a different substring
 * than the one used for filtering.
 */
internal object SuggestionMatcher {
    fun canShow(token: String, minimumCharacters: Int): Boolean =
        token.length >= minimumCharacters.coerceIn(1, 32)

    /** Returns the inclusive range that should be highlighted in [shortcut]. */
    fun matchRange(
        shortcut: String,
        token: String,
        fromBeginning: Boolean,
    ): IntRange? {
        if (token.isEmpty()) return null
        if (fromBeginning && !shortcut.startsWith(token, ignoreCase = true)) return null
        val start = shortcut.indexOf(token, ignoreCase = true)
        return if (start >= 0) start..(start + token.length - 1) else null
    }
}

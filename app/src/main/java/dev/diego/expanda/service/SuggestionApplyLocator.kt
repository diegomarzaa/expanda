package dev.diego.expanda.service

/**
 * Locates the range in an editor that a tapped suggestion should replace.
 *
 * When the user taps a snippet in the suggestion popup, we need to decide which
 * part of the current text to replace with the rendered snippet. Ideally this is
 * the prefix they've typed (`;ec` → the range `[cursor-3, cursor)`), so the
 * partial trigger disappears when we insert the replacement.
 *
 * Two things make this tricky:
 * - The accessibility node cache can be stale, so `node.text` may report the
 *   pre-typing value. We refresh the node before reading, but even then some
 *   editors (WebViews) coalesce updates and report old text.
 * - The user may have kept typing after the popup appeared, so `typed` might
 *   be longer than the trigger, contain a delimiter, or match nothing at all.
 *
 * Rather than silently giving up (which is what caused "popup disappears without
 * doing anything"), we fall back to inserting the full trigger at the cursor.
 * That preserves the user's intent even when the text state is not exactly what
 * the popup was built for.
 */
internal object SuggestionApplyLocator {

    data class Range(val start: Int, val end: Int, val matchedText: String)

    /**
     * Where a tapped [trigger] should be applied given the editor's [text] and
     * [cursor]. Returns null only when the cursor is genuinely invalid; otherwise
     * always returns a range so the caller never silently drops the tap.
     *
     * @param browseMode true when the popup is in "show all" mode: the user
     *   didn't type a prefix, so we just insert the trigger at the caret.
     */
    fun locate(
        text: String,
        cursor: Int,
        trigger: String,
        browseMode: Boolean,
    ): Range? {
        if (cursor !in 0..text.length) return null
        if (browseMode || trigger.isEmpty()) {
            return Range(cursor, cursor, trigger)
        }
        val before = text.substring(0, cursor)
        // Longest prefix of the trigger that ends at the cursor. Covers the
        // common case (`;ec` for trigger `;echo`) plus the "user finished typing
        // the whole trigger" case (`;echo` for trigger `;echo`).
        val maxPrefix = minOf(trigger.length, before.length)
        for (length in maxPrefix downTo 1) {
            val candidate = trigger.substring(0, length)
            if (before.endsWith(candidate)) {
                return Range(cursor - length, cursor, candidate)
            }
        }
        // No prefix of the trigger is at the cursor — either the accessibility
        // cache is stale or the user moved past the token. Fall back to
        // inserting the whole trigger; better than swallowing the tap silently.
        return Range(cursor, cursor, trigger)
    }
}

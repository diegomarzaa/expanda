package dev.diego.expanda.service

/**
 * Reads a fresh text + cursor snapshot from a [android.view.accessibility.AccessibilityEvent]
 * of type TYPE_VIEW_TEXT_CHANGED, falling back to the node's cached values when the event
 * does not carry enough information.
 *
 * Why we need this:
 * The Accessibility framework has a per-service cache of node info. WebView-based editors
 * (Gmail composer, Chrome, Obsidian, etc.) throttle content-changed events, which leaves
 * that cache stale for hundreds of milliseconds. Calling `node.text` right after the user
 * typed `;tim` can still return `;t`, so any matching we do on it fires against outdated
 * state and the popup highlights the wrong trigger.
 *
 * The event itself is delivered fresh: [android.view.accessibility.AccessibilityEvent.getText]
 * carries the field's post-change text, and `fromIndex + addedCount - removedCount` describes
 * the caret position after the edit. We prefer those, and only fall back to the (possibly
 * stale) node when the event lacks them (for example, on TYPE_VIEW_FOCUSED validations).
 */
internal object AccessibilityTextSnapshot {

    /**
     * Returns the best text snapshot for a text-changed event.
     *
     * @param eventTexts first entry of `event.getText()`; typically the full field text after the change.
     * @param nodeText   `node.text` (may be stale from the framework cache).
     * @param showingHint whether the field is showing its placeholder / hint; treated as empty content.
     */
    fun text(
        eventTexts: CharSequence?,
        nodeText: CharSequence?,
        showingHint: Boolean,
    ): String? {
        if (showingHint) return ""
        val fromEvent = eventTexts?.toString()
        if (!fromEvent.isNullOrEmpty()) return fromEvent
        val fromNode = nodeText?.toString()
        if (!fromNode.isNullOrEmpty()) return fromNode
        // Field is genuinely empty (e.g. user deleted everything); either source confirms it.
        if (fromEvent != null || fromNode != null) return ""
        return null
    }

    /**
     * Returns the caret position right after the edit described by the event.
     * Prefers `fromIndex + addedCount` (accurate for inserts, always fresh), falls back
     * to the node's [nodeSelectionEnd] when the event does not carry index information.
     */
    fun cursor(
        text: String,
        eventFromIndex: Int,
        eventAddedCount: Int,
        eventRemovedCount: Int,
        nodeSelectionEnd: Int,
    ): Int {
        val length = text.length
        if (eventFromIndex >= 0 && (eventAddedCount > 0 || eventRemovedCount > 0)) {
            val delta = if (eventAddedCount >= 0) eventAddedCount else 0
            val end = eventFromIndex + delta
            if (end in 0..length) return end
        }
        return nodeSelectionEnd.takeIf { it in 0..length } ?: length
    }
}

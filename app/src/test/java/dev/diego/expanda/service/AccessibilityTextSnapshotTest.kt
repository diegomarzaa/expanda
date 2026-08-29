package dev.diego.expanda.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityTextSnapshotTest {

    @Test fun `event text wins over stale node text`() {
        // Gmail composer scenario: user typed ";tim" but the node cache still says ";t".
        val text = AccessibilityTextSnapshot.text(
            eventTexts = ";tim",
            nodeText = ";t",
            showingHint = false,
        )
        assertEquals(";tim", text)
    }

    @Test fun `falls back to node text when event carries nothing`() {
        val text = AccessibilityTextSnapshot.text(
            eventTexts = null,
            nodeText = "hello world",
            showingHint = false,
        )
        assertEquals("hello world", text)
    }

    @Test fun `hint is never treated as content`() {
        val text = AccessibilityTextSnapshot.text(
            eventTexts = "Type here",
            nodeText = "Type here",
            showingHint = true,
        )
        assertEquals("", text)
    }

    @Test fun `empty field reports empty content`() {
        val text = AccessibilityTextSnapshot.text(
            eventTexts = "",
            nodeText = "",
            showingHint = false,
        )
        assertEquals("", text)
    }

    @Test fun `no signal at all returns null`() {
        val text = AccessibilityTextSnapshot.text(
            eventTexts = null,
            nodeText = null,
            showingHint = false,
        )
        assertNull(text)
    }

    @Test fun `cursor uses event fromIndex plus addedCount when consistent`() {
        // Typed ";" then "t" then "i" then "m" — this event reports the "m" append.
        val cursor = AccessibilityTextSnapshot.cursor(
            text = ";tim",
            eventFromIndex = 3,
            eventAddedCount = 1,
            eventRemovedCount = 0,
            nodeSelectionEnd = 2, // stale caret from the cache
        )
        assertEquals(4, cursor)
    }

    @Test fun `cursor falls back to node selection when event lacks indices`() {
        val cursor = AccessibilityTextSnapshot.cursor(
            text = "hello",
            eventFromIndex = -1,
            eventAddedCount = 0,
            eventRemovedCount = 0,
            nodeSelectionEnd = 3,
        )
        assertEquals(3, cursor)
    }

    @Test fun `cursor clamps to text length when node reports garbage`() {
        val cursor = AccessibilityTextSnapshot.cursor(
            text = "hi",
            eventFromIndex = -1,
            eventAddedCount = 0,
            eventRemovedCount = 0,
            nodeSelectionEnd = 999,
        )
        assertEquals(2, cursor)
    }

    @Test fun `cursor handles deletion via removedCount`() {
        // User deleted 2 chars at position 3 -> caret should sit at 3, not 5.
        val cursor = AccessibilityTextSnapshot.cursor(
            text = "hey",
            eventFromIndex = 3,
            eventAddedCount = 0,
            eventRemovedCount = 2,
            nodeSelectionEnd = 3,
        )
        assertEquals(3, cursor)
    }

    @Test fun `cursor ignores event indices that fall outside the fresh text`() {
        // Coalesced event reports an old index that no longer fits the new text length.
        val cursor = AccessibilityTextSnapshot.cursor(
            text = "hi",
            eventFromIndex = 10,
            eventAddedCount = 1,
            eventRemovedCount = 0,
            nodeSelectionEnd = 2,
        )
        assertEquals(2, cursor)
    }
}

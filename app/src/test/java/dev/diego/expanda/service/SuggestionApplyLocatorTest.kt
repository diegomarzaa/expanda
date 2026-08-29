package dev.diego.expanda.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionApplyLocatorTest {

    @Test fun `partial prefix maps to the typed range`() {
        // User typed ";ec" and tapped ";echo".
        val range = SuggestionApplyLocator.locate(
            text = ";ec",
            cursor = 3,
            trigger = ";echo",
            browseMode = false,
        )
        assertEquals(SuggestionApplyLocator.Range(0, 3, ";ec"), range)
    }

    @Test fun `complete trigger maps to the full range`() {
        val range = SuggestionApplyLocator.locate(
            text = ";echo",
            cursor = 5,
            trigger = ";echo",
            browseMode = false,
        )
        assertEquals(SuggestionApplyLocator.Range(0, 5, ";echo"), range)
    }

    @Test fun `trigger prefix inside longer text is picked up`() {
        // User typed "hello ;ec" — the prefix ";ec" of trigger ";echo" is at
        // the cursor, so we only replace the last three chars.
        val range = SuggestionApplyLocator.locate(
            text = "hello ;ec",
            cursor = 9,
            trigger = ";echo",
            browseMode = false,
        )
        assertEquals(SuggestionApplyLocator.Range(6, 9, ";ec"), range)
    }

    @Test fun `browse mode inserts trigger at cursor without replacing anything`() {
        val range = SuggestionApplyLocator.locate(
            text = "hello ",
            cursor = 6,
            trigger = ";echo",
            browseMode = true,
        )
        assertEquals(SuggestionApplyLocator.Range(6, 6, ";echo"), range)
    }

    @Test fun `no prefix at cursor falls back to inserting the trigger`() {
        // Cache is stale or user moved the caret past a whitespace: we no
        // longer see the trigger prefix but still want to honour the tap.
        val range = SuggestionApplyLocator.locate(
            text = "hello world",
            cursor = 11,
            trigger = ";echo",
            browseMode = false,
        )
        assertEquals(SuggestionApplyLocator.Range(11, 11, ";echo"), range)
    }

    @Test fun `invalid cursor is rejected`() {
        assertNull(
            SuggestionApplyLocator.locate(
                text = "hi",
                cursor = -1,
                trigger = ";echo",
                browseMode = false,
            ),
        )
        assertNull(
            SuggestionApplyLocator.locate(
                text = "hi",
                cursor = 5,
                trigger = ";echo",
                browseMode = false,
            ),
        )
    }

    @Test fun `empty trigger degrades to a caret insert`() {
        val range = SuggestionApplyLocator.locate(
            text = "hi",
            cursor = 2,
            trigger = "",
            browseMode = false,
        )
        assertEquals(SuggestionApplyLocator.Range(2, 2, ""), range)
    }
}

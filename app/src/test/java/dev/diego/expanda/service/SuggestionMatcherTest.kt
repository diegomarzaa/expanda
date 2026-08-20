package dev.diego.expanda.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionMatcherTest {
    @Test
    fun `minimum characters defaults to a meaningful token`() {
        assertFalse(SuggestionMatcher.canShow("/", 2))
        assertTrue(SuggestionMatcher.canShow("/e", 2))
        assertTrue(SuggestionMatcher.canShow("/", 1))
    }

    @Test
    fun `beginning matching highlights the typed token`() {
        assertEquals(0..1, SuggestionMatcher.matchRange("/ee", "/e", fromBeginning = true))
        assertNull(SuggestionMatcher.matchRange("hello/ee", "/e", fromBeginning = true))
    }

    @Test
    fun `contains matching highlights the actual coincidence`() {
        assertEquals(5..6, SuggestionMatcher.matchRange("helloEE", "ee", fromBeginning = false))
    }
}

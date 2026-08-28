package dev.diego.expanda.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetEditorOptionsTest {
    @Test
    fun `selection stays hidden until two replacements contain text`() {
        assertFalse(hasMultipleConfiguredReplacements(listOf("")))
        assertFalse(hasMultipleConfiguredReplacements(listOf("first", "")))
        assertFalse(hasMultipleConfiguredReplacements(listOf("first", "  \n")))
        assertTrue(hasMultipleConfiguredReplacements(listOf("first", "second")))
    }

    @Test
    fun `configured replacements do not need to be adjacent`() {
        assertTrue(hasMultipleConfiguredReplacements(listOf("first", "", "third")))
    }
}

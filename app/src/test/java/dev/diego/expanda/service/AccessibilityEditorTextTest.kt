package dev.diego.expanda.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityEditorTextTest {
    @Test fun `hint text is never treated as editor content`() {
        assertEquals("", AccessibilityEditorText.content(true, "Try ;hello, paste text…"))
    }

    @Test fun `real editor content is preserved`() {
        assertEquals("Hello", AccessibilityEditorText.content(false, "Hello"))
    }
}

package dev.diego.expanda.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardPasteSupportTest {
    @Test
    fun `substitute replaces markers and adjusts cursor after marker`() {
        val text = "Clipboard: ${CLIPBOARD_PASTE_MARKER}!"
        val (resolved, cursor) = substituteClipboardMarkers(text, cursor = text.length, clipboardText = "hello")
        assertEquals("Clipboard: hello!", resolved)
        assertEquals(resolved.length, cursor)
    }

    @Test
    fun `substitute keeps cursor before marker unchanged`() {
        val text = "Clipboard: $CLIPBOARD_PASTE_MARKER"
        val cursorBefore = "Clipboard: ".length
        val (resolved, cursor) = substituteClipboardMarkers(text, cursor = cursorBefore, clipboardText = "x")
        assertEquals("Clipboard: x", resolved)
        assertEquals(cursorBefore, cursor)
    }

    @Test
    fun `substitute handles multiple markers`() {
        val text = "${CLIPBOARD_PASTE_MARKER} and ${CLIPBOARD_PASTE_MARKER}"
        val (resolved, _) = substituteClipboardMarkers(text, cursor = text.length, clipboardText = "AB")
        assertEquals("AB and AB", resolved)
    }

    @Test
    fun `no markers returns text unchanged`() {
        val (resolved, cursor) = substituteClipboardMarkers("plain text", cursor = 5, clipboardText = "x")
        assertEquals("plain text", resolved)
        assertEquals(5, cursor)
    }
}

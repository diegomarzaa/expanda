package dev.diego.expanda.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewEditorDetectionTest {

    @Test fun `android EditText and its subclasses are native`() {
        assertTrue(WebViewEditorDetection.isNativeEditorClass("android.widget.EditText"))
        assertTrue(WebViewEditorDetection.isNativeEditorClass("androidx.appcompat.widget.AppCompatEditText"))
        assertTrue(WebViewEditorDetection.isNativeEditorClass("com.google.android.material.textfield.TextInputEditText"))
        assertTrue(WebViewEditorDetection.isNativeEditorClass("com.whatsapp.EntryEditText"))
    }

    @Test fun `autocomplete variants are native`() {
        assertTrue(WebViewEditorDetection.isNativeEditorClass("android.widget.AutoCompleteTextView"))
        assertTrue(WebViewEditorDetection.isNativeEditorClass("android.widget.MultiAutoCompleteTextView"))
        assertTrue(
            WebViewEditorDetection.isNativeEditorClass(
                "com.google.android.material.textfield.MaterialAutoCompleteTextView",
            ),
        )
    }

    @Test fun `plain view class is not native`() {
        // Chromium exposes editable HTML inputs as android.view.View nodes.
        assertFalse(WebViewEditorDetection.isNativeEditorClass("android.view.View"))
    }

    @Test fun `null or empty class name is not native`() {
        assertFalse(WebViewEditorDetection.isNativeEditorClass(null))
        assertFalse(WebViewEditorDetection.isNativeEditorClass(""))
        assertFalse(WebViewEditorDetection.isNativeEditorClass("   "))
    }

    @Test fun `WebView container classes are recognised`() {
        assertTrue(WebViewEditorDetection.isWebViewContainerClass("android.webkit.WebView"))
        assertTrue(WebViewEditorDetection.isWebViewContainerClass("org.chromium.content.browser.ContentView"))
    }

    @Test fun `arbitrary containers are not WebViews`() {
        assertFalse(WebViewEditorDetection.isWebViewContainerClass("android.widget.FrameLayout"))
        assertFalse(WebViewEditorDetection.isWebViewContainerClass("android.widget.EditText"))
        assertFalse(WebViewEditorDetection.isWebViewContainerClass(null))
        assertFalse(WebViewEditorDetection.isWebViewContainerClass(""))
    }

    @Test fun `requiresPasteWrite is off by default`() {
        assertFalse(WebViewEditorDetection.requiresPasteWrite("com.whatsapp"))
        assertFalse(WebViewEditorDetection.requiresPasteWrite("md.obsidian"))
        assertFalse(WebViewEditorDetection.requiresPasteWrite(null))
        assertFalse(WebViewEditorDetection.requiresPasteWrite(""))
    }
}

package dev.diego.expanda.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionAnchorPolicyTest {
    private val bounds = AnchorGeometry(10, 20, 300, 80)

    private fun anchor(
        packageName: String = "com.editor",
        windowId: Int = 7,
        uniqueId: String? = "editor-input",
        viewId: String? = "com.editor:id/input",
        geometry: AnchorGeometry = bounds,
    ) = SuggestionAnchor(packageName, windowId, uniqueId, viewId, "android.widget.EditText", geometry)

    @Test fun `same editor keeps popup`() {
        assertTrue(SuggestionAnchorPolicy.shouldKeep(anchor(), anchor()))
    }

    @Test fun `different package or window hides popup`() {
        assertFalse(SuggestionAnchorPolicy.shouldKeep(anchor(), anchor(packageName = "com.other")))
        assertFalse(SuggestionAnchorPolicy.shouldKeep(anchor(), anchor(windowId = 8)))
    }

    @Test fun `unique editor identity mismatch hides popup`() {
        assertFalse(SuggestionAnchorPolicy.shouldKeep(anchor(), anchor(uniqueId = "other-input")))
    }

    @Test fun `view id and geometry fallback keep matching editor`() {
        val withoutIdentity = anchor(uniqueId = null, viewId = null)
        assertTrue(SuggestionAnchorPolicy.shouldKeep(withoutIdentity, anchor(uniqueId = null, viewId = null)))
        assertFalse(SuggestionAnchorPolicy.shouldKeep(withoutIdentity, anchor(uniqueId = null, viewId = null, geometry = AnchorGeometry(11, 20, 300, 80))))
    }
}

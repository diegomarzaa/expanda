package dev.diego.expanda.service

/**
 * Heuristics to tell native Android editors apart from WebView-rendered ones
 * (Chromium/Blink content-shell fields such as CodeMirror in Obsidian, Chrome
 * search bars, Discord's message input, etc.).
 *
 * WebView editors expose an accessibility tree, but they refuse to honour
 * [android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT] as an
 * atomic replacement: CodeMirror keeps its own document model and gets
 * desynchronised, merging the requested text with the previous buffer or
 * dropping the caret at the wrong offset. The reliable path for those fields
 * is [android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE], which
 * Blink processes as a real paste event.
 *
 * We keep the class-name checks in a pure object so they can be unit-tested
 * without a running Android runtime; the caller walks the parent chain to
 * confirm the field lives inside a WebView.
 */
internal object WebViewEditorDetection {

    /**
     * Packages known to expose their editor with a native class name
     * ([android.widget.EditText] or similar) even though it's really a WebView /
     * canvas-based field, AND that behave better when written through paste. Extend
     * this list carefully: some apps (Obsidian, for example) route their editor
     * through a Capacitor bridge that desyncs with *both* paths, so forcing paste
     * makes things worse, not better.
     */
    private val PASTE_ONLY_PACKAGES = emptySet<String>()

    /** Whether the app identified by [packageName] should always be written through paste. */
    fun requiresPasteWrite(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName in PASTE_ONLY_PACKAGES
    }

    /**
     * Whether [className] corresponds to a native, non-WebView editor widget.
     * Anything ending in `EditText` (Android widget, Material AppCompat,
     * MaterialTextInput, custom subclasses…) plus the well-known suggestion
     * variants counts as native.
     */
    fun isNativeEditorClass(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        if (className.endsWith("EditText")) return true
        return when (className) {
            "android.widget.AutoCompleteTextView",
            "android.widget.MultiAutoCompleteTextView",
            "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
            "com.google.android.material.textfield.MaterialAutoCompleteTextView",
            -> true
            else -> false
        }
    }

    /**
     * Whether [className] identifies a WebView container. Only the Android
     * framework `WebView` and the underlying Chromium content-view class are
     * used in practice; both mark subtrees that route text edits through Blink.
     */
    fun isWebViewContainerClass(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        return className == "android.webkit.WebView" ||
            className == "org.chromium.content.browser.ContentView"
    }
}

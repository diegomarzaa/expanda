package dev.diego.expanda.service

internal object AccessibilityEditorText {
    fun content(showingHint: Boolean, nodeText: CharSequence?): String =
        if (showingHint) "" else nodeText?.toString().orEmpty()
}

package dev.diego.expanda.service

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.diego.expanda.ExpandaApplication

/**
 * Invisible activity that briefly takes input focus so Android allows a
 * clipboard read (blocked from AccessibilityService on Android 10+).
 *
 * Flow:
 *   1. Service starts this activity (FLAG_ACTIVITY_NEW_TASK)
 *   2. Activity gets focus → reads clipboard → caches it
 *   3. Activity finishes itself
 *   4. After a delay the service retries the expansion with the cached text
 */
class ClipboardCaptureActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) complete()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(::complete, 120)
    }

    private fun complete() {
        if (completed) return
        completed = true

        val text = (application as ExpandaApplication).clipboardMonitor.capture()
        Log.d(TAG, "Clipboard capture: ${if (text != null) "${text.length} chars" else "null"}")

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        handler.postDelayed({
            ExpansionAccessibilityService.onClipboardCaptured()
        }, FOCUS_RETURN_DELAY_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (!completed) {
            ExpansionAccessibilityService.onClipboardCaptureFailed()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClipboardCapture"
        private const val FOCUS_RETURN_DELAY_MS = 350L
    }
}

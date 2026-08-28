package dev.diego.expanda.data

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

/**
 * Keeps the latest readable clipboard text in memory so template expansion can
 * substitute `{{clipboard}}` without depending on [ClipboardManager.getPrimaryClip]
 * (blocked on Android 10+ when another app has focus).
 *
 * Staleness: the listener fires when the clipboard changes even if we cannot
 * read its contents. We record [changeDetectedAt] so [resolve] knows not to
 * return a cached value from a previous read.
 */
class ClipboardMonitor(
    private val context: Context,
    private val historyReader: () -> String? = { null },
    private val historyWriter: (String) -> Unit = {},
) {
    @Volatile var cachedText: String? = null
        private set

    @Volatile private var cachedAt: Long = 0L
    @Volatile private var changeDetectedAt: Long = 0L

    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    fun start() {
        if (listener != null) return
        val manager = clipboardManager()
        tryRead(manager)
        val callback = ClipboardManager.OnPrimaryClipChangedListener {
            changeDetectedAt = System.currentTimeMillis()
            tryRead(manager)
        }
        listener = callback
        manager.addPrimaryClipChangedListener(callback)
    }

    fun stop() {
        val callback = listener ?: return
        runCatching { clipboardManager().removePrimaryClipChangedListener(callback) }
        listener = null
    }

    /** Attempt a live read and cache the result. Returns the text or null. */
    fun capture(manager: ClipboardManager = clipboardManager()): String? {
        val result = tryRead(manager)
        Log.d(TAG, "capture: ${result?.let { "${it.length} chars" } ?: "null"}")
        return result
    }

    /**
     * Best-effort clipboard text for template expansion.
     *
     * Returns null when the live read fails AND the cache is older than
     * [FRESH_CACHE_WINDOW_MS] — the caller should trigger a clipboard
     * capture overlay. A recently captured value (e.g. from
     * [ClipboardCaptureActivity] or the overlay) is trusted within the window.
     *
     * This avoids the old `changeDetectedAt`-based staleness check which
     * silently returned stale data on OEMs where the listener never fires
     * for background apps (Xiaomi/MIUI, some Samsung).
     */
    fun resolve(manager: ClipboardManager = clipboardManager()): String? {
        tryRead(manager)?.let { return it }

        if (cachedText != null && System.currentTimeMillis() - cachedAt < FRESH_CACHE_WINDOW_MS) {
            Log.d(TAG, "resolve: returning recently cached text (${cachedText?.length} chars)")
            return cachedText
        }

        Log.d(TAG, "resolve: cache stale or empty, returning null to trigger capture")
        return null
    }

    private fun tryRead(manager: ClipboardManager): String? {
        val text = readClipboard(manager) ?: return null
        if (text.isBlank()) return null
        cachedText = text
        cachedAt = System.currentTimeMillis()
        runCatching { historyWriter(text) }
            .onFailure { Log.w(TAG, "Failed to persist clipboard history", it) }
        return text
    }

    private fun readClipboard(manager: ClipboardManager): String? = runCatching {
        val clip = manager.primaryClip ?: return@runCatching null
        if (clip.itemCount == 0) return@runCatching null
        val desc = clip.description
        if (desc != null &&
            !desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) &&
            !desc.hasMimeType("text/*")
        ) return@runCatching null
        clip.getItemAt(0).coerceToText(context)?.toString()
    }.getOrNull()

    private fun clipboardManager(): ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    companion object {
        private const val TAG = "ClipboardMonitor"
        private const val FRESH_CACHE_WINDOW_MS = 5_000L
    }
}

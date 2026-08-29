package dev.diego.expanda.service

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.ContextThemeWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.Spinner
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.diego.expanda.ExpandaApplication
import dev.diego.expanda.MainActivity
import dev.diego.expanda.R
import dev.diego.expanda.engine.ActionCategory
import dev.diego.expanda.engine.ActionContext
import dev.diego.expanda.engine.ActionDefinition
import dev.diego.expanda.engine.ActionEngine
import dev.diego.expanda.engine.ActionOutcome
import dev.diego.expanda.engine.ActionRequest
import dev.diego.expanda.engine.AppliedExpansion
import dev.diego.expanda.engine.ExpansionEngine
import dev.diego.expanda.engine.ExpansionMatch
import dev.diego.expanda.engine.TemplateRenderer
import dev.diego.expanda.engine.RenderedTemplate
import dev.diego.expanda.engine.TemplateFieldInputType
import dev.diego.expanda.engine.TemplateFieldRequest
import dev.diego.expanda.engine.TemplateSelector
import dev.diego.expanda.data.AppSettings
import dev.diego.expanda.data.SettingsRepository
import dev.diego.expanda.data.TextMatch
import dev.diego.expanda.data.TemplateSelectionMode
import dev.diego.expanda.service.overlay.OverlayViews
import dev.diego.expanda.ui.theme.NativeThemeTokens
import dev.diego.expanda.ui.theme.resolveNativeTheme
import dev.diego.expanda.ui.suggestion.SuggestionOverlaySpec
import dev.diego.expanda.ui.suggestion.SuggestionResizePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

class ExpansionAccessibilityService : AccessibilityService() {
    private sealed interface PopupSuggestion {
        val shortcut: String
        val matchedText: String

        data class TextSnippet(
            val textMatch: TextMatch,
            val suggestionTrigger: String,
            override val matchedText: String,
        ) : PopupSuggestion {
            override val shortcut: String get() = suggestionTrigger
        }

        data class Action(val definition: ActionDefinition, override val matchedText: String) : PopupSuggestion {
            override val shortcut: String get() = definition.shortcut
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = ExpansionEngine()
    private val templateSelector = TemplateSelector()
    private val actionEngine = ActionEngine()
    private val repository by lazy { (application as ExpandaApplication).matchRepository }
    private val settingsRepository by lazy { (application as ExpandaApplication).settingsRepository }
    private val actionSettingsStore by lazy { (application as ExpandaApplication).actionSettingsStore }
    private val clipboardMonitor by lazy { (application as ExpandaApplication).clipboardMonitor }

    private var lastAppliedText: String? = null
    private var lastAppliedAt = 0L
    private var reversibleExpansion: ReversibleExpansion? = null
    private var suppressedExpansion: ReversibleExpansion? = null
    private var suggestionOverlay: View? = null
    private var suggestionWindowParams: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var suggestionValidation: Runnable? = null
    private var suggestionAnchor: SuggestionAnchor? = null
    private var formOverlay: View? = null
    private var pendingFormNode: AccessibilityNodeInfo? = null
    private var pendingFormApply: Runnable? = null
    private var activeFieldDialog: Dialog? = null

    /** Context saved while clipboard overlay / capture reads the clipboard. */
    private var pendingClipboardRetry: PendingClipboardRetry? = null
    private var clipboardOverlay: View? = null
    private var clipboardOverlayTimeout: Runnable? = null

    private data class PendingClipboardRetry(
        val anchor: SuggestionAnchor,
        val packageName: String,
        val settings: AppSettings,
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = WeakReference(this)
        clipboardMonitor.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            when (event?.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    cancelSuggestionValidation()
                    handleTextChanged(event)
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                -> scheduleSuggestionValidation()
            }
        } catch (failure: RuntimeException) {
            recoverFromEventFailure(failure)
        } catch (failure: LinkageError) {
            recoverFromEventFailure(failure)
        }
    }

    private fun recoverFromEventFailure(failure: Throwable) {
        Log.e(TAG, "Accessibility event failed; keeping the service alive", failure)
        clearExpansionUndo()
        hideSuggestions()
        hideFormOverlay()
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        val node = event.source
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return
        try {
            if (!node.isEditable || node.isPassword || isPasswordInput(node.inputType)) return
            val packageName = event.packageName?.toString()?.takeIf { it.isNotEmpty() }
                ?: node.packageName?.toString().orEmpty()
            if (packageName.isEmpty()) return
            if (packageName == applicationContext.packageName &&
                node.viewIdResourceName != "${applicationContext.packageName}:id/${resources.getResourceEntryName(R.id.expanda_test_input)}"
            ) return
            val settings = settingsRepository.settings.value
            if (!settings.expansionEnabled || settings.isPaused || packageName in settings.globallyExcludedPackages) return

            // Prefer the event's fresh text over node.text: WebView-based editors (Gmail, Chrome,
            // Obsidian) throttle content-changed events, so the AccessibilityService cache can
            // return stale text (e.g. ";t" when the user just typed ";tim") for hundreds of ms.
            val text = AccessibilityTextSnapshot.text(
                eventTexts = event.text.firstOrNull(),
                nodeText = node.text,
                showingHint = node.isShowingHintText,
            ) ?: return
            val cursor = AccessibilityTextSnapshot.cursor(
                text = text,
                eventFromIndex = event.fromIndex,
                eventAddedCount = event.addedCount,
                eventRemovedCount = event.removedCount,
                nodeSelectionEnd = node.textSelectionEnd,
            )
            val selectionStart = node.textSelectionStart.takeIf { it in 0..text.length } ?: cursor
            val activeAnchor = createSuggestionAnchor(node, packageName)
            when (reversibleExpansion?.let {
                ExpansionUndoPolicy.backspaceDecision(it, activeAnchor, text, cursor)
            }) {
                ExpansionUndoDecision.Restore -> {
                    val expansion = reversibleExpansion ?: return
                    reversibleExpansion = null
                    hideSuggestions()
                    if (setFieldText(
                            node = node,
                            originalText = text,
                            newText = expansion.restoredText,
                            selectionStart = expansion.restoredCursor,
                            selectionEnd = expansion.restoredCursor,
                            settings = settings,
                        )
                    ) {
                        suppressedExpansion = expansion
                        lastAppliedText = expansion.restoredText
                        lastAppliedAt = SystemClock.elapsedRealtime()
                    } else {
                        suppressedExpansion = null
                    }
                    return
                }
                ExpansionUndoDecision.Clear -> reversibleExpansion = null
                ExpansionUndoDecision.Keep -> return
                null -> Unit
            }
            suppressedExpansion?.let { suppressed ->
                if (ExpansionUndoPolicy.isRestoredText(suppressed, activeAnchor, text)) return
            }
            if (text == lastAppliedText && SystemClock.elapsedRealtime() - lastAppliedAt < REENTRANCY_WINDOW_MS) return
            val action = actionEngine.execute(
                ActionContext(
                    text = text,
                    cursor = cursor,
                    selectionStart = selectionStart.coerceAtMost(cursor),
                    selectionEnd = cursor,
                    clipboard = readClipboardText(),
                ),
                enabledActionIds = actionSettingsStore.enabledIds.value,
                shortcutOverrides = actionSettingsStore.shortcutOverrides.value,
            )
            if (action != null) {
                suppressedExpansion = null
                hideSuggestions()
                if (applyAction(node, text, action, settings)) {
                    lastAppliedText = action.text
                    lastAppliedAt = SystemClock.elapsedRealtime()
                    handleActionRequest(action.request, action.text)
                }
                return
            }
            val candidates = engine.findMatchesAtCursor(text, cursor, repository.matches.value, packageName)
            suppressedExpansion?.let { suppressed ->
                suppressedExpansion = null
                if (candidates.isNotEmpty() && ExpansionUndoPolicy.shouldSuppressDelimiterExpansion(
                        suppressed, activeAnchor, text, cursor, candidates.first(),
                    )
                ) {
                    hideSuggestions()
                    return
                }
            }
            if (candidates.isEmpty()) {
                if (settings.suggestionEnabled) showSuggestions(node, text, cursor, packageName, settings)
                else hideSuggestions()
                return
            }
            if (candidates.size > 1) {
                hideSuggestions()
                @Suppress("DEPRECATION")
                showMatchDisambiguation(
                    AccessibilityNodeInfo.obtain(node),
                    text,
                    candidates,
                    packageName,
                    settings,
                )
                return
            }
            val match = candidates.single()
            hideSuggestions()
            continueExpansion(node, text, match, packageName, settings)
        } finally {
            node.recycle()
        }
    }

    override fun onInterrupt() {
        clearExpansionUndo()
        hideSuggestions()
        hideFormOverlay()
    }

    override fun onDestroy() {
        clearExpansionUndo()
        hideSuggestions()
        hideFormOverlay()
        removeClipboardOverlay()
        if (activeService?.get() === this) activeService = null
        scope.cancel()
        super.onDestroy()
    }

    private fun showAllSuggestionsForFocusedInput() {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        try {
            if (focused == null || !focused.isEditable || focused.isPassword || isPasswordInput(focused.inputType)) return
            val packageName = focused.packageName?.toString().orEmpty()
            if (packageName.isBlank()) return
            val settings = settingsRepository.settings.value
            if (!settings.expansionEnabled || settings.isPaused || packageName in settings.globallyExcludedPackages) return
            val text = editableText(focused)
            val cursor = focused.textSelectionEnd.takeIf { it in 0..text.length } ?: text.length
            showSuggestions(focused, text, cursor, packageName, settings, showAll = true)
        } finally {
            focused?.recycle()
            root.recycle()
        }
    }

    private fun isPasswordInput(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun editableText(node: AccessibilityNodeInfo): String =
        AccessibilityEditorText.content(node.isShowingHintText, node.text)

    /**
     * Reads textual clipboard content when Android exposes it to this service.
     * A null result means "not readable here", not "clipboard contains empty text".
     */
    private fun readClipboardTextOrNull(): String? =
        clipboardMonitor.resolve(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)

    private fun readClipboardText(): String = readClipboardTextOrNull().orEmpty()

    private fun renderMatch(
        expansion: ExpansionMatch,
        manualIndex: Int? = null,
    ): RenderedTemplate {
        val clipboardText = readClipboardTextOrNull()
        Log.d(TAG, "renderMatch: clipboard=${clipboardText?.let { "${it.length} chars" } ?: "null (will use marker)"}")
        return TemplateRenderer(
            clipboard = { clipboardText ?: CLIPBOARD_PASTE_MARKER },
            matchResolver = { trigger ->
                repository.matches.value.firstOrNull { nested ->
                    nested.runsOnAndroid && nested.triggers.any {
                        it.pattern.equals(trigger, ignoreCase = true)
                    }
                }
            },
        ).render(
            expansion.match,
            expansion,
            manualIndex = manualIndex,
            globalVariables = settingsRepository.settings.value.globalVariables,
        )
    }

    // ── Clipboard expansion ─────────────────────────────────────────────

    private fun applyExpansion(
        node: AccessibilityNodeInfo,
        originalText: String,
        match: ExpansionMatch,
        rendered: RenderedTemplate,
        packageName: String,
        settings: AppSettings,
        clipboardCaptureAttempted: Boolean = false,
    ): Boolean {
        if (rendered.unresolvedTokens.isNotEmpty() || !match.match.runsOnAndroid) {
            Log.w(TAG, "Blocked unsafe expansion: ${rendered.unresolvedTokens}")
            return false
        }

        val applied = engine.applyMatch(originalText, match, rendered)
        val hasMarkers = applied.text.contains(CLIPBOARD_PASTE_MARKER)

        // ── resolve clipboard markers ───────────────────────────────────
        val clipboardText = if (hasMarkers) readClipboardTextOrNull() else null
        Log.d(TAG, "applyExpansion: hasMarkers=$hasMarkers clipboardCaptureAttempted=$clipboardCaptureAttempted clipboardText=${clipboardText?.let { "${it.length}ch" } ?: "null"}")
        val (finalText, finalCursor) = when {
            hasMarkers && clipboardText != null ->
                substituteClipboardMarkers(applied.text, applied.cursor, clipboardText)

            hasMarkers && !clipboardCaptureAttempted -> {
                Log.d(TAG, "applyExpansion: launching clipboard capture")
                launchClipboardCapture(node, packageName, settings)
                return false
            }

            hasMarkers -> {
                Log.w(TAG, "Clipboard still unresolved after capture; inserting empty")
                substituteClipboardMarkers(applied.text, applied.cursor, "")
            }

            else -> applied.text to applied.cursor
        }

        // ── write into the editor ───────────────────────────────────────
        // Native EditText widgets accept ACTION_SET_TEXT as an atomic replacement.
        // Everything else — WebView editors (Obsidian/CodeMirror, Chrome, Discord,
        // Capacitor apps), Compose fields exposed as android.view.View, custom OEM
        // widgets — either desyncs its buffer or ignores the action, so we route
        // those through ACTION_PASTE (which Blink treats as a real paste event).
        val nativeEditor = isNativeEditText(node)
        val replacement = replacementSlice(match, applied, originalText, finalText)
        val writeSucceeded = if (nativeEditor) {
            writeViaSetText(
                node = node,
                finalText = finalText,
                selectionStart = finalCursor,
                selectionEnd = finalCursor,
            ) || pasteReplacement(
                node, match.replaceFrom, match.replaceTo,
                replacement, finalCursor,
            )
        } else {
            pasteReplacement(
                node, match.replaceFrom, match.replaceTo,
                replacement, finalCursor,
            ) || writeViaSetText(
                node = node,
                finalText = finalText,
                selectionStart = finalCursor,
                selectionEnd = finalCursor,
            )
        }
        if (!writeSucceeded) return false

        // ── book-keeping ────────────────────────────────────────────────
        val restoredText = originalText.replaceRange(
            match.replaceFrom, match.replaceTo, match.matchedText,
        )
        reversibleExpansion = ReversibleExpansion(
            anchor = createSuggestionAnchor(node, packageName),
            appliedText = finalText,
            appliedCursor = finalCursor,
            restoredText = restoredText,
            restoredCursor = match.replaceFrom + match.matchedText.length,
            matchId = match.match.id,
            matchedText = match.matchedText,
        )
        suppressedExpansion = null
        lastAppliedText = finalText
        lastAppliedAt = SystemClock.elapsedRealtime()
        finishExpansion(match.match, packageName, settings)
        if (rendered.actions.any { it.type == dev.diego.expanda.engine.TemplateActionType.SEND }) {
            mainHandler.postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                }
            }, 100L)
        }
        return true
    }

    // ── overlay-based clipboard capture ─────────────────────────────────
    //
    // A 1x1 transparent focusable TYPE_ACCESSIBILITY_OVERLAY window gains
    // input focus (satisfying Android 10+'s clipboard-read check) without
    // collapsing system dialogs or flickering the keyboard. If the overlay
    // cannot gain focus (OEM quirk), we fall back to a transparent activity.

    private fun launchClipboardCapture(
        node: AccessibilityNodeInfo,
        packageName: String,
        settings: AppSettings,
    ) {
        if (pendingClipboardRetry != null) return
        pendingClipboardRetry = PendingClipboardRetry(
            anchor = createSuggestionAnchor(node, packageName),
            packageName = packageName,
            settings = settings,
        )
        Log.d(TAG, "Clipboard capture: starting overlay")
        if (!startClipboardOverlay()) {
            Log.w(TAG, "Clipboard capture: overlay failed, trying activity fallback")
            launchClipboardCaptureActivity()
        }
    }

    private fun startClipboardOverlay(): Boolean {
        removeClipboardOverlay()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = View(this)
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        var handled = false
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus && !handled) {
                handled = true
                view.post {
                    val text = clipboardMonitor.capture(
                        getSystemService(CLIPBOARD_SERVICE) as ClipboardManager,
                    )
                    Log.d(TAG, "Overlay clipboard read: ${text?.let { "${it.length} chars" } ?: "null"}")
                    removeClipboardOverlay()
                    mainHandler.postDelayed(::onClipboardCaptureComplete, CLIPBOARD_OVERLAY_SETTLE_MS)
                }
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)

        return try {
            wm.addView(view, params)
            clipboardOverlay = view
            val timeout = Runnable {
                if (clipboardOverlay != null) {
                    Log.w(TAG, "Clipboard overlay timeout — falling back to activity")
                    removeClipboardOverlay()
                    launchClipboardCaptureActivity()
                }
            }
            clipboardOverlayTimeout = timeout
            mainHandler.postDelayed(timeout, CLIPBOARD_OVERLAY_TIMEOUT_MS)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Could not add clipboard overlay", t)
            view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
            false
        }
    }

    private fun removeClipboardOverlay() {
        clipboardOverlayTimeout?.let(mainHandler::removeCallbacks)
        clipboardOverlayTimeout = null
        val view = clipboardOverlay ?: return
        clipboardOverlay = null
        runCatching {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
        }
    }

    private fun launchClipboardCaptureActivity() {
        runCatching {
            startActivity(
                Intent(this, ClipboardCaptureActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                },
            )
        }.onFailure {
            Log.w(TAG, "Could not start ClipboardCaptureActivity", it)
            pendingClipboardRetry = null
        }
    }

    private fun onClipboardCaptureComplete() {
        val retry = pendingClipboardRetry ?: return
        pendingClipboardRetry = null
        retryClipboardExpansion(retry, attempt = 0)
    }

    private fun onClipboardCaptureCancel() {
        pendingClipboardRetry = null
    }

    private fun retryClipboardExpansion(retry: PendingClipboardRetry, attempt: Int) {
        Log.d(TAG, "retryClipboardExpansion: attempt=$attempt")
        val node = findAnchoredEditor(retry.anchor)
        if (node == null) {
            if (attempt < CLIPBOARD_RETRY_ATTEMPTS) {
                Log.d(TAG, "retryClipboardExpansion: editor not found, scheduling retry ${attempt + 1}")
                mainHandler.postDelayed({
                    retryClipboardExpansion(retry, attempt + 1)
                }, CLIPBOARD_RETRY_DELAY_MS)
                return
            }
            Log.w(TAG, "Clipboard retry: editor not found after $attempt attempts")
            return
        }
        try {
            val text = editableText(node)
            val cursor = node.textSelectionEnd
                .takeIf { it in 0..text.length } ?: text.length
            val candidates = engine.findMatchesAtCursor(
                text, cursor, repository.matches.value, retry.packageName,
            )
            val match = candidates.singleOrNull() ?: return
            val rendered = renderMatch(match)
            applyExpansion(
                node, text, match, rendered,
                retry.packageName, retry.settings,
                clipboardCaptureAttempted = true,
            )
        } finally {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    private fun showMatchDisambiguation(
        node: AccessibilityNodeInfo,
        originalText: String,
        candidates: List<ExpansionMatch>,
        packageName: String,
        settings: AppSettings,
    ) {
        hideFormOverlay()
        pendingFormNode = node
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val ui = OverlayViews(this, resolveNativeTheme(this, settings))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(8))
            addView(ui.title("Choose snippet").apply {
                setPadding(dp(6), dp(2), dp(6), dp(8))
            })
        }
        candidates.forEach { expansion ->
            val match = expansion.match
            val title = match.label.ifBlank {
                match.replace.replace('\n', ' ').take(180)
            }
            val row = ui.body(title).apply {
                if (match.label.isNotBlank()) {
                    val preview = match.replace.replace('\n', ' ').take(180)
                    text = "$title\n$preview"
                }
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = ui.surface()
                isClickable = true
                isFocusable = true
                contentDescription = "Use $title"
                setOnClickListener {
                    formOverlay?.let { overlay -> runCatching { windowManager.removeView(overlay) } }
                    formOverlay = null
                    pendingFormNode = null
                    continueExpansion(node, originalText, expansion, packageName, settings)
                }
            }
            content.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(6) },
            )
        }
        val footer = overlayCancelFooter(ui) { hideFormOverlay() }
        val bounds = displayBounds(windowManager)
        val maxContentHeight = (bounds.height() * 0.55f).toInt().coerceAtLeast(dp(180))
        val root = buildPickerOverlayRoot(content, footer, candidates.size, maxContentHeight, ui.panel(22), ui)
        val params = overlayDialogParams(windowManager, softInput = false)
        runCatching {
            windowManager.addView(root, params)
            formOverlay = root
        }.onFailure {
            pendingFormNode = null
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    private fun continueExpansion(
        node: AccessibilityNodeInfo,
        originalText: String,
        match: ExpansionMatch,
        packageName: String,
        settings: AppSettings,
    ) {
        if (match.match.selectionMode == TemplateSelectionMode.MANUAL && match.match.replacements.size > 1) {
            showTemplateChooser(node, originalText, match, packageName, settings)
            return
        }
        val rendered = renderMatch(match)
        if (rendered.requiresInput) {
            showFormOverlay(node, originalText, match, rendered, packageName, settings)
            return
        }
        applyExpansion(node, originalText, match, rendered, packageName, settings)
    }

    private fun showTemplateChooser(
        node: AccessibilityNodeInfo,
        originalText: String,
        match: ExpansionMatch,
        packageName: String,
        settings: AppSettings,
    ) {
        hideFormOverlay()
        pendingFormNode = node
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val ui = OverlayViews(this, resolveNativeTheme(this, settings))
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(8))
            addView(ui.title("Choose replacement").apply {
                setPadding(dp(6), dp(2), dp(6), dp(8))
            })
        }
        match.match.replacements.forEachIndexed { index, template ->
            list.addView(ui.body("${index + 1}. ${template.replace('\n', ' ').take(180)}").apply {
                this.text = "${index + 1}. ${template.replace('\n', ' ').take(180)}"
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = ui.surface()
                isClickable = true
                isFocusable = true
                contentDescription = "Use replacement ${index + 1}"
                setOnClickListener {
                    val rendered = renderMatch(match, index)
                    formOverlay?.let { overlay -> runCatching { windowManager.removeView(overlay) } }
                    formOverlay = null
                    pendingFormNode = null
                    if (rendered.requiresInput) {
                        showFormOverlay(node, originalText, match, rendered, packageName, settings)
                    } else {
                        try {
                            applyExpansion(node, originalText, match, rendered, packageName, settings)
                        } finally {
                            @Suppress("DEPRECATION")
                            node.recycle()
                        }
                    }
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            })
        }
        val footer = overlayCancelFooter(ui) { hideFormOverlay() }
        val bounds = displayBounds(windowManager)
        val maxContentHeight = (bounds.height() * 0.55f).toInt().coerceAtLeast(dp(180))
        val root = buildPickerOverlayRoot(list, footer, match.match.replacements.size, maxContentHeight, ui.panel(22), ui)
        val params = overlayDialogParams(windowManager, softInput = false)
        runCatching {
            windowManager.addView(root, params)
            formOverlay = root
        }.onFailure {
            pendingFormNode = null
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    private fun showFormOverlay(
        node: AccessibilityNodeInfo,
        originalText: String,
        match: ExpansionMatch,
        rendered: RenderedTemplate,
        packageName: String,
        settings: AppSettings,
    ) {
        hideFormOverlay()
        pendingFormNode = node
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val ui = OverlayViews(this, resolveNativeTheme(this, settings))
        val fields = rendered.fields.distinctBy { it.name }
        if (fields.size == 1 && fields.single().inputType == TemplateFieldInputType.CHOICE) {
            showChoiceOverlay(node, originalText, match, rendered, packageName, settings, fields.single())
            return
        }
        val valueReaders = linkedMapOf<String, () -> String>()
        var firstTextInput: EditText? = null
        val formScroll = BoundedScrollView(this, 0)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
            addView(ui.title("Complete snippet").apply {
                setPadding(0, 0, 0, dp(12))
            })
        }
        var previewIndex = 0
        rendered.fields.sortedBy { it.start }.forEach { field ->
            if (field.start >= previewIndex) {
                addPreviewText(panel, rendered.text.substring(previewIndex, field.start), ui)
            }
            if (field.name !in valueReaders) {
                when (field.inputType) {
                    TemplateFieldInputType.TEXT -> {
                        val input = ui.input(field.label, field.defaultValue).apply {
                            hint = ""
                        }
                        if (!field.multiline) {
                            input.setSingleLine(true)
                            input.maxLines = 1
                        }
                        valueReaders[field.name] = { input.text.toString() }
                        if (firstTextInput == null) firstTextInput = input
                        input.scrollIntoViewWhenFocused(formScroll)
                        panel.addView(ui.fieldGroup(field.label, input))
                    }
                    TemplateFieldInputType.CHOICE -> {
                        val spinner = ui.spinner(field.options)
                        valueReaders[field.name] = {
                            field.optionValues.getOrElse(spinner.selectedItemPosition) {
                                spinner.selectedItem?.toString().orEmpty()
                            }
                        }
                        panel.addView(ui.fieldGroup(field.label, spinner))
                    }
                    TemplateFieldInputType.DATE,
                    TemplateFieldInputType.TIME -> {
                        val button = dateTimeFieldButton(field.inputType, field.label, field.defaultValue, ui)
                        valueReaders[field.name] = { button.text.toString() }
                        panel.addView(ui.fieldGroup(field.label, button))
                    }
                }
            } else {
                addPreviewText(panel, "⟦${field.label}⟧", ui)
            }
            previewIndex = maxOf(previewIndex, field.end)
        }
        addPreviewText(panel, rendered.text.substring(previewIndex.coerceAtMost(rendered.text.length)), ui)
        val footer = overlayActionFooter(
            ui,
            onCancel = { hideFormOverlay() },
            onPrimary = {
                val values = valueReaders.mapValues { it.value.invoke() }
                val completed = rendered.fillFields(values)
                scheduleFormApply(node, 120L) {
                    applyExpansion(node, originalText, match, completed, packageName, settings)
                }
            },
        )
        val bounds = displayBounds(windowManager)
        val maxContentHeight = (bounds.height() * 0.5f).toInt().coerceAtLeast(dp(160))
        formScroll.setMaxHeight(maxContentHeight)
        val root = buildFormOverlayRoot(panel, footer, formScroll, ui.panel(), ui)
        val params = overlayDialogParams(windowManager, softInput = true)
        runCatching {
            windowManager.addView(root, params)
            formOverlay = root
            firstTextInput?.let { first ->
                first.requestFocus()
                first.setSelection(first.text.length)
                first.postDelayed({
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(first, InputMethodManager.SHOW_IMPLICIT)
                }, 150L)
            }
        }.onFailure {
            pendingFormNode = null
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    private fun showChoiceOverlay(
        node: AccessibilityNodeInfo,
        originalText: String,
        match: ExpansionMatch,
        rendered: RenderedTemplate,
        packageName: String,
        settings: AppSettings,
        field: TemplateFieldRequest,
    ) {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val ui = OverlayViews(this, resolveNativeTheme(this, settings))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(8))
            addView(ui.title("Choose ${field.label}").apply {
                setPadding(0, 0, 0, dp(8))
            })
            val preview = rendered.text.substring(0, field.start) + "[…]" +
                rendered.text.substring(field.end.coerceAtMost(rendered.text.length))
            addView(ui.body(preview, secondary = true).apply {
                setPadding(0, 0, 0, dp(10))
            })
        }
        field.options.forEachIndexed { index, option ->
            panel.addView(ui.choiceOption(option) {
                val completed = rendered.fillFields(
                    mapOf(field.name to field.optionValues.getOrElse(index) { option }),
                )
                scheduleFormApply(node, 80L) {
                    applyExpansion(node, originalText, match, completed, packageName, settings)
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(6) })
        }
        val footer = overlayCancelFooter(ui) { hideFormOverlay() }
        val bounds = displayBounds(windowManager)
        val maxContentHeight = (bounds.height() * 0.55f).toInt().coerceAtLeast(dp(180))
        val root = buildPickerOverlayRoot(panel, footer, field.options.size, maxContentHeight, ui.panel(), ui)
        val params = overlayDialogParams(windowManager, softInput = false)
        runCatching {
            windowManager.addView(root, params)
            formOverlay = root
        }.onFailure {
            pendingFormNode = null
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    private fun overlayDialogParams(windowManager: WindowManager, softInput: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            (displayBounds(windowManager).width() * 0.9f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.35f
            if (softInput) {
                @Suppress("DEPRECATION")
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
        }

    private fun overlayCancelFooter(
        ui: OverlayViews,
        onCancel: () -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        minimumHeight = dp(56)
        addView(
            ui.footerButton("Cancel", primary = false, onCancel),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(48),
            ),
        )
    }

    private fun overlayActionFooter(
        ui: OverlayViews,
        primaryLabel: String = "Insert",
        onCancel: () -> Unit,
        onPrimary: () -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        minimumHeight = dp(56)
        addView(
            ui.footerButton("Cancel", primary = false, onCancel),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(48),
            ).apply { marginEnd = dp(8) },
        )
        addView(
            ui.footerButton(primaryLabel, primary = true, onPrimary),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(48),
            ),
        )
    }

    private fun buildOverlayRoot(
        content: LinearLayout,
        footer: LinearLayout,
        background: android.graphics.drawable.Drawable,
        ui: OverlayViews,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        this.background = background
        addView(
            content,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            ui.divider(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dp(1),
            ).apply {
                topMargin = ui.dp(4)
                marginStart = ui.dp(16)
                marginEnd = ui.dp(16)
            },
        )
        addView(
            footer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun buildPickerOverlayRoot(
        content: LinearLayout,
        footer: LinearLayout,
        itemCount: Int,
        maxContentHeightPx: Int,
        background: android.graphics.drawable.Drawable,
        ui: OverlayViews,
    ): LinearLayout = if (itemCount > 6) {
        buildScrollableOverlayRoot(content, footer, maxContentHeightPx, background, ui)
    } else {
        buildOverlayRoot(content, footer, background, ui)
    }

    private fun buildFormOverlayRoot(
        content: LinearLayout,
        footer: LinearLayout,
        scroll: BoundedScrollView,
        background: android.graphics.drawable.Drawable,
        ui: OverlayViews,
    ): LinearLayout {
        scroll.addView(content)
        attachKeyboardScrollAssist(scroll, content)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            this.background = background
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                ui.divider(),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ui.dp(1),
                ).apply {
                    topMargin = ui.dp(4)
                    marginStart = ui.dp(16)
                    marginEnd = ui.dp(16)
                },
            )
            addView(
                footer,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun buildScrollableOverlayRoot(
        content: LinearLayout,
        footer: LinearLayout,
        maxContentHeightPx: Int,
        background: android.graphics.drawable.Drawable,
        ui: OverlayViews,
    ): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            this.background = background
        }
        val scroll = BoundedScrollView(this, maxContentHeightPx).apply { addView(content) }
        attachKeyboardScrollAssist(scroll, content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            ui.divider(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dp(1),
            ).apply {
                topMargin = ui.dp(4)
                marginStart = ui.dp(16)
                marginEnd = ui.dp(16)
            },
        )
        root.addView(
            footer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        return root
    }

    private fun attachKeyboardScrollAssist(scroll: BoundedScrollView, content: LinearLayout) {
        val baseBottomPadding = content.paddingBottom
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val visible = Rect()
            scroll.getWindowVisibleDisplayFrame(visible)
            val screenHeight = scroll.rootView.height
            val keyboardHeight = (screenHeight - visible.bottom).coerceAtLeast(0)
            val extra = if (keyboardHeight > screenHeight * 0.12) keyboardHeight else 0
            val targetBottom = baseBottomPadding + extra
            if (content.paddingBottom != targetBottom) {
                content.setPadding(
                    content.paddingLeft,
                    content.paddingTop,
                    content.paddingRight,
                    targetBottom,
                )
            }
        }
        scroll.viewTreeObserver.addOnGlobalLayoutListener(listener)
        scroll.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                scroll.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        })
    }

    private fun EditText.scrollIntoViewWhenFocused(scroll: ScrollView) {
        setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) return@setOnFocusChangeListener
            postDelayed({
                val rect = Rect(0, 0, width, height)
                requestRectangleOnScreen(rect, false)
                scroll.post { scroll.smoothScrollTo(0, bottom) }
            }, 180L)
        }
    }

    private fun addPreviewText(panel: LinearLayout, value: String, ui: OverlayViews) {
        if (value.isEmpty()) return
        panel.addView(ui.body(value, 14f, secondary = true).apply {
            setTextIsSelectable(false)
            setPadding(0, dp(2), 0, dp(6))
        })
    }

    private fun dateTimeFieldButton(
        inputType: TemplateFieldInputType,
        label: String,
        defaultValue: String,
        ui: OverlayViews,
    ): TextView = ui.pickerField(
        defaultValue.ifBlank {
            SimpleDateFormat(
                if (inputType == TemplateFieldInputType.DATE) "yyyy-MM-dd" else "HH:mm",
                Locale.getDefault(),
            ).format(Calendar.getInstance().time)
        },
    ).apply {
        val calendar = Calendar.getInstance()
        contentDescription = label
        setOnClickListener {
            val dialogContext = ContextThemeWrapper(
                this@ExpansionAccessibilityService,
                if (ui.theme.dark) R.style.Theme_Expanda_Dialog_Dark else R.style.Theme_Expanda_Dialog_Light,
            )
            val dialog = if (inputType == TemplateFieldInputType.DATE) {
                DatePickerDialog(
                    dialogContext,
                    { _, year, month, day -> text = "%04d-%02d-%02d".format(year, month + 1, day) },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH),
                )
            } else {
                TimePickerDialog(
                    dialogContext,
                    { _, hour, minute -> text = "%02d:%02d".format(hour, minute) },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true,
                )
            }
            activeFieldDialog?.dismiss()
            activeFieldDialog = dialog
            dialog.setOnDismissListener { if (activeFieldDialog === dialog) activeFieldDialog = null }
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            dialog.show()
        }
    }

    private fun scheduleFormApply(
        node: AccessibilityNodeInfo,
        delayMillis: Long,
        apply: () -> Unit,
    ) {
        formOverlay?.let { runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } }
        formOverlay = null
        pendingFormApply?.let(mainHandler::removeCallbacks)
        val task = Runnable {
            if (pendingFormNode !== node) return@Runnable
            pendingFormApply = null
            pendingFormNode = null
            try {
                apply()
            } finally {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }
        pendingFormApply = task
        mainHandler.postDelayed(task, delayMillis)
    }

    private fun hideFormOverlay() {
        pendingFormApply?.let(mainHandler::removeCallbacks)
        pendingFormApply = null
        activeFieldDialog?.dismiss()
        activeFieldDialog = null
        val overlay = formOverlay
        formOverlay = null
        if (overlay != null) runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(overlay) }
        pendingFormNode?.let {
            @Suppress("DEPRECATION")
            it.recycle()
        }
        pendingFormNode = null
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun setSelection(node: AccessibilityNodeInfo, start: Int, end: Int): Boolean =
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
            },
        )

    /**
     * Positions the caret / selection after an [AccessibilityNodeInfo.ACTION_SET_TEXT] call.
     *
     * WebView-based editors (Gmail composer, Chrome, Obsidian) apply the text change
     * asynchronously in Blink. The immediate ACTION_SET_SELECTION lands before the
     * DOM catches up and Chromium collapses the caret to position 0. We clear our
     * per-service cache, refresh the node so future reads see fresh data, apply the
     * selection once, and schedule a second attempt on the next frame that only
     * fires if the caret really did drift from what we requested.
     */
    private fun commitSelectionAfterSetText(
        node: AccessibilityNodeInfo,
        start: Int,
        end: Int = start,
    ) {
        clearAccessibilityCache()
        runCatching { node.refresh() }
        setSelection(node, start, end)

        val packageName = node.packageName?.toString().orEmpty()
        if (packageName.isEmpty()) return
        val anchor = createSuggestionAnchor(node, packageName)
        mainHandler.postDelayed(
            {
                val fresh = findAnchoredEditor(anchor) ?: return@postDelayed
                try {
                    val actualEnd = fresh.textSelectionEnd
                    val actualStart = fresh.textSelectionStart
                    if (actualEnd != end || actualStart != start) {
                        runCatching { fresh.refresh() }
                        setSelection(fresh, start, end)
                    }
                } finally {
                    @Suppress("DEPRECATION")
                    fresh.recycle()
                }
            },
            WEBVIEW_SELECTION_RETRY_DELAY_MS,
        )
    }

    /** Drops the framework's per-service node cache; a no-op below API 33. */
    private fun clearAccessibilityCache() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { clearCache() }
        }
    }

    /**
     * True when [node] is a native Android EditText / AutoCompleteTextView (or any
     * subclass). ACTION_SET_TEXT is only reliable for these; anything else — WebView
     * editors (Chrome, Discord message input), Compose text fields exposed with a
     * generic class name, custom OEM widgets, canvas-based editors — is routed
     * through ACTION_PASTE.
     *
     * An explicit package override lets us force paste for apps that lie about the
     * class name; today that list is empty because the offenders we've seen so far
     * (Obsidian) desync with paste too, so forcing it makes things worse.
     */
    private fun isNativeEditText(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString()
        val packageName = node.packageName?.toString()
        if (WebViewEditorDetection.requiresPasteWrite(packageName)) return false
        return WebViewEditorDetection.isNativeEditorClass(className)
    }

    /**
     * Slice of [finalText] that must land in `[match.replaceFrom, match.replaceTo)`
     * inside the editor. Everything outside that range stays untouched.
     */
    private fun replacementSlice(
        match: ExpansionMatch,
        applied: AppliedExpansion,
        originalText: String,
        finalText: String,
    ): String {
        val replacementLength = applied.text.length - originalText.length +
            (match.replaceTo - match.replaceFrom)
        return finalText.substring(
            match.replaceFrom,
            (match.replaceFrom + replacementLength).coerceAtMost(finalText.length),
        )
    }

    /** Apply an action as one atomic field update, with the existing fallback for editors that reject ACTION_SET_TEXT. */
    private fun applyAction(
        node: AccessibilityNodeInfo,
        originalText: String,
        outcome: ActionOutcome,
        settings: AppSettings,
    ): Boolean {
        return setFieldText(
            node = node,
            originalText = originalText,
            newText = outcome.text,
            selectionStart = outcome.selectionStart,
            selectionEnd = outcome.selectionEnd,
            settings = settings,
        )
    }

    private fun setFieldText(
        node: AccessibilityNodeInfo,
        originalText: String,
        newText: String,
        selectionStart: Int,
        selectionEnd: Int,
        settings: AppSettings,
    ): Boolean {
        // Same rationale as applyExpansion: only trust ACTION_SET_TEXT on native EditText.
        val nativeEditor = isNativeEditText(node)
        if (nativeEditor && writeViaSetText(node, newText, selectionStart, selectionEnd)) {
            return true
        }
        val pasted = pasteReplacement(
            node = node,
            start = 0,
            end = originalText.length,
            replacement = newText,
            cursor = selectionStart,
        ) && setSelection(node, selectionStart, selectionEnd)
        if (pasted) return true
        // Last-resort fallback for exotic non-native editors that refuse ACTION_PASTE.
        return !nativeEditor && writeViaSetText(node, newText, selectionStart, selectionEnd)
    }

    /**
     * Sends the whole field text through [AccessibilityNodeInfo.ACTION_SET_TEXT] and
     * commits the resulting selection with the WebView-aware retry helper.
     * Returns false if the editor rejected the action.
     */
    private fun writeViaSetText(
        node: AccessibilityNodeInfo,
        finalText: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return false
        commitSelectionAfterSetText(node, selectionStart, selectionEnd)
        return true
    }

    private fun clearExpansionUndo() {
        reversibleExpansion = null
        suppressedExpansion = null
    }

    private fun handleActionRequest(request: ActionRequest?, text: String) {
        when (request) {
            null -> Unit
            is ActionRequest.Copy -> writeClipboard(request.text)
            is ActionRequest.Share -> shareText(request.text)
            ActionRequest.ToggleSuggestions -> {
                val enabled = !settingsRepository.settings.value.suggestionEnabled
                hideSuggestions()
                scope.launch { settingsRepository.setSuggestionEnabled(enabled) }
            }
            ActionRequest.OpenNewSnippet -> openNewSnippetEditor()
        }
    }

    private fun writeClipboard(text: String) {
        val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("Expanda action", text))
    }

    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            startActivity(
                Intent.createChooser(send, "Share text").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun openNewSnippetEditor() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    putExtra(EXTRA_OPEN_NEW_SNIPPET, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
            )
        }
    }

    private fun pasteReplacement(
        node: AccessibilityNodeInfo,
        start: Int,
        end: Int,
        replacement: String,
        cursor: Int,
    ): Boolean {
        if (!setSelection(node, start, end)) return false
        val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val previous = manager.primaryClip
        manager.setPrimaryClip(ClipData.newPlainText("Expanda replacement", replacement))
        val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (pasted) setSelection(node, cursor, cursor)
        Handler(Looper.getMainLooper()).postDelayed({
            if (previous != null) {
                manager.setPrimaryClip(previous)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.clearPrimaryClip()
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }, CLIPBOARD_RESTORE_DELAY_MS)
        return pasted
    }

    private fun showSuggestions(
        anchorNode: AccessibilityNodeInfo,
        text: String,
        cursor: Int,
        packageName: String,
        settings: AppSettings,
        showAll: Boolean = false,
    ) {
        val typed = if (showAll) "" else currentToken(text, cursor)
        val minimumCharacters = settings.suggestionMinChars.coerceIn(1, MAX_SUGGESTION_LENGTH)
        if (!showAll && !SuggestionMatcher.canShow(typed, minimumCharacters)) {
            hideSuggestions()
            return
        }
        val suggestions = buildList<PopupSuggestion> {
            repository.matches.value.asSequence()
                .filter { it.enabled && it.runsOnAndroid && packageName !in it.excludedPackages }
                .flatMap { match -> match.textTriggers().asSequence().map { match to it } }
                .filter { (_, trigger) ->
                    showAll || SuggestionMatcher.matchRange(trigger, typed, settings.matchFromBeginning) != null
                }
                .mapTo(this) { (match, trigger) -> PopupSuggestion.TextSnippet(match, trigger, typed) }
            if (settings.suggestionShowActions && !showAll) {
                val enabledActions = actionSettingsStore.enabledIds.value
                val shortcutOverrides = actionSettingsStore.shortcutOverrides.value
                ActionEngine.definitions.asSequence()
                    .filter { it.id in enabledActions }
                    .map { definition ->
                        shortcutOverrides[definition.id]
                            ?.takeIf(String::isNotBlank)
                            ?.let { definition.copy(shortcut = it) }
                            ?: definition
                    }
                    .mapNotNull { definition ->
                        actionSuggestionPrefix(
                            shortcut = definition.shortcut,
                            text = text,
                            cursor = cursor,
                            minimumCharacters = minimumCharacters,
                            fromBeginning = settings.matchFromBeginning,
                        )?.let { matchedText -> PopupSuggestion.Action(definition, matchedText) }
                    }
                    .forEach { add(it) }
            }
        }.let { candidates ->
            if (showAll) {
                candidates.sortedWith(
                    compareBy<PopupSuggestion> { if (it is PopupSuggestion.TextSnippet) 0 else 1 }
                        .thenBy { it.shortcut.lowercase() },
                ).take(MAX_BROWSE_SUGGESTIONS)
            } else {
                candidates.sortedWith(
                    compareBy<PopupSuggestion> { it.shortcut.length }
                        .thenBy { if (it is PopupSuggestion.TextSnippet) 0 else 1 }
                        .thenByDescending { (it as? PopupSuggestion.TextSnippet)?.textMatch?.usageCount ?: 0L },
                ).take(MAX_SUGGESTIONS)
            }
        }
        if (suggestions.isEmpty()) {
            hideSuggestions()
            return
        }

        val anchor = createSuggestionAnchor(anchorNode, packageName)
        hideSuggestions()

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val ui = OverlayViews(this, resolveNativeTheme(this, settings))
        val bounds = displayBounds(windowManager)
        val horizontalMargin = dp(12)
        val popupWidth = (bounds.width() * settings.suggestionWidthFraction).toInt()
            .coerceIn(dp(MIN_WIDTH_DP), (bounds.width() - horizontalMargin * 2).coerceAtLeast(dp(MIN_WIDTH_DP)))
        val listMaxHeight = dp(
            settings.suggestionMaxHeightDp.coerceIn(
                SettingsRepository.MIN_SUGGESTION_HEIGHT_DP,
                SettingsRepository.MAX_SUGGESTION_HEIGHT_DP,
            ),
        )
        val estimatedHeight = listMaxHeight + dp(72)

        val container = FrameLayout(this).apply {
            setPadding(
                dp(SuggestionOverlaySpec.HORIZONTAL_PADDING_DP),
                dp(SuggestionOverlaySpec.TOP_PADDING_DP),
                dp(SuggestionOverlaySpec.HORIZONTAL_PADDING_DP),
                dp(SuggestionOverlaySpec.BOTTOM_PADDING_DP),
            )
            background = ui.panel(SuggestionOverlaySpec.PANEL_RADIUS_DP)
            elevation = dp(8).toFloat()
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val handle = object : TextView(this) {
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }.apply {
            this.text = "⠿"
            gravity = Gravity.CENTER
            setTextColor(ui.theme.onSurfaceVariant)
            textSize = ui.scaled(19f)
            includeFontPadding = false
            setPadding(0, dp(1), 0, dp(3))
            contentDescription = "Move suggestion popup"
        }
        val close = TextView(this).apply {
            this.text = "×"
            gravity = Gravity.CENTER
            setTextColor(ui.theme.onSurfaceVariant)
            textSize = ui.scaled(18f)
            includeFontPadding = false
            isClickable = true
            isFocusable = true
            contentDescription = "Close suggestion popup"
            background = ui.surface(9)
            setOnClickListener { hideSuggestions() }
        }
        val resize = object : TextView(this) {
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }.apply {
            this.text = "⤢"
            gravity = Gravity.CENTER
            setTextColor(ui.theme.onSurfaceVariant)
            textSize = ui.scaled(18f)
            includeFontPadding = false
            isClickable = true
            isFocusable = true
            contentDescription = "Resize suggestion popup width and height"
            background = ui.surface(9)
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = BoundedScrollView(this, listMaxHeight).apply {
            isFillViewport = false
            clipToPadding = false
            addView(
                list,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        content.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val footer = FrameLayout(this)
        handle.rotation = 90f
        footer.addView(
            handle,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        footer.addView(
            close,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.CENTER_VERTICAL),
        )
        if (settings.suggestionResizeHandleEnabled) footer.addView(
            resize,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL),
        )
        content.addView(
            footer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(SuggestionOverlaySpec.HANDLE_HEIGHT_DP),
            ),
        )

        suggestions.forEach { suggestion ->
            val row = when (suggestion) {
                is PopupSuggestion.TextSnippet -> createSnippetSuggestionRow(
                    match = suggestion.textMatch,
                    trigger = suggestion.suggestionTrigger,
                    typed = suggestion.matchedText,
                    packageName = packageName,
                    settings = settings,
                    ui = ui,
                    browseMode = showAll,
                )
                is PopupSuggestion.Action -> createActionSuggestionRow(
                    definition = suggestion.definition,
                    typed = suggestion.matchedText,
                    settings = settings,
                    ui = ui,
                )
            }
            list.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(SuggestionOverlaySpec.ROW_GAP_DP) },
            )
        }
        container.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val popupHeight = container.measuredHeight.takeIf { it > 0 } ?: estimatedHeight
        handle.setOnTouchListener(
            createDragListener(
                container = container,
                handle = handle,
                windowManager = windowManager,
                bounds = bounds,
                fallbackHeight = popupHeight,
            ),
        )
        if (settings.suggestionResizeHandleEnabled) {
            resize.setOnTouchListener(
                createResizeListener(
                    container = container,
                    scroll = scroll,
                    windowManager = windowManager,
                    bounds = bounds,
                    fallbackHeight = popupHeight,
                ),
            )
        }

        val safeBottom = safeBottom(bounds)
        val defaultTop = safeTop()
        val storedBottom = when {
            settings.suggestionPositionBottom >= 0 -> settings.suggestionPositionBottom
            settings.suggestionPositionY >= 0 -> settings.suggestionPositionY + popupHeight
            else -> defaultTop + popupHeight
        }
        val params = WindowManager.LayoutParams(
            popupWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (settings.suggestionPositionX >= 0) {
                settings.suggestionPositionX
            } else {
                (bounds.width() - popupWidth) / 2
            }
            y = storedBottom - popupHeight
            x = x.coerceIn(horizontalMargin, (bounds.width() - popupWidth - horizontalMargin).coerceAtLeast(horizontalMargin))
            y = y.coerceIn(defaultTop, (safeBottom - popupHeight).coerceAtLeast(defaultTop))
        }
        runCatching {
            windowManager.addView(container, params)
            suggestionOverlay = container
            suggestionWindowParams = params
            suggestionAnchor = anchor
        }
    }

    private fun createSnippetSuggestionRow(
        match: TextMatch,
        trigger: String,
        typed: String,
        packageName: String,
        settings: AppSettings,
        ui: OverlayViews,
        browseMode: Boolean,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val verticalPadding = if (settings.suggestionCompactList) {
            SuggestionOverlaySpec.COMPACT_ROW_VERTICAL_PADDING_DP
        } else {
            SuggestionOverlaySpec.COMFORTABLE_ROW_VERTICAL_PADDING_DP
        }
        setPadding(dp(10), dp(verticalPadding), dp(10), dp(verticalPadding))
        isClickable = true
        isFocusable = true
        background = ui.surface(SuggestionOverlaySpec.ROW_RADIUS_DP)
        contentDescription = "Text match $trigger: ${matchPreview(match)}"
        setOnClickListener { applySuggestion(match, trigger, packageName, browseMode) }
        addView(TextView(this@ExpansionAccessibilityService).apply {
            text = highlightedShortcut(trigger, typed, settings.matchFromBeginning, ui.theme)
            setTextColor(ui.theme.onSurface)
            textSize = ui.scaled(if (settings.suggestionCompactList) 15f else 14f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        if (settings.suggestionCompactList) {
            if (match.label.isNotBlank()) addView(TextView(this@ExpansionAccessibilityService).apply {
                text = highlightedTemplateTokens(match.label.replace('\n', ' ').trim().take(PREVIEW_LENGTH), ui.theme)
                setTextColor(ui.theme.onSurfaceVariant)
                textSize = ui.scaled(12f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        } else {
            addView(TextView(this@ExpansionAccessibilityService).apply {
                text = highlightedTemplateTokens(match.replace.trim().take(PREVIEW_LENGTH), ui.theme)
                setTextColor(ui.theme.onSurfaceVariant)
                textSize = ui.scaled(15f)
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun createActionSuggestionRow(
        definition: ActionDefinition,
        typed: String,
        settings: AppSettings,
        ui: OverlayViews,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(7), dp(10), dp(7))
        isClickable = true
        isFocusable = true
        background = ui.surface(SuggestionOverlaySpec.ROW_RADIUS_DP, emphasized = true)
        contentDescription = "${definition.category.name.lowercase()} action ${definition.shortcut}: ${definition.title}"
        setOnClickListener { applyActionSuggestion(definition) }

        addView(TextView(this@ExpansionAccessibilityService).apply {
            text = actionCategoryGlyph(definition.category)
            gravity = Gravity.CENTER
            setTextColor(ui.theme.onPrimaryContainer)
            textSize = ui.scaled(17f)
            background = ui.surface(9, emphasized = true)
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(9) })

        addView(LinearLayout(this@ExpansionAccessibilityService).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ExpansionAccessibilityService).apply {
                text = highlightedShortcut(definition.shortcut, typed, settings.matchFromBeginning, ui.theme)
                setTextColor(ui.theme.onSecondaryContainer)
                textSize = ui.scaled(14f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(TextView(this@ExpansionAccessibilityService).apply {
                text = definition.title
                setTextColor(ui.theme.onSecondaryContainer)
                textSize = ui.scaled(12f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (!settings.suggestionCompactList) addView(TextView(this@ExpansionAccessibilityService).apply {
                text = definition.description
                setTextColor(ui.theme.onSurfaceVariant)
                textSize = ui.scaled(12f)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun actionCategoryGlyph(category: ActionCategory): String = when (category) {
        ActionCategory.NUMBER -> "∑"
        ActionCategory.TEXT -> "T"
        ActionCategory.SELECTION -> "◉"
        ActionCategory.DELETION -> "⌫"
        ActionCategory.CURSOR -> "↦"
        ActionCategory.CLIPBOARD -> "▣"
        ActionCategory.ANDROID -> "◇"
        ActionCategory.EXPANDA -> "⚡"
    }

    private fun applySuggestion(
        textMatch: TextMatch,
        trigger: String,
        packageName: String,
        browseMode: Boolean = false,
    ) {
        val anchor = suggestionAnchor ?: run {
            hideSuggestions()
            return
        }
        // Drop any cached snapshot the framework may still be serving before we
        // resolve the editor; suggestion taps often arrive right after a burst
        // of keystrokes, and stale text used to make matchRange fail silently.
        clearAccessibilityCache()
        val node = findAnchoredEditor(anchor) ?: run {
            Log.d(TAG, "applySuggestion: anchored editor gone, hiding popup")
            hideSuggestions()
            return
        }
        try {
            if (!node.isEditable || node.isPassword || isPasswordInput(node.inputType)) {
                Log.d(TAG, "applySuggestion: node no longer editable, skipping")
                return
            }
            runCatching { node.refresh() }
            val text = editableText(node)
            val cursor = node.textSelectionEnd.takeIf { it in 0..text.length } ?: text.length
            val range = SuggestionApplyLocator.locate(
                text = text,
                cursor = cursor,
                trigger = trigger,
                browseMode = browseMode,
            ) ?: run {
                Log.d(TAG, "applySuggestion: cursor $cursor out of range for text of length ${text.length}")
                return
            }
            val currentSettings = settingsRepository.settings.value
            val match = ExpansionMatch(
                match = textMatch,
                replaceFrom = range.start,
                replaceTo = range.end,
                trailingDelimiter = "",
                matchedText = range.matchedText,
            )
            if (textMatch.selectionMode == TemplateSelectionMode.MANUAL && textMatch.replacements.size > 1) {
                @Suppress("DEPRECATION")
                showTemplateChooser(AccessibilityNodeInfo.obtain(node), text, match, packageName, currentSettings)
                return
            }
            val rendered = renderMatch(match)
            if (rendered.requiresInput) {
                @Suppress("DEPRECATION")
                showFormOverlay(AccessibilityNodeInfo.obtain(node), text, match, rendered, packageName, currentSettings)
            } else {
                applyExpansion(node, text, match, rendered, packageName, currentSettings)
            }
        } finally {
            node.recycle()
            hideSuggestions()
        }
    }

    private fun applyActionSuggestion(shownDefinition: ActionDefinition) {
        val anchor = suggestionAnchor ?: run {
            hideSuggestions()
            return
        }
        clearAccessibilityCache()
        val node = findAnchoredEditor(anchor) ?: run {
            hideSuggestions()
            return
        }
        try {
            if (!node.isEditable || node.isPassword || isPasswordInput(node.inputType)) return
            runCatching { node.refresh() }
            val currentSettings = settingsRepository.settings.value
            val enabledActions = actionSettingsStore.enabledIds.value
            if (!currentSettings.suggestionShowActions || shownDefinition.id !in enabledActions) return

            val baseDefinition = ActionEngine.definitions.firstOrNull { it.id == shownDefinition.id } ?: return
            val shortcutOverrides = actionSettingsStore.shortcutOverrides.value
            val definition = shortcutOverrides[baseDefinition.id]
                ?.takeIf(String::isNotBlank)
                ?.let { baseDefinition.copy(shortcut = it) }
                ?: baseDefinition
            val originalText = node.text?.toString() ?: ""
            val cursor = node.textSelectionEnd.takeIf { it in 0..originalText.length } ?: originalText.length
            val minimumCharacters = currentSettings.suggestionMinChars.coerceIn(1, MAX_SUGGESTION_LENGTH)
            // Same rationale as applySuggestion: never silently drop the tap. If the
            // typed prefix no longer matches, treat the tap as "insert the shortcut
            // at the caret and run the action against that".
            val typed = actionSuggestionPrefix(
                shortcut = definition.shortcut,
                text = originalText,
                cursor = cursor,
                minimumCharacters = minimumCharacters,
                fromBeginning = currentSettings.matchFromBeginning,
            )
            val commandStart = if (typed != null) cursor - typed.length else cursor
            val commandEnd = cursor
            val commandText = originalText.replaceRange(commandStart, commandEnd, definition.shortcut)
            val commandCursor = commandStart + definition.shortcut.length
            val outcome = actionEngine.execute(
                ActionContext(
                    text = commandText,
                    cursor = commandCursor,
                    selectionStart = commandCursor,
                    selectionEnd = commandCursor,
                    clipboard = readClipboardText(),
                ),
                enabledActionIds = setOf(definition.id),
                shortcutOverrides = mapOf(definition.id to definition.shortcut),
            ) ?: return
            if (applyAction(node, originalText, outcome, currentSettings)) {
                lastAppliedText = outcome.text
                lastAppliedAt = SystemClock.elapsedRealtime()
                handleActionRequest(outcome.request, outcome.text)
            }
        } finally {
            node.recycle()
            hideSuggestions()
        }
    }

    private fun finishExpansion(match: TextMatch, packageName: String, settings: AppSettings) {
        if (settings.hapticFeedback) vibrate()
        // Sequential templates must advance even when usage statistics are disabled.
        scope.launch { repository.recordExpansion(match, packageName, settings.statisticsEnabled) }
    }

    private fun scheduleSuggestionValidation() {
        if (suggestionOverlay == null || suggestionAnchor == null) return
        cancelSuggestionValidation()
        val validation = Runnable { validateSuggestionAnchor() }
        suggestionValidation = validation
        mainHandler.postDelayed(validation, SUGGESTION_VALIDATION_DELAY_MS)
    }

    private fun cancelSuggestionValidation() {
        suggestionValidation?.let(mainHandler::removeCallbacks)
        suggestionValidation = null
    }

    private fun validateSuggestionAnchor() {
        suggestionValidation = null
        val anchor = suggestionAnchor ?: return
        val active = findAnchoredEditor(anchor)
        if (active == null) {
            hideSuggestions()
        } else {
            active.recycle()
        }
    }

    private fun createSuggestionAnchor(node: AccessibilityNodeInfo, packageName: String): SuggestionAnchor {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return SuggestionAnchor(
            packageName = packageName,
            windowId = node.windowId,
            uniqueId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) node.uniqueId else null,
            viewId = node.viewIdResourceName,
            className = node.className?.toString(),
            bounds = AnchorGeometry(bounds.left, bounds.top, bounds.right, bounds.bottom),
        )
    }

    private fun findAnchoredEditor(anchor: SuggestionAnchor): AccessibilityNodeInfo? {
        val availableWindows = runCatching { windows }.getOrDefault(emptyList())
        val roots = mutableListOf<AccessibilityNodeInfo>()
        if (availableWindows.isNotEmpty()) {
            val anchorWindow = availableWindows.firstOrNull { it.id == anchor.windowId } ?: return null
            if (!anchorWindow.isActive && !anchorWindow.isFocused) return null
            anchorWindow.root?.let(roots::add)
        } else {
            rootInActiveWindow?.let(roots::add)
        }
        var matched: AccessibilityNodeInfo? = null
        roots.forEach { root ->
            if (matched == null) {
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null) {
                    val packageName = focused.packageName?.toString().orEmpty()
                    val candidate = createSuggestionAnchor(focused, packageName)
                    val valid = focused.isEditable &&
                        !focused.isPassword &&
                        !isPasswordInput(focused.inputType) &&
                        SuggestionAnchorPolicy.shouldKeep(anchor, candidate)
                    if (valid) matched = focused else focused.recycle()
                }
            }
            root.recycle()
        }
        return matched
    }

    private fun hideSuggestions() {
        cancelSuggestionValidation()
        val overlay = suggestionOverlay
        suggestionOverlay = null
        suggestionWindowParams = null
        suggestionAnchor = null
        if (overlay != null) {
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(overlay) }
        }
    }

    private fun actionSuggestionPrefix(
        shortcut: String,
        text: String,
        cursor: Int,
        minimumCharacters: Int,
        fromBeginning: Boolean,
    ): String? {
        if (cursor !in 0..text.length) return null
        val beforeCursor = text.substring(0, cursor)
        val minimum = minimumCharacters.coerceIn(1, MAX_SUGGESTION_LENGTH)
        val maximum = minOf(shortcut.length, beforeCursor.length, MAX_SUGGESTION_LENGTH)
        if (maximum < minimum) return null
        for (length in maximum downTo minimum) {
            val suffix = beforeCursor.takeLast(length)
            if (SuggestionMatcher.matchRange(shortcut, suffix, fromBeginning) != null) return suffix
        }
        return null
    }

    private fun currentToken(text: String, cursor: Int): String {
        if (cursor !in 0..text.length) return ""
        val before = text.substring(0, cursor)
        val boundary = before.indexOfLast(Char::isWhitespace)
        return before.substring(boundary + 1)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun highlightedShortcut(
        shortcut: String,
        typed: String,
        fromBeginning: Boolean,
        theme: NativeThemeTokens,
    ): CharSequence {
        val text = SpannableString(shortcut)
        SuggestionMatcher.matchRange(shortcut, typed, fromBeginning)?.let { range ->
            text.setSpan(
                BackgroundColorSpan(theme.warningContainer),
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return text
    }

    private fun highlightedTemplateTokens(value: String, theme: NativeThemeTokens): CharSequence {
        val text = SpannableString(value)
        Regex("\\{\\{[^{}]+\\}\\}|\\{[^{}]+\\}").findAll(value).forEach { match ->
            text.setSpan(BackgroundColorSpan(theme.primaryContainer), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(ForegroundColorSpan(theme.onPrimaryContainer), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    private fun matchPreview(match: TextMatch): String =
        match.label.ifBlank { match.replace }
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(PREVIEW_LENGTH)

    private fun displayBounds(windowManager: WindowManager): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(windowManager.currentWindowMetrics.bounds)
        } else {
            @Suppress("DEPRECATION")
            android.util.DisplayMetrics().also { metrics ->
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
            }.let { metrics -> Rect(0, 0, metrics.widthPixels, metrics.heightPixels) }
        }
    }

    /** Default top edge for the suggestion popup when no position has been saved yet. */
    private fun safeTop(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusInset = if (resourceId != 0) resources.getDimensionPixelSize(resourceId) else 0
        return (statusInset + dp(12)).coerceAtLeast(dp(12))
    }

    /** Keep the window above both 3-button navigation bars and gesture handles. */
    private fun safeBottom(bounds: Rect): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navigationInset = (if (resourceId != 0) resources.getDimensionPixelSize(resourceId) else 0)
            .coerceAtLeast(dp(24))
        return (bounds.bottom - navigationInset - dp(14)).coerceAtLeast(dp(80))
    }

    private fun constrainSuggestionPosition(
        container: View,
        params: WindowManager.LayoutParams,
        bounds: Rect,
        fallbackHeight: Int,
    ) {
        val margin = dp(12)
        // LayoutParams changes before View.width catches up while resizing. Prefer the
        // requested window width so clamping never jumps back to the previous size.
        val width = params.width.takeIf { it > 0 }
            ?: container.measuredWidth.takeIf { it > 0 }
            ?: container.width
        val height = container.measuredHeight.takeIf { it > 0 }
            ?: container.height.takeIf { it > 0 }
            ?: fallbackHeight
        val maxX = (bounds.width() - width - margin).coerceAtLeast(margin)
        val maxY = (safeBottom(bounds) - height).coerceAtLeast(dp(12))
        params.x = params.x.coerceIn(margin, maxX)
        params.y = params.y.coerceIn(dp(12), maxY)
    }

    private fun createDragListener(
        container: View,
        handle: View,
        windowManager: WindowManager,
        bounds: Rect,
        fallbackHeight: Int,
    ): View.OnTouchListener {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val params = suggestionWindowParams ?: return@OnTouchListener false
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val params = suggestionWindowParams ?: return@OnTouchListener false
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (!dragging && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        dragging = true
                        container.alpha = DRAG_ALPHA
                    }
                    if (dragging) {
                        params.x = startX + deltaX.toInt()
                        params.y = startY + deltaY.toInt()
                        constrainSuggestionPosition(container, params, bounds, fallbackHeight)
                        runCatching { windowManager.updateViewLayout(container, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val params = suggestionWindowParams ?: return@OnTouchListener false
                    if (dragging) {
                        container.alpha = 1f
                        constrainSuggestionPosition(container, params, bounds, fallbackHeight)
                        runCatching { windowManager.updateViewLayout(container, params) }
                        // Persist only after the gesture ends; DataStore is never written for every MOVE.
                        scope.launch {
                            settingsRepository.setSuggestionPosition(params.x, params.y, container.height)
                        }
                    } else {
                        handle.performClick()
                    }
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun createResizeListener(
        container: View,
        scroll: BoundedScrollView,
        windowManager: WindowManager,
        bounds: Rect,
        fallbackHeight: Int,
    ): View.OnTouchListener {
        var downX = 0f
        var downY = 0f
        var startWidth = 0
        var startListHeight = 0
        var fixedRight = 0
        var fixedBottom = 0
        var maximumListHeight = 0
        return View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val params = suggestionWindowParams ?: return@OnTouchListener false
                    downX = event.rawX
                    downY = event.rawY
                    startWidth = params.width
                    startListHeight = scroll.maxHeightPx
                    fixedRight = params.x + params.width
                    fixedBottom = params.y + (
                        container.measuredHeight.takeIf { it > 0 }
                            ?: container.height.takeIf { it > 0 }
                            ?: fallbackHeight
                        )
                    val nonListHeight = (
                        container.measuredHeight.takeIf { it > 0 }
                            ?: container.height.takeIf { it > 0 }
                            ?: fallbackHeight
                        ) - scroll.measuredHeight
                    maximumListHeight = minOf(
                        dp(SettingsRepository.MAX_SUGGESTION_HEIGHT_DP),
                        fixedBottom - dp(12) - nonListHeight.coerceAtLeast(0),
                    ).coerceAtLeast(dp(SettingsRepository.MIN_SUGGESTION_HEIGHT_DP))
                    container.alpha = DRAG_ALPHA
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val params = suggestionWindowParams ?: return@OnTouchListener false
                    val result = SuggestionResizePolicy.resize(
                        startWidthPx = startWidth,
                        startListHeightPx = startListHeight,
                        horizontalDragPx = (event.rawX - downX).toInt(),
                        verticalDragPx = (event.rawY - downY).toInt(),
                        minWidthPx = dp(MIN_WIDTH_DP),
                        maxWidthPx = (bounds.width() - dp(24)).coerceAtLeast(dp(MIN_WIDTH_DP)),
                        minListHeightPx = dp(SettingsRepository.MIN_SUGGESTION_HEIGHT_DP),
                        maxListHeightPx = maximumListHeight,
                    )
                    scroll.setMaxHeight(result.listHeightPx)
                    params.width = result.widthPx
                    params.x = fixedRight - result.widthPx
                    container.measure(
                        View.MeasureSpec.makeMeasureSpec(result.widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    )
                    params.y = fixedBottom - container.measuredHeight.coerceAtLeast(1)
                    constrainSuggestionPosition(container, params, bounds, fallbackHeight)
                    runCatching { windowManager.updateViewLayout(container, params) }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val params = suggestionWindowParams ?: return@OnTouchListener false
                    container.alpha = 1f
                    constrainSuggestionPosition(container, params, bounds, fallbackHeight)
                    runCatching { windowManager.updateViewLayout(container, params) }
                    scope.launch {
                        settingsRepository.setSuggestionLayout(
                            params.x,
                            params.y,
                            container.measuredHeight.takeIf { it > 0 }
                                ?: container.height.takeIf { it > 0 }
                                ?: fallbackHeight,
                            params.width.toFloat() / bounds.width().coerceAtLeast(1),
                            (scroll.maxHeightPx / resources.displayMetrics.density).roundToInt(),
                        )
                    }
                    view.performClick()
                    true
                }

                else -> false
            }
        }
    }

    private class BoundedScrollView(context: android.content.Context, maxHeight: Int) : ScrollView(context) {
        var maxHeightPx: Int = maxHeight
            private set

        fun setMaxHeight(heightPx: Int) {
            if (maxHeightPx == heightPx) return
            maxHeightPx = heightPx
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val boundedHeight = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, boundedHeight)
        }
    }

    companion object {
        @Volatile private var activeService: WeakReference<ExpansionAccessibilityService>? = null

        /** Opens the real overlay for the currently focused editor, without requiring typed characters. */
        fun requestSuggestionOverlay(): Boolean {
            val service = activeService?.get() ?: return false
            service.mainHandler.post(service::showAllSuggestionsForFocusedInput)
            return true
        }

        internal fun onClipboardCaptured() {
            activeService?.get()?.let { s ->
                s.mainHandler.post(s::onClipboardCaptureComplete)
            }
        }

        internal fun onClipboardCaptureFailed() {
            activeService?.get()?.let { s ->
                s.mainHandler.post(s::onClipboardCaptureCancel)
            }
        }

        private const val TAG = "ExpandaAccessibility"
        private const val REENTRANCY_WINDOW_MS = 1_000L
        private const val CLIPBOARD_RETRY_ATTEMPTS = 4
        private const val CLIPBOARD_RETRY_DELAY_MS = 200L
        private const val CLIPBOARD_OVERLAY_TIMEOUT_MS = 800L
        private const val CLIPBOARD_OVERLAY_SETTLE_MS = 100L
        private const val SUGGESTION_VALIDATION_DELAY_MS = 140L
        private const val CLIPBOARD_RESTORE_DELAY_MS = 250L
        /** Two frames at 60 Hz — enough for Blink to finish applying ACTION_SET_TEXT. */
        private const val WEBVIEW_SELECTION_RETRY_DELAY_MS = 32L
        private const val MAX_SUGGESTIONS = 24
        private const val MAX_BROWSE_SUGGESTIONS = 200
        private const val MAX_SUGGESTION_LENGTH = 32
        private const val PREVIEW_LENGTH = 220
        private const val DRAG_ALPHA = 0.55f
        private const val MIN_WIDTH_DP = 180
        /** MainActivity may consume this extra to open its snippet editor. */
        const val EXTRA_OPEN_NEW_SNIPPET = "dev.diego.expanda.OPEN_NEW_SNIPPET"

    }
}

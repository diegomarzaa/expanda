package dev.diego.expanda.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipDescription
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
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.BackgroundColorSpan
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.diego.expanda.ExpandaApplication
import dev.diego.expanda.MainActivity
import dev.diego.expanda.engine.ActionCategory
import dev.diego.expanda.engine.ActionContext
import dev.diego.expanda.engine.ActionDefinition
import dev.diego.expanda.engine.ActionEngine
import dev.diego.expanda.engine.ActionOutcome
import dev.diego.expanda.engine.ActionRequest
import dev.diego.expanda.engine.ExpansionEngine
import dev.diego.expanda.engine.ExpansionMatch
import dev.diego.expanda.engine.TemplateRenderer
import dev.diego.expanda.engine.RenderedTemplate
import dev.diego.expanda.engine.TemplateSelector
import dev.diego.expanda.data.AppSettings
import dev.diego.expanda.data.Snippet
import dev.diego.expanda.data.TemplateSelectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ExpansionAccessibilityService : AccessibilityService() {
    private sealed interface PopupSuggestion {
        val shortcut: String
        val matchedText: String

        data class TextSnippet(val snippet: Snippet, override val matchedText: String) : PopupSuggestion {
            override val shortcut: String get() = snippet.shortcut
        }

        data class Action(val definition: ActionDefinition, override val matchedText: String) : PopupSuggestion {
            override val shortcut: String get() = definition.shortcut
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = ExpansionEngine()
    private val templateSelector = TemplateSelector()
    private val actionEngine = ActionEngine()
    private val repository by lazy { (application as ExpandaApplication).repository }
    private val settingsRepository by lazy { (application as ExpandaApplication).settingsRepository }
    private val actionSettingsStore by lazy { (application as ExpandaApplication).actionSettingsStore }

    private var lastAppliedText: String? = null
    private var lastAppliedAt = 0L
    private var suggestionOverlay: View? = null
    private var suggestionWindowParams: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var suggestionValidation: Runnable? = null
    private var suggestionAnchor: SuggestionAnchor? = null
    private var formOverlay: View? = null
    private var pendingFormNode: AccessibilityNodeInfo? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
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
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        val node = event.source
            ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return
        try {
            if (!node.isEditable || node.isPassword || isPasswordInput(node.inputType)) return
            val packageName = event.packageName?.toString()?.takeIf { it.isNotEmpty() }
                ?: node.packageName?.toString().orEmpty()
            if (packageName.isEmpty() || packageName == applicationContext.packageName) return
            val settings = settingsRepository.settings.value
            if (!settings.expansionEnabled || settings.isPaused || packageName in settings.globallyExcludedPackages) return

            val text = node.text?.toString() ?: event.text.firstOrNull()?.toString() ?: return
            if (text == lastAppliedText && SystemClock.elapsedRealtime() - lastAppliedAt < REENTRANCY_WINDOW_MS) return
            val cursor = node.textSelectionEnd.takeIf { it in 0..text.length } ?: text.length
            val selectionStart = node.textSelectionStart.takeIf { it in 0..text.length } ?: cursor
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
                hideSuggestions()
                if (applyAction(node, text, action, settings)) {
                    lastAppliedText = action.text
                    lastAppliedAt = SystemClock.elapsedRealtime()
                    handleActionRequest(action.request, action.text)
                }
                return
            }
            val match = engine.findMatch(text, cursor, repository.snippets.value, packageName)
            if (match == null) {
                if (settings.suggestionEnabled) showSuggestions(node, text, cursor, packageName, settings)
                else hideSuggestions()
                return
            }
            hideSuggestions()
            if (match.snippet.selectionMode == TemplateSelectionMode.MANUAL && match.snippet.allTemplates().size > 1) {
                @Suppress("DEPRECATION")
                showTemplateChooser(AccessibilityNodeInfo.obtain(node), text, match, packageName, settings)
                return
            }
            val rendered = renderSnippet(match.snippet)
            if (rendered.requiresInput) {
                @Suppress("DEPRECATION")
                showFormOverlay(AccessibilityNodeInfo.obtain(node), text, match, rendered, packageName, settings)
                return
            }
            applyExpansion(node, text, match, rendered, packageName, settings)
        } finally {
            node.recycle()
        }
    }

    override fun onInterrupt() {
        hideSuggestions()
        hideFormOverlay()
    }

    override fun onDestroy() {
        hideSuggestions()
        hideFormOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun isPasswordInput(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun readClipboardText(): String {
        val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = manager.primaryClip ?: return ""
        if (!clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        ) return ""
        return clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
    }

    private fun renderSnippet(snippet: Snippet, manualIndex: Int? = null): RenderedTemplate {
        val selected = templateSelector.select(snippet, manualIndex).text
        return TemplateRenderer(
            clipboard = ::readClipboardText,
            snippetResolver = { shortcut ->
                repository.snippets.value.firstOrNull {
                    it.shortcut.equals(shortcut, ignoreCase = true)
                }?.let { templateSelector.select(it).text }
            },
        ).render(selected)
    }

    private fun applyExpansion(
        node: AccessibilityNodeInfo,
        originalText: String,
        match: ExpansionMatch,
        rendered: RenderedTemplate,
        packageName: String,
        settings: AppSettings,
    ): Boolean {
        val applied = engine.applyMatch(originalText, match, rendered)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, applied.text)
        }
        val replaced = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (replaced) {
            setSelection(node, applied.cursor, applied.cursor)
        } else if (!settings.pasteFallbackEnabled || !pasteReplacement(
                node, match.replaceFrom, match.replaceTo,
                rendered.text + match.trailingDelimiter, applied.cursor,
            )
        ) {
            return false
        }
        lastAppliedText = applied.text
        lastAppliedAt = SystemClock.elapsedRealtime()
        finishExpansion(match.snippet, packageName, settings)
        if (rendered.actions.any { it.type == dev.diego.expanda.engine.TemplateActionType.SEND }) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                }
            }, 100L)
        }
        return true
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
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(34, 32, 40))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.argb(190, 180, 150, 230))
            }
            addView(TextView(this@ExpansionAccessibilityService).apply {
                this.text = "Choose template"
                textSize = 19f
                setTextColor(Color.WHITE)
                setPadding(dp(6), dp(2), dp(6), dp(8))
            })
        }
        match.snippet.allTemplates().forEachIndexed { index, template ->
            list.addView(TextView(this).apply {
                this.text = "${index + 1}. ${template.replace('\n', ' ').take(180)}"
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.argb(55, 255, 255, 255))
                    cornerRadius = dp(10).toFloat()
                }
                setOnClickListener {
                    val rendered = renderSnippet(match.snippet, index)
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
        list.addView(Button(this).apply {
            this.text = "Cancel"
            setOnClickListener { hideFormOverlay() }
        })
        val scroll = BoundedScrollView(this, dp(520)).apply { addView(list) }
        val params = WindowManager.LayoutParams(
            (displayBounds(windowManager).width() * 0.9f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.3f
        }
        runCatching {
            windowManager.addView(scroll, params)
            formOverlay = scroll
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
        val fields = rendered.fields.distinctBy { it.name }
        val inputs = linkedMapOf<String, EditText>()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.rgb(34, 32, 40))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.argb(190, 180, 150, 230))
            }
            addView(TextView(this@ExpansionAccessibilityService).apply {
                this.text = "Complete snippet"
                textSize = 20f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, dp(10))
            })
        }
        fields.forEach { field ->
            panel.addView(TextView(this).apply {
                this.text = field.label
                setTextColor(Color.argb(220, 235, 225, 245))
                textSize = 14f
                setPadding(0, dp(6), 0, dp(3))
            })
            val input = EditText(this).apply {
                setText(field.defaultValue)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setSingleLine(false)
                minLines = 1
                maxLines = 4
                background = GradientDrawable().apply {
                    setColor(Color.argb(80, 255, 255, 255))
                    cornerRadius = dp(10).toFloat()
                }
                setPadding(dp(12), dp(9), dp(12), dp(9))
            }
            inputs[field.name] = input
            panel.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val buttons = LinearLayout(this).apply {
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
        }
        buttons.addView(Button(this).apply {
            this.text = "Cancel"
            setOnClickListener { hideFormOverlay() }
        })
        buttons.addView(Button(this).apply {
            this.text = "Insert"
            setOnClickListener {
                val values = inputs.mapValues { it.value.text.toString() }
                val completed = rendered.fillFields(values)
                formOverlay?.let { runCatching { windowManager.removeView(it) } }
                formOverlay = null
                pendingFormNode = null
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        applyExpansion(node, originalText, match, completed, packageName, settings)
                    } finally {
                        @Suppress("DEPRECATION")
                        node.recycle()
                    }
                }, 120L)
            }
        })
        panel.addView(buttons)
        val width = (displayBounds(windowManager).width() * 0.9f).toInt()
        val params = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.35f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        runCatching {
            windowManager.addView(panel, params)
            formOverlay = panel
            inputs.values.firstOrNull()?.let { first ->
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

    private fun hideFormOverlay() {
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

    /** Apply an action as one atomic field update, with the existing fallback for editors that reject ACTION_SET_TEXT. */
    private fun applyAction(
        node: AccessibilityNodeInfo,
        originalText: String,
        outcome: ActionOutcome,
        settings: AppSettings,
    ): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                outcome.text,
            )
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            setSelection(node, outcome.selectionStart, outcome.selectionEnd)
            return true
        }
        if (!settings.pasteFallbackEnabled) return false
        return pasteReplacement(
            node = node,
            start = 0,
            end = originalText.length,
            replacement = outcome.text,
            cursor = outcome.selectionStart,
        ) && setSelection(node, outcome.selectionStart, outcome.selectionEnd)
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

    private fun showSuggestions(anchorNode: AccessibilityNodeInfo, text: String, cursor: Int, packageName: String, settings: AppSettings) {
        val typed = currentToken(text, cursor)
        val minimumCharacters = settings.suggestionMinChars.coerceIn(1, MAX_SUGGESTION_LENGTH)
        if (!SuggestionMatcher.canShow(typed, minimumCharacters)) {
            hideSuggestions()
            return
        }
        val suggestions = buildList<PopupSuggestion> {
            repository.snippets.value.asSequence()
                .filter { it.enabled && packageName !in it.excludedPackages }
                .filter { SuggestionMatcher.matchRange(it.shortcut, typed, settings.matchFromBeginning) != null }
                .mapTo(this) { PopupSuggestion.TextSnippet(it, typed) }
            if (settings.suggestionShowActions) {
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
        }.sortedWith(
            compareBy<PopupSuggestion> { it.shortcut.length }
                .thenBy { if (it is PopupSuggestion.TextSnippet) 0 else 1 }
                .thenByDescending { (it as? PopupSuggestion.TextSnippet)?.snippet?.usageCount ?: 0L },
        ).take(MAX_SUGGESTIONS)
        if (suggestions.isEmpty()) {
            hideSuggestions()
            return
        }

        val anchor = createSuggestionAnchor(anchorNode, packageName)
        hideSuggestions()

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val bounds = displayBounds(windowManager)
        val horizontalMargin = dp(12)
        val popupWidth = (bounds.width() * POPUP_WIDTH_FRACTION).toInt()
            .coerceIn(dp(240), (bounds.width() - horizontalMargin * 2).coerceAtLeast(dp(240)))
        val listMaxHeight = dp(settings.suggestionMaxHeightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP))
        val estimatedHeight = listMaxHeight + dp(72)

        val container = FrameLayout(this).apply {
            setPadding(dp(8), dp(4), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.argb(246, 32, 30, 38))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(170, 164, 140, 119))
            }
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

        val handle = TextView(this).apply {
            this.text = "⠿"
            gravity = Gravity.CENTER
            setTextColor(Color.argb(210, 235, 220, 208))
            textSize = 19f
            includeFontPadding = false
            setPadding(0, dp(1), 0, dp(3))
            contentDescription = "Move suggestion popup"
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

        handle.rotation = 90f
        content.addView(
            handle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(26),
            ),
        )

        suggestions.forEach { suggestion ->
            val row = when (suggestion) {
                is PopupSuggestion.TextSnippet -> createSnippetSuggestionRow(
                    snippet = suggestion.snippet,
                    typed = suggestion.matchedText,
                    packageName = packageName,
                    settings = settings,
                )
                is PopupSuggestion.Action -> createActionSuggestionRow(
                    definition = suggestion.definition,
                    typed = suggestion.matchedText,
                    settings = settings,
                )
            }
            list.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(4) },
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

        val safeBottom = safeBottom(bounds)
        val storedBottom = when {
            settings.suggestionPositionBottom >= 0 -> settings.suggestionPositionBottom
            settings.suggestionPositionY >= 0 -> settings.suggestionPositionY + popupHeight
            else -> safeBottom
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
            y = y.coerceIn(dp(12), (safeBottom - popupHeight).coerceAtLeast(dp(12)))
        }
        runCatching {
            windowManager.addView(container, params)
            suggestionOverlay = container
            suggestionWindowParams = params
            suggestionAnchor = anchor
        }
    }

    private fun createSnippetSuggestionRow(
        snippet: Snippet,
        typed: String,
        packageName: String,
        settings: AppSettings,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), if (settings.suggestionCompactList) dp(7) else dp(8), dp(10), if (settings.suggestionCompactList) dp(7) else dp(8))
        isClickable = true
        isFocusable = true
        background = GradientDrawable().apply {
            setColor(Color.argb(48, 255, 255, 255))
            cornerRadius = dp(11).toFloat()
        }
        contentDescription = "Text snippet ${snippet.shortcut}: ${snippetPreview(snippet)}"
        setOnClickListener { applySuggestion(snippet, packageName, settings) }
        addView(TextView(this@ExpansionAccessibilityService).apply {
            text = highlightedShortcut(snippet.shortcut, typed, settings.matchFromBeginning)
            setTextColor(Color.WHITE)
            textSize = if (settings.suggestionCompactList) 15f else 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        if (settings.suggestionCompactList) {
            if (snippet.label.isNotBlank()) addView(TextView(this@ExpansionAccessibilityService).apply {
                text = highlightedTemplateTokens(snippet.label.replace('\n', ' ').trim().take(PREVIEW_LENGTH))
                setTextColor(Color.argb(178, 235, 230, 235))
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        } else {
            addView(TextView(this@ExpansionAccessibilityService).apply {
                text = highlightedTemplateTokens(snippet.content.trim().take(PREVIEW_LENGTH))
                setTextColor(Color.argb(225, 248, 243, 248))
                textSize = 15f
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
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(7), dp(10), dp(7))
        isClickable = true
        isFocusable = true
        background = GradientDrawable().apply {
            setColor(Color.argb(72, 126, 92, 164))
            cornerRadius = dp(11).toFloat()
            setStroke(dp(1), Color.argb(100, 211, 184, 240))
        }
        contentDescription = "${definition.category.name.lowercase()} action ${definition.shortcut}: ${definition.title}"
        setOnClickListener { applyActionSuggestion(definition) }

        addView(TextView(this@ExpansionAccessibilityService).apply {
            text = actionCategoryGlyph(definition.category)
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(238, 223, 255))
            textSize = 17f
            background = GradientDrawable().apply {
                setColor(Color.argb(90, 211, 184, 240))
                cornerRadius = dp(9).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(9) })

        addView(LinearLayout(this@ExpansionAccessibilityService).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ExpansionAccessibilityService).apply {
                text = highlightedShortcut(definition.shortcut, typed, settings.matchFromBeginning)
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(TextView(this@ExpansionAccessibilityService).apply {
                text = definition.title
                setTextColor(Color.argb(215, 238, 229, 242))
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (!settings.suggestionCompactList) addView(TextView(this@ExpansionAccessibilityService).apply {
                text = definition.description
                setTextColor(Color.argb(175, 235, 230, 235))
                textSize = 12f
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

    private fun applySuggestion(snippet: Snippet, packageName: String, settings: AppSettings) {
        val anchor = suggestionAnchor ?: run {
            hideSuggestions()
            return
        }
        val node = findAnchoredEditor(anchor) ?: run {
            hideSuggestions()
            return
        }
        try {
            if (!node.isEditable || node.isPassword || isPasswordInput(node.inputType)) return
            val text = node.text?.toString() ?: return
            val cursor = node.textSelectionEnd.takeIf { it in 0..text.length } ?: text.length
            val typed = currentToken(text, cursor)
            val currentSettings = settingsRepository.settings.value
            val minimumCharacters = currentSettings.suggestionMinChars.coerceIn(1, MAX_SUGGESTION_LENGTH)
            if (!SuggestionMatcher.canShow(typed, minimumCharacters)) return
            if (SuggestionMatcher.matchRange(snippet.shortcut, typed, currentSettings.matchFromBeginning) == null) return
            val start = cursor - typed.length
            val match = ExpansionMatch(
                snippet = snippet,
                replaceFrom = start,
                replaceTo = cursor,
                trailingDelimiter = "",
            )
            if (snippet.selectionMode == TemplateSelectionMode.MANUAL && snippet.allTemplates().size > 1) {
                @Suppress("DEPRECATION")
                showTemplateChooser(AccessibilityNodeInfo.obtain(node), text, match, packageName, currentSettings)
                return
            }
            val rendered = renderSnippet(snippet)
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
        val node = findAnchoredEditor(anchor) ?: run {
            hideSuggestions()
            return
        }
        try {
            if (!node.isEditable || node.isPassword || isPasswordInput(node.inputType)) return
            val currentSettings = settingsRepository.settings.value
            val enabledActions = actionSettingsStore.enabledIds.value
            if (!currentSettings.suggestionShowActions || shownDefinition.id !in enabledActions) return

            val baseDefinition = ActionEngine.definitions.firstOrNull { it.id == shownDefinition.id } ?: return
            val shortcutOverrides = actionSettingsStore.shortcutOverrides.value
            val definition = shortcutOverrides[baseDefinition.id]
                ?.takeIf(String::isNotBlank)
                ?.let { baseDefinition.copy(shortcut = it) }
                ?: baseDefinition
            val originalText = node.text?.toString() ?: return
            val cursor = node.textSelectionEnd.takeIf { it in 0..originalText.length } ?: originalText.length
            val minimumCharacters = currentSettings.suggestionMinChars.coerceIn(1, MAX_SUGGESTION_LENGTH)
            val typed = actionSuggestionPrefix(
                shortcut = definition.shortcut,
                text = originalText,
                cursor = cursor,
                minimumCharacters = minimumCharacters,
                fromBeginning = currentSettings.matchFromBeginning,
            ) ?: return

            val commandStart = cursor - typed.length
            val commandText = originalText.replaceRange(commandStart, cursor, definition.shortcut)
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

    private fun finishExpansion(snippet: Snippet, packageName: String, settings: AppSettings) {
        if (settings.hapticFeedback) vibrate()
        // Sequential templates must advance even when usage statistics are disabled.
        scope.launch { repository.recordExpansion(snippet, packageName, settings.statisticsEnabled) }
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
    ): CharSequence {
        val text = SpannableString(shortcut)
        SuggestionMatcher.matchRange(shortcut, typed, fromBeginning)?.let { range ->
            text.setSpan(
                BackgroundColorSpan(Color.argb(210, 255, 196, 91)),
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return text
    }

    private fun highlightedTemplateTokens(value: String): CharSequence {
        val text = SpannableString(value)
        Regex("\\{\\{[^{}]+\\}\\}|\\{[^{}]+\\}").findAll(value).forEach { match ->
            text.setSpan(BackgroundColorSpan(Color.argb(120, 126, 92, 164)), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(ForegroundColorSpan(Color.rgb(238, 223, 255)), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    private fun snippetPreview(snippet: Snippet): String =
        snippet.label.ifBlank { snippet.content }
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
        val width = container.width.takeIf { it > 0 } ?: params.width
        val height = container.height.takeIf { it > 0 } ?: fallbackHeight
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

    private class BoundedScrollView(context: android.content.Context, private val maxHeight: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val boundedHeight = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, boundedHeight)
        }
    }

    companion object {
        private const val REENTRANCY_WINDOW_MS = 1_000L
        private const val SUGGESTION_VALIDATION_DELAY_MS = 140L
        private const val CLIPBOARD_RESTORE_DELAY_MS = 250L
        private const val MAX_SUGGESTIONS = 24
        private const val MAX_SUGGESTION_LENGTH = 32
        private const val MIN_HEIGHT_DP = 120
        private const val MAX_HEIGHT_DP = 720
        private const val PREVIEW_LENGTH = 220
        private const val DRAG_ALPHA = 0.55f
        private const val POPUP_WIDTH_FRACTION = 0.92f
        /** MainActivity may consume this extra to open its snippet editor. */
        const val EXTRA_OPEN_NEW_SNIPPET = "dev.diego.expanda.OPEN_NEW_SNIPPET"

    }
}

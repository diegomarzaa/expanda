package dev.diego.expanda.service.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.ViewCompat
import dev.diego.expanda.ui.theme.NativeThemeTokens

/** Small native component kit for accessibility windows, backed by Material semantic roles. */
class OverlayViews(
    private val context: Context,
    val theme: NativeThemeTokens,
) {
    fun panel(radiusDp: Int = 22): GradientDrawable = drawable(
        color = theme.surface,
        radiusDp = radiusDp,
        strokeColor = withAlpha(theme.outline, 55),
        strokeDp = 1,
    )

    fun surface(radiusDp: Int = 12, emphasized: Boolean = false): GradientDrawable = drawable(
        color = if (emphasized) theme.secondaryContainer else theme.surfaceContainer,
        radiusDp = radiusDp,
        strokeColor = if (emphasized) theme.primary else null,
        strokeDp = if (emphasized) 1 else 0,
    )

    fun title(value: CharSequence, sizeSp: Float = 20f): TextView = text(
        value = value,
        sizeSp = sizeSp,
        color = theme.onSurface,
    ).apply {
        ViewCompat.setAccessibilityHeading(this, true)
    }

    fun body(
        value: CharSequence,
        sizeSp: Float = 15f,
        secondary: Boolean = false,
    ): TextView = text(
        value = value,
        sizeSp = sizeSp,
        color = if (secondary) theme.onSurfaceVariant else theme.onSurface,
    )

    fun text(value: CharSequence, sizeSp: Float, color: Int): TextView = TextView(context).apply {
        text = value
        textSize = scaled(sizeSp)
        setTextColor(color)
        includeFontPadding = false
    }

    fun button(
        label: String,
        primary: Boolean = false,
        onClick: () -> Unit,
    ): TextView = footerButton(label, primary, onClick)

    fun footerButton(
        label: String,
        primary: Boolean = false,
        onClick: () -> Unit,
    ): TextView = TextView(context).apply {
        text = label
        textSize = scaled(15f)
        gravity = Gravity.CENTER
        includeFontPadding = false
        minHeight = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(20), dp(14), dp(20), dp(14))
        setTextColor(if (primary) theme.onPrimary else theme.onSurface)
        background = drawable(
            color = if (primary) theme.primary else theme.surfaceContainerHigh,
            radiusDp = 24,
            strokeColor = if (primary) null else theme.outline,
            strokeDp = if (primary) 0 else 1,
        )
        isClickable = true
        isFocusable = true
        contentDescription = label
        foreground = selectableForeground()
        setOnClickListener { onClick() }
    }

    fun input(label: String, defaultValue: String): EditText = EditText(context).apply {
        hint = label
        setText(defaultValue)
        setTextColor(theme.onSurface)
        setHintTextColor(theme.onSurfaceVariant)
        highlightColor = withAlpha(theme.primary, 70)
        textSize = scaled(16f)
        setSingleLine(false)
        minLines = 1
        maxLines = 4
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        background = drawable(theme.surfaceContainer, 12, theme.outline, 1)
        setPadding(dp(14), dp(11), dp(14), dp(11))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable?.setTint(theme.primary)
        }
    }

    fun fieldGroup(label: String, field: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(10) }
        addView(
            body(label, sizeSp = 13f, secondary = true).apply {
                setPadding(0, 0, 0, dp(4))
            },
        )
        addView(
            field,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    fun choiceOption(label: String, onClick: () -> Unit): TextView = body(label).apply {
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = surface()
        isClickable = true
        isFocusable = true
        contentDescription = label
        foreground = selectableForeground()
        setOnClickListener { onClick() }
    }

    fun divider(): View = View(context).apply {
        setBackgroundColor(withAlpha(theme.outline, 70))
    }

    fun pickerField(value: String): TextView = body(value, 16f).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(11), dp(14), dp(11))
        background = surface(12)
        minHeight = dp(48)
        isClickable = true
        isFocusable = true
        foreground = selectableForeground()
    }

    fun spinner(options: List<String>): Spinner = Spinner(context).apply {
        adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                optionView(getItem(position).orEmpty(), dropdown = false)

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                optionView(getItem(position).orEmpty(), dropdown = true)

            private fun optionView(value: String, dropdown: Boolean): TextView = body(value).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(if (dropdown) 13 else 10), dp(14), dp(if (dropdown) 13 else 10))
                setBackgroundColor(if (dropdown) theme.surfaceContainerHigh else Color.TRANSPARENT)
            }
        }
        background = drawable(theme.surfaceContainer, 12, theme.outline, 1)
        minimumHeight = dp(48)
        isFocusable = true
    }

    fun warning(value: String): TextView = body(value, secondary = false).apply {
        setTextColor(theme.onWarningContainer)
        background = drawable(theme.warningContainer, 12)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    fun scaled(valueSp: Float): Float = valueSp * theme.textScale

    private fun drawable(
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
        strokeDp: Int = 0,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null && strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
    }

    private fun selectableForeground() = context.obtainStyledAttributes(
        intArrayOf(android.R.attr.selectableItemBackground),
    ).let { attributes ->
        attributes.getDrawable(0).also { attributes.recycle() }
    }
}

private fun withAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

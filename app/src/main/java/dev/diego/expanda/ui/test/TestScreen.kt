package dev.diego.expanda.ui.test

import android.graphics.Color as AndroidColor
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.diego.expanda.R
import dev.diego.expanda.service.ExpansionAccessibilityService
import kotlinx.coroutines.delay

/** A real native editor so the accessibility service can be exercised inside Expanda. */
@Composable
fun TestScreen(serviceEnabled: Boolean, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val horizontalPadding = with(density) { 18.dp.roundToPx() }
    val verticalPadding = with(density) { 16.dp.roundToPx() }
    val textSizePx = with(density) { 16.sp.toPx() }
    var editor by remember { mutableStateOf<EditText?>(null) }

    LaunchedEffect(active, editor) {
        val currentEditor = editor ?: return@LaunchedEffect
        if (active) {
            delay(180)
            currentEditor.post {
                currentEditor.requestFocus()
                val keyboard = currentEditor.context.getSystemService(InputMethodManager::class.java)
                keyboard?.showSoftInput(currentEditor, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (serviceEnabled) colors.secondaryContainer else colors.errorContainer,
            ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (serviceEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (serviceEnabled) colors.onSecondaryContainer else colors.onErrorContainer,
                )
                Text(
                    if (serviceEnabled) "Accessibility service ready"
                    else "Enable the accessibility service to expand triggers here",
                    color = if (serviceEnabled) colors.onSecondaryContainer else colors.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Button(
            onClick = {
                editor?.post {
                    editor?.requestFocus()
                    editor?.postDelayed(
                        { ExpansionAccessibilityService.requestSuggestionOverlay() },
                        90L,
                    )
                }
            },
            enabled = serviceEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null)
            Text("  Open suggestion overlay")
        }
        Surface(
            modifier = Modifier.fillMaxWidth().height(320.dp),
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainer,
            tonalElevation = 1.dp,
        ) {
            AndroidView(
                factory = { context ->
                    EditText(context).apply {
                        editor = this
                        id = R.id.expanda_test_input
                        gravity = Gravity.TOP or Gravity.START
                        hint = "Try a snippet…"
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        isSingleLine = false
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        contentDescription = "Expanda writing playground"
                    }
                },
                update = { currentEditor ->
                    editor = currentEditor
                    currentEditor.setTextColor(colors.onSurface.toArgb())
                    currentEditor.setHintTextColor(colors.onSurfaceVariant.toArgb())
                    currentEditor.highlightColor = colors.primary.copy(alpha = 0.24f).toArgb()
                    currentEditor.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                    currentEditor.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        currentEditor.textCursorDrawable?.setTint(colors.primary.toArgb())
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.EditNote, contentDescription = null, tint = colors.primary)
            Text(
                "Tip: undo an expansion with Backspace.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

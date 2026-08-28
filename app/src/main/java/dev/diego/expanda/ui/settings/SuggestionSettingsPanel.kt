package dev.diego.expanda.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.diego.expanda.data.AppSettings
import dev.diego.expanda.data.SettingsRepository
import kotlin.math.roundToInt

/** Shared controls used by Settings and the live onboarding preview. */
@Composable
fun SuggestionSettingsPanel(
    settings: AppSettings,
    onEnabledChanged: (Boolean) -> Unit,
    onCompactChanged: (Boolean) -> Unit,
    onMinCharsChanged: (Int) -> Unit,
    onMaxHeightChanged: (Int) -> Unit,
    onWidthChanged: (Float) -> Unit,
    onResizeHandleChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    compactPresentation: Boolean = false,
    showAdditionalOptions: Boolean = false,
    onShowActionsChanged: (Boolean) -> Unit = {},
    onMatchFromBeginningChanged: (Boolean) -> Unit = {},
    onWidthPreviewChanged: (Float) -> Unit = {},
    onHeightPreviewChanged: (Int) -> Unit = {},
) {
    Column(modifier) {
        ListItem(
            headlineContent = { Text("Suggestion overlay") },
            leadingContent = { Icon(Icons.Default.Lightbulb, null) },
            supportingContent = if (compactPresentation) null else {
                { Text("Show matching snippets while you type") }
            },
            trailingContent = { Switch(settings.suggestionEnabled, onEnabledChanged) },
        )
        if (settings.suggestionEnabled) {
            if (showAdditionalOptions) {
                ListItem(
                    headlineContent = { Text("Show actions in suggestions") },
                    leadingContent = { Icon(Icons.Default.Bolt, null) },
                    supportingContent = { Text("Include enabled actions alongside text snippets") },
                    trailingContent = { Switch(settings.suggestionShowActions, onShowActionsChanged) },
                )
            }
            ListItem(
                headlineContent = { Text(if (compactPresentation) "Compact list" else "Compact suggestion list") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.ViewList, null) },
                supportingContent = if (compactPresentation) null else {
                    { Text("Off shows the replacement preview") }
                },
                trailingContent = { Switch(settings.suggestionCompactList, onCompactChanged) },
            )
            ListItem(
                headlineContent = { Text(if (compactPresentation) "Minimum characters" else "Minimum matching characters") },
                leadingContent = { Icon(Icons.Default.FormatSize, null) },
                supportingContent = if (compactPresentation) null else {
                    { Text("Suggestions start after ${settings.suggestionMinChars} characters") }
                },
                trailingContent = {
                    Stepper(
                        value = settings.suggestionMinChars,
                        onDecrease = { onMinCharsChanged(settings.suggestionMinChars - 1) },
                        onIncrease = { onMinCharsChanged(settings.suggestionMinChars + 1) },
                        canDecrease = settings.suggestionMinChars > 1,
                        canIncrease = settings.suggestionMinChars < 32,
                    )
                },
            )
            PopupSizeSettings(
                widthFraction = settings.suggestionWidthFraction,
                heightDp = settings.suggestionMaxHeightDp,
                onWidthChanged = onWidthChanged,
                onHeightChanged = onMaxHeightChanged,
                onWidthPreviewChanged = onWidthPreviewChanged,
                onHeightPreviewChanged = onHeightPreviewChanged,
            )
            ListItem(
                headlineContent = { Text(if (compactPresentation) "Resize button" else "Show popup resize button") },
                leadingContent = { Icon(Icons.Default.AspectRatio, null) },
                supportingContent = if (compactPresentation) null else {
                    { Text("Drag the bottom-left control diagonally to resize width and height") }
                },
                trailingContent = {
                    Switch(settings.suggestionResizeHandleEnabled, onResizeHandleChanged)
                },
            )
            if (showAdditionalOptions) {
                ListItem(
                    headlineContent = { Text("Match suggestions from beginning") },
                    supportingContent = { Text("Turn off to match text anywhere in a shortcut") },
                    trailingContent = {
                        Switch(settings.matchFromBeginning, onMatchFromBeginningChanged)
                    },
                )
            }
        }
    }
}

@Composable
private fun PopupSizeSettings(
    widthFraction: Float,
    heightDp: Int,
    onWidthChanged: (Float) -> Unit,
    onHeightChanged: (Int) -> Unit,
    onWidthPreviewChanged: (Float) -> Unit,
    onHeightPreviewChanged: (Int) -> Unit,
) {
    var widthDraft by remember(widthFraction) { mutableFloatStateOf(widthFraction) }
    var heightDraft by remember(heightDp) { mutableFloatStateOf(heightDp.toFloat()) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Suggestion popup size", style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PopupDimensionSlider(
                icon = Icons.Default.SwapHoriz,
                label = "Width",
                valueLabel = "${(widthDraft * 100).roundToInt()}%",
                value = widthDraft,
                onValueChange = {
                    widthDraft = it
                    onWidthPreviewChanged(it)
                },
                onValueChangeFinished = { onWidthChanged(widthDraft) },
                valueRange = SettingsRepository.MIN_SUGGESTION_WIDTH..SettingsRepository.MAX_SUGGESTION_WIDTH,
                steps = 10,
                modifier = Modifier.weight(1f),
            )
            PopupDimensionSlider(
                icon = Icons.Default.SwapVert,
                label = "Height",
                valueLabel = "${heightDraft.roundToInt()} dp",
                value = heightDraft,
                onValueChange = {
                    heightDraft = it
                    onHeightPreviewChanged(it.roundToInt())
                },
                onValueChangeFinished = { onHeightChanged(heightDraft.roundToInt()) },
                valueRange = SettingsRepository.MIN_SUGGESTION_HEIGHT_DP.toFloat()..
                    SettingsRepository.MAX_SUGGESTION_HEIGHT_DP.toFloat(),
                steps = 14,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PopupDimensionSlider(
    icon: ImageVector,
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Text("  $label", modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun Stepper(
    value: Int?,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    canDecrease: Boolean,
    canIncrease: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDecrease, enabled = canDecrease) { Text("−") }
        if (value != null) Text(value.toString())
        TextButton(onClick = onIncrease, enabled = canIncrease) { Text("+") }
    }
}

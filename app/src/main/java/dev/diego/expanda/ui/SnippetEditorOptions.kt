package dev.diego.expanda.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Filter1
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.diego.expanda.data.TemplateSelectionMode
import dev.diego.expanda.data.UppercaseStyle

internal fun hasMultipleConfiguredReplacements(replacements: List<String>): Boolean =
    replacements.count(String::isNotBlank) > 1

@Composable
internal fun SnippetMatchingOptionsCard(
    immediate: Boolean,
    onImmediateChanged: (Boolean) -> Unit,
    delimiters: String,
    onDelimitersChanged: (String) -> Unit,
    alternativeTriggers: String,
    onAlternativeTriggersChanged: (String) -> Unit,
    caseSensitive: Boolean,
    onCaseSensitiveChanged: (Boolean) -> Unit,
    leftWord: Boolean,
    onLeftWordChanged: (Boolean) -> Unit,
    rightWord: Boolean,
    onRightWordChanged: (Boolean) -> Unit,
    propagateCase: Boolean,
    onPropagateCaseChanged: (Boolean) -> Unit,
    uppercaseStyle: UppercaseStyle,
    onUppercaseStyleChanged: (UppercaseStyle) -> Unit,
    searchTerms: String,
    onSearchTermsChanged: (String) -> Unit,
    excludedAppCount: Int,
    onOpenExcludedApps: () -> Unit,
) {
    var editingAlternativeTriggers by remember { mutableStateOf(false) }
    val alternativeCount = alternativeTriggers.lineSequence().count { it.isNotBlank() }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Matching options",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            OptionSection("Activation") {
                val options = listOf(
                    ActivationOption("After delimiter", Icons.Default.SpaceBar, false),
                    ActivationOption("Immediately", Icons.Default.Bolt, true),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, option ->
                        val selected = immediate == option.immediate
                        SegmentedButton(
                            selected = selected,
                            onClick = { onImmediateChanged(option.immediate) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = selected) {
                                    Icon(option.icon, null, Modifier.size(18.dp))
                                }
                            },
                            label = { Text(option.label, maxLines = 1) },
                        )
                    }
                }
                if (!immediate) {
                    OutlinedTextField(
                        value = delimiters,
                        onValueChange = onDelimitersChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Activation delimiters") },
                        minLines = 1,
                        maxLines = 2,
                    )
                }
            }

            HorizontalDivider()

            OptionSection("Case matching") {
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(
                            value = caseSensitive,
                            role = Role.Switch,
                            onValueChange = onCaseSensitiveChanged,
                        )
                        .padding(horizontal = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.FormatSize,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Match letter case exactly", Modifier.weight(1f))
                    Switch(
                        checked = caseSensitive,
                        onCheckedChange = null,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(
                            value = propagateCase,
                            enabled = !caseSensitive,
                            role = Role.Switch,
                            onValueChange = onPropagateCaseChanged,
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.FormatSize,
                        null,
                        tint = if (caseSensitive) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        "Preserve typed capitalization",
                        Modifier.weight(1f),
                        color = if (caseSensitive) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Switch(
                        checked = propagateCase,
                        onCheckedChange = null,
                        enabled = !caseSensitive,
                    )
                }
                if (propagateCase) {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(UppercaseStyle.entries) { style ->
                            val label = when (style) {
                                UppercaseStyle.CAPITALIZE -> "First letter"
                                UppercaseStyle.CAPITALIZE_WORDS -> "Every word"
                                UppercaseStyle.UPPERCASE -> "ALL CAPS"
                            }
                            FilterChip(
                                selected = uppercaseStyle == style,
                                onClick = { onUppercaseStyleChanged(style) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            OptionSection("Word boundaries") {
                MultiChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    BoundaryOption.entries.forEachIndexed { index, option ->
                        val selected = when (option) {
                            BoundaryOption.LEFT -> leftWord
                            BoundaryOption.RIGHT -> rightWord
                        }
                        SegmentedButton(
                            checked = selected,
                            onCheckedChange = {
                                when (option) {
                                    BoundaryOption.LEFT -> onLeftWordChanged(it)
                                    BoundaryOption.RIGHT -> onRightWordChanged(it)
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, BoundaryOption.entries.size),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = selected) {
                                    Icon(option.icon, null, Modifier.size(18.dp))
                                }
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (editingAlternativeTriggers) {
                    OutlinedTextField(
                        value = alternativeTriggers,
                        onValueChange = onAlternativeTriggersChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Alternative triggers") },
                        supportingText = { Text("One per line; use regex: before a regular expression") },
                        minLines = 2,
                    )
                    TextButton(
                        onClick = { editingAlternativeTriggers = false },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Done") }
                } else {
                    TextButton(onClick = { editingAlternativeTriggers = true }) {
                        Icon(
                            if (alternativeCount == 0) Icons.Default.AddLink else Icons.Default.Edit,
                            null,
                        )
                        Text(
                            if (alternativeCount == 0) "Add alternative trigger"
                            else "Edit alternative triggers ($alternativeCount)",
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchTerms,
                onValueChange = onSearchTermsChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Search terms") },
                supportingText = { Text("Separate terms with commas") },
                singleLine = true,
            )

            ListItem(
                headlineContent = { Text("Excluded apps") },
                supportingContent = {
                    Text(
                        if (excludedAppCount == 0) "Available in every app"
                        else "$excludedAppCount ${if (excludedAppCount == 1) "app" else "apps"} excluded",
                    )
                },
                leadingContent = { Icon(Icons.Default.Apps, null) },
                modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onOpenExcludedApps),
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Choose excluded apps")
                },
            )
        }
    }
}

@Composable
internal fun ReplacementSelectionCard(
    selectionMode: TemplateSelectionMode,
    onSelectionModeChanged: (TemplateSelectionMode) -> Unit,
) {
    val options = TemplateSelectionMode.entries.map { mode ->
        when (mode) {
            TemplateSelectionMode.FIRST -> SelectionOption(mode, "First", "Always use the first", Icons.Default.Filter1)
            TemplateSelectionMode.RANDOM -> SelectionOption(mode, "Random", "Pick one at random", Icons.Default.Shuffle)
            TemplateSelectionMode.SEQUENTIAL -> SelectionOption(mode, "Sequential", "Android only", Icons.Default.Repeat)
            TemplateSelectionMode.MANUAL -> SelectionOption(mode, "Choose", "Ask each time", Icons.Default.TouchApp)
        }
    }
    val selectedDescription = options.first { it.mode == selectionMode }.description

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Replacement selection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    selectedDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options) { option ->
                    FilterChip(
                        selected = selectionMode == option.mode,
                        onClick = { onSelectionModeChanged(option.mode) },
                        label = { Text(option.label) },
                        leadingIcon = { Icon(option.icon, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

private data class ActivationOption(
    val label: String,
    val icon: ImageVector,
    val immediate: Boolean,
)

private enum class BoundaryOption(val label: String, val icon: ImageVector) {
    LEFT("Left edge", Icons.AutoMirrored.Filled.AlignHorizontalLeft),
    RIGHT("Right edge", Icons.AutoMirrored.Filled.AlignHorizontalRight),
}

private data class SelectionOption(
    val mode: TemplateSelectionMode,
    val label: String,
    val description: String,
    val icon: ImageVector,
)

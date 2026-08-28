package dev.diego.expanda.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.diego.expanda.engine.ActionCategory
import dev.diego.expanda.engine.ActionDefinition
import dev.diego.expanda.engine.ActionEngine

@Composable
fun ActionCatalogScreen(
    enabledIds: Set<String>,
    shortcutOverrides: Map<String, String>,
    onSetEnabled: (String, Boolean) -> Unit,
    onSetAllEnabled: (Boolean) -> Unit,
    onSetShortcut: (String, String) -> Unit,
    onResetShortcut: (String) -> Unit,
) {
    val groups = ActionEngine.definitions.groupBy(ActionDefinition::category)
    var expandedCategories by remember { mutableStateOf(setOf(ActionCategory.NUMBER)) }
    var editingAction by remember { mutableStateOf<ActionDefinition?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onSetAllEnabled(true) }, label = { Text("Enable all") })
                    AssistChip(onClick = { onSetAllEnabled(false) }, label = { Text("Disable all") })
                }
            }
        }
        groups.forEach { (category, definitions) ->
            item(key = "category_${category.name}") {
                val expanded = category in expandedCategories
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        ListItem(
                            headlineContent = { Text(category.displayName()) },
                            supportingContent = {
                                Text("${definitions.count { it.id in enabledIds }} of ${definitions.size} enabled")
                            },
                            leadingContent = { Icon(category.icon(), null) },
                            trailingContent = {
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            },
                            modifier = Modifier.clickable {
                                expandedCategories = if (expanded) expandedCategories - category
                                else expandedCategories + category
                            },
                        )
                        if (expanded) definitions.forEachIndexed { index, definition ->
                            if (index > 0) HorizontalDivider()
                            ActionRow(
                                definition = definition,
                                shortcut = shortcutOverrides[definition.id] ?: definition.shortcut,
                                enabled = definition.id in enabledIds,
                                onSetEnabled = { onSetEnabled(definition.id, it) },
                                onEdit = { editingAction = definition },
                            )
                        }
                    }
                }
            }
        }
    }

    editingAction?.let { definition ->
        ShortcutEditorDialog(
            definition = definition,
            currentShortcut = shortcutOverrides[definition.id] ?: definition.shortcut,
            allShortcuts = ActionEngine.definitions.associate { candidate ->
                candidate.id to (shortcutOverrides[candidate.id] ?: candidate.shortcut)
            },
            onDismiss = { editingAction = null },
            onSave = {
                onSetShortcut(definition.id, it)
                editingAction = null
            },
            onReset = {
                onResetShortcut(definition.id)
                editingAction = null
            },
        )
    }
}

@Composable
private fun ActionRow(
    definition: ActionDefinition,
    shortcut: String,
    enabled: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    ListItem(
        overlineContent = { Text(shortcut, fontFamily = FontFamily.Monospace) },
        headlineContent = { Text(definition.title) },
        supportingContent = {
            Column {
                Text(definition.description)
                if (definition.supportsSelectedText) {
                    Text(
                        "Also available from Android's selected-text menu",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit shortcut") }
                Switch(checked = enabled, onCheckedChange = onSetEnabled)
            }
        },
    )
}

@Composable
private fun ShortcutEditorDialog(
    definition: ActionDefinition,
    currentShortcut: String,
    allShortcuts: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
) {
    var value by remember(definition.id, currentShortcut) { mutableStateOf(currentShortcut) }
    val normalized = value.trim()
    val duplicate = allShortcuts.any { (id, shortcut) -> id != definition.id && shortcut == normalized }
    val error = when {
        normalized.isEmpty() -> "The shortcut cannot be empty"
        duplicate -> "Another action already uses this shortcut"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit action shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(definition.title)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Shortcut") },
                    supportingText = { Text(error ?: "Default: ${definition.shortcut}") },
                    isError = error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onReset, enabled = currentShortcut != definition.shortcut) {
                    Icon(Icons.Default.RestartAlt, null)
                    Text("Restore default")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(normalized) }, enabled = error == null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun ActionCategory.displayName(): String = when (this) {
    ActionCategory.NUMBER -> "Numbers and calculations"
    ActionCategory.TEXT -> "Text"
    ActionCategory.SELECTION -> "Selection"
    ActionCategory.DELETION -> "Deletion"
    ActionCategory.CURSOR -> "Cursor"
    ActionCategory.CLIPBOARD -> "Clipboard"
    ActionCategory.ANDROID -> "Android"
    ActionCategory.EXPANDA -> "Expanda"
}

private fun ActionCategory.icon(): ImageVector = when (this) {
    ActionCategory.NUMBER -> Icons.Default.Calculate
    ActionCategory.TEXT -> Icons.Default.TextFields
    ActionCategory.SELECTION -> Icons.Default.SelectAll
    ActionCategory.DELETION -> Icons.Default.DeleteSweep
    ActionCategory.CURSOR -> Icons.AutoMirrored.Filled.KeyboardTab
    ActionCategory.CLIPBOARD -> Icons.Default.ContentPaste
    ActionCategory.ANDROID -> Icons.Default.Android
    ActionCategory.EXPANDA -> Icons.Default.Bolt
}

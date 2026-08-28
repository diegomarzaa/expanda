package dev.diego.expanda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.diego.expanda.data.SnippetSortMode
import dev.diego.expanda.data.TextMatch

@Composable
internal fun SnippetSortControl(
    mode: SnippetSortMode,
    onModeChange: (SnippetSortMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, "Sort snippets: ${mode.label()}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SnippetSortMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    leadingIcon = if (option == mode) {
                        { Icon(Icons.Default.Check, null) }
                    } else null,
                    onClick = {
                        onModeChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun SnippetActionBar(
    sortMode: SnippetSortMode,
    onSearch: () -> Unit,
    onSortModeChange: (SnippetSortMode) -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 3.dp,
        ) {
            Row(Modifier.padding(4.dp)) {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, "Search snippets")
                }
                SnippetSortControl(mode = sortMode, onModeChange = onSortModeChange)
            }
        }
        FloatingActionButton(onClick = onCreate) {
            Icon(Icons.Default.Add, "Create snippet")
        }
    }
}

@Composable
internal fun SnippetSelectionBar(
    selectedCount: Int,
    visibleCount: Int,
    enableSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEditTags: () -> Unit,
    onDeleteTags: () -> Unit,
) {
    Surface(tonalElevation = 4.dp, shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel selection") }
                Text("$selectedCount selected", style = MaterialTheme.typography.titleSmall)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TextButton(onClick = onSelectAll, enabled = selectedCount < visibleCount) { Text("All") }
                TextButton(onClick = onDeselectAll, enabled = selectedCount > 0) { Text("None") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = onDuplicate,
                        enabled = selectedCount > 0,
                        label = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    )
                }
                item {
                    AssistChip(
                        onClick = onToggleEnabled,
                        enabled = selectedCount > 0,
                        label = { Text(if (enableSelected) "Enable" else "Disable") },
                        leadingIcon = { Icon(Icons.Default.PowerSettingsNew, null) },
                    )
                }
                item {
                    AssistChip(
                        onClick = onEditTags,
                        enabled = selectedCount > 0,
                        label = { Text("Edit tags") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                    )
                }
                item {
                    AssistChip(
                        onClick = onDeleteTags,
                        enabled = selectedCount > 0,
                        label = { Text("Delete tags") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.LabelOff, null) },
                    )
                }
                item {
                    AssistChip(
                        onClick = onDelete,
                        enabled = selectedCount > 0,
                        label = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun BulkTagEditorDialog(
    selected: List<TextMatch>,
    allTags: List<String>,
    onDismiss: () -> Unit,
    onApply: (add: Set<String>, remove: Set<String>) -> Unit,
) {
    var add by remember(selected) { mutableStateOf(emptySet<String>()) }
    var remove by remember(selected) { mutableStateOf(emptySet<String>()) }
    var newTag by remember { mutableStateOf("") }
    val selectedTags = selected.flatMap { it.tags }.distinctBy(String::lowercase)

    fun addNewTag() {
        val normalized = newTag.trim().removePrefix("#")
        if (normalized.isNotBlank()) add = add + normalized
        newTag = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tags for ${selected.size} snippets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add", style = MaterialTheme.typography.labelLarge)
                if (allTags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allTags, key = { "add_$it" }) { tag ->
                            FilterChip(
                                selected = add.any { it.equals(tag, ignoreCase = true) },
                                onClick = {
                                    add = if (add.any { it.equals(tag, ignoreCase = true) }) {
                                        add.filterNot { it.equals(tag, ignoreCase = true) }.toSet()
                                    } else add + tag
                                    remove = remove.filterNot { it.equals(tag, ignoreCase = true) }.toSet()
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New tag") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addNewTag() }),
                    trailingIcon = {
                        IconButton(onClick = ::addNewTag, enabled = newTag.isNotBlank()) {
                            Icon(Icons.Default.Add, "Add tag")
                        }
                    },
                )
                if (add.isNotEmpty()) Text(
                    "Will add: ${add.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (selectedTags.isNotEmpty()) {
                    Text("Remove", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(selectedTags, key = { "remove_$it" }) { tag ->
                            FilterChip(
                                selected = remove.any { it.equals(tag, ignoreCase = true) },
                                onClick = {
                                    remove = if (remove.any { it.equals(tag, ignoreCase = true) }) {
                                        remove.filterNot { it.equals(tag, ignoreCase = true) }.toSet()
                                    } else remove + tag
                                    add = add.filterNot { it.equals(tag, ignoreCase = true) }.toSet()
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(add, remove) },
                enabled = add.isNotEmpty() || remove.isNotEmpty(),
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun SnippetSortMode.label(): String = when (this) {
    SnippetSortMode.RECENTLY_EDITED -> "Recently edited"
    SnippetSortMode.OLDEST_EDITED -> "Oldest edited"
    SnippetSortMode.NAME_ASCENDING -> "Name A–Z"
    SnippetSortMode.NAME_DESCENDING -> "Name Z–A"
    SnippetSortMode.NEWEST_CREATED -> "Newest created"
    SnippetSortMode.MOST_USED -> "Most used"
}

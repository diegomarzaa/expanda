package dev.diego.expanda.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.min
import dev.diego.expanda.data.Snippet
import dev.diego.expanda.data.ThemeMode
import dev.diego.expanda.data.ColorSchemeMode
import dev.diego.expanda.data.TextSizeMode
import dev.diego.expanda.data.TemplateSelectionMode
import dev.diego.expanda.data.TriggerMode
import dev.diego.expanda.engine.TemplateTokenEditor
import kotlinx.coroutines.launch

private enum class Destination(val label: String, val icon: ImageVector) {
    TEXT("Text", Icons.Default.TextFields),
    CLIPBOARD("Clipboard", Icons.Default.ContentPaste),
    ACTION("Action", Icons.Default.Bolt),
    SETTINGS("Settings", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandaApp(
    state: MainUiState,
    serviceEnabled: Boolean,
    backgroundAllowed: Boolean,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    openNewSnippetRequest: Boolean = false,
    onNewSnippetRequestConsumed: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit,
) {
    var destination by remember { mutableStateOf(Destination.TEXT) }
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showSnippetSearch by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var disclosureOverride by remember { mutableStateOf<Boolean?>(null) }
    val showDisclosure = disclosureOverride ?: !state.settings.consentAccepted

    LaunchedEffect(destination) {
        if (destination != Destination.TEXT) {
            showSnippetSearch = false
            viewModel.setSearch("")
        }
    }

    LaunchedEffect(openNewSnippetRequest) {
        if (openNewSnippetRequest) {
            destination = Destination.TEXT
            creating = true
            onNewSnippetRequestConsumed()
        }
    }

    if (creating || editing != null) {
        SnippetEditorScreen(
            initial = editing,
            availableSnippets = state.snippets,
            snackbarHostState = snackbarHostState,
            onDismiss = { creating = false; editing = null },
            onSave = { snippet ->
                viewModel.save(
                    snippet,
                    onSuccess = { creating = false; editing = null },
                    onError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                )
            },
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(destination.label) }) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (destination == Destination.TEXT) Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallFloatingActionButton(onClick = { showSnippetSearch = true }) {
                    Icon(Icons.Default.Search, "Search snippets")
                }
                FloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, "Create snippet")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                Destination.TEXT -> SnippetList(
                    state = state,
                    viewModel = viewModel,
                    serviceEnabled = serviceEnabled,
                    openSettings = onOpenAccessibilitySettings,
                    showSearch = showSnippetSearch,
                    onHideSearch = { showSnippetSearch = false },
                    onEdit = { editing = it },
                )
                Destination.CLIPBOARD -> ClipboardScreen(state, viewModel) { text ->
                    editing = Snippet(shortcut = "", content = text)
                }
                Destination.ACTION -> ActionCatalogScreen(
                    enabledIds = state.enabledActionIds,
                    shortcutOverrides = state.actionShortcutOverrides,
                    onSetEnabled = viewModel::setActionEnabled,
                    onSetAllEnabled = viewModel::setAllActionsEnabled,
                    onSetShortcut = viewModel::setActionShortcut,
                    onResetShortcut = viewModel::resetActionShortcut,
                )
                Destination.SETTINGS -> SettingsScreen(
                    state, serviceEnabled, backgroundAllowed, viewModel, onOpenAccessibilitySettings,
                    onOpenBackgroundSettings, onExportJson, onExportCsv, onImport,
                )
            }
        }
    }

    if (!state.settings.backgroundSetupAcknowledged) BackgroundSetupDialog(
        backgroundAllowed = backgroundAllowed,
        onOpenSettings = onOpenBackgroundSettings,
        onConfigured = viewModel::acknowledgeBackgroundSetup,
    ) else if (showDisclosure) AccessibilityDisclosure(
        onDismiss = { disclosureOverride = false },
        onAccept = {
            viewModel.acceptConsent()
            disclosureOverride = false
            onOpenAccessibilitySettings()
        },
    )
}

@Composable
private fun SnippetList(
    state: MainUiState,
    viewModel: MainViewModel,
    serviceEnabled: Boolean,
    openSettings: () -> Unit,
    showSearch: Boolean,
    onHideSearch: () -> Unit,
    onEdit: (Snippet) -> Unit,
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val visibleIds = state.visibleSnippets.map(Snippet::id).toSet()
    val selectedVisibleIds = selectedIds intersect visibleIds
    val selectedSnippets = state.visibleSnippets.filter { it.id in selectedVisibleIds }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    LaunchedEffect(visibleIds) {
        selectedIds = selectedIds intersect visibleIds
    }
    LaunchedEffect(state.tags, state.selectedTag) {
        val selectedTag = state.selectedTag ?: return@LaunchedEffect
        if (state.tags.none { it.equals(selectedTag, ignoreCase = true) }) viewModel.selectTag(null)
    }
    BackHandler(enabled = selectionMode) { exitSelection() }

    Column(Modifier.fillMaxSize()) {
        if (!serviceEnabled) Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Expansion service is off", fontWeight = FontWeight.SemiBold)
                    Text("Enable it in Android Accessibility settings.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = openSettings) { Text("Enable") }
            }
        }
        if (showSearch) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::setSearch,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search snippets") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    IconButton(onClick = {
                        viewModel.setSearch("")
                        onHideSearch()
                    }) {
                        Icon(Icons.Default.Close, "Close search")
                    }
                },
                singleLine = true,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedTag == null,
                    onClick = { viewModel.selectTag(null) },
                    label = { Text("All") },
                )
            }
            items(state.tags) { tag ->
                FilterChip(
                    selected = state.selectedTag?.equals(tag, ignoreCase = true) == true,
                    onClick = { viewModel.selectTag(tag) },
                    label = { Text(tag) },
                )
            }
        }
        if (selectionMode) {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::exitSelection) { Icon(Icons.Default.Close, "Cancel selection") }
                        Text("${selectedVisibleIds.size} selected", style = MaterialTheme.typography.titleMedium)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { TextButton(onClick = { selectedIds = visibleIds }) { Text("Select all") } }
                        item {
                            TextButton(onClick = { selectedIds = emptySet() }, enabled = selectedVisibleIds.isNotEmpty()) {
                                Text("Deselect all")
                            }
                        }
                        item {
                            TextButton(
                                onClick = {
                                    viewModel.duplicate(selectedVisibleIds)
                                    exitSelection()
                                },
                                enabled = selectedVisibleIds.isNotEmpty(),
                            ) { Text("Duplicate") }
                        }
                        item {
                            TextButton(
                                onClick = {
                                    if (selectedVisibleIds.size > 1) confirmDelete = true
                                    else if (selectedVisibleIds.size == 1) {
                                        viewModel.delete(selectedVisibleIds.first())
                                        exitSelection()
                                    }
                                },
                                enabled = selectedVisibleIds.isNotEmpty(),
                            ) { Text("Delete") }
                        }
                        item {
                            val enable = selectedSnippets.isNotEmpty() && !selectedSnippets.all(Snippet::enabled)
                            TextButton(
                                onClick = {
                                    viewModel.setEnabled(selectedVisibleIds, enable)
                                    exitSelection()
                                },
                                enabled = selectedVisibleIds.isNotEmpty(),
                            ) { Text(if (enable) "Enable" else "Disable") }
                        }
                    }
                }
            }
        }
        if (state.visibleSnippets.isEmpty()) EmptyState()
        else LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            items(state.visibleSnippets, key = Snippet::id) { snippet ->
                SnippetRow(
                    snippet = snippet,
                    viewModel = viewModel,
                    selectionMode = selectionMode,
                    selected = snippet.id in selectedVisibleIds,
                    onClick = {
                        if (selectionMode) {
                            selectedIds = if (snippet.id in selectedIds) selectedIds - snippet.id else selectedIds + snippet.id
                        } else onEdit(snippet)
                    },
                    onLongClick = {
                        selectionMode = true
                        selectedIds = selectedIds + snippet.id
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selectedVisibleIds.size} snippets?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(selectedVisibleIds)
                    confirmDelete = false
                    exitSelection()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SnippetRow(
    snippet: Snippet,
    viewModel: MainViewModel,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(snippet.label.ifBlank { snippet.shortcut }) },
        overlineContent = { Text(snippet.shortcut, fontFamily = FontFamily.Monospace) },
        supportingContent = {
            Column {
                Text(snippet.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (snippet.tags.isNotEmpty()) {
                    Text(
                        snippet.tags.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        leadingContent = if (selectionMode) ({ Checkbox(checked = selected, onCheckedChange = null) }) else null,
        trailingContent = { Switch(snippet.enabled, { viewModel.setSnippetEnabled(snippet.id, it) }) },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun ClipboardScreen(state: MainUiState, viewModel: MainViewModel, createSnippet: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${state.clipboardEntries.size} saved items", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = viewModel::clearClipboard) { Text("Clear") }
        }
        if (state.clipboardEntries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Copy text, then open Expanda to add it to history.")
            }
        } else LazyColumn {
            items(state.clipboardEntries, key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(if (entry.pinned) "Pinned" else "Clipboard item") },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { createSnippet(entry.text) }) { Text("Snippet") }
                            IconButton(onClick = { clipboard.setText(AnnotatedString(entry.text)) }) {
                                Icon(Icons.Default.ContentPaste, "Copy")
                            }
                            IconButton(onClick = { viewModel.deleteClipboard(entry.id) }) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable private fun EmptyState() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.TextFields, null)
        Spacer(Modifier.height(8.dp))
        Text("No snippets yet", style = MaterialTheme.typography.titleMedium)
        Text("Create one and type its shortcut in any editable field.")
    }
}

@Composable private fun StatsScreen(viewModel: MainViewModel) {
    val stats = viewModel.stats()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StatCard("Expansions", stats.totalExpansions.toString()) }
        item { StatCard("Characters saved", stats.estimatedCharactersSaved.toString()) }
        item { StatCard("Estimated time saved", "${stats.estimatedSecondsSaved / 60}m ${stats.estimatedSecondsSaved % 60}s") }
    }
}

@Composable private fun StatCard(label: String, value: String) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(20.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingsScreen(
    state: MainUiState,
    serviceEnabled: Boolean,
    backgroundAllowed: Boolean,
    viewModel: MainViewModel,
    openAccessibility: () -> Unit,
    openBackgroundSettings: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit,
) {
    LazyColumn {
        item {
            ListItem(
                headlineContent = { Text("Text expansion") },
                leadingContent = { Icon(Icons.Default.Lightbulb, null) },
                supportingContent = { Text(if (serviceEnabled) "Accessibility service enabled" else "Service disabled") },
                trailingContent = { Switch(state.settings.expansionEnabled, viewModel::setExpansionEnabled) },
            )
        }
        item { ListItem(leadingContent = { Icon(Icons.Default.Accessibility, null) }, headlineContent = { Text("Accessibility service") }, modifier = Modifier.selectable(false, onClick = openAccessibility)) }
        item {
            ListItem(
                headlineContent = { Text("Run reliably in background") },
                leadingContent = { Icon(Icons.Default.BatteryStd, null) },
                supportingContent = {
                    Text(
                        if (backgroundAllowed) "Battery optimization exemption granted"
                        else "Open battery settings and choose No restrictions for Expanda",
                    )
                },
                trailingContent = { Text(if (backgroundAllowed) "Ready" else "Required") },
                modifier = Modifier.selectable(false, onClick = openBackgroundSettings),
            )
        }
        item {
            ListItem(
                headlineContent = { Text(if (state.settings.isPaused) "Resume now" else "Pause for one hour") },
                leadingContent = { Icon(Icons.Default.PauseCircle, null) },
                modifier = Modifier.selectable(false) {
                    if (state.settings.isPaused) viewModel.resume() else viewModel.pauseFor(60 * 60 * 1000L)
                }
            )
        }
        item { HorizontalDivider() }
        item { Text("Interaction", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall) }
        item {
            ListItem(
                headlineContent = { Text("Clipboard history") },
                leadingContent = { Icon(Icons.Default.ContentPaste, null) },
                supportingContent = { Text("Save copied text while Expanda is open") },
                trailingContent = { Switch(state.settings.clipboardHistoryEnabled, viewModel::setClipboardHistoryEnabled) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Haptic feedback") },
                leadingContent = { Icon(Icons.Default.Vibration, null) },
                trailingContent = { Switch(state.settings.hapticFeedback, viewModel::setHapticFeedback) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Compatibility paste fallback") },
                leadingContent = { Icon(Icons.Default.Build, null) },
                supportingContent = { Text("For editors that reject direct replacement. Temporarily uses and restores the clipboard.") },
                trailingContent = { Switch(state.settings.pasteFallbackEnabled, viewModel::setPasteFallbackEnabled) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Suggestion overlay") },
                leadingContent = { Icon(Icons.Default.Lightbulb, null) },
                supportingContent = { Text("Show matching snippets above other apps as you type") },
                trailingContent = { Switch(state.settings.suggestionEnabled, viewModel::setSuggestionEnabled) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Show actions in suggestions") },
                leadingContent = { Icon(Icons.Default.Bolt, null) },
                supportingContent = { Text("Include enabled actions alongside matching text snippets") },
                trailingContent = {
                    Switch(
                        checked = state.settings.suggestionShowActions,
                        onCheckedChange = viewModel::setSuggestionShowActions,
                    )
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Compact suggestion list") },
                leadingContent = { Icon(Icons.Default.ViewList, null) },
                supportingContent = { Text("Off shows the full template preview in each result") },
                trailingContent = { Switch(state.settings.suggestionCompactList, viewModel::setSuggestionCompactList) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Minimum matching characters") },
                leadingContent = { Icon(Icons.Default.FormatSize, null) },
                supportingContent = { Text("Suggestions start after ${state.settings.suggestionMinChars} characters") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { viewModel.setSuggestionMinChars(state.settings.suggestionMinChars - 1) }) { Text("−") }
                        Text(state.settings.suggestionMinChars.toString())
                        TextButton(onClick = { viewModel.setSuggestionMinChars(state.settings.suggestionMinChars + 1) }) { Text("+") }
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Suggestion list height") },
                leadingContent = { Icon(Icons.Default.Height, null) },
                supportingContent = { Text("Scrollable area: ${state.settings.suggestionMaxHeightDp} dp") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { viewModel.setSuggestionMaxHeightDp(state.settings.suggestionMaxHeightDp - 40) }) { Text("−") }
                        TextButton(onClick = { viewModel.setSuggestionMaxHeightDp(state.settings.suggestionMaxHeightDp + 40) }) { Text("+") }
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Match suggestions from beginning") },
                supportingContent = { Text("Turn off to match typed text anywhere in a shortcut") },
                trailingContent = { Switch(state.settings.matchFromBeginning, viewModel::setMatchFromBeginning) },
            )
        }
        item { Text("Statistics", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall) }
        item {
            val stats = viewModel.stats()
            ListItem(
                headlineContent = { Text("${stats.estimatedCharactersSaved} characters saved") },
                leadingContent = { Icon(Icons.Default.QueryStats, null) },
                supportingContent = { Text("${stats.totalExpansions} expansions") },
                trailingContent = { Switch(state.settings.statisticsEnabled, viewModel::setStatisticsEnabled) },
            )
        }
        item { HorizontalDivider() }
        item { Text("Appearance", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall) }
        item {
            ListItem(
                headlineContent = { Text("Color scheme") },
                supportingContent = { Text("Choose the source of the app colors") },
                leadingContent = { Icon(Icons.Default.Palette, null) },
            )
        }
        items(ColorSchemeMode.entries) { mode ->
            ListItem(
                headlineContent = { Text(mode.label()) },
                leadingContent = { RadioButton(state.settings.colorSchemeMode == mode, { viewModel.setColorScheme(mode) }) },
                modifier = Modifier.selectable(state.settings.colorSchemeMode == mode) { viewModel.setColorScheme(mode) },
            )
        }
        if (state.settings.colorSchemeMode == ColorSchemeMode.CUSTOM) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Colorize, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Custom color", style = MaterialTheme.typography.titleMedium)
                            Text("Pick a seed color for the Material theme", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ColorPickerWheel(state.settings.customColor, viewModel::setCustomColor)
                }
            }
        }
        item {
            ListItem(
                headlineContent = { Text("Text size") },
                supportingContent = { Text("Adjust the size of text throughout Expanda") },
                leadingContent = { Icon(Icons.Default.FormatSize, null) },
            )
        }
        items(TextSizeMode.entries) { mode ->
            ListItem(
                headlineContent = { Text(mode.label()) },
                leadingContent = { RadioButton(state.settings.textSizeMode == mode, { viewModel.setTextSize(mode) }) },
                modifier = Modifier.selectable(state.settings.textSizeMode == mode) { viewModel.setTextSize(mode) },
            )
        }
        item { Text("Theme mode", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall) }
        items(ThemeMode.entries) { mode ->
            ListItem(
                headlineContent = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                leadingContent = { Icon(Icons.Default.DarkMode, null) },
                trailingContent = { RadioButton(state.settings.themeMode == mode, { viewModel.setTheme(mode) }) },
                modifier = Modifier.selectable(state.settings.themeMode == mode) { viewModel.setTheme(mode) },
            )
        }
        item { HorizontalDivider() }
        item { ListItem(leadingContent = { Icon(Icons.Default.Download, null) }, headlineContent = { Text("Export JSON backup (unencrypted)") }, modifier = Modifier.selectable(false, onClick = onExportJson)) }
        item { ListItem(leadingContent = { Icon(Icons.Default.Download, null) }, headlineContent = { Text("Export snippets as CSV") }, modifier = Modifier.selectable(false, onClick = onExportCsv)) }
        item { ListItem(leadingContent = { Icon(Icons.Default.Upload, null) }, headlineContent = { Text("Import JSON or CSV") }, modifier = Modifier.selectable(false, onClick = onImport)) }
        item { ListItem(headlineContent = { Text("Privacy") }, supportingContent = { Text("All snippets stay on this device. Password fields are ignored. No analytics or network access.") }) }
    }
}

private fun ColorSchemeMode.label(): String = when (this) {
    ColorSchemeMode.WALLPAPER -> "Wallpaper"
    ColorSchemeMode.DEFAULT -> "Default"
    ColorSchemeMode.CUSTOM -> "Custom"
}

private fun TextSizeMode.label(): String = when (this) {
    TextSizeMode.SMALL -> "Small"
    TextSizeMode.DEFAULT -> "Default"
    TextSizeMode.LARGE -> "Large"
}

@Composable
private fun ColorPickerWheel(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    val hues = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    val selected = Color(selectedColor)
    var wheelSize by remember { mutableStateOf(IntSize.Zero) }
    fun selectAt(position: Offset) {
        val center = Offset(wheelSize.width / 2f, wheelSize.height / 2f)
        val angle = ((Math.toDegrees(atan2(position.y - center.y, position.x - center.x).toDouble()) + 360.0) % 360.0).toFloat()
        onColorSelected(Color.hsv(angle, 0.78f, 0.92f).toArgb())
    }
    Canvas(
        Modifier.fillMaxWidth().height(220.dp)
            .onSizeChanged { wheelSize = it }
            .pointerInput(wheelSize) {
                detectTapGestures(onTap = ::selectAt)
            }
            .pointerInput(wheelSize) {
                detectDragGestures(
                    onDragStart = { selectAt(it) },
                    onDrag = { change, _ -> selectAt(change.position) },
                )
            },
    ) {
        val radius = min(size.width, size.height) / 2f - 24.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.sweepGradient(hues),
            radius = radius,
            center = center,
            style = Stroke(width = 32.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(color = selected, radius = radius - 30.dp.toPx(), center = center)
        drawCircle(color = Color.White, radius = 13.dp.toPx(), center = center, style = Stroke(width = 3.dp.toPx()))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnippetEditorScreen(
    initial: Snippet?,
    availableSnippets: List<Snippet>,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onSave: (Snippet) -> Unit,
) {
    var shortcut by remember(initial) { mutableStateOf(initial?.shortcut.orEmpty()) }
    var templates by remember(initial) {
        mutableStateOf(
            (listOf(initial?.content.orEmpty()) + initial?.templates.orEmpty())
                .map(::TextFieldValue),
        )
    }
    var selectionMode by remember(initial) { mutableStateOf(initial?.selectionMode ?: TemplateSelectionMode.FIRST) }
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var tags by remember(initial) { mutableStateOf(initial?.tags.orEmpty()) }
    var instant by remember(initial) { mutableStateOf(initial?.triggerMode == TriggerMode.INSTANT) }
    var caseSensitive by remember(initial) { mutableStateOf(initial?.caseSensitive ?: false) }
    var showAdvanced by remember(initial) { mutableStateOf(false) }
    var activeTemplateIndex by remember(initial) { mutableStateOf<Int?>(null) }
    val focusManager = LocalFocusManager.current
    val availableTags = availableSnippets
        .flatMap { it.tags }
        .distinctBy(String::lowercase)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    BackHandler(onBack = onDismiss)

    fun updateTemplate(index: Int, value: TextFieldValue) {
        templates = templates.toMutableList().also { it[index] = value }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        Column {
                            Text(
                                if (initial == null) "Create text snippet" else "Edit text snippet",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${label.ifBlank { shortcut.ifBlank { "New snippet" } }}, ${tags.size} ${if (tags.size == 1) "tag" else "tags"}, ${templates.size} ${if (templates.size == 1) "template" else "templates"}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().imePadding()) {
                    activeTemplateIndex?.let { activeIndex ->
                        TemplateTokenToolbar(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            snippets = availableSnippets.filterNot { it.id == initial?.id },
                            onInsert = { token ->
                                val current = templates.getOrNull(activeIndex) ?: return@TemplateTokenToolbar
                                val result = TemplateTokenEditor.insert(
                                    current.text, current.selection.start, current.selection.end, token,
                                )
                                updateTemplate(activeIndex, TextFieldValue(result.text, TextRange(result.cursor)))
                            },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            enabled = shortcut.isNotBlank() && templates.firstOrNull()?.text?.isNotEmpty() == true,
                            onClick = {
                                val contents = templates.map { it.text }
                                onSave((initial ?: Snippet(shortcut = shortcut, content = contents.first())).copy(
                                    shortcut = shortcut.trim(), content = contents.first(), templates = contents.drop(1),
                                    selectionMode = selectionMode, label = label, tags = tags,
                                    triggerMode = if (instant) TriggerMode.INSTANT else TriggerMode.DELIMITER,
                                    caseSensitive = caseSensitive,
                                ))
                            },
                        ) {
                            Icon(Icons.Default.Check, null)
                            Text("Save")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = shortcut,
                        onValueChange = { shortcut = it },
                        modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) activeTemplateIndex = null },
                        label = { Text("Keyword") },
                        singleLine = true,
                    )
                    FilledTonalIconButton(onClick = {
                        focusManager.clearFocus()
                        activeTemplateIndex = null
                        showAdvanced = !showAdvanced
                    }) { Icon(Icons.Default.Tune, "Additional settings") }
                }
            }
            item {
                TagEditor(
                    availableTags = availableTags,
                    selectedTags = tags,
                    onTagsChanged = { tags = it },
                    onFocus = { activeTemplateIndex = null },
                )
            }
            if (showAdvanced) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Additional settings", style = MaterialTheme.typography.titleSmall)
                            OutlinedTextField(
                                label, { label = it }, Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) activeTemplateIndex = null },
                                label = { Text("Name (optional)") }, singleLine = true,
                            )
                            Text("Expansion trigger", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { instant = false },
                                    label = { Text(if (!instant) "✓ After delimiter" else "After delimiter") },
                                )
                                AssistChip(
                                    onClick = { instant = true },
                                    label = { Text(if (instant) "✓ Immediately" else "Immediately") },
                                )
                            }
                            Text("Template selection", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(TemplateSelectionMode.entries) { mode ->
                                    AssistChip(
                                        onClick = { selectionMode = mode },
                                        label = { Text(if (selectionMode == mode) "✓ ${mode.name.lowercase().replaceFirstChar(Char::uppercase)}" else mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(caseSensitive, { caseSensitive = it })
                                Text("Case sensitive")
                            }
                        }
                    }
                }
            }
            itemsIndexed(templates, key = { index, _ -> "template-$index" }) { index, template ->
                OutlinedTextField(
                    value = template,
                    onValueChange = { updateTemplate(index, atomicTemplateValueChange(template, it)) },
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) activeTemplateIndex = index }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || !template.selection.collapsed) return@onPreviewKeyEvent false
                            val result = when (event.key) {
                                Key.Backspace -> TemplateTokenEditor.deleteBackward(template.text, template.selection.end)
                                Key.Delete -> TemplateTokenEditor.deleteForward(template.text, template.selection.end)
                                else -> return@onPreviewKeyEvent false
                            }
                            if (result.text == template.text) false else {
                                updateTemplate(index, TextFieldValue(result.text, TextRange(result.cursor)))
                                true
                            }
                        },
                    label = { Text("Template #${index + 1}") },
                    minLines = 3,
                    visualTransformation = templateTokenVisualTransformation(),
                    trailingIcon = if (templates.size > 1) ({
                        IconButton(onClick = {
                            templates = templates.toMutableList().also { it.removeAt(index) }
                            activeTemplateIndex = null
                        }) { Icon(Icons.Default.Delete, "Delete template") }
                    }) else null,
                )
            }
            item {
                OutlinedButton(onClick = {
                    templates = templates + TextFieldValue("")
                    activeTemplateIndex = null
                }) {
                    Icon(Icons.Default.Add, null)
                    Text("Add template")
                }
            }
        }
    }
}

@Composable
private fun templateTokenVisualTransformation(): VisualTransformation {
    val colors = MaterialTheme.colorScheme
    return VisualTransformation { text ->
        val highlighted = AnnotatedString.Builder(text.text)
        Regex("\\{\\{[^{}]+\\}\\}|\\{[^{}]+\\}").findAll(text.text).forEach { match ->
            if (TemplateTokenEditor.isTemplateToken(match.value)) {
                highlighted.addStyle(
                    SpanStyle(
                        color = colors.onSecondaryContainer,
                        background = colors.secondaryContainer.copy(alpha = 0.72f),
                    ),
                    match.range.first,
                    match.range.last + 1,
                )
            }
        }
        TransformedText(highlighted.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
private fun TagEditor(
    availableTags: List<String>,
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
    onFocus: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val suggestions = availableTags
        .filterNot { candidate -> selectedTags.any { it.equals(candidate, ignoreCase = true) } }
        .filter { input.isBlank() || it.contains(input.trim(), ignoreCase = true) }
        .take(8)

    fun addTag(raw: String) {
        val normalized = raw.trim().removePrefix("#").trim()
        if (normalized.isBlank()) return
        val canonical = availableTags.firstOrNull { it.equals(normalized, ignoreCase = true) } ?: normalized
        onTagsChanged(selectedTags.filterNot { it.equals(canonical, ignoreCase = true) }.toSet() + canonical)
        input = ""
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tags", style = MaterialTheme.typography.titleSmall)
            if (selectedTags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedTags.sortedWith(String.CASE_INSENSITIVE_ORDER)) { tag ->
                        InputChip(
                            selected = true,
                            onClick = { onTagsChanged(selectedTags.filterNot { it.equals(tag, ignoreCase = true) }.toSet()) },
                            label = { Text(tag) },
                            trailingIcon = { Icon(Icons.Default.Close, "Remove $tag") },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
                label = { Text("Add tag") },
                placeholder = { Text("Type a new or existing tag") },
                leadingIcon = { Text("#") },
                trailingIcon = {
                    IconButton(onClick = { addTag(input) }, enabled = input.trim().removePrefix("#").isNotBlank()) {
                        Icon(Icons.Default.Add, "Add tag")
                    }
                },
                singleLine = true,
            )
            if (suggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { tag ->
                        AssistChip(onClick = { addTag(tag) }, label = { Text(tag) })
                    }
                }
            }
        }
    }
}

private fun atomicTemplateValueChange(old: TextFieldValue, next: TextFieldValue): TextFieldValue {
    val oldCursor = old.selection.end
    val deletedBackwards = old.selection.collapsed && next.text.length == old.text.length - 1 &&
        next.selection.end == oldCursor - 1
    if (deletedBackwards && TemplateTokenEditor.tokenBefore(old.text, oldCursor) != null) {
        val result = TemplateTokenEditor.deleteBackward(old.text, oldCursor)
        return TextFieldValue(result.text, TextRange(result.cursor))
    }
    return next
}

@Composable
private fun BackgroundSetupDialog(
    backgroundAllowed: Boolean,
    onOpenSettings: () -> Unit,
    onConfigured: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Allow Expanda to run in background") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Android or the phone's battery saver may stop Expanda after it has been in the background. That can make expansion appear to fail intermittently.")
                Text("Open battery settings and set Expanda to No restrictions (or allow unrestricted background use).")
                Text(
                    if (backgroundAllowed) "Android battery optimization exemption: granted."
                    else "Android battery optimization exemption: not detected yet.",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = {
            Button(onClick = onOpenSettings) { Text("Open battery settings") }
        },
        dismissButton = {
            TextButton(onClick = onConfigured) { Text("I've configured it") }
        },
    )
}

@Composable
private fun AccessibilityDisclosure(onDismiss: () -> Unit, onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How text expansion works") },
        text = {
            Text("Expanda uses Android's Accessibility service to read changes in editable fields, detect shortcuts, and replace them with your snippets. Text is processed on-device and is never sent anywhere. Password fields are ignored. You can disable the service at any time in Android Settings.")
        },
        confirmButton = { Button(onClick = onAccept) { Text("I understand — open Settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

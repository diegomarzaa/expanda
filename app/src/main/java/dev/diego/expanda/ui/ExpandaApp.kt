package dev.diego.expanda.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import dev.diego.expanda.data.MatchOptions
import dev.diego.expanda.data.MatchTrigger
import dev.diego.expanda.data.OnboardingStatus
import dev.diego.expanda.data.TextMatch
import dev.diego.expanda.data.TriggerKind
import dev.diego.expanda.data.ThemeMode
import dev.diego.expanda.data.ColorSchemeMode
import dev.diego.expanda.data.SettingsRepository
import dev.diego.expanda.data.SnippetSortMode
import dev.diego.expanda.data.TemplateSelectionMode
import dev.diego.expanda.data.TemplateVariable
import dev.diego.expanda.data.TriggerActivation
import dev.diego.expanda.data.UppercaseStyle
import dev.diego.expanda.engine.TemplateTokenEditor
import dev.diego.expanda.engine.TemplateTokenSpan
import dev.diego.expanda.ui.settings.SuggestionSettingsPanel
import dev.diego.expanda.ui.test.TestScreen
import dev.diego.expanda.ui.tutorial.WorkspaceOnboardingScreen
import dev.diego.expanda.ui.tutorial.TutorialScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.diego.expanda.R

private enum class Destination(val label: String, val icon: ImageVector) {
    TEXT("Snippets", Icons.Default.TextFields),
    TEST("Playground", Icons.Default.EditNote),
    ACTION("Actions", Icons.Default.Bolt),
    SOURCE("Source", Icons.Default.Code),
    SETTINGS("Settings", Icons.Default.Settings),
}

private val PORTABLE_VARIABLE_TYPES = setOf("echo", "date", "choice", "random", "clipboard", "form", "match")

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
    onExportEspanso: () -> Unit,
    onImport: () -> Unit,
    onChooseEspansoFolder: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { Destination.entries.size })
    val destination = Destination.entries[pagerState.currentPage]
    var editing by remember { mutableStateOf<TextMatch?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showSnippetSearch by remember { mutableStateOf(false) }
    var selectedSnippetIds by remember { mutableStateOf<Set<Long>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeleteTags by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var sourceInitialPath by remember { mutableStateOf<String?>(null) }
    var sourceInitialMatchIndex by remember { mutableStateOf<Int?>(null) }
    var sourceReturnPage by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val rootFocusManager = LocalFocusManager.current
    val rootKeyboardController = LocalSoftwareKeyboardController.current
    var disclosureOverride by remember { mutableStateOf<Boolean?>(null) }
    val showDisclosure = disclosureOverride ?: !state.settings.consentAccepted
    val visibleIds = state.visibleMatches.map(TextMatch::id).toSet()
    val selectedVisibleIds = selectedSnippetIds.orEmpty() intersect visibleIds
    val selectedSnippets = state.visibleMatches.filter { it.id in selectedVisibleIds }
    val sourceFiles by viewModel.sourceFiles.collectAsState()

    fun exitSelection() { selectedSnippetIds = null }

    fun openSource(path: String? = null, matchIndex: Int? = null) {
        sourceReturnPage = pagerState.currentPage
        sourceInitialPath = path
        sourceInitialMatchIndex = matchIndex
        scope.launch { pagerState.animateScrollToPage(Destination.SOURCE.ordinal) }
    }

    fun leaveSource() {
        val returnPage = sourceReturnPage
        sourceReturnPage = null
        sourceInitialPath = null
        sourceInitialMatchIndex = null
        if (returnPage != null) {
            scope.launch { pagerState.animateScrollToPage(returnPage) }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { page ->
            if (page != Destination.TEST.ordinal) {
                rootFocusManager.clearFocus(force = true)
                rootKeyboardController?.hide()
            }
            if (page != Destination.TEXT.ordinal) {
                showSnippetSearch = false
                viewModel.setSearch("")
                exitSelection()
            }
            if (page != Destination.SOURCE.ordinal) {
                sourceInitialPath = null
                sourceInitialMatchIndex = null
                sourceReturnPage = null
            }
        }
    }
    LaunchedEffect(visibleIds) {
        selectedSnippetIds?.let { selectedSnippetIds = it intersect visibleIds }
    }
    BackHandler(enabled = selectedSnippetIds != null) { exitSelection() }
    BackHandler(enabled = destination == Destination.SOURCE && sourceReturnPage != null) {
        leaveSource()
    }

    LaunchedEffect(openNewSnippetRequest) {
        if (openNewSnippetRequest) {
            pagerState.scrollToPage(Destination.TEXT.ordinal)
            creating = true
            onNewSnippetRequestConsumed()
        }
    }

    if (creating || editing != null) {
        SnippetEditorScreen(
            initial = editing,
            availableSnippets = state.matches,
            globalVariables = state.settings.globalVariables,
            onGlobalVariablesChanged = viewModel::setGlobalVariables,
            snackbarHostState = snackbarHostState,
            onDismiss = { creating = false; editing = null },
            onSave = { match ->
                viewModel.save(
                    match,
                    onSuccess = { creating = false; editing = null },
                    onError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
                )
            },
        )
        return
    }

    if (state.matchesLoaded && state.onboarding.status == OnboardingStatus.ACTIVE) {
        TutorialScreen(
            step = state.onboarding.step,
            settings = state.settings,
            onStepChange = viewModel::setTutorialStep,
            onSuggestionEnabledChanged = viewModel::setSuggestionEnabled,
            onSuggestionCompactChanged = viewModel::setSuggestionCompactList,
            onSuggestionMinCharsChanged = viewModel::setSuggestionMinChars,
            onSuggestionMaxHeightChanged = viewModel::setSuggestionMaxHeightDp,
            onSuggestionWidthChanged = viewModel::setSuggestionWidthFraction,
            onSuggestionResizeHandleChanged = viewModel::setSuggestionResizeHandleEnabled,
            onDone = viewModel::completeTutorial,
        )
        return
    }

    if (state.matchesLoaded && !state.onboarding.workspaceReady) {
        WorkspaceOnboardingScreen(
            folderLinked = state.settings.espansoFolderUri != null,
            sourceFileCount = sourceFiles.size,
            matchCount = state.matches.count(TextMatch::runsOnAndroid),
            onChooseFolder = onChooseEspansoFolder,
            onDone = viewModel::finishWorkspaceSetup,
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { ExpandaAppTitle(destination.label) { showAbout = true } }) },
        bottomBar = {
            Column {
                if (destination == Destination.TEXT && selectedSnippetIds != null) {
                    val enable = selectedSnippets.isNotEmpty() && !selectedSnippets.all(TextMatch::enabled)
                    SnippetSelectionBar(
                        selectedCount = selectedVisibleIds.size,
                        visibleCount = visibleIds.size,
                        enableSelected = enable,
                        onClose = ::exitSelection,
                        onSelectAll = { selectedSnippetIds = visibleIds },
                        onDeselectAll = { selectedSnippetIds = emptySet() },
                        onDuplicate = {
                            viewModel.duplicate(selectedVisibleIds)
                            exitSelection()
                        },
                        onDelete = {
                            if (selectedVisibleIds.size > 1) confirmDelete = true
                            else selectedVisibleIds.firstOrNull()?.let {
                                viewModel.delete(it)
                                exitSelection()
                            }
                        },
                        onToggleEnabled = {
                            viewModel.setEnabled(selectedVisibleIds, enable)
                            exitSelection()
                        },
                        onEditTags = { showTagEditor = true },
                        onDeleteTags = { confirmDeleteTags = true },
                    )
                }
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = {
                                if (item == Destination.SOURCE) sourceReturnPage = null
                                scope.launch { pagerState.animateScrollToPage(item.ordinal) }
                            },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (destination == Destination.TEXT) SnippetActionBar(
                sortMode = state.settings.snippetSortMode,
                onSearch = { showSnippetSearch = true },
                onSortModeChange = viewModel::setSnippetSort,
                onCreate = { creating = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
            beyondViewportPageCount = 1,
        ) { page ->
            when (Destination.entries[page]) {
                Destination.TEXT -> SnippetList(
                    state = state,
                    viewModel = viewModel,
                    serviceEnabled = serviceEnabled,
                    openSettings = onOpenAccessibilitySettings,
                    showSearch = showSnippetSearch,
                    onHideSearch = { showSnippetSearch = false },
                    selectedIds = selectedSnippetIds,
                    onSelectedIdsChange = { selectedSnippetIds = it },
                    snackbarHostState = snackbarHostState,
                    onEdit = { match ->
                        if (match.canEditVisually && match.runsOnAndroid) {
                            editing = match
                        } else {
                            openSource(match.sourceFile, match.sourceMatchIndex)
                        }
                    },
                )
                Destination.TEST -> TestScreen(
                    serviceEnabled = serviceEnabled,
                    active = pagerState.settledPage == Destination.TEST.ordinal,
                )
                Destination.ACTION -> ActionCatalogScreen(
                    enabledIds = state.enabledActionIds,
                    shortcutOverrides = state.actionShortcutOverrides,
                    onSetEnabled = viewModel::setActionEnabled,
                    onSetAllEnabled = viewModel::setAllActionsEnabled,
                    onSetShortcut = viewModel::setActionShortcut,
                    onResetShortcut = viewModel::resetActionShortcut,
                )
                Destination.SOURCE -> SnippetSourceScreen(
                    files = sourceFiles,
                    linkedFolderUri = state.settings.espansoFolderUri,
                    initialPath = sourceInitialPath,
                    initialMatchIndex = sourceInitialMatchIndex,
                    embedded = true,
                    onDismiss = {},
                    onApply = viewModel::applySnippetSource,
                    onChooseFolder = onChooseEspansoFolder,
                    onSyncFolder = { callback ->
                        viewModel.syncEspansoFolder { result ->
                            callback(result.map { it.imported })
                        }
                    },
                )
                Destination.SETTINGS -> SettingsScreen(
                    state, serviceEnabled, backgroundAllowed, viewModel, onOpenAccessibilitySettings,
                    onOpenBackgroundSettings, onExportJson, onExportCsv, onExportEspanso, onImport,
                    onChooseEspansoFolder,
                    onOpenAbout = { showAbout = true },
                    onOpenSnippetSource = { openSource() },
                )
            }
        }
    }

    if (confirmDelete) AlertDialog(
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
    if (confirmDeleteTags) AlertDialog(
        onDismissRequest = { confirmDeleteTags = false },
        title = { Text("Delete tags from ${selectedVisibleIds.size} snippets?") },
        text = { Text("The snippets will remain; only their tags will be removed.") },
        confirmButton = {
            TextButton(onClick = {
                viewModel.clearTags(selectedVisibleIds)
                confirmDeleteTags = false
                exitSelection()
            }) { Text("Delete tags") }
        },
        dismissButton = { TextButton(onClick = { confirmDeleteTags = false }) { Text("Cancel") } },
    )
    if (showTagEditor) BulkTagEditorDialog(
        selected = selectedSnippets,
        allTags = state.tags,
        onDismiss = { showTagEditor = false },
        onApply = { add, remove ->
            viewModel.updateTags(selectedVisibleIds, add, remove)
            showTagEditor = false
            exitSelection()
        },
    )
    if (showAbout) AboutExpandaSheet(onDismiss = { showAbout = false })

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
private fun ExpandaAppTitle(section: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.width(34.dp).height(34.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            shadowElevation = 2.dp,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_app_panda),
                contentDescription = "Expanda",
                modifier = Modifier.padding(4.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("Expanda", fontWeight = FontWeight.Bold)
        Text("  ·  $section", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SnippetList(
    state: MainUiState,
    viewModel: MainViewModel,
    serviceEnabled: Boolean,
    openSettings: () -> Unit,
    showSearch: Boolean,
    onHideSearch: () -> Unit,
    selectedIds: Set<Long>?,
    onSelectedIdsChange: (Set<Long>?) -> Unit,
    snackbarHostState: SnackbarHostState,
    onEdit: (TextMatch) -> Unit,
) {
    val visibleIds = state.visibleMatches.map(TextMatch::id).toSet()
    val selectionMode = selectedIds != null
    val selectedVisibleIds = selectedIds.orEmpty() intersect visibleIds
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var installingExamples by remember { mutableStateOf(false) }
    val installExamples: () -> Unit = {
        if (!installingExamples) {
            installingExamples = true
            viewModel.installExampleSnippets { result ->
                installingExamples = false
                result.onFailure { error ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            error.message ?: "Could not add example snippets",
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(showSearch) {
        if (showSearch) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    LaunchedEffect(state.tags, state.selectedTag) {
        val selectedTag = state.selectedTag ?: return@LaunchedEffect
        if (state.tags.none { it.equals(selectedTag, ignoreCase = true) }) viewModel.selectTag(null)
    }

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
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(searchFocusRequester),
                placeholder = { Text("Search snippets") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    IconButton(onClick = {
                        viewModel.setSearch("")
                        focusManager.clearFocus()
                        keyboardController?.hide()
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
        if (!state.matchesLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.visibleMatches.isEmpty()) {
            EmptyState(
                hasSnippets = state.matches.isNotEmpty(),
                installingExamples = installingExamples,
                onAddExamples = installExamples,
            )
        } else LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            items(state.visibleMatches, key = TextMatch::id) { match ->
                SnippetRow(
                    match = match,
                    viewModel = viewModel,
                    selectionMode = selectionMode,
                    selected = match.id in selectedVisibleIds,
                    onClick = {
                        if (selectionMode) {
                            val current = selectedIds.orEmpty()
                            onSelectedIdsChange(
                                if (match.id in current) current - match.id else current + match.id,
                            )
                        } else onEdit(match)
                    },
                    onLongClick = {
                        onSelectedIdsChange(selectedIds.orEmpty() + match.id)
                    },
                )
                HorizontalDivider()
            }
            item {
                AddExampleSnippetsButton(
                    compact = true,
                    installing = installingExamples,
                    onClick = installExamples,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }

}

@Composable
private fun SnippetRow(
    match: TextMatch,
    viewModel: MainViewModel,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                match.label.ifBlank { match.trigger },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },

        overlineContent = if (match.label.isNotBlank()) {
            {
                Text(
                    match.trigger,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                )
            }
        } else {
            null
        },

        supportingContent = {
            Column {
                Text(
                    match.replace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (match.compatibilityWarnings.isNotEmpty()) {
                    Text(
                        "⚠ ${match.compatibilityWarnings.first()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!match.runsOnAndroid || !match.canEditVisually) {
                    Text(
                        when {
                            !match.runsOnAndroid -> "Desktop only · kept inactive on Android"
                            else -> "Source editing required"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (match.tags.isNotEmpty()) {
                    Text(
                        match.tags.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        leadingContent = if (selectionMode) ({ Checkbox(checked = selected, onCheckedChange = null) }) else null,
        trailingContent = {
            Switch(
                checked = match.enabled && match.runsOnAndroid,
                onCheckedChange = { viewModel.setSnippetEnabled(match.id, it) },
                enabled = match.runsOnAndroid,
            )
        },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun EmptyState(
    hasSnippets: Boolean,
    installingExamples: Boolean,
    onAddExamples: () -> Unit,
) =
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.TextFields, null)
        Text(if (hasSnippets) "No matching snippets" else "No snippets yet", style = MaterialTheme.typography.titleMedium)
        Text(
            if (hasSnippets) "Try another search or tag."
            else "Create your own snippet, or start with ready-made examples.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasSnippets) {
            AddExampleSnippetsButton(
                compact = false,
                installing = installingExamples,
                onClick = onAddExamples,
            )
        }
    }
}

@Composable
private fun AddExampleSnippetsButton(
    compact: Boolean,
    installing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        OutlinedButton(
            onClick = onClick,
            enabled = !installing,
            modifier = modifier,
        ) {
            AddExampleSnippetsContent(installing, compact = true)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = !installing,
            modifier = modifier,
        ) {
            AddExampleSnippetsContent(installing, compact = false)
        }
    }
}

@Composable
private fun AddExampleSnippetsContent(installing: Boolean, compact: Boolean) {
    if (installing) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = if (compact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        Text("Add example snippets")
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
    onExportEspanso: () -> Unit,
    onImport: () -> Unit,
    onChooseEspansoFolder: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSnippetSource: () -> Unit,
) {
    var showGlobalAppPicker by remember { mutableStateOf(false) }
    var confirmClearClipboard by remember { mutableStateOf(false) }
    var confirmResetStatistics by remember { mutableStateOf(false) }
    var confirmResetAll by remember { mutableStateOf(false) }
    var diagnosticsCopied by remember { mutableStateOf(false) }
    var folderStatus by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        LazyColumn {
        item { SettingsSectionHeader(Icons.Default.Settings, "General") }
        item {
            ListItem(
                headlineContent = { Text("About Expanda") },
                supportingContent = { Text("Project, source code and contact links") },
                leadingContent = { Icon(Icons.Default.Info, null) },
                modifier = Modifier.selectable(false, onClick = onOpenAbout),
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Learn Expanda") },
                leadingContent = { Icon(Icons.Default.AutoAwesome, null) },
                supportingContent = { Text("Reopen the animated introduction") },
                modifier = Modifier.selectable(false, onClick = viewModel::showTutorial),
            )
        }
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
        item {
            ListItem(
                headlineContent = { Text("Excluded apps") },
                leadingContent = { Icon(Icons.Default.Apps, null) },
                supportingContent = {
                    Text(
                        if (state.settings.globallyExcludedPackages.isEmpty()) "Expanda is available in every app"
                        else "${state.settings.globallyExcludedPackages.size} apps excluded globally",
                    )
                },
                modifier = Modifier.clickable { showGlobalAppPicker = true },
            )
        }
        item { HorizontalDivider() }
        item { SettingsSectionHeader(Icons.Default.Tune, "Interaction") }
        item {
            ListItem(
                headlineContent = { Text("Clipboard history") },
                leadingContent = { Icon(Icons.Default.ContentPaste, null) },
                supportingContent = { Text("Save copied text for clipboard snippets in other apps") },
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
            SuggestionSettingsPanel(
                settings = state.settings,
                onEnabledChanged = viewModel::setSuggestionEnabled,
                onCompactChanged = viewModel::setSuggestionCompactList,
                onMinCharsChanged = viewModel::setSuggestionMinChars,
                onMaxHeightChanged = viewModel::setSuggestionMaxHeightDp,
                onWidthChanged = viewModel::setSuggestionWidthFraction,
                onResizeHandleChanged = viewModel::setSuggestionResizeHandleEnabled,
                showAdditionalOptions = true,
                onShowActionsChanged = viewModel::setSuggestionShowActions,
                onMatchFromBeginningChanged = viewModel::setMatchFromBeginning,
            )
        }
        item { SettingsSectionHeader(Icons.Default.QueryStats, "Statistics") }
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
        item { SettingsSectionHeader(Icons.Default.Palette, "Appearance") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    CompactChoiceSetting(
                        icon = Icons.Default.Palette,
                        title = "Color scheme",
                        options = ColorSchemeMode.entries,
                        selected = state.settings.colorSchemeMode,
                        label = ColorSchemeMode::label,
                        onSelected = viewModel::setColorScheme,
                    )
                    if (state.settings.colorSchemeMode == ColorSchemeMode.CUSTOM) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                "Custom Material color",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ColorPickerWheel(state.settings.customColor, viewModel::setCustomColor)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    TextScaleSetting(
                        value = state.settings.textScale,
                        onValueChanged = viewModel::setTextScale,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    CompactChoiceSetting(
                        icon = Icons.Default.DarkMode,
                        title = "Theme mode",
                        options = ThemeMode.entries,
                        selected = state.settings.themeMode,
                        label = ThemeMode::label,
                        onSelected = viewModel::setTheme,
                    )
                }
            }
        }
        item { HorizontalDivider() }
        item { SettingsSectionHeader(Icons.Default.ImportExport, "Import & export") }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ImportExport, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Espanso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Plain-text Espanso .yml files. Keep them in Expanda, or link a folder to sync with desktop.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Upload, null)
                            Text("  Import")
                        }
                        OutlinedButton(onClick = onExportEspanso, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, null)
                            Text("  Export")
                        }
                    }
                    OutlinedButton(
                        onClick = onChooseEspansoFolder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Folder, null)
                        Text(
                            if (state.settings.espansoFolderUri == null) "  Link match folder"
                            else "  Change match folder",
                        )
                    }
                    if (state.settings.espansoFolderUri != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    folderStatus = "Syncing…"
                                    viewModel.syncEspansoFolder { result ->
                                        folderStatus = result.fold(
                                            onSuccess = { "Synced ${it.imported} matches" },
                                            onFailure = { "Sync failed: ${it.message}" },
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Sync, null)
                                Text("  Sync now")
                            }
                            TextButton(
                                onClick = {
                                    viewModel.unlinkEspansoFolder()
                                    folderStatus = "Folder unlinked; its files were not deleted."
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.LinkOff, null)
                                Text("  Unlink")
                            }
                        }
                    }
                    folderStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = onOpenSnippetSource,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Code, null)
                        Text("  Open Espanso source files")
                    }
                }
            }
        }
        item {
            Text(
                "Expanda backup",
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Full app backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Restores snippets, variables, settings, exclusions and actions. Clipboard contents and device permissions are not included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Upload, null)
                            Text("  Restore")
                        }
                        Button(onClick = onExportJson, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, null)
                            Text("  Back up")
                        }
                    }
                }
            }
        }
        item {
            Text(
                "CSV",
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { ListItem(leadingContent = { Icon(Icons.Default.Upload, null) }, headlineContent = { Text("Import snippets from CSV") }, modifier = Modifier.selectable(false, onClick = onImport)) }
        item { ListItem(leadingContent = { Icon(Icons.Default.Download, null) }, headlineContent = { Text("Export snippets as CSV") }, modifier = Modifier.selectable(false, onClick = onExportCsv)) }
        item { HorizontalDivider() }
        item { SettingsSectionHeader(Icons.Default.Security, "Privacy & data") }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Default.Security, null) },
                headlineContent = { Text("Privacy") },
                supportingContent = { Text("All snippets stay on this device. Password fields are ignored. No analytics or network access.") },
            )
        }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Default.ContentPaste, null) },
                headlineContent = { Text("Clear clipboard history") },
                supportingContent = { Text("${state.clipboardEntries.size} saved entries") },
                modifier = Modifier.clickable(enabled = state.clipboardEntries.isNotEmpty()) {
                    confirmClearClipboard = true
                },
            )
        }
        item {
            val stats = viewModel.stats()
            ListItem(
                leadingContent = { Icon(Icons.Default.QueryStats, null) },
                headlineContent = { Text("Reset usage statistics") },
                supportingContent = { Text("Clears ${stats.totalExpansions} expansion counts and local usage records") },
                modifier = Modifier.clickable(enabled = stats.totalExpansions > 0) {
                    confirmResetStatistics = true
                },
            )
        }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Default.Info, null) },
                headlineContent = { Text(if (diagnosticsCopied) "Diagnostics copied" else "Copy diagnostics") },
                supportingContent = { Text("Version and device state only; no text, clipboard or app names") },
                modifier = Modifier.clickable {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Expanda diagnostics", viewModel.diagnostics(serviceEnabled, backgroundAllowed)),
                    )
                    diagnosticsCopied = true
                },
            )
        }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text("Reset Expanda", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Delete local data and return to the first-run tutorial") },
                modifier = Modifier.clickable { confirmResetAll = true },
            )
        }
        }

        if (showGlobalAppPicker) AppExclusionPicker(
            title = "Exclude Expanda from apps",
            selectedPackages = state.settings.globallyExcludedPackages,
            onDismiss = { showGlobalAppPicker = false },
            onSave = {
                viewModel.setGloballyExcludedPackages(it)
                showGlobalAppPicker = false
            },
        )
        if (confirmClearClipboard) ConfirmationDialog(
            title = "Clear clipboard history?",
            text = "All locally saved clipboard entries will be deleted.",
            confirmLabel = "Clear",
            destructive = true,
            onDismiss = { confirmClearClipboard = false },
            onConfirm = {
                viewModel.clearClipboard()
                confirmClearClipboard = false
            },
        )
        if (confirmResetStatistics) ConfirmationDialog(
            title = "Reset usage statistics?",
            text = "Expansion counters and local usage records will return to zero.",
            confirmLabel = "Reset",
            destructive = true,
            onDismiss = { confirmResetStatistics = false },
            onConfirm = {
                viewModel.resetStatistics()
                confirmResetStatistics = false
            },
        )
        if (confirmResetAll) ConfirmationDialog(
            title = "Reset Expanda?",
            text = "Snippets, variables, settings, actions, statistics and clipboard history will be deleted. Default examples and the tutorial will return.",
            confirmLabel = "Reset everything",
            destructive = true,
            onDismiss = { confirmResetAll = false },
            onConfirm = {
                confirmResetAll = false
                viewModel.resetAllData()
            },
        )
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun <T> CompactChoiceSetting(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options) { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun TextScaleSetting(value: Float, onValueChanged: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.FormatSize, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Text size", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${(draft * 100).roundToInt()}%", color = MaterialTheme.colorScheme.primary)
            TextButton(
                onClick = {
                    draft = SettingsRepository.DEFAULT_TEXT_SCALE
                    onValueChanged(draft)
                },
                enabled = draft != SettingsRepository.DEFAULT_TEXT_SCALE,
            ) { Text("Reset") }
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onValueChanged(draft) },
            valueRange = SettingsRepository.MIN_TEXT_SCALE..SettingsRepository.MAX_TEXT_SCALE,
            steps = 14,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Smaller", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Larger", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ColorSchemeMode.label(): String = when (this) {
    ColorSchemeMode.WALLPAPER -> "Wallpaper"
    ColorSchemeMode.DEFAULT -> "Default"
    ColorSchemeMode.CUSTOM -> "Custom"
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED"
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

private data class VariableEditTarget(
    val variable: TemplateVariable,
    val global: Boolean,
)

private data class RegexCaptureEditTarget(
    val replacementIndex: Int,
    val span: TemplateTokenSpan,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnippetEditorScreen(
    initial: TextMatch?,
    availableSnippets: List<TextMatch>,
    globalVariables: List<TemplateVariable>,
    onGlobalVariablesChanged: (List<TemplateVariable>) -> Unit,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onSave: (TextMatch) -> Unit,
) {
    var triggerText by remember(initial) { mutableStateOf(initial?.trigger.orEmpty()) }
    var triggerKind by remember(initial) { mutableStateOf(initial?.triggers?.firstOrNull()?.kind ?: TriggerKind.TEXT) }
    var otherTriggers by remember(initial) {
        mutableStateOf(initial?.triggers?.drop(1)?.joinToString("\n") {
            if (it.kind == TriggerKind.REGEX) "regex:${it.pattern}" else it.pattern
        }.orEmpty())
    }
    var replacementFields by remember(initial) {
        mutableStateOf(
            initial?.replacements.orEmpty().ifEmpty { listOf("") }
                .map(::TextFieldValue),
        )
    }
    var selectionMode by remember(initial) { mutableStateOf(initial?.selectionMode ?: TemplateSelectionMode.FIRST) }
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var tags by remember(initial) { mutableStateOf(initial?.tags.orEmpty()) }
    var instant by remember(initial) {
        mutableStateOf(initial?.options?.activation?.let { it == TriggerActivation.IMMEDIATE } ?: true)
    }
    var caseSensitive by remember(initial) { mutableStateOf(initial?.options?.caseSensitive ?: true) }
    var leftWord by remember(initial) { mutableStateOf(initial?.options?.leftWord ?: false) }
    var rightWord by remember(initial) { mutableStateOf(initial?.options?.rightWord ?: false) }
    var propagateCase by remember(initial) { mutableStateOf(initial?.options?.propagateCase ?: false) }
    var uppercaseStyle by remember(initial) {
        mutableStateOf(initial?.options?.uppercaseStyle ?: UppercaseStyle.CAPITALIZE)
    }
    var delimiters by remember(initial) { mutableStateOf(initial?.options?.delimiters ?: " \n\t.,!?;:") }
    var excludedPackages by remember(initial) { mutableStateOf(initial?.excludedPackages.orEmpty()) }
    var showExcludedApps by remember(initial) { mutableStateOf(false) }
    var searchTerms by remember(initial) { mutableStateOf(initial?.searchTerms?.joinToString(", ").orEmpty()) }
    var variables by remember(initial) { mutableStateOf(initial?.vars.orEmpty()) }
    var variableEditTarget by remember(initial) { mutableStateOf<VariableEditTarget?>(null) }
    var captureEditTarget by remember(initial) { mutableStateOf<RegexCaptureEditTarget?>(null) }
    var showAdvanced by remember(initial) { mutableStateOf(false) }
    var activeTemplateIndex by remember(initial) { mutableStateOf<Int?>(null) }
    val compatibilityWarnings = (
        initial?.compatibilityWarnings.orEmpty().filterNot { warning ->
            warning.startsWith("Variable type '") &&
                warning.contains("not executed", ignoreCase = true)
        } + variables.filterNot { it.type.lowercase() in PORTABLE_VARIABLE_TYPES }.map { variable ->
            "Variable type '${variable.type}' is retained but is not executed on Android."
        }
    ).distinct()
    LaunchedEffect(caseSensitive) {
        if (caseSensitive) propagateCase = false
    }
    LaunchedEffect(propagateCase) {
        if (propagateCase) caseSensitive = false
    }
    val focusManager = LocalFocusManager.current
    val captureCatalog = remember(triggerKind, triggerText) {
        if (triggerKind == TriggerKind.REGEX) regexCaptureCatalog(triggerText) else null
    }
    val availableTags = availableSnippets
        .flatMap { it.tags }
        .distinctBy(String::lowercase)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
    val showReplacementSelection = hasMultipleConfiguredReplacements(
        replacementFields.map(TextFieldValue::text),
    )

    BackHandler(onBack = onDismiss)

    LaunchedEffect(showReplacementSelection) {
        if (!showReplacementSelection) selectionMode = TemplateSelectionMode.FIRST
    }

    fun updateReplacement(index: Int, value: TextFieldValue) {
        replacementFields = replacementFields.toMutableList().also { it[index] = value }
    }

    fun insertIntoActiveReplacement(token: String) {
        val index = activeTemplateIndex ?: 0
        val current = replacementFields.getOrNull(index) ?: return
        val result = TemplateTokenEditor.insert(
            current.text, current.selection.start, current.selection.end, token,
        )
        updateReplacement(index, TextFieldValue(result.text, TextRange(result.cursor)))
    }

    fun rewriteVariableReferences(oldName: String, newName: String?) {
        replacementFields = replacementFields.map { field ->
            val rewritten = TemplateTokenEditor.rewriteVariableReferences(
                text = field.text,
                oldName = oldName,
                newName = newName,
                selectionStart = field.selection.start,
                selectionEnd = field.selection.end,
            )
            TextFieldValue(
                text = rewritten.text,
                selection = TextRange(rewritten.selectionStart, rewritten.selectionEnd),
            )
        }
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
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        Text(
                            if (initial == null) "Create snippet" else "Edit snippet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().imePadding()) {
                    activeTemplateIndex?.let { activeIndex ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Insert into replacement", style = MaterialTheme.typography.labelLarge)
                            TemplateTokenToolbar(
                                modifier = Modifier.fillMaxWidth(),
                                snippets = availableSnippets.filterNot { it.id == initial?.id },
                                variables = variables,
                                globalVariables = globalVariables,
                                onVariablesChanged = { variables = it },
                                onGlobalVariablesChanged = onGlobalVariablesChanged,
                                onVariableReferencesChanged = ::rewriteVariableReferences,
                                regexPattern = triggerText.takeIf { triggerKind == TriggerKind.REGEX },
                                onInsert = ::insertIntoActiveReplacement,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            enabled = triggerText.isNotBlank() && replacementFields.firstOrNull()?.text?.isNotEmpty() == true,
                            onClick = {
                                val replacements = replacementFields.map { it.text }
                                val caseOptions = MatchOptions(
                                    activation = if (instant) TriggerActivation.IMMEDIATE else TriggerActivation.DELIMITER,
                                    delimiters = delimiters,
                                    caseSensitive = caseSensitive,
                                    leftWord = leftWord,
                                    rightWord = rightWord,
                                    propagateCase = propagateCase,
                                    uppercaseStyle = uppercaseStyle,
                                ).normalizedCase()
                                onSave((initial ?: TextMatch(
                                    triggers = listOf(MatchTrigger(triggerText.trim())),
                                    replacements = replacements,
                                )).copy(
                                    triggers = buildList {
                                        add(MatchTrigger(triggerText.trim(), triggerKind))
                                        otherTriggers.lineSequence().map(String::trim).filter(String::isNotBlank)
                                            .forEach { value ->
                                                if (value.startsWith("regex:")) {
                                                    value.removePrefix("regex:").takeIf(String::isNotBlank)
                                                        ?.let { add(MatchTrigger(it, TriggerKind.REGEX)) }
                                                } else {
                                                    add(MatchTrigger(value, TriggerKind.TEXT))
                                                }
                                            }
                                    },
                                    replacements = replacements,
                                    selectionMode = if (showReplacementSelection) {
                                        selectionMode
                                    } else {
                                        TemplateSelectionMode.FIRST
                                    },
                                    label = label, tags = tags,
                                    searchTerms = searchTerms.split(',').map(String::trim).filter(String::isNotBlank).toSet(),
                                    vars = variables,
                                    compatibilityWarnings = compatibilityWarnings,
                                    excludedPackages = excludedPackages,
                                    options = caseOptions,
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
            if (compatibilityWarnings.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Espanso compatibility warning",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            compatibilityWarnings.forEach { warning ->
                                Text(
                                    "• $warning",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth().onFocusChanged {
                        if (it.isFocused) activeTemplateIndex = null
                    },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = triggerText,
                    onValueChange = { triggerText = it },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) activeTemplateIndex = null },
                    label = { Text(if (triggerKind == TriggerKind.REGEX) "Regex trigger" else "Trigger") },
                    singleLine = true,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text("Use regular expression matching") } },
                                state = rememberTooltipState(),
                            ) {
                                IconButton(
                                    onClick = {
                                        triggerKind = if (triggerKind == TriggerKind.REGEX) TriggerKind.TEXT else TriggerKind.REGEX
                                        if (triggerKind == TriggerKind.REGEX) instant = true
                                    },
                                    modifier = Modifier.semantics {
                                        contentDescription = if (triggerKind == TriggerKind.REGEX) {
                                            "Regular expression trigger enabled"
                                        } else {
                                            "Use a regular expression trigger"
                                        }
                                    },
                                ) {
                                    Text(
                                        ".*",
                                        color = if (triggerKind == TriggerKind.REGEX) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (triggerKind == TriggerKind.REGEX) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                            /*
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text("Match uppercase and lowercase exactly") } },
                                state = rememberTooltipState(),
                            ) {
                                IconButton(
                                    onClick = { caseSensitive = !caseSensitive },
                                    modifier = Modifier.semantics {
                                        contentDescription = if (caseSensitive) {
                                            "Match case enabled"
                                        } else {
                                            "Enable match case"
                                        }
                                    },
                                ) {
                                    Text(
                                        "Aa",
                                        color = if (caseSensitive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (caseSensitive) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                            */
                            IconButton(onClick = {
                                focusManager.clearFocus()
                                activeTemplateIndex = null
                                showAdvanced = !showAdvanced
                            }) {
                                Icon(
                                    if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    if (showAdvanced) "Hide matching options" else "Show matching options",
                                )
                            }
                        }
                    },
                )
            }
            if (showAdvanced) {
                item {
                    SnippetMatchingOptionsCard(
                        immediate = instant,
                        onImmediateChanged = { instant = it },
                        delimiters = delimiters,
                        onDelimitersChanged = { delimiters = it },
                        alternativeTriggers = otherTriggers,
                        onAlternativeTriggersChanged = { otherTriggers = it },
                        caseSensitive = caseSensitive,
                        onCaseSensitiveChanged = { enabled ->
                            caseSensitive = enabled
                            if (!enabled) propagateCase = true
                        },
                        leftWord = leftWord,
                        onLeftWordChanged = { leftWord = it },
                        rightWord = rightWord,
                        onRightWordChanged = { rightWord = it },
                        propagateCase = propagateCase,
                        onPropagateCaseChanged = { enabled ->
                            propagateCase = enabled
                            if (!enabled) caseSensitive = true
                        },
                        uppercaseStyle = uppercaseStyle,
                        onUppercaseStyleChanged = { uppercaseStyle = it },
                        searchTerms = searchTerms,
                        onSearchTermsChanged = { searchTerms = it },
                        excludedAppCount = excludedPackages.size,
                        onOpenExcludedApps = {
                            focusManager.clearFocus()
                            showExcludedApps = true
                        },
                    )
                }
            }

            item {
                TagEditor(
                    resetKey = initial?.id,
                    availableTags = availableTags,
                    selectedTags = tags,
                    onTagsChanged = { tags = it },
                    onFocus = { activeTemplateIndex = null },
                )
            }

            item {
                Text("Replacements", style = MaterialTheme.typography.titleSmall)
            }
            itemsIndexed(replacementFields, key = { index, _ -> "replacement-$index" }) { index, replacement ->
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { nextValue ->
                        val updated = atomicTemplateValueChange(replacement, nextValue)
                        updateReplacement(index, updated)
                        if (
                            updated.text == replacement.text &&
                            updated.selection.collapsed &&
                            updated.selection != replacement.selection
                        ) {
                            val name = TemplateTokenEditor.variableNameAt(updated.text, updated.selection.end)
                            val localVariable = variables.firstOrNull { it.name == name }
                            val globalVariable = globalVariables.firstOrNull { it.name == name }
                            val capture = if (triggerKind == TriggerKind.REGEX) {
                                TemplateTokenEditor.captureReferenceAt(
                                    updated.text,
                                    updated.selection.end,
                                    captureCatalog?.namedCaptures.orEmpty(),
                                )
                            } else null
                            when {
                                localVariable != null -> variableEditTarget = VariableEditTarget(localVariable, global = false)
                                globalVariable != null -> variableEditTarget = VariableEditTarget(globalVariable, global = true)
                                capture != null -> captureEditTarget = RegexCaptureEditTarget(index, capture)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) activeTemplateIndex = index }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || !replacement.selection.collapsed) return@onPreviewKeyEvent false
                            val result = when (event.key) {
                                Key.Backspace -> TemplateTokenEditor.deleteBackward(replacement.text, replacement.selection.end)
                                Key.Delete -> TemplateTokenEditor.deleteForward(replacement.text, replacement.selection.end)
                                else -> return@onPreviewKeyEvent false
                            }
                            if (result.text == replacement.text) false else {
                                updateReplacement(index, TextFieldValue(result.text, TextRange(result.cursor)))
                                true
                            }
                        },
                    label = { Text("Replacement #${index + 1}") },
                    minLines = 3,
                    visualTransformation = templateTokenVisualTransformation(
                        variables,
                        globalVariables,
                        captureCatalog?.namedCaptures.orEmpty(),
                    ),
                    trailingIcon = if (replacementFields.size > 1) ({
                        IconButton(onClick = {
                            replacementFields = replacementFields.toMutableList().also { it.removeAt(index) }
                            activeTemplateIndex = null
                        }) { Icon(Icons.Default.Delete, "Delete replacement") }
                    }) else null,
                )
            }
            item {
                OutlinedButton(onClick = {
                    replacementFields = replacementFields + TextFieldValue("")
                    activeTemplateIndex = null
                }) {
                    Icon(Icons.Default.Add, null)
                    Text("Add replacement")
                }
            }
            if (showReplacementSelection) {
                item {
                    ReplacementSelectionCard(
                        selectionMode = selectionMode,
                        onSelectionModeChanged = { selectionMode = it },
                    )
                }
            }
        }
    }

    variableEditTarget?.let { target ->
        TemplateVariableEditorDialog(
            variable = target.variable,
            initialGlobal = target.global,
            reservedVariables = variables + globalVariables,
            snippets = availableSnippets.filterNot { it.id == initial?.id },
            onDismiss = {
                variableEditTarget = null
            },
            onSave = { original, updated, saveAsGlobal ->
                when {
                    // Was global and stays global
                    target.global && saveAsGlobal -> {
                        onGlobalVariablesChanged(
                            globalVariables.map {
                                if (it.name == original?.name) updated else it
                            },
                        )
                    }

                    // Was local and stays local
                    !target.global && !saveAsGlobal -> {
                        variables = variables.map {
                            if (it.name == original?.name) updated else it
                        }
                    }

                    // Global -> local
                    target.global && !saveAsGlobal -> {
                        onGlobalVariablesChanged(
                            globalVariables.filterNot {
                                it.name == original?.name
                            },
                        )

                        variables = variables + updated
                    }

                    // Local -> global
                    !target.global && saveAsGlobal -> {
                        variables = variables.filterNot {
                            it.name == original?.name
                        }

                        onGlobalVariablesChanged(
                            globalVariables + updated,
                        )
                    }
                }

                if (
                    original != null &&
                    original.name != updated.name
                ) {
                    rewriteVariableReferences(
                        original.name,
                        updated.name,
                    )
                }

                variableEditTarget = null
            },
            onDelete = {
                if (target.global) {
                    onGlobalVariablesChanged(
                        globalVariables.filterNot {
                            it.name == target.variable.name
                        },
                    )
                } else {
                    variables = variables.filterNot {
                        it.name == target.variable.name
                    }
                }

                rewriteVariableReferences(
                    target.variable.name,
                    null,
                )

                variableEditTarget = null
            },
        )
    }

    captureEditTarget?.let { target ->
        val reference = target.span.token.removePrefix("{{").removeSuffix("}}")
        RegexCaptureEditorDialog(
            pattern = triggerText,
            initialReference = reference,
            onDismiss = { captureEditTarget = null },
            onSave = { updatedReference ->
                val field = replacementFields.getOrNull(target.replacementIndex)
                if (field != null && target.span.endExclusive <= field.text.length) {
                    val token = "{{$updatedReference}}"
                    val text = field.text.replaceRange(target.span.start, target.span.endExclusive, token)
                    updateReplacement(
                        target.replacementIndex,
                        TextFieldValue(text, TextRange(target.span.start + token.length)),
                    )
                }
                captureEditTarget = null
            },
            onDelete = {
                val field = replacementFields.getOrNull(target.replacementIndex)
                if (field != null && target.span.endExclusive <= field.text.length) {
                    val text = field.text.removeRange(target.span.start, target.span.endExclusive)
                    updateReplacement(
                        target.replacementIndex,
                        TextFieldValue(text, TextRange(target.span.start)),
                    )
                }
                captureEditTarget = null
            },
        )
    }
    if (showExcludedApps) {
        AppExclusionPicker(
            title = "Exclude this snippet from apps",
            selectedPackages = excludedPackages,
            onDismiss = { showExcludedApps = false },
            onSave = {
                excludedPackages = it
                showExcludedApps = false
            },
        )
    }
}

@Composable
private fun templateTokenVisualTransformation(
    variables: List<TemplateVariable>,
    globalVariables: List<TemplateVariable>,
    namedCaptures: Set<String> = emptySet(),
): VisualTransformation {
    val colors = MaterialTheme.colorScheme
    return VisualTransformation { text ->
        val display = text.text.toCharArray()
        val tokenMatches = Regex("\\$\\|\\$|\\{\\{[^{}]+\\}\\}|\\{[^{}]+\\}").findAll(text.text).toList()
        tokenMatches.forEach { match ->
            if (match.value.startsWith("{{")) {
                val expression = match.value.removePrefix("{{").removeSuffix("}}")
                val name = expression.substringBefore('.')
                val variable = variables.firstOrNull { it.name == name }
                    ?: globalVariables.firstOrNull { it.name == name }
                if (variable != null) {
                    val label = "${variableTypeGlyph(variable.type)} $expression"
                        .padEnd(match.value.length)
                        .take(match.value.length)
                    label.forEachIndexed { index, char -> display[match.range.first + index] = char }
                } else if (expression.toIntOrNull() != null || expression in namedCaptures) {
                    val label = "# $expression".padEnd(match.value.length).take(match.value.length)
                    label.forEachIndexed { index, char -> display[match.range.first + index] = char }
                }
            }
        }
        val highlighted = AnnotatedString.Builder(String(display))
        tokenMatches.forEach { match ->
            if (TemplateTokenEditor.isTemplateToken(match.value)) {
                val expression = match.value.removePrefix("{{").removeSuffix("}}")
                val name = expression.substringBefore('.')
                val global = globalVariables.any { it.name == name } && variables.none { it.name == name }
                highlighted.addStyle(
                    SpanStyle(
                        color = if (global) colors.onTertiaryContainer else colors.onSecondaryContainer,
                        background = if (global) colors.tertiaryContainer else colors.secondaryContainer,
                    ),
                    match.range.first,
                    match.range.last + 1,
                )
            }
        }
        TransformedText(highlighted.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private fun variableTypeGlyph(type: String): String = when (type.lowercase()) {
    "echo" -> "T"
    "date" -> "◷"
    "clipboard" -> "▣"
    "random" -> "⤨"
    "choice" -> "≡"
    "form" -> "□"
    "match" -> "↪"
    else -> "!"
}

@Composable
private fun TagEditor(
    resetKey: Long?,
    availableTags: List<String>,
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
    onFocus: () -> Unit,
) {
    var input by remember(resetKey) { mutableStateOf("") }
    var inputVisible by remember(resetKey) { mutableStateOf(false) }
    val focusRequester = remember(resetKey) { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val suggestions = availableTags
        .filterNot { candidate -> selectedTags.any { it.equals(candidate, ignoreCase = true) } }
        .filter { input.isBlank() || it.contains(input.trim(), ignoreCase = true) }
        .take(8)

    fun closeInput() {
        inputVisible = false
        input = ""
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun addTag(raw: String) {
        val normalized = raw.trim().removePrefix("#").trim()
        if (normalized.isBlank()) return
        val canonical = availableTags.firstOrNull { it.equals(normalized, ignoreCase = true) } ?: normalized
        onTagsChanged(selectedTags.filterNot { it.equals(canonical, ignoreCase = true) }.toSet() + canonical)
        closeInput()
    }

    BackHandler(enabled = inputVisible) { closeInput() }
    LaunchedEffect(inputVisible) {
        if (inputVisible) focusRequester.requestFocus()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Tags",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 8.dp),
            )
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(selectedTags.sortedWith(String.CASE_INSENSITIVE_ORDER)) { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onTagsChanged(selectedTags.filterNot { it.equals(tag, ignoreCase = true) }.toSet()) },
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .semantics { contentDescription = "Remove tag $tag" },
                        label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { Icon(Icons.Default.Close, null) },
                    )
                }
            }
            if (!inputVisible) {
                IconButton(
                    onClick = { inputVisible = true },
                    modifier = Modifier.semantics { contentDescription = "Add tag" },
                ) {
                    Icon(Icons.Default.Add, null)
                }
            }
        }
        if (inputVisible) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused) onFocus() },
                    label = { Text("Add tag") },
                    placeholder = { Text("Existing or new tag") },
                    leadingIcon = { Text("#") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTag(input) }),
                )
                IconButton(
                    onClick = { addTag(input) },
                    enabled = input.trim().removePrefix("#").isNotBlank(),
                    modifier = Modifier.semantics { contentDescription = "Save tag" },
                ) {
                    Icon(Icons.Default.Check, null)
                }
                IconButton(
                    onClick = ::closeInput,
                    modifier = Modifier.semantics { contentDescription = "Cancel adding tag" },
                ) {
                    Icon(Icons.Default.Close, null)
                }
            }
            if (suggestions.isNotEmpty()) {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(suggestions) { tag ->
                        AssistChip(
                            onClick = { addTag(tag) },
                            label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
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

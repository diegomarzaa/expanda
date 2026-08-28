package dev.diego.expanda.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.diego.expanda.data.EspansoFolderAccess
import dev.diego.expanda.data.EspansoSourceFile
import dev.diego.expanda.data.EspansoSourceText
import dev.diego.expanda.data.EspansoYamlCodec
import kotlinx.coroutines.delay

private data class SourceStatus(val text: String, val error: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SnippetSourceScreen(
    files: List<EspansoSourceFile>,
    linkedFolderUri: String?,
    initialPath: String? = null,
    initialMatchIndex: Int? = null,
    embedded: Boolean = false,
    onDismiss: () -> Unit,
    onApply: (EspansoSourceFile, (Result<Int>) -> Unit) -> Unit,
    onChooseFolder: () -> Unit,
    onSyncFolder: ((Result<Int>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sourceFocusRequester = remember { FocusRequester() }
    var selectedPath by remember(initialPath, files) {
        mutableStateOf(initialPath?.takeIf { path -> files.any { it.relativePath == path } } ?: files.firstOrNull()?.relativePath)
    }
    val selected = files.firstOrNull { it.relativePath == selectedPath } ?: files.firstOrNull()
    var draft by remember(selected?.relativePath, selected?.content) {
        mutableStateOf(TextFieldValue(selected?.content.orEmpty()))
    }
    var savedSource by remember(selected?.relativePath, selected?.content) { mutableStateOf(selected?.content.orEmpty()) }
    var status by remember { mutableStateOf<SourceStatus?>(null) }
    var pendingDocument by remember { mutableStateOf<EspansoSourceFile?>(null) }
    var showGuide by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var highlightedRange by remember(initialPath, initialMatchIndex) { mutableStateOf<TextRange?>(null) }
    var initialTargetShown by remember(initialPath, initialMatchIndex) { mutableStateOf(false) }
    val changed = draft.text != savedSource
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(files) {
        if (selectedPath !in files.map(EspansoSourceFile::relativePath)) {
            selectedPath = files.firstOrNull()?.relativePath
        }
    }

    LaunchedEffect(selected?.relativePath, initialPath, initialMatchIndex) {
        if (
            !initialTargetShown &&
            selected != null &&
            selected.relativePath == initialPath &&
            initialMatchIndex != null
        ) {
            val span = EspansoSourceText.matchSpans(draft.text).getOrNull(initialMatchIndex)
            if (span != null) {
                initialTargetShown = true
                highlightedRange = TextRange(span.start, span.endExclusive)
                draft = draft.copy(selection = TextRange(span.start.coerceIn(0, draft.text.length)))
                delay(80)
                sourceFocusRequester.requestFocus()
                delay(80)
                keyboardController?.hide()
                delay(4_000)
                highlightedRange = null
            }
        }
    }

    fun leave() {
        if (changed) confirmDiscard = true else onDismiss()
    }

    fun validate(): EspansoSourceFile? {
        val file = selected ?: return null
        return runCatching {
            EspansoYamlCodec.decode(draft.text.removePrefix("\uFEFF"), file.relativePath)
            file.copy(content = draft.text)
        }.onFailure {
            status = SourceStatus(it.message ?: "Invalid Espanso YAML", error = true)
        }.getOrNull()
    }

    fun copy(label: String, text: String) {
        runCatching {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(label, text))
        }.onSuccess { status = SourceStatus("$label copied") }
            .onFailure { status = SourceStatus("Could not copy: ${it.message}", error = true) }
    }

    fun openExternal() {
        val tree = linkedFolderUri?.let(Uri::parse) ?: return
        val file = selected ?: return
        val uri = EspansoFolderAccess.findFileUri(context, tree, file.relativePath)
        if (uri == null) {
            status = SourceStatus("Sync the linked folder before opening this file.", error = true)
            return
        }
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, "text/yaml")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Open YAML with")
        runCatching { context.startActivity(chooser) }
            .onFailure { status = SourceStatus("No app can edit this YAML file.", error = true) }
    }

    fun syncFolder() {
        if (linkedFolderUri == null || changed || saving || syncing) return
        syncing = true
        status = SourceStatus("Syncing from folder…")
        onSyncFolder { result ->
            syncing = false
            status = result.fold(
                onSuccess = { count -> SourceStatus("Synced $count matches from the folder") },
                onFailure = { error -> SourceStatus("Sync failed: ${error.message ?: "Unknown error"}", error = true) },
            )
        }
    }

    BackHandler(enabled = !embedded, onBack = ::leave)

    Scaffold(
        contentWindowInsets = if (embedded) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text("Espanso source") },
                    navigationIcon = {
                        IconButton(onClick = ::leave) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        if (imeVisible) {
                            IconButton(
                                onClick = { validate()?.let { pendingDocument = it } },
                                enabled = changed && !saving && !syncing && selected != null,
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Save source")
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!imeVisible) {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showGuide = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Info, null)
                                Text(" Guide")
                            }
                            OutlinedButton(
                                onClick = { copy("Espanso source", draft.text) },
                                modifier = Modifier.weight(1f),
                                enabled = selected != null,
                            ) {
                                Icon(Icons.Default.ContentCopy, null)
                                Text(" Copy")
                            }
                            if (linkedFolderUri != null) {
                                OutlinedButton(
                                    onClick = ::openExternal,
                                    modifier = Modifier.weight(1f),
                                    enabled = selected != null && !saving && !syncing,
                                ) {
                                    Icon(Icons.Default.Edit, null)
                                    Text(" Open")
                                }
                            }
                        }
                        Button(
                            onClick = { validate()?.let { pendingDocument = it } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = changed && !saving && !syncing && selected != null,
                        ) {
                            Icon(Icons.Default.Check, null)
                            Text(" Save source")
                        }
                    }
                }
            }
        },
    ) { padding ->
        val contentPadding = if (embedded) {
            Modifier.padding(
                start = padding.calculateStartPadding(layoutDirection),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = padding.calculateBottomPadding(),
            )
        } else {
            Modifier.padding(padding)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(contentPadding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (embedded) 8.dp else 10.dp),
        ) {
            if (embedded && imeVisible) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(
                        onClick = { validate()?.let { pendingDocument = it } },
                        enabled = changed && !saving && !syncing && selected != null,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save source")
                    }
                }
            }
            if (!imeVisible) {
                SourceFolderControls(
                    linked = linkedFolderUri != null,
                    changed = changed,
                    busy = saving || syncing,
                    onChooseFolder = onChooseFolder,
                    onSyncFolder = ::syncFolder,
                )
                if (files.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        files.forEach { file ->
                            AssistChip(
                                onClick = {
                                    if (!changed || file.relativePath == selectedPath) {
                                        selectedPath = file.relativePath
                                        if (file.relativePath != initialPath) highlightedRange = null
                                    } else {
                                        status = SourceStatus("Save or discard this file before switching.", error = true)
                                    }
                                },
                                label = { Text(file.relativePath) },
                            )
                        }
                    }
                }
            }
            if (selected == null) {
                Spacer(Modifier.weight(1f))
                Text(
                    if (linkedFolderUri == null) {
                        "No source files yet. Create a snippet from the list, or link a folder above to use existing .yml files."
                    } else {
                        "No .yml files found in the linked folder. Sync the folder or add files there."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
            } else {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        if (it.text.length <= 2_000_000) {
                            draft = it
                            highlightedRange = null
                            status = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(sourceFocusRequester),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    visualTransformation = VisualTransformation { original ->
                        val transformed = buildAnnotatedString {
                            append(original)
                            highlightedRange?.let { range ->
                                val start = range.start.coerceIn(0, original.length)
                                val end = range.end.coerceIn(start, original.length)
                                if (end > start) {
                                    addStyle(
                                        SpanStyle(background = Color(0xFFFFE082).copy(alpha = 0.62f)),
                                        start,
                                        end,
                                    )
                                }
                            }
                        }
                        TransformedText(transformed, OffsetMapping.Identity)
                    },
                    label = { Text(selected.relativePath) },
                    supportingText = if (imeVisible) null else { { Text("${draft.text.length} characters") } },
                )
            }
            if (!imeVisible) {
                status?.let { current ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (current.error) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (current.error) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(current.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (saving || syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }

    pendingDocument?.let { document ->
        val decoded = remember(document) {
            EspansoYamlCodec.decode(document.content.removePrefix("\uFEFF"), document.relativePath)
        }
        AlertDialog(
            onDismissRequest = { if (!saving) pendingDocument = null },
            title = { Text("Save ${document.relativePath}?") },
            text = { Text("${decoded.matches.size} matches and ${decoded.globalVariables.size} global variables are valid.") },
            confirmButton = {
                Button(
                    onClick = {
                        saving = true
                        onApply(document) { result ->
                            saving = false
                            result.onSuccess { count ->
                                savedSource = draft.text
                                status = SourceStatus("Saved $count matches without reformatting the file")
                                pendingDocument = null
                            }.onFailure {
                                status = SourceStatus(it.message ?: "Could not save source", error = true)
                                pendingDocument = null
                            }
                        }
                    },
                    enabled = !saving,
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDocument = null }, enabled = !saving) { Text("Cancel") }
            },
        )
    }

    if (showGuide) {
        SourceGuideDialog(
            onDismiss = { showGuide = false },
            onCopyPrompt = {
                copy("Espanso AI editing prompt", espansoAiPrompt(selected?.relativePath.orEmpty(), draft.text))
                showGuide = false
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard source changes?") },
            text = { Text("Edits made since the last save will be lost.") },
            confirmButton = { Button(onClick = onDismiss) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun SourceGuideDialog(onDismiss: () -> Unit, onCopyPrompt: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AutoAwesome, null) },
        title = { Text("Source and AI guide") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GuideBlock("Plain-text .yml", "These are normal editable files in the folder you choose.")
                GuideBlock("Espanso format", "They use the same match-set .yml format as the Espanso desktop app.")
                GuideBlock("Safe editing", "Ask for a complete file and keep comments and unrelated matches unchanged.")
                GuideBlock("Folder sync", "Sync from the linked folder before editing when another device or app changed its files.")
                GuideBlock("Documentation", "The same source works with the official Espanso match documentation.")
            }
        },
        confirmButton = {
            Button(onClick = onCopyPrompt) {
                Icon(Icons.Default.AutoAwesome, null)
                Text(" Copy AI prompt")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SourceFolderControls(
    linked: Boolean,
    changed: Boolean,
    busy: Boolean,
    onChooseFolder: () -> Unit,
    onSyncFolder: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (linked) Icons.Default.Folder else Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        if (linked) "Snippet folder linked" else "Stored in Expanda",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (linked) {
                            "Edits here are written back to the linked folder."
                        } else {
                            "Snippets live inside the app. Link a folder anytime to sync with Espanso desktop or other tools."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onChooseFolder,
                    modifier = Modifier.weight(1f),
                    enabled = !changed && !busy,
                ) {
                    Icon(Icons.Default.Folder, null)
                    Text(if (linked) " Change folder" else " Link folder")
                }
                if (linked) {
                    OutlinedButton(
                        onClick = onSyncFolder,
                        modifier = Modifier.weight(1f),
                        enabled = !changed && !busy,
                    ) {
                        Icon(Icons.Default.Sync, null)
                        Text(" Sync folder")
                    }
                }
            }
            if (changed) {
                Text(
                    "Save or discard this file before changing or syncing the folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GuideBlock(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun espansoAiPrompt(path: String, source: String): String = """
    Edit the Espanso match-set file "$path" using standard Espanso YAML syntax.
    Follow https://espanso.org/docs/matches/basics/ and the related official pages for variables, forms and regex triggers.
    Preserve comments, ordering, quoting, multiline scalar style and every unrelated match.
    Return only the complete YAML file, without Markdown fences or explanation.

    Requested change:
    [DESCRIBE THE CHANGE HERE]

    Current file:
    $source
""".trimIndent()

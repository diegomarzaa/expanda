package dev.diego.expanda.gitsync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.diego.expanda.ExpandaApplication
import dev.diego.expanda.ui.ImportKind
import dev.diego.expanda.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * "Git sync" card for Settings > Import & export. Collapsed by default. [locallyLinked] is true when
 * upstream's SAF folder mode is active; Git sync is then disabled (one source at a time).
 * "Sync now" runs in [GitSyncController], so leaving the screen does not cancel it.
 */
@Composable
fun GitSyncCard(locallyLinked: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { GitSyncPrefs(context) }
    // Preferences and the token file are read off the main thread.
    val loaded by produceState<Pair<GitSyncConfig, GitSyncStatus>?>(null, prefs) {
        value = withContext(Dispatchers.IO) { prefs.loadConfig() to prefs.loadStatus() }
    }
    val snapshot = loaded
    if (snapshot == null) {
        Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Git sync", fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        GitSyncCardContent(prefs, snapshot.first, snapshot.second, locallyLinked, modifier)
    }
}

@Composable
private fun GitSyncCardContent(
    prefs: GitSyncPrefs,
    initial: GitSyncConfig,
    initialStatus: GitSyncStatus,
    locallyLinked: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ExpandaApplication
    val ui by GitSyncController.state.collectAsState()

    var expanded by remember { mutableStateOf(false) } // set by needsAction below
    var issuesOpen by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(initial) }
    var repoUrl by remember { mutableStateOf(GitSyncInput.displayUrl(initial.owner, initial.repo, initial.branch, initial.host)) }
    var token by remember { mutableStateOf(initial.token) }
    var status by remember { mutableStateOf(initialStatus) }
    val syncing = ui.syncing
    val busy = syncing || ui.pending != null

    // Expanded by default (and re-expanded when it becomes true) while the user has something to do:
    // waiting for the first manual sync, a replace awaiting confirmation, or a failed last sync.
    val needsAction = ui.pending != null || status.waitingForFirstSync || !status.lastOk
    LaunchedEffect(needsAction) { if (needsAction) expanded = true }
    val viewModel: MainViewModel = viewModel()
    val uiScope = rememberCoroutineScope()
    val backups = remember { SyncBackups(SyncBackups.directory(context)) }
    var newestBackup by remember { mutableStateOf(backups.newest()) }
    var confirmRestore by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    // Blank is allowed (clears the repository); anything else must parse.
    val parsed = remember(repoUrl) { if (repoUrl.isBlank()) null else GitSyncInput.parseRepositoryUrl(repoUrl) }
    val urlError = parsed?.error
    val tokenHost = parsed?.takeIf { it.valid }?.host ?: GitSyncInput.GITHUB_HOST
    val tokenError = remember(token, tokenHost) { if (token.isBlank()) null else GitSyncInput.validateToken(token.trim(), tokenHost) }
    val inputValid = urlError == null && tokenError == null

    // A token belongs to the host it was entered for: if the URL moves to another host while the field still
    // holds the saved token, drop it instead of sending it there.
    val enteredHost = parsed?.takeIf { it.valid }?.host
    LaunchedEffect(enteredHost) {
        if (enteredHost != null && enteredHost != saved.host && saved.token.isNotEmpty() && token == saved.token) {
            token = ""
            note = "Token removed because the host changed. Enter a token for $enteredHost if the repository is private."
        }
    }

    // Background syncs write the status; refresh the card when they do.
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            status = prefs.loadStatus()
            newestBackup = backups.newest()
        }
        prefs.registerListener(listener)
        status = prefs.loadStatus()
        newestBackup = backups.newest()
        onDispose { prefs.unregisterListener(listener) }
    }

    fun current(): GitSyncConfig =
        if (parsed != null && parsed.valid) GitSyncConfig(parsed.owner, parsed.repo, parsed.branch, token.trim(), parsed.host)
        else GitSyncConfig(token = token.trim())

    fun refreshSaved() {
        saved = prefs.loadConfig()
        // The storage guard drops a token that is carried over to another host.
        if (token.isNotEmpty() && saved.token.isEmpty()) token = ""
    }

    fun save() {
        if (!inputValid) return
        val config = current()
        prefs.saveConfig(config)
        refreshSaved()
        if (config.configured) GitSyncScheduler.schedule(context) else GitSyncScheduler.cancel(context)
        note = if (config.configured) "Saved. Tap Sync now for the first download; after that it checks every ${GitSyncScheduler.SYNC_INTERVAL_HOURS} hours (and when you open the app) on a network connection." else "Saved. Enter the repository URL to enable sync."
    }

    fun syncNow() {
        if (syncing || !inputValid) return
        val config = current()
        prefs.saveConfig(config)
        refreshSaved()
        note = null
        GitSyncController.syncNow(app)
    }

    fun clearToken() {
        prefs.clearToken()
        saved = prefs.loadConfig()
        token = ""
        note = "Token cleared. A public repository still syncs without one. To stop syncing, clear the repository URL and tap Save."
    }

    fun restoreBackup() {
        val file = newestBackup ?: return
        confirmRestore = false
        restoring = true
        uiScope.launch {
            // Under the sync gate so a background sync cannot overwrite the restore halfway.
            val message = GitSyncer.exclusive {
                runCatching {
                    val text = withContext(Dispatchers.IO) { file.readText() }
                    val prepared = viewModel.prepareImport(text, file.name).getOrThrow()
                    check(prepared.kind == ImportKind.FULL_BACKUP) { "The backup file is not an Expanda full backup." }
                    var result: Result<*>? = null
                    viewModel.applyImport(prepared) { result = it }.join()
                    result!!.getOrThrow()
                }.fold(
                    onSuccess = { "Restored. Clear the repository URL (or change the repository) and tap Save so Git sync does not replace the restored snippets again." },
                    onFailure = { "Restore failed: ${it.message ?: it.javaClass.simpleName}" },
                )
            }
            note = message
            restoring = false
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore snippets from before Git sync?") },
            text = {
                Text(
                    "This replaces your current snippets, settings and shortcuts with the full backup that was saved " +
                        "just before Git sync first replaced your snippets" +
                        (newestBackup?.let { " (${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.lastModified()))})" } ?: "") +
                        ". Git sync will replace the restored snippets again the next time the branch changes " +
                        "or you tap Sync now, so clear the repository URL and tap Save afterwards if you want to keep them.",
                )
            },
            confirmButton = { TextButton(onClick = ::restoreBackup) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }

    ui.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = GitSyncController::dismissReplace,
            title = { Text("Replace local snippets?") },
            text = {
                Text(
                    "Replace ${pending.localFiles} local snippet files with ${pending.remoteFiles} files from the repository? " +
                        "A backup is exported first.",
                )
            },
            confirmButton = { TextButton(onClick = { GitSyncController.confirmReplace(app) }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = GitSyncController::dismissReplace) { Text("Cancel") } },
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Git sync", fontWeight = FontWeight.SemiBold)
                    Text(
                        summaryLine(status),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!status.lastOk) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (status.lastOk && status.issueCount > 0) {
                        val noun = if (status.issueCount == 1) "issue" else "issues"
                        Text(
                            "Last sync: ${status.issueCount} $noun" + if (issuesOpen) " \u25B4" else " \u25BE",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = "Show or hide sync issues") { issuesOpen = !issuesOpen }
                                .padding(vertical = 4.dp),
                        )
                    }
                    if (issuesOpen && status.lastOk && status.issueCount > 0) {
                        Column(
                            Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            status.issues.forEach {
                                Text(
                                    "${it.severity} \u00B7 ${it.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (it.severity == "ERROR") MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (status.issueCount > status.issues.size) {
                                Text(
                                    "\u2026 and ${status.issueCount - status.issues.size} more",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse Git sync" else "Expand Git sync",
                    )
                }
            }
            if (expanded) {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (locallyLinked) {
                        Text(
                            "A local folder is linked, so Git sync is disabled (one source at a time). " +
                                "Unlink the folder to use Git sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "Pulls match/**/*.yml from a Git repository on GitHub or an open-source Forgejo/Gitea host such as Codeberg, laid out like espanso's config folder. " +
                            "Snippets edited on the phone are replaced when the branch changes or when you tap Sync now, so edit them in the repository. " +
                            "Public repositories need no token (hosts rate-limit anonymous requests). For a private one, use a token with read-only access to just that repository and an expiry. " +
                            "The token is stored on this device and sent only to the host in the repository URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val enabled = !locallyLinked && !syncing && !restoring
                    OutlinedTextField(
                        repoUrl,
                        { repoUrl = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Repository URL") },
                        supportingText = {
                            Text(urlError ?: "GitHub: https://github.com/owner/repo, owner/repo, or add /tree/<branch>. Codeberg, Forgejo or Gitea: https://host/owner/repo, or add /src/branch/<branch>. Default: the repository's default branch.")
                        },
                        isError = urlError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        enabled = enabled,
                    )
                    OutlinedTextField(
                        token,
                        { token = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Token (optional, read-only)") },
                        supportingText = tokenError?.let { { Text(it) } },
                        isError = tokenError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = enabled,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = ::save, modifier = Modifier.weight(1f), enabled = enabled && inputValid) { Text("Save") }
                        Button(
                            onClick = ::syncNow,
                            modifier = Modifier.weight(1f),
                            enabled = enabled && !busy && inputValid && current().configured,
                        ) { Text(if (syncing) "Syncing…" else "Sync now") }
                    }
                    OutlinedButton(
                        onClick = ::clearToken,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = token.isNotEmpty() && !syncing,
                    ) { Text("Clear token") }
                    if (newestBackup != null) {
                        OutlinedButton(
                            onClick = { confirmRestore = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && !restoring && !locallyLinked,
                        ) { Text("Restore snippets from before Git sync") }
                    }
                    note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Without the issues part (rendered separately as a tappable toggle). */
internal fun summaryLine(status: GitSyncStatus): String {
    if (status.waitingForFirstSync) return "Waiting for first manual sync. Tap Sync now to download your snippets."
    val at = status.lastSyncAt ?: return "Never synced. Pulls match/**/*.yml from a Git repository."
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    if (!status.lastOk) return "Sync failed ${fmt.format(Date(status.lastCheckedAt ?: at))}: ${status.lastMessage.orEmpty()}"
    if (status.unchanged) return "Checked ${fmt.format(Date(status.lastCheckedAt ?: at))} \u00B7 no changes"
    return "Synced ${fmt.format(Date(at))} \u00B7 ${status.lastMessage.orEmpty().substringBefore(',')}"
}

package dev.diego.expanda.ui

import android.app.Application
import android.os.Build
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.diego.expanda.BuildConfig
import dev.diego.expanda.ExpandaApplication
import dev.diego.expanda.data.AppSettings
import dev.diego.expanda.data.BackupCodec
import dev.diego.expanda.data.ClipboardEntry
import dev.diego.expanda.data.ColorSchemeMode
import dev.diego.expanda.data.CompatibilityIssue
import dev.diego.expanda.data.CsvCodec
import dev.diego.expanda.data.DashboardStats
import dev.diego.expanda.data.EspansoYamlCodec
import dev.diego.expanda.data.EspansoFolderAccess
import dev.diego.expanda.data.EspansoSourceFile
import dev.diego.expanda.data.MatchTrigger
import dev.diego.expanda.data.OnboardingState
import dev.diego.expanda.data.OnboardingStatus
import dev.diego.expanda.data.initialOnboardingStatus
import dev.diego.expanda.data.TextMatch
import dev.diego.expanda.data.SnippetSortMode
import dev.diego.expanda.data.ThemeMode
import dev.diego.expanda.data.TemplateVariable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.diego.expanda.data.sortedForDisplay

data class MainUiState(
    val matches: List<TextMatch> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val clipboardEntries: List<ClipboardEntry> = emptyList(),
    val enabledActionIds: Set<String> = emptySet(),
    val actionShortcutOverrides: Map<String, String> = emptyMap(),
    val matchesLoaded: Boolean = false,
    val onboarding: OnboardingState = OnboardingState(),
    val search: String = "",
    val selectedTag: String? = null,
) {
    val tags: List<String> get() = matches
        .flatMap { it.tags }
        .distinctBy(String::lowercase)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    val visibleMatches: List<TextMatch> get() = matches.filter { match ->
        (selectedTag == null || match.tags.any { it.equals(selectedTag, ignoreCase = true) }) &&
            (search.isBlank() || (
                match.triggers.map(MatchTrigger::pattern) + match.label + match.replacements +
                    match.tags + match.searchTerms
                ).any { it.contains(search, ignoreCase = true) })
    }.sortedForDisplay(settings.snippetSortMode)
}

data class ImportSummary(
    val imported: Int,
    val issues: List<CompatibilityIssue> = emptyList(),
)

enum class ImportKind(val label: String) {
    FULL_BACKUP("Expanda full backup"),
    SNIPPET_BACKUP("Expanda snippet backup"),
    ESPANSO("Espanso YAML"),
    ESPANSO_FOLDER("Espanso match folder"),
    CSV("CSV"),
}

data class PreparedImport(
    val kind: ImportKind,
    val matches: List<TextMatch>,
    val globalVariables: List<TemplateVariable> = emptyList(),
    val settings: AppSettings? = null,
    val actions: BackupCodec.ActionSnapshot? = null,
    val issues: List<CompatibilityIssue> = emptyList(),
    val sourceFiles: List<EspansoSourceFile> = emptyList(),
    val linkedFolderUri: String? = null,
) {
    val replacesExistingData: Boolean get() = kind == ImportKind.FULL_BACKUP
    val replacesSnippetLibrary: Boolean get() = replacesExistingData || kind == ImportKind.ESPANSO_FOLDER
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ExpandaApplication
    private val repository = app.matchRepository
    private val settingsRepository = app.settingsRepository
    private val clipboardRepository = app.clipboardRepository
    private val actionSettingsStore = app.actionSettingsStore
    private val onboardingStore = app.onboardingStore
    private val sourceRepository = app.espansoSourceRepository
    val sourceFiles: StateFlow<List<EspansoSourceFile>> = sourceRepository.files
    private val filters = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String?>>("" to null)

    private val actionSettings = combine(
        actionSettingsStore.enabledIds,
        actionSettingsStore.shortcutOverrides,
    ) { enabledIds, shortcutOverrides -> enabledIds to shortcutOverrides }

    private val matchContent = combine(
        repository.matches,
        repository.isReady,
        onboardingStore.state,
    ) { matches, ready, onboarding -> Triple(matches, ready, onboarding) }

    val uiState: StateFlow<MainUiState> = combine(
        matchContent,
        settingsRepository.settings,
        clipboardRepository.entries,
        actionSettings,
        filters,
    ) { content, settings, clipboard, actions, (search, tag) ->
        MainUiState(
            matches = content.first,
            settings = settings,
            clipboardEntries = clipboard,
            enabledActionIds = actions.first,
            actionShortcutOverrides = actions.second,
            matchesLoaded = content.second,
            onboarding = content.third,
            search = search,
            selectedTag = tag,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            repository.isReady.filter { it }.first()
            if (onboardingStore.state.value.status == OnboardingStatus.UNINITIALIZED) {
                when (initialOnboardingStatus(repository.matches.value.isNotEmpty())) {
                    OnboardingStatus.ACTIVE -> {
                        onboardingStore.activate()
                    }
                    OnboardingStatus.SKIPPED -> onboardingStore.skip()
                    else -> Unit
                }
            }
            if (!onboardingStore.state.value.workspaceReady && repository.matches.value.isNotEmpty()) {
                onboardingStore.finishWorkspaceSetup()
            }
        }
    }

    fun setSearch(value: String) { filters.value = value to filters.value.second }
    fun selectTag(value: String?) { filters.value = filters.value.first to value }

    fun save(
        match: TextMatch,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) = viewModelScope.launch {
        runCatching { sourceRepository.saveMatch(match) }
            .onSuccess { onSuccess() }
            .onFailure { onError(it.message ?: "Could not save match") }
    }

    fun showTutorial() = viewModelScope.launch {
        onboardingStore.activate()
    }
    fun setTutorialStep(step: Int) = viewModelScope.launch { onboardingStore.setStep(step) }
    fun finishWorkspaceSetup() = viewModelScope.launch {
        sourceRepository.ensureBaseFile()
        onboardingStore.finishWorkspaceSetup()
    }
    fun completeTutorial() = viewModelScope.launch {
        onboardingStore.complete()
    }
    fun skipTutorial() = viewModelScope.launch {
        onboardingStore.skip()
    }

    fun installExampleSnippets(onResult: (Result<Int>) -> Unit = {}) = viewModelScope.launch {
        val result = runCatching { sourceRepository.installExampleSnippets().matches }
        onResult(result)
    }

    fun delete(id: Long) = viewModelScope.launch {
        repository.matches.value.firstOrNull { it.id == id }?.let { sourceRepository.deleteMatch(it) }
    }
    fun delete(ids: Set<Long>) = viewModelScope.launch {
        repository.matches.value.filter { it.id in ids }
            .sortedByDescending { it.sourceMatchIndex ?: -1 }
            .forEach { sourceRepository.deleteMatch(it) }
    }
    fun setEnabled(ids: Set<Long>, enabled: Boolean) = viewModelScope.launch {
        ids.forEach { repository.setEnabled(it, enabled) }
    }

    fun duplicate(ids: Set<Long>) = viewModelScope.launch {
        val selected = repository.matches.value.filter { it.id in ids }
        val used = repository.matches.value.mapTo(mutableSetOf()) { it.trigger.lowercase() }
        selected.forEach { match ->
            val trigger = uniqueTrigger(match.trigger, used)
            sourceRepository.saveMatch(
                match.copy(
                    id = 0,
                    triggers = match.triggers.mapIndexed { index, item ->
                        if (index == 0) item.copy(pattern = trigger) else item
                    },
                    usageCount = 0,
                    templateIndex = 0,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    sourceMatchIndex = null,
                ),
            )
            used += trigger.lowercase()
        }
    }

    fun setSnippetEnabled(id: Long, enabled: Boolean) = viewModelScope.launch { repository.setEnabled(id, enabled) }
    fun setExpansionEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setEnabled(enabled) }
    fun acceptConsent() = viewModelScope.launch { settingsRepository.acceptConsent() }
    fun acknowledgeBackgroundSetup() = viewModelScope.launch { settingsRepository.acknowledgeBackgroundSetup() }
    fun setTheme(theme: ThemeMode) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setColorScheme(mode: ColorSchemeMode) = viewModelScope.launch { settingsRepository.setColorScheme(mode) }
    fun setCustomColor(color: Int) = viewModelScope.launch { settingsRepository.setCustomColor(color) }
    fun setTextScale(scale: Float) = viewModelScope.launch { settingsRepository.setTextScale(scale) }
    fun setSnippetSort(mode: SnippetSortMode) = viewModelScope.launch { settingsRepository.setSnippetSort(mode) }
    fun setClipboardHistoryEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setClipboardHistoryEnabled(enabled) }
    fun setStatisticsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setStatisticsEnabled(enabled) }
    fun setHapticFeedback(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHapticFeedback(enabled) }
    fun setPasteFallbackEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setPasteFallbackEnabled(enabled) }
    fun setSuggestionEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSuggestionEnabled(enabled) }
    fun setSuggestionShowActions(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSuggestionShowActions(enabled) }
    fun setMatchFromBeginning(enabled: Boolean) = viewModelScope.launch { settingsRepository.setMatchFromBeginning(enabled) }
    fun setSuggestionCompactList(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSuggestionCompactList(enabled) }
    fun setSuggestionMaxHeightDp(heightDp: Int) = viewModelScope.launch { settingsRepository.setSuggestionMaxHeightDp(heightDp) }
    fun setSuggestionWidthFraction(fraction: Float) = viewModelScope.launch {
        settingsRepository.setSuggestionWidthFraction(fraction)
    }
    fun setSuggestionResizeHandleEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSuggestionResizeHandleEnabled(enabled)
    }
    fun setGloballyExcludedPackages(packages: Set<String>) = viewModelScope.launch {
        settingsRepository.setExcludedPackages(packages)
    }
    fun setSuggestionMinChars(minChars: Int) = viewModelScope.launch { settingsRepository.setSuggestionMinChars(minChars) }
    fun setGlobalVariables(variables: List<TemplateVariable>) = viewModelScope.launch {
        sourceRepository.saveGlobalVariables(variables)
    }
    fun setActionEnabled(id: String, enabled: Boolean) = actionSettingsStore.setEnabled(id, enabled)
    fun setAllActionsEnabled(enabled: Boolean) = actionSettingsStore.setAllEnabled(enabled)
    fun setActionShortcut(id: String, shortcut: String) = actionSettingsStore.setShortcut(id, shortcut)
    fun resetActionShortcut(id: String) = actionSettingsStore.resetShortcut(id)
    fun pauseFor(durationMillis: Long) = viewModelScope.launch { settingsRepository.pauseFor(durationMillis) }
    fun resume() = viewModelScope.launch { settingsRepository.resume() }
    fun captureClipboard(text: String) = viewModelScope.launch { clipboardRepository.add(text) }
    fun deleteClipboard(id: Long) = viewModelScope.launch { clipboardRepository.delete(id) }
    fun clearClipboard() = viewModelScope.launch { clipboardRepository.clear() }
    fun setClipboardPinned(id: Long, pinned: Boolean) = viewModelScope.launch {
        clipboardRepository.setPinned(id, pinned)
    }

    fun updateTags(ids: Set<Long>, add: Set<String>, remove: Set<String>) = viewModelScope.launch {
        repository.matches.value.filter { it.id in ids }.forEach { match ->
            val removed = match.tags.filterNot { current -> remove.any { it.equals(current, ignoreCase = true) } }
            repository.save(match.copy(tags = (removed + add).map(String::trim).filter(String::isNotBlank).toSet()))
        }
    }

    fun clearTags(ids: Set<Long>) = viewModelScope.launch {
        repository.matches.value.filter { it.id in ids }.forEach { match ->
            if (match.tags.isNotEmpty()) repository.save(match.copy(tags = emptySet()))
        }
    }

    fun stats(): DashboardStats = repository.stats()
    fun exportBackup(): String = BackupCodec.encode(
        repository.matches.value,
        settingsRepository.settings.value,
        BackupCodec.ActionSnapshot(
            enabledIds = actionSettingsStore.enabledIds.value,
            shortcutOverrides = actionSettingsStore.shortcutOverrides.value,
        ),
        sourceRepository.files.value,
    )
    fun exportCsv(): String = CsvCodec.encode(repository.matches.value)
    fun exportEspanso() = sourceRepository.exportAll()

    fun applySnippetSource(
        document: EspansoSourceFile,
        onResult: (Result<Int>) -> Unit,
    ) = viewModelScope.launch {
        val result = runCatching {
            sourceRepository.replaceSource(document).matches
        }
        onResult(result)
    }

    fun prepareImport(data: String, sourceName: String = "import.yml"): Result<PreparedImport> = runCatching {
        val normalized = data.removePrefix("\uFEFF")
        val trimmed = normalized.trimStart()
        when {
            trimmed.startsWith("{") -> {
                val decoded = BackupCodec.decodeWithGlobals(normalized)
                PreparedImport(
                    kind = if (decoded.isFullBackup) ImportKind.FULL_BACKUP else ImportKind.SNIPPET_BACKUP,
                    matches = decoded.matches,
                    globalVariables = decoded.globalVariables,
                    settings = decoded.settings,
                    actions = decoded.actions,
                    sourceFiles = decoded.sourceFiles,
                )
            }
            Regex("(?m)^\\s*(matches|global_vars|imports)\\s*:").containsMatchIn(normalized) -> {
                val yamlName = sourceName.takeIf {
                    it.endsWith(".yml", true) || it.endsWith(".yaml", true)
                } ?: "import.yml"
                val decoded = EspansoYamlCodec.decode(normalized.removePrefix("\uFEFF"), yamlName)
                PreparedImport(
                    kind = ImportKind.ESPANSO,
                    matches = decoded.matches,
                    globalVariables = decoded.globalVariables,
                    issues = decoded.issues,
                    sourceFiles = listOf(EspansoSourceFile(yamlName, data)),
                )
            }
            else -> PreparedImport(ImportKind.CSV, CsvCodec.decode(normalized))
        }
    }

    suspend fun prepareEspansoFolder(uri: Uri): Result<PreparedImport> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFiles = EspansoFolderAccess.read(getApplication(), uri)
            val decoded = sourceRepository.inspectFiles(sourceFiles)
            PreparedImport(
                kind = ImportKind.ESPANSO_FOLDER,
                matches = decoded.matches,
                globalVariables = decoded.globalVariables,
                issues = decoded.issues,
                sourceFiles = sourceFiles,
                linkedFolderUri = uri.toString(),
            )
        }
    }

    fun applyImport(prepared: PreparedImport, onResult: (Result<ImportSummary>) -> Unit) = viewModelScope.launch {
        val result = runCatching {
            if (prepared.replacesExistingData) {
                if (prepared.sourceFiles.isEmpty()) sourceRepository.reset()
                else sourceRepository.restoreFiles(prepared.sourceFiles)
                repository.replace(prepared.matches)
                val restoredSettings = requireNotNull(prepared.settings).copy(
                    globalVariables = prepared.globalVariables,
                )
                settingsRepository.restore(restoredSettings)
                actionSettingsStore.restore(requireNotNull(prepared.actions))
                if (prepared.sourceFiles.isEmpty()) sourceRepository.initialize()
                else sourceRepository.reconcileSources()
                ImportSummary(prepared.matches.size, prepared.issues)
            } else if (prepared.kind == ImportKind.ESPANSO_FOLDER) {
                sourceRepository.linkFolder(
                    Uri.parse(requireNotNull(prepared.linkedFolderUri)),
                    prepared.sourceFiles,
                )
                ImportSummary(prepared.matches.size, prepared.issues)
            } else if (prepared.kind == ImportKind.ESPANSO && prepared.sourceFiles.isNotEmpty()) {
                val imported = sourceRepository.importFiles(prepared.sourceFiles, replaceAll = false)
                ImportSummary(imported.matches, imported.issues)
            } else {
                if (prepared.globalVariables.isNotEmpty()) {
                    settingsRepository.setGlobalVariables(prepared.globalVariables)
                }
                ImportSummary(repository.import(prepared.matches), prepared.issues)
            }
        }
        onResult(result)
    }

    fun syncEspansoFolder(onResult: (Result<ImportSummary>) -> Unit) = viewModelScope.launch {
        onResult(runCatching {
            sourceRepository.syncLinkedFolder().let { ImportSummary(it.matches, it.issues) }
        })
    }

    fun refreshEspansoFolderSilently() = viewModelScope.launch {
        if (settingsRepository.settings.value.espansoFolderUri != null) {
            runCatching { sourceRepository.syncLinkedFolder() }
        }
    }

    fun unlinkEspansoFolder() = viewModelScope.launch { sourceRepository.unlinkFolder() }

    fun resetStatistics() = viewModelScope.launch { repository.resetStatistics() }

    fun resetAllData(onComplete: () -> Unit = {}) = viewModelScope.launch {
        repository.replace(emptyList())
        sourceRepository.reset()
        clipboardRepository.clear()
        settingsRepository.reset()
        actionSettingsStore.reset()
        onboardingStore.restart()
        onComplete()
    }

    fun diagnostics(serviceEnabled: Boolean, backgroundAllowed: Boolean): String = buildString {
        appendLine("Expanda diagnostics")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Accessibility service: ${if (serviceEnabled) "enabled" else "disabled"}")
        appendLine("Background access: ${if (backgroundAllowed) "allowed" else "not allowed"}")
        appendLine("Matches: ${repository.matches.value.size}")
        appendLine("Global variables: ${settingsRepository.settings.value.globalVariables.size}")
        appendLine("Enabled actions: ${actionSettingsStore.enabledIds.value.size}")
        appendLine("Suggestion overlay: ${if (settingsRepository.settings.value.suggestionEnabled) "enabled" else "disabled"}")
        append("No snippet text, clipboard content or package names included.")
    }

}

private fun uniqueTrigger(base: String, used: Set<String>): String {
    val normalized = used.map(String::lowercase).toHashSet()
    var candidate = "$base-copy"
    var number = 2
    while (candidate.lowercase() in normalized) {
        candidate = "$base-copy-$number"
        number++
    }
    return candidate
}

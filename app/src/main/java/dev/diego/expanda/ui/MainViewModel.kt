package dev.diego.expanda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.diego.expanda.ExpandaApplication
import dev.diego.expanda.data.AppSettings
import dev.diego.expanda.data.BackupCodec
import dev.diego.expanda.data.DashboardStats
import dev.diego.expanda.data.ClipboardEntry
import dev.diego.expanda.data.CsvCodec
import dev.diego.expanda.data.Snippet
import dev.diego.expanda.data.ThemeMode
import dev.diego.expanda.data.ColorSchemeMode
import dev.diego.expanda.data.TextSizeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val snippets: List<Snippet> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val clipboardEntries: List<ClipboardEntry> = emptyList(),
    val enabledActionIds: Set<String> = emptySet(),
    val actionShortcutOverrides: Map<String, String> = emptyMap(),
    val search: String = "",
    val selectedTag: String? = null,
) {
    val tags: List<String> get() = snippets
        .flatMap { it.tags }
        .distinctBy(String::lowercase)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
    val visibleSnippets: List<Snippet> get() = snippets.filter { snippet ->
        (selectedTag == null || snippet.tags.any { it.equals(selectedTag, ignoreCase = true) }) &&
            (search.isBlank() || (listOf(snippet.shortcut, snippet.label, snippet.content) + snippet.tags)
                .any { it.contains(search, ignoreCase = true) })
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ExpandaApplication
    private val repository = app.repository
    private val settingsRepository = app.settingsRepository
    private val clipboardRepository = app.clipboardRepository
    private val actionSettingsStore = app.actionSettingsStore
    private val filters = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String?>>("" to null)

    private val actionSettings = combine(
        actionSettingsStore.enabledIds,
        actionSettingsStore.shortcutOverrides,
    ) { enabledIds, shortcutOverrides -> enabledIds to shortcutOverrides }

    val uiState: StateFlow<MainUiState> = combine(
        repository.snippets,
        settingsRepository.settings,
        clipboardRepository.entries,
        actionSettings,
        filters,
    ) { snippets, settings, clipboard, actions, (search, tag) ->
        MainUiState(snippets, settings, clipboard, actions.first, actions.second, search, tag)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun setSearch(value: String) { filters.value = value to filters.value.second }
    fun selectTag(value: String?) { filters.value = filters.value.first to value }
    fun save(
        snippet: Snippet,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) = viewModelScope.launch {
        runCatching { repository.save(snippet) }
            .onSuccess { onSuccess() }
            .onFailure { onError(it.message ?: "Could not save snippet") }
    }
    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }

    fun delete(ids: Set<Long>) = viewModelScope.launch {
        ids.forEach { repository.delete(it) }
    }

    fun setEnabled(ids: Set<Long>, enabled: Boolean) = viewModelScope.launch {
        ids.forEach { repository.setEnabled(it, enabled) }
    }

    fun duplicate(ids: Set<Long>) = viewModelScope.launch {
        val selected = repository.snippets.value.filter { it.id in ids }
        val usedShortcuts = repository.snippets.value.mapTo(mutableSetOf()) { it.shortcut.lowercase() }
        selected.forEach { snippet ->
            val shortcut = uniqueShortcut(snippet.shortcut, usedShortcuts)
            repository.save(
                snippet.copy(
                    id = 0L,
                    shortcut = shortcut,
                    usageCount = 0L,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    templateIndex = 0L,
                ),
            )
            usedShortcuts += shortcut.lowercase()
        }
    }
    fun setSnippetEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(id, enabled)
    }
    fun setExpansionEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setEnabled(enabled) }
    fun acceptConsent() = viewModelScope.launch { settingsRepository.acceptConsent() }
    fun acknowledgeBackgroundSetup() = viewModelScope.launch { settingsRepository.acknowledgeBackgroundSetup() }
    fun setTheme(theme: ThemeMode) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setColorScheme(mode: ColorSchemeMode) = viewModelScope.launch { settingsRepository.setColorScheme(mode) }
    fun setCustomColor(color: Int) = viewModelScope.launch { settingsRepository.setCustomColor(color) }
    fun setTextSize(mode: TextSizeMode) = viewModelScope.launch { settingsRepository.setTextSize(mode) }
    fun setClipboardHistoryEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setClipboardHistoryEnabled(enabled) }
    fun setStatisticsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setStatisticsEnabled(enabled) }
    fun setHapticFeedback(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHapticFeedback(enabled) }
    fun setPasteFallbackEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setPasteFallbackEnabled(enabled) }
    fun setSuggestionEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSuggestionEnabled(enabled) }
    fun setSuggestionShowActions(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSuggestionShowActions(enabled) }
    fun setMatchFromBeginning(enabled: Boolean) = viewModelScope.launch { settingsRepository.setMatchFromBeginning(enabled) }
    fun setSuggestionCompactList(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSuggestionCompactList(enabled) }
    fun setSuggestionMaxHeightDp(heightDp: Int) = viewModelScope.launch { settingsRepository.setSuggestionMaxHeightDp(heightDp) }
    fun setSuggestionMinChars(minChars: Int) = viewModelScope.launch { settingsRepository.setSuggestionMinChars(minChars) }
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
    fun stats(): DashboardStats = repository.stats()
    fun exportBackup(): String = BackupCodec.encode(repository.snippets.value)
    fun exportCsv(): String = CsvCodec.encode(repository.snippets.value)
    fun importData(data: String, onResult: (Result<Int>) -> Unit) = viewModelScope.launch {
        val result = runCatching {
            val incoming = if (data.trimStart().startsWith("{")) BackupCodec.decode(data) else CsvCodec.decode(data)
            incoming.forEach { imported ->
                val existingId = repository.snippets.value.firstOrNull {
                    it.shortcut.equals(imported.shortcut, ignoreCase = true)
                }?.id ?: 0L
                repository.save(imported.copy(id = existingId))
            }
            incoming.size
        }
        onResult(result)
    }
}

private fun uniqueShortcut(base: String, used: Set<String>): String {
    val normalized = used.map(String::lowercase).toHashSet()
    var candidate = "$base-copy"
    var number = 2
    while (candidate.lowercase() in normalized) {
        candidate = "$base-copy-$number"
        number++
    }
    return candidate
}

package dev.diego.expanda.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class ColorSchemeMode { WALLPAPER, DEFAULT, CUSTOM }
data class AppSettings(
    val expansionEnabled: Boolean = true,
    val consentAccepted: Boolean = false,
    /** User dismissed sideload restricted-settings guidance after trying Accessibility. */
    val restrictedSettingsHintDismissed: Boolean = false,
    val backgroundSetupAcknowledged: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSchemeMode: ColorSchemeMode = ColorSchemeMode.DEFAULT,
    val customColor: Int = 0xFF6750A4.toInt(),
    val textScale: Float = SettingsRepository.DEFAULT_TEXT_SCALE,
    val snippetSortMode: SnippetSortMode = SnippetSortMode.RECENTLY_EDITED,
    val pausedUntil: Long = 0L,
    val globallyExcludedPackages: Set<String> = emptySet(),
    val globalVariables: List<TemplateVariable> = emptyList(),
    val clipboardHistoryEnabled: Boolean = true,
    val statisticsEnabled: Boolean = true,
    val hapticFeedback: Boolean = false,
    val pasteFallbackEnabled: Boolean = false,
    val suggestionEnabled: Boolean = true,
    val suggestionShowActions: Boolean = true,
    val matchFromBeginning: Boolean = true,
    /** Keep the suggestion list visually dense when enabled. */
    val suggestionCompactList: Boolean = true,
    /** Maximum height of the scrollable suggestion list, in dp. */
    val suggestionMaxHeightDp: Int = SettingsRepository.DEFAULT_SUGGESTION_HEIGHT_DP,
    /** Fraction of the usable display width occupied by the suggestion popup. */
    val suggestionWidthFraction: Float = SettingsRepository.DEFAULT_SUGGESTION_WIDTH,
    /** Show a bottom-left handle that resizes the popup horizontally and vertically. */
    val suggestionResizeHandleEnabled: Boolean = true,
    /** Number of characters in the current token required before suggestions appear. */
    val suggestionMinChars: Int = 2,
    /** Last drag position of the suggestion window, in physical pixels. -1 means default. */
    val suggestionPositionX: Int = -1,
    val suggestionPositionY: Int = -1,
    /** Bottom edge of the suggestion window, in physical pixels. Keeps the drag handle anchored when its height changes. */
    val suggestionPositionBottom: Int = -1,
    /** Persisted SAF tree grant. Device-local and intentionally excluded from backups. */
    val espansoFolderUri: String? = null,
) {
    val isPaused: Boolean get() = pausedUntil > System.currentTimeMillis()
}

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.settingsDataStore
    private val loaded = MutableStateFlow(false)

    val settings: StateFlow<AppSettings> = store.data.map { values ->
        AppSettings(
            expansionEnabled = values[Keys.ENABLED] ?: true,
            consentAccepted = values[Keys.CONSENT] ?: false,
            restrictedSettingsHintDismissed = values[Keys.RESTRICTED_SETTINGS_HINT_DISMISSED] ?: false,
            backgroundSetupAcknowledged = values[Keys.BACKGROUND_SETUP_ACKNOWLEDGED] ?: false,
            themeMode = values[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            colorSchemeMode = values[Keys.COLOR_SCHEME]?.let { runCatching { ColorSchemeMode.valueOf(it) }.getOrNull() }
                ?: ColorSchemeMode.DEFAULT,
            customColor = values[Keys.CUSTOM_COLOR] ?: 0xFF6750A4.toInt(),
            textScale = (values[Keys.TEXT_SCALE] ?: legacyTextScale(values[Keys.TEXT_SIZE]))
                .coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
            snippetSortMode = values[Keys.SNIPPET_SORT]?.let {
                runCatching { SnippetSortMode.valueOf(it) }.getOrNull()
            } ?: SnippetSortMode.RECENTLY_EDITED,
            pausedUntil = values[Keys.PAUSED_UNTIL] ?: 0L,
            globallyExcludedPackages = values[Keys.EXCLUDED]
                ?.split(SEPARATOR)?.filter(String::isNotBlank)?.toSet().orEmpty(),
            globalVariables = TemplateVariableJsonCodec.decode(values[Keys.GLOBAL_VARIABLES]),
            clipboardHistoryEnabled = values[Keys.CLIPBOARD_HISTORY] ?: true,
            statisticsEnabled = values[Keys.STATISTICS] ?: true,
            hapticFeedback = values[Keys.HAPTIC] ?: false,
            pasteFallbackEnabled = values[Keys.PASTE_FALLBACK] ?: false,
            suggestionEnabled = values[Keys.SUGGESTIONS] ?: true,
            suggestionShowActions = values[Keys.SUGGESTION_SHOW_ACTIONS] ?: true,
            matchFromBeginning = values[Keys.MATCH_BEGINNING] ?: true,
            suggestionCompactList = values[Keys.SUGGESTION_COMPACT] ?: true,
            suggestionMaxHeightDp = (
                values[Keys.SUGGESTION_MAX_HEIGHT_DP] ?: DEFAULT_SUGGESTION_HEIGHT_DP
            ).coerceIn(MIN_SUGGESTION_HEIGHT_DP, MAX_SUGGESTION_HEIGHT_DP),
            suggestionWidthFraction = (values[Keys.SUGGESTION_WIDTH] ?: DEFAULT_SUGGESTION_WIDTH)
                .coerceIn(MIN_SUGGESTION_WIDTH, MAX_SUGGESTION_WIDTH),
            suggestionResizeHandleEnabled = values[Keys.SUGGESTION_RESIZE_HANDLE] ?: true,
            suggestionMinChars = (values[Keys.SUGGESTION_MIN_CHARS] ?: 2).coerceIn(1, 32),
            suggestionPositionX = values[Keys.SUGGESTION_POSITION_X] ?: -1,
            suggestionPositionY = values[Keys.SUGGESTION_POSITION_Y] ?: -1,
            suggestionPositionBottom = values[Keys.SUGGESTION_POSITION_BOTTOM] ?: -1,
            espansoFolderUri = values[Keys.ESPANSO_FOLDER_URI]?.takeIf(String::isNotBlank),
        )
    }.onEach { loaded.value = true }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun awaitLoaded() {
        loaded.filter { it }.first()
    }

    suspend fun setEnabled(enabled: Boolean) = store.edit { it[Keys.ENABLED] = enabled }
    suspend fun acceptConsent() = store.edit { it[Keys.CONSENT] = true }
    suspend fun dismissRestrictedSettingsHint() = store.edit {
        it[Keys.RESTRICTED_SETTINGS_HINT_DISMISSED] = true
    }
    suspend fun acknowledgeBackgroundSetup() = store.edit { it[Keys.BACKGROUND_SETUP_ACKNOWLEDGED] = true }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
    suspend fun setColorScheme(mode: ColorSchemeMode) = store.edit { it[Keys.COLOR_SCHEME] = mode.name }
    suspend fun setCustomColor(color: Int) = store.edit { it[Keys.CUSTOM_COLOR] = color }
    suspend fun setTextScale(scale: Float) = store.edit {
        it[Keys.TEXT_SCALE] = scale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        it.remove(Keys.TEXT_SIZE)
    }
    suspend fun setSnippetSort(mode: SnippetSortMode) = store.edit { it[Keys.SNIPPET_SORT] = mode.name }
    suspend fun pauseFor(durationMillis: Long) = store.edit {
        it[Keys.PAUSED_UNTIL] = System.currentTimeMillis() + durationMillis
    }
    suspend fun resume() = store.edit { it[Keys.PAUSED_UNTIL] = 0L }
    suspend fun setExcludedPackages(packages: Set<String>) = store.edit {
        it[Keys.EXCLUDED] = packages.sorted().joinToString(SEPARATOR)
    }
    suspend fun setGlobalVariables(variables: List<TemplateVariable>) = store.edit {
        it[Keys.GLOBAL_VARIABLES] = TemplateVariableJsonCodec.encode(variables)
    }
    suspend fun setClipboardHistoryEnabled(enabled: Boolean) = store.edit { it[Keys.CLIPBOARD_HISTORY] = enabled }
    suspend fun setStatisticsEnabled(enabled: Boolean) = store.edit { it[Keys.STATISTICS] = enabled }
    suspend fun setHapticFeedback(enabled: Boolean) = store.edit { it[Keys.HAPTIC] = enabled }
    suspend fun setPasteFallbackEnabled(enabled: Boolean) = store.edit { it[Keys.PASTE_FALLBACK] = enabled }
    suspend fun setSuggestionEnabled(enabled: Boolean) = store.edit { it[Keys.SUGGESTIONS] = enabled }
    suspend fun setSuggestionShowActions(enabled: Boolean) = store.edit { it[Keys.SUGGESTION_SHOW_ACTIONS] = enabled }
    suspend fun setMatchFromBeginning(enabled: Boolean) = store.edit { it[Keys.MATCH_BEGINNING] = enabled }
    suspend fun setSuggestionCompactList(enabled: Boolean) = store.edit {
        it[Keys.SUGGESTION_COMPACT] = enabled
    }
    suspend fun setSuggestionMaxHeightDp(heightDp: Int) = store.edit {
        it[Keys.SUGGESTION_MAX_HEIGHT_DP] = heightDp.coerceIn(
            MIN_SUGGESTION_HEIGHT_DP,
            MAX_SUGGESTION_HEIGHT_DP,
        )
    }
    suspend fun setSuggestionWidthFraction(fraction: Float) = store.edit {
        it[Keys.SUGGESTION_WIDTH] = fraction.coerceIn(MIN_SUGGESTION_WIDTH, MAX_SUGGESTION_WIDTH)
    }
    suspend fun setSuggestionResizeHandleEnabled(enabled: Boolean) = store.edit {
        it[Keys.SUGGESTION_RESIZE_HANDLE] = enabled
    }
    suspend fun setSuggestionMinChars(minChars: Int) = store.edit {
        it[Keys.SUGGESTION_MIN_CHARS] = minChars.coerceIn(1, 32)
    }
    suspend fun setEspansoFolderUri(uri: String?) = store.edit {
        if (uri.isNullOrBlank()) it.remove(Keys.ESPANSO_FOLDER_URI)
        else it[Keys.ESPANSO_FOLDER_URI] = uri
    }
    suspend fun setSuggestionPosition(x: Int, y: Int, height: Int) = store.edit {
        it[Keys.SUGGESTION_POSITION_X] = x.coerceAtLeast(0)
        it[Keys.SUGGESTION_POSITION_Y] = y.coerceAtLeast(0)
        it[Keys.SUGGESTION_POSITION_BOTTOM] = (y + height).coerceAtLeast(0)
    }
    suspend fun setSuggestionLayout(
        x: Int,
        y: Int,
        height: Int,
        widthFraction: Float,
        maxHeightDp: Int,
    ) = store.edit {
        it[Keys.SUGGESTION_POSITION_X] = x.coerceAtLeast(0)
        it[Keys.SUGGESTION_POSITION_Y] = y.coerceAtLeast(0)
        it[Keys.SUGGESTION_POSITION_BOTTOM] = (y + height).coerceAtLeast(0)
        it[Keys.SUGGESTION_WIDTH] = widthFraction.coerceIn(MIN_SUGGESTION_WIDTH, MAX_SUGGESTION_WIDTH)
        it[Keys.SUGGESTION_MAX_HEIGHT_DP] = maxHeightDp.coerceIn(
            MIN_SUGGESTION_HEIGHT_DP,
            MAX_SUGGESTION_HEIGHT_DP,
        )
    }

    private object Keys {
        val ENABLED = booleanPreferencesKey("expansion_enabled")
        val CONSENT = booleanPreferencesKey("accessibility_consent")
        val RESTRICTED_SETTINGS_HINT_DISMISSED = booleanPreferencesKey("restricted_settings_hint_dismissed")
        val BACKGROUND_SETUP_ACKNOWLEDGED = booleanPreferencesKey("background_setup_acknowledged")
        val THEME = stringPreferencesKey("theme")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val CUSTOM_COLOR = intPreferencesKey("custom_color")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val SNIPPET_SORT = stringPreferencesKey("snippet_sort")
        val PAUSED_UNTIL = longPreferencesKey("paused_until")
        val EXCLUDED = stringPreferencesKey("globally_excluded_packages")
        val GLOBAL_VARIABLES = stringPreferencesKey("global_variables")
        val CLIPBOARD_HISTORY = booleanPreferencesKey("clipboard_history")
        val STATISTICS = booleanPreferencesKey("statistics")
        val HAPTIC = booleanPreferencesKey("haptic_feedback")
        val PASTE_FALLBACK = booleanPreferencesKey("paste_fallback")
        val SUGGESTIONS = booleanPreferencesKey("suggestions")
        val SUGGESTION_SHOW_ACTIONS = booleanPreferencesKey("suggestion_show_actions")
        val MATCH_BEGINNING = booleanPreferencesKey("match_beginning")
        val SUGGESTION_COMPACT = booleanPreferencesKey("suggestion_compact_list")
        val SUGGESTION_MAX_HEIGHT_DP = intPreferencesKey("suggestion_max_height_dp")
        val SUGGESTION_WIDTH = floatPreferencesKey("suggestion_width_fraction")
        val SUGGESTION_RESIZE_HANDLE = booleanPreferencesKey("suggestion_resize_handle")
        val SUGGESTION_MIN_CHARS = intPreferencesKey("suggestion_min_chars")
        val SUGGESTION_POSITION_X = intPreferencesKey("suggestion_position_x")
        val SUGGESTION_POSITION_Y = intPreferencesKey("suggestion_position_y")
        val SUGGESTION_POSITION_BOTTOM = intPreferencesKey("suggestion_position_bottom")
        val ESPANSO_FOLDER_URI = stringPreferencesKey("espanso_folder_uri")
    }

    suspend fun restore(snapshot: AppSettings) = store.edit { values ->
        values[Keys.ENABLED] = snapshot.expansionEnabled
        values[Keys.THEME] = snapshot.themeMode.name
        values[Keys.COLOR_SCHEME] = snapshot.colorSchemeMode.name
        values[Keys.CUSTOM_COLOR] = snapshot.customColor
        values[Keys.TEXT_SCALE] = snapshot.textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        values.remove(Keys.TEXT_SIZE)
        values[Keys.SNIPPET_SORT] = snapshot.snippetSortMode.name
        values[Keys.EXCLUDED] = snapshot.globallyExcludedPackages.sorted().joinToString(SEPARATOR)
        values[Keys.GLOBAL_VARIABLES] = TemplateVariableJsonCodec.encode(snapshot.globalVariables)
        values[Keys.CLIPBOARD_HISTORY] = snapshot.clipboardHistoryEnabled
        values[Keys.STATISTICS] = snapshot.statisticsEnabled
        values[Keys.HAPTIC] = snapshot.hapticFeedback
        values[Keys.PASTE_FALLBACK] = snapshot.pasteFallbackEnabled
        values[Keys.SUGGESTIONS] = snapshot.suggestionEnabled
        values[Keys.SUGGESTION_SHOW_ACTIONS] = snapshot.suggestionShowActions
        values[Keys.MATCH_BEGINNING] = snapshot.matchFromBeginning
        values[Keys.SUGGESTION_COMPACT] = snapshot.suggestionCompactList
        values[Keys.SUGGESTION_MAX_HEIGHT_DP] = snapshot.suggestionMaxHeightDp.coerceIn(
            MIN_SUGGESTION_HEIGHT_DP,
            MAX_SUGGESTION_HEIGHT_DP,
        )
        values[Keys.SUGGESTION_MIN_CHARS] = snapshot.suggestionMinChars.coerceIn(1, 32)
        values[Keys.SUGGESTION_WIDTH] = snapshot.suggestionWidthFraction
            .coerceIn(MIN_SUGGESTION_WIDTH, MAX_SUGGESTION_WIDTH)
        values[Keys.SUGGESTION_RESIZE_HANDLE] = snapshot.suggestionResizeHandleEnabled
        // Consent, pause state, battery setup and physical popup coordinates belong to this device.
    }

    suspend fun reset() = store.edit { it.clear() }

    companion object {
        private const val SEPARATOR = "\u001F"
        const val MIN_TEXT_SCALE = 0.75f
        const val MAX_TEXT_SCALE = 1.50f
        const val DEFAULT_TEXT_SCALE = 1f
        const val MIN_SUGGESTION_WIDTH = 0.50f
        const val MAX_SUGGESTION_WIDTH = 0.98f
        const val DEFAULT_SUGGESTION_WIDTH = 0.92f
        const val MIN_SUGGESTION_HEIGHT_DP = 120
        const val MAX_SUGGESTION_HEIGHT_DP = 720
        const val DEFAULT_SUGGESTION_HEIGHT_DP = 280

        private fun legacyTextScale(mode: String?): Float = when (mode) {
            "SMALL" -> 0.90f
            "LARGE" -> 1.12f
            else -> DEFAULT_TEXT_SCALE
        }
    }
}

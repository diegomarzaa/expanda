package dev.diego.expanda.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class ColorSchemeMode { WALLPAPER, DEFAULT, CUSTOM }
enum class TextSizeMode { SMALL, DEFAULT, LARGE }

data class AppSettings(
    val expansionEnabled: Boolean = true,
    val consentAccepted: Boolean = false,
    val backgroundSetupAcknowledged: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSchemeMode: ColorSchemeMode = ColorSchemeMode.DEFAULT,
    val customColor: Int = 0xFF6750A4.toInt(),
    val textSizeMode: TextSizeMode = TextSizeMode.DEFAULT,
    val pausedUntil: Long = 0L,
    val globallyExcludedPackages: Set<String> = emptySet(),
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
    val suggestionMaxHeightDp: Int = 280,
    /** Number of characters in the current token required before suggestions appear. */
    val suggestionMinChars: Int = 2,
    /** Last drag position of the suggestion window, in physical pixels. -1 means default. */
    val suggestionPositionX: Int = -1,
    val suggestionPositionY: Int = -1,
    /** Bottom edge of the suggestion window, in physical pixels. Keeps the drag handle anchored when its height changes. */
    val suggestionPositionBottom: Int = -1,
) {
    val isPaused: Boolean get() = pausedUntil > System.currentTimeMillis()
}

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.settingsDataStore

    val settings: StateFlow<AppSettings> = store.data.map { values ->
        AppSettings(
            expansionEnabled = values[Keys.ENABLED] ?: true,
            consentAccepted = values[Keys.CONSENT] ?: false,
            backgroundSetupAcknowledged = values[Keys.BACKGROUND_SETUP_ACKNOWLEDGED] ?: false,
            themeMode = values[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            colorSchemeMode = values[Keys.COLOR_SCHEME]?.let { runCatching { ColorSchemeMode.valueOf(it) }.getOrNull() }
                ?: ColorSchemeMode.DEFAULT,
            customColor = values[Keys.CUSTOM_COLOR] ?: 0xFF6750A4.toInt(),
            textSizeMode = values[Keys.TEXT_SIZE]?.let { runCatching { TextSizeMode.valueOf(it) }.getOrNull() }
                ?: TextSizeMode.DEFAULT,
            pausedUntil = values[Keys.PAUSED_UNTIL] ?: 0L,
            globallyExcludedPackages = values[Keys.EXCLUDED]
                ?.split(SEPARATOR)?.filter(String::isNotBlank)?.toSet().orEmpty(),
            clipboardHistoryEnabled = values[Keys.CLIPBOARD_HISTORY] ?: true,
            statisticsEnabled = values[Keys.STATISTICS] ?: true,
            hapticFeedback = values[Keys.HAPTIC] ?: false,
            pasteFallbackEnabled = values[Keys.PASTE_FALLBACK] ?: false,
            suggestionEnabled = values[Keys.SUGGESTIONS] ?: true,
            suggestionShowActions = values[Keys.SUGGESTION_SHOW_ACTIONS] ?: true,
            matchFromBeginning = values[Keys.MATCH_BEGINNING] ?: true,
            suggestionCompactList = values[Keys.SUGGESTION_COMPACT] ?: true,
            suggestionMaxHeightDp = (values[Keys.SUGGESTION_MAX_HEIGHT_DP] ?: 280).coerceIn(120, 720),
            suggestionMinChars = (values[Keys.SUGGESTION_MIN_CHARS] ?: 2).coerceIn(1, 32),
            suggestionPositionX = values[Keys.SUGGESTION_POSITION_X] ?: -1,
            suggestionPositionY = values[Keys.SUGGESTION_POSITION_Y] ?: -1,
            suggestionPositionBottom = values[Keys.SUGGESTION_POSITION_BOTTOM] ?: -1,
        )
    }.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun setEnabled(enabled: Boolean) = store.edit { it[Keys.ENABLED] = enabled }
    suspend fun acceptConsent() = store.edit { it[Keys.CONSENT] = true }
    suspend fun acknowledgeBackgroundSetup() = store.edit { it[Keys.BACKGROUND_SETUP_ACKNOWLEDGED] = true }
    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
    suspend fun setColorScheme(mode: ColorSchemeMode) = store.edit { it[Keys.COLOR_SCHEME] = mode.name }
    suspend fun setCustomColor(color: Int) = store.edit { it[Keys.CUSTOM_COLOR] = color }
    suspend fun setTextSize(mode: TextSizeMode) = store.edit { it[Keys.TEXT_SIZE] = mode.name }
    suspend fun pauseFor(durationMillis: Long) = store.edit {
        it[Keys.PAUSED_UNTIL] = System.currentTimeMillis() + durationMillis
    }
    suspend fun resume() = store.edit { it[Keys.PAUSED_UNTIL] = 0L }
    suspend fun setExcludedPackages(packages: Set<String>) = store.edit {
        it[Keys.EXCLUDED] = packages.sorted().joinToString(SEPARATOR)
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
        it[Keys.SUGGESTION_MAX_HEIGHT_DP] = heightDp.coerceIn(120, 720)
    }
    suspend fun setSuggestionMinChars(minChars: Int) = store.edit {
        it[Keys.SUGGESTION_MIN_CHARS] = minChars.coerceIn(1, 32)
    }
    suspend fun setSuggestionPosition(x: Int, y: Int, height: Int) = store.edit {
        it[Keys.SUGGESTION_POSITION_X] = x.coerceAtLeast(0)
        it[Keys.SUGGESTION_POSITION_Y] = y.coerceAtLeast(0)
        it[Keys.SUGGESTION_POSITION_BOTTOM] = (y + height).coerceAtLeast(0)
    }

    private object Keys {
        val ENABLED = booleanPreferencesKey("expansion_enabled")
        val CONSENT = booleanPreferencesKey("accessibility_consent")
        val BACKGROUND_SETUP_ACKNOWLEDGED = booleanPreferencesKey("background_setup_acknowledged")
        val THEME = stringPreferencesKey("theme")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val CUSTOM_COLOR = intPreferencesKey("custom_color")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val PAUSED_UNTIL = longPreferencesKey("paused_until")
        val EXCLUDED = stringPreferencesKey("globally_excluded_packages")
        val CLIPBOARD_HISTORY = booleanPreferencesKey("clipboard_history")
        val STATISTICS = booleanPreferencesKey("statistics")
        val HAPTIC = booleanPreferencesKey("haptic_feedback")
        val PASTE_FALLBACK = booleanPreferencesKey("paste_fallback")
        val SUGGESTIONS = booleanPreferencesKey("suggestions")
        val SUGGESTION_SHOW_ACTIONS = booleanPreferencesKey("suggestion_show_actions")
        val MATCH_BEGINNING = booleanPreferencesKey("match_beginning")
        val SUGGESTION_COMPACT = booleanPreferencesKey("suggestion_compact_list")
        val SUGGESTION_MAX_HEIGHT_DP = intPreferencesKey("suggestion_max_height_dp")
        val SUGGESTION_MIN_CHARS = intPreferencesKey("suggestion_min_chars")
        val SUGGESTION_POSITION_X = intPreferencesKey("suggestion_position_x")
        val SUGGESTION_POSITION_Y = intPreferencesKey("suggestion_position_y")
        val SUGGESTION_POSITION_BOTTOM = intPreferencesKey("suggestion_position_bottom")
    }

    companion object { private const val SEPARATOR = "\u001F" }
}

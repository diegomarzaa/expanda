package dev.diego.expanda.data

import org.json.JSONArray
import org.json.JSONObject

/** Versioned backup boundary for the canonical [TextMatch] model. */
object BackupCodec {
    const val FORMAT_VERSION = 6

    data class ActionSnapshot(
        val enabledIds: Set<String> = emptySet(),
        val shortcutOverrides: Map<String, String> = emptyMap(),
    )

    data class ImportResult(
        val matches: List<TextMatch>,
        val globalVariables: List<TemplateVariable> = emptyList(),
        val settings: AppSettings? = null,
        val actions: ActionSnapshot? = null,
        val sourceFiles: List<EspansoSourceFile> = emptyList(),
    ) {
        val isFullBackup: Boolean get() = settings != null && actions != null
    }

    fun encode(
        matches: List<TextMatch>,
        settings: AppSettings,
        actions: ActionSnapshot,
        sourceFiles: List<EspansoSourceFile> = emptyList(),
    ): String = JSONObject().apply {
        put("format", "expanda-backup")
        put("version", FORMAT_VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("matches", JSONArray().apply {
            matches.forEach { put(MatchJsonCodec.toJson(it)) }
        })
        put("globalVariables", TemplateVariableJsonCodec.toJsonArray(settings.globalVariables))
        put("settings", settingsToJson(settings))
        put("actions", actionsToJson(actions))
        put("espansoSourceFiles", JSONArray().apply {
            sourceFiles.forEach { file ->
                put(JSONObject().put("path", file.relativePath).put("content", file.content))
            }
        })
    }.toString(2)

    fun decode(json: String): List<TextMatch> = decodeWithGlobals(json).matches

    fun decodeWithGlobals(json: String): ImportResult {
        val root = JSONObject(json)
        require(root.optString("format") == "expanda-backup") { "Not an Expanda backup" }
        val version = root.optInt("version", -1)
        require(version in 1..FORMAT_VERSION) { "Unsupported backup version" }

        val entries = root.optJSONArray(if (version == FORMAT_VERSION) "matches" else "snippets")
            ?: root.optJSONArray(if (version == FORMAT_VERSION) "snippets" else "matches")
            ?: throw IllegalArgumentException("Backup does not contain a matches array")

        val matches = buildList(entries.length()) {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index)
                    ?: throw IllegalArgumentException("Backup match #${index + 1} is not an object")
                add(if (version >= 4 && entry.has("triggers")) {
                    MatchJsonCodec.fromJson(entry)
                } else {
                    decodeLegacy(entry, index)
                })
            }
        }
        return ImportResult(
            matches = matches,
            globalVariables = TemplateVariableJsonCodec.decode(root.optJSONArray("globalVariables")),
            settings = root.optJSONObject("settings")?.let(::settingsFromJson),
            actions = root.optJSONObject("actions")?.let(::actionsFromJson),
            sourceFiles = root.optJSONArray("espansoSourceFiles").sourceFiles(),
        )
    }

    private fun settingsToJson(settings: AppSettings): JSONObject = JSONObject().apply {
        put("expansionEnabled", settings.expansionEnabled)
        put("themeMode", settings.themeMode.name)
        put("colorSchemeMode", settings.colorSchemeMode.name)
        put("customColor", settings.customColor)
        put("textScale", settings.textScale.toDouble())
        put("snippetSortMode", settings.snippetSortMode.name)
        put("globallyExcludedPackages", JSONArray(settings.globallyExcludedPackages.sorted()))
        put("clipboardHistoryEnabled", settings.clipboardHistoryEnabled)
        put("statisticsEnabled", settings.statisticsEnabled)
        put("hapticFeedback", settings.hapticFeedback)
        put("pasteFallbackEnabled", settings.pasteFallbackEnabled)
        put("suggestionEnabled", settings.suggestionEnabled)
        put("suggestionShowActions", settings.suggestionShowActions)
        put("matchFromBeginning", settings.matchFromBeginning)
        put("suggestionCompactList", settings.suggestionCompactList)
        put("suggestionMaxHeightDp", settings.suggestionMaxHeightDp)
        put("suggestionMinChars", settings.suggestionMinChars)
        put("suggestionWidthFraction", settings.suggestionWidthFraction.toDouble())
        put("suggestionResizeHandleEnabled", settings.suggestionResizeHandleEnabled)
    }

    private fun settingsFromJson(json: JSONObject): AppSettings = AppSettings(
        expansionEnabled = json.optBoolean("expansionEnabled", true),
        themeMode = enumOrDefault(json.optString("themeMode"), ThemeMode.SYSTEM),
        colorSchemeMode = enumOrDefault(json.optString("colorSchemeMode"), ColorSchemeMode.DEFAULT),
        customColor = json.optInt("customColor", 0xFF6750A4.toInt()),
        textScale = json.optDouble("textScale", SettingsRepository.DEFAULT_TEXT_SCALE.toDouble()).toFloat()
            .coerceIn(SettingsRepository.MIN_TEXT_SCALE, SettingsRepository.MAX_TEXT_SCALE),
        snippetSortMode = enumOrDefault(json.optString("snippetSortMode"), SnippetSortMode.RECENTLY_EDITED),
        globallyExcludedPackages = json.stringList("globallyExcludedPackages").toSet(),
        clipboardHistoryEnabled = json.optBoolean("clipboardHistoryEnabled", true),
        statisticsEnabled = json.optBoolean("statisticsEnabled", true),
        hapticFeedback = json.optBoolean("hapticFeedback"),
        pasteFallbackEnabled = json.optBoolean("pasteFallbackEnabled"),
        suggestionEnabled = json.optBoolean("suggestionEnabled"),
        suggestionShowActions = json.optBoolean("suggestionShowActions", true),
        matchFromBeginning = json.optBoolean("matchFromBeginning", true),
        suggestionCompactList = json.optBoolean("suggestionCompactList", true),
        suggestionMaxHeightDp = json.optInt(
            "suggestionMaxHeightDp",
            SettingsRepository.DEFAULT_SUGGESTION_HEIGHT_DP,
        ).coerceIn(
            SettingsRepository.MIN_SUGGESTION_HEIGHT_DP,
            SettingsRepository.MAX_SUGGESTION_HEIGHT_DP,
        ),
        suggestionMinChars = json.optInt("suggestionMinChars", 2).coerceIn(1, 32),
        suggestionWidthFraction = json.optDouble(
            "suggestionWidthFraction",
            SettingsRepository.DEFAULT_SUGGESTION_WIDTH.toDouble(),
        ).toFloat().coerceIn(SettingsRepository.MIN_SUGGESTION_WIDTH, SettingsRepository.MAX_SUGGESTION_WIDTH),
        suggestionResizeHandleEnabled = json.optBoolean("suggestionResizeHandleEnabled", true),
    )

    private fun actionsToJson(actions: ActionSnapshot): JSONObject = JSONObject().apply {
        put("enabledIds", JSONArray(actions.enabledIds.sorted()))
        put("shortcutOverrides", JSONObject().apply {
            actions.shortcutOverrides.toSortedMap().forEach { (id, shortcut) -> put(id, shortcut) }
        })
    }

    private fun actionsFromJson(json: JSONObject): ActionSnapshot {
        val shortcuts = json.optJSONObject("shortcutOverrides")
        return ActionSnapshot(
            enabledIds = json.stringList("enabledIds").toSet(),
            shortcutOverrides = buildMap {
                shortcuts?.keys()?.forEach { id ->
                    shortcuts.optString(id).takeIf(String::isNotBlank)?.let { put(id, it) }
                }
            },
        )
    }

    /** One-time adapter for the flat v1-v3 Snippet backup representation. */
    private fun decodeLegacy(json: JSONObject, index: Int): TextMatch {
        val shortcut = json.optString("shortcut").ifBlank { json.optString("trigger") }
        val aliases = json.stringList("aliases")
        val regex = json.optString("regexTrigger").ifBlank { json.optString("regex") }
        val isRegex = json.optString("matchKind", "LITERAL").equals("REGEX", ignoreCase = true) ||
            regex.isNotBlank()
        val triggerPatterns = if (isRegex) {
            listOf(regex.ifBlank { shortcut })
        } else {
            (listOf(shortcut) + aliases).filter(String::isNotBlank).distinct()
        }
        require(triggerPatterns.isNotEmpty() && triggerPatterns.all(String::isNotBlank)) {
            "Legacy backup match #${index + 1} has no trigger"
        }

        val replacements = when {
            json.stringList("replacements").isNotEmpty() -> json.stringList("replacements")
            json.has("content") || json.has("replace") -> buildList {
                add(if (json.has("content")) json.optString("content") else json.optString("replace"))
                addAll(json.stringList("templates"))
            }
            else -> json.stringList("templates")
        }
        require(replacements.isNotEmpty()) { "Legacy backup match #${index + 1} has no replacement" }

        val legacyMode = json.optString("triggerMode")
        val options = json.optJSONObject("options")
        val tags = json.stringList("tags").ifEmpty {
            json.optString("folder").split('\u001F').filter(String::isNotBlank)
        }
        val variables = if (json.optJSONArray("vars") != null) {
            json.variableList("vars")
        } else {
            json.variableList("espansoVariables")
        }

        return TextMatch(
            id = json.optLong("id", 0),
            triggers = triggerPatterns.map {
                MatchTrigger(it, if (isRegex) TriggerKind.REGEX else TriggerKind.TEXT)
            },
            replacements = replacements,
            label = json.optString("label"),
            tags = tags.toSet(),
            searchTerms = json.stringList("searchTerms").toSet(),
            enabled = json.optBoolean("enabled", true),
            options = MatchOptions(
                caseSensitive = options?.optBoolean("caseSensitive") ?: json.optBoolean("caseSensitive"),
                activation = enumOrDefault(
                    options?.optString("activation").ifBlankIfNull { legacyMode },
                    if (legacyMode.equals("INSTANT", ignoreCase = true)) {
                        TriggerActivation.IMMEDIATE
                    } else {
                        TriggerActivation.DELIMITER
                    },
                ),
                delimiters = options?.optString("delimiters").ifBlankIfNull {
                    json.optString("delimiters", MatchOptions().delimiters)
                },
                leftWord = options?.optBoolean("leftWord") ?: json.optBoolean("leftWord"),
                rightWord = options?.optBoolean("rightWord") ?: json.optBoolean("rightWord"),
                propagateCase = options?.optBoolean("propagateCase") ?: json.optBoolean("propagateCase"),
                uppercaseStyle = enumOrDefault(
                    options?.optString("uppercaseStyle").ifBlankIfNull { json.optString("uppercaseStyle") },
                    UppercaseStyle.UPPERCASE,
                ),
            ),
            vars = variables,
            excludedPackages = json.stringList("excludedPackages").toSet(),
            selectionMode = enumOrDefault(
                json.optString("selectionMode"),
                TemplateSelectionMode.FIRST,
            ),
            templateIndex = json.optLong("templateIndex", 0),
            usageCount = json.optLong("usageCount", 0),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun JSONObject.stringList(name: String): List<String> = when (val value = opt(name)) {
        is JSONArray -> buildList(value.length()) {
            for (index in 0 until value.length()) {
                value.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
        is String -> value.split('\u001F').map(String::trim).filter(String::isNotBlank)
        else -> emptyList()
    }

    private fun JSONArray?.sourceFiles(): List<EspansoSourceFile> = if (this == null) {
        emptyList()
    } else {
        buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isNotBlank()) add(EspansoSourceFile(path, item.optString("content")))
            }
        }
    }

    private fun JSONObject.variableList(name: String): List<TemplateVariable> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val variableName = item.optString("name")
                val type = item.optString("type")
                if (variableName.isBlank() || type.isBlank()) continue
                val params = item.opt("params")
                val paramsJson = when (params) {
                    is JSONObject -> params.toString()
                    is String -> params.ifBlank { "{}" }
                    else -> "{}"
                }
                add(
                    TemplateVariable(
                        name = variableName,
                        type = type,
                        paramsJson = paramsJson,
                        dependsOn = item.stringList("dependsOn").ifEmpty {
                            item.stringList("depends_on")
                        },
                    ),
                )
            }
        }
    }

    private fun String?.ifBlankIfNull(fallback: () -> String): String =
        if (this.isNullOrBlank()) fallback() else this

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}

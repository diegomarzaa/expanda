package dev.diego.expanda.data

import org.json.JSONArray
import org.json.JSONObject

/** Canonical persistence codec. Import formats are converted before reaching it. */
object MatchJsonCodec {
    fun encode(match: TextMatch): String = toJson(match).toString()

    fun decode(json: String, id: Long = 0): TextMatch = fromJson(JSONObject(json), id)

    fun toJson(match: TextMatch): JSONObject = JSONObject().apply {
        put("triggers", JSONArray().apply {
            match.triggers.forEach { trigger ->
                put(JSONObject().put("pattern", trigger.pattern).put("kind", trigger.kind.name))
            }
        })
        put("replacements", JSONArray(match.replacements))
        put("label", match.label)
        put("tags", JSONArray(match.tags.sortedWith(String.CASE_INSENSITIVE_ORDER)))
        put("searchTerms", JSONArray(match.searchTerms.sortedWith(String.CASE_INSENSITIVE_ORDER)))
        put("enabled", match.enabled)
        put("options", JSONObject().apply {
            put("caseSensitive", match.options.caseSensitive)
            put("activation", match.options.activation.name)
            put("delimiters", match.options.delimiters)
            put("leftWord", match.options.leftWord)
            put("rightWord", match.options.rightWord)
            put("propagateCase", match.options.propagateCase)
            put("uppercaseStyle", match.options.uppercaseStyle.name)
        })
        put("vars", TemplateVariableJsonCodec.toJsonArray(match.vars))
        put("compatibilityWarnings", JSONArray(match.compatibilityWarnings))
        put("runtimeCompatibility", match.runtimeCompatibility.name)
        put("sourceEditMode", match.sourceEditMode.name)
        put("excludedPackages", JSONArray(match.excludedPackages.sorted()))
        put("selectionMode", match.selectionMode.name)
        put("templateIndex", match.templateIndex)
        put("usageCount", match.usageCount)
        put("createdAt", match.createdAt)
        put("updatedAt", match.updatedAt)
        match.sourceFile?.let { put("sourceFile", it) }
        match.sourceMatchIndex?.let { put("sourceMatchIndex", it) }
    }

    fun fromJson(json: JSONObject, id: Long = 0): TextMatch {
        val options = json.optJSONObject("options") ?: JSONObject()
        return TextMatch(
            id = id,
            triggers = json.optJSONArray("triggers").objects().mapNotNull { item ->
                val pattern = item.optString("pattern")
                if (pattern.isBlank()) null else MatchTrigger(
                    pattern,
                    enumOrDefault(item.optString("kind"), TriggerKind.TEXT),
                )
            },
            replacements = json.optJSONArray("replacements").values(),
            label = json.optString("label"),
            tags = json.optJSONArray("tags").strings().toSet(),
            searchTerms = json.optJSONArray("searchTerms").strings().toSet(),
            enabled = json.optBoolean("enabled", true),
            options = MatchOptions(
                caseSensitive = options.optBoolean("caseSensitive", true),
                activation = enumOrDefault(options.optString("activation"), TriggerActivation.IMMEDIATE),
                delimiters = options.optString("delimiters", " \n\t.,!?;:"),
                leftWord = options.optBoolean("leftWord"),
                rightWord = options.optBoolean("rightWord"),
                propagateCase = options.optBoolean("propagateCase"),
                uppercaseStyle = enumOrDefault(options.optString("uppercaseStyle"), UppercaseStyle.UPPERCASE),
            ),
            vars = TemplateVariableJsonCodec.decode(json.optJSONArray("vars")),
            compatibilityWarnings = json.optJSONArray("compatibilityWarnings").strings(),
            runtimeCompatibility = enumOrDefault(
                json.optString("runtimeCompatibility"),
                RuntimeCompatibility.PORTABLE,
            ),
            sourceEditMode = enumOrDefault(json.optString("sourceEditMode"), SourceEditMode.VISUAL),
            excludedPackages = json.optJSONArray("excludedPackages").strings().toSet(),
            selectionMode = enumOrDefault(json.optString("selectionMode"), TemplateSelectionMode.FIRST),
            templateIndex = json.optLong("templateIndex"),
            usageCount = json.optLong("usageCount"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            sourceFile = json.optString("sourceFile").takeIf(String::isNotBlank),
            sourceMatchIndex = if (json.has("sourceMatchIndex")) json.optInt("sourceMatchIndex") else null,
        ).also {
            require(it.triggers.isNotEmpty()) { "Match needs at least one trigger" }
            require(it.replacements.isNotEmpty()) { "Match needs at least one replacement" }
        }
    }

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else buildList(length()) {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }

    private fun JSONArray?.values(): List<String> = if (this == null) emptyList() else buildList(length()) {
        for (index in 0 until length()) add(optString(index))
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else buildList(length()) {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}

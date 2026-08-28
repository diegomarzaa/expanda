package dev.diego.expanda.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Human-readable tabular import/export for the canonical [TextMatch] model. */
object CsvCodec {
    private const val FIELD_SEPARATOR = "\u001F"

    private val header = listOf(
        "trigger", "replace", "triggerKinds", "label", "tags", "searchTerms",
        "enabled", "caseSensitive", "activation", "delimiters", "leftWord", "rightWord",
        "propagateCase", "uppercaseStyle", "excludedPackages", "vars", "selectionMode",
        "templateIndex", "usageCount", "createdAt", "updatedAt",
    )

    fun encode(matches: List<TextMatch>): String = buildString {
        appendLine(header.joinToString(","))
        matches.forEach { match ->
            val fields = listOf(
                encodeList(match.triggers.map(MatchTrigger::pattern)),
                encodeList(match.replacements),
                encodeList(match.triggers.map { it.kind.name }),
                match.label,
                encodeList(match.tags.sortedWith(String.CASE_INSENSITIVE_ORDER)),
                encodeList(match.searchTerms.sortedWith(String.CASE_INSENSITIVE_ORDER)),
                match.enabled.toString(),
                match.options.caseSensitive.toString(),
                match.options.activation.name,
                match.options.delimiters,
                match.options.leftWord.toString(),
                match.options.rightWord.toString(),
                match.options.propagateCase.toString(),
                match.options.uppercaseStyle.name,
                encodeList(match.excludedPackages.sorted()),
                MatchJsonCodec.toJson(match).optJSONArray("vars")?.toString() ?: "[]",
                match.selectionMode.name,
                match.templateIndex.toString(),
                match.usageCount.toString(),
                match.createdAt.toString(),
                match.updatedAt.toString(),
            )
            appendLine(fields.joinToString(",", transform = ::escape))
        }
    }

    fun decode(csv: String): List<TextMatch> {
        val rows = parseRows(csv)
        require(rows.isNotEmpty()) { "CSV is empty" }
        val indexes = rows.first().mapIndexed { index, value ->
            value.removePrefix("\uFEFF").trim().lowercase(Locale.ROOT) to index
        }.filter { it.first.isNotBlank() }.toMap()

        val triggerColumn = when {
            "trigger" in indexes -> "trigger"
            "shortcut" in indexes -> "shortcut"
            else -> throw IllegalArgumentException("CSV needs a trigger column")
        }
        val replacementColumn = when {
            "replace" in indexes -> "replace"
            "content" in indexes -> "content"
            else -> throw IllegalArgumentException("CSV needs a replace column")
        }
        val legacyColumns = triggerColumn == "shortcut" || replacementColumn == "content"

        fun raw(row: List<String>, name: String): String? =
            indexes[name.lowercase(Locale.ROOT)]?.let { row.getOrNull(it).orEmpty() }

        fun text(row: List<String>, name: String, fallback: String = ""): String =
            raw(row, name)?.takeIf(String::isNotEmpty) ?: fallback

        return rows.drop(1)
            .filter { it.any(String::isNotBlank) }
            .mapIndexed { rowIndex, row ->
                val triggerValue = raw(row, triggerColumn).orEmpty()
                val triggers = when {
                    "triggers" in indexes && raw(row, "triggers").orEmpty().isNotBlank() ->
                        decodeTriggers(raw(row, "triggers").orEmpty())
                    else -> decodeTriggers(triggerValue, raw(row, "triggerKinds"))
                }
                require(triggers.isNotEmpty() && triggers.all { it.pattern.isNotBlank() }) {
                    "CSV row ${rowIndex + 2} needs a non-empty trigger"
                }

                val replacements = when {
                    "replacements" in indexes && raw(row, "replacements").orEmpty().isNotBlank() ->
                        decodeStringList(raw(row, "replacements").orEmpty(), trimValues = false)
                    legacyColumns -> buildList {
                        add(raw(row, replacementColumn).orEmpty())
                        addAll(decodeStringList(raw(row, "templates").orEmpty(), trimValues = false))
                    }
                    else -> decodeStringList(
                        raw(row, replacementColumn).orEmpty(),
                        keepBlank = true,
                        trimValues = false,
                    )
                }
                require(replacements.isNotEmpty()) { "CSV row ${rowIndex + 2} needs a replacement" }

                TextMatch(
                    triggers = triggers,
                    replacements = replacements,
                    label = text(row, "label"),
                    tags = decodeStringList(text(row, "tags").ifBlank { text(row, "folder") }).toSet(),
                    searchTerms = decodeStringList(text(row, "searchTerms")).toSet(),
                    enabled = parseBoolean(text(row, "enabled", "true"), true),
                    options = MatchOptions(
                        caseSensitive = parseBoolean(text(row, "caseSensitive", "false")),
                        activation = parseActivation(text(row, "activation").ifBlank {
                            text(row, "triggerMode", "DELIMITER")
                        }),
                        delimiters = text(row, "delimiters", MatchOptions().delimiters),
                        leftWord = parseBoolean(text(row, "leftWord")),
                        rightWord = parseBoolean(text(row, "rightWord")),
                        propagateCase = parseBoolean(text(row, "propagateCase")),
                        uppercaseStyle = enumOrDefault(
                            text(row, "uppercaseStyle"),
                            UppercaseStyle.UPPERCASE,
                        ),
                    ),
                    vars = decodeVariables(
                        text(row, "vars").ifBlank { text(row, "espansoVariables") },
                    ),
                    excludedPackages = decodeStringList(text(row, "excludedPackages")).toSet(),
                    selectionMode = enumOrDefault(
                        text(row, "selectionMode"),
                        TemplateSelectionMode.FIRST,
                    ),
                    templateIndex = text(row, "templateIndex", "0").toLongOrNull() ?: 0,
                    usageCount = text(row, "usageCount", "0").toLongOrNull() ?: 0,
                    createdAt = text(row, "createdAt", System.currentTimeMillis().toString()).toLongOrNull()
                        ?: System.currentTimeMillis(),
                    updatedAt = text(row, "updatedAt", System.currentTimeMillis().toString()).toLongOrNull()
                        ?: System.currentTimeMillis(),
                )
            }
    }

    private fun decodeTriggers(value: String, kindsValue: String? = null): List<MatchTrigger> {
        if (value.isBlank()) return emptyList()
        val json = runCatching { JSONArray(value) }.getOrNull()
        val patterns = if (json != null) buildList(json.length()) {
            for (index in 0 until json.length()) {
                when (val item = json.opt(index)) {
                    is JSONObject -> item.optString("pattern").takeIf(String::isNotBlank)?.let(::add)
                    is String -> item.takeIf(String::isNotBlank)?.let(::add)
                }
            }
        } else {
            decodeDelimited(value)
        }
        val jsonKinds = if (json != null) buildList(json.length()) {
            for (index in 0 until json.length()) {
                json.optJSONObject(index)?.optString("kind").orEmpty().let(::add)
            }
        } else {
            decodeDelimited(kindsValue.orEmpty())
        }
        return patterns.mapIndexed { index, pattern ->
            MatchTrigger(pattern, enumOrDefault(jsonKinds.getOrNull(index).orEmpty(), TriggerKind.TEXT))
        }
    }

    private fun decodeStringList(
        value: String,
        keepBlank: Boolean = false,
        trimValues: Boolean = true,
    ): List<String> {
        if (value.isBlank() && !keepBlank) return emptyList()
        val json = runCatching { JSONArray(value) }.getOrNull()
        if (json != null) return buildList(json.length()) {
            for (index in 0 until json.length()) {
                val item = json.optString(index)
                if (keepBlank || item.isNotBlank()) add(item)
            }
        }
        if (value.isBlank()) return listOf("")
        return value.split(FIELD_SEPARATOR, ignoreCase = false, limit = Int.MAX_VALUE)
            .map { if (trimValues) it.trim() else it }
            .filter { keepBlank || it.isNotBlank() }
    }

    private fun decodeDelimited(value: String): List<String> =
        value.split(FIELD_SEPARATOR, ignoreCase = false, limit = Int.MAX_VALUE)
            .map(String::trim)
            .filter(String::isNotBlank)

    private fun decodeVariables(value: String): List<TemplateVariable> {
        if (value.isBlank()) return emptyList()
        val array = runCatching { JSONArray(value) }.getOrNull() ?: return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name")
                val type = item.optString("type")
                if (name.isBlank() || type.isBlank()) continue
                val params = item.opt("params")
                add(
                    TemplateVariable(
                        name = name,
                        type = type,
                        paramsJson = when (params) {
                            is JSONObject -> params.toString()
                            is String -> params.ifBlank { "{}" }
                            else -> "{}"
                        },
                        dependsOn = decodeJsonOrDelimitedList(item.opt("dependsOn") ?: item.opt("depends_on")),
                    ),
                )
            }
        }
    }

    private fun decodeJsonOrDelimitedList(value: Any?): List<String> = when (value) {
        is JSONArray -> buildList(value.length()) {
            for (index in 0 until value.length()) value.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
        is String -> decodeStringList(value)
        else -> emptyList()
    }

    private fun parseActivation(value: String): TriggerActivation = when (value.uppercase(Locale.ROOT)) {
        "IMMEDIATE", "INSTANT" -> TriggerActivation.IMMEDIATE
        else -> TriggerActivation.DELIMITER
    }

    private fun parseBoolean(value: String, default: Boolean = false): Boolean = when (value.lowercase(Locale.ROOT)) {
        "true", "1", "yes", "y" -> true
        "false", "0", "no", "n" -> false
        else -> default
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun parseRows(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < csv.length) {
            val char = csv[index]
            when {
                quoted && char == '\\' && csv.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                quoted && char == '"' && csv.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                quoted && char == '"' -> quoted = false
                !quoted && char == '"' && field.isEmpty() -> quoted = true
                !quoted && char == ',' -> {
                    row += field.toString()
                    field.clear()
                }
                !quoted && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && csv.getOrNull(index + 1) == '\n') index++
                    row += field.toString()
                    field.clear()
                    rows += row
                    row = mutableListOf()
                }
                else -> field.append(char)
            }
            index++
        }
        require(!quoted) { "Unclosed quoted CSV field" }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }
        return rows
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    private fun encodeList(values: Iterable<String>): String = values.joinToString(FIELD_SEPARATOR)
}

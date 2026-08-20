package dev.diego.expanda.data

import org.json.JSONArray

object CsvCodec {
    private val header = listOf(
        "shortcut", "content", "label", "tags", "enabled", "caseSensitive", "triggerMode",
        "delimiters", "excludedPackages", "templates", "selectionMode", "templateIndex",
    )

    fun encode(snippets: List<Snippet>): String = buildString {
        appendLine(header.joinToString(","))
        snippets.forEach { snippet ->
            appendLine(
                listOf(
                    snippet.shortcut, snippet.content, snippet.label,
                    snippet.tags.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString("\u001F"),
                    snippet.enabled.toString(), snippet.caseSensitive.toString(), snippet.triggerMode.name,
                    snippet.delimiters,
                    snippet.excludedPackages.sorted().joinToString("\u001F"),
                    JSONArray().apply { snippet.templates.forEach(::put) }.toString(),
                    snippet.selectionMode.name,
                    snippet.templateIndex.toString(),
                ).joinToString(",", transform = ::escape),
            )
        }
    }

    fun decode(csv: String): List<Snippet> {
        val rows = parseRows(csv)
        require(rows.isNotEmpty()) { "CSV is empty" }
        val indexes = rows.first().mapIndexed { index, value -> value.trim() to index }.toMap()
        require("shortcut" in indexes && "content" in indexes) { "CSV needs shortcut and content columns" }
        return rows.drop(1).filter { it.any(String::isNotBlank) }.map { row ->
            fun value(name: String, fallback: String = ""): String =
                indexes[name]?.let { row.getOrNull(it) }.orEmpty().ifEmpty { fallback }
            Snippet(
                shortcut = value("shortcut"),
                content = value("content"),
                label = value("label"),
                tags = value("tags").ifBlank { value("folder") }
                    .split('\u001F')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .toSet(),
                enabled = value("enabled", "true").toBooleanStrictOrNull() ?: true,
                caseSensitive = value("caseSensitive", "false").toBooleanStrictOrNull() ?: false,
                triggerMode = runCatching { TriggerMode.valueOf(value("triggerMode", "DELIMITER")) }
                    .getOrDefault(TriggerMode.DELIMITER),
                delimiters = value("delimiters", " \n\t.,!?;:"),
                excludedPackages = value("excludedPackages")
                    .split('\u001F')
                    .filter(String::isNotBlank)
                    .toSet(),
                templates = decodeTemplates(value("templates")),
                selectionMode = runCatching {
                    TemplateSelectionMode.valueOf(value("selectionMode", "FIRST"))
                }.getOrDefault(TemplateSelectionMode.FIRST),
                templateIndex = value("templateIndex", "0").toLongOrNull() ?: 0,
            )
        }.also { snippets ->
            require(snippets.all { it.shortcut.isNotBlank() && it.content.isNotEmpty() }) {
                "Every CSV row needs a shortcut and content"
            }
        }
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value

    private fun decodeTemplates(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            JSONArray(value).let { array ->
                buildList(array.length()) {
                    for (index in 0 until array.length()) add(array.optString(index))
                }
            }
        }.getOrDefault(emptyList())
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
                quoted && char == '"' && csv.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> { row += field.toString(); field.clear() }
                !quoted && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && csv.getOrNull(index + 1) == '\n') index++
                    row += field.toString(); field.clear()
                    rows += row; row = mutableListOf()
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
}

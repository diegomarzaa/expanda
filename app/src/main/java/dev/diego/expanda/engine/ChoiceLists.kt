package dev.diego.expanda.engine

import org.json.JSONArray
import org.json.JSONObject

/** Parsing and normalization for Espanso `random` / `choice` list parameters. */
object ChoiceLists {
    fun hasChoicesParam(params: JSONObject): Boolean = params.has("choices")

    fun parseAny(raw: Any?): List<String> =
        when (raw) {
            null -> emptyList()
            is JSONArray -> buildList(raw.length()) {
                for (index in 0 until raw.length()) {
                    raw.opt(index)?.let { add(parseElement(it)) }
                }
            }.filter(String::isNotBlank)
            is String -> parseString(raw)
            else -> listOfNotNull(parseElement(raw))
        }

    fun parseEditorLines(text: String): List<String> =
        text.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(::normalizeLine)
            .filter(String::isNotBlank)
            .toList()

    fun toEditorText(raw: Any?): String = parseAny(raw).joinToString("\n")

    private fun parseString(value: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return emptyList()
        parseBracketedList(trimmed)?.let { return it }
        return parseEditorLines(value)
    }

    private fun parseBracketedList(value: String): List<String>? {
        if (!value.startsWith('[') || !value.endsWith(']')) return null
        val inner = value.substring(1, value.length - 1).trim()
        if (inner.isEmpty()) return emptyList()
        return inner.split(',')
            .map { normalizeLine(it.trim()) }
            .filter(String::isNotBlank)
    }

    private fun parseElement(raw: Any): String = when (raw) {
        is JSONObject -> raw.optString("label", raw.optString("id", raw.optString("value"))).trim()
        else -> normalizeLine(raw.toString())
    }

    fun normalizeLine(line: String): String {
        val trimmed = line.trim()
        if (trimmed.length >= 2) {
            if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
                return trimmed.substring(1, trimmed.length - 1)
            }
            if (trimmed.startsWith('\'') && trimmed.endsWith('\'')) {
                return trimmed.substring(1, trimmed.length - 1)
            }
        }
        return trimmed
    }
}

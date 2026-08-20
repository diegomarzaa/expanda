package dev.diego.expanda.data

import org.json.JSONArray
import org.json.JSONObject

object BackupCodec {
    const val FORMAT_VERSION = 3

    fun encode(snippets: List<Snippet>): String = JSONObject().apply {
        put("format", "expanda-backup")
        put("version", FORMAT_VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("snippets", JSONArray().apply { snippets.forEach { put(it.toJson()) } })
    }.toString(2)

    fun decode(json: String): List<Snippet> {
        val root = JSONObject(json)
        require(root.optString("format") == "expanda-backup") { "Not an Expanda backup" }
        require(root.optInt("version") in 1..FORMAT_VERSION) { "Unsupported backup version" }
        val array = root.getJSONArray("snippets")
        return buildList(array.length()) {
            for (index in 0 until array.length()) add(array.getJSONObject(index).toSnippet())
        }
    }

    private fun Snippet.toJson() = JSONObject().apply {
        put("shortcut", shortcut)
        put("content", content)
        put("label", label)
        put("tags", JSONArray(tags.sortedWith(String.CASE_INSENSITIVE_ORDER)))
        put("enabled", enabled)
        put("caseSensitive", caseSensitive)
        put("triggerMode", triggerMode.name)
        put("delimiters", delimiters)
        put("excludedPackages", JSONArray(excludedPackages.sorted()))
        put("usageCount", usageCount)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        // content is retained as the v1-compatible first template. Additional
        // variants are stored separately so old backups/importers keep working.
        put("templates", JSONArray(templates))
        put("selectionMode", selectionMode.name)
        put("templateIndex", templateIndex)
    }

    private fun JSONObject.toSnippet(): Snippet {
        val excluded = optJSONArray("excludedPackages")
        val templatesArray = optJSONArray("templates")
        val tagsArray = optJSONArray("tags")
        return Snippet(
            shortcut = getString("shortcut"),
            content = getString("content"),
            label = optString("label"),
            tags = buildSet {
                if (tagsArray != null) {
                    for (index in 0 until tagsArray.length()) tagsArray.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                } else {
                    optString("folder").trim().takeIf(String::isNotBlank)?.let(::add)
                }
            },
            enabled = optBoolean("enabled", true),
            caseSensitive = optBoolean("caseSensitive", false),
            triggerMode = runCatching { TriggerMode.valueOf(optString("triggerMode")) }
                .getOrDefault(TriggerMode.DELIMITER),
            delimiters = optString("delimiters", " \n\t.,!?;:"),
            excludedPackages = buildSet {
                if (excluded != null) for (index in 0 until excluded.length()) add(excluded.getString(index))
            },
            usageCount = optLong("usageCount"),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
            templates = buildList {
                if (templatesArray != null) {
                    for (index in 0 until templatesArray.length()) {
                        add(templatesArray.optString(index))
                    }
                }
            },
            selectionMode = runCatching {
                TemplateSelectionMode.valueOf(optString("selectionMode", "FIRST"))
            }.getOrDefault(TemplateSelectionMode.FIRST),
            templateIndex = optLong("templateIndex", 0),
        )
    }
}

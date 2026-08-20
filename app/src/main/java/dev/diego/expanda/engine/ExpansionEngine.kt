package dev.diego.expanda.engine

import dev.diego.expanda.data.Snippet
import dev.diego.expanda.data.TriggerMode

data class ExpansionMatch(
    val snippet: Snippet,
    val replaceFrom: Int,
    val replaceTo: Int,
    val trailingDelimiter: String,
)

class ExpansionEngine {
    fun findMatch(
        text: String,
        cursor: Int,
        snippets: List<Snippet>,
        packageName: String,
    ): ExpansionMatch? {
        if (cursor !in 0..text.length) return null
        val beforeCursor = text.substring(0, cursor)

        return snippets.asSequence()
            .filter { it.enabled && it.shortcut.isNotEmpty() }
            .filterNot { packageName in it.excludedPackages }
            .sortedByDescending { it.shortcut.length }
            .mapNotNull { snippet -> matchSnippet(beforeCursor, cursor, snippet) }
            .firstOrNull()
    }

    fun applyMatch(text: String, match: ExpansionMatch, rendered: RenderedTemplate): AppliedExpansion {
        val replacement = rendered.text + match.trailingDelimiter
        val newText = text.replaceRange(match.replaceFrom, match.replaceTo, replacement)
        val afterRendered = match.replaceFrom + rendered.cursorOffset
        val cursor = if (rendered.cursorOffset == rendered.text.length) {
            afterRendered + match.trailingDelimiter.length
        } else {
            afterRendered
        }
        return AppliedExpansion(newText, cursor.coerceIn(0, newText.length))
    }

    private fun matchSnippet(beforeCursor: String, cursor: Int, snippet: Snippet): ExpansionMatch? {
        val delimiter = if (
            snippet.triggerMode == TriggerMode.DELIMITER &&
            beforeCursor.lastOrNull()?.let(snippet.delimiters::contains) == true
        ) {
            beforeCursor.last().toString()
        } else {
            ""
        }
        if (snippet.triggerMode == TriggerMode.DELIMITER && delimiter.isEmpty()) return null

        val candidateEnd = beforeCursor.length - delimiter.length
        val candidateStart = candidateEnd - snippet.shortcut.length
        if (candidateStart < 0) return null
        val candidate = beforeCursor.substring(candidateStart, candidateEnd)
        if (!candidate.equals(snippet.shortcut, ignoreCase = !snippet.caseSensitive)) return null

        return ExpansionMatch(
            snippet = snippet,
            replaceFrom = candidateStart,
            replaceTo = cursor,
            trailingDelimiter = delimiter,
        )
    }
}

data class AppliedExpansion(val text: String, val cursor: Int)

package dev.diego.expanda.engine

import dev.diego.expanda.data.ESPANSO_WORD_PATTERN
import dev.diego.expanda.data.TEMPLATE_VARIABLE_REFERENCE
import dev.diego.expanda.data.isEspansoWord

data class TemplateTokenSpan(
    val start: Int,
    val endExclusive: Int,
    val token: String,
) {
    val range: IntRange get() = start until endExclusive
}

data class TemplateEditResult(
    val text: String,
    val cursor: Int,
    val replacedRange: IntRange? = null,
)

data class TemplateRewriteResult(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/**
 * Token-aware editing helpers for the Compose match editor.
 *
 * Android's BasicTextField reports ordinary character offsets. These helpers
 * turn a template token into one logical unit for insertion and backspace,
 * preventing Espanso references such as `{{clipboard}}` from being left half-written.
 */
object TemplateTokenEditor {
    private val tokenPattern = Regex("""\$\|\$|${TEMPLATE_VARIABLE_REFERENCE.pattern}""")

    fun insert(
        text: String,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
        token: String,
    ): TemplateEditResult {
        val normalized = normalizeToken(token)
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, text.length)
        val result = text.replaceRange(start, end, normalized)
        return TemplateEditResult(result, start + normalized.length, start until end)
    }

    fun deleteBackward(text: String, cursor: Int, selectionStart: Int = cursor): TemplateEditResult {
        val safeCursor = cursor.coerceIn(0, text.length)
        val safeStart = selectionStart.coerceIn(0, safeCursor)
        if (safeStart != safeCursor) {
            val result = text.removeRange(safeStart, safeCursor)
            return TemplateEditResult(result, safeStart, safeStart until safeCursor)
        }
        val token = tokenBefore(text, safeCursor)
        if (token != null) {
            val result = text.removeRange(token.start, token.endExclusive)
            return TemplateEditResult(result, token.start, token.range)
        }
        if (safeCursor == 0) return TemplateEditResult(text, 0)
        val result = text.removeRange(safeCursor - 1, safeCursor)
        return TemplateEditResult(result, safeCursor - 1, (safeCursor - 1) until safeCursor)
    }

    fun deleteForward(text: String, cursor: Int, selectionEnd: Int = cursor): TemplateEditResult {
        val safeCursor = cursor.coerceIn(0, text.length)
        val safeEnd = selectionEnd.coerceIn(safeCursor, text.length)
        if (safeEnd != safeCursor) {
            val result = text.removeRange(safeCursor, safeEnd)
            return TemplateEditResult(result, safeCursor, safeCursor until safeEnd)
        }
        val token = tokenAt(text, safeCursor)
        if (token != null) {
            val result = text.removeRange(token.start, token.endExclusive)
            return TemplateEditResult(result, safeCursor, token.range)
        }
        if (safeCursor == text.length) return TemplateEditResult(text, safeCursor)
        val result = text.removeRange(safeCursor, safeCursor + 1)
        return TemplateEditResult(result, safeCursor, safeCursor..safeCursor)
    }

    fun tokenAt(text: String, offset: Int): TemplateTokenSpan? {
        val safeOffset = offset.coerceIn(0, text.length)
        return tokenPattern.findAll(text).firstOrNull { match ->
            safeOffset in match.range.first..match.range.last && isTemplateToken(match.value)
        }?.let { TemplateTokenSpan(it.range.first, it.range.last + 1, it.value) }
    }

    fun tokenBefore(text: String, offset: Int): TemplateTokenSpan? {
        val safeOffset = offset.coerceIn(0, text.length)
        return tokenPattern.findAll(text).lastOrNull { match ->
            match.range.last + 1 == safeOffset && isTemplateToken(match.value)
        }?.let { TemplateTokenSpan(it.range.first, it.range.last + 1, it.value) }
    }

    /** Returns the owning variable when the caret is on a {{name}} reference. */
    fun variableNameAt(text: String, offset: Int): String? {
        val safeOffset = offset.coerceIn(0, text.length)
        return TEMPLATE_VARIABLE_REFERENCE.findAll(text).firstOrNull { match ->
            safeOffset > match.range.first && safeOffset < match.range.last + 1
        }?.groupValues?.get(2)
    }

    /** Returns a known named regex capture only when the caret is inside it. */
    fun captureReferenceAt(text: String, offset: Int, namedCaptures: Set<String> = emptySet()): TemplateTokenSpan? {
        val safeOffset = offset.coerceIn(0, text.length)
        return TEMPLATE_VARIABLE_REFERENCE.findAll(text).firstOrNull { match ->
            if (safeOffset <= match.range.first || safeOffset >= match.range.last + 1) return@firstOrNull false
            match.groupValues[1] in namedCaptures
        }?.let { TemplateTokenSpan(it.range.first, it.range.last + 1, it.value) }
    }

    /**
     * Renames or removes every reference to one variable while keeping a Compose
     * text selection aligned. Dotted form references keep their field suffix.
     */
    fun rewriteVariableReferences(
        text: String,
        oldName: String,
        newName: String?,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart,
    ): TemplateRewriteResult {
        val pattern = Regex(
            """\{\{\s*${Regex.escape(oldName)}(?:\.($ESPANSO_WORD_PATTERN))?\s*\}\}""",
        )
        val replacements = pattern.findAll(text).map { match ->
            val suffix = match.groupValues[1].takeIf(String::isNotEmpty)?.let { ".$it" }.orEmpty()
            match to newName?.let { "{{$it$suffix}}" }.orEmpty()
        }.toList()
        val rewritten = buildString(text.length) {
            var sourceIndex = 0
            replacements.forEach { (match, replacement) ->
                append(text, sourceIndex, match.range.first)
                append(replacement)
                sourceIndex = match.range.last + 1
            }
            append(text, sourceIndex, text.length)
        }
        return TemplateRewriteResult(
            text = rewritten,
            selectionStart = mapOffset(selectionStart, text.length, replacements).coerceIn(0, rewritten.length),
            selectionEnd = mapOffset(selectionEnd, text.length, replacements).coerceIn(0, rewritten.length),
        )
    }

    fun isTemplateToken(value: String): Boolean =
        value == "$|$" || TEMPLATE_VARIABLE_REFERENCE.matches(value)

    private fun mapOffset(
        offset: Int,
        textLength: Int,
        replacements: List<Pair<MatchResult, String>>,
    ): Int {
        val safeOffset = offset.coerceIn(0, textLength)
        var accumulatedDelta = 0
        replacements.forEach { (match, replacement) ->
            val start = match.range.first
            val end = match.range.last + 1
            if (safeOffset <= start) return safeOffset + accumulatedDelta
            if (safeOffset < end) return start + accumulatedDelta + replacement.length
            accumulatedDelta += replacement.length - (end - start)
        }
        return safeOffset + accumulatedDelta
    }

    private fun normalizeToken(token: String): String {
        if (token == "\n" || token == "$|$") return token
        val trimmed = token.trim()
        if (TEMPLATE_VARIABLE_REFERENCE.matches(trimmed)) return trimmed
        return if (isEspansoWord(trimmed)) "{{$trimmed}}" else token
    }
}

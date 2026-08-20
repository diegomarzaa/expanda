package dev.diego.expanda.engine

import java.util.Locale

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

data class TemplateTokenOption(
    val label: String,
    val token: String,
    val description: String = "",
)

/**
 * Token-aware editing helpers for the Compose snippet editor.
 *
 * Android's BasicTextField reports ordinary character offsets. These helpers
 * turn a template token into one logical unit for insertion and backspace,
 * preventing `{FORM: NAME}` or `{SNIPPET: /sig}` from being left half-written.
 */
object TemplateTokenEditor {
    private val tokenPattern = Regex("""\{\{[^{}]+\}\}|\{[^{}]+\}""")

    val options: List<TemplateTokenOption> = listOf(
        TemplateTokenOption("Cursor", "{CURSOR}", "Place the cursor here"),
        TemplateTokenOption("Clipboard", "{CLIPBOARD}", "Insert the current clipboard text"),
        TemplateTokenOption("Date", "{DATE:yyyy-MM-dd}", "Insert a formatted date"),
        TemplateTokenOption("Time", "{TIME:HH:mm}", "Insert a formatted time"),
        TemplateTokenOption("Form field", "{FORM: NAME}", "Ask for a value when the snippet is used"),
        TemplateTokenOption("Nested snippet", "{SNIPPET: shortcut}", "Insert another snippet"),
        TemplateTokenOption("Enter", "{ENTER}", "Insert a line break"),
        TemplateTokenOption("Send", "{SEND}", "Request a send action after insertion"),
    )

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

    fun isTemplateToken(value: String): Boolean {
        val expression = value.removePrefix("{{").removeSuffix("}}").removePrefix("{").removeSuffix("}")
        val name = expression.substringBefore(':').trim().uppercase(Locale.ROOT)
        return name in setOf(
            "CURSOR", "CLIPBOARD", "DATE", "TIME", "DATETIME", "FUTUREDATE", "PASTDATE",
            "FUTURETIME", "PASTTIME", "UUID", "RANDOM", "SNIPPET", "FORM", "TEXTINPUT",
            "INPUT", "ENTER", "NEWLINE", "SEND", "UPPER", "LOWER", "TITLE",
        )
    }

    private fun normalizeToken(token: String): String {
        val trimmed = token.trim()
        return when {
            trimmed.startsWith("{{") && trimmed.endsWith("}}") -> trimmed
            trimmed.startsWith("{") && trimmed.endsWith("}") -> trimmed
            else -> "{$trimmed}"
        }
    }
}

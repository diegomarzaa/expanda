package dev.diego.expanda.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

enum class ActionCategory { NUMBER, TEXT, SELECTION, DELETION, CURSOR, CLIPBOARD, ANDROID, EXPANDA }

data class ActionDefinition(
    val id: String,
    val shortcut: String,
    val title: String,
    val category: ActionCategory,
    val description: String,
    val enabledByDefault: Boolean = false,
    /** Can run from Android's PROCESS_TEXT menu with only the selected text. */
    val supportsSelectedText: Boolean = false,
)

data class ActionContext(
    val text: String,
    val cursor: Int,
    val selectionStart: Int = cursor,
    val selectionEnd: Int = cursor,
    val clipboard: String = "",
)

sealed interface ActionRequest {
    data class Copy(val text: String) : ActionRequest
    data class Share(val text: String) : ActionRequest
    data object ToggleSuggestions : ActionRequest
    data object OpenNewSnippet : ActionRequest
}

data class ActionOutcome(
    val definition: ActionDefinition,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val request: ActionRequest? = null,
)

/** Executes opt-in typing actions without depending on Android UI classes. */
class ActionEngine {
    /** Runs the same pure transformation used by typing actions on an Android text selection. */
    fun processSelectedText(actionId: String, text: String): String? = when (actionId) {
        "math_replace" -> calculate(text, append = false)
        "math_append" -> calculate(text, append = true)
        "number_space" -> formatNumbers(text, ' ', ',')
        "number_period" -> formatNumbers(text, '.', ',')
        "number_comma" -> formatNumbers(text, ',', '.')
        "remove_diacritics" -> removeDiacritics(text)
        "uppercase" -> text.uppercase(Locale.getDefault())
        "lowercase" -> text.lowercase(Locale.getDefault())
        "sentence_case" -> sentenceCase(text)
        "title_case" -> titleCase(text)
        "space_underscore" -> text.replace(' ', '_')
        "space_dash" -> text.replace(' ', '-')
        "underscore_space" -> text.replace('_', ' ')
        "dash_space" -> text.replace('-', ' ')
        "uuid" -> UUID.randomUUID().toString()
        "trim_spaces" -> text.trim()
        "delete_blank_lines" -> text.lineSequence().filterNot(String::isBlank).joinToString("\n")
        else -> null
    }

    fun execute(
        context: ActionContext,
        enabledActionIds: Set<String> = definitions.mapTo(linkedSetOf()) { it.id },
        shortcutOverrides: Map<String, String> = emptyMap(),
    ): ActionOutcome? {
        if (context.cursor !in 0..context.text.length) return null
        val definition = definitions
            .asSequence()
            .filter { it.id in enabledActionIds }
            .map { definition ->
                shortcutOverrides[definition.id]
                    ?.takeIf(String::isNotBlank)
                    ?.let { definition.copy(shortcut = it) }
                    ?: definition
            }
            .sortedByDescending { it.shortcut.length }
            .firstOrNull { context.text.regionMatches(context.cursor - it.shortcut.length, it.shortcut, 0, it.shortcut.length) }
            ?: return null
        val commandStart = context.cursor - definition.shortcut.length
        if (commandStart < 0) return null
        val withoutCommand = context.text.removeRange(commandStart, context.cursor)
        val baseCursor = commandStart

        fun outcome(
            text: String = withoutCommand,
            start: Int = baseCursor.coerceIn(0, text.length),
            end: Int = start,
            request: ActionRequest? = null,
        ) = ActionOutcome(definition, text, start.coerceIn(0, text.length), end.coerceIn(0, text.length), request)

        fun replaceAll(transform: (String) -> String): ActionOutcome {
            val transformed = transform(withoutCommand)
            // A shortcut can be typed in the middle of an existing field. Keep
            // the caret at the same logical point instead of jumping it to
            // the end after transforming the complete field.
            val transformedPrefix = transform(withoutCommand.substring(0, baseCursor))
            return outcome(transformed, transformedPrefix.length)
        }

        return when (definition.id) {
            "math_replace", "math_append" -> processSelectedText(definition.id, withoutCommand)
                ?.let { outcome(it, it.length) }
            "number_space", "number_period", "number_comma", "remove_diacritics",
            "uppercase", "lowercase", "sentence_case", "title_case",
            "space_underscore", "space_dash", "underscore_space", "dash_space" ->
                replaceAll { processSelectedText(definition.id, it) ?: it }
            "select_all" -> outcome(start = 0, end = withoutCommand.length)
            "select_before" -> outcome(start = 0, end = baseCursor.coerceAtMost(withoutCommand.length))
            "select_after" -> outcome(start = baseCursor.coerceAtMost(withoutCommand.length), end = withoutCommand.length)
            "delete_all" -> outcome("", 0)
            "delete_before" -> {
                val text = withoutCommand.substring(baseCursor.coerceAtMost(withoutCommand.length))
                outcome(text, 0)
            }
            "delete_after" -> {
                val text = withoutCommand.substring(0, baseCursor.coerceAtMost(withoutCommand.length))
                outcome(text, text.length)
            }
            "trim_spaces", "delete_blank_lines" ->
                replaceAll { processSelectedText(definition.id, it) ?: it }
            "uuid" -> processSelectedText(definition.id, withoutCommand)
                ?.let { outcome(it, it.length) }
            "cursor_start" -> outcome(start = 0)
            "cursor_end" -> outcome(start = withoutCommand.length)
            "copy_all" -> outcome(request = ActionRequest.Copy(withoutCommand))
            "copy_before" -> outcome(request = ActionRequest.Copy(withoutCommand.substring(0, baseCursor)))
            "copy_after" -> outcome(request = ActionRequest.Copy(withoutCommand.substring(baseCursor)))
            "paste" , "clipboard_history" -> {
                val text = withoutCommand.replaceRange(baseCursor, baseCursor, context.clipboard)
                outcome(text, baseCursor + context.clipboard.length)
            }
            "paste_numbers" -> {
                val pasted = context.clipboard.filter(Char::isDigit)
                val text = withoutCommand.replaceRange(baseCursor, baseCursor, pasted)
                outcome(text, baseCursor + pasted.length)
            }
            "cut_all" -> outcome("", 0, request = ActionRequest.Copy(withoutCommand))
            "cut_before" -> {
                val copied = withoutCommand.substring(0, baseCursor)
                val text = withoutCommand.substring(baseCursor)
                outcome(text, 0, request = ActionRequest.Copy(copied))
            }
            "cut_after" -> {
                val copied = withoutCommand.substring(baseCursor)
                val text = withoutCommand.substring(0, baseCursor)
                outcome(text, text.length, request = ActionRequest.Copy(copied))
            }
            "share" -> outcome(request = ActionRequest.Share(withoutCommand))
            "toggle_suggestions" -> outcome(request = ActionRequest.ToggleSuggestions)
            "new_snippet" -> outcome(request = ActionRequest.OpenNewSnippet)
            else -> null
        }
    }

    private fun calculate(text: String, append: Boolean): String? {
        val match = MATH_AT_END.find(text) ?: return null
        val expression = match.value.trim()
        val value = MathEvaluator.evaluate(expression).getOrNull() ?: return null
        val formatted = formatResult(value)
        val leadingWhitespace = match.value.takeWhile(Char::isWhitespace)
        return if (append) "$text = $formatted"
        else text.replaceRange(match.range, leadingWhitespace + formatted)
    }

    private fun formatNumbers(source: String, grouping: Char, decimal: Char): String =
        NUMBER.replace(source) { match ->
            parseFlexibleNumber(match.value)?.let { number ->
                val plain = number.stripTrailingZeros().toPlainString()
                val parts = plain.split('.', limit = 2)
                val sign = if (parts[0].startsWith('-')) "-" else ""
                val digits = parts[0].removePrefix("-")
                val grouped = digits.reversed().chunked(3).joinToString(grouping.toString()).reversed()
                sign + grouped + parts.getOrNull(1)?.let { "$decimal$it" }.orEmpty()
            } ?: match.value
        }

    private fun parseFlexibleNumber(raw: String): BigDecimal? = runCatching {
        val lastComma = raw.lastIndexOf(',')
        val lastDot = raw.lastIndexOf('.')
        val decimalIndex = maxOf(lastComma, lastDot).takeIf { it >= 0 }
        val normalized = buildString {
            raw.forEachIndexed { index, char ->
                when {
                    char.isDigit() || (char == '-' && index == 0) -> append(char)
                    index == decimalIndex -> append('.')
                }
            }
        }
        BigDecimal(normalized).setScale(12, RoundingMode.HALF_UP).stripTrailingZeros()
    }.getOrNull()

    companion object {
        val definitions: List<ActionDefinition> = listOf(
            ActionDefinition("math_append", ",==", "Append calculation result", ActionCategory.NUMBER, "Keep the expression and append its result", supportsSelectedText = true),
            ActionDefinition("math_replace", "==", "Calculate expression", ActionCategory.NUMBER, "Replace the last math expression with its result", supportsSelectedText = true),
            ActionDefinition("number_space", ",nfs", "Space thousands", ActionCategory.NUMBER, "12 345,67", supportsSelectedText = true),
            ActionDefinition("number_period", ",nfp", "Period thousands", ActionCategory.NUMBER, "12.345,67", supportsSelectedText = true),
            ActionDefinition("number_comma", ",nfc", "Comma thousands", ActionCategory.NUMBER, "12,345.67", supportsSelectedText = true),
            ActionDefinition("remove_diacritics", ",rd", "Remove diacritics", ActionCategory.TEXT, "Convert áéñ to aen", supportsSelectedText = true),
            ActionDefinition("uppercase", ",uu", "Uppercase", ActionCategory.TEXT, "Convert all text to uppercase", supportsSelectedText = true),
            ActionDefinition("lowercase", ",ll", "Lowercase", ActionCategory.TEXT, "Convert all text to lowercase", supportsSelectedText = true),
            ActionDefinition("sentence_case", ",ss", "Sentence case", ActionCategory.TEXT, "Capitalize each sentence", supportsSelectedText = true),
            ActionDefinition("title_case", ",ww", "Capitalize words", ActionCategory.TEXT, "Capitalize the first letter of every word", supportsSelectedText = true),
            ActionDefinition("space_underscore", ",su", "Spaces to underscores", ActionCategory.TEXT, "Replace spaces with underscores", supportsSelectedText = true),
            ActionDefinition("space_dash", ",sd", "Spaces to dashes", ActionCategory.TEXT, "Replace spaces with dashes", supportsSelectedText = true),
            ActionDefinition("underscore_space", ",us", "Underscores to spaces", ActionCategory.TEXT, "Replace underscores with spaces", supportsSelectedText = true),
            ActionDefinition("dash_space", ",ds", "Dashes to spaces", ActionCategory.TEXT, "Replace dashes with spaces", supportsSelectedText = true),
            ActionDefinition("uuid", ",uuid", "Generate UUID", ActionCategory.TEXT, "Replace the current text or selection with a UUID", supportsSelectedText = true),
            ActionDefinition("select_all", ",aa", "Select all", ActionCategory.SELECTION, "Select all remaining text"),
            ActionDefinition("select_before", ",sb", "Select before cursor", ActionCategory.SELECTION, "Select from the start to the cursor"),
            ActionDefinition("select_after", ",sa", "Select after cursor", ActionCategory.SELECTION, "Select from the cursor to the end"),
            ActionDefinition("delete_all", ",dd", "Delete all", ActionCategory.DELETION, "Delete all text"),
            ActionDefinition("delete_before", ",db", "Delete before cursor", ActionCategory.DELETION, "Delete from the start to the cursor"),
            ActionDefinition("delete_after", ",da", "Delete after cursor", ActionCategory.DELETION, "Delete from the cursor to the end"),
            ActionDefinition("trim_spaces", ",ts", "Trim spaces", ActionCategory.DELETION, "Delete leading and trailing spaces", supportsSelectedText = true),
            ActionDefinition("delete_blank_lines", ",ka", "Delete blank lines", ActionCategory.DELETION, "Remove empty lines", supportsSelectedText = true),
            ActionDefinition("cursor_start", ",cs", "Cursor to start", ActionCategory.CURSOR, "Place the cursor at the start"),
            ActionDefinition("cursor_end", ",ce", "Cursor to end", ActionCategory.CURSOR, "Place the cursor at the end"),
            ActionDefinition("copy_all", ",cc", "Copy all", ActionCategory.CLIPBOARD, "Copy all text"),
            ActionDefinition("paste", ",pp", "Paste", ActionCategory.CLIPBOARD, "Paste clipboard text"),
            ActionDefinition("copy_before", ",cb", "Copy before cursor", ActionCategory.CLIPBOARD, "Copy from start to cursor"),
            ActionDefinition("copy_after", ",ca", "Copy after cursor", ActionCategory.CLIPBOARD, "Copy from cursor to end"),
            ActionDefinition("paste_numbers", ",pn", "Paste numbers", ActionCategory.CLIPBOARD, "Paste only numeric characters"),
            ActionDefinition("cut_all", ",xx", "Cut all", ActionCategory.CLIPBOARD, "Cut all text"),
            ActionDefinition("cut_before", ",xb", "Cut before cursor", ActionCategory.CLIPBOARD, "Cut from start to cursor"),
            ActionDefinition("cut_after", ",xa", "Cut after cursor", ActionCategory.CLIPBOARD, "Cut from cursor to end"),
            ActionDefinition("share", ",sh", "Share", ActionCategory.ANDROID, "Open Android's share sheet"),
            ActionDefinition("new_snippet", ",ns", "New snippet", ActionCategory.EXPANDA, "Open Expanda's new snippet editor"),
            ActionDefinition("clipboard_history", ",ch", "Insert clipboard", ActionCategory.EXPANDA, "Insert the latest copied text"),
            ActionDefinition("toggle_suggestions", ",sg", "Toggle suggestions", ActionCategory.EXPANDA, "Enable or disable the suggestion overlay"),
        )

        private val MATH_AT_END = Regex("""[-+]?\s*(?:\d+(?:\.\d+)?|\([^\n]+\))(?:\s*[-+*/%^]\s*(?:\d+(?:\.\d+)?|\([^\n]+\)))+\s*$""")
        private val NUMBER = Regex("""(?<![\p{L}\d])[-+]?\d[\d.,]*(?![\p{L}\d])""")

        private fun formatResult(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

        private fun removeDiacritics(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

        private fun sentenceCase(value: String): String {
            var capitalize = true
            return buildString(value.length) {
                value.forEach { char ->
                    val next = if (capitalize && char.isLetter()) char.titlecaseChar() else char
                    append(next)
                    if (char.isLetter()) capitalize = false
                    if (char in ".!?\n") capitalize = true
                }
            }
        }

        private fun titleCase(value: String): String = buildString(value.length) {
            var boundary = true
            value.forEach { char ->
                append(if (boundary && char.isLetter()) char.titlecaseChar() else char)
                boundary = !char.isLetterOrDigit()
            }
        }
    }
}

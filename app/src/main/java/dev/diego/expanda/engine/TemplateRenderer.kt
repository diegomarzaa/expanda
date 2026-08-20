package dev.diego.expanda.engine

import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

/** A field that must be completed before a rendered snippet is inserted. */
data class TemplateFieldRequest(
    val name: String,
    val label: String = name,
    val token: String,
    val defaultValue: String = "",
    /** Range in [RenderedTemplate.text] occupied by the default value. */
    val start: Int = 0,
    val end: Int = start,
    val occurrence: Int = 0,
)

typealias TemplateField = TemplateFieldRequest

enum class TemplateActionType { SEND }

/** A non-text operation requested by a template (for example, sending a message). */
data class TemplateActionRequest(
    val type: TemplateActionType,
    val token: String,
    /** Position in the rendered text at which the action was declared. */
    val position: Int,
)

data class RenderedTemplate(
    val text: String,
    val cursorOffset: Int,
    val fields: List<TemplateFieldRequest> = emptyList(),
    val actions: List<TemplateActionRequest> = emptyList(),
    val unresolvedTokens: List<String> = emptyList(),
) {
    val formFields: List<TemplateFieldRequest> get() = fields
    val requiresInput: Boolean get() = fields.isNotEmpty()

    /**
     * Applies form values to the ranges reported by the renderer. Values are
     * looked up by the stable field name first and by the display label as a
     * convenience. Applying from right to left keeps all earlier ranges valid.
     */
    fun fillFields(values: Map<String, String>): RenderedTemplate {
        if (fields.isEmpty()) return this
        var result = text
        var cursor = cursorOffset
        fields.sortedByDescending { it.start }.forEach { field ->
            val value = values[field.name]
                ?: values[field.label]
                ?: field.defaultValue
            val start = field.start.coerceIn(0, result.length)
            val end = field.end.coerceIn(start, result.length)
            result = result.replaceRange(start, end, value)
            val delta = value.length - (end - start)
            cursor = when {
                cursor >= end -> cursor + delta
                cursor > start -> start + value.length
                else -> cursor
            }
        }
        return copy(text = result, cursorOffset = cursor.coerceIn(0, result.length), fields = emptyList())
    }
}

class TemplateRenderer(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val clipboard: () -> String = { "" },
    private val snippetResolver: (String) -> String? = { null },
    private val random: Random = Random.Default,
) {
    fun render(template: String, fieldValues: Map<String, String> = emptyMap()): RenderedTemplate {
        val rendered = renderInternal(normalizeLegacy(template), depth = 0)
        return if (fieldValues.isEmpty()) rendered else rendered.fillFields(fieldValues)
    }

    private fun renderInternal(template: String, depth: Int): RenderedTemplate {
        val now = LocalDateTime.now(clock)
        var cursorOffset: Int? = null
        val result = StringBuilder()
        val fields = mutableListOf<TemplateFieldRequest>()
        val actions = mutableListOf<TemplateActionRequest>()
        val unresolved = mutableListOf<String>()
        var index = 0

        TOKEN.findAll(template).forEach { match ->
            result.append(template, index, match.range.first)
            val expression = (match.groups[1]?.value ?: match.groups[2]?.value).orEmpty().trim()
            val baseOffset = result.length
            val output = resolve(expression, match.value, now, depth)
            if (output == null) {
                result.append(match.value)
                unresolved += match.value
            } else {
                result.append(output.text)
                output.cursorOffset?.let { cursorOffset = baseOffset + it }
                fields += output.fields.map { field ->
                    field.copy(start = baseOffset + field.start, end = baseOffset + field.end)
                }
                actions += output.actions.map { action -> action.copy(position = baseOffset + action.position) }
                unresolved += output.unresolvedTokens
            }
            index = match.range.last + 1
        }
        result.append(template, index, template.length)
        return RenderedTemplate(
            text = result.toString(),
            cursorOffset = (cursorOffset ?: result.length).coerceIn(0, result.length),
            fields = fields,
            actions = actions,
            unresolvedTokens = unresolved,
        )
    }

    private fun resolve(
        expression: String,
        originalToken: String,
        now: LocalDateTime,
        depth: Int,
    ): TokenOutput? {
        val name = expression.substringBefore(':').trim().lowercase(Locale.ROOT)
        val argument = expression.substringAfter(':', "").trim()
        return when (name) {
            "cursor" -> TokenOutput(text = "", cursorOffset = 0)
            "date" -> {
                val relative = relativeDateTime(argument, now)
                if (relative != null) TokenOutput(format(relative.first, relative.second))
                else TokenOutput(format(now, argument.removePrefix("custom:").ifBlank { "yyyy-MM-dd" }))
            }
            "time" -> {
                val relative = relativeDateTime(argument, now)
                if (relative != null) TokenOutput(format(relative.first, relative.second))
                else TokenOutput(format(now, argument.removePrefix("custom:").ifBlank { "HH:mm" }))
            }
            "datetime" -> {
                val relative = relativeDateTime(argument, now)
                if (relative != null) TokenOutput(format(relative.first, relative.second))
                else TokenOutput(format(now, argument.removePrefix("custom:").ifBlank { "yyyy-MM-dd HH:mm" }))
            }
            "futuredate", "pastdate", "futuretime", "pasttime" ->
                offsetDateTime(name, argument, now)?.let(::TokenOutput)
            "clipboard" -> TokenOutput(clipboard())
            "uuid" -> TokenOutput(UUID.randomUUID().toString())
            "random" -> TokenOutput(randomString(argument.toIntOrNull()?.coerceIn(1, 128) ?: 8))
            "snippet" -> {
                if (depth >= MAX_REFERENCE_DEPTH) {
                    // A bounded expansion is preferable to a stack overflow;
                    // dropping the recursive token also avoids inserting a
                    // misleading half-expanded reference.
                    TokenOutput("")
                } else {
                    snippetResolver(argument)?.let {
                        val nested = renderInternal(normalizeLegacy(it), depth + 1)
                        TokenOutput(
                            text = nested.text,
                            cursorOffset = nested.cursorOffset,
                            fields = nested.fields,
                            actions = nested.actions,
                            unresolvedTokens = nested.unresolvedTokens,
                        )
                    }
                }
            }
            "form", "textinput", "input" -> formOutput(argument, originalToken)
            "enter", "newline" -> TokenOutput("\n")
            "send" -> TokenOutput(
                text = "",
                actions = listOf(TemplateActionRequest(TemplateActionType.SEND, originalToken, 0)),
            )
            "upper" -> TokenOutput(argument.uppercase(Locale.getDefault()))
            "lower" -> TokenOutput(argument.lowercase(Locale.getDefault()))
            "title" -> TokenOutput(
                argument.split(' ').joinToString(" ") { word ->
                    word.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }
                },
            )
            else -> null
        }
    }

    private fun formOutput(argument: String, token: String): TokenOutput {
        val parts = argument.split('|', limit = 2)
        val label = parts.firstOrNull()?.trim().orEmpty().ifBlank { "Field" }
        val defaultValue = parts.getOrNull(1)?.trim().orEmpty()
        val name = stableFieldName(label)
        return TokenOutput(
            text = defaultValue,
            fields = listOf(
                TemplateFieldRequest(
                    name = name,
                    label = label,
                    token = token,
                    defaultValue = defaultValue,
                    start = 0,
                    end = defaultValue.length,
                    occurrence = 0,
                ),
            ),
        )
    }

    private fun stableFieldName(label: String): String = label
        .uppercase(Locale.ROOT)
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')
        .ifBlank { "FIELD" }

    private fun format(value: LocalDateTime, pattern: String): String = runCatching {
        value.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }.getOrElse { value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) }

    private fun randomString(length: Int): String = buildString(length) {
        repeat(length) { append(ALPHANUMERIC[random.nextInt(ALPHANUMERIC.length)]) }
    }

    private fun normalizeLegacy(template: String): String {
        var result = template
        LEGACY_SIMPLE.forEach { (legacy, modern) -> result = result.replace(legacy, modern, ignoreCase = true) }
        result = LEGACY_SINGLE_TOKEN.replace(result) { "{{${it.groupValues[1]}}}" }
        result = LEGACY_SNIPPET_PERCENT.replace(result) { "{{snippet:${it.groupValues[1]}}}" }
        result = LEGACY_SNIPPET_BRACES.replace(result) { "{{snippet:${it.groupValues[1]}}}" }
        result = LEGACY_DATETIME.replace(result) { "{{datetime:${it.groupValues[1]}}}" }
        result = LEGACY_FORM.replace(result) { "{{form:${it.groupValues[1]}}}" }
        result = LEGACY_OFFSET.replace(result) { "{{${it.groupValues[1]}:${it.groupValues[2]}}}" }
        return result
    }

    /** Parses both the historic `futuredate:2:DAY:pattern` and relative DATE/TIME forms. */
    private fun offsetDateTime(name: String, argument: String, now: LocalDateTime): String? {
        val parts = argument.split(':', limit = 3)
        if (parts.size != 3) return null
        val amount = parts[0].toLongOrNull() ?: return null
        val signed = if (name.startsWith("past")) -amount else amount
        val shifted = shift(now, signed, parts[1]) ?: return null
        return format(shifted, parts[2])
    }

    /** Supports `{DATE:+2:DAY:yyyy-MM-dd}` and compact `{TIME:-3d:yyyy-MM-dd}`. */
    private fun relativeDateTime(argument: String, now: LocalDateTime): Pair<LocalDateTime, String>? {
        if (!argument.startsWith('+') && !argument.startsWith('-')) return null
        val parts = argument.split(':', limit = 3)
        val unit: String
        val pattern: String
        if (parts.size >= 3) {
            val rawAmount = parts.firstOrNull()?.toLongOrNull() ?: return null
            unit = parts[1]
            pattern = parts[2]
            return shift(now, rawAmount, unit)?.let { it to pattern }
        } else {
            val compact = parts.first()
            if (compact.length < 2) return null
            val amount = compact.dropLast(1).toLongOrNull() ?: return null
            unit = when (compact.last().lowercaseChar()) {
                'm' -> "MINUTE"
                'h' -> "HOUR"
                'd' -> "DAY"
                'w' -> "WEEK"
                else -> return null
            }
            pattern = "yyyy-MM-dd"
            return shift(now, amount, unit)?.let { it to pattern }
        }
    }

    private fun shift(now: LocalDateTime, amount: Long, unit: String): LocalDateTime? = when (unit.uppercase(Locale.ROOT)) {
        "MINUTE", "MINUTES", "M" -> now.plus(amount, ChronoUnit.MINUTES)
        "HOUR", "HOURS", "H" -> now.plus(amount, ChronoUnit.HOURS)
        "DAY", "DAYS", "D" -> now.plus(amount, ChronoUnit.DAYS)
        "WEEK", "WEEKS", "W" -> now.plus(amount, ChronoUnit.WEEKS)
        "MONTH", "MONTHS" -> now.plus(amount, ChronoUnit.MONTHS)
        "YEAR", "YEARS", "Y" -> now.plus(amount, ChronoUnit.YEARS)
        else -> null
    }

    private data class TokenOutput(
        val text: String,
        val cursorOffset: Int? = null,
        val fields: List<TemplateFieldRequest> = emptyList(),
        val actions: List<TemplateActionRequest> = emptyList(),
        val unresolvedTokens: List<String> = emptyList(),
    )

    companion object {
        private val TOKEN = Regex("""\{\{([^{}]+)\}\}|\{([^{}]+)\}""")
        private val LEGACY_SNIPPET_PERCENT = Regex("""%snippet:([^%\s]{2,})%""", RegexOption.IGNORE_CASE)
        private val LEGACY_SNIPPET_BRACES = Regex("""(?<!\{)\{snippet:([^}\s]{2,})\}(?!\})""", RegexOption.IGNORE_CASE)
        private val LEGACY_DATETIME = Regex("""(?<!\{)\{datetime:([^}]+)\}(?!\})""", RegexOption.IGNORE_CASE)
        private val LEGACY_FORM = Regex("""%textinput:([^%]+)%""", RegexOption.IGNORE_CASE)
        private val LEGACY_OFFSET = Regex("""%(futuredate|pastdate|futuretime|pasttime):([^%]+)%""", RegexOption.IGNORE_CASE)
        private val LEGACY_SINGLE_TOKEN = Regex("""(?<!\{)\{(cursor|clipboard|date|time|enter|send)\}(?!\})""", RegexOption.IGNORE_CASE)
        private val LEGACY_SIMPLE = mapOf(
            "%cursor%" to "{{cursor}}",
            "[cursor]" to "{{cursor}}",
            "%clipboard_paste%" to "{{clipboard}}",
            "[clipboard]" to "{{clipboard}}",
        )
        private const val MAX_REFERENCE_DEPTH = 8
        private const val ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    }
}

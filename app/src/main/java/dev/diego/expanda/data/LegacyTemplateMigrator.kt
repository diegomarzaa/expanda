package dev.diego.expanda.data

import org.json.JSONObject
import java.util.Locale

/** Result of converting one 0.2 replacement template to Espanso syntax. */
data class LegacyTemplateMigration(
    val replace: String,
    val vars: List<TemplateVariable>,
    val notes: List<String> = emptyList(),
)

/**
 * Converts Expanda 0.2 inline template tokens to portable Espanso variables.
 *
 * Runtime rendering no longer understands single-brace tokens such as
 * `{FORM: name}` or `{DATE:yyyy-MM-dd}`; this migrator rewrites them once when
 * upgrading stored snippets.
 */
object LegacyTemplateMigrator {
    fun containsLegacySyntax(text: String): Boolean = findLegacyTokens(text).isNotEmpty()

    fun migrateMatch(match: TextMatch): TextMatch {
        var vars = match.vars
        val notes = mutableListOf<String>()
        val replacements = match.replacements.map { replacement ->
            val migration = migrateReplacement(replacement, vars)
            vars = mergeVars(vars, migration.vars)
            notes += migration.notes
            migration.replace
        }
        return match.copy(
            replacements = replacements,
            vars = vars,
            compatibilityWarnings = (match.compatibilityWarnings + notes).distinct(),
        )
    }

    fun migrateReplacement(
        text: String,
        existingVars: List<TemplateVariable> = emptyList(),
    ): LegacyTemplateMigration {
        if (!containsLegacySyntax(text)) {
            return LegacyTemplateMigration(text, emptyList())
        }
        val namer = VarNamer(existingVars.map { it.name })
        val notes = mutableListOf<String>()
        val newVars = mutableListOf<TemplateVariable>()
        val tokens = findLegacyTokens(text)
        if (tokens.isEmpty()) return LegacyTemplateMigration(text, emptyList())

        val result = StringBuilder()
        var index = 0
        tokens.forEach { token ->
            result.append(text, index, token.range.first)
            val converted = convertToken(token, namer, newVars, notes)
            result.append(converted)
            index = token.range.last + 1
        }
        result.append(text, index, text.length)
        return LegacyTemplateMigration(result.toString(), newVars, notes.distinct())
    }

    private fun convertToken(
        token: LegacyToken,
        namer: VarNamer,
        vars: MutableList<TemplateVariable>,
        notes: MutableList<String>,
    ): String = when (token.kind) {
        LegacyTokenKind.CURSOR -> CURSOR_MARKER
        LegacyTokenKind.NEWLINE -> "\n"
        LegacyTokenKind.SEND -> {
            notes += "Legacy {SEND} was removed; press your keyboard's send action after expansion."
            ""
        }
        LegacyTokenKind.CLIPBOARD -> {
            val name = namer.next("clip")
            vars += TemplateVariable(name, "clipboard")
            "{{$name}}"
        }
        LegacyTokenKind.DATE -> {
            val dateArg = when (token.name.lowercase(Locale.ROOT)) {
                "futuredate", "pastdate", "futuretime", "pasttime" -> "${token.name}:${token.argument}"
                else -> token.argument
            }
            val (offsetSeconds, format) = parseDateArgument(dateArg)
            val name = namer.next("date")
            vars += dateVar(name, format, offsetSeconds)
            "{{$name}}"
        }
        LegacyTokenKind.SNIPPET -> {
            val trigger = token.argument.trim()
            val name = namer.next("snippet")
            vars += TemplateVariable(name, "match", """{"trigger":${json(trigger)}}""")
            "{{$name}}"
        }
        LegacyTokenKind.FORM -> {
            val (field, defaultValue) = parseFormArgument(token.argument)
            val name = namer.next("form")
            val layout = if (defaultValue.isEmpty()) "[[$field]]" else "[[$field=$defaultValue]]"
            vars += formVar(name, layout)
            "{{$name}}"
        }
        LegacyTokenKind.RANDOM -> {
            val length = token.argument.trim().toIntOrNull()?.coerceIn(1, 128) ?: 8
            val name = namer.next("random")
            vars += TemplateVariable(name, "random", """{"length":$length}""")
            "{{$name}}"
        }
        LegacyTokenKind.UUID -> {
            val name = namer.next("uuid")
            vars += TemplateVariable(name, "random", """{"length":36,"alphabet":"0123456789abcdef"}""")
            notes += "Legacy {UUID} was converted to a random hex string; it is no longer guaranteed to be unique."
            "{{$name}}"
        }
        LegacyTokenKind.TRANSFORM -> applyTransform(token.name, token.argument)
    }

    private fun findLegacyTokens(text: String): List<LegacyToken> {
        val protected = mutableListOf<IntRange>()
        TEMPLATE_VARIABLE_REFERENCE.findAll(text).forEach { protected += it.range }
        var cursorIndex = text.indexOf(CURSOR_MARKER)
        while (cursorIndex >= 0) {
            protected += cursorIndex until cursorIndex + CURSOR_MARKER.length
            cursorIndex = text.indexOf(CURSOR_MARKER, cursorIndex + CURSOR_MARKER.length)
        }

        val found = mutableListOf<LegacyToken>()
        LEGACY_PATTERNS.forEach { pattern ->
            pattern.regex.findAll(text).forEach { match ->
                if (protected.none { match.range in it }) {
                    found += pattern.mapper(match)
                }
            }
        }
        return found
            .sortedBy { it.range.first }
            .fold(mutableListOf<LegacyToken>()) { merged, token ->
                if (merged.none { token.range in it.range }) merged += token
                merged
            }
    }

    private fun parseFormArgument(argument: String): Pair<String, String> {
        val parts = argument.split('|', limit = 2)
        val field = parts.firstOrNull()?.trim().orEmpty().ifBlank { "field" }
        val defaultValue = parts.getOrNull(1)?.trim().orEmpty()
        return field to defaultValue
    }

    private fun parseDateArgument(argument: String): Pair<Long, String> {
        val trimmed = argument.trim()
        if (trimmed.isEmpty()) return 0L to "yyyy-MM-dd"

        val offsetNameMatch = OFFSET_NAME.matchEntire(trimmed)
        if (offsetNameMatch != null) {
            val parts = offsetNameMatch.groupValues[2].split(':', limit = 3)
            if (parts.size == 3) {
                val amount = parts[0].toLongOrNull() ?: return 0L to "yyyy-MM-dd"
                val signed = if (offsetNameMatch.groupValues[1].startsWith("past")) -amount else amount
                val format = parts[2]
                return offsetSeconds(signed, parts[1]) to format
            }
        }

        val relative = RELATIVE_STANDARD.matchEntire(trimmed)
        if (relative != null) {
            val amount = relative.groupValues[1].toLongOrNull() ?: return 0L to "yyyy-MM-dd"
            return offsetSeconds(amount, relative.groupValues[2]) to relative.groupValues[3]
        }

        val compact = RELATIVE_COMPACT.matchEntire(trimmed)
        if (compact != null) {
            val amount = compact.groupValues[1].toLongOrNull() ?: return 0L to "yyyy-MM-dd"
            val unit = when (compact.groupValues[2].first().lowercaseChar()) {
                'm' -> "MINUTE"
                'h' -> "HOUR"
                'd' -> "DAY"
                'w' -> "WEEK"
                else -> return 0L to "yyyy-MM-dd"
            }
            return offsetSeconds(amount, unit) to "yyyy-MM-dd"
        }

        val custom = trimmed.removePrefix("custom:").ifBlank { "yyyy-MM-dd" }
        return 0L to custom
    }

    private fun applyTransform(name: String, argument: String): String = when (name.lowercase(Locale.ROOT)) {
        "upper" -> argument.uppercase(Locale.getDefault())
        "lower" -> argument.lowercase(Locale.getDefault())
        "title" -> argument.split(' ').joinToString(" ") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
        else -> "{$name:$argument}"
    }

    private fun offsetSeconds(amount: Long, unit: String): Long = when (unit.uppercase(Locale.ROOT)) {
        "MINUTE", "MINUTES", "M" -> amount * 60
        "HOUR", "HOURS", "H" -> amount * 3_600
        "DAY", "DAYS", "D" -> amount * 86_400
        "WEEK", "WEEKS", "W" -> amount * 7 * 86_400
        "MONTH", "MONTHS" -> amount * 30 * 86_400
        "YEAR", "YEARS", "Y" -> amount * 365 * 86_400
        else -> 0L
    }

    private fun mergeVars(
        existing: List<TemplateVariable>,
        incoming: List<TemplateVariable>,
    ): List<TemplateVariable> = (existing + incoming).distinctBy { it.name }

    private fun dateVar(name: String, format: String, offsetSeconds: Long): TemplateVariable {
        val params = JSONObject()
            .put("format", format)
            .put("offset", offsetSeconds)
        return TemplateVariable(name, "date", params.toString())
    }

    private fun formVar(name: String, layout: String): TemplateVariable {
        val params = JSONObject()
            .put("layout", layout)
            .put("fields", JSONObject())
        return TemplateVariable(name, "form", params.toString())
    }

    private fun json(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private data class LegacyToken(
        val range: IntRange,
        val kind: LegacyTokenKind,
        val name: String = "",
        val argument: String = "",
    )

    private enum class LegacyTokenKind {
        CURSOR,
        CLIPBOARD,
        DATE,
        SNIPPET,
        FORM,
        RANDOM,
        UUID,
        NEWLINE,
        SEND,
        TRANSFORM,
    }

    private data class LegacyPattern(
        val regex: Regex,
        val mapper: (MatchResult) -> LegacyToken,
    )

    private class VarNamer(existing: Collection<String>) {
        private val used = existing.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }

        fun next(base: String): String {
            val root = base.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "var" }
            var candidate = root
            var suffix = 2
            while (!used.add(candidate)) {
                candidate = "${root}_$suffix"
                suffix++
            }
            return candidate
        }
    }

    private val TEMPLATE_VARIABLE_REFERENCE = Regex("""\{\{[^{}]+\}\}""")
    private const val CURSOR_MARKER = "$|$"

    private val OFFSET_NAME = Regex(
        """^(futuredate|pastdate|futuretime|pasttime):(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    private val RELATIVE_STANDARD = Regex("""^([+-]\d+):([^:]+):(.+)$""")
    private val RELATIVE_COMPACT = Regex("""^([+-]\d+)([dhwm])$""", RegexOption.IGNORE_CASE)

    private val LEGACY_PATTERNS = listOf(
        LegacyPattern(Regex("""%clipboard_paste%""", RegexOption.IGNORE_CASE)) {
            LegacyToken(it.range, LegacyTokenKind.CLIPBOARD)
        },
        LegacyPattern(Regex("""%cursor%""", RegexOption.IGNORE_CASE)) {
            LegacyToken(it.range, LegacyTokenKind.CURSOR)
        },
        LegacyPattern(Regex("""\[clipboard]""", RegexOption.IGNORE_CASE)) {
            LegacyToken(it.range, LegacyTokenKind.CLIPBOARD)
        },
        LegacyPattern(Regex("""\[cursor]""", RegexOption.IGNORE_CASE)) {
            LegacyToken(it.range, LegacyTokenKind.CURSOR)
        },
        LegacyPattern(Regex("""%snippet:([^%\s]{1,})%""", RegexOption.IGNORE_CASE)) {
            LegacyToken(it.range, LegacyTokenKind.SNIPPET, argument = it.groupValues[1])
        },
        LegacyPattern(Regex("""%textinput:([^%]+)%""", RegexOption.IGNORE_CASE)) {
            LegacyToken(it.range, LegacyTokenKind.FORM, argument = it.groupValues[1])
        },
        LegacyPattern(Regex("""%(futuredate|pastdate|futuretime|pasttime):([^%]+)%""", RegexOption.IGNORE_CASE)) {
            LegacyToken(it.range, LegacyTokenKind.DATE, argument = "${it.groupValues[1]}:${it.groupValues[2]}")
        },
        LegacyPattern(Regex("""(?<!\{)\{([^{}]+)\}(?!\})""")) { match ->
            val expression = match.groupValues[1].trim()
            val name = expression.substringBefore(':').trim()
            val argument = expression.substringAfter(':', "").trim()
            when (name.lowercase(Locale.ROOT)) {
                "cursor" -> LegacyToken(match.range, LegacyTokenKind.CURSOR)
                "clipboard" -> LegacyToken(match.range, LegacyTokenKind.CLIPBOARD)
                "uuid" -> LegacyToken(match.range, LegacyTokenKind.UUID)
                "date", "time", "datetime", "futuredate", "pastdate", "futuretime", "pasttime" ->
                    LegacyToken(match.range, LegacyTokenKind.DATE, name = name, argument = expression.substringAfter(':'))
                "snippet" -> LegacyToken(match.range, LegacyTokenKind.SNIPPET, argument = argument)
                "form", "textinput", "input" -> LegacyToken(match.range, LegacyTokenKind.FORM, argument = argument)
                "random" -> LegacyToken(match.range, LegacyTokenKind.RANDOM, argument = argument)
                "enter", "newline" -> LegacyToken(match.range, LegacyTokenKind.NEWLINE)
                "send" -> LegacyToken(match.range, LegacyTokenKind.SEND)
                "upper", "lower", "title" -> LegacyToken(match.range, LegacyTokenKind.TRANSFORM, name = name, argument = argument)
                else -> LegacyToken(match.range, LegacyTokenKind.TRANSFORM, name = name, argument = argument)
            }
        },
    )
}

private operator fun IntRange.contains(other: IntRange): Boolean =
    other.first >= first && other.last <= last

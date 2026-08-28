package dev.diego.expanda.engine

import dev.diego.expanda.data.MatchTrigger
import dev.diego.expanda.data.RuntimeCompatibility
import dev.diego.expanda.data.TextMatch
import dev.diego.expanda.data.TriggerActivation
import dev.diego.expanda.data.TriggerKind
import dev.diego.expanda.data.UppercaseStyle
import java.util.Locale

/** The result of matching one canonical [TextMatch] at the text cursor. */
data class ExpansionMatch(
    val match: TextMatch,
    val replaceFrom: Int,
    val replaceTo: Int,
    val trailingDelimiter: String = "",
    /** The text consumed by the trigger, excluding [trailingDelimiter]. */
    val matchedText: String = "",
    /** Regex capture groups in numbered order, excluding group zero. */
    val captureGroups: List<String?> = emptyList(),
    /** Regex named capture groups, keyed by their source name. */
    val namedCaptureGroups: Map<String, String?> = emptyMap(),
) {
    /** Explicit name for callers that prefer the model terminology. */
    val textMatch: TextMatch get() = match

    /** Short aliases useful when passing a match through a renderer. */
    val captures: List<String?> get() = captureGroups
    val namedCaptures: Map<String, String?> get() = namedCaptureGroups

    /** Group zero followed by numbered groups, mirroring regex APIs. */
    val groupValues: List<String?> get() = listOf(matchedText) + captureGroups

    fun capture(index: Int): String? = when {
        index == 0 -> matchedText
        index > 0 -> captureGroups.getOrNull(index - 1)
        else -> null
    }

    fun capture(name: String): String? = namedCaptureGroups[name]
}

class ExpansionEngine {
    /**
     * Finds the longest valid literal or regex suffix immediately before [cursor].
     * A delimiter-triggered match consumes the delimiter only so it can be put back
     * after the rendered replacement.
     */
    fun findMatch(
        text: String,
        cursor: Int,
        matches: List<TextMatch>,
        packageName: String = "",
    ): ExpansionMatch? = findMatchesAtCursor(text, cursor, matches, packageName).singleOrNull()

    /** All portable matches at the cursor that share the longest trigger suffix. */
    fun findMatchesAtCursor(
        text: String,
        cursor: Int,
        matches: List<TextMatch>,
        packageName: String = "",
    ): List<ExpansionMatch> {
        if (cursor !in 0..text.length) return emptyList()

        val candidates = matches.mapNotNull { candidate ->
            if (
                !candidate.enabled ||
                candidate.runtimeCompatibility != RuntimeCompatibility.PORTABLE ||
                packageName in candidate.excludedPackages
            ) {
                null
            } else {
                matchOne(text, cursor, candidate)
            }
        }
        if (candidates.isEmpty()) return emptyList()
        val maxLength = candidates.maxOf { it.matchedText.length }
        return candidates.filter { it.matchedText.length == maxLength }
    }

    /** Applies a rendered replacement while preserving the trigger delimiter and cursor. */
    fun applyMatch(text: String, match: ExpansionMatch, rendered: RenderedTemplate): AppliedExpansion {
        require(match.replaceFrom in 0..match.replaceTo && match.replaceTo <= text.length) {
            "Expansion range is outside the source text"
        }

        val replacementText = if (match.match.options.propagateCase) {
            propagateCase(rendered.text, match.matchedText, match.match.options.uppercaseStyle)
        } else {
            rendered.text
        }
        val replacement = replacementText + match.trailingDelimiter
        val newText = text.replaceRange(match.replaceFrom, match.replaceTo, replacement)

        val renderedCursor = rendered.cursorOffset.coerceIn(0, rendered.text.length)
        val transformedCursor = if (match.match.options.propagateCase) {
            propagateCase(
                rendered.text.substring(0, renderedCursor),
                match.matchedText,
                match.match.options.uppercaseStyle,
            ).length
        } else {
            renderedCursor
        }
        val afterRendered = match.replaceFrom + transformedCursor.coerceAtMost(replacementText.length)
        val cursor = if (rendered.cursorOffset >= rendered.text.length) {
            match.replaceFrom + replacementText.length + match.trailingDelimiter.length
        } else {
            afterRendered
        }
        return AppliedExpansion(newText, cursor.coerceIn(0, newText.length))
    }

    private fun matchOne(text: String, cursor: Int, match: TextMatch): ExpansionMatch? {
        val options = match.options
        val delimiter = trailingDelimiter(text, cursor, options.activation, options.delimiters)
        if (options.activation == TriggerActivation.DELIMITER && delimiter.isEmpty()) return null

        val candidateEnd = cursor - delimiter.length
        if (candidateEnd < 0) return null
        val beforeCandidate = text.substring(0, candidateEnd)

        return match.triggers
            .asSequence()
            .filter { it.pattern.isNotBlank() }
            .mapNotNull { trigger -> matchTrigger(text, beforeCandidate, candidateEnd, trigger, match) }
            .maxByOrNull { it.matchedText.length }
    }

    private fun matchTrigger(
        text: String,
        beforeCandidate: String,
        candidateEnd: Int,
        trigger: MatchTrigger,
        match: TextMatch,
    ): ExpansionMatch? {
        val result = when (trigger.kind) {
            TriggerKind.TEXT -> matchLiteral(text, candidateEnd, trigger.pattern, match)
            TriggerKind.REGEX -> matchRegex(text, beforeCandidate, candidateEnd, trigger.pattern, match)
        } ?: return null

        if (!hasWordBoundaries(text, result.replaceFrom, candidateEnd, match)) return null
        return result
    }

    private fun matchLiteral(
        text: String,
        candidateEnd: Int,
        pattern: String,
        match: TextMatch,
    ): ExpansionMatch? {
        val start = candidateEnd - pattern.length
        if (start < 0) return null
        if (!text.regionMatches(start, pattern, 0, pattern.length, ignoreCase = !match.options.caseSensitive)) {
            return null
        }
        val delimiterLength = trailingDelimiterLength(text, candidateEnd, match)
        return ExpansionMatch(
            match = match,
            replaceFrom = start,
            replaceTo = candidateEnd + delimiterLength,
            trailingDelimiter = text.substring(candidateEnd, candidateEnd + delimiterLength),
            matchedText = text.substring(start, candidateEnd),
        )
    }

    private fun matchRegex(
        text: String,
        beforeCandidate: String,
        candidateEnd: Int,
        sourcePattern: String,
        match: TextMatch,
    ): ExpansionMatch? {
        val pattern = sourcePattern.normalizeNamedGroups()
        val regex = runCatching {
            Regex(
                pattern,
                if (match.options.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE),
            )
        }.getOrNull() ?: return null

        val suffix = regex.findAll(beforeCandidate)
            .filter { it.value.isNotEmpty() && it.range.last + 1 == candidateEnd }
            .maxByOrNull { it.range.first }
            ?: return null
        val delimiterLength = trailingDelimiterLength(text, candidateEnd, match)
        return ExpansionMatch(
            match = match,
            replaceFrom = suffix.range.first,
            replaceTo = candidateEnd + delimiterLength,
            trailingDelimiter = text.substring(candidateEnd, candidateEnd + delimiterLength),
            matchedText = suffix.value,
            captureGroups = suffix.groups.drop(1).map { it?.value },
            namedCaptureGroups = namedGroupNames(sourcePattern).associateWith { name ->
                suffix.groups[name]?.value
            },
        )
    }

    private fun hasWordBoundaries(
        text: String,
        replaceFrom: Int,
        candidateEnd: Int,
        match: TextMatch,
    ): Boolean {
        val options = match.options
        val leftOk = !options.leftWord || replaceFrom == 0 || !isWordCharacter(text[replaceFrom - 1])
        val rightOk = !options.rightWord || candidateEnd == text.length || !isWordCharacter(text[candidateEnd])
        return leftOk && rightOk
    }

    private fun trailingDelimiter(
        text: String,
        cursor: Int,
        activation: TriggerActivation,
        delimiters: String,
    ): String = if (
        activation == TriggerActivation.DELIMITER &&
        cursor > 0 &&
        text[cursor - 1] in delimiters
    ) {
        text[cursor - 1].toString()
    } else {
        ""
    }

    private fun trailingDelimiterLength(text: String, candidateEnd: Int, match: TextMatch): Int =
        if (
            match.options.activation == TriggerActivation.DELIMITER &&
            candidateEnd < text.length &&
            text[candidateEnd] in match.options.delimiters
        ) 1 else 0

    private fun isWordCharacter(character: Char): Boolean = character == '_' || character.isLetterOrDigit()

    private fun propagateCase(text: String, matchedText: String, style: UppercaseStyle): String {
        val cased = matchedText.filter { it.isLetter() }
        if (cased.isEmpty()) return text
        if (cased.all { it.isUpperCase() }) return text.uppercase(Locale.getDefault())
        if (cased.first().isUpperCase() && cased.drop(1).any { it.isLowerCase() }) {
            return when (style) {
                UppercaseStyle.CAPITALIZE -> capitalizeFirst(text)
                UppercaseStyle.CAPITALIZE_WORDS -> capitalizeWords(text)
                UppercaseStyle.UPPERCASE -> text.uppercase(Locale.getDefault())
            }
        }
        return text
    }

    private fun capitalizeFirst(text: String): String {
        val index = text.indexOfFirst { it.isLetter() }
        return if (index < 0) text else text.replaceRange(index, index + 1, text[index].uppercase(Locale.getDefault()))
    }

    private fun capitalizeWords(text: String): String = buildString(text.length) {
        var capitalize = true
        text.forEach { character ->
            if (character.isLetter()) {
                append(if (capitalize) character.titlecase(Locale.getDefault()) else character.toString())
                capitalize = false
            } else {
                append(character)
                capitalize = true
            }
        }
    }

    private fun namedGroupNames(pattern: String): Set<String> =
        NAMED_GROUP.findAll(pattern).map { it.groupValues[1] }.toSet()

    private fun String.normalizeNamedGroups(): String =
        PYTHON_NAMED_GROUP.replace(this) { "(?<${it.groupValues[1]}>" }

    companion object {
        private val PYTHON_NAMED_GROUP = Regex("""\(\?P<([A-Za-z][A-Za-z0-9_]*)>""")
        private val NAMED_GROUP = Regex("""\(\?(?:P<|<)([A-Za-z][A-Za-z0-9_]*)>""")
    }
}

data class AppliedExpansion(val text: String, val cursor: Int)

package dev.diego.expanda.data

/**
 * Lossless text surgery for Espanso match-set files.
 *
 * Parsing is still delegated to SnakeYAML. This class only finds root sections
 * and top-level match items so a visual edit can replace one item without
 * reformatting comments, quoting or unrelated block scalars.
 */
object EspansoSourceText {
    data class Span(val start: Int, val endExclusive: Int) {
        init {
            require(start >= 0 && endExclusive >= start)
        }
    }

    fun matchSpans(source: String): List<Span> {
        val lines = lines(source)
        val section = rootSection(lines, "matches") ?: return emptyList()
        val candidates = lines.indices.filter { index ->
            val line = lines[index]
            index > section.first && index < section.second &&
                line.trimmed.startsWith("-") &&
                line.trimmed.removePrefix("-").let { it.isBlank() || it.firstOrNull()?.isWhitespace() == true }
        }
        if (candidates.isEmpty()) return emptyList()
        val itemIndent = candidates.minOf { lines[it].indent }
        val starts = candidates.filter { lines[it].indent == itemIndent }
        return starts.mapIndexed { position, lineIndex ->
            val nextLine = starts.getOrNull(position + 1) ?: section.second
            Span(lines[lineIndex].start, contentEnd(lines, lineIndex, nextLine))
        }
    }

    fun matchText(source: String, index: Int): String? = matchSpans(source).getOrNull(index)?.let { span ->
        source.substring(span.start, span.endExclusive)
    }

    /** Complex YAML stays editable from Source instead of being regenerated. */
    fun visualEditMode(source: String, index: Int): SourceEditMode {
        val block = matchText(source, index) ?: return SourceEditMode.SOURCE_ONLY
        return if (hasUnsafeMatchPresentationSyntax(block)) {
            SourceEditMode.SOURCE_ONLY
        } else {
            SourceEditMode.VISUAL
        }
    }

    fun rootSectionVisualEditMode(source: String, key: String): SourceEditMode {
        val sourceLines = lines(source)
        val section = rootSection(sourceLines, key) ?: return SourceEditMode.VISUAL
        val start = sourceLines[section.first].start
        val end = sourceLines.getOrNull(section.second)?.start ?: source.length
        return if (source.substring(start, end).lineSequence().any(::hasPresentationSyntax)) {
            SourceEditMode.SOURCE_ONLY
        } else {
            SourceEditMode.VISUAL
        }
    }

    fun replaceMatch(source: String, index: Int, replacementItems: String): String {
        val span = matchSpans(source).getOrNull(index)
            ?: throw IllegalArgumentException("Espanso source has no match #${index + 1}")
        val prepared = indentItems(
            normalizeInsertedText(source, replacementItems).trimEnd('\r', '\n'),
            lineIndentAt(source, span.start),
            eol(source),
        )
        val suffixNeedsBreak = span.endExclusive < source.length &&
            source[span.endExclusive] != '\r' && source[span.endExclusive] != '\n'
        return source.replaceRange(
            span.start,
            span.endExclusive,
            prepared + if (suffixNeedsBreak) eol(source) else "",
        )
    }

    fun deleteMatch(source: String, index: Int): String {
        val span = matchSpans(source).getOrNull(index)
            ?: throw IllegalArgumentException("Espanso source has no match #${index + 1}")
        var end = span.endExclusive
        while (end < source.length && (source[end] == '\r' || source[end] == '\n')) end++
        return source.removeRange(span.start, end.coerceAtMost(source.length))
    }

    fun appendMatches(source: String, replacementItems: String): String {
        val lines = lines(source)
        val section = rootSection(lines, "matches")
        if (section != null) {
            val header = lines[section.first]
            val inlineValue = header.trimmed.substringAfter(':').trim()
            if (inlineValue == "[]") {
                val expanded = source.replaceRange(
                    header.start,
                    header.start + header.text.length,
                    "matches:",
                )
                return appendMatches(expanded, replacementItems)
            }
            require(inlineValue.isBlank()) {
                "Flow-style matches must be edited from Espanso source to preserve their formatting"
            }
        }
        val targetIndent = matchSpans(source).firstOrNull()?.let { lineIndentAt(source, it.start) } ?: "  "
        val prepared = indentItems(
            normalizeInsertedText(source, replacementItems).trimEnd('\r', '\n'),
            targetIndent,
            eol(source),
        )
        if (section == null) {
            val separator = if (source.isEmpty() || source.endsWith('\n') || source.endsWith('\r')) "" else eol(source)
            return source + separator + "matches:" + eol(source) + prepared + eol(source)
        }
        val insertion = lines.getOrNull(section.second)?.start ?: source.length
        val prefix = source.substring(0, insertion)
        val separator = if (prefix.isEmpty() || prefix.endsWith('\n') || prefix.endsWith('\r')) "" else eol(source)
        return prefix + separator + prepared + eol(source) + source.substring(insertion)
    }

    fun replaceRootSection(source: String, key: String, replacement: String?): String {
        val sourceLines = lines(source)
        val section = rootSection(sourceLines, key)
        val prepared = replacement?.let { normalizeInsertedText(source, it).trimEnd('\r', '\n') }
        if (section != null) {
            val start = sourceLines[section.first].start
            val end = contentEnd(sourceLines, section.first, section.second)
            return if (prepared == null) {
                source.removeRange(start, consumeLineBreaks(source, end))
            } else {
                val suffixNeedsBreak = end < source.length && source[end] != '\r' && source[end] != '\n'
                source.replaceRange(start, end, prepared + if (suffixNeedsBreak) eol(source) else "")
            }
        }
        if (prepared == null) return source

        val matches = rootSection(sourceLines, "matches")
        val insertion = matches?.let { sourceLines[it.first].start } ?: source.length
        val prefix = source.substring(0, insertion)
        val before = if (prefix.isEmpty() || prefix.endsWith('\n') || prefix.endsWith('\r')) "" else eol(source)
        return prefix + before + prepared + eol(source) + source.substring(insertion)
    }

    private data class SourceLine(
        val start: Int,
        val endExclusive: Int,
        val text: String,
    ) {
        val indent: Int = text.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) text.length else it }
        val trimmed: String = text.trimStart()
        val blankOrComment: Boolean = trimmed.isBlank() || trimmed.startsWith('#')
    }

    /** Returns the header line (inclusive) and next root key line (exclusive). */
    private fun rootSection(lines: List<SourceLine>, key: String): Pair<Int, Int>? {
        val header = lines.indexOfFirst { line ->
            line.indent == 0 &&
                !line.blankOrComment &&
                rootKeyName(line.trimmed) == key
        }

        if (header < 0) return null

        val end = (header + 1 until lines.size).firstOrNull { index ->
            val line = lines[index]

            line.indent == 0 &&
                !line.blankOrComment &&
                rootKeyName(line.trimmed) != null
        } ?: lines.size

        return header to end
    }

    private fun rootKeyName(line: String): String? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null

        val rawKey = line.substring(0, colon).trim()

        val key = when {
            rawKey.length >= 2 &&
                rawKey.startsWith('"') &&
                rawKey.endsWith('"') ->
                rawKey.substring(1, rawKey.length - 1)

            rawKey.length >= 2 &&
                rawKey.startsWith('\'') &&
                rawKey.endsWith('\'') ->
                rawKey.substring(1, rawKey.length - 1)

            else -> rawKey
        }

        return key.takeIf { ROOT_KEY_NAME.matches(it) }
    }

    private fun contentEnd(lines: List<SourceLine>, firstLine: Int, boundaryLine: Int): Int {
        var endLine = boundaryLine
        while (endLine > firstLine + 1 && lines[endLine - 1].blankOrComment) endLine--
        return lines.getOrNull(endLine)?.start
            ?: lines.getOrNull(boundaryLine - 1)?.endExclusive
            ?: lines[firstLine].endExclusive
    }

    private fun consumeLineBreaks(source: String, from: Int): Int {
        var index = from
        while (index < source.length && (source[index] == '\r' || source[index] == '\n')) index++
        return index
    }

    private fun normalizeInsertedText(source: String, value: String): String =
        value.replace("\r\n", "\n").replace('\r', '\n').replace("\n", eol(source))

    private fun indentItems(value: String, targetIndent: String, lineBreak: String): String {
        val sourceLines = value.split(lineBreak)
        val commonIndent = sourceLines.asSequence()
            .filter(String::isNotBlank)
            .map { line -> line.takeWhile(Char::isWhitespace).length }
            .minOrNull()
            ?: 0
        return sourceLines.joinToString(lineBreak) { line ->
            if (line.isBlank()) "" else targetIndent + line.drop(commonIndent)
        }
    }

    private fun lineIndentAt(source: String, offset: Int): String {
        var end = offset.coerceIn(0, source.length)
        while (end < source.length && (source[end] == ' ' || source[end] == '\t')) end++
        return source.substring(offset.coerceIn(0, source.length), end)
    }

    private fun eol(source: String): String = if ("\r\n" in source) "\r\n" else "\n"

    /**
     * A multiline scalar (`replace: |` / `replace: >`) is semantic content, not a
     * reason by itself to force Source editing. SnakeYAML already gives the visual
     * editor the resolved string value and the exporter writes that value back as
     * valid Espanso YAML.
     *
     * While inside a block scalar, lines such as `# heading` or `text # literal`
     * are replacement text rather than YAML comments, so they must not trigger the
     * presentation-syntax safety gate either.
     */
    private fun hasUnsafeMatchPresentationSyntax(block: String): Boolean {
        var scalarHeaderIndent: Int? = null

        block.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            scalarHeaderIndent?.let { headerIndent ->
                // Blank lines and every line indented beyond the scalar header are
                // literal scalar content. Resume YAML inspection only after dedent.
                if (trimmed.isBlank() || indent > headerIndent) return@forEach
                scalarHeaderIndent = null
            }

            // A real YAML comment still needs Source editing because a visual save
            // rewrites the match block and would discard that comment. This check is
            // intentionally before BLOCK_SCALAR so `replace: | # note` remains safe-
            // gated while a plain `replace: |` is allowed.
            if (trimmed.startsWith('#') || COMMENT_AFTER_VALUE.containsMatchIn(line)) {
                return true
            }
            if (YAML_ANCHOR_OR_ALIAS.containsMatchIn(line) || FLOW_COLLECTION.containsMatchIn(line)) {
                return true
            }
            if (BLOCK_SCALAR.containsMatchIn(line)) {
                scalarHeaderIndent = indent
            }
        }

        return false
    }

    private fun hasPresentationSyntax(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith('#') ||
            COMMENT_AFTER_VALUE.containsMatchIn(line) ||
            BLOCK_SCALAR.containsMatchIn(line) ||
            YAML_ANCHOR_OR_ALIAS.containsMatchIn(line) ||
            FLOW_COLLECTION.containsMatchIn(line)
    }

    private fun lines(source: String): List<SourceLine> {
        if (source.isEmpty()) return emptyList()
        val result = mutableListOf<SourceLine>()
        var start = 0
        var index = 0
        while (index < source.length) {
            if (source[index] == '\n' || source[index] == '\r') {
                val textEnd = index
                if (source[index] == '\r' && index + 1 < source.length && source[index + 1] == '\n') index++
                result += SourceLine(start, index + 1, source.substring(start, textEnd))
                start = index + 1
            }
            index++
        }
        if (start < source.length) result += SourceLine(start, source.length, source.substring(start))
        return result
    }

    private val ROOT_KEY_NAME = Regex("""[A-Za-z_][A-Za-z0-9_-]*""")
    private val COMMENT_AFTER_VALUE = Regex("""\s#[^\n]*$""")
    private val BLOCK_SCALAR = Regex(""":\s*[|>]([+-]?\d*)?\s*(?:#.*)?$""")
    private val YAML_ANCHOR_OR_ALIAS = Regex("""(?:^|\s)[&*][A-Za-z0-9_-]+""")
    private val FLOW_COLLECTION =
    Regex("""^\s*(?:-\s*)?[A-Za-z_][A-Za-z0-9_-]*\s*:\s*[\[{]""")
}

package dev.diego.expanda.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.regex.Pattern

internal data class RegexCaptureOption(
    val reference: String,
    val label: String,
    val sourceRange: IntRange? = null,
    val source: String = "",
)

internal data class RegexCaptureCatalog(
    val options: List<RegexCaptureOption>,
    val namedCaptures: Set<String>,
    val error: String? = null,
)

internal fun regexCaptureCatalog(pattern: String): RegexCaptureCatalog {
    val sources = captureSources(pattern)
    val named = sources.map(CaptureSource::name).toCollection(linkedSetOf())
    val normalized = PYTHON_NAMED_CAPTURE.replace(pattern) { "(?<${it.groupValues[1]}>" }
    runCatching { Pattern.compile(normalized) }.getOrElse {
        return RegexCaptureCatalog(emptyList(), named, "Fix the regex before adding a capture.")
    }
    return RegexCaptureCatalog(
        options = sources
            .distinctBy(CaptureSource::name)
            .map { source ->
                RegexCaptureOption(
                    reference = source.name,
                    label = "Named · ${source.name}",
                    sourceRange = source.range,
                    source = pattern.substring(source.range),
                )
            },
        namedCaptures = named,
    )
}

@Composable
internal fun RegexCaptureEditorDialog(
    pattern: String,
    initialReference: String? = null,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val catalog = remember(pattern) { regexCaptureCatalog(pattern) }
    var selected by remember(pattern, initialReference) {
        mutableStateOf(
            initialReference?.takeIf { reference -> catalog.options.any { it.reference == reference } }
                ?: catalog.options.firstOrNull()?.reference,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialReference == null) "Insert regex capture" else "Edit regex capture") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Parentheses capture the changing parts of a regex. Choose which part to reuse.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Trigger preview", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            highlightedRegexPattern(
                                pattern = pattern,
                                range = catalog.options.firstOrNull { it.reference == selected }?.sourceRange,
                            ),
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                catalog.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (catalog.error == null && catalog.options.isEmpty()) {
                    Text(
                        "Espanso exposes only named regex captures. Add one such as (?P<number>\\d+), then insert {{number}}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                catalog.options.forEach { option ->
                    ListItem(
                        headlineContent = { Text(option.label) },
                        supportingContent = {
                            Text(
                                "${option.source.ifBlank { "Captured part" }} → {{${option.reference}}}",
                                fontFamily = FontFamily.Monospace,
                            )
                        },
                        leadingContent = {
                            RadioButton(
                                selected = selected == option.reference,
                                onClick = { selected = option.reference },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                }
                TextButton(
                    onClick = onDismiss,
                ) { Text("Cancel") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onSave) },
                enabled = selected != null,
            ) { Text(if (initialReference == null) "Insert" else "Save") }
        },
    )
}

private val PYTHON_NAMED_CAPTURE = Regex("""\(\?P<([A-Za-z][A-Za-z0-9_]*)>""")

private data class CaptureSource(
    val name: String,
    val range: IntRange,
)

private data class OpenRegexGroup(
    val start: Int,
    val name: String?,
)

/** Small source scanner used only for explaining capture groups in the editor. */
private fun captureSources(pattern: String): List<CaptureSource> {
    val openGroups = ArrayDeque<OpenRegexGroup>()
    val result = mutableListOf<CaptureSource>()
    var escaped = false
    var inCharacterClass = false

    pattern.forEachIndexed { index, char ->
        if (escaped) {
            escaped = false
            return@forEachIndexed
        }
        if (char == '\\') {
            escaped = true
            return@forEachIndexed
        }
        if (char == '[') {
            inCharacterClass = true
            return@forEachIndexed
        }
        if (char == ']' && inCharacterClass) {
            inCharacterClass = false
            return@forEachIndexed
        }
        if (inCharacterClass) return@forEachIndexed

        when (char) {
            '(' -> {
                val name = when {
                    pattern.startsWith("(?P<", index) -> pattern.indexOf('>', index + 4)
                        .takeIf { it >= 0 }
                        ?.let { pattern.substring(index + 4, it) }
                    pattern.startsWith("(?<", index) && pattern.getOrNull(index + 3) !in setOf('=', '!') ->
                        pattern.indexOf('>', index + 3)
                            .takeIf { it >= 0 }
                            ?.let { pattern.substring(index + 3, it) }
                    else -> null
                }
                openGroups.addLast(OpenRegexGroup(index, name))
            }
            ')' -> if (openGroups.isNotEmpty()) {
                val open = openGroups.removeLast()
                open.name?.let { result += CaptureSource(it, open.start..index) }
            }
        }
    }
    return result.sortedBy { it.range.first }
}

@Composable
private fun highlightedRegexPattern(pattern: String, range: IntRange?): AnnotatedString {
    val builder = AnnotatedString.Builder(pattern.ifEmpty { "Type a regex trigger first" })
    if (pattern.isNotEmpty() && range != null && range.first >= 0 && range.last < pattern.length) {
        builder.addStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                background = MaterialTheme.colorScheme.primaryContainer,
                fontWeight = FontWeight.Bold,
            ),
            range.first,
            range.last + 1,
        )
    }
    return builder.toAnnotatedString()
}

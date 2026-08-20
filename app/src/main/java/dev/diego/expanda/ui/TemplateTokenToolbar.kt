package dev.diego.expanda.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.diego.expanda.data.Snippet

private enum class TokenDialog { DATE, TIME, OFFSET, FORM, SNIPPET }

/** Guided token palette: common actions insert directly; configurable tokens open a form. */
@Composable
fun TemplateTokenToolbar(
    onInsert: (String) -> Unit,
    snippets: List<Snippet> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<TokenDialog?>(null) }
    val direct = listOf(
        TokenButton("Cursor", "{CURSOR}", Icons.Default.TouchApp),
        TokenButton("Clipboard", "{CLIPBOARD}", Icons.Default.ContentPaste),
        TokenButton("Enter", "{ENTER}", Icons.AutoMirrored.Filled.KeyboardReturn),
        TokenButton("Send", "{SEND}", Icons.AutoMirrored.Filled.Send),
    )
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        direct.forEach { item -> TokenChip(item.label, item.icon) { onInsert(item.token) } }
        TokenChip("Date", Icons.Default.CalendarMonth) { dialog = TokenDialog.DATE }
        TokenChip("Time", Icons.Default.Schedule) { dialog = TokenDialog.TIME }
        TokenChip("Date offset", Icons.Default.Update) { dialog = TokenDialog.OFFSET }
        TokenChip("Form field", Icons.Default.TextFields) { dialog = TokenDialog.FORM }
        TokenChip("Snippet", Icons.Default.Link) { dialog = TokenDialog.SNIPPET }
    }

    when (dialog) {
        TokenDialog.DATE -> FormatTokenDialog("Insert date", listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd.MM.yyyy", "MMM d, yyyy", "EEEE, d MMMM yyyy"), "DATE", { dialog = null }) { onInsert(it); dialog = null }
        TokenDialog.TIME -> FormatTokenDialog("Insert time", listOf("HH:mm", "HH:mm:ss", "h:mm a"), "TIME", { dialog = null }) { onInsert(it); dialog = null }
        TokenDialog.OFFSET -> OffsetTokenDialog({ dialog = null }) { onInsert(it); dialog = null }
        TokenDialog.FORM -> FormTokenDialog({ dialog = null }) { onInsert(it); dialog = null }
        TokenDialog.SNIPPET -> SnippetTokenDialog(snippets, { dialog = null }) { onInsert(it); dialog = null }
        null -> Unit
    }
}

private data class TokenButton(val label: String, val token: String, val icon: ImageVector)

@Composable
private fun TokenChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
    )
}

@Composable
private fun FormatTokenDialog(title: String, formats: List<String>, tokenName: String, dismiss: () -> Unit, insert: (String) -> Unit) {
    var format by remember { mutableStateOf(formats.first()) }
    AlertDialog(
        onDismissRequest = dismiss, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            formats.forEach { option -> AssistChip(onClick = { format = option }, label = { Text(if (format == option) "✓ $option" else option) }) }
            OutlinedTextField(format, { format = it }, label = { Text("Custom format") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { insert("{$tokenName:$format}") }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OffsetTokenDialog(dismiss: () -> Unit, insert: (String) -> Unit) {
    var amount by remember { mutableStateOf("1") }
    var direction by remember { mutableStateOf(1) }
    var unit by remember { mutableStateOf("DAY") }
    var format by remember { mutableStateOf("yyyy-MM-dd") }
    AlertDialog(
        onDismissRequest = dismiss, title = { Text("Date/time offset") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { direction = 1 }, label = { Text(if (direction > 0) "✓ Add" else "Add") })
                AssistChip(onClick = { direction = -1 }, label = { Text(if (direction < 0) "✓ Subtract" else "Subtract") })
            }
            OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Amount") }, singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MINUTE", "HOUR", "DAY", "WEEK", "MONTH", "YEAR").forEach { option ->
                    AssistChip(onClick = { unit = option }, label = { Text(if (unit == option) "✓ $option" else option) })
                }
            }
            OutlinedTextField(format, { format = it }, label = { Text("Output format") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = {
            val signed = (amount.toLongOrNull() ?: 1L) * direction
            insert("{DATE:${if (signed >= 0) "+" else ""}$signed:$unit:$format}")
        }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FormTokenDialog(dismiss: () -> Unit, insert: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var default by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss, title = { Text("Form field") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The user will be asked for this value whenever the snippet is used.")
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Field name") }, singleLine = true)
            OutlinedTextField(default, { default = it }, Modifier.fillMaxWidth(), label = { Text("Default value (optional)") })
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            insert("{FORM: ${name.trim()}${if (default.isNotEmpty()) "|$default" else ""}}")
        }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SnippetTokenDialog(snippets: List<Snippet>, dismiss: () -> Unit, insert: (String) -> Unit) {
    var shortcut by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss, title = { Text("Nested snippet") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(shortcut, { shortcut = it }, label = { Text("Shortcut") }, singleLine = true)
            snippets.take(12).forEach { snippet -> AssistChip(
                onClick = { shortcut = snippet.shortcut },
                label = { Text("${snippet.shortcut} · ${snippet.label.ifBlank { snippet.content.take(28) }}") },
            ) }
        } },
        confirmButton = { TextButton(enabled = shortcut.isNotBlank(), onClick = { insert("{SNIPPET: ${shortcut.trim()}}") }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

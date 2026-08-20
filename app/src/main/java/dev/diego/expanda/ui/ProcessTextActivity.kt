package dev.diego.expanda.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.diego.expanda.engine.MathEvaluator
import java.util.Locale

class ProcessTextActivity : ComponentActivity() {
    private val selectedText: String by lazy {
        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (selectedText.isEmpty()) {
            finish()
            return
        }
        setContent {
            MaterialTheme { ActionChooser(selectedText, ::returnText) { finish() } }
        }
    }

    private fun returnText(value: String) {
        if (intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)) {
            finish()
            return
        }
        setResult(Activity.RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, value))
        finish()
    }
}

@Composable
private fun ActionChooser(text: String, select: (String) -> Unit, dismiss: () -> Unit) {
    val title = text.split(Regex("\\s+")).joinToString(" ") { word ->
        word.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
    val calculation = MathEvaluator.evaluate(text).getOrNull()?.let(::formatNumber)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Expanda actions") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                ListItem(headlineContent = { Text("UPPERCASE") }, modifier = Modifier.fillMaxWidth(),
                    trailingContent = { TextButton(onClick = { select(text.uppercase()) }) { Text("Use") } })
                ListItem(headlineContent = { Text("lowercase") }, modifier = Modifier.fillMaxWidth(),
                    trailingContent = { TextButton(onClick = { select(text.lowercase()) }) { Text("Use") } })
                ListItem(headlineContent = { Text("Title Case") }, modifier = Modifier.fillMaxWidth(),
                    trailingContent = { TextButton(onClick = { select(title) }) { Text("Use") } })
                if (calculation != null) ListItem(
                    headlineContent = { Text("Calculate: $calculation") },
                    trailingContent = { TextButton(onClick = { select(calculation) }) { Text("Use") } },
                )
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

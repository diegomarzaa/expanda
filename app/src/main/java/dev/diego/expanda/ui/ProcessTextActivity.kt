package dev.diego.expanda.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.diego.expanda.ExpandaApplication
import dev.diego.expanda.engine.ActionDefinition
import dev.diego.expanda.engine.ActionEngine
import dev.diego.expanda.ui.theme.ExpandaTheme

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
            val settings by (application as ExpandaApplication).settingsRepository.settings.collectAsState()
            ExpandaTheme(settings) { ActionChooser(selectedText, ::returnText) { finish() } }
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
    val engine = ActionEngine()
    val actions = ActionEngine.definitions.filter(ActionDefinition::supportsSelectedText)
    val results = actions.mapNotNull { action ->
        engine.processSelectedText(action.id, text)
            ?.takeIf { it != text }
            ?.let { action to it }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Expanda actions") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (results.isEmpty()) Text("No transformation changes this selection.")
                results.forEach { (action, result) ->
                    ListItem(
                        headlineContent = { Text(action.title) },
                        supportingContent = { Text(action.description) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingContent = {
                            TextButton(onClick = { select(result) }) { Text("Use") }
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

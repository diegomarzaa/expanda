package dev.diego.expanda.engine

import dev.diego.expanda.data.Snippet
import dev.diego.expanda.data.TemplateSelectionMode
import kotlin.random.Random

data class SelectedTemplate(
    val text: String,
    val index: Int,
    val total: Int,
)

/** Pure selection logic so the service and Compose suggestion UI share rules. */
class TemplateSelector(private val random: Random = Random.Default) {
    fun select(snippet: Snippet, manualIndex: Int? = null): SelectedTemplate {
        val templates = snippet.allTemplates().ifEmpty { listOf("") }
        val index = when (snippet.selectionMode) {
            TemplateSelectionMode.FIRST -> 0
            TemplateSelectionMode.RANDOM -> random.nextInt(templates.size)
            TemplateSelectionMode.SEQUENTIAL -> snippet.templateIndex.mod(templates.size.toLong()).toInt()
            TemplateSelectionMode.MANUAL -> (manualIndex ?: 0).mod(templates.size)
        }
        return SelectedTemplate(templates[index], index, templates.size)
    }
}

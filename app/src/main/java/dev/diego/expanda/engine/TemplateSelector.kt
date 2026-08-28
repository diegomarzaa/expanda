package dev.diego.expanda.engine

import dev.diego.expanda.data.TemplateSelectionMode
import dev.diego.expanda.data.TextMatch
import kotlin.random.Random

data class SelectedTemplate(
    val text: String,
    val index: Int,
    val total: Int,
)

/** Pure replacement selection shared by the runtime and any future UI picker. */
class TemplateSelector(private val random: Random = Random.Default) {
    fun select(match: TextMatch, manualIndex: Int? = null): SelectedTemplate {
        val replacements = match.replacements.ifEmpty { listOf("") }
        val index = when (match.selectionMode) {
            TemplateSelectionMode.FIRST -> 0
            TemplateSelectionMode.RANDOM -> random.nextInt(replacements.size)
            TemplateSelectionMode.SEQUENTIAL -> floorMod(match.templateIndex, replacements.size)
            TemplateSelectionMode.MANUAL -> floorMod((manualIndex ?: 0).toLong(), replacements.size)
        }
        return SelectedTemplate(replacements[index], index, replacements.size)
    }

    private fun floorMod(value: Long, modulus: Int): Int = Math.floorMod(value, modulus.toLong()).toInt()
}

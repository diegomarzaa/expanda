package dev.diego.expanda.data

enum class TriggerMode { DELIMITER, INSTANT }

/**
 * How a snippet chooses one of its templates when it is expanded.
 *
 * `content` remains the first (and backwards-compatible) template.  Entries
 * in [Snippet.templates] are additional templates, in their editing order.
 */
enum class TemplateSelectionMode { FIRST, RANDOM, SEQUENTIAL, MANUAL }

data class Snippet(
    val id: Long = 0,
    val shortcut: String,
    val content: String,
    val label: String = "",
    val tags: Set<String> = emptySet(),
    val enabled: Boolean = true,
    val caseSensitive: Boolean = false,
    val triggerMode: TriggerMode = TriggerMode.DELIMITER,
    val delimiters: String = " \n\t.,!?;:",
    val excludedPackages: Set<String> = emptySet(),
    val usageCount: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Additional templates. [content] is always the first template. */
    val templates: List<String> = emptyList(),
    val selectionMode: TemplateSelectionMode = TemplateSelectionMode.FIRST,
    /** Cursor used by SEQUENTIAL. It is persisted so the sequence survives restarts. */
    val templateIndex: Long = 0,
) {
    /** All templates in stable editor order, including the legacy [content] field. */
    fun allTemplates(): List<String> = buildList {
        add(content)
        addAll(templates)
    }.filter(String::isNotEmpty)

    /** Alias useful to callers that think of the first content as a variant too. */
    val templateVariants: List<String> get() = allTemplates()

    /**
     * Chooses a template without mutating the snippet. Persistence of the
     * sequential cursor is handled by [ExpandaDatabase.recordExpansion].
     */
    fun chooseTemplate(
        randomIndex: Int? = null,
        manualIndex: Int? = null,
    ): String {
        val available = allTemplates().ifEmpty { listOf("") }
        val index = when (selectionMode) {
            TemplateSelectionMode.FIRST -> 0
            TemplateSelectionMode.RANDOM ->
                (randomIndex ?: kotlin.random.Random.nextInt(available.size))
                    .mod(available.size)
            TemplateSelectionMode.SEQUENTIAL -> templateIndex.mod(available.size.toLong()).toInt()
            // MANUAL is deliberately deterministic when no UI choice is
            // supplied. This keeps accessibility expansion safe; a Compose
            // editor/suggestion picker can pass the selected index explicitly.
            TemplateSelectionMode.MANUAL -> (manualIndex ?: 0).mod(available.size)
        }
        return available[index]
    }
}

data class ExpansionLog(
    val id: Long = 0,
    val snippetId: Long,
    val packageName: String,
    val expandedAt: Long = System.currentTimeMillis(),
)

data class DashboardStats(
    val totalExpansions: Long,
    val estimatedCharactersSaved: Long,
    val estimatedSecondsSaved: Long,
)

data class ClipboardEntry(
    val id: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
)

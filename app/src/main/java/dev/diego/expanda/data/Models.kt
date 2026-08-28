package dev.diego.expanda.data

enum class TriggerKind { TEXT, REGEX }

data class MatchTrigger(
    val pattern: String,
    val kind: TriggerKind = TriggerKind.TEXT,
)

enum class TriggerActivation { DELIMITER, IMMEDIATE }

enum class UppercaseStyle { CAPITALIZE, CAPITALIZE_WORDS, UPPERCASE }

data class MatchOptions(
    /** Espanso literal triggers are case-sensitive unless case propagation is used. */
    val caseSensitive: Boolean = true,
    /** Espanso expands as soon as the trigger is complete. */
    val activation: TriggerActivation = TriggerActivation.IMMEDIATE,
    val delimiters: String = " \n\t.,!?;:",
    val leftWord: Boolean = false,
    val rightWord: Boolean = false,
    val propagateCase: Boolean = false,
    /** Espanso default with propagate_case: capitalize the first letter only. */
    val uppercaseStyle: UppercaseStyle = UppercaseStyle.CAPITALIZE,
) {
    /** Espanso never keeps case-insensitive matching without case propagation. */
    fun normalizedCase(): MatchOptions = when {
        caseSensitive && propagateCase -> copy(propagateCase = false)
        !caseSensitive && !propagateCase -> copy(propagateCase = true)
        else -> this
    }
}

/** A portable template variable. Import adapters reject or warn about unsupported types. */
data class TemplateVariable(
    val name: String,
    val type: String,
    /** JSON object containing type-specific parameters. */
    val paramsJson: String = "{}",
    val dependsOn: List<String> = emptyList(),
    /** Espanso's inject_vars option. False keeps {{references}} inside params literal. */
    val injectVars: Boolean = true,
)

enum class TemplateSelectionMode { FIRST, RANDOM, SEQUENTIAL, MANUAL }

/** Whether Android can execute a source match without changing its meaning. */
enum class RuntimeCompatibility { PORTABLE, DESKTOP_ONLY }

/** Whether the visual editor can rewrite the source block without losing syntax. */
enum class SourceEditMode { VISUAL, SOURCE_ONLY }

/**
 * Expanda's canonical text-match model.
 *
 * File formats and older database versions are converted to this type at the
 * boundary. Runtime matching, rendering and editing depend only on this model.
 */
data class TextMatch(
    val id: Long = 0,
    val triggers: List<MatchTrigger>,
    val replacements: List<String>,
    val label: String = "",
    val tags: Set<String> = emptySet(),
    val searchTerms: Set<String> = emptySet(),
    val enabled: Boolean = true,
    val options: MatchOptions = MatchOptions(),
    val vars: List<TemplateVariable> = emptyList(),
    /** Import details that need user attention but do not prevent safe storage. */
    val compatibilityWarnings: List<String> = emptyList(),
    val runtimeCompatibility: RuntimeCompatibility = RuntimeCompatibility.PORTABLE,
    val sourceEditMode: SourceEditMode = SourceEditMode.VISUAL,
    val excludedPackages: Set<String> = emptySet(),
    val selectionMode: TemplateSelectionMode = TemplateSelectionMode.FIRST,
    /** Persisted cursor used only by [TemplateSelectionMode.SEQUENTIAL]. */
    val templateIndex: Long = 0,
    val usageCount: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Relative path of the Espanso match-set that owns this match. */
    val sourceFile: String? = null,
    /** Zero-based position inside sourceFile's matches list. */
    val sourceMatchIndex: Int? = null,
) {
    val trigger: String get() = triggers.firstOrNull()?.pattern.orEmpty()
    val replace: String get() = replacements.firstOrNull().orEmpty()
    val runsOnAndroid: Boolean get() = runtimeCompatibility == RuntimeCompatibility.PORTABLE
    val canEditVisually: Boolean get() = sourceEditMode == SourceEditMode.VISUAL

    fun textTriggers(): List<String> = triggers
        .asSequence()
        .filter { it.kind == TriggerKind.TEXT }
        .map(MatchTrigger::pattern)
        .filter(String::isNotBlank)
        .distinct()
        .toList()

    fun chooseReplacement(randomIndex: Int? = null, manualIndex: Int? = null): String {
        val available = replacements.ifEmpty { listOf("") }
        val index = when (selectionMode) {
            TemplateSelectionMode.FIRST -> 0
            TemplateSelectionMode.RANDOM ->
                (randomIndex ?: kotlin.random.Random.nextInt(available.size)).mod(available.size)
            TemplateSelectionMode.SEQUENTIAL -> templateIndex.mod(available.size.toLong()).toInt()
            TemplateSelectionMode.MANUAL -> (manualIndex ?: 0).mod(available.size)
        }
        return available[index]
    }
}

data class ExpansionLog(
    val id: Long = 0,
    val matchId: Long,
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

package dev.diego.expanda.data

/**
 * Espanso template syntax shared by the renderer and visual editor.
 *
 * Espanso only treats `{{name}}` and `{{name.subname}}` as variable references.
 * A single `{...}` is ordinary replacement text and must never be interpreted as
 * a template command. Rust regex's `\w` is Unicode-aware, so the word definition
 * below mirrors it closely on Android/JVM (letters, marks, numbers, connector
 * punctuation, and join controls).
 */
internal const val ESPANSO_WORD_PATTERN = "[\\p{L}\\p{M}\\p{N}\\p{Pc}\\u200C\\u200D]+"

/** Group 1 = full reference expression, group 2 = variable name, group 3 = subname. */
internal const val TEMPLATE_VARIABLE_REFERENCE_PATTERN =
    "\\{\\{\\s*(($ESPANSO_WORD_PATTERN)(?:\\.($ESPANSO_WORD_PATTERN))?)\\s*\\}\\}"

internal val TEMPLATE_VARIABLE_REFERENCE = Regex(TEMPLATE_VARIABLE_REFERENCE_PATTERN)
private val ESPANSO_WORD = Regex("^$ESPANSO_WORD_PATTERN$")

internal fun referencedVariableNames(template: String): Set<String> =
    TEMPLATE_VARIABLE_REFERENCE.findAll(template)
        .map { it.groupValues[2] }
        .toSet()

internal fun isEspansoWord(value: String): Boolean =
    value.isNotEmpty() && ESPANSO_WORD.matches(value)

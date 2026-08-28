package dev.diego.expanda.engine

import dev.diego.expanda.data.TemplateVariable
import dev.diego.expanda.data.TextMatch
import dev.diego.expanda.data.TEMPLATE_VARIABLE_REFERENCE_PATTERN
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** A field that must be completed before a rendered replacement is inserted. */
data class TemplateFieldRequest(
    val name: String,
    val label: String = name,
    val token: String,
    val defaultValue: String = "",
    /** Range in [RenderedTemplate.text] occupied by the default value. */
    val start: Int = 0,
    val end: Int = start,
    val occurrence: Int = 0,
    val inputType: TemplateFieldInputType = TemplateFieldInputType.TEXT,
    val options: List<String> = emptyList(),
    /** Values inserted for [options], preserving Espanso choice label/id pairs. */
    val optionValues: List<String> = options,
    val multiline: Boolean = false,
)

typealias TemplateField = TemplateFieldRequest

enum class TemplateFieldInputType { TEXT, CHOICE, DATE, TIME }

enum class TemplateActionType { SEND }

/** A non-text operation requested by a template. */
data class TemplateActionRequest(
    val type: TemplateActionType,
    val token: String,
    /** Position in the rendered text at which the action was declared. */
    val position: Int,
)

data class RenderedTemplate(
    val text: String,
    val cursorOffset: Int,
    val fields: List<TemplateFieldRequest> = emptyList(),
    val actions: List<TemplateActionRequest> = emptyList(),
    val unresolvedTokens: List<String> = emptyList(),
) {
    val formFields: List<TemplateFieldRequest> get() = fields
    val requiresInput: Boolean get() = fields.isNotEmpty()

    /**
     * Applies form values from right to left, so nested and repeated fields keep
     * valid source ranges while earlier replacements are made.
     */
    fun fillFields(values: Map<String, String>): RenderedTemplate {
        if (fields.isEmpty()) return this
        var result = text
        var cursor = cursorOffset
        fields.sortedWith(compareByDescending<TemplateFieldRequest> { it.start }.thenByDescending { it.end })
            .forEach { field ->
                val value = values[field.name]
                    ?: values[field.label]
                    ?: field.defaultValue
                val start = field.start.coerceIn(0, result.length)
                val end = field.end.coerceIn(start, result.length)
                result = result.replaceRange(start, end, value)
                val delta = value.length - (end - start)
                cursor = when {
                    cursor >= end -> cursor + delta
                    cursor > start -> start + value.length
                    else -> cursor
                }
            }
        return copy(
            text = result,
            cursorOffset = cursor.coerceIn(0, result.length),
            fields = emptyList(),
        )
    }
}

private fun String.unescapeVariableInjections(): String =
    replace("\\{\\{", "{{").replace("\\}\\}", "}}")

private fun RenderedTemplate.unescapeVariableInjections(): RenderedTemplate {
    if ("\\{\\{" !in text && "\\}\\}" !in text) return this

    fun mappedOffset(offset: Int): Int = text
        .substring(0, offset.coerceIn(0, text.length))
        .replace("\\{\\{", "{{")
        .replace("\\}\\}", "}}")
        .length

    val unescaped = text.unescapeVariableInjections()
    return copy(
        text = unescaped,
        cursorOffset = mappedOffset(cursorOffset).coerceIn(0, unescaped.length),
        fields = fields.map { field ->
            field.copy(
                start = mappedOffset(field.start),
                end = mappedOffset(field.end),
            )
        },
        actions = actions.map { action -> action.copy(position = mappedOffset(action.position)) },
    )
}

/**
 * Renders the portable part of Espanso's template language.
 *
 * Variable references follow Espanso syntax exactly: `{{name}}` or
 * `{{name.subname}}`. Single braces are always literal text. Supported portable
 * variable types are echo, date, random, clipboard, match, choice and form;
 * unsupported types remain unresolved so Android never silently changes meaning.
 */
class TemplateRenderer(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val clipboard: () -> String = { "" },
    private val matchResolver: (String) -> TextMatch? = { null },
    private val random: Random = Random.Default,
) {
    fun render(template: String, fieldValues: Map<String, String> = emptyMap()): RenderedTemplate =
        render(template, variables = emptyList(), expansionMatch = null, fieldValues = fieldValues)

    fun render(
        template: String,
        variables: List<TemplateVariable>,
        expansionMatch: ExpansionMatch? = null,
        fieldValues: Map<String, String> = emptyMap(),
    ): RenderedTemplate = renderWithScope(
        template = template,
        localVariables = variables,
        globalVariables = emptyList(),
        expansionMatch = expansionMatch,
        fieldValues = fieldValues,
    )

    private fun renderWithScope(
        template: String,
        localVariables: List<TemplateVariable>,
        globalVariables: List<TemplateVariable>,
        expansionMatch: ExpansionMatch?,
        fieldValues: Map<String, String>,
    ): RenderedTemplate {
        val variables = (localVariables + globalVariables).distinctBy(TemplateVariable::name)
        val context = RenderContext(
            variables = variables,
            globalVariables = globalVariables,
            expansionMatch = expansionMatch,
            now = Instant.now(clock).atZone(clock.zone),
        )
        val rendered = renderInternal(template, context, depth = 0)
            .unescapeVariableInjections()
        return if (fieldValues.isEmpty()) rendered else rendered.fillFields(fieldValues)
    }

    /** Convenience overload for a regex expansion's captures. */
    fun render(
        template: String,
        expansionMatch: ExpansionMatch,
        variables: List<TemplateVariable> = emptyList(),
        fieldValues: Map<String, String> = emptyMap(),
    ): RenderedTemplate = render(template, variables, expansionMatch, fieldValues)

    /** Selects a replacement and renders the variables owned by the canonical match. */
    fun render(
        match: TextMatch,
        expansionMatch: ExpansionMatch? = null,
        fieldValues: Map<String, String> = emptyMap(),
        manualIndex: Int? = null,
        globalVariables: List<TemplateVariable> = emptyList(),
    ): RenderedTemplate {
        val selected = TemplateSelector(random).select(match, manualIndex)
        return renderWithScope(
            template = selected.text,
            localVariables = match.vars,
            globalVariables = globalVariables,
            expansionMatch = expansionMatch,
            fieldValues = fieldValues,
        )
    }

    private fun renderInternal(
        template: String,
        context: RenderContext,
        depth: Int,
    ): RenderedTemplate {
        if (depth > MAX_REFERENCE_DEPTH) {
            return RenderedTemplate(
                text = template,
                cursorOffset = template.length,
                unresolvedTokens = VARIABLE_REFERENCE.findAll(template).map { it.value }.toList(),
            )
        }

        var cursorOffset: Int? = null
        val result = StringBuilder()
        val fields = mutableListOf<TemplateFieldRequest>()
        val actions = mutableListOf<TemplateActionRequest>()
        val unresolved = mutableListOf<String>()
        var sourceIndex = 0

        TEMPLATE_TOKEN.findAll(template).forEach { tokenMatch ->
            result.append(template, sourceIndex, tokenMatch.range.first)
            val baseOffset = result.length

            if (tokenMatch.value == CURSOR_MARKER) {
                cursorOffset = baseOffset
            } else {
                val expression = tokenMatch.groups[1]?.value.orEmpty()
                val output = resolveReference(expression, tokenMatch.value, context, depth)
                if (output == null) {
                    result.append(tokenMatch.value)
                    unresolved += tokenMatch.value
                } else {
                    result.append(output.text)
                    output.cursorOffset?.let { cursorOffset = baseOffset + it }
                    fields += output.fields.map { field ->
                        val occurrence = fields.count { existing -> existing.name == field.name }
                        field.copy(
                            start = baseOffset + field.start,
                            end = baseOffset + field.end,
                            occurrence = occurrence,
                        )
                    }
                    actions += output.actions.map { action ->
                        action.copy(position = baseOffset + action.position)
                    }
                    unresolved += output.unresolvedTokens
                }
            }
            sourceIndex = tokenMatch.range.last + 1
        }
        result.append(template, sourceIndex, template.length)
        return RenderedTemplate(
            text = result.toString(),
            cursorOffset = (cursorOffset ?: result.length).coerceIn(0, result.length),
            fields = fields,
            actions = actions,
            unresolvedTokens = unresolved,
        )
    }

    /**
     * Resolves only syntax Espanso itself recognizes: a declared variable, a
     * dotted output from a multi-value form variable, or a named regex capture.
     * The variable's `type` owns its behavior; names such as `clipboard` or
     * `date` have no special meaning by themselves.
     */
    private fun resolveReference(
        expression: String,
        originalToken: String,
        context: RenderContext,
        depth: Int,
    ): TokenOutput? {
        val separator = expression.indexOf('.')
        if (separator > 0) {
            val variableName = expression.substring(0, separator)
            val subname = expression.substring(separator + 1)
            val variable = context.variables.firstOrNull { it.name == variableName }
            if (variable != null && variable.type.equals("form", ignoreCase = true)) {
                return formFieldOutput(variable, subname, originalToken, context, depth)
            }
            return null
        }

        context.variables.firstOrNull { it.name == expression }?.let { variable ->
            // Espanso form variables produce multiple named values and therefore
            // require a dotted reference such as {{form.field}}.
            if (variable.type.equals("form", ignoreCase = true)) return null
            return resolveVariable(variable, context, depth)
        }

        return context.expansionMatch
            ?.namedCaptureGroups
            ?.get(expression)
            ?.let { TokenOutput(it.orEmpty()) }
    }

    private fun resolveVariable(
        variable: TemplateVariable,
        context: RenderContext,
        depth: Int,
    ): TokenOutput? {
        context.cachedVariables[variable.name]?.let { return it }
        if (depth > MAX_REFERENCE_DEPTH || !context.resolvingVariables.add(variable.name)) return null

        return try {
            for (dependency in variable.dependsOn) {
                val dependencyVariable = context.variables.firstOrNull { it.name == dependency } ?: return null
                if (resolveVariable(dependencyVariable, context, depth + 1) == null) return null
            }

            val rawParams = parseParams(variable.paramsJson)
            val params = if (variable.injectVars) {
                try {
                    expandParams(rawParams, context, depth + 1)
                } catch (_: UnresolvedVariableReference) {
                    return null
                }
            } else {
                rawParams
            }
            val type = variable.type.lowercase(Locale.ROOT)
            val output = when (type) {
                "echo" -> TokenOutput(params.string("echo", "value"))
                "date" -> dateOutput(
                    params.string("format").ifBlank { "yyyy-MM-dd" },
                    context,
                    offsetSeconds = params.optLong("offset", 0),
                    locale = params.locale(),
                    timezone = params.timezone(),
                )
                "random" -> randomOutput(params)
                "clipboard" -> TokenOutput(clipboard())
                "choice" -> choiceOutput(variable, params)
                "match" -> nestedMatchOutput(params.string("trigger", "match", "name"), context, depth)
                // In particular, shell and script are intentionally not handled.
                else -> null
            }

            if (output != null) context.cachedVariables[variable.name] = output
            output
        } finally {
            context.resolvingVariables.remove(variable.name)
        }
    }

    private fun formFieldOutput(
        variable: TemplateVariable,
        fieldName: String,
        token: String,
        context: RenderContext,
        depth: Int,
    ): TokenOutput? {
        if (depth > MAX_REFERENCE_DEPTH || !context.resolvingVariables.add(variable.name)) return null
        return try {
            for (dependency in variable.dependsOn) {
                val dependencyVariable = context.variables.firstOrNull { it.name == dependency } ?: return null
                if (resolveVariable(dependencyVariable, context, depth + 1) == null) return null
            }

            val rawParams = parseParams(variable.paramsJson)
            val params = if (variable.injectVars) {
                try {
                    expandParams(rawParams, context, depth + 1)
                } catch (_: UnresolvedVariableReference) {
                    return null
                }
            } else {
                rawParams
            }
            val definition = params.optJSONObject("fields")?.optJSONObject(fieldName)
            val defaultValue = fieldDefault(definition)
            val inputType = fieldInputType(definition)
            val label = "${variable.name}.$fieldName"
            TokenOutput(
                text = defaultValue,
                fields = listOf(
                    TemplateFieldRequest(
                        name = stableFieldName(label),
                        label = label,
                        token = token,
                        defaultValue = defaultValue,
                        end = defaultValue.length,
                        inputType = inputType,
                        options = definition?.strings("values").orEmpty(),
                        multiline = definition?.optBoolean("multiline") == true,
                    ),
                ),
            )
        } finally {
            context.resolvingVariables.remove(variable.name)
        }
    }

    private fun fieldDefault(definition: JSONObject?, fallback: String = ""): String {
        if (definition == null) return fallback
        val explicit = definition.string("default")
        if (explicit.isNotBlank()) return explicit
        val values = definition.strings("values")
        return if (
            definition.string("type").lowercase(Locale.ROOT) in setOf("choice", "list") &&
            values.isNotEmpty()
        ) {
            values.first()
        } else {
            fallback
        }
    }

    private fun fieldInputType(definition: JSONObject?): TemplateFieldInputType = when (
        definition?.string("type")?.lowercase(Locale.ROOT)
    ) {
        "choice", "list" -> TemplateFieldInputType.CHOICE
        "date" -> TemplateFieldInputType.DATE
        "time" -> TemplateFieldInputType.TIME
        else -> TemplateFieldInputType.TEXT
    }

    private fun dateOutput(
        pattern: String,
        context: RenderContext,
        offsetSeconds: Long = 0,
        locale: Locale = Locale.getDefault(),
        timezone: ZoneId = context.now.zone,
    ): TokenOutput {
        val value = context.now.plusSeconds(offsetSeconds).withZoneSameInstant(timezone)
        return TokenOutput(format(value, pattern.ifBlank { "yyyy-MM-dd" }, locale))
    }

    private fun randomOutput(params: JSONObject): TokenOutput {
        val choices = params.strings("choices")
        if (choices.isNotEmpty()) return TokenOutput(choices[random.nextInt(choices.size)])
        val length = params.optInt("length", DEFAULT_RANDOM_LENGTH).coerceIn(1, MAX_RANDOM_LENGTH)
        val alphabet = params.string("alphabet").ifBlank { ALPHANUMERIC }
        return TokenOutput(randomString(length, alphabet))
    }

    private fun choiceOutput(
        variable: TemplateVariable,
        params: JSONObject,
    ): TokenOutput {
        val choices = params.choiceValues().map {
            ChoiceValue(
                label = it.label,
                value = it.value,
            )
        }
        return choiceValueFieldOutput(variable.name, "{{${variable.name}}}", choices)
    }

    private fun choiceValueFieldOutput(name: String, token: String, choices: List<ChoiceValue>): TokenOutput {
        if (choices.isEmpty()) return TokenOutput("")
        return TokenOutput(
            text = "",
            fields = listOf(
                TemplateFieldRequest(
                    name = stableFieldName(name),
                    label = name,
                    token = token,
                    inputType = TemplateFieldInputType.CHOICE,
                    options = choices.map(ChoiceValue::label),
                    optionValues = choices.map(ChoiceValue::value),
                ),
            ),
        )
    }

    private fun nestedMatchOutput(
        trigger: String,
        context: RenderContext,
        depth: Int,
    ): TokenOutput? {
        val key = trigger.trim()
        if (key.isBlank() || depth >= MAX_REFERENCE_DEPTH) return null
        val nested = matchResolver(key) ?: return null
        val selected = TemplateSelector(random).select(nested).text
        val nestedContext = RenderContext(
            variables = (nested.vars + context.globalVariables).distinctBy(TemplateVariable::name),
            globalVariables = context.globalVariables,
            expansionMatch = context.expansionMatch,
            now = context.now,
        )
        val rendered = renderInternal(selected, nestedContext, depth + 1)
        return TokenOutput(
            text = rendered.text,
            cursorOffset = rendered.cursorOffset,
            fields = rendered.fields,
            actions = rendered.actions,
            unresolvedTokens = rendered.unresolvedTokens,
        )
    }

    private fun expandScalar(value: String, context: RenderContext, depth: Int): String {
        if (value.isBlank() || depth > MAX_REFERENCE_DEPTH) return value.unescapeVariableInjections()
        val expanded = VARIABLE_REFERENCE.replace(value) { token ->
            resolveReference(
                expression = token.groups[1]?.value.orEmpty(),
                originalToken = token.value,
                context = context,
                depth = depth + 1,
            )?.text ?: throw UnresolvedVariableReference(token.value)
        }
        return expanded.unescapeVariableInjections()
    }

    private fun expandParams(value: JSONObject, context: RenderContext, depth: Int): JSONObject = JSONObject().apply {
        value.keys().forEach { key -> put(key, expandParamValue(value.opt(key), context, depth)) }
    }

    private fun expandParamValue(value: Any?, context: RenderContext, depth: Int): Any? = when (value) {
        is String -> expandScalar(value, context, depth)
        is JSONObject -> expandParams(value, context, depth)
        is JSONArray -> JSONArray().apply {
            for (index in 0 until value.length()) put(expandParamValue(value.opt(index), context, depth))
        }
        else -> value
    }

    private fun format(value: ZonedDateTime, pattern: String, locale: Locale): String = runCatching {
        value.format(DateTimeFormatter.ofPattern(toJavaDatePattern(pattern), locale))
    }.getOrElse { value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) }

    private fun toJavaDatePattern(pattern: String): String {
        if (!pattern.contains('%')) return pattern
        return pattern
            .replace("%%", "%")
            .replace("%Y", "yyyy")
            .replace("%y", "yy")
            .replace("%B", "MMMM")
            .replace("%b", "MMM")
            .replace("%A", "EEEE")
            .replace("%a", "EEE")
            .replace("%m", "MM")
            .replace("%d", "dd")
            .replace("%H", "HH")
            .replace("%M", "mm")
            .replace("%S", "ss")
            .replace("%x", "MM/dd/yy")
            .replace("%X", "HH:mm:ss")
    }

    private fun randomString(length: Int, alphabet: String = ALPHANUMERIC): String {
        val source = alphabet.ifEmpty { ALPHANUMERIC }
        return buildString(length) {
            repeat(length) { append(source[random.nextInt(source.length)]) }
        }
    }

    private fun stableFieldName(label: String): String = label
        .uppercase(Locale.ROOT)
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')
        .ifBlank { "FIELD" }

    private fun parseParams(value: String): JSONObject = runCatching { JSONObject(value) }.getOrElse { JSONObject() }

    private class UnresolvedVariableReference(token: String) : RuntimeException(token)

    private data class RenderContext(
        val variables: List<TemplateVariable>,
        val globalVariables: List<TemplateVariable> = emptyList(),
        val expansionMatch: ExpansionMatch?,
        val now: ZonedDateTime,
        val cachedVariables: MutableMap<String, TokenOutput> = mutableMapOf(),
        val resolvingVariables: MutableSet<String> = mutableSetOf(),
    )

    private data class TokenOutput(
        val text: String,
        val cursorOffset: Int? = null,
        val fields: List<TemplateFieldRequest> = emptyList(),
        val actions: List<TemplateActionRequest> = emptyList(),
        val unresolvedTokens: List<String> = emptyList(),
    )

    private fun JSONObject.string(vararg keys: String): String = keys
        .asSequence()
        .map { optString(it) }
        .firstOrNull(String::isNotBlank)
        .orEmpty()

    private fun JSONObject.locale(): Locale = string("locale")
        .takeIf(String::isNotBlank)
        ?.let(Locale::forLanguageTag)
        ?.takeIf { it.language.isNotBlank() }
        ?: Locale.getDefault()

    private fun JSONObject.timezone(): ZoneId = runCatching { ZoneId.of(string("tz", "timezone")) }
        .getOrDefault(ZoneId.systemDefault())

    private fun JSONObject.strings(key: String): List<String> {
        val value = opt(key)
        return when (value) {
            is JSONArray -> buildList(value.length()) {
                for (index in 0 until value.length()) {
                    val item = value.opt(index)
                    when (item) {
                        is JSONObject -> item.string("id", "value", "label").takeIf(String::isNotBlank)?.let(::add)
                        else -> item?.toString()?.takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }
            is String -> value.lines().filter(String::isNotBlank)
            else -> emptyList()
        }
    }

    private fun JSONObject.choiceValues(): List<ChoiceValue> {
        val value = opt("values") ?: opt("choices") ?: return emptyList()
        return when (value) {
            is JSONArray -> buildList(value.length()) {
                for (index in 0 until value.length()) {
                    val item = value.opt(index)
                    if (item is JSONObject) {
                        add(ChoiceValue(item.string("label"), item.string("id", "value", "label")))
                    } else {
                        item?.toString()?.let { add(ChoiceValue(it, it)) }
                    }
                }
            }
            is String -> value.lines().filter(String::isNotBlank).map { ChoiceValue(it, it) }
            else -> emptyList()
        }
    }

    private data class ChoiceValue(val label: String, val value: String)

    companion object {
        private const val DEFAULT_RANDOM_LENGTH = 8
        private const val MAX_RANDOM_LENGTH = 128
        private const val MAX_REFERENCE_DEPTH = 8
        private const val CURSOR_MARKER = "$|$"
        private val VARIABLE_REFERENCE = Regex(TEMPLATE_VARIABLE_REFERENCE_PATTERN)
        private val TEMPLATE_TOKEN = Regex("""\$\|\$|$TEMPLATE_VARIABLE_REFERENCE_PATTERN""")
        private const val ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    }
}

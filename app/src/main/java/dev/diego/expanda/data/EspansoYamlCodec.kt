package dev.diego.expanda.data

import org.json.JSONArray
import org.json.JSONObject
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.common.ScalarStyle
import java.util.Locale

enum class CompatibilitySeverity { INFO, WARNING, ERROR }

data class CompatibilityIssue(
    val severity: CompatibilitySeverity,
    val source: String,
    val message: String,
)

data class EspansoImportResult(
    val matches: List<TextMatch>,
    val issues: List<CompatibilityIssue>,
    val globalVariables: List<TemplateVariable> = emptyList(),
)

data class EspansoExportResult(
    val yaml: String,
    val issues: List<CompatibilityIssue>,
)

/** Offline Espanso YAML interoperability boundary for [TextMatch]. */
object EspansoYamlCodec {
    private val loadSettings = LoadSettings.builder()
        .setLabel("Espanso configuration")
        // Espanso's own YAML stack accepts the last value. Keeping this lenient
        // lets Expanda preserve and round-trip hand-edited files unchanged.
        .setAllowDuplicateKeys(true)
        .setCodePointLimit(2_000_000)
        .setMaxAliasesForCollections(50)
        .build()
    private val dumpSettings = DumpSettings.builder()
        .setDefaultFlowStyle(FlowStyle.BLOCK)
        // Let the emitter use plain Espanso-style scalars and quote only when YAML requires it.
        .setDefaultScalarStyle(ScalarStyle.PLAIN)
        .setIndent(2)
        .build()

    fun decode(
        yaml: String,
        sourceName: String = "Espanso YAML",
        importsResolved: Boolean = false,
    ): EspansoImportResult {
        val loaded = runCatching { Load(loadSettings).loadFromString(yaml) }
            .getOrElse { throw IllegalArgumentException("Invalid Espanso YAML", it) }
        val root = loaded.asStringMap()
            ?: throw IllegalArgumentException("Espanso YAML must contain an object at its root")
        val issues = mutableListOf<CompatibilityIssue>()
        val globalVariables = parseVariables(root["global_vars"], "$sourceName global_vars", issues)
        globalVariables.map { it.type.lowercase(Locale.ROOT) }
            .filterNot { it in PORTABLE_VARIABLE_TYPES }
            .distinct()
            .forEach { type ->
                issues += CompatibilityIssue(
                    CompatibilitySeverity.WARNING,
                    "$sourceName global_vars",
                    "Global variable type '$type' is retained but is not executed on Android.",
                )
            }
        val rawMatches = when (val value = root["matches"]) {
            null -> emptyList<Any?>()
            is List<*> -> value
            else -> throw IllegalArgumentException("Espanso YAML matches must be a list")
        }

        val unknownRootFields = root.keys.filterNot { it in ROOT_FIELDS }
        unknownRootFields.forEach { field ->
            issues += CompatibilityIssue(
                CompatibilitySeverity.WARNING,
                sourceName,
                "Root field '$field' is not interpreted on Android.",
            )
        }
        if (root["imports"] != null && !importsResolved) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.WARNING,
                sourceName,
                "imports are not followed on Android; referenced YAML files must be selected separately.",
            )
        }
        if (root["anchors"] != null) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.WARNING,
                sourceName,
                "Root anchors are not interpreted on Android.",
            )
        }

        val rootWarnings = issues.map(CompatibilityIssue::message)
        val rootRequiresDesktop = unknownRootFields.isNotEmpty() || root["anchors"] != null ||
            (root["imports"] != null && !importsResolved)
        val matches = rawMatches.mapIndexedNotNull { index, raw ->
            val map = raw.asStringMap()
            if (map == null) {
                issues += CompatibilityIssue(
                    CompatibilitySeverity.ERROR,
                    "$sourceName match #${index + 1}",
                    "Match is not an object.",
                )
                null
            } else {
                parseMatch(map, index, sourceName, issues)
            }
        }.map { match ->
            match.copy(
                compatibilityWarnings = (rootWarnings + match.compatibilityWarnings).distinct(),
                runtimeCompatibility = if (rootRequiresDesktop) {
                    RuntimeCompatibility.DESKTOP_ONLY
                } else {
                    match.runtimeCompatibility
                },
                sourceEditMode = if (rootRequiresDesktop) SourceEditMode.SOURCE_ONLY else match.sourceEditMode,
            )
        }
        return EspansoImportResult(matches, issues, globalVariables)
    }

    /** Paths named by an Espanso match-set's imports root field. */
    fun importPaths(yaml: String): List<String> {
        val loaded = runCatching { Load(loadSettings).loadFromString(yaml.removePrefix("\uFEFF")) }
            .getOrElse { throw IllegalArgumentException("Invalid Espanso YAML", it) }
        return stringList(loaded.asStringMap()?.get("imports"))
    }

    fun encode(
        matches: List<TextMatch>,
        globalVariables: List<TemplateVariable> = emptyList(),
    ): EspansoExportResult {
        val issues = mutableListOf<CompatibilityIssue>()
        val exported = buildList {
            matches.forEach { addAll(exportMatch(it, issues)) }
        }
        val root = linkedMapOf<String, Any?>()
        if (globalVariables.isNotEmpty()) {
            root["global_vars"] = globalVariables.map(::exportVariable)
        }
        root["matches"] = exported
        return EspansoExportResult(Dump(dumpSettings).dumpToString(root), issues)
    }

    /** Encodes a match-set file with a blank line between each match for readability. */
    fun encodeSpaced(matches: List<TextMatch>): EspansoExportResult {
        val issues = mutableListOf<CompatibilityIssue>()
        val itemBlocks = matches.map { encodeMatchItems(it).yaml }
        val yaml = buildString {
            appendLine("matches:")
            itemBlocks.forEachIndexed { index, block ->
                block.lines().forEach { line ->
                    append("  ")
                    appendLine(line)
                }
                if (index < itemBlocks.lastIndex) appendLine()
            }
        }.trimEnd('\r', '\n') + "\n"
        return EspansoExportResult(yaml, issues)
    }

    /** Canonical Espanso YAML item(s) for one visually edited match. */
    fun encodeMatchItems(match: TextMatch): EspansoExportResult {
        val result = encode(listOf(match), emptyList())
        val spans = EspansoSourceText.matchSpans(result.yaml)
        return result.copy(
            yaml = spans.joinToString("\n") { span ->
                result.yaml.substring(span.start, span.endExclusive).trimEnd('\r', '\n')
            },
        )
    }

    fun encodeGlobalVariablesSection(variables: List<TemplateVariable>): String? {
        if (variables.isEmpty()) return null
        val yaml = Dump(dumpSettings).dumpToString(
            linkedMapOf<String, Any?>("global_vars" to variables.map(::exportVariable)),
        )
        return yaml.trimEnd('\r', '\n')
    }

    private fun parseMatch(
        map: Map<String, Any?>,
        index: Int,
        sourceName: String,
        issues: MutableList<CompatibilityIssue>,
    ): TextMatch? {
        val source = "$sourceName match #${index + 1}"
        val issueStart = issues.size
        val unknownFields = map.keys.filterNot { it in SUPPORTED_MATCH_FIELDS }
        unknownFields.forEach { field ->
            issues += CompatibilityIssue(
                CompatibilitySeverity.WARNING,
                source,
                "Field '$field' is not interpreted on Android.",
            )
        }

        val regex = stringValue(map["regex"])
        val literalTriggers = when {
            map["triggers"] != null -> stringList(map["triggers"])
            else -> listOfNotNull(stringValue(map["trigger"]).takeIf(String::isNotBlank))
        }
        if (regex.isNotBlank() && literalTriggers.isNotEmpty()) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.WARNING,
                source,
                "regex takes precedence over literal trigger fields.",
            )
        }
        val triggers = if (regex.isNotBlank()) {
            listOf(MatchTrigger(regex, TriggerKind.REGEX))
        } else {
            literalTriggers.distinct().map { MatchTrigger(it, TriggerKind.TEXT) }
        }
        if (triggers.isEmpty()) {
            issues += CompatibilityIssue(CompatibilitySeverity.ERROR, source, "Match has no trigger.")
            return null
        }

        val parsedVariables = parseVariables(map["vars"], "$source vars", issues).toMutableList()
        var runtimeCompatibility = if (unknownFields.isEmpty()) {
            RuntimeCompatibility.PORTABLE
        } else {
            RuntimeCompatibility.DESKTOP_ONLY
        }
        var sourceEditMode = if (unknownFields.isEmpty()) SourceEditMode.VISUAL else SourceEditMode.SOURCE_ONLY
        val replacement = when {
            map.containsKey("replace") -> stringValue(map["replace"])
            map["form"] is String -> {
                val form = map["form"].toString()
                val formName = uniqueVariableName("form1", parsedVariables)
                val fields = formFieldsToJson(map["form_fields"], form, source, issues)
                parsedVariables += TemplateVariable(
                    name = formName,
                    type = "form",
                    paramsJson = JSONObject().put("layout", form).apply {
                        if (fields.length() > 0) put("fields", fields)
                    }.toString(),
                )
                espansoFormToReplacement(form, formName)
            }
            else -> {
                val kind = listOf("html", "markdown", "image_path").firstOrNull { map[it] != null }
                val message = if (kind == null) {
                    "Match has no textual replacement."
                } else {
                    "$kind replacements are not supported on Android."
                }
                issues += CompatibilityIssue(CompatibilitySeverity.WARNING, source, message)
                runtimeCompatibility = RuntimeCompatibility.DESKTOP_ONLY
                sourceEditMode = SourceEditMode.SOURCE_ONLY
                stringValue(kind?.let(map::get))
            }
        }

        val localVariables = parsedVariables
        localVariables.map { it.type.lowercase(Locale.ROOT) }
            .filterNot { it in PORTABLE_VARIABLE_TYPES }
            .distinct()
            .forEach { type ->
                issues += CompatibilityIssue(
                    CompatibilitySeverity.WARNING,
                    source,
                    "Variable type '$type' is retained as data but is not executed on Android.",
                )
                runtimeCompatibility = RuntimeCompatibility.DESKTOP_ONLY
                sourceEditMode = SourceEditMode.SOURCE_ONLY
            }
        regexPortabilityIssue(regex)?.let { problem ->
            issues += CompatibilityIssue(CompatibilitySeverity.WARNING, source, problem)
            runtimeCompatibility = RuntimeCompatibility.DESKTOP_ONLY
            sourceEditMode = SourceEditMode.SOURCE_ONLY
        }

        val word = booleanValue(map["word"])
        val propagateCase = booleanValue(map["propagate_case"])
        val explicitCaseSensitive = map["case_sensitive"] ?: map["caseSensitive"]
        val caseSensitive = when {
            propagateCase -> false
            explicitCaseSensitive != null -> booleanValue(explicitCaseSensitive, default = true)
            else -> parseCaseSensitive(map)
        }

        return TextMatch(
            triggers = triggers,
            replacements = listOf(replacement),
            label = stringValue(map["label"]),
            searchTerms = stringList(map["search_terms"]).toSet(),
            enabled = if (map.containsKey("enabled")) booleanValue(map["enabled"], true)
            else !booleanValue(map["disabled"]),
            options = MatchOptions(
                caseSensitive = caseSensitive,
                activation = TriggerActivation.IMMEDIATE,
                leftWord = word || booleanValue(map["left_word"]),
                rightWord = word || booleanValue(map["right_word"]),
                propagateCase = propagateCase,
                uppercaseStyle = parseUppercaseStyle(map["uppercase_style"]),
            ),
            vars = localVariables,
            compatibilityWarnings = issues.drop(issueStart).map(CompatibilityIssue::message).distinct(),
            runtimeCompatibility = runtimeCompatibility,
            sourceEditMode = sourceEditMode,
            sourceMatchIndex = index,
        )
    }

    private fun exportVariable(variable: TemplateVariable): Map<String, Any?> = linkedMapOf(
        "name" to variable.name,
        "type" to variable.type,
        "params" to jsonToMap(variable.paramsJson),
    ).also { map ->
        if (variable.dependsOn.isNotEmpty()) map["depends_on"] = variable.dependsOn
        if (!variable.injectVars) map["inject_vars"] = false
    }

    private fun exportMatch(
        match: TextMatch,
        issues: MutableList<CompatibilityIssue>,
    ): List<Map<String, Any?>> {
        val source = match.label.ifBlank { match.trigger }
        if (match.triggers.isEmpty()) {
            issues += CompatibilityIssue(CompatibilitySeverity.ERROR, source, "Match has no trigger to export.")
            return emptyList()
        }
        if (match.replacements.isEmpty()) {
            issues += CompatibilityIssue(CompatibilitySeverity.ERROR, source, "Match has no replacement to export.")
            return emptyList()
        }

        val base = linkedMapOf<String, Any?>()
        val regexTriggers = match.triggers.filter { it.kind == TriggerKind.REGEX }
        val literalTriggers = match.triggers.filter { it.kind == TriggerKind.TEXT }.map(MatchTrigger::pattern)
        if (regexTriggers.isNotEmpty()) {
            val regex = normalizeEspansoNamedGroups(regexTriggers.first().pattern)
            regexPortabilityIssue(regex)?.let { problem ->
                issues += CompatibilityIssue(CompatibilitySeverity.ERROR, source, problem)
            }
            base["regex"] = regex
            if (regexTriggers.size > 1 || literalTriggers.isNotEmpty()) {
                issues += CompatibilityIssue(
                    CompatibilitySeverity.WARNING,
                    source,
                    "Espanso accepts one regex trigger; other trigger variants were omitted.",
                )
            }
        } else if (literalTriggers.size == 1) {
            base["trigger"] = literalTriggers.first()
        } else if (literalTriggers.size > 1) {
            base["triggers"] = literalTriggers
        }

        if (match.label.isNotBlank()) base["label"] = match.label
        if (match.searchTerms.isNotEmpty()) {
            base["search_terms"] = match.searchTerms.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
        val options = match.options
        if (options.leftWord && options.rightWord) {
            base["word"] = true
        } else {
            if (options.leftWord) base["left_word"] = true
            if (options.rightWord) base["right_word"] = true
        }
        if (options.propagateCase) {
            base["propagate_case"] = true
            if (options.uppercaseStyle != UppercaseStyle.CAPITALIZE) {
                base["uppercase_style"] = when (options.uppercaseStyle) {
                    UppercaseStyle.CAPITALIZE_WORDS -> "capitalize_words"
                    UppercaseStyle.UPPERCASE -> "uppercase"
                    UppercaseStyle.CAPITALIZE -> "capitalize"
                }
            }
        } else if (options.caseSensitive) {
            val literalTriggers = match.triggers
                .filter { it.kind == TriggerKind.TEXT }
                .map(MatchTrigger::pattern)
            if (literalTriggers.any { pattern -> pattern.any(Char::isUpperCase) }) {
                base["case_sensitive"] = true
            }
        }
        if (match.tags.isNotEmpty()) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.WARNING,
                source,
                "Expanda tags cannot be represented in an Espanso match.",
            )
        }
        if (match.excludedPackages.isNotEmpty()) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.WARNING,
                source,
                "Android package exclusions cannot be represented in an Espanso match.",
            )
        }
        if (!match.enabled) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.WARNING,
                source,
                "Disabled Expanda matches cannot be represented in an Espanso match.",
            )
        }
        if (options.activation == TriggerActivation.DELIMITER) {
            issues += CompatibilityIssue(
                CompatibilitySeverity.INFO,
                source,
                "Expanda delimiter activation has no exact per-match Espanso equivalent.",
            )
        }

        val templates = match.replacements
        return when (match.selectionMode) {
            TemplateSelectionMode.FIRST -> {
                if (templates.size > 1) warnMultipleTemplates(source, issues)
                listOf(mergeTemplate(base.toMutableMap(), templates.first(), match.vars))
            }
            TemplateSelectionMode.RANDOM -> {
                val randomName = uniqueVariableName("expanda_variant", match.vars)
                val randomVariable = TemplateVariable(
                    name = randomName,
                    type = "random",
                    paramsJson = JSONObject().put("choices", JSONArray(templates)).toString(),
                )
                listOf(
                    mergeTemplate(
                        base.toMutableMap(),
                        "{{${randomName}}}",
                        match.vars + randomVariable,
                    ),
                )
            }
            TemplateSelectionMode.MANUAL -> {
                val choiceName = uniqueVariableName("expanda_variant", match.vars)
                val choiceVariable = TemplateVariable(
                    name = choiceName,
                    type = "choice",
                    paramsJson = JSONObject().put("values", JSONArray(templates)).toString(),
                )
                listOf(
                    mergeTemplate(
                        base.toMutableMap(),
                        "{{${choiceName}}}",
                        match.vars + choiceVariable,
                    ),
                )
            }
            TemplateSelectionMode.SEQUENTIAL -> {
                if (templates.size > 1) {
                    issues += CompatibilityIssue(
                        CompatibilitySeverity.WARNING,
                        source,
                        "Sequential replacement selection is not portable; only the first replacement was exported.",
                    )
                }
                listOf(mergeTemplate(base.toMutableMap(), templates.first(), match.vars))
            }
        }
    }

    private fun warnMultipleTemplates(source: String, issues: MutableList<CompatibilityIssue>) {
        issues += CompatibilityIssue(
            CompatibilitySeverity.WARNING,
            source,
            "Only the first replacement was exported because this Espanso match has no selection strategy.",
        )
    }

    private fun mergeTemplate(
        base: MutableMap<String, Any?>,
        template: String,
        variables: List<TemplateVariable>,
    ): MutableMap<String, Any?> {
        // The canonical model already stores Espanso syntax. Do not reinterpret
        // replacement text during export: `$|$` stays a cursor marker,
        // `{{name}}` stays a variable reference, and every other brace is literal.
        base["replace"] = template
        base.remove("form")
        val distinctVariables = variables.distinctBy(TemplateVariable::name)
        if (distinctVariables.isEmpty()) {
            base.remove("vars")
        } else {
            base["vars"] = distinctVariables.map(::variableToMap)
        }
        return base
    }

    private fun variableToMap(variable: TemplateVariable): Map<String, Any?> =
        linkedMapOf<String, Any?>("name" to variable.name, "type" to variable.type).apply {
            val params = jsonToMap(variable.paramsJson)
            if (params.isNotEmpty()) put("params", params)
            if (variable.dependsOn.isNotEmpty()) put("depends_on", variable.dependsOn)
            if (!variable.injectVars) put("inject_vars", false)
        }

    private fun parseVariables(
        raw: Any?,
        source: String,
        issues: MutableList<CompatibilityIssue>,
    ): List<TemplateVariable> {
        if (raw == null) return emptyList()
        val list = raw as? List<*>
        if (list == null) {
            issues += CompatibilityIssue(CompatibilitySeverity.WARNING, source, "Variables must be a list.")
            return emptyList()
        }
        return list.mapIndexedNotNull { index, item ->
            val map = item.asStringMap()
            if (map == null) {
                issues += CompatibilityIssue(
                    CompatibilitySeverity.WARNING,
                    "$source #${index + 1}",
                    "Variable is not an object.",
                )
                null
            } else {
                val name = stringValue(map["name"])
                val type = stringValue(map["type"])
                if (name.isBlank() || type.isBlank()) {
                    issues += CompatibilityIssue(
                        CompatibilitySeverity.WARNING,
                        "$source #${index + 1}",
                        "Variable needs name and type.",
                    )
                    null
                } else {
                    map.keys.filterNot { it in VARIABLE_FIELDS }.forEach { field ->
                        issues += CompatibilityIssue(
                            CompatibilitySeverity.WARNING,
                            "$source #${index + 1}",
                            "Variable field '$field' is not interpreted on Android.",
                        )
                    }
                    val params = map["params"]
                    val paramsJson = when {
                        params == null -> "{}"
                        params.asStringMap() != null -> mapToJson(params.asStringMap().orEmpty()).toString()
                        params is String && params.isBlank() -> "{}"
                        params is String -> runCatching { JSONObject(params).toString() }.getOrElse {
                            issues += CompatibilityIssue(
                                CompatibilitySeverity.WARNING,
                                "$source #${index + 1}",
                                "Variable params are not an object and were ignored.",
                            )
                            "{}"
                        }
                        else -> {
                            issues += CompatibilityIssue(
                                CompatibilitySeverity.WARNING,
                                "$source #${index + 1}",
                                "Variable params are not an object and were ignored.",
                            )
                            "{}"
                        }
                    }
                    TemplateVariable(
                        name = name,
                        type = type,
                        paramsJson = paramsJson,
                        dependsOn = stringList(map["depends_on"] ?: map["dependsOn"]),
                        injectVars = booleanValue(map["inject_vars"] ?: map["injectVars"], true),
                    )
                }
            }
        }
    }

    private fun mergeVariables(
        global: List<TemplateVariable>,
        local: List<TemplateVariable>,
    ): List<TemplateVariable> = (global + local).associateBy(TemplateVariable::name).values.toList()

    private fun espansoFormToReplacement(value: String, variableName: String): String =
        SIMPLE_FORM_FIELD.replace(value) { match ->
            val fieldName = match.groupValues[1].substringBefore('=').trim()
            if (fieldName.isBlank()) match.value else "{{${variableName}.${fieldName}}}"
        }

    private fun formFieldsToJson(
        raw: Any?,
        layout: String,
        source: String,
        issues: MutableList<CompatibilityIssue>,
    ): JSONObject {
        val definitions = raw.asStringMap().orEmpty().toMutableMap()
        SIMPLE_FORM_FIELD.findAll(layout).forEach { match ->
            val expression = match.groupValues[1].trim()
            val name = expression.substringBefore('=').trim()
            val inlineDefault = expression.substringAfter('=', "").trim()
            if (name.isNotBlank() && inlineDefault.isNotBlank() && definitions[name] == null) {
                definitions[name] = linkedMapOf("default" to inlineDefault)
            }
        }
        raw?.let {
            if (it.asStringMap() == null) {
                issues += CompatibilityIssue(
                    CompatibilitySeverity.WARNING,
                    source,
                    "form_fields must be an object.",
                )
            }
        }
        return mapToJson(definitions)
    }

    private fun parseCaseSensitive(map: Map<String, Any?>): Boolean {
        val explicit = map["case_sensitive"] ?: map["caseSensitive"]
        if (explicit != null) return booleanValue(explicit)
        return when (stringValue(map["case"]).lowercase(Locale.ROOT)) {
            "sensitive", "case_sensitive" -> true
            else -> true
        }
    }

    private fun normalizeEspansoNamedGroups(pattern: String): String =
        JAVA_NAMED_GROUP.replace(pattern) { match -> "(?P<${match.groupValues[1]}>" }

    private fun regexPortabilityIssue(pattern: String): String? = when {
        REGEX_LOOKAROUND.containsMatchIn(pattern) ->
            "Regex look-around is not supported by Espanso's regex engine."
        REGEX_BACK_REFERENCE.containsMatchIn(pattern) ->
            "Regex back-references are not supported by Espanso's regex engine."
        REGEX_ATOMIC_OR_CONDITIONAL.containsMatchIn(pattern) ->
            "Atomic or conditional regex groups are not supported by Espanso's regex engine."
        hasPossessiveQuantifier(pattern) ->
            "Possessive regex quantifiers are not supported by Espanso's regex engine."
        else -> null
    }

    /** Avoids relying on host-specific regex parsing while inspecting regex syntax. */
    private fun hasPossessiveQuantifier(pattern: String): Boolean {
        pattern.indices.forEach { index ->
            if (pattern[index] != '+' || isEscaped(pattern, index) || index == 0) return@forEach
            val previous = pattern[index - 1]
            if (previous in "*+?" && !isEscaped(pattern, index - 1)) return true
            if (previous == '}' && !isEscaped(pattern, index - 1)) {
                val opening = pattern.lastIndexOf('{', index - 1)
                if (opening >= 0 && !isEscaped(pattern, opening)) {
                    val range = pattern.substring(opening + 1, index - 1)
                    val parts = range.split(',', limit = 3)
                    if (parts.size <= 2 && parts.firstOrNull()?.isNotEmpty() == true &&
                        parts.first().all(Char::isDigit) &&
                        (parts.size == 1 || parts[1].all(Char::isDigit))
                    ) return true
                }
            }
        }
        return false
    }

    private fun isEscaped(value: String, index: Int): Boolean {
        var slashCount = 0
        var cursor = index - 1
        while (cursor >= 0 && value[cursor] == '\\') {
            slashCount++
            cursor--
        }
        return slashCount % 2 == 1
    }

    private fun parseUppercaseStyle(value: Any?): UppercaseStyle = when (stringValue(value).lowercase(Locale.ROOT)) {
        "capitalize" -> UppercaseStyle.CAPITALIZE
        "capitalize_words" -> UppercaseStyle.CAPITALIZE_WORDS
        "uppercase" -> UppercaseStyle.UPPERCASE
        else -> UppercaseStyle.CAPITALIZE
    }

    private fun uniqueVariableName(base: String, variables: List<TemplateVariable>): String {
        val used = variables.mapTo(mutableSetOf()) { it.name }
        if (base !in used) return base
        var index = 2
        while ("${base}_$index" in used) index++
        return "${base}_$index"
    }


    private fun Any?.asStringMap(): Map<String, Any?>? = (this as? Map<*, *>)?.entries
        ?.associate { (key, value) -> key.toString() to value }

    private fun stringValue(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        else -> value.toString()
    }

    private fun booleanValue(value: Any?, default: Boolean = false): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.lowercase(Locale.ROOT)) {
            "true", "1", "yes", "y" -> true
            "false", "0", "no", "n" -> false
            else -> default
        }
        else -> default
    }

    private fun stringList(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is List<*> -> value.mapNotNull { stringValue(it).takeIf(String::isNotBlank) }
        is String -> listOf(value).filter(String::isNotBlank)
        else -> listOf(stringValue(value)).filter(String::isNotBlank)
    }

    private fun mapToJson(map: Map<String, Any?>): JSONObject = JSONObject().apply {
        map.forEach { (key, value) -> put(key, toJsonValue(value)) }
    }

    private fun toJsonValue(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> mapToJson(value.entries.associate { it.key.toString() to it.value })
        is List<*> -> JSONArray().apply { value.forEach { put(toJsonValue(it)) } }
        else -> value
    }

    private fun jsonToMap(value: String): Map<String, Any?> {
        if (value.isBlank()) return emptyMap()
        return runCatching { jsonObjectToMap(JSONObject(value)) }.getOrDefault(emptyMap())
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> = buildMap {
        json.keys().forEach { key -> put(key, fromJsonValue(json.opt(key))) }
    }

    private fun fromJsonValue(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> buildList(value.length()) {
            for (index in 0 until value.length()) add(fromJsonValue(value.opt(index)))
        }
        else -> value
    }

    private val SIMPLE_FORM_FIELD = Regex("""\[\[([^]{}]+)\]\]""")
    private val JAVA_NAMED_GROUP = Regex("""\(\?<([A-Za-z][A-Za-z0-9_]*)>""")
    private val REGEX_LOOKAROUND = Regex("""\(\?(?:[=!]|<[=!])""")
    private val REGEX_BACK_REFERENCE = Regex("""\\(?:[1-9][0-9]*|k<|k'|g<)""")
    private val REGEX_ATOMIC_OR_CONDITIONAL = Regex("""\(\?(?:>|\()""")
    private val PORTABLE_VARIABLE_TYPES = setOf("echo", "date", "choice", "random", "clipboard", "form", "match")
    private val ROOT_FIELDS = setOf("matches", "global_vars", "imports", "anchors")
    private val SUPPORTED_MATCH_FIELDS = setOf(
        "trigger", "triggers", "regex", "replace", "form", "form_fields", "label", "search_terms", "vars",
        "word", "left_word", "right_word", "propagate_case", "uppercase_style",
        "case_sensitive", "caseSensitive", "case", "enabled", "disabled",
    )
    private val VARIABLE_FIELDS = setOf(
        "name", "type", "params", "depends_on", "dependsOn", "inject_vars", "injectVars",
    )
}

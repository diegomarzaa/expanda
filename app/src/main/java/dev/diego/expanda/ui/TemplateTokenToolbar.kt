package dev.diego.expanda.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DynamicForm
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.diego.expanda.data.TemplateVariable
import dev.diego.expanda.data.formFieldInlineDefaults
import dev.diego.expanda.data.formFieldNames
import dev.diego.expanda.data.removeFormFieldPlaceholder
import dev.diego.expanda.data.setFormFieldInlineDefault
import dev.diego.expanda.data.isEspansoWord
import dev.diego.expanda.data.TextMatch
import dev.diego.expanda.engine.ChoiceLists
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

internal enum class TemplateVariableKind(
    val label: String,
    val type: String,
    val defaultName: String,
    val icon: ImageVector,
) {
    TEXT("Text", "echo", "text", Icons.Default.TextFields),
    DATE("Date/time", "date", "date", Icons.Default.DateRange),
    CLIPBOARD("Clipboard", "clipboard", "clipboard", Icons.Default.ContentPaste),
    RANDOM("Random", "random", "random", Icons.Default.Casino),
    CHOICE("Choice", "choice", "choice", Icons.Default.Checklist),
    FORM("Form", "form", "form", Icons.Default.DynamicForm),
    MATCH("Snippet", "match", "match", Icons.AutoMirrored.Filled.CallMerge),
    ;

    companion object {
        fun from(variable: TemplateVariable): TemplateVariableKind =
            entries.firstOrNull {
                it.type.equals(variable.type, ignoreCase = true)
            } ?: TEXT
    }
}

private sealed interface ToolbarDialog {
    data class Edit(
        val variable: TemplateVariable? = null,
        val kind: TemplateVariableKind = TemplateVariableKind.TEXT,
        val global: Boolean = false,
        val insertAfterSave: Boolean = false,
    ) : ToolbarDialog

    data object Variables : ToolbarDialog
    data object Capture : ToolbarDialog
}

private enum class RandomAlphabetPreset(
    val label: String,
    val alphabet: String?,
) {
    LETTERS(
        "Letters",
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
    ),
    NUMBERS(
        "Numbers",
        "0123456789",
    ),
    ALPHANUMERIC(
        "Letters + numbers",
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
    ),
    CUSTOM(
        "Custom",
        null,
    ),
}

private data class DateFormatPreset(
    val label: String,
    val format: String,
)

private val DATE_FORMAT_PRESETS = listOf(
    DateFormatPreset("ISO date", "%Y-%m-%d"),
    DateFormatPreset("Numeric date", "%d/%m/%Y"),
    DateFormatPreset("Short month", "%d %b %Y"),
    DateFormatPreset("Long date", "%d %B %Y"),
    DateFormatPreset("Weekday", "%A, %d %B"),
    DateFormatPreset("Time", "%H:%M"),
    DateFormatPreset("Date + time", "%d/%m/%Y %H:%M"),
)

private enum class DateOffsetUnit(
    val label: String,
    val seconds: Long,
) {
    DAYS("Days", 86_400L),
    HOURS("Hours", 3_600L),
    MINUTES("Minutes", 60L),
    SECONDS("Seconds", 1L),
}

private enum class FormFieldKind(
    val label: String,
    val type: String,
    val multiline: Boolean = false,
) {
    TEXT("Text", "text"),
    MULTILINE("Long text", "text", multiline = true),
    CHOICE("Choice", "choice"),
    DATE("Date", "date"),
    TIME("Time", "time"),
}

private data class FormFieldConfig(
    val kind: FormFieldKind = FormFieldKind.TEXT,
    val defaultValue: String = "",
    val options: List<String> = emptyList(),
)

/**
 * The replacement palette.
 *
 * Each configurable variable type opens its relevant editor.
 * Clipboard is special because it has no configuration:
 * pressing it directly inserts/reuses a clipboard variable.
 */
@Composable
fun TemplateTokenToolbar(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
    snippets: List<TextMatch> = emptyList(),
    variables: List<TemplateVariable> = emptyList(),
    globalVariables: List<TemplateVariable> = emptyList(),
    onVariablesChanged: (List<TemplateVariable>) -> Unit = {},
    onGlobalVariablesChanged: (List<TemplateVariable>) -> Unit = {},
    onVariableReferencesChanged: (oldName: String, newName: String?) -> Unit = { _, _ -> },
    regexPattern: String? = null,
) {
    var dialog by remember {
        mutableStateOf<ToolbarDialog?>(null)
    }

    val creatableKinds = listOf(
        TemplateVariableKind.TEXT,
        TemplateVariableKind.DATE,
        TemplateVariableKind.RANDOM,
        TemplateVariableKind.CHOICE,
        TemplateVariableKind.FORM,
        TemplateVariableKind.MATCH,
    )

    val directTokens = listOf(
        TokenButton(
            "Cursor",
            "$|$",
            Icons.Default.TouchApp,
        ),
        TokenButton(
            "New line",
            "\n",
            Icons.AutoMirrored.Filled.KeyboardReturn,
        ),
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        //Text(
        //    "Create variable",
        //    style = MaterialTheme.typography.labelMedium,
        //    color = MaterialTheme.colorScheme.onSurfaceVariant,
        //)

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            creatableKinds.forEach { kind ->
                TokenChip(kind.label, kind.icon) {
                    dialog = ToolbarDialog.Edit(
                        kind = kind,
                        insertAfterSave = true,
                    )
                }
            }
        }

        //Text(
        //    "Insert",
        //    style = MaterialTheme.typography.labelMedium,
        //    color = MaterialTheme.colorScheme.onSurfaceVariant,
        //)

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (regexPattern != null) {
                TokenChip("Capture", Icons.Default.DataObject) {
                    dialog = ToolbarDialog.Capture
                }
            }

            TokenChip("Variables", Icons.Default.AddLink) {
                dialog = ToolbarDialog.Variables
            }

            TokenChip("Clipboard", Icons.Default.ContentPaste) {
                val existingClipboard =
                    (variables + globalVariables).firstOrNull {
                        TemplateVariableKind.from(it) ==
                            TemplateVariableKind.CLIPBOARD
                    }

                if (existingClipboard != null) {
                    onInsert("{{${existingClipboard.name}}}")
                } else {
                    val clipboard = createClipboardVariable(
                        reservedVariables = variables + globalVariables,
                    )

                    onVariablesChanged(variables + clipboard)
                    onInsert("{{${clipboard.name}}}")
                }
            }

            directTokens.forEach { item ->
                TokenChip(item.label, item.icon) {
                    onInsert(item.token)
                }
            }
        }
    }

    when (val current = dialog) {
        is ToolbarDialog.Edit -> {
            TemplateVariableEditorDialog(
                variable = current.variable,
                initialKind = current.kind,
                initialGlobal = current.global,
                reservedVariables = variables + globalVariables,
                snippets = snippets,
                onDismiss = {
                    dialog = null
                },
                onSave = { original, updated, saveAsGlobal ->
                    val wasGlobal = current.global

                    when {
                        original == null && saveAsGlobal -> {
                            onGlobalVariablesChanged(
                                globalVariables + updated,
                            )
                        }

                        original == null -> {
                            onVariablesChanged(
                                variables + updated,
                            )
                        }

                        wasGlobal && saveAsGlobal -> {
                            onGlobalVariablesChanged(
                                upsertVariable(
                                    globalVariables,
                                    original,
                                    updated,
                                ),
                            )
                        }

                        !wasGlobal && !saveAsGlobal -> {
                            onVariablesChanged(
                                upsertVariable(
                                    variables,
                                    original,
                                    updated,
                                ),
                            )
                        }

                        wasGlobal -> {
                            onGlobalVariablesChanged(
                                globalVariables.filterNot {
                                    it.name == original.name
                                },
                            )

                            onVariablesChanged(
                                variables + updated,
                            )
                        }

                        else -> {
                            onVariablesChanged(
                                variables.filterNot {
                                    it.name == original.name
                                },
                            )

                            onGlobalVariablesChanged(
                                globalVariables + updated,
                            )
                        }
                    }

                    if (
                        original != null &&
                        original.name != updated.name
                    ) {
                        onVariableReferencesChanged(
                            original.name,
                            updated.name,
                        )
                    }

                    if (current.insertAfterSave) {
                        onInsert(
                            "{{${updated.name}}}",
                        )
                    }

                    dialog = null
                },
                onDelete = current.variable?.let { original ->
                    {
                        if (current.global) {
                            onGlobalVariablesChanged(
                                globalVariables.filterNot {
                                    it.name == original.name
                                },
                            )
                        } else {
                            onVariablesChanged(
                                variables.filterNot {
                                    it.name == original.name
                                },
                            )
                        }

                        onVariableReferencesChanged(
                            original.name,
                            null,
                        )

                        dialog = null
                    }
                },
            )
        }

        ToolbarDialog.Variables -> {
            VariablesDialog(
                localVariables = variables,
                globalVariables = globalVariables,
                onDismiss = {
                    dialog = null
                },
                onInsert = { variable ->
                    onInsert("{{${variable.name}}}")
                    dialog = null
                },
                onEdit = { variable, global ->
                    dialog = ToolbarDialog.Edit(
                        variable = variable,
                        kind = TemplateVariableKind.from(variable),
                        global = global,
                    )
                },
            )
        }

        ToolbarDialog.Capture -> {
            RegexCaptureEditorDialog(
                pattern = regexPattern.orEmpty(),
                onDismiss = {
                    dialog = null
                },
                onSave = { reference ->
                    onInsert(
                        "{{$reference}}",
                    )

                    dialog = null
                },
            )
        }

        null -> Unit
    }
}

@Composable
internal fun TemplateVariableEditorDialog(
    variable: TemplateVariable?,
    initialKind: TemplateVariableKind =
        variable?.let(TemplateVariableKind::from)
            ?: TemplateVariableKind.TEXT,
    initialGlobal: Boolean,
    reservedVariables: List<TemplateVariable>,
    snippets: List<TextMatch>,
    onDismiss: () -> Unit,
    onSave: (
        original: TemplateVariable?,
        updated: TemplateVariable,
        global: Boolean,
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val originalParams = remember(variable) {
        parseParams(variable?.paramsJson)
    }

    /*
     * Variable type is intentionally fixed.
     *
     * To change type, the user creates/inserts a variable of the
     * desired type from "Insert into replacement".
     */
    val kind = remember(
        variable,
        initialKind,
    ) {
        variable?.let(TemplateVariableKind::from)
            ?: initialKind
    }

    var global by remember(variable, initialGlobal) {
        mutableStateOf(initialGlobal)
    }

    var name by remember(variable) {
        mutableStateOf(
            variable?.name.orEmpty(),
        )
    }

    var value by remember(
        variable,
        kind,
    ) {
        mutableStateOf(
            valueFor(
                kind,
                originalParams,
            ),
        )
    }

    var showAdvanced by remember(variable) {
        mutableStateOf(false)
    }

    var locale by remember(variable) {
        mutableStateOf(
            originalParams.optString(
                "locale",
            ),
        )
    }

    var timezone by remember(variable) {
        mutableStateOf(
            originalParams.optString(
                "timezone",
                originalParams.optString("tz"),
            ),
        )
    }

    val initialOffsetSeconds = remember(variable) {
        originalParams.optLong("offset", 0L)
    }

    val initialOffsetUnit = remember(variable) {
        preferredDateOffsetUnit(initialOffsetSeconds)
    }

    var offsetUnit by remember(variable) {
        mutableStateOf(initialOffsetUnit)
    }

    var offsetAmount by remember(variable) {
        mutableStateOf(
            formatDateOffsetAmount(
                initialOffsetSeconds,
                initialOffsetUnit,
            ),
        )
    }

    var dateFormatMenuExpanded by remember(variable) {
        mutableStateOf(false)
    }

    var customDateFormat by remember(variable, kind) {
        mutableStateOf(
            DATE_FORMAT_PRESETS.none {
                it.format == value
            },
        )
    }

    var showDateAdvanced by remember(variable) {
        mutableStateOf(
            originalParams.optString("locale").isNotBlank() ||
                originalParams.optString(
                    "timezone",
                    originalParams.optString("tz"),
                ).isNotBlank(),
        )
    }

    val offsetValid =
        offsetAmount.isBlank() ||
            offsetAmount.toDoubleOrNull() != null

    val dateOffsetSeconds =
        (
            (offsetAmount.toDoubleOrNull() ?: 0.0) *
                offsetUnit.seconds.toDouble()
            ).roundToLong()

    val timezoneValid =
        timezone.isBlank() ||
            runCatching {
                ZoneId.of(timezone.trim())
            }.isSuccess

    var randomLength by remember(variable) {
        mutableStateOf(
            originalParams
                .optInt(
                    "length",
                    12,
                )
                .toString(),
        )
    }

    var randomFromList by remember(variable) {
        mutableStateOf(
            ChoiceLists.hasChoicesParam(originalParams),
        )
    }

    var randomAlphabet by remember(variable) {
        mutableStateOf(
            originalParams.optString(
                "alphabet",
                RandomAlphabetPreset
                    .ALPHANUMERIC
                    .alphabet
                    .orEmpty(),
            ),
        )
    }

    var randomAlphabetPreset by remember(variable) {
        mutableStateOf(
            RandomAlphabetPreset.entries.firstOrNull {
                it.alphabet != null &&
                    it.alphabet == originalParams.optString(
                        "alphabet",
                    )
            } ?: if (
                originalParams.has("alphabet")
            ) {
                RandomAlphabetPreset.CUSTOM
            } else {
                RandomAlphabetPreset.ALPHANUMERIC
            },
        )
    }

    var formFieldConfigs by remember(variable) {
        mutableStateOf(
            mergedFormFieldConfigs(
                layout = valueFor(TemplateVariableKind.FORM, originalParams),
                fields = originalParams.optJSONObject("fields"),
            ),
        )
    }

    var newFormFieldName by remember(variable) {
        mutableStateOf("")
    }

    var injectVars by remember(variable) {
        mutableStateOf(
            variable?.injectVars ?: true,
        )
    }

    LaunchedEffect(value) {
        if (kind != TemplateVariableKind.FORM) return@LaunchedEffect
        val inlineDefaults = formFieldInlineDefaults(value)
        if (inlineDefaults.isEmpty()) return@LaunchedEffect
        formFieldConfigs = formFieldConfigs.mapValues { (name, config) ->
            inlineDefaults[name]?.let { config.copy(defaultValue = it) } ?: config
        }
    }

    val reservedNames =
        reservedVariables
            .asSequence()
            .filterNot {
                it.name == variable?.name
            }
            .map {
                it.name.lowercase()
            }
            .toSet()

    val suggestedName =
        uniqueVariableName(
            kind.defaultName,
            reservedNames,
        )

    val normalizedName =
        name
            .trim()
            .ifBlank {
                suggestedName
            }

    val explicitNameValid =
        name.isBlank() || isEspansoWord(name.trim())

    val duplicate =
        normalizedName.lowercase() in
            reservedNames

    val containsNestedVariableReference = when (kind) {
        TemplateVariableKind.TEXT ->
            "{{" in value

        TemplateVariableKind.DATE ->
            "{{" in value ||
                "{{" in locale ||
                "{{" in timezone

        TemplateVariableKind.CLIPBOARD ->
            false

        TemplateVariableKind.RANDOM ->
            if (randomFromList) {
                "{{" in value
            } else {
                "{{" in randomAlphabet
            }

        TemplateVariableKind.CHOICE ->
            "{{" in value

        TemplateVariableKind.FORM ->
            "{{" in value ||
                formFieldConfigs.values.any { config ->
                    "{{" in config.defaultValue ||
                        config.options.any { "{{" in it }
                }

        TemplateVariableKind.MATCH ->
            "{{" in value
    }

    val contentValid =
        when (kind) {
            TemplateVariableKind.FORM,
            TemplateVariableKind.MATCH,
            -> value.isNotBlank()

            TemplateVariableKind.CHOICE -> {
                val options =
                    parseChoiceEditorOptions(value)

                options.isNotEmpty() &&
                    options.all {
                        it.label.isNotBlank() &&
                            (
                                it.insertedValue == null ||
                                    it.insertedValue.isNotBlank()
                                )
                    }
            }

            TemplateVariableKind.RANDOM ->
                !randomFromList ||
                    ChoiceLists.parseEditorLines(value).isNotEmpty()

            TemplateVariableKind.DATE ->
                offsetValid && timezoneValid

            else -> true
        }

    fun buildVariable(): TemplateVariable {
        val params =
            when (kind) {
                TemplateVariableKind.TEXT ->
                    JSONObject()
                        .put(
                            "echo",
                            value,
                        )

                TemplateVariableKind.DATE ->
                    JSONObject()
                        .put(
                            "format",
                            value.ifBlank {
                                "%Y-%m-%d"
                            },
                        )
                        .put(
                            "offset",
                            dateOffsetSeconds,
                        )
                        .apply {
                            if (locale.isNotBlank()) {
                                put(
                                    "locale",
                                    locale.trim(),
                                )
                            }

                            if (timezone.isNotBlank()) {
                                put(
                                    "timezone",
                                    timezone.trim(),
                                )
                            }
                        }

                TemplateVariableKind.CLIPBOARD ->
                    JSONObject()

                TemplateVariableKind.RANDOM -> {
                    val choices = ChoiceLists.parseEditorLines(value)

                    if (!randomFromList) {
                        JSONObject()
                            .put(
                                "length",
                                randomLength
                                    .toIntOrNull()
                                    ?.coerceIn(
                                        1,
                                        256,
                                    )
                                    ?: 12,
                            )
                            .put(
                                "alphabet",
                                randomAlphabet.ifBlank {
                                    RandomAlphabetPreset
                                        .ALPHANUMERIC
                                        .alphabet
                                        .orEmpty()
                                },
                            )
                    } else {
                        JSONObject()
                            .put(
                                "choices",
                                JSONArray(
                                    choices,
                                ),
                            )
                    }
                }

                TemplateVariableKind.CHOICE ->
                    JSONObject()
                        .put(
                            "values",
                            choiceValuesJson(
                                lines(value),
                            ),
                        )

                TemplateVariableKind.FORM ->
                    run {
                        var layout = value
                        formFieldNames(layout).forEach { fieldName ->
                            val config = formFieldConfigs[fieldName] ?: FormFieldConfig()
                            layout = setFormFieldInlineDefault(layout, fieldName, config.defaultValue)
                        }
                        val inlineDefaults = formFieldInlineDefaults(layout)
                        JSONObject()
                            .put("layout", layout)
                            .apply {
                                val fields = JSONObject()

                                formFieldNames(layout).forEach { fieldName ->
                                    val config =
                                        formFieldConfigs[fieldName]
                                            ?: FormFieldConfig()

                                    fields.put(
                                        fieldName,
                                        JSONObject().apply {
                                            if (
                                                config.kind.type !=
                                                "text"
                                            ) {
                                                put(
                                                    "type",
                                                    config.kind.type,
                                                )
                                            }

                                            if (
                                                config.kind.multiline
                                            ) {
                                                put(
                                                    "multiline",
                                                    true,
                                                )
                                            }

                                            if (
                                                config.defaultValue.isNotBlank() &&
                                                inlineDefaults[fieldName] != config.defaultValue
                                            ) {
                                                put(
                                                    "default",
                                                    config.defaultValue,
                                                )
                                            }

                                            if (
                                                config.kind ==
                                                FormFieldKind.CHOICE
                                            ) {
                                                put(
                                                    "values",
                                                    JSONArray(
                                                        config.options,
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                }

                                if (fields.length() > 0) {
                                    put(
                                        "fields",
                                        fields,
                                    )
                                }
                            }
                    }

                TemplateVariableKind.MATCH ->
                    JSONObject()
                        .put(
                            "trigger",
                            value.trim(),
                        )
            }

        return TemplateVariable(
            name = normalizedName,
            type = kind.type,
            paramsJson = params.toString(),
            dependsOn = variable
                ?.dependsOn
                .orEmpty(),
            injectVars = injectVars,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (variable == null) {
                    "New ${kind.label.lowercase()} variable"
                } else {
                    "Edit {{${variable.name}}}"
                },
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        max = 540.dp,
                    )
                    .verticalScroll(
                        rememberScrollState(),
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                when (kind) {
                    TemplateVariableKind.TEXT -> {
                        OutlinedTextField(
                            value = value,
                            onValueChange = {
                                value = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Text")
                            },
                            placeholder = {
                                Text("Text to insert")
                            },
                            minLines = 3,
                        )
                    }

                    TemplateVariableKind.DATE -> {
                        val selectedPreset =
                            DATE_FORMAT_PRESETS.firstOrNull {
                                it.format == value
                            }

                        val effectiveFormat =
                            value.ifBlank { "%Y-%m-%d" }

                        Text(
                            "Format",
                            style = MaterialTheme.typography.labelLarge,
                        )

                        Box(
                            Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    dateFormatMenuExpanded = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start,
                                ) {
                                    Text(
                                        if (customDateFormat) {
                                            "Custom"
                                        } else {
                                            selectedPreset?.label ?: "Custom"
                                        },
                                    )

                                    if (!customDateFormat && selectedPreset != null) {
                                        Text(
                                            previewEspansoDateFormat(
                                                selectedPreset.format,
                                                dateOffsetSeconds,
                                                locale,
                                                timezone,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Text("v")
                            }

                            DropdownMenu(
                                expanded = dateFormatMenuExpanded,
                                onDismissRequest = {
                                    dateFormatMenuExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                DATE_FORMAT_PRESETS.forEach { preset ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(preset.label)

                                                Text(
                                                    previewEspansoDateFormat(
                                                        preset.format,
                                                        dateOffsetSeconds,
                                                        locale,
                                                        timezone,
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            value = preset.format
                                            customDateFormat = false
                                            dateFormatMenuExpanded = false
                                        },
                                    )
                                }

                                DropdownMenuItem(
                                    text = {
                                        Text("Custom format...")
                                    },
                                    onClick = {
                                        customDateFormat = true
                                        dateFormatMenuExpanded = false
                                    },
                                )
                            }
                        }

                        if (customDateFormat) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = {
                                    value = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text("Custom format")
                                },
                                supportingText = {
                                    Text(
                                        "Uses Espanso/strftime syntax, for example %Y-%m-%d or %H:%M.",
                                    )
                                },
                                singleLine = true,
                            )
                        }

                        Text(
                            "Offset",
                            style = MaterialTheme.typography.labelLarge,
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = dateOffsetSeconds == -86_400L,
                                    onClick = {
                                        offsetUnit = DateOffsetUnit.DAYS
                                        offsetAmount = "-1"
                                    },
                                    label = {
                                        Text("Yesterday")
                                    },
                                )
                            }

                            item {
                                FilterChip(
                                    selected = dateOffsetSeconds == 0L,
                                    onClick = {
                                        offsetUnit = DateOffsetUnit.DAYS
                                        offsetAmount = "0"
                                    },
                                    label = {
                                        Text("Today")
                                    },
                                )
                            }

                            item {
                                FilterChip(
                                    selected = dateOffsetSeconds == 86_400L,
                                    onClick = {
                                        offsetUnit = DateOffsetUnit.DAYS
                                        offsetAmount = "1"
                                    },
                                    label = {
                                        Text("Tomorrow")
                                    },
                                )
                            }
                        }

                        OutlinedTextField(
                            value = offsetAmount,
                            onValueChange = {
                                offsetAmount = sanitizeDateOffsetInput(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Custom offset")
                            },
                            isError = !offsetValid,
                            supportingText =
                                if (!offsetValid) {
                                    {
                                        Text("Enter a valid number.")
                                    }
                                } else {
                                    null
                                },
                            singleLine = true,
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(DateOffsetUnit.entries) { unit ->
                                FilterChip(
                                    selected = offsetUnit == unit,
                                    onClick = {
                                        val currentSeconds = dateOffsetSeconds

                                        offsetUnit = unit

                                        offsetAmount =
                                            formatDateOffsetAmount(
                                                currentSeconds,
                                                unit,
                                            )
                                    },
                                    label = {
                                        Text(unit.label)
                                    },
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                showDateAdvanced = !showDateAdvanced
                            },
                        ) {
                            Text(
                                if (showDateAdvanced) {
                                    "Hide advanced options"
                                } else {
                                    "More options"
                                },
                            )
                        }

                        if (showDateAdvanced) {
                            OutlinedTextField(
                                value = locale,
                                onValueChange = {
                                    locale = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text("Locale")
                                },
                                supportingText = {
                                    Text(
                                        "Leave empty to use the device language. Example: es-ES or en-US.",
                                    )
                                },
                                singleLine = true,
                            )

                            OutlinedTextField(
                                value = timezone,
                                onValueChange = {
                                    timezone = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text("Timezone")
                                },
                                supportingText = {
                                    Text(
                                        if (timezoneValid) {
                                            "Leave empty to use the device timezone. Example: Europe/Madrid."
                                        } else {
                                            "Unknown timezone. Use an IANA name such as Europe/Madrid."
                                        },
                                    )
                                },
                                isError = !timezoneValid,
                                singleLine = true,
                            )
                        }
                    }

                    /*
                     * Clipboard has no configuration.
                     *
                     * New clipboard variables do not reach this dialog,
                     * but keeping this branch allows old/global clipboard
                     * variables to still be renamed/deleted if edited.
                     */
                    TemplateVariableKind.CLIPBOARD ->
                        Unit

                    TemplateVariableKind.RANDOM -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = !randomFromList,
                                onClick = {
                                    randomFromList = false
                                },
                                label = {
                                    Text("Random text")
                                },
                            )

                            FilterChip(
                                selected = randomFromList,
                                onClick = {
                                    randomFromList = true
                                },
                                label = {
                                    Text("Random choice")
                                },
                            )
                        }

                        if (randomFromList) {
                            EditableOptionList(
                                options = lines(value),
                                onOptionsChanged = {
                                    value = it.joinToString("\n")
                                },
                                emptyText = "Add one option per line.",
                            )
                        } else {
                            OutlinedTextField(
                                value = randomLength,
                                onValueChange = {
                                    randomLength = it.filter(Char::isDigit)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text("Length")
                                },
                                singleLine = true,
                            )

                            Text(
                                "Characters",
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelLarge,
                            )

                            LazyRow(
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        8.dp,
                                    ),
                            ) {
                                items(
                                    RandomAlphabetPreset
                                        .entries,
                                ) { preset ->
                                    FilterChip(
                                        selected =
                                            randomAlphabetPreset ==
                                                preset,
                                        onClick = {
                                            randomAlphabetPreset =
                                                preset

                                            preset.alphabet
                                                ?.let {
                                                    randomAlphabet =
                                                        it
                                                }
                                        },
                                        label = {
                                            Text(
                                                preset.label,
                                            )
                                        },
                                    )
                                }
                            }

                            if (
                                randomAlphabetPreset ==
                                RandomAlphabetPreset.CUSTOM
                            ) {
                                OutlinedTextField(
                                    value = randomAlphabet,
                                    onValueChange = {
                                        randomAlphabet = it
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = {
                                        Text("Characters")
                                    },
                                )
                            }

                            val length = randomLength
                                .toIntOrNull()
                                ?.coerceIn(1, 256)
                                ?: 12

                            val preview = remember(
                                length,
                                randomAlphabet,
                            ) {
                                randomTextPreview(
                                    length = length,
                                    alphabet = randomAlphabet,
                                )
                            }

                            PreviewCard(
                                title = "Example",
                                value = preview,
                            )
                        }
                    }

                    TemplateVariableKind.CHOICE -> {
                        ChoiceEditor(
                            value = value,
                            onValueChanged = {
                                value = it
                            },
                        )
                    }

                    TemplateVariableKind.FORM -> {
                        OutlinedTextField(
                            value = value,
                            onValueChange = {
                                value = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Layout")
                            },
                            supportingText = {
                                Text(
                                    "Use [[field]] placeholders. Optional defaults: [[field=value]].",
                                )
                            },
                            minLines = 4,
                        )

                        fun addFormField() {
                            val field =
                                normalizedFormFieldName(
                                    newFormFieldName,
                                )

                            if (field.isBlank()) {
                                return
                            }

                            value =
                                value.trimEnd() +
                                    if (value.isBlank()) {
                                        "[[$field]]"
                                    } else if ("[[$field]]" in value) {
                                        ""
                                    } else {
                                        " [[$field]]"
                                    }

                            formFieldConfigs =
                                formFieldConfigs +
                                    (
                                        field to
                                            FormFieldConfig()
                                        )

                            newFormFieldName = ""
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = newFormFieldName,
                                onValueChange = {
                                    newFormFieldName = it
                                },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text("Add field")
                                },
                                placeholder = {
                                    Text("recipient")
                                },
                                singleLine = true,
                                keyboardOptions =
                                    KeyboardOptions(
                                        imeAction =
                                            ImeAction.Done,
                                    ),
                                keyboardActions =
                                    KeyboardActions(
                                        onDone = {
                                            addFormField()
                                        },
                                    ),
                            )
                            OutlinedButton(
                                enabled =
                                    normalizedFormFieldName(
                                        newFormFieldName,
                                    ).isNotBlank(),
                                onClick = {
                                    addFormField()
                                },
                            ) {
                                Text("Add")
                            }
                        }

                        val fieldNames = formFieldNames(value)
                        if (fieldNames.isNotEmpty()) {
                            Text(
                                "Field settings",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        fieldNames.forEach { fieldName ->
                                val config =
                                    formFieldConfigs[fieldName]
                                        ?: FormFieldConfig()

                                Card(
                                    Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    fieldName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                )
                                                Text(
                                                    formFieldToken(
                                                        fieldName,
                                                        config.defaultValue,
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }

                                            TextButton(
                                                onClick = {
                                                    value =
                                                        removeFormFieldPlaceholder(
                                                            value,
                                                            fieldName,
                                                        )

                                                    formFieldConfigs =
                                                        formFieldConfigs -
                                                            fieldName
                                                },
                                            ) {
                                                Text(
                                                    "Remove",
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }

                                        FormFieldTypePicker(
                                            selected = config.kind,
                                            onSelected = { kind ->
                                                formFieldConfigs =
                                                    formFieldConfigs +
                                                        (
                                                            fieldName to
                                                                config.copy(
                                                                    kind = kind,
                                                                )
                                                            )
                                            },
                                        )

                                        when (config.kind) {
                                            FormFieldKind.CHOICE -> {
                                                EditableOptionList(
                                                    options = config.options,
                                                    onOptionsChanged = { options ->
                                                        val nextDefault =
                                                            config.defaultValue.takeIf { it in options }.orEmpty()
                                                        formFieldConfigs =
                                                            formFieldConfigs +
                                                                (
                                                                    fieldName to
                                                                        config.copy(
                                                                            options = options,
                                                                            defaultValue = nextDefault,
                                                                        )
                                                                )
                                                        value = setFormFieldInlineDefault(
                                                            value,
                                                            fieldName,
                                                            nextDefault,
                                                        )
                                                    },
                                                    emptyText = "One option per line.",
                                                )
                                                if (config.options.isNotEmpty()) {
                                                    Text(
                                                        "Default choice",
                                                        style = MaterialTheme.typography.labelLarge,
                                                    )
                                                    LazyRow(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        items(config.options) { option ->
                                                            FilterChip(
                                                                selected = config.defaultValue == option,
                                                                onClick = {
                                                                    formFieldConfigs =
                                                                        formFieldConfigs +
                                                                            (
                                                                                fieldName to
                                                                                    config.copy(
                                                                                        defaultValue = option,
                                                                                    )
                                                                            )
                                                                    value = setFormFieldInlineDefault(
                                                                        value,
                                                                        fieldName,
                                                                        option,
                                                                    )
                                                                },
                                                                label = { Text(option) },
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            else -> {
                                                FormFieldDefaultEditor(
                                                    kind = config.kind,
                                                    value = config.defaultValue,
                                                    onValueChange = { defaultValue ->
                                                        formFieldConfigs =
                                                            formFieldConfigs +
                                                                (
                                                                    fieldName to
                                                                        config.copy(
                                                                            defaultValue = defaultValue,
                                                                        )
                                                                )
                                                        value = setFormFieldInlineDefault(
                                                            value,
                                                            fieldName,
                                                            defaultValue,
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                    }

                    TemplateVariableKind.MATCH -> {
                        Text(
                            "Choose a snippet",
                            style = MaterialTheme.typography.labelLarge,
                        )

                        if (snippets.isEmpty()) {
                            Text(
                                "No snippets available.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(snippets.take(20)) { match ->
                                    FilterChip(
                                        selected = value == match.trigger,
                                        onClick = {
                                            value = match.trigger
                                        },
                                        label = {
                                            Text(
                                                match.label.ifBlank {
                                                    match.trigger
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = value,
                            onValueChange = {
                                value = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Trigger")
                            },
                            supportingText = {
                                Text("Choose above, or enter a trigger manually.")
                            },
                            singleLine = true,
                        )

                        val selectedSnippet =
                            snippets.firstOrNull {
                                it.trigger == value
                            }

                        if (selectedSnippet != null) {
                            PreviewCard(
                                value = selectedSnippet.replace,
                            )
                        }
                    }
                }

                VariableAdvancedSection(
                    expanded = showAdvanced,
                    onExpandedChange = { showAdvanced = it },
                    name = name,
                    onNameChanged = {
                        name = it.filterNot(Char::isWhitespace)
                    },
                    explicitNameValid = explicitNameValid,
                    duplicate = duplicate,
                    global = global,
                    onGlobalChanged = { global = it },
                    showResolveVariables = containsNestedVariableReference,
                    injectVars = injectVars,
                    onInjectVarsChanged = { injectVars = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled =
                    explicitNameValid &&
                        !duplicate &&
                        contentValid,
                onClick = {
                    onSave(
                        variable,
                        buildVariable(),
                        global,
                    )
                },
            ) {
                Text(
                    if (variable == null) {
                        "Insert"
                    } else {
                        "Save"
                    },
                )
            }
        },
        dismissButton = {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    TextButton(
                        onClick =
                            onDelete,
                    ) {
                        Text(
                            "Delete variable",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error,
                        )
                    }
                }

                TextButton(
                    onClick =
                        onDismiss,
                ) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun VariablesDialog(
    localVariables: List<TemplateVariable>,
    globalVariables: List<TemplateVariable>,
    onDismiss: () -> Unit,
    onInsert: (TemplateVariable) -> Unit,
    onEdit: (TemplateVariable, Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Variables") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        max = 500.dp,
                    )
                    .verticalScroll(
                        rememberScrollState(),
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        8.dp,
                    ),
            ) {
                if (
                    localVariables.isEmpty() &&
                    globalVariables.isEmpty()
                ) {
                    Text(
                        "No variables yet. Create one from the toolbar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (localVariables.isNotEmpty()) {
                    Text(
                        "This snippet",
                        style = MaterialTheme.typography.labelLarge,
                    )

                    localVariables.forEach { variable ->
                        VariableRow(
                            variable = variable,
                            onInsert = { onInsert(variable) },
                            onEdit = { onEdit(variable, false) },
                        )
                    }
                }

                if (
                    localVariables.isNotEmpty() &&
                    globalVariables.isNotEmpty()
                ) {
                    HorizontalDivider()
                }

                if (globalVariables.isNotEmpty()) {
                    Text(
                        "Available everywhere",
                        style = MaterialTheme.typography.labelLarge,
                    )

                    globalVariables.forEach { variable ->
                        VariableRow(
                            variable = variable,
                            onInsert = { onInsert(variable) },
                            onEdit = { onEdit(variable, true) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick =
                    onDismiss,
            ) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun VariableRow(
    variable: TemplateVariable,
    onInsert: () -> Unit,
    onEdit: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text("{{${variable.name}}}")
        },
        supportingContent = {
            Text(
                TemplateVariableKind.from(variable).label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onInsert) {
                    Text("Insert")
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit {{${variable.name}}}",
                    )
                }
            }
        },
    )
}

@Composable
private fun VariableAdvancedSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    name: String,
    onNameChanged: (String) -> Unit,
    explicitNameValid: Boolean,
    duplicate: Boolean,
    global: Boolean,
    onGlobalChanged: (Boolean) -> Unit,
    showResolveVariables: Boolean,
    injectVars: Boolean,
    onInjectVarsChanged: (Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(
            onClick = {
                onExpandedChange(!expanded)
            },
        ) {
            Icon(
                if (expanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = null,
            )

            Text("Advanced")
        }

        if (expanded) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Variable name (optional)")
                },
                supportingText = when {
                    !explicitNameValid -> {
                        {
                            Text(
                                "Use letters, numbers and underscores; do not start with a number.",
                            )
                        }
                    }

                    duplicate -> {
                        {
                            Text("That name is already used.")
                        }
                    }

                    else -> null
                },
                isError = !explicitNameValid || duplicate,
                singleLine = true,
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Available in every snippet")
                    Text(
                        "Store this variable globally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = global,
                    onCheckedChange = onGlobalChanged,
                )
            }

            if (showResolveVariables) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Expand variables inside this value")
                        Text(
                            "Resolve {{variables}} used by this variable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = injectVars,
                        onCheckedChange = onInjectVarsChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditableOptionList(
    options: List<String>,
    onOptionsChanged: (List<String>) -> Unit,
    emptyText: String,
) {
    var input by remember {
        mutableStateOf("")
    }

    fun addInput() {
        val option =
            input.trim()

        if (option.isBlank()) {
            return
        }

        onOptionsChanged(
            options + option,
        )

        input = ""
    }

    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                6.dp,
            ),
    ) {
        if (options.isEmpty()) {
            Text(
                emptyText,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
            )
        }

        options.forEachIndexed {
                index,
                option ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Text(
                    option,
                    modifier =
                        Modifier.weight(1f),
                )

                IconButton(
                    enabled =
                        index > 0,
                    onClick = {
                        onOptionsChanged(
                            options
                                .toMutableList()
                                .apply {
                                    add(
                                        index - 1,
                                        removeAt(
                                            index,
                                        ),
                                    )
                                },
                        )
                    },
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription =
                            "Move up",
                    )
                }

                IconButton(
                    enabled =
                        index <
                            options.lastIndex,
                    onClick = {
                        onOptionsChanged(
                            options
                                .toMutableList()
                                .apply {
                                    add(
                                        index + 1,
                                        removeAt(
                                            index,
                                        ),
                                    )
                                },
                        )
                    },
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription =
                            "Move down",
                    )
                }

                IconButton(
                    onClick = {
                        onOptionsChanged(
                            options
                                .toMutableList()
                                .also {
                                    it.removeAt(
                                        index,
                                    )
                                },
                        )
                    },
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription =
                            "Delete option",
                    )
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
            },
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text(
                    "Add option",
                )
            },
            trailingIcon = {
                IconButton(
                    onClick =
                        ::addInput,
                    enabled =
                        input.isNotBlank(),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription =
                            "Add option",
                    )
                }
            },
            keyboardOptions =
                KeyboardOptions(
                    imeAction =
                        ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        addInput()
                    },
                ),
            singleLine = true,
        )
    }
}

@Composable
private fun PreviewCard(
    title: String = "Preview",
    value: String,
) {
    if (value.isBlank()) return

    Card(
        Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(value)
        }
    }
}

private data class ChoiceEditorOption(
    val label: String,
    val insertedValue: String? = null,
) {
    val result: String
        get() = insertedValue ?: label
}

private fun parseChoiceEditorOptions(
    raw: String,
): List<ChoiceEditorOption> =
    lines(raw).map { line ->
        if ("=>" in line) {
            ChoiceEditorOption(
                label = line.substringBefore("=>").trim(),
                insertedValue = line.substringAfter("=>").trim(),
            )
        } else {
            ChoiceEditorOption(
                label = line,
            )
        }
    }

private fun encodeChoiceEditorOptions(
    options: List<ChoiceEditorOption>,
): String =
    options.joinToString("\n") { option ->
        val insertedValue = option.insertedValue

        if (
            insertedValue == null ||
            insertedValue == option.label
        ) {
            option.label
        } else {
            "${option.label} => $insertedValue"
        }
    }

@Composable
private fun ChoiceEditor(
    value: String,
    onValueChanged: (String) -> Unit,
) {
    val options = parseChoiceEditorOptions(value)
    var newOption by remember {
        mutableStateOf("")
    }

    fun update(
        index: Int,
        option: ChoiceEditorOption,
    ) {
        val next = options.toMutableList()
        next[index] = option

        onValueChanged(
            encodeChoiceEditorOptions(next),
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (options.isEmpty()) {
            Text(
                "Add the options the user can choose from.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        options.forEachIndexed { index, option ->
            Card(
                Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = option.label,
                            onValueChange = {
                                update(
                                    index,
                                    option.copy(label = it),
                                )
                            },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text("Option ${index + 1}")
                            },
                            singleLine = true,
                        )

                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                val next = options.toMutableList()
                                next.add(index - 1, next.removeAt(index))
                                onValueChanged(encodeChoiceEditorOptions(next))
                            },
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Move up",
                            )
                        }

                        IconButton(
                            enabled = index < options.lastIndex,
                            onClick = {
                                val next = options.toMutableList()
                                next.add(index + 1, next.removeAt(index))
                                onValueChanged(encodeChoiceEditorOptions(next))
                            },
                        ) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = "Move down",
                            )
                        }

                        IconButton(
                            onClick = {
                                val next = options.toMutableList()
                                next.removeAt(index)
                                onValueChanged(encodeChoiceEditorOptions(next))
                            },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete option",
                            )
                        }
                    }

                    var useDifferentValue by remember(option) {
                        mutableStateOf(option.insertedValue != null)
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Use a different inserted value",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Switch(
                            checked = useDifferentValue,
                            onCheckedChange = { enabled ->
                                useDifferentValue = enabled
                                if (!enabled) {
                                    update(index, option.copy(insertedValue = null))
                                } else {
                                    update(index, option.copy(insertedValue = option.label))
                                }
                            },
                        )
                    }

                    if (useDifferentValue) {
                        OutlinedTextField(
                            value = option.insertedValue ?: option.label,
                            onValueChange = {
                                update(index, option.copy(insertedValue = it))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Inserted value")
                            },
                            singleLine = true,
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = newOption,
            onValueChange = {
                newOption = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Add option")
            },
            trailingIcon = {
                IconButton(
                    enabled = newOption.isNotBlank(),
                    onClick = {
                        val label = newOption.trim()
                        if (label.isBlank()) return@IconButton
                        val next = options + ChoiceEditorOption(label = label)
                        onValueChanged(encodeChoiceEditorOptions(next))
                        newOption = ""
                    },
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Add option",
                    )
                }
            },
            singleLine = true,
        )
    }
}

private fun randomTextPreview(
    length: Int,
    alphabet: String,
): String {
    if (alphabet.isBlank()) return ""

    val random = kotlin.random.Random(
        alphabet.hashCode() * 31 + length,
    )

    return buildString {
        repeat(length.coerceAtMost(32)) {
            append(
                alphabet[random.nextInt(alphabet.length)],
            )
        }
    }
}

private data class TokenButton(
    val label: String,
    val token: String,
    val icon: ImageVector,
)

@Composable
private fun TokenChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(label)
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription =
                    null,
            )
        },
    )
}

/**
 * Creates the one configuration-free variable type.
 *
 * The generated name is still collision-safe, so an imported project
 * containing an unrelated {{clipboard}} variable won't be overwritten.
 */
private fun createClipboardVariable(
    reservedVariables: List<TemplateVariable>,
): TemplateVariable {
    val reservedNames =
        reservedVariables
            .map {
                it.name.lowercase()
            }
            .toSet()

    val name =
        uniqueVariableName(
            TemplateVariableKind
                .CLIPBOARD
                .defaultName,
            reservedNames,
        )

    return TemplateVariable(
        name = name,
        type =
            TemplateVariableKind
                .CLIPBOARD
                .type,
        paramsJson =
            JSONObject()
                .toString(),
        dependsOn =
            emptyList(),
        injectVars = true,
    )
}

private fun upsertVariable(
    variables: List<TemplateVariable>,
    original: TemplateVariable?,
    updated: TemplateVariable,
): List<TemplateVariable> =
    if (original == null) {
        variables + updated
    } else {
        variables.map {
            if (
                it.name ==
                original.name
            ) {
                updated
            } else {
                it
            }
        }
    }

private fun uniqueVariableName(
    base: String,
    reservedNames: Set<String>,
): String {
    if (
        base.lowercase() !in
        reservedNames
    ) {
        return base
    }

    var index = 2

    while (
        "${base}_$index".lowercase() in
        reservedNames
    ) {
        index++
    }

    return "${base}_$index"
}

private fun preferredDateOffsetUnit(
    seconds: Long,
): DateOffsetUnit = when {
    seconds == 0L ->
        DateOffsetUnit.DAYS

    seconds % DateOffsetUnit.DAYS.seconds == 0L ->
        DateOffsetUnit.DAYS

    seconds % DateOffsetUnit.HOURS.seconds == 0L ->
        DateOffsetUnit.HOURS

    seconds % DateOffsetUnit.MINUTES.seconds == 0L ->
        DateOffsetUnit.MINUTES

    else ->
        DateOffsetUnit.SECONDS
}

private fun formatDateOffsetAmount(
    seconds: Long,
    unit: DateOffsetUnit,
): String {
    val amount =
        seconds.toDouble() /
            unit.seconds.toDouble()

    return if (amount % 1.0 == 0.0) {
        amount.toLong().toString()
    } else {
        String
            .format(
                Locale.US,
                "%.2f",
                amount,
            )
            .trimEnd('0')
            .trimEnd('.')
    }
}

private fun sanitizeDateOffsetInput(
    input: String,
): String {
    val normalized =
        input.replace(',', '.')

    val result =
        StringBuilder()

    normalized.forEachIndexed { index, char ->
        when {
            char.isDigit() ->
                result.append(char)

            char == '-' &&
                index == 0 &&
                result.isEmpty() ->
                result.append(char)

            char == '.' &&
                '.' !in result ->
                result.append(char)
        }
    }

    return result.toString()
}

private fun previewEspansoDateFormat(
    format: String,
    offsetSeconds: Long,
    localeTag: String,
    timezone: String,
): String {
    val locale =
        localeTag
            .trim()
            .replace('_', '-')
            .takeIf(String::isNotBlank)
            ?.let(Locale::forLanguageTag)
            ?.takeIf {
                it.language.isNotBlank()
            }
            ?: Locale.getDefault()

    val zone =
        runCatching {
            if (timezone.isBlank()) {
                ZoneId.systemDefault()
            } else {
                ZoneId.of(timezone.trim())
            }
        }.getOrDefault(
            ZoneId.systemDefault(),
        )

    val dateTime =
        Instant
            .now()
            .plusSeconds(offsetSeconds)
            .atZone(zone)

    fun pattern(
        value: String,
    ): String =
        dateTime.format(
            DateTimeFormatter.ofPattern(
                value,
                locale,
            ),
        )

    val percentPlaceholder =
        "\u0000PERCENT\u0000"

    var result =
        format.replace(
            "%%",
            percentPlaceholder,
        )

    val replacements = linkedMapOf(
        "%F" to pattern("yyyy-MM-dd"),
        "%T" to pattern("HH:mm:ss"),
        "%R" to pattern("HH:mm"),
        "%Y" to pattern("yyyy"),
        "%y" to pattern("yy"),
        "%m" to pattern("MM"),
        "%B" to pattern("MMMM"),
        "%b" to pattern("MMM"),
        "%d" to pattern("dd"),
        "%e" to dateTime.dayOfMonth
            .toString()
            .padStart(2, ' '),
        "%A" to pattern("EEEE"),
        "%a" to pattern("EEE"),
        "%H" to pattern("HH"),
        "%I" to pattern("hh"),
        "%M" to pattern("mm"),
        "%S" to pattern("ss"),
        "%p" to pattern("a"),
        "%j" to dateTime.dayOfYear
            .toString()
            .padStart(3, '0'),
        "%w" to (
            dateTime.dayOfWeek.value % 7
            ).toString(),
        "%z" to pattern("xx"),
        "%Z" to pattern("z"),
    )

    replacements.forEach { (token, replacement) ->
        result =
            result.replace(
                token,
                replacement,
            )
    }

    return result.replace(
        percentPlaceholder,
        "%",
    )
}

private fun parseParams(
    value: String?,
): JSONObject =
    runCatching {
        JSONObject(
            value.orEmpty(),
        )
    }.getOrElse {
        JSONObject()
    }

private fun valueFor(
    kind: TemplateVariableKind,
    params: JSONObject,
): String =
    when (kind) {
        TemplateVariableKind.TEXT ->
            params.optString(
                "value",
                params.optString(
                    "echo",
                ),
            )

        TemplateVariableKind.DATE ->
            params.optString(
                "format",
                "%Y-%m-%d",
            )

        TemplateVariableKind.CLIPBOARD ->
            ""

        TemplateVariableKind.RANDOM ->
            ChoiceLists.toEditorText(
                params.opt("choices"),
            )

        TemplateVariableKind.CHOICE ->
            choiceEditorLines(
                params.optJSONArray(
                    "values",
                ),
            )

        TemplateVariableKind.FORM ->
            params.optString(
                "layout",
            )

        TemplateVariableKind.MATCH ->
            params.optString(
                "trigger",
            )
    }

private fun lines(
    value: String,
): List<String> =
    value
        .lineSequence()
        .map(
            String::trim,
        )
        .filter(
            String::isNotBlank,
        )
        .toList()

private fun arrayLines(
    array: JSONArray?,
): String =
    if (array == null) {
        ""
    } else {
        buildList(
            array.length(),
        ) {
            for (
                index in
                0 until array.length()
            ) {
                array
                    .optString(
                        index,
                    )
                    .takeIf(
                        String::isNotBlank,
                    )
                    ?.let(
                        ::add,
                    )
            }
        }.joinToString(
            "\n",
        )
    }

private fun choiceEditorLines(
    array: JSONArray?,
): String =
    if (array == null) {
        ""
    } else {
        buildList(
            array.length(),
        ) {
            for (
                index in
                0 until array.length()
            ) {
                when (
                    val item =
                        array.opt(index)
                ) {
                    is JSONObject -> {
                        val label =
                            item.optString(
                                "label",
                                item.optString(
                                    "id",
                                ),
                            )

                        val id =
                            item.optString(
                                "id",
                                item.optString(
                                    "value",
                                    label,
                                ),
                            )

                        if (
                            label.isNotBlank()
                        ) {
                            add(
                                if (
                                    label == id
                                ) {
                                    label
                                } else {
                                    "$label => $id"
                                },
                            )
                        }
                    }

                    else -> {
                        item
                            ?.toString()
                            ?.takeIf(
                                String::isNotBlank,
                            )
                            ?.let(
                                ::add,
                            )
                    }
                }
            }
        }.joinToString(
            "\n",
        )
    }

private fun choiceValuesJson(
    lines: List<String>,
): JSONArray =
    JSONArray().apply {
        lines.forEach { line ->
            val label =
                line
                    .substringBefore(
                        "=>",
                    )
                    .trim()

            val value =
                line
                    .substringAfter(
                        "=>",
                        label,
                    )
                    .trim()

            if (
                "=>" in line &&
                label.isNotBlank() &&
                value.isNotBlank()
            ) {
                put(
                    JSONObject()
                        .put(
                            "label",
                            label,
                        )
                        .put(
                            "id",
                            value,
                        ),
                )
            } else {
                put(line)
            }
        }
    }

private fun mergedFormFieldConfigs(
    layout: String,
    fields: JSONObject?,
): Map<String, FormFieldConfig> {
    val fromJson = parseFormFieldConfigs(fields)
    val inline = formFieldInlineDefaults(layout)
    return (formFieldNames(layout) + fromJson.keys)
        .distinct()
        .associateWith { name ->
            val base = fromJson[name] ?: FormFieldConfig()
            inline[name]?.let { base.copy(defaultValue = it) } ?: base
        }
}

private fun formFieldToken(
    fieldName: String,
    defaultValue: String,
): String =
    if (defaultValue.isBlank()) {
        "[[$fieldName]]"
    } else {
        "[[$fieldName=$defaultValue]]"
    }

private fun parseFormFieldConfigs(
    fields: JSONObject?,
): Map<String, FormFieldConfig> {
    if (fields == null) {
        return emptyMap()
    }

    return buildMap {
        fields.keys()
            .forEach { name ->
                val definition =
                    fields.optJSONObject(
                        name,
                    ) ?: return@forEach

                val kind =
                    if (
                        definition.optBoolean(
                            "multiline",
                        )
                    ) {
                        FormFieldKind.MULTILINE
                    } else if (
                        definition
                            .optString(
                                "type",
                            )
                            .equals(
                                "list",
                                ignoreCase = true,
                            )
                    ) {
                        FormFieldKind.CHOICE
                    } else {
                        FormFieldKind.entries
                            .firstOrNull {
                                !it.multiline &&
                                    it.type.equals(
                                        definition.optString(
                                            "type",
                                        ),
                                        ignoreCase = true,
                                    )
                            } ?: FormFieldKind.TEXT
                    }

                put(
                    name,
                    FormFieldConfig(
                        kind = kind,
                        defaultValue =
                            definition.optString(
                                "default",
                            ),
                        options =
                            jsonStringList(
                                definition.opt(
                                    "values",
                                ),
                            ),
                    ),
                )
            }
    }
}

private fun jsonStringList(
    value: Any?,
): List<String> =
    when (value) {
        is JSONArray -> {
            buildList(
                value.length(),
            ) {
                for (
                    index in
                    0 until value.length()
                ) {
                    value
                        .optString(
                            index,
                        )
                        .takeIf(
                            String::isNotBlank,
                        )
                        ?.let(
                            ::add,
                        )
                }
            }
        }

        is String -> {
            value
                .lineSequence()
                .map(
                    String::trim,
                )
                .filter(
                    String::isNotBlank,
                )
                .toList()
        }

        else ->
            emptyList()
    }

@Composable
private fun FormFieldTypePicker(
    selected: FormFieldKind,
    onSelected: (FormFieldKind) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Type: ${selected.label}")
                Icon(Icons.Default.ExpandMore, contentDescription = null)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            FormFieldKind.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FormFieldDefaultEditor(
    kind: FormFieldKind,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    when (kind) {
        FormFieldKind.DATE -> {
            OutlinedButton(
                onClick = {
                    val calendar = Calendar.getInstance()
                    parseFormDate(value)?.let { (year, month, day) ->
                        calendar.set(year, month, day)
                    }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            onValueChange("%04d-%02d-%02d".format(year, month + 1, day))
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH),
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(value.ifBlank { "Pick default date" })
            }
        }

        FormFieldKind.TIME -> {
            OutlinedButton(
                onClick = {
                    val calendar = Calendar.getInstance()
                    parseFormTime(value)?.let { (hour, minute) ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                    }
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            onValueChange("%02d:%02d".format(hour, minute))
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        true,
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(value.ifBlank { "Pick default time" })
            }
        }

        FormFieldKind.MULTILINE -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Default text (optional)") },
                minLines = 2,
            )
        }

        else -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Default text (optional)") },
                singleLine = true,
            )
        }
    }
}

private fun parseFormDate(value: String): Triple<Int, Int, Int>? {
    val parts = value.trim().split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull()?.minus(1) ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return Triple(year, month, day)
}

private fun parseFormTime(value: String): Pair<Int, Int>? {
    val parts = value.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return hour to minute
}

private fun normalizedFormFieldName(
    value: String,
): String =
    value
        .trim()
        .lowercase()
        .replace(
            Regex(
                "[^a-z0-9_]+",
            ),
            "_",
        )
        .trim('_')
        .let { normalized ->
            when {
                normalized.isBlank() ->
                    ""

                normalized.first().isDigit() ->
                    "field_$normalized"

                else ->
                    normalized
            }
        }
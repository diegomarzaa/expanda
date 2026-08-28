package dev.diego.expanda.data

import org.json.JSONArray
import org.json.JSONObject

/** Built-in example snippets shipped as a single imported YAML file. */
object ExampleSnippets {
    const val EXAMPLES_FILE = "_examples.yml"
    const val EXAMPLES_TAG = "examples"
    const val IMPORT_PATH = "./$EXAMPLES_FILE"

    fun emptyBaseContent(): String = "matches: []\n"

    fun baseWithImport(existing: String): String {
        val imports = runCatching { EspansoYamlCodec.importPaths(existing) }.getOrDefault(emptyList())
        if (imports.any { it.removePrefix("./") == EXAMPLES_FILE }) return existing
        return buildString {
            appendLine("imports:")
            appendLine("  - $IMPORT_PATH")
            appendLine()
            append(existing.trimStart('\n', '\r'))
        }
    }

    val file: EspansoSourceFile
        get() = EspansoSourceFile(EXAMPLES_FILE, EspansoYamlCodec.encodeSpaced(matches).yaml)

    private val matches = listOf(
        match(";hello", "Hello! Thanks for getting in touch.", "Simple replacement"),
        match(";address", "Street and number\nCity, postcode\nCountry", "Multiline text"),
        TextMatch(
            triggers = listOf(MatchTrigger(";bye"), MatchTrigger(";goodbye")),
            replacements = listOf("See you soon!"),
            label = "Two triggers",
        ),
        match(";follow", "Hi $|$,\n\nJust following up on this.", "Cursor position"),
        variableMatch(
            trigger = ";today",
            replacement = "Today is {{today}}",
            label = "Current date",
            TemplateVariable("today", "date", json("format" to "%Y-%m-%d")),
        ),
        variableMatch(
            trigger = ";clip",
            replacement = "Clipboard: {{clipboard}}",
            label = "Clipboard",
            TemplateVariable("clipboard", "clipboard"),
        ),
        variableMatch(
            trigger = ";greet",
            replacement = "Hello {{details.name}}!",
            label = "Simple form",
            form("details", "Name: [[name]]"),
        ),
        variableMatch(";echo", "{{value}}", "Echo variable", TemplateVariable("value", "echo", json("echo" to "Reusable text"))),
        variableMatch(";time", "{{time}}", "Current time", TemplateVariable("time", "date", json("format" to "%H:%M"))),
        variableMatch(";tomorrow", "{{date}}", "Tomorrow", TemplateVariable("date", "date", json("format" to "%Y-%m-%d", "offset" to 86400))),
        variableMatch(";random", "{{item}}", "Random choice", TemplateVariable("item", "random", json("choices" to listOf("Alpha", "Beta", "Gamma")))),
        variableMatch(";priority", "Priority: {{priority}}", "Choice", choice("priority", listOf("Low", "Medium", "High"))),
        variableMatch(
            ";status", "Status: {{status}}", "Labeled choice",
            TemplateVariable(
                "status", "choice",
                JSONObject().put("values", JSONArray().apply {
                    put(JSONObject().put("label", "Ready to send").put("id", "ready"))
                    put(JSONObject().put("label", "Needs review").put("id", "review"))
                }).toString(),
            ),
        ),
        variableMatch(";person", "{{person.first}} {{person.last}}", "Form fields", form("person", "[[first]] [[last]]")),
        variableMatch(
            ";meeting", "{{meeting.title}}\n{{meeting.notes}}", "Multiline form",
            form(
                "meeting",
                "Title: [[title]]\nNotes: [[notes]]",
                fields = JSONObject().put("notes", JSONObject().put("multiline", true)),
            ),
        ),
        variableMatch(
            ";channel", "Channel: {{form.channel}}", "Form choice",
            form(
                "form",
                "Channel: [[channel]]",
                fields = JSONObject().put(
                    "channel",
                    JSONObject().put("type", "choice").put("values", JSONArray(listOf("Email", "Chat", "Call"))),
                ),
            ),
        ),
        variableMatch(";signature", "Best regards,\n{{name}}", "Reusable signature", TemplateVariable("name", "echo", json("echo" to "Your name"))),
        variableMatch(";signed", "Message\n\n{{signature}}", "Nested match", TemplateVariable("signature", "match", json("trigger" to ";signature"))),
        TextMatch(
            triggers = listOf(MatchTrigger(";order\\s+(?P<amount>\\d+)", TriggerKind.REGEX)),
            replacements = listOf("Order amount: {{amount}}"),
            label = "Named regex capture",
        ),
        TextMatch(
            triggers = listOf(MatchTrigger(";pair\\s+(?P<first>\\w+)\\s+(?P<second>\\w+)", TriggerKind.REGEX)),
            replacements = listOf("First: {{first}}, second: {{second}}"),
            label = "Multiple named regex captures",
        ),
        match(";word", "Whole-word match", "Word boundaries").copy(
            options = MatchOptions(leftWord = true, rightWord = true),
        ),
        match(";case", "case follows the trigger", "Case propagation").copy(
            options = MatchOptions(caseSensitive = false, propagateCase = true),
        ),
        match(";unicode", "Español · Ελληνικά · 日本語 · 🌍", "Unicode"),
        match(";code", "if (ready) {\n    run()\n}", "Code block"),
        match(";mail", "Hello,\n\nThanks for your message.\n\nBest regards,", "Email reply"),
        match(";phone", "+34 600 000 000", "Contact detail"),
        match(";link", "https://example.com", "Link"),
        match(";iban", "ES00 0000 0000 0000 0000 0000", "Structured text"),
        variableMatch(
            ";ticket", "Ticket {{team}}-{{number}}", "Combined variables",
            choice("team", listOf("APP", "WEB", "OPS")),
            TemplateVariable("number", "random", json("choices" to listOf("101", "202", "303"))),
        ),
        variableMatch(
            ";brief", "{{brief.project}}: {{brief.summary}}\nDue: {{due}}", "Form and date",
            form("brief", "Project: [[project]]\nSummary: [[summary]]"),
            TemplateVariable("due", "date", json("format" to "%Y-%m-%d", "offset" to 604800)),
        ),
    )

    private fun match(trigger: String, replacement: String, label: String) = TextMatch(
        triggers = listOf(MatchTrigger(trigger)),
        replacements = listOf(replacement),
        label = label,
    )

    private fun variableMatch(
        trigger: String,
        replacement: String,
        label: String,
        vararg variables: TemplateVariable,
    ) = match(trigger, replacement, label).copy(vars = variables.toList())

    private fun form(name: String, layout: String, fields: JSONObject? = null): TemplateVariable = TemplateVariable(
        name = name,
        type = "form",
        paramsJson = JSONObject().put("layout", layout).apply { fields?.let { put("fields", it) } }.toString(),
    )

    private fun choice(name: String, values: List<String>) = TemplateVariable(
        name,
        "choice",
        JSONObject().put("values", JSONArray(values)).toString(),
    )

    private fun json(vararg values: Pair<String, Any>): String = JSONObject().apply {
        values.forEach { (key, value) -> put(key, value) }
    }.toString()
}

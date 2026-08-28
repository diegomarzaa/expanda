package dev.diego.expanda.engine

import dev.diego.expanda.data.MatchTrigger
import dev.diego.expanda.data.TemplateVariable
import dev.diego.expanda.data.TextMatch
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

internal object TemplateTestSupport {
    val fixedClock: Clock = Clock.fixed(Instant.parse("2026-08-19T10:15:30Z"), ZoneOffset.UTC)

    fun echo(name: String, value: String) = TemplateVariable(name, "echo", """{"echo":"$value"}""")

    fun form(name: String, layout: String, fieldsJson: String = "{}") =
        TemplateVariable(name, "form", """{"layout":${quote(layout)},"fields":$fieldsJson}""")

    fun matchVar(name: String, trigger: String) =
        TemplateVariable(name, "match", """{"trigger":"$trigger"}""")

    fun dateVar(name: String, format: String, offsetSeconds: Long = 0) =
        TemplateVariable(name, "date", """{"format":"$format","offset":$offsetSeconds}""")

    fun renderer(
        clock: Clock = fixedClock,
        clipboard: () -> String = { "" },
        random: Random = Random.Default,
        matchResolver: (String) -> TextMatch? = { null },
    ) = TemplateRenderer(
        clock = clock,
        clipboard = clipboard,
        matchResolver = matchResolver,
        random = random,
    )

    fun textMatch(trigger: String, replace: String, vars: List<TemplateVariable> = emptyList()) =
        TextMatch(
            triggers = listOf(MatchTrigger(trigger)),
            replacements = listOf(replace),
            vars = vars,
        )

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}

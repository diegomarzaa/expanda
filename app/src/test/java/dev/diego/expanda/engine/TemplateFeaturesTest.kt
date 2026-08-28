package dev.diego.expanda.engine

import dev.diego.expanda.data.TemplateSelectionMode
import dev.diego.expanda.data.TemplateVariable
import dev.diego.expanda.data.TextMatch
import dev.diego.expanda.data.MatchTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

class TemplateFeaturesTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-19T10:15:30Z"), ZoneOffset.UTC)

    @Test fun `portable variables resolve without executing unsupported types`() {
        val variables = listOf(
            TemplateVariable("name", "echo", """{"echo":"Diego"}"""),
            TemplateVariable("today", "date", """{"format":"%Y-%m-%d"}"""),
            TemplateVariable("quotes", "random", """{"choices":["one","two"]}"""),
            TemplateVariable("clip", "clipboard"),
            TemplateVariable("choice", "choice", """{"values":["A","B"]}"""),
            TemplateVariable("unsafe", "shell", """{"cmd":"touch /tmp/must-not-run"}"""),
        )
        val rendered = TemplateRenderer(
            clock = fixedClock,
            clipboard = { "copied" },
            random = Random(1),
        ).render(
            "{{name}} {{today}} {{quotes}} {{clip}} {{choice}} {{unsafe}}",
            variables,
        )

        assertEquals("Diego 2026-08-19 one copied  {{unsafe}}", rendered.text)
        assertEquals(TemplateFieldInputType.CHOICE, rendered.fields.single().inputType)
        assertEquals(listOf("A", "B"), rendered.fields.single().options)
        assertEquals(
            "Diego 2026-08-19 one copied B {{unsafe}}",
            rendered.fillFields(mapOf("choice" to "B")).text,
        )
        assertEquals(listOf("{{unsafe}}"), rendered.unresolvedTokens)
    }

    @Test fun `random text can use a custom alphabet`() {
        val variable = TemplateVariable(
            "pin",
            "random",
            """{"length":8,"alphabet":"01"}""",
        )
        val value = TemplateRenderer(random = Random(2)).render("{{pin}}", listOf(variable)).text
        assertEquals(8, value.length)
        assertTrue(value.all { it == '0' || it == '1' })
    }

    @Test fun `inject vars false keeps parameter references literal`() {
        val variables = listOf(
            TemplateVariable("author", "echo", """{"echo":"Diego"}"""),
            TemplateVariable("expanded", "echo", """{"echo":"{{author}}"}"""),
            TemplateVariable(
                "literal",
                "echo",
                """{"echo":"{{author}}"}""",
                injectVars = false,
            ),
        )

        val rendered = TemplateRenderer().render("{{expanded}} / {{literal}}", variables)
        assertEquals("Diego / {{author}}", rendered.text)
    }

    @Test fun `choice labels show friendly text but insert their Espanso ids`() {
        val variable = TemplateVariable(
            "priority",
            "choice",
            """{"values":[{"label":"Urgent","id":"P1"},{"label":"Normal","id":"P2"}]}""",
        )
        val rendered = TemplateRenderer().render("Priority: {{priority}}", listOf(variable))

        assertEquals(listOf("Urgent", "Normal"), rendered.fields.single().options)
        assertEquals(listOf("P1", "P2"), rendered.fields.single().optionValues)
    }

    @Test fun `native cursor marker is atomic in the editor`() {
        val text = "Hello $|$ world"
        val cursor = "Hello $|$".length
        val deleted = TemplateTokenEditor.deleteBackward(text, cursor)

        assertEquals("Hello  world", deleted.text)
        assertEquals("Hello ".length, deleted.cursor)
    }

    @Test fun `local variables override globals and nested matches inherit globals`() {
        val nested = TextMatch(
            triggers = listOf(MatchTrigger(":nested")),
            replacements = listOf("{{shared}}/{{global_only}}"),
            vars = listOf(TemplateVariable("shared", "echo", """{"value":"nested"}""")),
        )
        val match = TextMatch(
            triggers = listOf(MatchTrigger(":main")),
            replacements = listOf("{{shared}} {{global_only}} {{match::nested}}"),
            vars = listOf(TemplateVariable("shared", "echo", """{"value":"local"}""")),
        )
        val rendered = TemplateRenderer(matchResolver = { if (it == ":nested") nested else null }).render(
            match,
            globalVariables = listOf(
                TemplateVariable("shared", "echo", """{"value":"global"}"""),
                TemplateVariable("global_only", "echo", """{"value":"available"}"""),
            ),
        )

        assertEquals("local available nested/available", rendered.text)
    }

    @Test fun `uuid token generates a valid identifier`() {
        val value = TemplateRenderer().render("{UUID}").text
        assertEquals(value, UUID.fromString(value).toString())
    }

    @Test fun `modern form and send tokens produce structured request`() {
        val rendered = TemplateRenderer(clock = fixedClock).render("Hi {FORM: NAME|Diego} {CURSOR} {SEND}")

        assertEquals("Hi Diego  ", rendered.text)
        assertEquals(1, rendered.fields.size)
        assertEquals("NAME", rendered.fields.single().name)
        assertEquals(3, rendered.fields.single().start)
        assertEquals(8, rendered.fields.single().end)
        assertEquals(1, rendered.actions.size)
        assertTrue(rendered.requiresInput)
        assertEquals("Hi Ana  ", rendered.fillFields(mapOf("NAME" to "Ana")).text)
    }

    @Test fun `repeated fields and nested cursor keep offsets after filling`() {
        val rendered = TemplateRenderer().render("A {FORM: NAME|Diego} {CURSOR} B {FORM: NAME|Diego}")
        assertEquals("A Diego  B Diego", rendered.text)
        assertEquals("A Diego ".length, rendered.cursorOffset)
        assertEquals(0, rendered.fields[0].occurrence)
        assertEquals(1, rendered.fields[1].occurrence)

        val filled = rendered.fillFields(mapOf("NAME" to "Ana"))
        assertEquals("A Ana  B Ana", filled.text)
        assertEquals("A Ana ".length, filled.cursorOffset)
    }

    @Test fun `date offsets and nested match variables preserve form fields`() {
        val nested = TextMatch(
            triggers = listOf(MatchTrigger(":sig")),
            replacements = listOf("Regards, {FORM: NAME}"),
        )
        val renderer = TemplateRenderer(
            clock = fixedClock,
            matchResolver = { if (it == ":sig") nested else null },
        )
        val rendered = renderer.render("{{date:+2:DAY:yyyy-MM-dd}} {{match::sig}}")

        assertEquals("2026-08-21 Regards, ", rendered.text)
        assertEquals("NAME", rendered.fields.single().name)
        assertEquals("2026-08-21 Regards, Ana", rendered.fillFields(mapOf("NAME" to "Ana")).text)
    }

    @Test fun `meeting template with nested signature renders without initializing failure`() {
        val signature = TextMatch(
            triggers = listOf(MatchTrigger(";sig")),
            replacements = listOf("Regards,\nDiego"),
        )
        val meeting = TextMatch(
            triggers = listOf(MatchTrigger(";meeting")),
            replacements = listOf("Hi Sam,\n\nMeeting: {DATE:yyyy-MM-dd}\n\n{SNIPPET: ;sig}{CURSOR}"),
        )

        val rendered = TemplateRenderer(
            clock = fixedClock,
            matchResolver = { if (it == ";sig") signature else null },
        ).render(meeting)

        assertEquals("Hi Sam,\n\nMeeting: 2026-08-19\n\nRegards,\nDiego", rendered.text)
        assertEquals(rendered.text.length, rendered.cursorOffset)
    }

    @Test fun `verbose form variable exposes dotted field names`() {
        val variable = TemplateVariable(
            name = "form1",
            type = "form",
            paramsJson = """{"layout":"Name: [[name]] / [[kind]]","fields":{"name":{"default":"Diego"},"kind":{"type":"choice","values":["one","two"]}}}""",
        )
        val rendered = TemplateRenderer().render("{{form1}} -> {{form1.name}}", listOf(variable))

        assertEquals("Name: Diego / one -> Diego", rendered.text)
        assertEquals(listOf("form1.name", "form1.kind", "form1.name"), rendered.fields.map { it.label })
        assertEquals(TemplateFieldInputType.TEXT, rendered.fields[0].inputType)
        assertEquals(TemplateFieldInputType.CHOICE, rendered.fields[1].inputType)
        assertEquals(listOf("one", "two"), rendered.fields[1].options)
        assertEquals("Name: Ana / one -> Ana", rendered.fillFields(mapOf("FORM1_NAME" to "Ana")).text)
    }

    @Test fun `form layouts support inline defaults and multiline controls`() {
        val variable = TemplateVariable(
            name = "report",
            type = "form",
            paramsJson = """{"layout":"Owner: [[owner=Diego]]\nSummary: [[summary]]","fields":{"summary":{"multiline":true}}}""",
        )
        val rendered = TemplateRenderer().render("{{report}}", listOf(variable))

        assertEquals("Owner: Diego\nSummary: ", rendered.text)
        assertEquals("Diego", rendered.fields[0].defaultValue)
        assertTrue(rendered.fields[1].multiline)
    }

    @Test fun `recursive match variables are bounded`() {
        val loop = TextMatch(
            triggers = listOf(MatchTrigger(":loop")),
            replacements = listOf("{{match::loop}}"),
        )
        val renderer = TemplateRenderer(matchResolver = { loop })

        val rendered = renderer.render("{{match::loop}}")
        assertTrue(rendered.text.length < 100)
    }

    @Test fun `template selector supports all replacement strategies`() {
        val first = TextMatch(
            triggers = listOf(MatchTrigger("/x")),
            replacements = listOf("one", "two"),
        )
        assertEquals("one", TemplateSelector().select(first).text)
        assertEquals("two", TemplateSelector().select(first.copy(selectionMode = TemplateSelectionMode.MANUAL), 1).text)
        assertEquals("two", TemplateSelector().select(first.copy(selectionMode = TemplateSelectionMode.SEQUENTIAL, templateIndex = 1)).text)
        assertTrue(
            TemplateSelector(Random(1)).select(first.copy(selectionMode = TemplateSelectionMode.RANDOM)).text in setOf("one", "two"),
        )
    }

    @Test fun `backspace treats a token as one character`() {
        val text = "hello {FORM: NAME} world"
        val cursor = "hello {FORM: NAME}".length
        val result = TemplateTokenEditor.deleteBackward(text, cursor)
        assertEquals("hello  world", result.text)
        assertEquals("hello ".length, result.cursor)
    }

    @Test fun `named variables are clickable and atomic tokens`() {
        val text = "Hello {{today}} and {{form.name}}"

        assertEquals("today", TemplateTokenEditor.variableNameAt(text, text.indexOf("today") + 2))
        assertEquals("form", TemplateTokenEditor.variableNameAt(text, text.indexOf("form") + 1))
        assertEquals(null, TemplateTokenEditor.variableNameAt(text, text.indexOf("{{today}}")))
        assertEquals(null, TemplateTokenEditor.variableNameAt(text, text.indexOf("{{today}}") + "{{today}}".length))

        val cursor = "Hello {{today}}".length
        val deleted = TemplateTokenEditor.deleteBackward(text, cursor)
        assertEquals("Hello  and {{form.name}}", deleted.text)
    }

    @Test fun `regex captures are clickable only inside and delete atomically`() {
        val text = "ETA {{1}} / {{minutes}}"
        val numberedStart = text.indexOf("{{1}}")
        val namedStart = text.indexOf("{{minutes}}")

        assertEquals("{{1}}", TemplateTokenEditor.captureReferenceAt(text, numberedStart + 2)?.token)
        assertEquals(null, TemplateTokenEditor.captureReferenceAt(text, numberedStart))
        assertEquals(
            "{{minutes}}",
            TemplateTokenEditor.captureReferenceAt(text, namedStart + 3, setOf("minutes"))?.token,
        )
        assertEquals("ETA  / {{minutes}}", TemplateTokenEditor.deleteBackward(text, numberedStart + 5).text)
    }

    @Test fun `renaming a variable preserves dotted references and selection`() {
        val text = "{{person}} / {{person.name}}"
        val rewritten = TemplateTokenEditor.rewriteVariableReferences(
            text = text,
            oldName = "person",
            newName = "contact",
            selectionStart = text.length,
        )

        assertEquals("{{contact}} / {{contact.name}}", rewritten.text)
        assertEquals(rewritten.text.length, rewritten.selectionStart)
    }

    @Test fun `deleting a variable removes all of its references`() {
        val rewritten = TemplateTokenEditor.rewriteVariableReferences(
            text = "A {{unused}} B {{unused}}",
            oldName = "unused",
            newName = null,
        )

        assertEquals("A  B ", rewritten.text)
    }

    @Test fun `selection between references only receives preceding rename deltas`() {
        val text = "{{a}} middle {{a}}"
        val selection = text.indexOf("middle") + "middle".length
        val rewritten = TemplateTokenEditor.rewriteVariableReferences(
            text = text,
            oldName = "a",
            newName = "very_long_name",
            selectionStart = selection,
        )

        val expectedSelection = rewritten.text.indexOf("middle") + "middle".length
        assertEquals(expectedSelection, rewritten.selectionStart)
    }
}

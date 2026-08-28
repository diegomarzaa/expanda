package dev.diego.expanda.engine

import dev.diego.expanda.data.MatchTrigger
import dev.diego.expanda.data.TemplateSelectionMode
import dev.diego.expanda.data.TemplateVariable
import dev.diego.expanda.data.TextMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TemplateFeaturesTest {
    private val support get() = TemplateTestSupport

    @Test fun `portable variables resolve without executing unsupported types`() {
        val variables = listOf(
            TemplateTestSupport.echo("name", "Diego"),
            TemplateTestSupport.dateVar("today", "%Y-%m-%d"),
            TemplateVariable("quotes", "random", """{"choices":["one","two"]}"""),
            TemplateVariable("clip", "clipboard"),
            TemplateVariable("choice", "choice", """{"values":["A","B"]}"""),
            TemplateVariable("unsafe", "shell", """{"cmd":"touch /tmp/must-not-run"}"""),
        )
        val rendered = support.renderer(
            clipboard = { "copied" },
            random = Random(1),
        ).render("{{name}} {{today}} {{quotes}} {{clip}} {{choice}} {{unsafe}}", variables)

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
        val variable = TemplateVariable("pin", "random", """{"length":8,"alphabet":"01"}""")
        val value = TemplateRenderer(random = Random(2)).render("{{pin}}", listOf(variable)).text
        assertEquals(8, value.length)
        assertTrue(value.all { it == '0' || it == '1' })
    }

    @Test fun `inject vars false keeps parameter references literal`() {
        val variables = listOf(
            TemplateTestSupport.echo("author", "Diego"),
            TemplateVariable("expanded", "echo", """{"echo":"{{author}}"}"""),
            TemplateVariable("literal", "echo", """{"echo":"{{author}}"}""", injectVars = false),
        )
        assertEquals("Diego / {{author}}", support.renderer().render("{{expanded}} / {{literal}}", variables).text)
    }

    @Test fun `choice labels show friendly text but insert their Espanso ids`() {
        val variable = TemplateVariable(
            "priority",
            "choice",
            """{"values":[{"label":"Urgent","id":"P1"},{"label":"Normal","id":"P2"}]}""",
        )
        val rendered = support.renderer().render("Priority: {{priority}}", listOf(variable))

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
        val nested = TemplateTestSupport.textMatch(
            ":nested",
            "{{shared}}/{{global_only}}",
            vars = listOf(TemplateTestSupport.echo("shared", "nested")),
        )
        val match = TemplateTestSupport.textMatch(
            ":main",
            "{{shared}} {{global_only}} {{sig}}",
            vars = listOf(
                TemplateTestSupport.echo("shared", "local"),
                TemplateTestSupport.matchVar("sig", ":nested"),
            ),
        )
        val rendered = support.renderer(matchResolver = { if (it == ":nested") nested else null }).render(
            match,
            globalVariables = listOf(
                TemplateTestSupport.echo("shared", "global"),
                TemplateTestSupport.echo("global_only", "available"),
            ),
        )

        assertEquals("local available nested/available", rendered.text)
    }

    @Test fun `form variable with cursor marker collects structured input`() {
        val name = TemplateTestSupport.form("name", "[[who=Diego]]")
        val rendered = support.renderer(clock = support.fixedClock).render("Hi {{name}} $|$", listOf(name))

        assertEquals("Hi Diego ", rendered.text)
        assertEquals(1, rendered.fields.size)
        assertEquals("NAME_WHO", rendered.fields.single().name)
        assertEquals(3, rendered.fields.single().start)
        assertEquals(8, rendered.fields.single().end)
        assertTrue(rendered.requiresInput)
        assertEquals("Hi Ana ", rendered.fillFields(mapOf("NAME_WHO" to "Ana")).text)
    }

    @Test fun `repeated form fields and cursor keep offsets after filling`() {
        val name = TemplateTestSupport.form("name", "[[who=Diego]]")
        val rendered = support.renderer().render("A {{name}} $|$ B {{name}}", listOf(name))

        assertEquals("A Diego  B Diego", rendered.text)
        assertEquals("A Diego ".length, rendered.cursorOffset)
        assertEquals(0, rendered.fields[0].occurrence)
        assertEquals(1, rendered.fields[1].occurrence)

        val filled = rendered.fillFields(mapOf("NAME_WHO" to "Ana"))
        assertEquals("A Ana  B Ana", filled.text)
        assertEquals("A Ana ".length, filled.cursorOffset)
    }

    @Test fun `date offsets and nested match variables preserve form fields`() {
        val nested = TemplateTestSupport.textMatch(
            ":sig",
            "Regards, {{sig}}",
            vars = listOf(TemplateTestSupport.form("sig", "[[name]]")),
        )
        val rendered = support.renderer(clock = support.fixedClock, matchResolver = { if (it == ":sig") nested else null }).render(
            "{{later}} {{sig}}",
            variables = listOf(
                TemplateTestSupport.dateVar("later", "yyyy-MM-dd", offsetSeconds = 2 * 24 * 3600),
                TemplateTestSupport.matchVar("sig", ":sig"),
            ),
        )

        assertEquals("2026-08-21 Regards, ", rendered.text)
        assertEquals("SIG_NAME", rendered.fields.single().name)
        assertEquals("2026-08-21 Regards, Ana", rendered.fillFields(mapOf("SIG_NAME" to "Ana")).text)
    }

    @Test fun `meeting template with nested signature renders without initializing failure`() {
        val signature = TemplateTestSupport.textMatch(";sig", "Regards,\nDiego")
        val meeting = TemplateTestSupport.textMatch(
            ";meeting",
            "Hi Sam,\n\nMeeting: {{when}}\n\n{{sig}}$|$",
            vars = listOf(
                TemplateTestSupport.dateVar("when", "yyyy-MM-dd"),
                TemplateTestSupport.matchVar("sig", ";sig"),
            ),
        )
        val rendered = support.renderer(clock = support.fixedClock, matchResolver = { if (it == ";sig") signature else null }).render(meeting)

        assertEquals("Hi Sam,\n\nMeeting: 2026-08-19\n\nRegards,\nDiego", rendered.text)
        assertEquals(rendered.text.length, rendered.cursorOffset)
    }

    @Test fun `verbose form variable exposes dotted field names`() {
        val form1 = TemplateTestSupport.form(
            "form1",
            "Name: [[name]] / [[kind]]",
            """{"name":{"default":"Diego"},"kind":{"type":"choice","values":["one","two"]}}""",
        )
        val rendered = support.renderer().render("{{form1}} -> {{form1.name}}", listOf(form1))

        assertEquals("Name: Diego / one -> Diego", rendered.text)
        assertEquals(listOf("form1.name", "form1.kind", "form1.name"), rendered.fields.map { it.label })
        assertEquals(TemplateFieldInputType.TEXT, rendered.fields[0].inputType)
        assertEquals(TemplateFieldInputType.CHOICE, rendered.fields[1].inputType)
        assertEquals(listOf("one", "two"), rendered.fields[1].options)
        assertEquals("Name: Ana / one -> Ana", rendered.fillFields(mapOf("FORM1_NAME" to "Ana")).text)
    }

    @Test fun `form layouts support inline defaults and multiline controls`() {
        val report = TemplateTestSupport.form(
            "report",
            "Owner: [[owner=Diego]]\nSummary: [[summary]]",
            """{"summary":{"multiline":true}}""",
        )
        val rendered = support.renderer().render("{{report}}", listOf(report))

        assertEquals("Owner: Diego\nSummary: ", rendered.text)
        assertEquals("Diego", rendered.fields[0].defaultValue)
        assertTrue(rendered.fields[1].multiline)
    }

    @Test fun `recursive match variables are bounded`() {
        val loop = TemplateTestSupport.textMatch(
            ":loop",
            "{{loop}}",
            vars = listOf(TemplateTestSupport.matchVar("loop", ":loop")),
        )
        val rendered = support.renderer(matchResolver = { loop }).render("{{loop}}")

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

    @Test fun `backspace treats a variable reference as one character`() {
        val text = "hello {{name}} world"
        val cursor = "hello {{name}}".length
        val result = TemplateTokenEditor.deleteBackward(text, cursor)

        assertEquals("hello  world", result.text)
        assertEquals("hello ".length, result.cursor)
    }

    @Test fun `named variables are clickable and atomic tokens`() {
        val text = "Hello {{today}} and {{form.name}}"

        assertEquals("today", TemplateTokenEditor.variableNameAt(text, text.indexOf("today") + 2))
        assertEquals("form", TemplateTokenEditor.variableNameAt(text, text.indexOf("form") + 1))
        assertNull(TemplateTokenEditor.variableNameAt(text, text.indexOf("{{today}}")))
        assertNull(TemplateTokenEditor.variableNameAt(text, text.indexOf("{{today}}") + "{{today}}".length))

        val cursor = "Hello {{today}}".length
        val deleted = TemplateTokenEditor.deleteBackward(text, cursor)
        assertEquals("Hello  and {{form.name}}", deleted.text)
    }

    @Test fun `named regex captures are clickable only inside and delete atomically`() {
        val text = "ETA {{unit}} / {{minutes}}"
        val namedStart = text.indexOf("{{minutes}}")

        assertNull(TemplateTokenEditor.captureReferenceAt(text, namedStart))
        assertEquals(
            "{{minutes}}",
            TemplateTokenEditor.captureReferenceAt(text, namedStart + 3, setOf("minutes"))?.token,
        )
        assertEquals("ETA {{unit}} / ", TemplateTokenEditor.deleteBackward(text, namedStart + "{{minutes}}".length).text)
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

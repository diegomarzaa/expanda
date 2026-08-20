package dev.diego.expanda.engine

import dev.diego.expanda.data.Snippet
import dev.diego.expanda.data.TemplateSelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

class TemplateFeaturesTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-19T10:15:30Z"), ZoneOffset.UTC)

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

    @Test fun `modern date offsets and nested snippets are supported`() {
        val renderer = TemplateRenderer(
            clock = fixedClock,
            snippetResolver = { if (it == "sig") "Regards, {FORM: NAME}" else null },
        )
        val rendered = renderer.render("{DATE:+2:DAY:yyyy-MM-dd} {SNIPPET: sig}")

        assertEquals("2026-08-21 Regards, ", rendered.text)
        assertEquals("NAME", rendered.fields.single().name)
        assertEquals("2026-08-21 Regards, Ana", rendered.fillFields(mapOf("NAME" to "Ana")).text)
    }

    @Test fun `recursive snippets are bounded`() {
        val renderer = TemplateRenderer(snippetResolver = { "{SNIPPET:loop}" })
        assertTrue(renderer.render("{SNIPPET:loop}").text.length < 100)
    }

    @Test fun `template selector supports all strategies`() {
        val first = Snippet(shortcut = "/x", content = "one", templates = listOf("two"))
        assertEquals("one", TemplateSelector().select(first).text)
        assertEquals("two", TemplateSelector().select(first.copy(selectionMode = TemplateSelectionMode.MANUAL), 1).text)
        assertEquals("two", TemplateSelector().select(first.copy(selectionMode = TemplateSelectionMode.SEQUENTIAL, templateIndex = 1)).text)
        assertTrue(TemplateSelector(Random(1)).select(first.copy(selectionMode = TemplateSelectionMode.RANDOM)).text in setOf("one", "two"))
    }

    @Test fun `backspace treats a token as one character`() {
        val text = "hello {FORM: NAME} world"
        val cursor = "hello {FORM: NAME}".length
        val result = TemplateTokenEditor.deleteBackward(text, cursor)
        assertEquals("hello  world", result.text)
        assertEquals("hello ".length, result.cursor)
    }
}

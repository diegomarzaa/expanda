package dev.diego.expanda.engine

import dev.diego.expanda.data.Snippet
import dev.diego.expanda.data.TriggerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

class ExpansionEngineTest {
    private val engine = ExpansionEngine()

    @Test fun `delimiter expansion preserves delimiter and cursor`() {
        val snippet = Snippet(id = 1, shortcut = ";mail", content = "hello@example.com")
        val input = "Send to ;mail "
        val match = engine.findMatch(input, input.length, listOf(snippet), "com.example")!!
        val applied = engine.applyMatch(input, match, RenderedTemplate(snippet.content, snippet.content.length))

        assertEquals("Send to hello@example.com ", applied.text)
        assertEquals(applied.text.length, applied.cursor)
    }

    @Test fun `instant expansion works without delimiter`() {
        val snippet = Snippet(shortcut = "addr", content = "Main Street", triggerMode = TriggerMode.INSTANT)
        val input = "addr"
        val match = engine.findMatch(input, input.length, listOf(snippet), "com.example")!!
        assertEquals(0, match.replaceFrom)
    }

    @Test fun `longest shortcut wins`() {
        val short = Snippet(id = 1, shortcut = "mail", content = "A", triggerMode = TriggerMode.INSTANT)
        val long = Snippet(id = 2, shortcut = ";mail", content = "B", triggerMode = TriggerMode.INSTANT)
        val match = engine.findMatch(";mail", 5, listOf(short, long), "com.example")!!
        assertEquals(2, match.snippet.id)
    }

    @Test fun `excluded package does not expand`() {
        val snippet = Snippet(shortcut = "pw", content = "secret", excludedPackages = setOf("bank.app"))
        assertNull(engine.findMatch("pw ", 3, listOf(snippet), "bank.app"))
    }

    @Test fun `template variables and cursor are deterministic`() {
        val renderer = TemplateRenderer(
            clock = Clock.fixed(Instant.parse("2026-08-19T10:15:30Z"), ZoneOffset.UTC),
            clipboard = { "clip" },
            random = Random(1),
        )
        val result = renderer.render("{{date}} {{clipboard}} {{cursor}}end")
        assertEquals("2026-08-19 clip end", result.text)
        assertEquals("2026-08-19 clip ".length, result.cursorOffset)
    }

    @Test fun `Typing Hero legacy placeholders and snippet references are supported`() {
        val renderer = TemplateRenderer(
            clipboard = { "copied" },
            snippetResolver = { if (it == "sig") "Regards,{enter}Diego" else null },
        )
        val result = renderer.render("{clipboard} {snippet:sig} {cursor}")
        assertEquals("copied Regards,\nDiego ", result.text)
        assertEquals(result.text.length, result.cursorOffset)
    }

    @Test fun `past and future Typing Hero date placeholders are supported`() {
        val renderer = TemplateRenderer(
            clock = Clock.fixed(Instant.parse("2026-08-19T10:15:30Z"), ZoneOffset.UTC),
        )
        assertEquals("2026-08-21 09:15", renderer.render("%futuredate:2:DAY:yyyy-MM-dd% %pasttime:1:HOUR:HH:mm%").text)
    }
}

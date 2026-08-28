package dev.diego.expanda.engine

import dev.diego.expanda.data.MatchOptions
import dev.diego.expanda.data.MatchTrigger
import dev.diego.expanda.data.TextMatch
import dev.diego.expanda.data.TriggerActivation
import dev.diego.expanda.data.TriggerKind
import dev.diego.expanda.data.UppercaseStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionEngineTest {
    private val engine = ExpansionEngine()

    @Test fun `literal multi-trigger expansion preserves delimiter and cursor`() {
        val match = textMatch(
            id = 1,
            triggers = listOf("mail", "email"),
            replacement = "hello@example.com",
            options = MatchOptions(activation = TriggerActivation.DELIMITER),
        )
        val input = "Send to email "

        val found = engine.findMatch(input, input.length, listOf(match), "com.example")!!
        val applied = engine.applyMatch(input, found, RenderedTemplate(match.replace, match.replace.length))

        assertEquals("email", found.matchedText)
        assertEquals(" ", found.trailingDelimiter)
        assertEquals("Send to hello@example.com ", applied.text)
        assertEquals(applied.text.length, applied.cursor)
    }

    @Test fun `immediate literal expansion works without delimiter`() {
        val match = textMatch(
            triggers = listOf("addr"),
            replacement = "Main Street",
            options = MatchOptions(activation = TriggerActivation.IMMEDIATE),
        )

        val found = engine.findMatch("addr", 4, listOf(match), "com.example")!!
        assertEquals(0, found.replaceFrom)
        assertEquals(4, found.replaceTo)
    }

    @Test fun `propagate case defaults to first-letter capitalization`() {
        val match = textMatch(
            triggers = listOf(";hello"),
            replacement = "hello world",
            options = MatchOptions(
                caseSensitive = false,
                activation = TriggerActivation.IMMEDIATE,
                propagateCase = true,
            ),
        )
        val found = engine.findMatch(";Hello", 6, listOf(match), "")!!
        val applied = engine.applyMatch(
            ";Hello",
            found,
            RenderedTemplate(match.replace, match.replace.length),
        )
        assertEquals("Hello world", applied.text)
    }

    @Test fun `duplicate triggers return all candidates`() {
        val first = textMatch(id = 1, triggers = listOf(":quote"), replacement = "One", immediate = true)
        val second = textMatch(id = 2, triggers = listOf(":quote"), replacement = "Two", immediate = true)
        val candidates = engine.findMatchesAtCursor(":quote", 6, listOf(first, second), "")
        assertEquals(2, candidates.size)
        assertEquals(setOf(1L, 2L), candidates.map { it.match.id }.toSet())
    }

    @Test fun `longest trigger still wins over shorter duplicate trigger`() {
        val short = textMatch(id = 1, triggers = listOf("mail"), replacement = "A", immediate = true)
        val long = textMatch(id = 2, triggers = listOf("email"), replacement = "B", immediate = true)

        val found = engine.findMatch("email", 5, listOf(short, long), "com.example")!!
        assertEquals(2, found.match.id)
    }

    @Test fun `word boundaries apply independently on both sides`() {
        val word = textMatch(
            triggers = listOf("cat"),
            replacement = "feline",
            options = MatchOptions(
                activation = TriggerActivation.IMMEDIATE,
                leftWord = true,
                rightWord = true,
            ),
        )

        assertNull(engine.findMatch("bobcat", 6, listOf(word), ""))
        assertNull(engine.findMatch("catapult", 3, listOf(word), ""))
        assertTrue(engine.findMatch("a cat!", 5, listOf(word), "") != null)
    }

    @Test fun `regex suffix exposes named captures in replacements`() {
        val match = textMatch(
            triggers = listOf("greet\\((?P<person>[^)]+),(?P<id>\\d+)\\)"),
            triggerKind = TriggerKind.REGEX,
            replacement = "Hi {{person}} #{{id}}",
            immediate = true,
        )
        val input = "greet(Bob,42)"

        val found = engine.findMatch(input, input.length, listOf(match), "")!!
        val rendered = TemplateRenderer().render(match.replace, found)

        assertEquals(input, found.matchedText)
        assertEquals(listOf("Bob", "42"), found.captureGroups)
        assertEquals("Bob", found.namedCaptureGroups["person"])
        assertEquals("42", found.namedCaptureGroups["id"])
        assertEquals("Hi Bob #42", rendered.text)
    }

    @Test fun `invalid regex is ignored without breaking other matches`() {
        val invalid = textMatch(
            triggers = listOf("["),
            triggerKind = TriggerKind.REGEX,
            replacement = "bad",
            immediate = true,
        )
        val valid = textMatch(triggers = listOf("ok"), replacement = "good", immediate = true)

        assertEquals("good", engine.findMatch("ok", 2, listOf(invalid, valid), "")!!.match.replace)
        assertNull(engine.findMatch("[", 1, listOf(invalid), ""))
    }

    @Test fun `propagate case follows lowercase titlecase and uppercase input`() {
        val capitalize = textMatch(
            triggers = listOf("hello"),
            replacement = "good morning",
            options = MatchOptions(
                caseSensitive = false,
                activation = TriggerActivation.IMMEDIATE,
                propagateCase = true,
                uppercaseStyle = UppercaseStyle.CAPITALIZE,
            ),
        )
        val titleWords = capitalize.copy(
            options = capitalize.options.copy(uppercaseStyle = UppercaseStyle.CAPITALIZE_WORDS),
        )

        fun apply(source: String, value: TextMatch): String {
            val found = engine.findMatch(source, source.length, listOf(value), "")!!
            return engine.applyMatch(source, found, RenderedTemplate(value.replace, value.replace.length)).text
        }

        assertEquals("good morning", apply("hello", capitalize))
        assertEquals("Good morning", apply("Hello", capitalize))
        assertEquals("GOOD MORNING", apply("HELLO", capitalize))
        assertEquals("Good Morning", apply("Hello", titleWords))
    }

    @Test fun `propagate case keeps cursor after length-changing uppercase`() {
        val match = textMatch(
            triggers = listOf("SS"),
            replacement = "aßz",
            immediate = true,
            options = MatchOptions(propagateCase = true),
        )
        val found = engine.findMatch("SS", 2, listOf(match), "")!!
        val applied = engine.applyMatch("SS", found, RenderedTemplate("aßz", 2))

        assertEquals("ASSZ", applied.text)
        assertEquals(3, applied.cursor)
    }

    @Test fun `case sensitive trigger rejects wrong casing`() {
        val match = textMatch(
            triggers = listOf(";GPU"),
            replacement = "Graphics Processing Unit",
            options = MatchOptions(caseSensitive = true, activation = TriggerActivation.IMMEDIATE),
        )
        assertTrue(engine.findMatch(";GPU", 4, listOf(match), "") != null)
        assertNull(engine.findMatch(";gpu", 4, listOf(match), ""))
    }

    @Test fun `excluded package does not expand`() {
        val match = textMatch(
            triggers = listOf("pw"),
            replacement = "secret",
            immediate = true,
            excludedPackages = setOf("bank.app"),
        )
        assertNull(engine.findMatch("pw", 2, listOf(match), "bank.app"))
    }

    private fun textMatch(
        id: Long = 0,
        triggers: List<String>,
        replacement: String,
        options: MatchOptions = MatchOptions(),
        immediate: Boolean = false,
        triggerKind: TriggerKind = TriggerKind.TEXT,
        excludedPackages: Set<String> = emptySet(),
    ): TextMatch = TextMatch(
        id = id,
        triggers = triggers.map { MatchTrigger(it, triggerKind) },
        replacements = listOf(replacement),
        options = if (immediate) options.copy(activation = TriggerActivation.IMMEDIATE) else options,
        excludedPackages = excludedPackages,
    )
}

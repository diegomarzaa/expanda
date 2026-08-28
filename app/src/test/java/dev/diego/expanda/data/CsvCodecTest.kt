package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvCodecTest {
    @Test fun `round trips canonical triggers replacements and useful fields`() {
        val original = TextMatch(
            triggers = listOf(
                MatchTrigger(";a"),
                MatchTrigger("a+", TriggerKind.REGEX),
            ),
            replacements = listOf("Hello, \"Diego\"\nSecond line", "Other"),
            label = "Greeting",
            tags = setOf("personal", "email"),
            searchTerms = setOf("hello"),
            options = MatchOptions(
                caseSensitive = true,
                activation = TriggerActivation.IMMEDIATE,
                delimiters = " ,",
                leftWord = true,
                propagateCase = true,
                uppercaseStyle = UppercaseStyle.CAPITALIZE,
            ),
            vars = listOf(TemplateVariable("name", "form", "{\"default\":\"Diego\"}")),
            excludedPackages = setOf("com.example.app"),
            selectionMode = TemplateSelectionMode.SEQUENTIAL,
            templateIndex = 4,
            usageCount = 8,
        )

        val encoded = CsvCodec.encode(listOf(original))
        assertTrue(encoded.startsWith("trigger,replace,"))
        assertFalse(encoded.lineSequence().first().contains("shortcut"))
        assertFalse(encoded.lineSequence().first().contains("content"))

        val decoded = CsvCodec.decode(encoded).single()
        assertEquals(original, decoded)
    }

    @Test fun `legacy shortcut content and folder are accepted only at decode`() {
        val csv = "shortcut,content,folder\n/x,hello,Work\n"

        val decoded = CsvCodec.decode(csv).single()
        assertEquals(listOf(MatchTrigger("/x")), decoded.triggers)
        assertEquals(listOf("hello"), decoded.replacements)
        assertEquals(setOf("Work"), decoded.tags)
    }

    @Test fun `legacy templates and trigger mode map to canonical fields`() {
        val csv = "shortcut,content,templates,triggerMode,caseSensitive\n" +
            "/hello,First," +
            "\"[\\\"Second, with comma\\\",\\\"Third\\\\nline\\\"]\",INSTANT,true\n"

        val decoded = CsvCodec.decode(csv).single()
        assertEquals(listOf("First", "Second, with comma", "Third\\nline"), decoded.replacements)
        assertEquals(TriggerActivation.IMMEDIATE, decoded.options.activation)
        assertTrue(decoded.options.caseSensitive)
    }

    @Test fun `legacy csv with a multiline quoted delimiter imports`() {
        val delimiters = " \n\t.,!?;:"
        val csv = buildString {
            appendLine("shortcut,content,label,tags,enabled,caseSensitive,triggerMode,delimiters,excludedPackages,templates,selectionMode,templateIndex")
            appendLine("cyp,Could you please ,,,TRUE,FALSE,INSTANT,\"$delimiters\",,[],FIRST,0")
            appendLine("cyh,Could you please help?,,,TRUE,FALSE,INSTANT,\"$delimiters\",,[],FIRST,1")
        }

        val decoded = CsvCodec.decode(csv)

        assertEquals(2, decoded.size)
        assertEquals("cyp", decoded[0].trigger)
        assertEquals("Could you please ", decoded[0].replace)
        assertEquals(TriggerActivation.IMMEDIATE, decoded[0].options.activation)
        assertTrue(decoded[0].options.delimiters.contains('\n'))
        assertEquals("cyh", decoded[1].trigger)
    }
}

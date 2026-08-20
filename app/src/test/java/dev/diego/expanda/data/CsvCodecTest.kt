package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvCodecTest {
    @Test fun `round trips commas quotes and multiline content`() {
        val original = Snippet(
            shortcut = ";a",
            content = "Hello, \"Diego\"\nSecond line",
            label = "Greeting",
            tags = setOf("personal", "email"),
        )
        val decoded = CsvCodec.decode(CsvCodec.encode(listOf(original))).single()
        assertEquals(original.shortcut, decoded.shortcut)
        assertEquals(original.content, decoded.content)
        assertEquals(original.label, decoded.label)
        assertEquals(original.tags, decoded.tags)
    }

    @Test fun `round trips template variants and selection`() {
        val original = Snippet(
            shortcut = "/hello",
            content = "First {CURSOR}",
            templates = listOf("Second, with comma", "Third\nline"),
            selectionMode = TemplateSelectionMode.SEQUENTIAL,
            templateIndex = 4,
        )
        val decoded = CsvCodec.decode(CsvCodec.encode(listOf(original))).single()
        assertEquals(original.templates, decoded.templates)
        assertEquals(original.selectionMode, decoded.selectionMode)
        assertEquals(original.templateIndex, decoded.templateIndex)
    }
    @Test fun `legacy folder CSV becomes a tag`() {
        val csv = "shortcut,content,folder\n/x,hello,Work\n"
        val decoded = CsvCodec.decode(csv).single()
        assertEquals(setOf("Work"), decoded.tags)
    }

}

package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCodecTest {
    @Test fun `backup preserves variants and strategy`() {
        val original = Snippet(
            shortcut = "/multi",
            content = "one",
            templates = listOf("two", "three"),
            selectionMode = TemplateSelectionMode.RANDOM,
            templateIndex = 9,
            tags = setOf("work", "email"),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(listOf(original))).single()
        assertEquals(original.content, decoded.content)
        assertEquals(original.templates, decoded.templates)
        assertEquals(original.selectionMode, decoded.selectionMode)
        assertEquals(original.templateIndex, decoded.templateIndex)
        assertEquals(original.tags, decoded.tags)
    }

    @Test fun `v1 backup still decodes`() {
        val json = """
            {
              "format":"expanda-backup",
              "version":1,
              "snippets":[{"shortcut":"/x","content":"hello"}]
            }
        """.trimIndent()
        val decoded = BackupCodec.decode(json).single()
        assertEquals("hello", decoded.content)
        assertEquals(emptyList<String>(), decoded.templates)
        assertEquals(TemplateSelectionMode.FIRST, decoded.selectionMode)
    }
    @Test fun `v2 folder becomes a tag`() {
        val json = """
            {
              "format":"expanda-backup",
              "version":2,
              "snippets":[{"shortcut":"/x","content":"hello","folder":"Work"}]
            }
        """.trimIndent()
        val decoded = BackupCodec.decode(json).single()
        assertEquals(setOf("Work"), decoded.tags)
    }

}

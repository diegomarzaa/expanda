package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleSnippetsTest {
    @Test
    fun `examples file contains portable matches with blank lines between them`() {
        val yaml = ExampleSnippets.file.content
        val matches = EspansoYamlCodec.decode(yaml, importsResolved = true).matches

        assertTrue(matches.size >= 30)
        assertTrue(matches.all(TextMatch::runsOnAndroid))
        assertTrue(matches.all(TextMatch::canEditVisually))
        assertTrue(yaml.contains("\n\n  - "))
    }

    @Test
    fun `base import helper adds examples import once`() {
        val updated = ExampleSnippets.baseWithImport(ExampleSnippets.emptyBaseContent())

        assertTrue(updated.contains("imports:"))
        assertTrue(updated.contains(ExampleSnippets.IMPORT_PATH))
        assertEquals(updated, ExampleSnippets.baseWithImport(updated))
    }
}

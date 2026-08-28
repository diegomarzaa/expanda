package dev.diego.expanda.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RegexCaptureDialogTest {
    @Test fun `catalog exposes numbered and named regex captures`() {
        val catalog = regexCaptureCatalog("ETA (\\d+) (?<unit>m|h)")

        assertEquals(listOf("0", "1", "2", "unit"), catalog.options.map { it.reference })
        assertEquals("(\\d+)", catalog.options.first { it.reference == "1" }.source)
        assertEquals("(?<unit>m|h)", catalog.options.first { it.reference == "unit" }.source)
        assertEquals(setOf("unit"), catalog.namedCaptures)
        assertEquals(null, catalog.error)
    }

    @Test fun `catalog reports invalid regex without inventing captures`() {
        val catalog = regexCaptureCatalog("(")

        assertNotNull(catalog.error)
        assertEquals(emptyList<RegexCaptureOption>(), catalog.options)
    }
}

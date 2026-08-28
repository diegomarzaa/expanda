package dev.diego.expanda.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RegexCaptureDialogTest {
    @Test fun `catalog exposes named regex captures only`() {
        val catalog = regexCaptureCatalog("ETA (\\d+) (?<unit>m|h)")

        assertEquals(listOf("unit"), catalog.options.map { it.reference })
        assertEquals("(?<unit>m|h)", catalog.options.single().source)
        assertEquals(setOf("unit"), catalog.namedCaptures)
        assertEquals(null, catalog.error)
    }

    @Test fun `catalog reports invalid regex without inventing captures`() {
        val catalog = regexCaptureCatalog("(")

        assertNotNull(catalog.error)
        assertEquals(emptyList<RegexCaptureOption>(), catalog.options)
    }
}

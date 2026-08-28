package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormFieldLayoutTest {
    @Test
    fun `parses inline defaults from layout`() {
        val layout = "Owner: [[owner=Diego]]\nSummary: [[summary]]"
        assertEquals(
            mapOf("owner" to "Diego"),
            formFieldInlineDefaults(layout),
        )
        assertEquals(listOf("owner", "summary"), formFieldNames(layout))
    }

    @Test
    fun `writes and clears inline defaults`() {
        val layout = "Hello [[name]]"
        assertEquals(
            "Hello [[name=Diego]]",
            setFormFieldInlineDefault(layout, "name", "Diego"),
        )
        assertEquals(
            "Hello [[name]]",
            setFormFieldInlineDefault(
                setFormFieldInlineDefault(layout, "name", "Diego"),
                "name",
                "",
            ),
        )
    }

    @Test
    fun `updates existing inline default`() {
        val layout = "[[channel=Email]]"
        assertEquals(
            "[[channel=Chat]]",
            setFormFieldInlineDefault(layout, "channel", "Chat"),
        )
    }

    @Test
    fun `removes placeholder with or without default`() {
        val layout = "A [[one=1]] B [[two]]"
        assertEquals("A  B [[two]]", removeFormFieldPlaceholder(layout, "one"))
    }
}

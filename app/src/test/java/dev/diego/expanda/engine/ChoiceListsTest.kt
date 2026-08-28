package dev.diego.expanda.engine

import dev.diego.expanda.data.TemplateVariable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChoiceListsTest {
    @Test
    fun `parses bracketed yaml flow list string`() {
        assertEquals(
            listOf("Alpha", "Beta", "Gamma"),
            ChoiceLists.parseAny("['Alpha', 'Beta', 'Gamma']"),
        )
    }

    @Test
    fun `parses double quoted bracketed list`() {
        assertEquals(
            listOf("one", "two"),
            ChoiceLists.parseAny("""["one", "two"]"""),
        )
    }

    @Test
    fun `parses multiline editor text`() {
        assertEquals(
            listOf("Alpha", "Beta", "Gamma"),
            ChoiceLists.parseEditorLines("Alpha\n'Beta'\n\"Gamma\""),
        )
    }

    @Test
    fun `random variable expands from bracketed choices string`() {
        val variable = TemplateVariable(
            "item",
            "random",
            """{"choices":"['Alpha', 'Beta', 'Gamma']"}""",
        )
        val outputs = (0 until 30).map { seed ->
            TemplateRenderer(random = Random(seed)).render("{{item}}", listOf(variable)).text
        }.toSet()
        assertEquals(setOf("Alpha", "Beta", "Gamma"), outputs)
    }

    @Test
    fun `random variable expands from choices array`() {
        val variable = TemplateVariable(
            "item",
            "random",
            """{"choices":["Alpha","Beta","Gamma"]}""",
        )
        val output = TemplateRenderer(random = Random(3)).render("{{item}}", listOf(variable)).text
        assertTrue(output in setOf("Alpha", "Beta", "Gamma"))
    }
}

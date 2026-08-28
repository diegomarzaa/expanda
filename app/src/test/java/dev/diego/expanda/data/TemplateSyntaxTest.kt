package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateSyntaxTest {
    @Test fun `variable reference pattern escapes closing braces for Android ICU`() {
        assertTrue(TEMPLATE_VARIABLE_REFERENCE_PATTERN.contains("\\}\\}"))
        val matches = Regex(TEMPLATE_VARIABLE_REFERENCE_PATTERN)
            .findAll("A {{name}} B {CURSOR} {{user.id}}")
            .toList()
        assertEquals(listOf("{{name}}", "{{user.id}}"), matches.map { it.value })
        assertEquals(listOf("name", "user"), matches.map { it.groupValues[2] })
    }
}

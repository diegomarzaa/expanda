package dev.diego.expanda.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class ActionEngineTest {
    private val engine = ActionEngine()

    @Test fun `math replace consumes trigger and replaces expression`() {
        val text = "The result is 2 + 3*4=="
        val result = engine.execute(ActionContext(text, text.length))
        assertEquals("The result is 14", result?.text)
    }

    @Test fun `longer append math trigger wins`() {
        val text = "2+3,=="
        val result = engine.execute(ActionContext(text, text.length))
        assertEquals("2+3 = 5", result?.text)
        assertEquals("math_append", result?.definition?.id)
    }

    @Test fun `text actions transform and remove command`() {
        val text = "á hola mundo,uu"
        assertEquals("Á HOLA MUNDO", engine.execute(ActionContext(text, text.length))?.text)
        val remove = "áéíóú ñ,rd"
        assertEquals("aeiou n", engine.execute(ActionContext(remove, remove.length))?.text)
    }

    @Test fun `number actions format common styles`() {
        val text = "Importe 12345.67,nfp"
        assertEquals("Importe 12.345,67", engine.execute(ActionContext(text, text.length))?.text)
    }

    @Test fun `selection and clipboard requests are structured`() {
        val selected = engine.execute(ActionContext("hello,aa", 8))
        assertEquals(0, selected?.selectionStart)
        assertEquals(5, selected?.selectionEnd)
        val copied = engine.execute(ActionContext("hello,cc", 8))
        assertEquals(ActionRequest.Copy("hello"), copied?.request)
    }

    @Test fun `paste numbers inserts filtered clipboard`() {
        val text = "Phone: ,pn"
        val result = engine.execute(ActionContext(text, text.length, clipboard = "+34 600-12-34"))
        assertEquals("Phone: 346001234", result?.text)
    }

    @Test fun `disabled or incomplete actions do not run`() {
        val text = "hello,uu"
        assertNull(engine.execute(ActionContext(text, text.length), emptySet()))
        assertNull(engine.execute(ActionContext("hello,u", 7)))
        assertNotNull(engine.execute(ActionContext(text, text.length)))
    }

    @Test fun `custom shortcut replaces the default trigger`() {
        val enabled = setOf("uppercase")
        val overrides = mapOf("uppercase" to ";up")
        assertNull(engine.execute(ActionContext("hello,uu", 8), enabled, overrides))
        assertEquals("HELLO", engine.execute(ActionContext("hello;up", 8), enabled, overrides)?.text)
    }

    @Test fun `typing actions are declared opt in by default`() {
        assertEquals(true, ActionEngine.definitions.all { !it.enabledByDefault })
    }

    @Test fun `selected text actions reuse the canonical transformations`() {
        assertEquals("AE N", engine.processSelectedText("uppercase", "áé ñ")?.let {
            engine.processSelectedText("remove_diacritics", it)
        })
        assertEquals("12.345,67", engine.processSelectedText("number_period", "12345.67"))
        assertEquals("2+3 = 5", engine.processSelectedText("math_append", "2+3"))
        assertNull(engine.processSelectedText("cursor_start", "hello"))
    }

    @Test fun `only context free actions are exposed to Android selected text`() {
        val exposed = ActionEngine.definitions.filter { it.supportsSelectedText }.map { it.id }.toSet()
        assertEquals(true, "uppercase" in exposed)
        assertEquals(true, "delete_blank_lines" in exposed)
        assertEquals(false, "copy_before" in exposed)
        assertEquals(false, "new_snippet" in exposed)
    }

    @Test fun `uuid works from typing and selected text actions`() {
        val selected = engine.processSelectedText("uuid", "replace me")
        assertEquals(selected, UUID.fromString(selected).toString())

        val typed = engine.execute(ActionContext("replace me,uuid", "replace me,uuid".length))?.text
        assertEquals(typed, UUID.fromString(typed).toString())
    }
}

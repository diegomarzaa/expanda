package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EspansoSourceTextTest {
    private val source = """
        # File header stays byte-for-byte
        global_vars:
          - name: author
            type: echo
            params: {echo: "Diego"}

        matches:
          # First explanation
          - trigger: ";one"
            replace: |
              First line
              Second line

          # Keep this comment with the boundary
          - trigger: ';two'
            replace: "Second"

        # Tail stays too
    """.trimIndent() + "\n"

    @Test fun `finds only top-level match items`() {
        assertEquals(2, EspansoSourceText.matchSpans(source).size)
    }

    @Test fun `replacing one match leaves comments and unrelated style intact`() {
        val replacement = """
            - trigger: ";changed"
              replace: "Changed"
        """.trimIndent()
        val changed = EspansoSourceText.replaceMatch(source, 0, replacement)

        assertTrue(changed.startsWith("# File header stays byte-for-byte\n"))
        assertTrue(changed.contains("# Keep this comment with the boundary\n  - trigger: ';two'"))
        assertTrue(changed, changed.contains("  - trigger: \";changed\"\n    replace: \"Changed\"\n"))
        assertFalse(changed.contains("First line"))
        assertTrue(changed.endsWith("# Tail stays too\n"))
    }

    @Test fun `root section replacement does not regenerate matches`() {
        val globals = """
            global_vars:
              - name: project
                type: echo
                params:
                  echo: Expanda
        """.trimIndent()
        val changed = EspansoSourceText.replaceRootSection(source, "global_vars", globals)

        assertTrue(changed.contains("params:\n      echo: Expanda"))
        assertTrue(changed.contains("replace: |\n      First line\n      Second line"))
        assertTrue(changed.contains("# Keep this comment with the boundary"))
        assertEquals(2, EspansoYamlCodec.decode(changed).matches.size)
    }

    @Test fun `first visual match expands an empty inline list safely`() {
        val changed = EspansoSourceText.appendMatches(
            "global_vars: []\nmatches: []\n",
            "- trigger: \";first\"\n  replace: \"Hello\"",
        )

        assertEquals(
            "global_vars: []\nmatches:\n  - trigger: \";first\"\n    replace: \"Hello\"\n",
            changed,
        )
        assertEquals(1, EspansoYamlCodec.decode(changed).matches.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `complex flow match lists require source editing`() {
        EspansoSourceText.appendMatches(
            "matches: [{trigger: ';one', replace: One}]\n",
            "- trigger: \";two\"\n  replace: \"Two\"",
        )
    }

    @Test fun `block scalars stay visual and inline yaml comments require source`() {
        assertEquals(SourceEditMode.VISUAL, EspansoSourceText.visualEditMode(source, 0))
        assertEquals(SourceEditMode.VISUAL, EspansoSourceText.visualEditMode(source, 1))

        val commented = source.replace(
            """- trigger: ";one"""",
            """- trigger: ";one"  # keep exact casing""",
        )
        assertEquals(SourceEditMode.SOURCE_ONLY, EspansoSourceText.visualEditMode(commented, 0))
    }
}

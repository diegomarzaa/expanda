package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyTemplateMigratorTest {
    @Test fun `detects legacy single-brace tokens but ignores espanso syntax`() {
        assertTrue(LegacyTemplateMigrator.containsLegacySyntax("{CLIPBOARD}"))
        assertTrue(LegacyTemplateMigrator.containsLegacySyntax("%cursor%"))
        assertFalse(LegacyTemplateMigrator.containsLegacySyntax("Hello {{name}} and $|$"))
    }

    @Test fun `migrates the original 0_2 sample library`() {
        val matches = listOf(
            textMatch("6", "{SNIPPET: 1}"),
            textMatch("5", "{FORM: text}"),
            textMatch("4", "{DATE:yyyy-MM-dd} \n{TIME:HH:mm}"),
            textMatch("3", "Clipboard: {CLIPBOARD}"),
            textMatch("2", "two\nlines \n\nwow"),
            textMatch("1", "easy line"),
        ).map(LegacyTemplateMigrator::migrateMatch)

        assertEquals("{{snippet}}", matches[0].replacements.single())
        assertEquals("1", matches[0].vars.single().paramsJson.substringAfter("trigger\":\"").substringBefore("\""))

        assertEquals("{{form}}", matches[1].replacements.single())
        assertTrue(matches[1].vars.single().paramsJson.contains("[[text]]"))

        assertEquals("{{date}} \n{{date_2}}", matches[2].replacements.single())
        assertEquals(2, matches[2].vars.count { it.type == "date" })

        assertEquals("Clipboard: {{clip}}", matches[3].replacements.single())
        assertEquals("clipboard", matches[3].vars.single().type)

        assertEquals("two\nlines \n\nwow", matches[4].replacements.single())
        assertEquals("easy line", matches[5].replacements.single())
    }

    @Test fun `cursor clipboard and form defaults migrate`() {
        val migration = LegacyTemplateMigrator.migrateReplacement("Hi {FORM: NAME|Diego} {CURSOR}")

        assertEquals("Hi {{form}} $|$", migration.replace)
        assertEquals("form", migration.vars.single().name)
        assertTrue(migration.vars.single().paramsJson.contains("[[NAME=Diego]]"))
    }

    @Test fun `percent and bracket legacy aliases migrate`() {
        val migration = LegacyTemplateMigrator.migrateReplacement("%cursor% [clipboard] %snippet:;sig%")

        assertEquals("$|$ {{clip}} {{snippet}}", migration.replace)
        assertEquals(setOf("clip", "snippet"), migration.vars.map { it.name }.toSet())
        assertEquals(";sig", migration.vars.single { it.type == "match" }.paramsJson.substringAfter("trigger\":\"").substringBefore("\""))
    }

    @Test fun `relative dates and offset percent forms migrate`() {
        val migration = LegacyTemplateMigrator.migrateReplacement(
            "{DATE:+2:DAY:yyyy-MM-dd} and %futuredate:1:HOUR:HH:mm%",
        )

        assertEquals("{{date}} and {{date_2}}", migration.replace)
        assertTrue(migration.vars[0].paramsJson.contains("\"offset\":${2 * 86_400}"))
        assertTrue(migration.vars[1].paramsJson.contains("\"offset\":${3_600}"))
    }

    @Test fun `send newline and transforms are handled`() {
        val migration = LegacyTemplateMigrator.migrateReplacement("{enter}{upper:hi}{send}")

        assertEquals("\nHI", migration.replace)
        assertTrue(migration.notes.any { it.contains("SEND") })
    }

    @Test fun `existing variable names are not reused`() {
        val existing = listOf(TemplateVariable("clip", "clipboard"))
        val migration = LegacyTemplateMigrator.migrateReplacement("{CLIPBOARD}", existing)

        assertEquals("{{clip_2}}", migration.replace)
        assertEquals("clip_2", migration.vars.single().name)
    }

    @Test fun `already migrated text is left untouched`() {
        val original = "Hello {{name}} $|$"
        val migration = LegacyTemplateMigrator.migrateReplacement(original)

        assertEquals(original, migration.replace)
        assertTrue(migration.vars.isEmpty())
    }

    private fun textMatch(trigger: String, replace: String) = TextMatch(
        triggers = listOf(MatchTrigger(trigger)),
        replacements = listOf(replace),
    )
}

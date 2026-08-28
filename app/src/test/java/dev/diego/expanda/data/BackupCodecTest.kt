package dev.diego.expanda.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test fun `current backup uses canonical payloads and includes sources and app state`() {
        val original = TextMatch(
            triggers = listOf(MatchTrigger("/multi"), MatchTrigger("/m")),
            replacements = listOf("one", "", "two"),
            options = MatchOptions(
                caseSensitive = true,
                activation = TriggerActivation.IMMEDIATE,
                leftWord = true,
            ),
            vars = listOf(TemplateVariable("name", "form")),
            selectionMode = TemplateSelectionMode.RANDOM,
            templateIndex = 9,
        )

        val settings = AppSettings(
            themeMode = ThemeMode.AMOLED,
            textScale = 1.35f,
            globallyExcludedPackages = setOf("com.example.private"),
            globalVariables = listOf(TemplateVariable("name", "echo", """{"value":"Diego"}""")),
            suggestionMaxHeightDp = 440,
            suggestionWidthFraction = 0.68f,
            suggestionResizeHandleEnabled = false,
        )
        val actions = BackupCodec.ActionSnapshot(
            enabledIds = setOf("uppercase"),
            shortcutOverrides = mapOf("uppercase" to ",up"),
        )
        val sources = listOf(
            EspansoSourceFile("base.yml", "# exact comment\nmatches:\n  - trigger: /multi\n    replace: one\n"),
        )
        val encoded = BackupCodec.encode(listOf(original), settings, actions, sources)
        val root = JSONObject(encoded)
        val entry = root.getJSONArray("matches").getJSONObject(0)
        assertEquals(BackupCodec.FORMAT_VERSION, root.getInt("version"))
        assertTrue(entry.has("triggers"))
        assertTrue(entry.has("replacements"))
        assertFalse(entry.has("shortcut"))
        assertFalse(entry.has("content"))

        val decoded = BackupCodec.decodeWithGlobals(encoded)
        assertEquals(original.copy(id = 0), decoded.matches.single())
        assertTrue(decoded.isFullBackup)
        assertEquals(settings.globalVariables, decoded.globalVariables)
        assertEquals(ThemeMode.AMOLED, decoded.settings?.themeMode)
        assertEquals(1.35f, decoded.settings?.textScale)
        assertEquals(440, decoded.settings?.suggestionMaxHeightDp)
        assertEquals(0.68f, decoded.settings?.suggestionWidthFraction)
        assertEquals(false, decoded.settings?.suggestionResizeHandleEnabled)
        assertEquals(actions, decoded.actions)
        assertEquals(sources, decoded.sourceFiles)
        assertFalse(root.getJSONObject("settings").has("suggestionPositionX"))
    }

    @Test fun `v1 backup is adapted to canonical model`() {
        val json = """
            {
              "format":"expanda-backup",
              "version":1,
              "snippets":[{"shortcut":"/x","content":"hello"}]
            }
        """.trimIndent()

        val decoded = BackupCodec.decode(json).single()
        assertEquals(listOf(MatchTrigger("/x")), decoded.triggers)
        assertEquals(listOf("hello"), decoded.replacements)
        assertEquals(TriggerActivation.DELIMITER, decoded.options.activation)
    }

    @Test fun `v2 folder and v3 fields are adapted once`() {
        val json = """
            {
              "format":"expanda-backup",
              "version":3,
              "snippets":[{
                "shortcut":"/x",
                "content":"hello",
                "templates":["bye"],
                "aliases":["/alias"],
                "folder":"Work",
                "matchKind":"REGEX",
                "regexTrigger":"x+",
                "triggerMode":"INSTANT",
                "leftWord":true,
                "propagateCase":true,
                "uppercaseStyle":"CAPITALIZE_WORDS"
              }]
            }
        """.trimIndent()

        val decoded = BackupCodec.decode(json).single()
        assertEquals(listOf(MatchTrigger("x+", TriggerKind.REGEX)), decoded.triggers)
        assertEquals(listOf("hello", "bye"), decoded.replacements)
        assertEquals(setOf("Work"), decoded.tags)
        assertEquals(TriggerActivation.IMMEDIATE, decoded.options.activation)
        assertTrue(decoded.options.leftWord)
        assertTrue(decoded.options.propagateCase)
        assertEquals(UppercaseStyle.CAPITALIZE_WORDS, decoded.options.uppercaseStyle)
    }

    @Test fun `v4 canonical snippet backup remains importable without becoming a full restore`() {
        val json = """
            {
              "format":"expanda-backup",
              "version":4,
              "matches":[{
                "triggers":[{"pattern":";hello","kind":"TEXT"}],
                "replacements":["Hello"],
                "globalVariables":[]
              }],
              "globalVariables":[{"name":"name","type":"echo","params":{"value":"Diego"}}]
            }
        """.trimIndent()

        val decoded = BackupCodec.decodeWithGlobals(json)
        assertEquals(";hello", decoded.matches.single().trigger)
        assertFalse(decoded.isFullBackup)
        assertEquals("name", decoded.globalVariables.single().name)
    }
}

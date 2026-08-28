package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class EspansoYamlCodecTest {
    @Test fun `imports form fields regex options and native cursor without rewriting them`() {
        val yaml = """
            imports:
              - other.yml
            matches:
              - triggers: [":hello", ":hi"]
                form: |
                  Hello [[name]],${'$'}|${'$'}
                form_fields:
                  name:
                    default: "Sam"
                    multiline: true
                word: true
                propagate_case: true
                uppercase_style: capitalize_words
                search_terms: [greeting]
              - regex: "\\\\d+"
                replace: "number$|$"
        """.trimIndent()

        val result = EspansoYamlCodec.decode(yaml)
        val literal = result.matches[0]
        assertEquals(
            listOf(MatchTrigger(":hello"), MatchTrigger(":hi")),
            literal.triggers,
        )
        assertEquals("Hello {{form1.name}},$|$\n", literal.replace)
        assertTrue(literal.options.leftWord)
        assertTrue(literal.options.rightWord)
        assertTrue(literal.options.propagateCase)
        assertEquals(UppercaseStyle.CAPITALIZE_WORDS, literal.options.uppercaseStyle)
        assertEquals(setOf("greeting"), literal.searchTerms)
        assertEquals("form1", literal.vars.single().name)
        val fields = JSONObject(literal.vars.single().paramsJson).getJSONObject("fields")
        assertEquals("Sam", fields.getJSONObject("name").getString("default"))
        assertTrue(fields.getJSONObject("name").getBoolean("multiline"))
        assertEquals(TriggerKind.REGEX, result.matches[1].triggers.single().kind)
        assertEquals("number$|$", result.matches[1].replace)
        assertTrue(result.issues.any { it.message.contains("imports") })
    }

    @Test fun `exports cursor with valid Kotlin escaping and reports desktop-only fields`() {
        val match = TextMatch(
            triggers = listOf(MatchTrigger(":hello")),
            replacements = listOf("Hello $|$"),
            options = MatchOptions(activation = TriggerActivation.DELIMITER),
            excludedPackages = setOf("com.example.desktop"),
            tags = setOf("personal"),
        )

        val result = EspansoYamlCodec.encode(listOf(match))
        assertTrue(result.yaml.contains("${'$'}|${'$'}"))
        assertTrue(result.issues.any { it.message.contains("exclusions") })
        assertTrue(result.issues.any { it.message.contains("tags") })
        assertTrue(result.issues.any { it.severity == CompatibilitySeverity.INFO })

        val decoded = EspansoYamlCodec.decode(result.yaml).matches.single()
        assertEquals("Hello $|$", decoded.replace)
    }

    @Test fun `does not execute unsupported shell variables`() {
        val yaml = """
            matches:
              - trigger: :shell
                replace: "{{command}}"
                vars:
                  - name: command
                    type: shell
                    params:
                      cmd: "touch /tmp/should-not-run"
                force_mode: clipboard
        """.trimIndent()

        val result = EspansoYamlCodec.decode(yaml)
        assertEquals("shell", result.matches.single().vars.single().type)
        assertTrue(result.issues.any { it.message.contains("not executed") })
        assertTrue(result.issues.any { it.message.contains("force_mode") })
        assertTrue(result.matches.single().compatibilityWarnings.any { it.contains("not executed") })
        assertTrue(result.matches.single().compatibilityWarnings.any { it.contains("force_mode") })
    }

    @Test fun `global vars stay global through YAML round trip`() {
        val globals = listOf(
            TemplateVariable("tomorrow", "date", """{"format":"%Y-%m-%d","offset":86400}"""),
        )
        val match = TextMatch(
            triggers = listOf(MatchTrigger(":tomorrow")),
            replacements = listOf("Tomorrow is {{tomorrow}}."),
        )

        val encoded = EspansoYamlCodec.encode(listOf(match), globals)
        val decoded = EspansoYamlCodec.decode(encoded.yaml)

        assertEquals(globals.single().name, decoded.globalVariables.single().name)
        assertEquals(globals.single().type, decoded.globalVariables.single().type)
        assertEquals(86400L, JSONObject(decoded.globalVariables.single().paramsJson).optLong("offset"))
        assertEquals("%Y-%m-%d", JSONObject(decoded.globalVariables.single().paramsJson).optString("format"))
        assertTrue(decoded.matches.single().vars.isEmpty())
        assertTrue(encoded.yaml.contains("global_vars"))
    }

    @Test fun `inject vars and choice ids survive YAML semantics`() {
        val yaml = """
            matches:
              - trigger: ":portable"
                replace: "{{raw}} / {{priority}}"
                vars:
                  - name: raw
                    type: echo
                    inject_vars: false
                    params:
                      echo: "{{not_expanded}}"
                  - name: priority
                    type: choice
                    params:
                      values:
                        - label: "Urgent"
                          id: "P1"
        """.trimIndent()

        val decoded = EspansoYamlCodec.decode(yaml).matches.single()
        assertEquals(false, decoded.vars.first { it.name == "raw" }.injectVars)
        val encoded = EspansoYamlCodec.encode(listOf(decoded)).yaml
        assertTrue(encoded.contains("inject_vars"))
        assertTrue(encoded.contains("P1"))
    }

    @Test fun `accepts imports or globals only source files`() {
        val globalsOnly = """
            # Shared variables
            global_vars:
              - name: author
                type: echo
                params: {echo: Diego}
        """.trimIndent()

        val result = EspansoYamlCodec.decode(globalsOnly, "globals.yml")
        assertTrue(result.matches.isEmpty())
        assertEquals("author", result.globalVariables.single().name)
    }

    @Test fun `exposes relative imports for folder resolution`() {
        val yaml = """
            imports:
              - "./_private.yml"
              - "shared/common.yaml"
        """.trimIndent()

        assertEquals(
            listOf("./_private.yml", "shared/common.yaml"),
            EspansoYamlCodec.importPaths(yaml),
        )
        assertTrue(EspansoYamlCodec.decode(yaml, importsResolved = true).issues.none {
            it.message.contains("not followed")
        })
    }

    @Test fun `retains desktop-only matches at their exact raw source index`() {
        val yaml = """
            matches:
              - trigger: ;one
                replace: One
              - trigger: ;desktop
                html: <strong>Desktop</strong>
              - trigger: ;three
                replace: Three
        """.trimIndent()

        val matches = EspansoYamlCodec.decode(yaml, "mixed.yml").matches

        assertEquals(listOf(0, 1, 2), matches.map(TextMatch::sourceMatchIndex))
        assertEquals(RuntimeCompatibility.DESKTOP_ONLY, matches[1].runtimeCompatibility)
        assertEquals(SourceEditMode.SOURCE_ONLY, matches[1].sourceEditMode)
        assertEquals(RuntimeCompatibility.PORTABLE, matches[2].runtimeCompatibility)
    }

    @Test fun `emits readable Espanso YAML instead of forcing every scalar into quotes`() {
        val encoded = EspansoYamlCodec.encode(
            listOf(
                TextMatch(
                    triggers = listOf(MatchTrigger(";hello")),
                    replacements = listOf("Hello world"),
                ),
            ),
        ).yaml

        assertTrue(encoded.contains("matches:"))
        assertTrue(encoded.contains("trigger: ;hello"))
        assertTrue(encoded.contains("replace: Hello world"))
        assertTrue(!encoded.contains("\"matches\""))
    }

    @Test fun `case propagation imports with matching behavior compatible with Espanso`() {
        val match = EspansoYamlCodec.decode(
            "matches:\n  - trigger: ;hello\n    replace: Hi\n    propagate_case: true\n",
        ).matches.single()

        assertTrue(match.options.propagateCase)
        assertTrue(!match.options.caseSensitive)
        assertEquals(UppercaseStyle.CAPITALIZE, match.options.uppercaseStyle)
        assertEquals(RuntimeCompatibility.PORTABLE, match.runtimeCompatibility)
    }

    @Test fun `case sensitive trigger imports as portable`() {
        val match = EspansoYamlCodec.decode(
            "matches:\n  - trigger: ;GPU\n    replace: Graphics Processing Unit\n    case_sensitive: true\n",
        ).matches.single()

        assertTrue(match.options.caseSensitive)
        assertTrue(!match.options.propagateCase)
        assertEquals(RuntimeCompatibility.PORTABLE, match.runtimeCompatibility)
        assertTrue(match.compatibilityWarnings.none { it.contains("not interpreted") })
    }

    @Test fun `standalone imports and unknown root behavior fail closed`() {
        val match = EspansoYamlCodec.decode(
            "imports: [./shared.yml]\nfilter_title: Editor\nmatches:\n  - trigger: ;x\n    replace: X\n",
        ).matches.single()

        assertEquals(RuntimeCompatibility.DESKTOP_ONLY, match.runtimeCompatibility)
        assertEquals(SourceEditMode.SOURCE_ONLY, match.sourceEditMode)
    }

    @Test fun `normalizes named regex groups and rejects non-Espanso lookaround`() {
        val named = TextMatch(
            triggers = listOf(MatchTrigger("(?<word>\\w+)", TriggerKind.REGEX)),
            replacements = listOf("{{word}}"),
        )
        val encoded = EspansoYamlCodec.encode(listOf(named))
        assertTrue(encoded.yaml.contains("(?P<word>"))
        assertTrue(encoded.issues.none { it.severity == CompatibilitySeverity.ERROR })

        val lookaround = named.copy(triggers = listOf(MatchTrigger("foo(?=bar)", TriggerKind.REGEX)))
        assertTrue(EspansoYamlCodec.encode(listOf(lookaround)).issues.any {
            it.severity == CompatibilitySeverity.ERROR && it.message.contains("look-around")
        })
        val possessive = named.copy(triggers = listOf(MatchTrigger("a{1,3}+", TriggerKind.REGEX)))
        assertTrue(EspansoYamlCodec.encode(listOf(possessive)).issues.any {
            it.severity == CompatibilitySeverity.ERROR && it.message.contains("Possessive")
        })
        val escapedPlus = named.copy(triggers = listOf(MatchTrigger("\\++", TriggerKind.REGEX)))
        assertTrue(EspansoYamlCodec.encode(listOf(escapedPlus)).issues.none {
            it.severity == CompatibilitySeverity.ERROR && it.message.contains("Possessive")
        })
        assertEquals(
            RuntimeCompatibility.DESKTOP_ONLY,
            EspansoYamlCodec.decode("matches:\n  - regex: foo(?=bar)\n    replace: x\n").matches.single()
                .runtimeCompatibility,
        )
    }
}

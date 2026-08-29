# Changelog

Expanda follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.3.1] - unreleased

Compatibility and reliability release. Expansion now works in more WebView-based
editors, and both the caret position and the suggestion popup match what you typed
in editors that throttle accessibility events.

### Fixed

- **Stale suggestion highlight** in WebView-based editors (Gmail compose, Chrome, Obsidian): we now read the text from the accessibility event itself instead of the framework cache.
- **Caret jumping to position 0** after expansion in Gmail-style composers: the service now clears the accessibility cache, refreshes the node, and reapplies the selection on the next frame if it drifted from what we requested.
- **Text corruption in WebView editors** (Discord, Chrome, most Capacitor apps): expansions in non-native editors now go through `ACTION_PASTE` first (which Blink treats as a real paste event), keeping `ACTION_SET_TEXT` as a fallback. Native `EditText` widgets still take the fast path.
- **Suggestion tap sometimes doing nothing**: tapping a snippet or action in the suggestion popup used to silently return when the accessibility cache reported outdated text. Now we refresh the node before reading, and if the typed prefix no longer matches the trigger we fall back to inserting the trigger at the caret instead of dropping the tap.

### Known limitations

- **Obsidian** still expands with the caret in the wrong position. Its Capacitor bridge desyncs with both `ACTION_SET_TEXT` and `ACTION_PASTE`, so a proper fix will need the `FLAG_INPUT_METHOD_EDITOR` API (Android 13+).
- **Google Docs** and other canvas-based editors do not expose text through accessibility at all; expansions don't fire there.
- **Chrome address bar** never emits text-changed events; a follow-up may add `TYPE_WINDOW_CONTENT_CHANGED` support.

## [0.3.0] - 2026-08-29

Major release. Rebuilt around Espanso-compatible matches and local-first storage, improved UI and a full onboarding/tutorial flow.

### Added

- **Espanso-compatible YAML library** — text/regex triggers (word boundaries, `propagate_case`, `case_sensitive`), portable variables.
- **Source** tab for full text based editing, possibility to save all data to a local folder.
- **Interactive tutorial** and workspace onboarding (accessibility, suggestions, source editing).
- **Playground** tab for testing snippets and the suggestion overlay without leaving the app.
- **Expansion undo** — press **Backspace** right after expanding to restore the original trigger.
- **Match disambiguation** when multiple snippets share the same trigger.
- **Full app backup/restore** (snippets, variables, settings, exclusions, actions).
- **Suggestion popup** is now resizable and closable with additional optional buttons.
- **Privacy controls**: clear clipboard history, reset stats, diagnostics copy, reset local data.
- **Example snippets pack** installable from the snippet list.
- **Sideload guidance for Android 13+ restricted settings** when installing outside Google Play.
- **LegacyTemplateMigrator** converts 0.2 inline tokens (`{FORM:}`, `{DATE:}`, `{CLIPBOARD}`, `{SNIPPET:}`, `{CURSOR}`, etc.) to Espanso variables on upgrade.

### Changed

- Rebuilt **Edit snippet** UI: visual token/variable toolbar, per-type Espanso variable editors, regex triggers with capture groups, and advanced matching options (word boundaries, case rules, multiple replacements).
- App bar shows the **Expanda icon and title** with the current section name (tap to open **About**).
- Better **per-app exclusion picker** (global and per-snippet).
- **Suggestion popup** now defaults near the top of the screen (away from the keyboard).
- Improved **Form variable editor**: inline Espanso defaults (`[[name=value]]`), native date/time default pickers, and choice default selection synced into the layout.
- License changed to **GNU GPLv3** (0.2.0 stays MIT).
- Text scale is now a 75–150% slider.
- Secondary snippet actions grouped in a compact toolbar with **search** (opens the keyboard immediately) and **sort** controls.
- Suggestion popup width/height sliders; optional diagonal resize handle.
- **Suggestion overlay enabled by default** for new installs.
- Unit tests and docs aligned with Espanso-only template syntax (removed legacy `{CURSOR}`, `{FORM:}`, etc.).
- Form overlay and picker UI: compact layout, proper action button sizing, list-style choice rows.

### Fixed

- **Clipboard variable** returning stale clipboard content in other apps.
- `propagate_case` default capitalization (first letter, per Espanso — not ALL CAPS).
- `case_sensitive` triggers now work on Android (no longer marked desktop-only).
- Case-matching UI: toggling one option auto-adjusts the other; no confusing save errors.
- `{{form}}` variables now render their full layout (with `[[field]]` placeholders) instead of staying unresolved.
- Random variables with `choices` stored as a quoted/bracketed string no longer expand literally as one option.

## [0.3.0-beta] - 2026-08-28

Pre-release tag for early testers. Functionally superseded by [0.3.0]; kept for users who installed the beta APK.

## [0.2.0](https://github.com/diegomarzaa/expanda/releases/tag/v0.2.0) - 2026-08-20

First stable public release.

### Added

- Cross-app text expansion through Android's Accessibility service.
- Movable suggestion popup for matching snippets and enabled actions.
- Multiple templates with first, random, sequential and manual selection.
- Dynamic cursor, clipboard, date, time, form, nested-snippet, line-break and send tokens.
- Tag organization, search, filtering and bulk snippet operations.
- Text, number, selection, deletion, cursor, clipboard, Android and Expanda actions.
- Optional local clipboard history and usage statistics.
- JSON backup plus CSV import and export.
- Material color schemes, custom color picker, text sizing, dark mode and AMOLED mode.
- Per-app exclusions, configurable triggers, matching controls and compatibility fallback.
- Accessibility disclosure, password-field filtering and popup lifecycle handling.
- Unit coverage for expansion, templates, actions, math, backups, CSV, suggestions and popup anchoring.

### Changed

- Replaced single-folder organization with multiple tags.
- Anchored the suggestion popup by its bottom edge so result changes expand upward.
- Moved the popup drag handle to the bottom and changed it to a horizontal grip.
- Added token highlighting in the editor and suggestion popup.

## [0.1.0] - Initial alpha

- Established the first text-expansion prototype.
- Added basic snippet storage, shortcut detection and accessibility-based replacement.
- Used as an early testing build while the complete workflow was developed.

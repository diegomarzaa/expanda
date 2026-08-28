# Changelog

Expanda follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- **LegacyTemplateMigrator** converts 0.2 inline tokens (`{FORM:}`, `{DATE:}`, `{CLIPBOARD}`, `{SNIPPET:}`, `{CURSOR}`, etc.) to Espanso variables on upgrade and when legacy syntax is detected in YAML source files.
- Sideload guidance for Android 13+ **restricted settings** (required before Accessibility can be enabled on APKs installed outside Google Play).

### Fixed

- `{{form}}` variables now render their full layout (with `[[field]]` placeholders) instead of staying unresolved.
- Regex replacements can reference numeric capture groups (`{{0}}`, `{{1}}`, …) in addition to named groups.

### Changed

- Unit tests and docs aligned with Espanso-only template syntax (removed legacy `{CURSOR}`, `{FORM:}`, etc.).
## [0.3.0-beta]: https://github.com/diegomarzaa/expanda/releases/tag/v0.3.0-beta

Major pre-release. Rebuilt around Espanso-compatible matches, a new data model, and a full onboarding/tutorial flow. **Not a drop-in upgrade from 0.2.0** — legacy snippets are migrated on first launch.

### Added

- Espanso-compatible triggers (text, regex, multiple triggers, word boundaries, `propagate_case`, `case_sensitive`, portable variables).
- Espanso YAML import/export with compatibility warnings for unsupported desktop features.
- **Source** main tab: full-library YAML editor with validation, sync, copy and AI editing prompt.
- Optional linked Espanso folder, or **app-only storage** (`base.yml` + examples) without linking anything.
- Interactive tutorial and workspace onboarding (accessibility, suggestions, source editing).
- **Playground** tab for testing snippets without leaving the app.
- Match disambiguation when multiple snippets share the same trigger (Espanso-style picker).
- Full app backup/restore (snippets, variables, settings, exclusions, actions).
- Searchable per-app exclusion picker (global and per-snippet).
- Resizable, movable suggestion popup; defaults near the **top** of the screen (away from the keyboard).
- Privacy controls: clear clipboard history, reset stats, diagnostics copy, reset local data.
- Clipboard expansion (`;clip`) in external apps via focusable overlay capture.
- Example snippets pack (`_examples.yml`) installable from the snippet list.

### Changed

- Rebuilt match model and SQLite schema; one-time migration from 0.2 data.
- License changed to **GNU GPLv3** (0.2.0 stays MIT).
- Snippet editor: regex triggers, capture groups, Espanso-style forms, advanced matching options.
- Text scale is now a 75–150% slider.
- Secondary snippet actions grouped in a compact toolbar; **Source** moved out of that bar into main navigation.
- Suggestion popup width/height sliders; optional diagonal resize handle.

### Fixed

- Clipboard variable (`{{clipboard}}`) / `;clip` returning stale clipboard content in other apps.
- `propagate_case` default capitalization (first letter, per Espanso — not ALL CAPS).
- `case_sensitive` triggers now work on Android (no longer marked desktop-only).
- Case-matching UI: toggling one option auto-adjusts the other; no confusing save errors.
- Back from Source (opened via a non-visual snippet) returns to the previous tab instead of exiting the app.
- Form/choice overlays: Cancel and Insert stay visible; content scrolls above the keyboard.
- Popup resize anchoring, Playground keyboard dismiss, empty Playground browse inserts, `$|$` cursor in nested matches.
- Broader CSV/JSON/YAML file picking despite wrong MIME types from document providers.

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


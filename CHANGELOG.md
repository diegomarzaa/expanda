# Changelog

Expanda follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed

- Kept a parent `{CURSOR}` in place when a later nested snippet has no cursor token.
- Removed the unreliable automated Enter and Send template actions.

## [0.2.0] - 2026-08-20

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

[0.2.0]: https://github.com/diegomarzaa/expanda/releases/tag/v0.2.0

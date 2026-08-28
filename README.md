<div align="center">
  <img src="docs/expanda-readme-icon.png" width="112" alt="Expanda logo">
  <h1>Expanda</h1>
  <p><strong>Free and open-source text expansion for Android.</strong></p>
  <p>Create snippets, dynamic templates and text actions, then use them in any editable field.</p>

  <p>
    <a href="https://github.com/diegomarzaa/expanda/releases/latest"><img src="https://img.shields.io/github/v/release/diegomarzaa/expanda?label=Download&amp;logo=github" alt="Latest release"></a>
    <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22dev.diego.expanda%22%2C%22url%22%3A%2F%2Fgithub.com%2Fdiegomarzaa%2Fexpanda%22%2C%22author%22%3A%22diegomarzaa%22%2C%22name%22%3A%22Expanda%22%7D"><img src="https://img.shields.io/badge/Get_it_on-Obtainium-2f80ed" alt="Get Expanda on Obtainium"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-2ea44f.svg" alt="GPLv3 License"></a>
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 8.0 or newer">
    <img src="https://img.shields.io/badge/Internet_permission-none-5c6bc0" alt="No Internet permission">
    <img src="https://img.shields.io/badge/Ads_%26_analytics-none-5c6bc0" alt="No ads or analytics">
  </p>
</div>

> [!WARNING]
> I am not an Android developer. I built Expanda quickly for my own use with extensive help from AI, so parts of the code have not received thorough human review and bugs may remain. I am sharing it because others may find it useful and because community feedback can make it better for everyone. Bug reports and pull requests are welcome.

## See it in action

<table>
  <tr>
    <td align="center"><img src="docs/media/demo-overview.gif" width="300" alt="Using an Expanda snippet in another Android app"><br><strong>Use snippets anywhere</strong></td>
    <td align="center"><img src="docs/media/demo-create-snippet.gif" width="300" alt="Creating a snippet in Expanda"><br><strong>Create a snippet</strong></td>
  </tr>
</table>

## Why Expanda?

Expanda is a free, open-source text expander for Android. Snippets stay on your device: no Internet permission, accounts, ads, or analytics.

**It speaks the same YAML as [Espanso](https://espanso.org/) on your PC.** Write a match once, use it on phone and desktop. Link an Espanso `match/` folder on your phone (local copy, Syncthing, Google Drive, etc.) and edits sync both ways.

Expansion uses Android's Accessibility service to read editable fields and replace shortcuts. Password fields are ignored. The app explains this before opening system settings. See [Privacy](PRIVACY.md) and [Permissions](PERMISSIONS.md).

## How it works

1. Create snippets in the visual editor or paste Espanso YAML in the **Source** tab.
2. Optionally link a folder that mirrors your desktop Espanso `match/` library.
3. Enable Expanda's Accessibility service.
4. Type a shortcut in any app, or pick a match from the suggestion popup. Press **Backspace** right after expanding to undo and restore the trigger.

A match looks the same on Android and on Espanso for Windows, macOS, or Linux. Example:

```yaml
- trigger: ";meeting"
  replace: |
    Hi {{details.name}},

    Can we meet on {{when}} at {{time}}?
    $|$
  vars:
    - name: details
      type: form
      params:
        layout: "[[name]]"
    - name: when
      type: date
      params: { format: "%A", offset: 86400 }
    - name: time
      type: date
      params: { format: "%H:%M" }
```

`[[name]]` opens a small form when you expand; `{{when}}` and `{{time}}` are filled from date variables. Unsupported desktop-only types (`shell`, `script`, …) stay literal in the output.

## Import, export and backup

- **Folder sync** — point Expanda at an Espanso `match/` tree for two-way editing with desktop Espanso.
- **YAML import/export** — move compatible matches and variables; Expanda warns about features it cannot translate.
- **Full backup** — snippets, variables, settings, exclusions, and actions (not clipboard history, logs, or popup position).
- **CSV** — spreadsheet-friendly export/import for simple snippet lists.

Expanda detects file type by content, so `.yml`, `.yaml`, `.json`, and `.csv` work even when Android reports the wrong MIME type.

## Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/media/snippets.jpg" width="230" alt="Expanda snippets screen"><br><strong>Snippets</strong></td>
    <td align="center"><img src="docs/media/template-editor.jpg" width="230" alt="Expanda template editor"><br><strong>Template editor</strong></td>
    <td align="center"><img src="docs/media/suggestion-popup.jpg" width="230" alt="Expanda suggestion popup"><br><strong>Suggestions</strong></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/media/actions-library.jpg" width="230" alt="Expanda action library"><br><strong>Actions</strong></td>
    <td align="center"><img src="docs/media/action-settings.jpg" width="230" alt="Expanda action shortcut settings"><br><strong>Action settings</strong></td>
    <td align="center"><img src="docs/media/settings.jpg" width="230" alt="Expanda settings screen"><br><strong>Settings</strong></td>
  </tr>
  <tr>
    <td align="center" colspan="3"><img src="docs/media/appearance.jpg" width="230" alt="Expanda appearance settings"><br><strong>Appearance</strong></td>
  </tr>
</table>

## Features

- **Espanso-compatible YAML** — same matches and variables as desktop Espanso; optional `match/` folder sync.
- **Expand in any app** — type a shortcut or pick from the suggestion popup; **Backspace** undoes the last expansion; exclude apps you do not want.
- **Dynamic templates** — forms (`[[field]]`), dates, clipboard, choices, random picks, nested matches, `$|$` cursor.
- **Regex triggers** — pattern shortcuts with named captures (`{{name}}`) in replacements.
- **Built-in actions** — formatting, math, selection, deletion, cursor moves, clipboard, Android share.
- **Library tools** — tags, search, bulk edit, replacement modes (first / random / sequential / manual); **Source** tab and playground.
- **Backup & CSV** — full restore or spreadsheet-friendly import/export.
- **Themes** — light, dark, AMOLED; adjustable text scale; haptic feedback; Quick Settings tile.

## Template syntax

Expanda follows [Espanso](https://espanso.org/) template syntax. Single braces are always literal text.

| Syntax | Result |
|---|---|
| `{{variable}}` | Resolves a portable variable (`echo`, `date`, `random`, `clipboard`, `choice`, `form`, `match`) |
| `{{form.field}}` | Inserts one field from a `type: form` variable |
| `[[field]]` / `[[field=default]]` | Form layout placeholder inside a form variable's `layout` |
| `$|$` | Places the cursor here after expansion |
| `{{capture}}` | Named regex capture from `(?<capture>...)` in a regex trigger |

Unsupported desktop variable types (for example `shell` or `script`) stay unresolved in the output so Android never silently changes their meaning.

## Download and install

Download the APK from [GitHub Releases](https://github.com/diegomarzaa/expanda/releases/latest). Expanda requires Android 8.0 or newer.

Android may ask you to allow installation from your browser or file manager. After installation, open Expanda and follow the in-app explanation before enabling its Accessibility service.

**Android 13+ sideload installs:** if you install the APK from GitHub (not Google Play), Android may block Accessibility until you open App info → menu (⋮) → **Allow restricted settings**. Expanda detects this and shows setup steps in the app. This is an Android security requirement; publishing on Play Store avoids it for most users.

Updates must use an APK signed with the same certificate. Check [APK verification](#apk-verification) if Android rejects an update or you want to verify the file.

## Permissions and privacy

Expanda uses only these Android permissions and protected capabilities:

| Permission | Purpose |
|---|---|
| Accessibility service | Reads changes in active editable fields and replaces matching shortcuts. Password fields are ignored. |
| Battery optimization exemption request | Opens Android's battery settings so you can allow reliable background operation. |
| Vibration | Provides optional haptic feedback after an expansion. |

The Accessibility service is a special service capability rather than a regular manifest permission. Expanda does **not** declare `INTERNET`, so the app cannot send snippets, typed text, clipboard history or usage statistics over the network through Android's standard networking APIs.

The clipboard history and local usage statistics are enabled by default and can be disabled in Settings. See [Privacy](PRIVACY.md) for stored data and deletion instructions, and [Permissions](PERMISSIONS.md) for the complete permission rationale.

## APK verification

Official releases use the following identity:

```text
Package ID: dev.diego.expanda
Signing certificate SHA-256:
8F:74:53:E5:C2:C8:F8:BC:F6:F8:F1:16:27:F1:7D:3F:43:F2:35:6C:CF:BF:C4:84:EA:07:20:DE:46:F2:D4:A2
```

The SHA-256 checksum of the official `v0.2.0` APK is:

```text
6ae7bd9a4ffd2d6a7b586ace4e1b9816344f5d5e3a4bb26f261bd22b7d9712ec  Expanda-v0.2.0.apk
```

The same value is stored in [checksums/Expanda-v0.2.0.apk.sha256](checksums/Expanda-v0.2.0.apk.sha256).

On a computer with Android SDK Build Tools installed:

```bash
sha256sum Expanda-v0.2.0.apk
apksigner verify --print-certs Expanda-v0.2.0.apk
```

## Compatibility and limitations

Android apps, keyboards and manufacturer customizations expose editable fields in different ways. Direct replacement can fail in some editors; the optional compatibility fallback temporarily uses and restores the clipboard for those cases.

Some manufacturers stop accessibility services during background use. Expanda can open the relevant Android battery settings, but the exact option name depends on the device.

Please use the [bug report template](https://github.com/diegomarzaa/expanda/issues/new?template=bug-report.yml) for compatibility problems. Include the Android version, device, keyboard, target app and exact steps without attaching private text.

## Build from source

You need JDK 17 and the Android SDK with API 36 installed.

```bash
git clone https://github.com/diegomarzaa/expanda.git
cd expanda
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

Run the local checks with:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Release signing uses a local, ignored `signing.properties` file. The repository contains no signing keys. See [BUILDING.md](BUILDING.md) for the full setup and release-signing format.

## Project status

Version **0.3.0** is the current release. It rebuilds Expanda around Espanso-compatible YAML, a new match model, tutorial onboarding, a Playground, a Source tab, and full local backups. Upgrading from 0.2.0 migrates your snippets automatically; syntax changes are documented in [CHANGELOG.md](CHANGELOG.md).

The project was developed through an AI-assisted workflow. Much of the implementation still needs deeper human review.

See [CHANGELOG.md](CHANGELOG.md) for release history and [SECURITY.md](SECURITY.md) for private vulnerability reports.

## Motivation and credits

I started Expanda because I could not find an open-source Android text expander that covered the workflow I wanted. The project also became a way to learn about accessibility services, background execution, overlays and Android text editing.

[Typing Hero](https://typinghero.app/) provided the main product and workflow reference. [Expandroid](https://github.com/lochidev/Expandroid) also influenced the project. Expanda is an independent implementation and is not affiliated with either project.

## Contributing

Bug reports, documentation improvements and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a change.

## License

Expanda 0.3.0 and later are free software under [GNU GPLv3](LICENSE). If you distribute a modified build, you must provide its corresponding source under the same license. The published 0.2.0 release remains available under MIT because changing the license cannot revoke permissions already granted for that version.

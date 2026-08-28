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

Expanda gives you reusable text without sending what you type to a server. The app has no Internet permission, accounts, ads, analytics or tracking SDKs. Snippet matching and expansion run on your device.

Expanda uses Android's Accessibility service to detect shortcuts and replace text in other apps. It ignores password fields and shows a clear explanation before opening Android's accessibility settings. Read [Privacy](PRIVACY.md) and [Permissions](PERMISSIONS.md) for the exact behavior.

## How it works

1. Create a snippet and assign a shortcut such as `;mail`.
2. Enable Expanda's Accessibility service.
3. Type the shortcut in an editable field or select it from the suggestion popup.

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

You can edit matches in the visual snippet editor or as raw YAML in the **Source** tab.

## Import, export and backup

Snippets can live entirely inside the app, or you can link an Espanso `match/` folder for two-way sync.

- **Espanso YAML** moves compatible matches and variables between Expanda and Espanso. Expanda reports features it cannot translate.
- **Snippet source** lets you copy, edit, validate and replace the full library as Expanda YAML. Its AI prompt includes the supported fields, variables and current source.
- **Full app backup** restores snippets, variables, settings, excluded apps and action configuration. It excludes clipboard history, expansion logs, Android permissions and popup coordinates.
- **CSV** provides an editable table of snippets for spreadsheets and older Expanda exports.

Expanda reads the selected file by content, so Android can open `.yml`, `.yaml`, `.json` and `.csv` files even when a document provider reports the wrong file type. Full backups and exports contain plain text. Store them somewhere you trust.

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

- Snippets with unique shortcuts, names, multiple tags and per-app exclusions.
- Delimiter-based or instant expansion with optional case sensitivity.
- Multiple templates with first, random, sequential and manual selection modes.
- Espanso template variables for cursor placement, clipboard text, dates, forms, choices and nested matches.
- A movable and resizable suggestion popup with configurable matching, height, width, previews and text actions.
- Built-in actions for text formatting, calculations, selection, deletion, cursor movement, clipboard operations and Android sharing.
- Search, tag filters and bulk selection for managing snippets.
- Playground for testing expansions inside the app.
- Espanso-compatible YAML import/export, optional folder sync, and a dedicated Source tab.
- Interactive first-run tutorial and optional app-only snippet storage (no folder required).
- Material themes using wallpaper colors, default colors or a custom color, plus light, dark and AMOLED modes.
- Adjustable text scale, haptic feedback and a Quick Settings tile.

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

Version `0.3.0-beta` is a major pre-release. It rebuilds Expanda around Espanso-compatible YAML, a new match model, tutorial onboarding, a Playground, a Source tab, and full local backups. Expect breaking changes from `0.2.0` and possible rough edges — bug reports are welcome.

The project was developed through an AI-assisted workflow. Much of the implementation still needs deeper human review.

See [CHANGELOG.md](CHANGELOG.md) for release history and [SECURITY.md](SECURITY.md) for private vulnerability reports.

## Motivation and credits

I started Expanda because I could not find an open-source Android text expander that covered the workflow I wanted. The project also became a way to learn about accessibility services, background execution, overlays and Android text editing.

[Typing Hero](https://typinghero.app/) provided the main product and workflow reference. [Expandroid](https://github.com/lochidev/Expandroid) also influenced the project. Expanda is an independent implementation and is not affiliated with either project.

## Contributing

Bug reports, documentation improvements and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a change.

## License

Expanda 0.3.0 and later are free software under [GNU GPLv3](LICENSE). If you distribute a modified build, you must provide its corresponding source under the same license. The published 0.2.0 release remains available under MIT because changing the license cannot revoke permissions already granted for that version.

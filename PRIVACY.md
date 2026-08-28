# Privacy

Effective date: 25 August 2026

Expanda processes text on your Android device. The app does not declare the Android `INTERNET` permission and contains no advertising, analytics or tracking SDKs.

## Data Expanda processes

The Accessibility service receives changes from the active editable field so it can detect shortcuts, show suggestions and insert the selected result. Expanda processes this text in memory. It does not add the full contents of editable fields to its database.

Expanda ignores fields that Android identifies as password fields. Some apps expose custom fields with incomplete accessibility metadata, so you should disable Expanda for any app where you do not want text processing.

## Data stored on the device

Expanda stores the following data in its private app storage:

- Snippets, templates, labels, tags and snippet settings.
- App settings, including excluded apps and the saved suggestion-popup position.
- Expansion counts and, while local statistics are enabled, the package name and time of each expansion.
- Up to 200 recent non-pinned clipboard entries while clipboard history is enabled, plus any entries you pin.

Clipboard history and local statistics are enabled by default. You can disable either option in Settings. Disabling an option stops new collection but does not delete data already stored.

Expanda stores no account, advertising identifier or remote analytics identifier.

## Clipboard access

Expanda can read clipboard text for the `{CLIPBOARD}` token, clipboard actions and the optional clipboard history. The history captures the current clipboard when Expanda returns to the foreground. It remains in the local database until you clear the history in Settings or clear the app's storage.

The optional compatibility paste fallback uses the system clipboard when an editor rejects direct text replacement. It restores the previous clipboard content after the operation where Android permits it.

## Network access and sharing

Expanda does not declare `INTERNET`, so it cannot send data through Android's standard networking APIs. The app contains no remote account, sync, telemetry or crash-reporting service.

An Android text action can open the system share sheet. You choose the receiving application and control whether to send the selected text. Expanda does not select a recipient or transmit the text itself.

## Backups and exports

Automatic Android application backup is disabled in the manifest.

You can create a full JSON backup, CSV export or Espanso YAML export from Settings. These files are unencrypted and may contain private snippet text. You choose the destination through Android's document picker and remain responsible for storing or sharing the exported file.

Full backups contain snippets, variables, settings, excluded-app package names and action configuration. They do not include clipboard history, expansion logs, Android permissions, battery settings or the popup's screen coordinates. CSV and Espanso files contain portable snippet data rather than app settings.

## Data deletion

You can delete snippets inside the app and clear the complete clipboard history or usage statistics from Settings. **Reset Expanda**, Android's **Clear storage** action or uninstalling Expanda removes its local data and settings from the device.

Delete exported JSON, YAML or CSV files separately from the location where you saved them.

## Permissions

[PERMISSIONS.md](PERMISSIONS.md) explains each permission and Accessibility-service capability. You can disable the Accessibility service at any time in Android Settings.

## Changes

Future versions will update this document if Expanda changes what it processes, stores or shares. The release changelog will call out privacy-relevant changes.

## Contact

Open a [GitHub issue](https://github.com/diegomarzaa/expanda/issues) for privacy questions that contain no sensitive information. Follow [SECURITY.md](SECURITY.md) for reports that need private handling.

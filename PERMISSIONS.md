# Permissions

Expanda uses a small set of Android capabilities to expand text across apps. It does not request Internet, location, camera, microphone, contacts or account access.

## Accessibility service

Android requires an Accessibility service for Expanda's core cross-app workflow. Once you enable it, Expanda can:

- Observe changes in the active editable field.
- Identify a matching shortcut.
- Show the suggestion popup over the current app.
- Replace text, move the cursor and perform enabled text actions.
- Inspect active windows and focus changes so it can remove a stale popup when you leave the editor.

The service ignores fields marked as passwords and stops processing when expansion is paused or disabled. You can also exclude applications from expansion.

Expanda displays an explanation inside the app before it opens Android's Accessibility settings. Android controls the final permission screen, and you can revoke access at any time.

### Restricted settings (Android 13+, sideloaded APKs)

When Expanda is installed outside Google Play (for example from a GitHub release APK), Android treats it as an untrusted install. Accessibility, notification listeners and similar sensitive capabilities stay disabled until the user opens **App info → menu (⋮) → Allow restricted settings** (wording may vary by device language).

This is enforced by Android; the app cannot bypass or auto-grant it. Installs from Google Play are not subject to this extra step in normal circumstances. Expanda detects sideloaded installs and guides you through the flow before opening Accessibility settings.

## `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

Some Android manufacturers stop background accessibility services to save battery. Expanda uses this permission to open Android's battery-optimization settings, where you decide whether to allow unrestricted background operation.

Expanda continues to work without the exemption while Android keeps its service alive, but availability may become unreliable after the app remains in the background.

## `android.permission.VIBRATE`

Expanda uses this permission for optional haptic feedback after an expansion. Haptic feedback is disabled by default and can be changed in Settings.

## Clipboard

Modern Android versions expose clipboard access through the system API rather than a manifest permission. Expanda uses it for:

- The `{{clipboard}}` template variable.
- Enabled clipboard actions.
- Optional local clipboard history.
- An optional compatibility fallback for editors that reject direct replacement.

Clipboard history is enabled by default and can be disabled in Settings. Read [PRIVACY.md](PRIVACY.md) for retention and deletion details.

## Quick Settings tile

The `BIND_QUICK_SETTINGS_TILE` capability protects Expanda's on/off tile. Android grants it to the tile service; the app does not show a separate runtime permission prompt.

## Capabilities Expanda does not request

The manifest does not declare permissions for Internet access, location, camera, microphone, contacts, SMS, phone calls or external-storage browsing. Android's document picker grants access only to the import or export file you choose.

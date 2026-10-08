# Git sync (optional)

Expanda can pull your snippets from a Git repository instead of a local folder. It works with GitHub and with open-source, self-hostable Forgejo/Gitea servers such as Codeberg, so it does not depend on a proprietary service. It is off until you fill in a repository URL on the "Git sync" card in Settings → Import & export. A token is optional: public repositories can be read anonymously.

## What it does

- Downloads every `match/**/*.yml` / `.yaml` file (except `match/packages/`) from the configured repository and branch, using the repository layout espanso uses.
- Replaces the app's snippets with them. Snippets edited on the phone are overwritten when the branch changes or when you tap "Sync now", so edit them in the repository.
- The first sync from a repository (or after changing the repository or branch) replaces the existing local snippet files, including the default `base.yml`, example snippets and global variables. It only runs when you tap "Sync now", after you confirm "Replace N local snippet files with M files from the repository?". A backup of the previous snippets and settings is exported to app-private storage first (the last three are kept). Background checks and checks on app open never do this first sync; they show "Waiting for first manual sync".
- Nothing is replaced if none of the downloaded files contains a snippet Expanda can read while you have local snippets.
- Only works while no local folder is linked (one source at a time).
- If the repository has no matching files, nothing is changed.

## Restoring the backup

When a backup exists, the card shows "Restore snippets from before Git sync". It loads the newest backup through the same import path as Settings > Expanda backup > Import, and replaces your current snippets, settings and shortcuts after you confirm. **Clear the repository URL and tap Save afterwards**: otherwise the next branch change (or "Sync now") replaces the restored snippets again.

## Token (optional)

Leave the token empty to read a public repository anonymously. Hosts rate-limit anonymous requests (GitHub: 60 per hour per IP; every sync costs one request for the branch check, one for the file list and one per file; on Forgejo/Gitea add one request for the default branch when no branch is set, and one per page of the file list), so a repository with many files may need a token. A rate-limit response shows "rate limit reached" and nothing is changed. Private repositories always need a token.

- **GitHub**: Settings, Developer settings, Personal access tokens, Fine-grained tokens. Select only the one repository you sync, set an expiry, and grant Repository permissions: Contents: Read-only (nothing else). Sent as `Authorization: Bearer <token>`.
- **Codeberg / Forgejo / Gitea**: Settings, Applications, generate a token with only the `read:repository` scope. Sent as `Authorization: token <token>`.

A token must be 20-255 letters, digits or underscores (anything else is rejected). It is stored privately on the device together with the host it was entered for, and sent only to that host, over HTTPS, and only when set. If you change the repository URL to a different host, the token is removed and you enter one for the new host. It is never logged. Redirects are not followed, so it cannot be forwarded to another host.

## Repository URL

One field takes the repository; the host is taken from it. Accepted forms:

- GitHub: `https://github.com/owner/repo` (optionally with `.git` or a trailing slash), `github.com/owner/repo` (no scheme) or `owner/repo`; `https://github.com/owner/repo/tree/<branch>` picks a branch.
- Codeberg, Forgejo or Gitea: `https://host/owner/repo` (e.g. `https://codeberg.org/owner/repo`, optionally with a port such as `https://git.example.com:3000/owner/repo`, or without the scheme); `https://host/owner/repo/src/branch/<branch>` picks a branch. The server must offer the Forgejo/Gitea API v1 at `https://host/api/v1`.
- The branch may contain slashes (everything after `/tree/` or `/src/branch/`).

When the URL has no branch part, the repository's default branch is used. `http://` URLs, other paths and empty owner/repo are rejected with an inline error, and Save / Sync stay disabled until the field is valid. The field is pre-filled from the saved settings. Host, owner, repository and branch are stored separately, and changing any of them (including the host) counts as a new repository: the next sync is a first, manually confirmed one.

The token is stored as a file in the app's no-backup private storage (not in shared preferences). It is never included in Android backups or device-to-device transfers, and it is not part of Expanda's own backup export. "Clear token" and "Reset Expanda" delete the file; reset also waits for a running sync to finish first, so nothing is written after it.

## Permissions added

This feature adds `INTERNET` and `ACCESS_NETWORK_STATE`; AndroidX WorkManager, which schedules the periodic check, adds `WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED`. See [PERMISSIONS.md](../PERMISSIONS.md). That is a change from the app's otherwise no-internet design. The network is used only for HTTPS requests to the git host you configure (for example `api.github.com` or `codeberg.org`), and only after you configure the card. `ACCESS_NETWORK_STATE` lets background sync wait for a connection.

## Sync cadence

- Periodic background check every 12 hours (when a network is available). It first asks the host whether the branch's latest commit changed (commit SHA; GitHub also supports an ETag so an unchanged branch costs a tiny 304 response, Forgejo compares the SHA) and only downloads files when it did. All following requests (tree and file contents) are pinned to that commit, so a push in the middle of a sync cannot mix two versions.
- On app open, if more than 1 hour has passed since the last check.
- "Sync now" always does a full download, ignoring the change check. It keeps running if you leave the screen. Saving or syncing a configured repository (re)schedules the periodic check.

## Limits

To keep a bad or hostile response from exhausting memory: at most 500 files, 1 MiB per file, 16 MiB across all files and 5 MiB per listing response (Forgejo/Gitea listings are fetched page by page, up to 20,000 entries). Symbolic links are ignored. Exceeding a limit fails the sync with a message and leaves your snippets unchanged.

# Contributing

Expanda welcomes focused fixes, tests and documentation improvements.

## Before opening an issue

- Search existing issues for the same problem.
- Remove private text, clipboard contents, contacts and account information from screenshots and logs.
- Include your Android version, device manufacturer, keyboard and the app where expansion failed.
- Explain the shortcut, trigger mode and exact steps needed to reproduce the problem.

Use the bug template for failures and the feature template for new ideas.

## Pull requests

Keep each pull request limited to one change. Reuse existing components and data structures where possible.

Before submitting:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Add or update tests when you change pure expansion, token, action, import or suggestion logic. Describe any manual checks you performed, but do not include private app content.

## Accessibility and privacy

Changes to Accessibility events, clipboard behavior, storage, networking, analytics or permissions need a clear rationale. Update [PRIVACY.md](PRIVACY.md) and [PERMISSIONS.md](PERMISSIONS.md) when behavior changes.

Do not add trackers, advertising SDKs or non-free dependencies without prior discussion.

## Development process

Expanda began through an AI-assisted, vibe-coding workflow. Review the surrounding implementation before extending it and call out generated code in a pull request when that context helps reviewers assess the change.

By contributing, you agree that your work will be released under the repository's [GPLv3 License](LICENSE).

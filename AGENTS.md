# AGENTS.md

Guidance for AI coding agents (and humans in a hurry) working with this repository.

## What this is

Nookast is a native Android client for a self-hosted [PinePods](https://github.com/madeofpendletonwool/PinePods)
podcast server. Package `ru.e1173.nookast`, minSdk 26, targetSdk 37. The whole app was written with
AI under a human's direction.

This repository is a **read-only snapshot mirror** of the releases. Each release is one commit; the
next release replaces the tree. Do not open pull requests: propose changes as a patch in an issue.

## Layout

- `app/` — the application: Kotlin, Jetpack Compose, Room, Media3, WorkManager, OkHttp, Coil.
  - `core/`, `sync/`, `data/` — the server API, local database and the sync engine.
  - `player/` — playback service, sources, sound processing, chapters.
  - `ui/`, `designsystem/` — screens and the shared design system.
  - `update/` — the over-the-air updater that installs releases from this repository.
- `app/schemas/` — Room schemas; migrations must keep every pending local change.
- `baselineprofile/` — the startup profile generator.
- `release-notes/` — release notes, `<!-- lang:en -->` and `<!-- lang:ru -->` sections.
- `.github/workflows/release.yml` — builds, signs, starts the APK on an emulator and publishes.

## Build and check

JDK 17, Android SDK platform 37, build-tools 36.0.0.

```
./gradlew assembleDebug testDebugUnitTest lintVitalRelease spotlessCheck
```

## Rules that matter

- **Sync is the core.** Every user action is written to Room (an outbox) first and sent later. A
  server response must never overwrite a newer local intent. Read the sync code before touching it.
- **Strings:** every user-facing string in English (`values`) and Russian (`values-ru`).
- **Formatting:** ktlint with the rules of `.editorconfig`.
- **Secrets:** never put a server address, login, password, API key or signing data in code,
  tests, logs or issues.
- **Release artifacts:** the APK is `nookast-<version>.apk` with `nookast-<version>.apk.sha256`;
  the updater depends on these names and on the signing certificate
  `837fd9d7c432b1b0e58e954cf6835689f4fcac15849c76289fea723a66c6c5ad`.
- Comments that cite `docs/…` point to design documents kept in the private repository.

## License

[PolyForm Noncommercial 1.0.0](LICENSE.md): any noncommercial use; commercial use needs the
author's permission.

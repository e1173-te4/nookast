# Contributing to Nookast

Thanks for taking the time to look at Nookast. This repository is a **snapshot mirror**: every
release is a single commit with the exact source its APK is built from, and development happens in
a private repository. That shapes how you can help.

## Ways to help

- **Report a bug.** Open an issue with the [bug report form](../../issues/new?template=bug_report.yml).
  Include the Nookast version (Settings → About), your device and Android version, and the PinePods
  server version if you know it.
- **Suggest an improvement.** Use the [feature request form](../../issues/new?template=feature_request.yml)
  and describe the problem first, then the idea.
- **Review the code.** Read anything, comment in an issue, point at a line. Reviews are welcome.
- **Report a security problem privately**, never in a public issue: see [SECURITY.md](SECURITY.md).

Never post your server address, login, password, API key or an unredacted log.

## Pull requests

Pull requests cannot be merged here: the next release replaces the whole tree. If you have a fix,
open an issue and put the patch or the diff in it. It will be applied in the private repository,
with credit, and arrive here with the next release.

## Building

You need JDK 17 and the Android SDK (platform 37, build-tools 36.0.0).

```
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit tests
./gradlew lintVitalRelease       # lint for the release build
./gradlew spotlessCheck          # formatting (ktlint, rules in .editorconfig)
```

The release APK is built and signed only by [the release workflow](.github/workflows/release.yml).

## Code conventions

- Kotlin and Jetpack Compose; formatting follows `.editorconfig` (ktlint).
- Every user-facing string exists in English and Russian (`res/values` and `res/values-ru`).
- Local changes go through Room first and reach the server later: an answer from the server never
  overwrites a newer change made on the device.
- Comments that cite `docs/…` refer to design documents kept in the private repository.

## Code of conduct

Everyone taking part follows the [Code of Conduct](CODE_OF_CONDUCT.md).

---

**По-русски.** Здесь зеркало выпусков, поэтому pull request не принимаются. Ошибки и идеи
присылайте через issues, исправление можно приложить к issue патчем. Об уязвимостях пишите
закрыто ([SECURITY.md](SECURITY.md)). Адрес сервера, логин, пароль и журналы с личными данными не
публикуйте.

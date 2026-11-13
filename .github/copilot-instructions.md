Nookast is a native Android client (Kotlin, Jetpack Compose, Room, Media3) for a self-hosted
PinePods podcast server. Read `AGENTS.md` at the repository root first: it describes the layout,
the build and the rules.

Key rules:
- Every user action goes to Room first and syncs later; a server response never overwrites a newer
  local change.
- Every user-facing string exists in English (`res/values`) and Russian (`res/values-ru`).
- Format Kotlin with ktlint using `.editorconfig`.
- Never add server addresses, logins, passwords, API keys or signing data anywhere.
- This repository is a release mirror: changes are proposed as patches in issues, not pull requests.

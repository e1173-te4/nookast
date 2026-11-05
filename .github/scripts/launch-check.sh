#!/usr/bin/env bash
# Installs the signed release APK ($1) on the running emulator, starts it and fails when the
# process is gone 15 s later or the log has a fatal exception of the package. A release that
# crashes at start must never become "latest": the in-app updater runs in the same process
# and could not replace it.
set -euo pipefail

apk="$1"
package="${PACKAGE:?}"

adb wait-for-device
adb install "$apk"
adb logcat -c
activity=$(adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$package" | tail -n 1 | tr -d '\r')
start=$(adb shell am start -W -n "$activity" | tr -d '\r')
echo "$start"
sleep 15

crashes=$(adb logcat -d -b crash -b main | grep -A 2 'FATAL EXCEPTION' || true)
if ! adb shell pidof "$package" > /dev/null || grep -q "Process: $package," <<< "$crashes" || ! grep -q '^Status: ok' <<< "$start"; then
  echo "$package did not survive 15 s after the start"
  adb logcat -d -b crash | tail -n 200
  exit 1
fi
echo "$package is running 15 s after the start, no fatal exception"

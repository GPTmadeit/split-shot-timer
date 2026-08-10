#!/usr/bin/env bash
#
# Install both APKs on a running emulator, launch them, and fail if either dies.
#
# This exists because both apps once compiled, passed every unit test and passed
# Android Lint while crash-looping on launch: a SecurityException thrown at
# runtime from an undeclared manifest permission is invisible to static analysis.
# The only way to catch that class of bug is to actually start the app.
#
# Kept as a file rather than inlined into the workflow because the emulator
# action hands its `script:` input to `sh -c`, which mangles multi-line shell
# functions. It also means the same check can be run locally:
#
#   ./scripts/smoke-test.sh
#
set -euo pipefail

PKG=com.carlb.split
ADB="${ADB:-adb}"
SETTLE_SECONDS="${SETTLE_SECONDS:-12}"

fail() {
  echo "::error::$1"
  echo "--- last 80 lines of logcat ---"
  "$ADB" logcat -d | tail -80 || true
  exit 1
}

smoke() {
  apk=$1
  activity=$2
  label=$3

  echo "=============== $label ==============="
  "$ADB" uninstall "$PKG" >/dev/null 2>&1 || true
  "$ADB" install -r "$apk"

  # Pre-grant so the run exercises the post-permission path, which is where the
  # microphone foreground service actually starts.
  "$ADB" shell pm grant "$PKG" android.permission.RECORD_AUDIO || true
  "$ADB" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true

  "$ADB" logcat -c
  "$ADB" shell am start -n "$PKG/$activity"
  sleep "$SETTLE_SECONDS"

  if "$ADB" logcat -d -s AndroidRuntime:E | grep -q "FATAL EXCEPTION"; then
    echo "--- fatal exception ---"
    "$ADB" logcat -d -s AndroidRuntime:E | head -40
    fail "$label crashed on launch"
  fi

  # A crash loop can leave a pid present at any given instant, so check that the
  # process is not only alive but the *same* one across a second interval.
  pid1=$("$ADB" shell pidof "$PKG" | tr -d '\r' || true)
  [ -n "$pid1" ] || fail "$label process died after launch"
  sleep 4
  pid2=$("$ADB" shell pidof "$PKG" | tr -d '\r' || true)
  [ -n "$pid2" ] || fail "$label process died shortly after launch"
  [ "$pid1" = "$pid2" ] || fail "$label is restarting (pid $pid1 -> $pid2), likely a crash loop"

  echo "$label: alive after launch (pid $pid1)"
}

smoke wear/build/outputs/apk/debug/wear-debug.apk \
      com.carlb.split.wear.MainActivity "watch app"

smoke mobile/build/outputs/apk/debug/mobile-debug.apk \
      com.carlb.split.mobile.MainActivity "phone app"

echo "both apps launched and stayed up"

#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-report
SDK="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
BT="$(find "$SDK/build-tools" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -n 1)"
test -x "$BT/apksigner" || { echo "::error::apksigner missing"; exit 1; }
# CI runners may not have a debug keystore before this emulator-only test.
# Generate a throwaway key; this MUST NEVER sign an official Vitrum release.
if [ ! -f "$HOME/.android/debug.keystore" ]; then
  mkdir -p "$HOME/.android"
  keytool -genkeypair -noprompt -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 7 -keystore "$HOME/.android/debug.keystore" \
    -storepass android -keypass android \
    -dname "CN=Android Debug, O=Android, C=US"
fi

RELEASE_UNSIGNED="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' | head -n 1)"
test -n "$RELEASE_UNSIGNED" || { echo "::error::Unsigned release APK missing"; exit 1; }
"$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out emulator-report/release-ui-only.apk "$RELEASE_UNSIGNED"

launch_and_check() {
  local label="$1" apk="$2"
  echo "=== Testing ${label} on Android $(adb shell getprop ro.build.version.release) ==="
  adb uninstall app.rayclient >/dev/null 2>&1 || true
  adb install "$apk"
  adb logcat -c
  adb shell am start -W -n app.rayclient/.MainActivity > "emulator-report/${label}-launch.txt" 2>&1
  sleep 8
  adb logcat -d -b crash -v threadtime > "emulator-report/${label}-crash.txt"
  if grep -E 'FATAL EXCEPTION|Fatal signal' "emulator-report/${label}-crash.txt"; then
    echo "::error::Android crash in ${label}"
    exit 1
  fi
  if ! adb shell pidof app.rayclient >/dev/null; then
    echo "::error::App process died after ${label} launch"
    adb logcat -d -v threadtime -t 350 > "emulator-report/${label}-logcat.txt"
    exit 1
  fi
  adb exec-out screencap -p > "emulator-report/${label}-screen.png"
  adb shell am force-stop app.rayclient
  adb shell am start -W -n app.rayclient/.MainActivity > "emulator-report/${label}-relaunch.txt" 2>&1
  sleep 4
  if ! adb shell pidof app.rayclient >/dev/null; then
    echo "::error::App process died after ${label} relaunch"
    adb logcat -d -v threadtime -t 350 > "emulator-report/${label}-logcat.txt"
    exit 1
  fi
  echo "PASS: ${label} launch and relaunch" | tee "emulator-report/${label}-result.txt"
}

launch_and_check debug app/build/outputs/apk/debug/app-debug.apk
launch_and_check release emulator-report/release-ui-only.apk
echo "UI-only x86_64 Android 13 emulator; this does NOT test the author-signed ARM64 release APK or actual VPN traffic" >> emulator-report/limitations.txt

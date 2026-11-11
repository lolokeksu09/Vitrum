#!/usr/bin/env bash
set -euo pipefail

# Captures real Android 13 emulator pixels from a DEBUG Vitrum build.
# Demo.kt injects in-memory example.com-only fixture data and NEVER starts a VPN.
# No real server URLs, subscriptions, credentials, or personal app data are used.
if [[ "${CI:-}" != "true" ]]; then
  echo "::error::This screenshot script is for CI's isolated Android emulator only."
  exit 1
fi

SDK="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
MANAGER="$(find "$SDK/cmdline-tools" -maxdepth 3 -type f -name sdkmanager | head -n 1)"
test -n "$MANAGER" || { echo "::error::Missing Android sdkmanager"; exit 1; }
"$MANAGER" "platforms;android-37.0" "build-tools;37.0.0"

# Production ARM64 native runtime cannot execute on this x86_64 emulator.
# Change ABI for the temporary DEBUG UI build only. Do not commit this patch.
grep -Fq 'abiFilters += listOf("arm64-v8a")' app/build.gradle.kts
sed -i 's/abiFilters += listOf("arm64-v8a")/abiFilters += listOf("x86_64")/' app/build.gradle.kts
tools/FETCH_BINARIES.sh --linux
./gradlew :app:assembleDebug --no-daemon --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear app.rayclient

mkdir -p assets/screenshots emulator-showcase
{
  echo 'Origin: Vitrum source debug build on Android 13 x86_64 emulator'
  echo 'Source revision:' "$GITHUB_SHA"
  echo 'Android version:' "$(adb shell getprop ro.build.version.release)"
  echo 'Emulator ABI:' "$(adb shell getprop ro.product.cpu.abi)"
  echo 'Source fixtures: app/src/main/java/app/rayclient/Demo.kt'
  echo 'Fixtures: synthetic example.com servers and in-memory states only'
  echo 'Networking: no VPN tunnel is started, no servers or subscriptions are contacted'
  echo 'Limitations: not a screenshot of the signed ARM64 release APK; not VPN QA'
} > emulator-showcase/verification.txt

# Status-bar cosmetics do not alter any UI or connection behavior.
adb shell settings put system time_12_24 24 || true
adb shell settings put global animator_duration_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global window_animation_scale 0 || true

screens=(home servers routing shield settings game)
for screen in "${screens[@]}"; do
  echo "Capturing real emulator view: $screen"
  adb logcat -c
  adb shell am start -W -S -n app.rayclient/.MainActivity \
    --ez vitrum_demo true \
    --ez vitrum_offline false \
    --es vitrum_screen "$screen" \
    --es vitrum_theme dark \
    --es vitrum_lang en > "emulator-showcase/$screen-launch.txt" 2>&1
  sleep 5
  adb shell pidof app.rayclient >/dev/null || {
    echo "::error::Vitrum process stopped while rendering $screen"
    adb logcat -d -b crash -v threadtime > "emulator-showcase/$screen-crash.txt"
    exit 1
  }
  adb logcat -d -b crash -v threadtime > "emulator-showcase/$screen-crash.txt"
  if grep -Eq 'Process: app\.rayclient|FATAL EXCEPTION.*app\.rayclient' "emulator-showcase/$screen-crash.txt"; then
    echo "::error::Vitrum crash when capturing $screen"
    exit 1
  fi
  adb exec-out screencap -p > "assets/screenshots/$screen-android13-demo.png"
done

python3 - <<'PY'
import hashlib
from pathlib import Path
import struct

folder = Path("assets/screenshots")
paths = [folder / f"{name}-android13-demo.png" for name in
         ["home", "servers", "routing", "shield", "settings", "game"]]
digests = set()
for path in paths:
    raw = path.read_bytes()
    if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit(f"Not a PNG: {path}")
    width, height = struct.unpack(">II", raw[16:24])
    if width < 600 or height < 1100 or len(raw) < 15000:
        raise SystemExit(f"Suspiciously small/empty capture: {path} ({width}x{height}, {len(raw)} bytes)")
    digest = hashlib.sha256(raw).hexdigest()
    if digest in digests:
        raise SystemExit(f"Duplicate screenshot: {path}")
    digests.add(digest)
    print(f"OK {path} | {width}x{height} | {len(raw)} bytes | SHA-256 {digest}")
print("PASS: Six distinct real Android emulator screenshots captured")
PY

# The debug-only source edits are local; only screenshots and evidence are publishable.
test "$(git status --short app/build.gradle.kts | wc -l)" -eq 1

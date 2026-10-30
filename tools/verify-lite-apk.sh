#!/usr/bin/env bash
set -euo pipefail
APK="${1:?usage: bash tools/verify-lite-apk.sh path/to/lite-debug.apk}"
[ -s "$APK" ] || { echo "Missing APK" >&2; exit 1; }
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BT="$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
META="$("$BT/aapt" dump badging "$APK")"
grep -q "package: name='app.rayclient.lite'" <<<"$META"
grep -q "sdkVersion:'23'" <<<"$META"
grep -q "native-code: 'armeabi-v7a'" <<<"$META"
unzip -l "$APK" | grep -q 'lib/armeabi-v7a/libxray.so'
unzip -l "$APK" | grep -q 'lib/armeabi-v7a/libspawn.so'
if unzip -l "$APK" | grep -q 'lib/arm64-v8a/'; then
  echo "arm64 must not be present in tablet Lite APK" >&2; exit 1
fi
"$BT/apksigner" verify "$APK"
sha256sum "$APK"
echo "Verified package ID, Android 6 minSdk, ARM32 native libs and signature; real VPN behavior NOT verified."

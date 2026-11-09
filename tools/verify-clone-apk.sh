#!/usr/bin/env bash
# Verify built APK identity before it is distributed to testers.
set -euo pipefail
APK="${1:?Usage: bash tools/verify-clone-apk.sh path/to/app-clone.apk}"
[ -s "$APK" ] || { echo "Clone APK missing: $APK" >&2; exit 1; }
[ -n "${ANDROID_HOME:-}" ] || { echo "ANDROID_HOME is required" >&2; exit 1; }
BT="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -n 1)"
[ -x "$BT/aapt" ] && [ -x "$BT/apksigner" ] || { echo "Android build-tools missing" >&2; exit 1; }
META="$("$BT/aapt" dump badging "$APK")"
printf '%s\n' "$META" | grep -F "package: name='app.rayclient.clone'" >/dev/null || { echo "Not a clone package" >&2; exit 1; }
printf '%s\n' "$META" | grep -F "application-label:'Vitrum Clone'" >/dev/null || { echo "Wrong clone label" >&2; exit 1; }
"$BT/aapt" dump xmltree "$APK" res/xml/shortcuts.xml | grep -F 'app.rayclient.clone' >/dev/null || { echo "Shortcut does not target clone" >&2; exit 1; }
"$BT/apksigner" verify "$APK"
echo "Verified: app.rayclient.clone / Vitrum Clone / shortcut / APK signature"

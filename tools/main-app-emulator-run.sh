#!/usr/bin/env bash
set -euo pipefail
# This is run inside the emulator runner as ONE bash process, preserving local variables.
# x86_64 is UI only: no production ARM64 Xray or VPN/TUN checks.
sed -i 's/abiFilters += listOf("arm64-v8a")/abiFilters += listOf("x86_64")/' app/build.gradle.kts
SDK="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
MANAGER="$(find "$SDK/cmdline-tools" -maxdepth 3 -type f -name sdkmanager | head -n 1)"
test -n "$MANAGER" || { echo "::error::Android sdkmanager missing in $SDK"; exit 1; }
"$MANAGER" "platforms;android-37.0" "build-tools;37.0.0"
# Include the same verified GeoIP/GeoSite/whitelist assets as the production build.
tools/FETCH_BINARIES.sh --linux
./gradlew :app:assembleDebug :app:assembleRelease --no-daemon --console=plain
bash tools/main-app-startup-smoke.sh

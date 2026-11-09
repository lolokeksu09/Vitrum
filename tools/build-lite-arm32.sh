#!/usr/bin/env bash
# Xray v26.3.27 is not published as Android armeabi-v7a. Build an experimental
# Android ARM32 binary from tagged source with the NDK. Never package Linux ARM.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || { echo "ANDROID_HOME is required" >&2; exit 1; }
NDK="$SDK/ndk/27.2.12479018"
[ -d "$NDK" ] || { echo "Android NDK 27.2.12479018 is required" >&2; exit 1; }
CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi23-clang"
[ -x "$CC" ] || { echo "ARMv7 API23 clang not found" >&2; exit 1; }
command -v go >/dev/null
command -v git >/dev/null
command -v readelf >/dev/null
OUT="$ROOT/lite/src/main/jniLibs/armeabi-v7a"
mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
git clone --depth=1 --branch v26.3.27 --single-branch https://github.com/XTLS/Xray-core.git "$TMP/Xray-core"
(
  cd "$TMP/Xray-core"
  GOOS=android GOARCH=arm GOARM=7 CGO_ENABLED=1 CC="$CC"     go build -p 2 -trimpath -buildvcs=false -ldflags="-s -w -buildid= -checklinkname=0"     -o "$OUT/libxray.so" ./main
)
"$CC" -fPIC -shared -O2 -o "$OUT/libspawn.so" "$ROOT/lite/src/main/cpp/spawn.c"
# Avoid silently shipping an ARM64 or non-PIE executable on Android 6.
for lib in libxray.so libspawn.so; do
  readelf -h "$OUT/$lib" | grep -q 'Class: *ELF32'
  readelf -h "$OUT/$lib" | grep -q 'Machine: *ARM'
  readelf -h "$OUT/$lib" | grep -q 'Type: *DYN'
done
sha256sum "$OUT/libxray.so" "$OUT/libspawn.so"
echo "ARMv7 Android API23 candidates built; device execution remains unverified."

#!/bin/bash
# Готовит чистую среду: JDK 21 и Android SDK (платформа 35, build-tools 35.0.0). Уже стоящее пропускает.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; SDK="${ANDROID_HOME:-$HOME/android-sdk}"
if ! command -v javac >/dev/null || ! javac -version 2>&1 | grep -q " 21"; then
  apt-get update -qq && apt-get install -y -qq openjdk-21-jdk-headless unzip curl zip python3
fi
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools" && cd "$SDK/cmdline-tools"
  curl -fsSL --retry 3 -m 400 -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q -o /tmp/clt.zip -d /tmp/clt && rm -rf latest && mv /tmp/clt/cmdline-tools latest && rm -rf /tmp/clt /tmp/clt.zip
fi
SM="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$SM" --licenses >/dev/null 2>&1 || true
[ -d "$SDK/platforms/android-35" ] && [ -d "$SDK/build-tools/35.0.0" ] || "$SM" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
echo "sdk.dir=$SDK" > "$ROOT/local.properties"; echo "Среда готова: SDK=$SDK"

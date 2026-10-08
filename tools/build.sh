#!/bin/bash
# Фоновая сборка. Использование: tools/build.sh tests|debug|release|all
#   tests   - модульные тесты; debug - отладочный APK; release - подписанный APK (нужны keystore/vitrum-release.jks и VITRUM_KS_PASS); all - тесты, debug, release.
# Ход: tail -n 3 /tmp/vitrum-build/<этап>.log ; конец: файл /tmp/vitrum-build/done. Релиз занимает 5-9 минут (R8), не убивайте Gradle через pkill -f gradlew.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; OUT=/tmp/vitrum-build; mkdir -p "$OUT"
if [ "${VITRUM_BG:-}" != 1 ]; then rm -f "$OUT/done"; VITRUM_BG=1 setsid nohup "$0" "$@" >/dev/null 2>&1 </dev/null & echo "Запущено в фоне: $OUT"; exit 0; fi
cd "$ROOT"; export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
run() { ./gradlew "${@:2}" --no-daemon --console=plain > "$OUT/$1.log" 2>&1; echo "exit=$?" >> "$OUT/$1.log"; }
case "${1:-all}" in
  tests)   run tests testDebugUnitTest ;;
  debug)   run debug assembleDebug ;;
  release) run release assembleRelease ;;
  all)     run tests testDebugUnitTest assembleDebug; run release assembleRelease ;;
esac
echo done > "$OUT/done"

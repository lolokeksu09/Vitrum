#!/bin/bash
# Скачивает Xray v26.3.27 с проверкой контрольной суммы и кладёт в проект: libxray.so, geoip.dat, geosite.dat (в исходники они не входят из-за размера).
# С ключом --linux ставит ещё Linux-сборку Xray в ~/dl/xl/xray для проверок конфигураций (xray run -test).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; VER=v26.3.27; BASE="https://github.com/XTLS/Xray-core/releases/download/$VER"
for t in curl unzip python3 sha256sum; do command -v "$t" >/dev/null || { echo "Нет утилиты $t: apt-get update && apt-get install -y $t"; exit 1; }; done
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
get() {  # имя архива -> скачать и сверить сумму с файлом .dgst из релиза
  curl -fsSL --retry 3 -m 400 -o "$tmp/$1" "$BASE/$1"; curl -fsSL --retry 3 -m 60 -o "$tmp/$1.dgst" "$BASE/$1.dgst"
  want="$(python3 - "$tmp/$1.dgst" <<'PY'
import re,sys
for l in open(sys.argv[1],encoding='utf-8',errors='replace'):
    if re.match(r'\s*sha2?-?256',l,re.I):
        m=re.search(r'[0-9a-fA-F]{64}',l)
        if m: print(m.group(0).lower()); break
PY
)"
  have="$(sha256sum "$tmp/$1" | cut -d' ' -f1)"
  [ -n "$want" ] && [ "$want" = "$have" ] || { echo "КОНТРОЛЬНАЯ СУММА НЕ СОВПАЛА для $1 (ждали $want, получили $have)"; exit 1; }
  echo "ок: $1 sha256=$have"
}
get Xray-android-arm64-v8a.zip
mkdir -p "$tmp/a" "$ROOT/app/src/main/jniLibs/arm64-v8a" "$ROOT/app/src/main/assets"
unzip -q -o "$tmp/Xray-android-arm64-v8a.zip" -d "$tmp/a"
cp "$tmp/a/xray" "$ROOT/app/src/main/jniLibs/arm64-v8a/libxray.so"; chmod 755 "$ROOT/app/src/main/jniLibs/arm64-v8a/libxray.so"
cp "$tmp/a/geoip.dat" "$tmp/a/geosite.dat" "$ROOT/app/src/main/assets/"
( cd "$ROOT" && sha256sum -c tools/binaries.sha256 ) || { echo "Файлы не совпали с ожидаемыми: проверьте версию Xray"; exit 1; }
if [ "${1:-}" = "--linux" ]; then
  get Xray-linux-64.zip; mkdir -p "$HOME/dl/xl" "$HOME/dl/xray"
  unzip -q -o "$tmp/Xray-linux-64.zip" -d "$HOME/dl/xl"; chmod +x "$HOME/dl/xl/xray"; cp "$tmp/a/geoip.dat" "$tmp/a/geosite.dat" "$HOME/dl/xray/"
  echo "Linux-сборка: $HOME/dl/xl/xray, базы: $HOME/dl/xray (XRAY_LOCATION_ASSET)"
fi
echo "Готово."

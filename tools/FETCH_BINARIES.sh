#!/bin/bash
# Скачивает Xray v26.3.27 с проверкой контрольной суммы и кладёт в проект: libxray.so, geoip.dat, geosite.dat.
# При сбое release-download использует официальный GitHub Releases Assets API.
# С ключом --linux ставит ещё Linux-сборку Xray в ~/dl/xl/xray для проверок конфигураций.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VER=v26.3.27
BASE="https://github.com/XTLS/Xray-core/releases/download/$VER"
API="https://api.github.com/repos/XTLS/Xray-core"
for t in curl unzip python3 sha256sum; do
  command -v "$t" >/dev/null || { echo "Нет утилиты $t: apt-get update && apt-get install -y $t"; exit 1; }
done
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
release_json="$tmp/release.json"

# Получаем asset ID из официальных метаданных релиза; ID и URL не угадываются.
get_asset_id() {
  python3 - "$release_json" "$1" <<'PY'
import json,sys
data=json.load(open(sys.argv[1],encoding="utf-8"))
for asset in data.get("assets",[]):
    if asset.get("name")==sys.argv[2]:
        print(asset["id"])
        break
else:
    raise SystemExit("Asset отсутствует в официальном релизе: "+sys.argv[2])
PY
}

download_asset() {
  local name="$1" destination="$2" asset_id
  echo "Загрузка $name (официальный release URL)..."
  if curl -fLsS --retry 2 --retry-all-errors --retry-delay 3 --retry-max-time 180 -m 400 -o "$destination" "$BASE/$name"; then
    return 0
  fi

  echo "Предупреждение: release URL недоступен; переключаюсь на GitHub Releases Assets API для $name." >&2
  if [ ! -s "$release_json" ]; then
    curl -fLsS --retry 3 --retry-all-errors --retry-delay 3 --retry-max-time 120 -m 60 \
      -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28" \
      -o "$release_json" "$API/releases/tags/$VER"
  fi
  asset_id="$(get_asset_id "$name")"
  echo "Загрузка $name через Releases Assets API (asset id: $asset_id)..."
  curl -fLsS --retry 3 --retry-all-errors --retry-delay 5 --retry-max-time 300 -m 400 \
    -H "Accept: application/octet-stream" -H "X-GitHub-Api-Version: 2022-11-28" \
    -o "$destination" "$API/releases/assets/$asset_id"
}

get() {
  local name="$1" want have
  download_asset "$name" "$tmp/$name"
  download_asset "$name.dgst" "$tmp/$name.dgst"
  want="$(python3 - "$tmp/$name.dgst" <<'PY'
import re,sys
for line in open(sys.argv[1],encoding="utf-8",errors="replace"):
    if re.match(r"\\s*sha2?-?256",line,re.I):
        match=re.search(r"[0-9a-fA-F]{64}",line)
        if match:
            print(match.group(0).lower())
            break
PY
)"
  have="$(sha256sum "$tmp/$name" | cut -d' ' -f1)"
  [ -n "$want" ] && [ "$want" = "$have" ] || {
    echo "КОНТРОЛЬНАЯ СУММА НЕ СОВПАЛА для $name (ждали $want, получили $have)" >&2
    exit 1
  }
  echo "ок: $name sha256=$have"
}

get Xray-android-arm64-v8a.zip
mkdir -p "$tmp/a" "$ROOT/app/src/main/jniLibs/arm64-v8a" "$ROOT/app/src/main/assets"
unzip -q -o "$tmp/Xray-android-arm64-v8a.zip" -d "$tmp/a"
cp "$tmp/a/xray" "$ROOT/app/src/main/jniLibs/arm64-v8a/libxray.so"
chmod 755 "$ROOT/app/src/main/jniLibs/arm64-v8a/libxray.so"
cp "$tmp/a/geoip.dat" "$tmp/a/geosite.dat" "$ROOT/app/src/main/assets/"
( cd "$ROOT" && sha256sum -c tools/binaries.sha256 ) || {
  echo "Файлы не совпали с ожидаемыми: проверьте версию Xray" >&2
  exit 1
}
if [ "${1:-}" = "--linux" ]; then
  get Xray-linux-64.zip
  mkdir -p "$HOME/dl/xl" "$HOME/dl/xray"
  unzip -q -o "$tmp/Xray-linux-64.zip" -d "$HOME/dl/xl"
  chmod +x "$HOME/dl/xl/xray"
  cp "$tmp/a/geoip.dat" "$tmp/a/geosite.dat" "$HOME/dl/xray/"
  echo "Linux-сборка: $HOME/dl/xl/xray, базы: $HOME/dl/xray (XRAY_LOCATION_ASSET)"
fi
echo "Готово."

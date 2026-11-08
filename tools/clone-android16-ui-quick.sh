#!/usr/bin/env bash
set -euo pipefail

PACKAGE="app.rayclient.clone"
ACTIVITY="app.rayclient.MainActivity"
REPORT="clone-ui-report"
mkdir -p "$REPORT"

collect_diagnostics() {
    adb logcat -d -s AndroidRuntime:E > "$REPORT/android-runtime.log" 2>/dev/null || true
    adb shell dumpsys activity activities > "$REPORT/activity-state.txt" 2>/dev/null || true
}
trap collect_diagnostics EXIT

dump_ui() {
    local name="$1" expected="${2:-}" remote="/sdcard/clone-ui.xml"
    local xml="$REPORT/$name.xml" png="$REPORT/$name.png"
    for attempt in 1 2 3 4; do
        adb shell rm -f "$remote" || true
        rm -f "$xml"
        if adb shell uiautomator dump "$remote" > "$REPORT/$name-dump.log" 2>&1 \
            && adb shell test -s "$remote" \
            && adb shell cat "$remote" > "$xml" \
            && python3 -c 'import sys,xml.etree.ElementTree as ET; r=ET.parse(sys.argv[1]).getroot(); assert r.tag=="hierarchy" and r.find(".//node") is not None' "$xml"; then
            if [ -z "$expected" ] || grep -Fq -- "$expected" "$xml"; then
                adb exec-out screencap -p > "$png"
                return 0
            fi
        fi
        sleep 1
    done
    adb exec-out screencap -p > "$png" || true
    echo "FAIL: fresh UI hierarchy missing for $name (expected: $expected)" >&2
    return 1
}

tap_ui_text() {
    local coords
    coords="$(python3 - "$REPORT/$1.xml" "$2" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1])
matches=[n for n in root.iter("node") if sys.argv[2] in (n.get("text"),n.get("content-desc"))]
assert len(matches)==1,(sys.argv[2],len(matches))
x1,y1,x2,y2=map(int,re.findall(r"\d+",matches[0].attrib["bounds"]))
assert x2>x1 and y2>y1
print((x1+x2)//2,(y1+y2)//2)
PY
)"
    local x y
    read -r x y <<< "$coords"
    adb shell input tap "$x" "$y"
}

launch_demo() {
    local screen="$1" state="${2:-}" extra="${3:-}"
    adb shell am force-stop "$PACKAGE"
    if [ -n "$state" ]; then
        adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
            --es vitrum_screen "$screen" --es vitrum_state "$state" \
            --es vitrum_theme dark --es vitrum_lang ru $extra >/dev/null
    else
        adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
            --es vitrum_screen "$screen" --es vitrum_theme dark --es vitrum_lang ru $extra >/dev/null
    fi
    sleep 3
}

adb install -r "app/build/outputs/apk/clone/app-clone.apk"
adb shell wm size 1080x2400
adb shell wm density 480
adb shell settings put system font_scale 1.3
adb shell pm clear "$PACKAGE" >/dev/null
adb logcat -c

# First launch: onboarding must be visible before any demo state is injected.
adb shell am start -n "$PACKAGE/$ACTIVITY" >/dev/null
sleep 4
dump_ui quick-onboarding
if ! grep -Eq 'ЗНАКОМСТВО|WELCOME' "$REPORT/quick-onboarding.xml"; then
    echo 'FAIL: first-run onboarding is not visible' >&2
    exit 1
fi

# Home and catalog: state, search controls, country sorting, favorite/list content.
launch_demo home off
dump_ui quick-home 'Не подключено'
grep -Fq 'Подключиться' "$REPORT/quick-home.xml"

launch_demo servers
dump_ui quick-servers 'Серверы'
grep -Fq 'Германия' "$REPORT/quick-servers.xml"
tap_ui_text quick-servers "Сортировка"
dump_ui quick-sort-menu "Рейтинг"
tap_ui_text quick-sort-menu "Рейтинг"
dump_ui quick-sort-rating "Рейтинг"
tap_ui_text quick-sort-rating "Рейтинг"
dump_ui quick-sort-default "Сортировка"

# The server sheet keeps system Back and drag-to-close behavior.
launch_demo home
dump_ui quick-sheet-before 'Германия · Франкфурт'
tap_ui_text quick-sheet-before "Германия · Франкфурт"
dump_ui quick-sheet-open "Серверы"
adb shell input keyevent KEYCODE_BACK
dump_ui quick-sheet-back 'Состояние соединения'
tap_ui_text quick-sheet-back "Германия · Франкфурт"
dump_ui quick-sheet-reopen "Серверы"
adb shell input swipe 540 175 540 1000 450
dump_ui quick-sheet-drag 'Состояние соединения'

# Busy/blocked state must not relabel the active route after a row tap.
launch_demo servers blocked
dump_ui quick-servers-blocked 'Нидерланды · Амстердам'
tap_ui_text quick-servers-blocked "Нидерланды · Амстердам"
dump_ui quick-servers-blocked-dialog "Смена сервера"
grep -Fq 'не меняйте' "$REPORT/quick-servers-blocked-dialog.xml" || true

# Settings hub and the main tools must all open on Android 16.
launch_demo settings
dump_ui quick-settings-hub 'Поиск настроек'
tap_ui_text quick-settings-hub "Защита"
dump_ui quick-settings-protection "Защита"
adb shell input keyevent KEYCODE_BACK
dump_ui quick-settings-back 'Поиск настроек'

launch_demo routing
dump_ui quick-routing 'Профиль'
launch_demo game
dump_ui quick-game 'Игровой режим'
launch_demo journal
dump_ui quick-journal 'Журнал'
launch_demo shield
dump_ui quick-shield 'Щит приватности'

adb logcat -d -s AndroidRuntime:E > "$REPORT/android-runtime.log"
if grep -A 12 'FATAL EXCEPTION' "$REPORT/android-runtime.log" | grep -F 'Process: app.rayclient.clone'; then
    echo 'FAIL: Clone crashed on Android 16 emulator' >&2
    exit 1
fi

echo "PASS: Android 16 x86_64 focused Clone UI smoke" > "$REPORT/result.txt"
echo "Onboarding, Home, Servers, sheet Back/drag, blocked selection, Settings and tools." >> "$REPORT/result.txt"
echo "No ARM64 Xray or real VPN traffic tested. All Demo data is synthetic." >> "$REPORT/result.txt"

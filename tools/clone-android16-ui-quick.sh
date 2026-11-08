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
            if [ -z "$expected" ] || grep -Eq -- "$expected" "$xml"; then
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
root=ET.parse(sys.argv[1]).getroot()
parents={child:parent for parent in root.iter() for child in parent}
candidates=sys.argv[2].split("|")
matches=[n for n in root.iter("node") if any(candidate in (n.get("text"),n.get("content-desc")) for candidate in candidates)]
assert len(matches)==1,(sys.argv[2],len(matches))
target=matches[0]
while target is not None and target.get("clickable")!="true":
    target=parents.get(target)
assert target is not None,("no clickable ancestor",sys.argv[2])
# Tap the matched visible label; row center may be empty padding at font_scale 1.5.
x1,y1,x2,y2=map(int,re.findall(r"\\d+",matches[0].attrib["bounds"]))
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

# First launch: exercise all onboarding steps, system Back and skip.
adb shell am start -n "$PACKAGE/$ACTIVITY" >/dev/null
sleep 4
dump_ui quick-onboarding
if ! grep -Eq 'ЗНАКОМСТВО|WELCOME' "$REPORT/quick-onboarding.xml"; then
    echo 'FAIL: first-run onboarding is not visible' >&2
    exit 1
fi
tap_ui_text quick-onboarding "Далее|Next"
dump_ui quick-onboarding-add-server 'Добавьте сервер|Add a server'
tap_ui_text quick-onboarding-add-server "Далее|Next"
dump_ui quick-onboarding-readiness 'VPN'
adb shell input keyevent KEYCODE_BACK
dump_ui quick-onboarding-back 'Добавьте сервер|Add a server'
tap_ui_text quick-onboarding-back "Пропустить|Skip"
dump_ui quick-onboarding-finished
if grep -Eq 'ЗНАКОМСТВО|WELCOME' "$REPORT/quick-onboarding-finished.xml"; then
    echo 'FAIL: onboarding remained visible after Skip' >&2
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
dump_ui quick-sort-reset-menu "По умолчанию"
tap_ui_text quick-sort-reset-menu "По умолчанию"
dump_ui quick-sort-default "Сортировка"

# The add-server form exposes a paste field and dismisses without mutating data.
tap_ui_text quick-servers "Добавить"
dump_ui quick-add-server-dialog 'Ссылка'
tap_ui_text quick-add-server-dialog "Отмена"
dump_ui quick-add-server-cancelled "Серверы"

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

# Walk all nine settings destinations from the real hub and return with system Back.
launch_demo settings
dump_ui quick-settings-hub 'Поиск настроек'
adb shell settings put system font_scale 1.5
hub_screen=quick-settings-hub
while IFS='|' read -r page expected; do
    id="$(printf '%s' "$page" | tr ' ' '-' | tr '[:upper:]' '[:lower:]')"
    tap_ui_text "$hub_screen" "$page"
    # Wait for page-specific content, not its title (the same title is also in the hub).
    sleep 1
    dump_ui "quick-settings-$id" "$expected"
    adb shell input keyevent KEYCODE_BACK
    sleep 1
    hub_screen="quick-settings-back-$id"
    dump_ui "$hub_screen" 'Поиск настроек'
done <<'PAGES'
Защита|Переподключаться при смене сети
Маршруты|Профиль
Подключение|Работа в фоне
Автоматизация|Расписание VPN
Сеть и DNS|Отпечаток TLS
Оформление|Отклик
Сервис|Root
Справка|TLS
О приложении|SHA-256 сертификата
PAGES
adb shell settings put system font_scale 1.3

launch_demo routing
dump_ui quick-routing 'Профиль'

# A blocked Game result cannot change the active route through the result list.
launch_demo game-results blocked
dump_ui quick-game-blocked 'Выбрать лучший сервер'
tap_ui_text quick-game-blocked "Нидерланды · Амстердам"
dump_ui quick-game-blocked-dialog "Смена сервера"
adb shell input keyevent KEYCODE_BACK
dump_ui quick-game-blocked-return 'Германия · Франкфурт'

# Journal: distinguish an empty filter from an empty history and restore the filter.
launch_demo journal-traffic connected
dump_ui quick-journal 'Все 2'
tap_ui_text quick-journal "Все 2"
dump_ui quick-journal-filter-menu 'Блок 0'
tap_ui_text quick-journal-filter-menu "Блок 0"
dump_ui quick-journal-filter-empty 'Нет записей для этого маршрута'
tap_ui_text quick-journal-filter-empty "Сбросить фильтры"
dump_ui quick-journal-filter-reset 'example.com'

# Shield: direct-IP comparison requires a separate confirmation.
launch_demo shield connected
dump_ui quick-shield-connected 'Отдельно сравнить IP вне VPN'
tap_ui_text quick-shield-connected "Отдельно сравнить IP вне VPN"
dump_ui quick-shield-direct-confirm 'Сравнить IP вне VPN?'
if ! grep -Fq 'ваш прямой IP' "$REPORT/quick-shield-direct-confirm.xml"; then
    echo 'FAIL: Shield direct-IP consent warning is missing' >&2
    exit 1
fi
tap_ui_text quick-shield-direct-confirm "Отмена"
dump_ui quick-shield-direct-cancelled 'Отдельно сравнить IP вне VPN'

adb logcat -d -s AndroidRuntime:E > "$REPORT/android-runtime.log"
if grep -A 12 'FATAL EXCEPTION' "$REPORT/android-runtime.log" | grep -F 'Process: app.rayclient.clone'; then
    echo 'FAIL: Clone crashed on Android 16 emulator' >&2
    exit 1
fi

echo "PASS: Android 16 x86_64 focused Clone UI smoke" > "$REPORT/result.txt"
echo "Onboarding step navigation, Home, Servers, sheet Back/drag, blocked server selection, all nine Settings pages, routing, Game, Journal filter and Shield direct-IP consent." >> "$REPORT/result.txt"
echo "No ARM64 Xray or real VPN traffic tested. All Demo data is synthetic." >> "$REPORT/result.txt"

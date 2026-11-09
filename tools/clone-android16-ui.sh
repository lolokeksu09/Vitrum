#!/usr/bin/env bash
set -euo pipefail
PACKAGE="app.rayclient.clone"
ACTIVITY="app.rayclient.MainActivity"
mkdir -p clone-ui-report
collect_diagnostics() {
    adb logcat -d -s AndroidRuntime:E > clone-ui-report/android-runtime.log 2>/dev/null || true
    adb shell dumpsys activity activities > clone-ui-report/activity-state.txt 2>/dev/null || true
}
trap collect_diagnostics EXIT
adb install -r "app/build/outputs/apk/clone/app-clone.apk"
adb shell wm size 1080x2400
adb shell wm density 440
adb shell settings put system font_scale 1.0
adb shell pm clear "$PACKAGE" >/dev/null

dump_ui() {
    local name="$1" expected="${2:-}" remote="/sdcard/clone-ui.xml"
    local xml="clone-ui-report/$name.xml" png="clone-ui-report/$name.png"
    local attempt
    for attempt in 1 2 3 4 5 6; do
        # Android 16 sometimes reports a null accessibility root. Without removing
        # the old remote XML, a failed dump silently reuses the previous screen.
        adb shell rm -f "$remote" || true
        rm -f "$xml"
        if adb shell uiautomator dump "$remote" > "clone-ui-report/$name-dump.log" 2>&1 \
            && adb shell test -s "$remote" \
            && adb shell cat "$remote" > "$xml" \
            && python3 -c 'import sys, xml.etree.ElementTree as ET; root = ET.parse(sys.argv[1]).getroot(); assert root.tag == "hierarchy" and root.find(".//node") is not None' "$xml"; then
            if [ -z "$expected" ] || grep -Fq -- "$expected" "$xml"; then
                adb exec-out screencap -p > "$png"
                return 0
            fi
        fi
        echo "WARNING: fresh UI tree for $name unavailable (attempt $attempt/6)" >&2
        sleep 2
    done
    # Save the actual screen even when accessibility dumping is broken.
    adb exec-out screencap -p > "$png" || true
    echo "FAIL: cannot obtain a fresh UI hierarchy for $name" >&2
    return 1
}

tap_ui_text() {
    local coords
    coords="$(python3 - "clone-ui-report/$1.xml" "$2" <<'PY_TAP'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1])
matches = [n for n in root.iter("node") if sys.argv[2] in (n.get("text"), n.get("content-desc"))]
assert len(matches) == 1, (sys.argv[2], len(matches))
x1, y1, x2, y2 = map(int, re.findall(r"\d+", matches[0].attrib["bounds"]))
assert x2 > x1 and y2 > y1
print((x1+x2)//2, (y1+y2)//2)
PY_TAP
)"
    local x y
    read -r x y <<< "$coords"
    adb shell input tap "$x" "$y"
}

# New users must see onboarding, not an unexpected permission dialog.
adb logcat -c
adb shell am start -n "$PACKAGE/$ACTIVITY"
sleep 8
dump_ui onboarding
if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
    echo 'FAIL: Clone process disappeared after launch; inspect AndroidRuntime log' >&2
    exit 1
fi
if ! grep -Eq 'ЗНАКОМСТВО|WELCOME' clone-ui-report/onboarding.xml; then
    echo 'FAIL: first-run onboarding not visible; inspect foreground window, crash log and permission dialog' >&2
    exit 1
fi
if grep -Eq 'com\.android\.permissioncontroller|com\.google\.android\.permissioncontroller' clone-ui-report/onboarding.xml; then
    echo 'FAIL: unsolicited runtime-permission dialog appeared before onboarding' >&2
    exit 1
fi

# The built-in debug-only demo contains synthetic data; no production VPN is started.
for entry in 'home:480:1.0' 'servers:480:1.3' 'routing:440:1.3' 'home:400:1.5'; do
    IFS=: read -r screen density scale <<< "$entry"
    adb shell wm density "$density"
    adb shell settings put system font_scale "$scale"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
        --es vitrum_screen "$screen" --es vitrum_theme dark --es vitrum_lang ru
    sleep 5
    id="${screen}-d${density}-f${scale}"
    dump_ui "$id"
    test -s "clone-ui-report/$id.png"
done

# PR-02: the Home primary action and status must stay explicit in every VPN state.
for entry in 'off:Не подключено' 'connecting:Подключение…' 'connected:VPN запущен' \
    'switching:Переключение сервера…' 'blocked:Трафик заблокирован' 'error:Ошибка' \
    'long-server:Не подключено'; do
    IFS=: read -r state expected <<< "$entry"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
        --es vitrum_screen home --es vitrum_state "$state" --es vitrum_theme dark --es vitrum_lang ru
    sleep 4
    dump_ui "home-state-$state" "$expected"
    case "$state" in
        connected|switching|blocked)
            grep -Fq 'Отключиться' "clone-ui-report/home-state-$state.xml"
            ;;
        off|error)
            grep -Fq 'Подключиться' "clone-ui-report/home-state-$state.xml"
            ;;
        connecting)
            grep -Fq 'Отменить подключение' "clone-ui-report/home-state-$state.xml"
            ;;
        long-server)
            grep -Fq 'Россия →' "clone-ui-report/home-state-$state.xml"
            grep -Fq 'Германия (мост)' "clone-ui-report/home-state-$state.xml"
            grep -Fq 'Подключиться' "clone-ui-report/home-state-$state.xml"
            ;;
    esac
done

# With no saved server the main action becomes Add server, while all existing
# first-use import methods remain discoverable and no VPN connection is attempted.
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen home --es vitrum_state empty --es vitrum_theme dark --es vitrum_lang ru
sleep 4
dump_ui home-empty 'Добавить сервер'
for expected in 'Добавьте сервер' 'Вставить из буфера' 'QR камерой' 'QR из фото' 'Ввести самому'; do
    grep -Fq "$expected" clone-ui-report/home-empty.xml || {
        echo "FAIL: first-use Home action missing: $expected" >&2
        exit 1
    }
done

# Compact-height + maximum planned text scale: the primary action must remain
# in the first viewport, not below the secondary tools or traffic statistics.
adb shell wm size 1080x1920
adb shell wm density 480
adb shell settings put system font_scale 1.5
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen home --es vitrum_state off --es vitrum_theme dark --es vitrum_lang ru
sleep 4
dump_ui home-compact-f150 'Подключиться'
for entry in 'switching:Отключиться:Переключение сервера…' \
    'blocked:Отключиться:Трафик заблокирован' 'error:Подключиться:Ошибка'; do
    IFS=: read -r state action expected <<< "$entry"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
        --es vitrum_screen home --es vitrum_state "$state" --es vitrum_theme dark --es vitrum_lang ru
    sleep 4
    dump_ui "home-compact-$state-f150" "$action"
    grep -Fq "$expected" "clone-ui-report/home-compact-$state-f150.xml"
done
adb shell wm size 1080x2400
adb shell settings put system font_scale 1.0

# Check the manual-evidence card under synthetic connected UI; no VPN/Xray traffic is generated.
for entry in 'ru:dark:480:1.3' 'en:light:400:1.5'; do
    IFS=: read -r lang theme density scale <<< "$entry"
    adb shell wm density "$density"
    adb shell settings put system font_scale "$scale"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
        --es vitrum_screen home --es vitrum_state connected --es vitrum_theme "$theme" --es vitrum_lang "$lang"
    sleep 5
    id="home-evidence-$lang-d$density-f$scale"
    dump_ui "$id"
    test -s "clone-ui-report/$id.png"
    if [ "$lang" = ru ]; then
        expected='Доступ не проверен'
    else
        expected='Access not checked'
    fi
    evidence_xml="clone-ui-report/$id.xml"
    for scroll_attempt in 1 2 3; do
        if grep -Fq "$expected" "$evidence_xml"; then
            break
        fi
        # The evidence panel is below the server card on compact screens and
        # larger font scales. Scroll the actual home content before asserting it.
        adb shell input swipe 540 1850 540 900 400
        sleep 1
        scroll_id="$id-scroll$scroll_attempt"
        dump_ui "$scroll_id"
        evidence_xml="clone-ui-report/$scroll_id.xml"
    done
    if ! grep -Fq "$expected" "$evidence_xml"; then
        echo "FAIL: unverified connection evidence state is not reachable ($lang); inspect $id*.png" >&2
        exit 1
    fi
done

# The demo state cannot exercise a real VPN switch (there is no TUN/Xray).
# Assert that the UI advertises the new guarded-switch flow instead of the
# obsolete disconnect-only warning. Actual switch/rollback requires ARM64 checks.
adb shell wm density 480
adb shell settings put system font_scale 1.3
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen servers --es vitrum_state connected --es vitrum_theme dark --es vitrum_lang ru
sleep 5
dump_ui servers-active-before "Нажмите на другой сервер для переключения без отключения VPN."
if ! grep -Fq 'Нидерланды' clone-ui-report/servers-active-before.xml; then
    echo 'FAIL: synthetic alternate server is not visible in UI tree' >&2
    exit 1
fi

# Measure is deliberately not invoked: all demo server data is synthetic.
tap_ui_text servers-active-before "Сортировка"
dump_ui servers-sort-menu 'text="Рейтинг"'
tap_ui_text servers-sort-menu "Рейтинг"
dump_ui servers-sort-rating 'text="Рейтинг"'
tap_ui_text servers-sort-rating "Рейтинг"
dump_ui servers-sort-reset 'text="По умолчанию"'
tap_ui_text servers-sort-reset "По умолчанию"
dump_ui servers-sort-restored 'text="Сортировка"'
tap_ui_text servers-sort-restored "Все страны"
dump_ui servers-country-menu 'Германия · 1'
tap_ui_text servers-country-menu "🇩🇪 Германия · 1"
dump_ui servers-country-filtered 'text="Германия"'
if grep -Fq 'Нидерланды' clone-ui-report/servers-country-filtered.xml; then
    echo 'FAIL: server from another country remained visible' >&2
    exit 1
fi

# PR-03: offline, refresh, measurement, filtered-empty and no-server states are distinct.
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true --ez vitrum_offline true \
    --es vitrum_screen servers --es vitrum_theme dark --es vitrum_lang ru
sleep 4
dump_ui servers-offline 'Нет подключения к интернету.'
grep -Fq 'Германия · Франкфурт' clone-ui-report/servers-offline.xml

adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true --ez vitrum_sub_busy true \
    --es vitrum_screen servers --es vitrum_theme dark --es vitrum_lang ru
sleep 4
dump_ui servers-refreshing 'Обновление подписок…'
grep -Fq 'Германия · Франкфурт' clone-ui-report/servers-refreshing.xml

adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true --ez vitrum_ping_busy true \
    --es vitrum_screen servers --es vitrum_theme dark --es vitrum_lang ru
sleep 4
dump_ui servers-measuring 'Измерение задержки серверов…'
grep -Fq 'Германия · Франкфурт' clone-ui-report/servers-measuring.xml

adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true --es vitrum_query no-such-server \
    --es vitrum_screen servers --es vitrum_theme dark --es vitrum_lang ru
sleep 4
dump_ui servers-filter-empty 'По выбранным фильтрам серверов нет'
tap_ui_text servers-filter-empty "Сбросить фильтры"
dump_ui servers-filter-reset 'Германия · Франкфурт'

adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true --es vitrum_state empty \
    --es vitrum_screen servers --es vitrum_theme dark --es vitrum_lang ru
sleep 4
dump_ui servers-empty 'Пока пусто'

# A blocked tunnel must prevent a row tap from changing the selected route.
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen servers --es vitrum_state blocked --es vitrum_theme dark --es vitrum_lang ru
sleep 4
dump_ui servers-blocked-before 'Нидерланды · Амстердам'
tap_ui_text servers-blocked-before "Нидерланды · Амстердам"
dump_ui servers-blocked-dialog 'Смена сервера'
if ! grep -Fq 'Дождитесь завершения' clone-ui-report/servers-blocked-before.xml; then
    echo 'FAIL: blocked state warning is missing from Servers catalog' >&2
    exit 1
fi
if ! grep -Fq 'Смена сервера' clone-ui-report/servers-blocked-dialog.xml; then
    echo 'FAIL: blocked server selection did not show its guard dialog' >&2
    exit 1
fi

# PR-01: exercise the real Home -> ServersSheet entry, system Back and handle drag.
# The demo fixture is synthetic; this validates Compose overlay/navigation behavior only.
adb shell wm density 480
adb shell settings put system font_scale 1.3
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen home --es vitrum_theme dark --es vitrum_lang ru
sleep 5
dump_ui sheet-home-before 'text="Германия · Франкфурт"'
tap_ui_text sheet-home-before "Германия · Франкфурт"
sleep 1
dump_ui sheet-open 'text="Серверы"'
if ! grep -Fq 'Нидерланды' clone-ui-report/sheet-open.xml; then
    echo 'FAIL: ServersSheet did not expose the synthetic server list' >&2
    exit 1
fi
adb shell input keyevent KEYCODE_BACK
sleep 1
dump_ui sheet-closed-back 'text="Состояние соединения"'
if grep -Fq 'Нидерланды' clone-ui-report/sheet-closed-back.xml; then
    echo 'FAIL: ServersSheet content remained in the accessibility tree after system Back' >&2
    exit 1
fi

# Reopen and drag the visible sheet handle down beyond its 120dp close threshold.
tap_ui_text sheet-closed-back "Германия · Франкфурт"
sleep 1
dump_ui sheet-open-drag 'text="Серверы"'
adb shell input swipe 540 175 540 1000 450
sleep 1
dump_ui sheet-closed-drag 'text="Состояние соединения"'
if grep -Fq 'Нидерланды' clone-ui-report/sheet-closed-drag.xml; then
    echo 'FAIL: ServersSheet did not close after dragging its handle down' >&2
    exit 1
fi

# PR-05 visual follow-up: first-run onboarding across both themes, locales and text scales.
# The dedicated demo extra re-opens onboarding after synthetic fixtures are loaded.
for theme in dark light; do
    for lang in ru en; do
        for scale in 1.0 1.5; do
            adb shell wm size 1080x2400
            adb shell wm density 480
            adb shell settings put system font_scale "$scale"
            adb shell pm clear "$PACKAGE" >/dev/null
            adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true --ez vitrum_onboarding true \
                --es vitrum_screen home --es vitrum_theme "$theme" --es vitrum_lang "$lang" \
                > clone-ui-report/onboarding-launch.log
            sleep 4
            prefix="onboarding-$theme-$lang-f$scale"
            if [ "$lang" = ru ]; then
                page0='Добавьте сервер или подписку'
                page1='Добавьте сервер'
                page2='Готовность к подключению'
            else
                page0='Add a server or subscription'
                page1='Add a server'
                page2='Ready to connect'
            fi
            dump_ui "$prefix-page-1" "$page0"
            tap_ui_text "$prefix-page-1" "Далее"
            if [ "$lang" = en ]; then
                # The shared I18n Text wrapper translates navigation actions.
                tap_ui_text "$prefix-page-1" "Next"
            fi
            dump_ui "$prefix-page-2" "$page1"
            if [ "$lang" = ru ]; then
                tap_ui_text "$prefix-page-2" "Далее"
            else
                tap_ui_text "$prefix-page-2" "Next"
            fi
            dump_ui "$prefix-page-3" "$page2"
            test -s "clone-ui-report/$prefix-page-1.png"
            test -s "clone-ui-report/$prefix-page-2.png"
            test -s "clone-ui-report/$prefix-page-3.png"
        done
    done
done

# Capture the bottom of every Settings destination at the most demanding text scale.
# The bottom navigation must remain present after the content reaches its scroll end.
printf 'page\ttheme\tlanguage\tfont_scale\tpng\txml\n' > clone-ui-report/settings-bottom.tsv
for theme in dark light; do
    for lang in ru en; do
        for page in protect routes connect auto network look service help about; do
            adb shell wm size 1080x2400
            adb shell wm density 480
            adb shell settings put system font_scale 1.5
            adb shell am force-stop "$PACKAGE"
            adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
                --es vitrum_screen "settings:$page" --es vitrum_theme "$theme" --es vitrum_lang "$lang" \
                > clone-ui-report/settings-bottom-launch.log
            sleep 3
            id="settings-bottom-$page-$theme-$lang-f1.5"
            dump_ui "$id"
            for swipe in 1 2 3 4 5; do
                adb shell input swipe 540 1900 540 500 350
            done
            dump_ui "$id"
            grep -Fq 'Главная' "clone-ui-report/$id.xml" || grep -Fq 'Home' "clone-ui-report/$id.xml"
            grep -Fq 'Настройки' "clone-ui-report/$id.xml" || grep -Fq 'Settings' "clone-ui-report/$id.xml"
            printf '%s\t%s\t%s\t1.5\t%s.png\t%s.xml\n' "$page" "$theme" "$lang" "$id" "$id" >> clone-ui-report/settings-bottom.tsv
        done
    done
done
test "$(($(wc -l < clone-ui-report/settings-bottom.tsv) - 1))" -eq 36
adb shell wm density 440
adb shell settings put system font_scale 1.0

# Verify the settings destinations and the full-screen tools with large text.
adb shell wm density 480
# Full Settings acceptance matrix: all nine destinations, RU/EN, dark/light,
# and font scales 1.0/1.3/1.5. Widths are ~432/393/360dp respectively.
# These are synthetic UI fixtures only; no VPN, server or user data is used.
# Read the fixture list once, before any adb command can inherit this script's
# stdin. adb shell may consume stdin, so a here-doc directly on a read-loop
# silently truncates the Settings destinations after the first entry.
settings_pages=()
mapfile -t settings_pages <<'SETTINGS_PAGES'
protect|settings:protect|Защита|Protection
routes|settings:routes|Маршруты|Routes
connect|settings:connect|Подключение|Connection
auto|settings:auto|Автоматизация|Automation
network|settings:network|Сеть и DNS|Network and DNS
look|settings:look|Оформление|Appearance
service|settings:service|Сервис|Service
help|settings:help|Справка|Help
about|settings:about|О приложении|About the app
SETTINGS_PAGES
if [ "${#settings_pages[@]}" -ne 9 ]; then
    echo "FAIL: expected 9 Settings destinations, found ${#settings_pages[@]}" >&2
    exit 1
fi

settings_matrix_sha="$(git rev-parse HEAD)"
printf 'page\ttheme\tlanguage\tdensity_dpi\tfont_scale\tpng\txml\tcommit\n' > clone-ui-report/settings-matrix.tsv
for lang in ru en; do
    for theme in dark light; do
        for scale_entry in '1.0:400' '1.3:440' '1.5:480'; do
            IFS=: read -r scale density <<< "$scale_entry"
            for settings_entry in "${settings_pages[@]}"; do
                IFS='|' read -r page screen ru_title en_title <<< "$settings_entry"
                if [ "$lang" = ru ]; then expected="$ru_title"; else expected="$en_title"; fi
                adb shell wm size 1080x2400
                adb shell wm density "$density"
                adb shell settings put system font_scale "$scale"
                adb shell am force-stop "$PACKAGE"
                adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
                    --es vitrum_screen "$screen" --es vitrum_theme "$theme" --es vitrum_lang "$lang" \
                    > clone-ui-report/settings-launch.log
                sleep 3
                safe_page="$(printf '%s' "$page" | tr ':' '-')"
                id="settings-$safe_page-$theme-$lang-d$density-f$scale"
                dump_ui "$id" "text=\"$expected\""
                test -s "clone-ui-report/$id.png"
                printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
                    "$page" "$theme" "$lang" "$density" "$scale" "$id.png" "$id.xml" "$settings_matrix_sha" \
                    >> clone-ui-report/settings-matrix.tsv
            done
        done
    done
done
test "$(($(wc -l < clone-ui-report/settings-matrix.tsv) - 1))" -eq 108
adb shell wm density 440
adb shell settings put system font_scale 1.0

adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen routing --es vitrum_theme dark --es vitrum_lang ru
dump_ui routing-profile-actions 'text="Новый профиль"'
tap_ui_text routing-profile-actions "Новый профиль"
dump_ui routing-new-profile 'text="Отмена"'
tap_ui_text routing-new-profile "Отмена"

# PR-04: rules are ordered, have accessible 48dp controls, and deletion is confirmed.
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen routing --es vitrum_theme dark --es vitrum_lang ru
sleep 4
rule_screen=''
for attempt in $(seq 1 24); do
    # Routing contains several long sections before the ordered rules. Keep
    # scrolling the same viewport until the rule action itself is accessible.
    dump_ui routing-rules-search
    if grep -Fq 'Удалить правило: example.com' clone-ui-report/routing-rules-search.xml; then
        rule_screen="routing-rules-search"
        break
    fi
    adb shell input swipe 540 1900 540 700 400
    sleep 0.4
done
if [ -z "$rule_screen" ]; then
    echo 'FAIL: routing rules did not become reachable by vertical scrolling' >&2
    exit 1
fi
tap_ui_text "$rule_screen" "Удалить правило: example.com"
dump_ui routing-delete-confirm 'Удалить правило?'
if ! grep -Fq 'Это действие нельзя отменить.' clone-ui-report/routing-delete-confirm.xml; then
    echo 'FAIL: route rule deletion was not protected by a confirmation' >&2
    exit 1
fi
tap_ui_text routing-delete-confirm "Отмена"
dump_ui routing-delete-cancelled 'example.com'


adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen settings:network --es vitrum_theme dark --es vitrum_lang ru
dump_ui settings-network-top 'text="Сеть и DNS"'
adb shell input swipe 500 1800 500 520 500
dump_ui settings-network-scrolled 'content-desc="Назад"'
tap_ui_text settings-network-scrolled "Назад"
dump_ui settings-returned 'text="Поиск настроек"'

# Blocked tunnel: a result must not replace the active server ID.
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen game-results --es vitrum_state blocked --es vitrum_theme dark --es vitrum_lang ru
dump_ui game-blocked-results 'text="Нидерланды · Амстердам"'
tap_ui_text game-blocked-results "Нидерланды · Амстердам"
dump_ui game-switch-blocked 'text="Смена сервера"'
tap_ui_text game-switch-blocked "Понятно"
adb shell input keyevent KEYCODE_BACK
dump_ui game-blocked-home 'text="Германия · Франкфурт"'

# With VPN off, the best server button can update the next connection.
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen game-results --es vitrum_theme dark --es vitrum_lang ru
dump_ui game-disconnected-results 'text="Выбрать лучший сервер"'
tap_ui_text game-disconnected-results "Выбрать лучший сервер"
adb shell input keyevent KEYCODE_BACK
dump_ui game-disconnected-home 'text="Нидерланды · Амстердам"'

# The traffic journal distinguishes a filter with no matches and lets it be reset.
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen journal-traffic --es vitrum_state connected --es vitrum_theme dark --es vitrum_lang ru
dump_ui journal-traffic 'text="Все 2"'
tap_ui_text journal-traffic "Все 2"
dump_ui journal-route-menu 'text="Блок 0"'
tap_ui_text journal-route-menu "Блок 0"
dump_ui journal-filter-empty 'text="Нет записей для этого маршрута"'
tap_ui_text journal-filter-empty "Сбросить фильтры"
dump_ui journal-filter-reset 'text="example.com"'

# Stage 0 baseline matrix. These are additional captures, not VPN/runtime assertions.
# Density controls Android dp width: 1080 px => 360dp @480, ~393dp @440, 432dp @400.
# Synthetic Demo only: never use real profile/server data for screenshot artifacts.
baseline_sha="$(git rev-parse HEAD)"
printf 'screen\ttheme\tlanguage\tdensity_dpi\tfont_scale\tapprox_width_dp\tpng\txml\tcommit\n' > clone-ui-report/baseline-matrix.tsv
for entry in \
    'home|dark|ru|480|1.5' \
    'home|light|ru|440|1.3' \
    'servers|light|ru|440|1.3' \
    'routing|light|ru|440|1.3' \
    'settings|light|ru|440|1.3' \
    'home|light|en|400|1.5' \
    'servers|light|en|400|1.5' \
    'routing|light|en|400|1.5' \
    'settings|light|en|400|1.5' \
    'settings:protect|light|en|400|1.5' \
    'settings:connect|light|en|400|1.5' \
    'settings:auto|light|en|400|1.5' \
    'settings:network|light|en|400|1.5' \
    'settings:look|light|en|400|1.5' \
    'settings:service|light|en|400|1.5' \
    'settings:help|light|en|400|1.5' \
    'settings:about|light|en|400|1.5' \
    'game|light|en|400|1.5' \
    'journal|light|en|400|1.5' \
    'shield|light|en|400|1.5'; do
    IFS='|' read -r screen theme lang density scale <<< "$entry"
    adb shell wm size 1080x2400
    adb shell wm density "$density"
    adb shell settings put system font_scale "$scale"
    adb shell am force-stop "$PACKAGE"
    adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
        --es vitrum_screen "$screen" --es vitrum_theme "$theme" --es vitrum_lang "$lang" \
        > clone-ui-report/baseline-launch.log
    sleep 3
    safe_screen="$(printf '%s' "$screen" | tr ':' '-')"
    id="baseline-$safe_screen-$theme-$lang-d$density-f$scale"
    # A syntactically valid accessibility tree may belong to the previous or
    # wrong screen. Require a unique visible marker for the requested route.
    case "$lang:$screen" in
        ru:home) expected='text="Не подключено"' ;;
        ru:servers) expected='text="Серверы"' ;;
        ru:routing) expected='text="Маршруты"' ;;
        ru:settings) expected='text="Поиск настроек"' ;;
        en:home) expected='text="Not connected"' ;;
        en:servers) expected='text="Servers"' ;;
        en:routing) expected='text="Routes"' ;;
        en:settings) expected='text="Search settings"' ;;
        en:settings:protect) expected='text="Protection"' ;;
        en:settings:connect) expected='text="Connection"' ;;
        en:settings:auto) expected='text="Automation"' ;;
        en:settings:network) expected='text="Network and DNS"' ;;
        en:settings:look) expected='text="Appearance"' ;;
        en:settings:service) expected='text="Service"' ;;
        en:settings:help) expected='text="Help"' ;;
        en:settings:about) expected='text="About the app"' ;;
        en:game) expected='text="Game mode"' ;;
        en:journal) expected='text="Traffic and events"' ;;
        en:shield) expected='text="Privacy Shield"' ;;
        *) echo "FAIL: baseline case has no screen assertion: $lang:$screen" >&2; exit 1 ;;
    esac
    dump_ui "$id" "$expected"
    # Check PNG magic and the actual pixel size using stdlib (Pillow not required).
    python3 - "clone-ui-report/$id.png" <<'PY_PNG'
import struct
import sys
with open(sys.argv[1], "rb") as f:
    header = f.read(24)
assert len(header) == 24 and header[:8] == b"\x89PNG\r\n\x1a\n", sys.argv[1]
width, height = struct.unpack(">II", header[16:24])
assert (width, height) == (1080, 2400), (sys.argv[1], width, height)
PY_PNG
    width_dp=$((1080 * 160 / density))
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$screen" "$theme" "$lang" "$density" "$scale" "$width_dp" \
        "$id.png" "$id.xml" "$baseline_sha" >> clone-ui-report/baseline-matrix.tsv
done
# Restore predictable emulator defaults; no persistent user app data is involved.
adb shell wm density 440
adb shell settings put system font_scale 1.0

adb logcat -d -s AndroidRuntime:E > clone-ui-report/android-runtime.log
if grep -A 12 'FATAL EXCEPTION' clone-ui-report/android-runtime.log | grep -F 'Process: app.rayclient.clone'; then
    echo 'FAIL: Clone crashed on Android 16 emulator' >&2
    exit 1
fi
echo "PASS: Android 16 x86_64 UI-only onboarding and demo smoke" > clone-ui-report/result.txt
echo "No ARM64 Xray or VPN traffic tested. All demo servers synthetic." >> clone-ui-report/result.txt

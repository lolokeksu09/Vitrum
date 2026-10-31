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
    if ! grep -Fq "$expected" "clone-ui-report/$id.xml"; then
        echo "FAIL: unverified connection evidence state missing ($lang); inspect $id.png" >&2
        exit 1
    fi
done

# Exercise the active-VPN guard in a debug-only, synthetic connected state.
adb shell wm density 480
adb shell settings put system font_scale 1.3
adb shell am force-stop "$PACKAGE"
adb shell am start -n "$PACKAGE/$ACTIVITY" --ez vitrum_demo true \
    --es vitrum_screen servers --es vitrum_state connected --es vitrum_theme dark --es vitrum_lang ru
sleep 5
dump_ui servers-active-before "Чтобы выбрать другой сервер"
if ! grep -Fq 'Чтобы выбрать другой сервер' clone-ui-report/servers-active-before.xml; then
    echo 'FAIL: active-session server warning is not visible in UI tree' >&2
    exit 1
fi
# Tap a different synthetic server in the actual list (not the selected Germany).
tap="$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
root = ET.parse('clone-ui-report/servers-active-before.xml').getroot()
for node in root.iter('node'):
    a = node.attrib
    label = a.get('text', '') + ' ' + a.get('content-desc', '')
    if 'Нидерланды' not in label:
        continue
    m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', a.get('bounds', ''))
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        print(x1 + min(45, max(5, (x2 - x1) // 4)), (y1 + y2) // 2)
        break
else:
    raise SystemExit('FAIL: target server not found in UI hierarchy')
PY
)"
adb shell input tap $tap
sleep 2
dump_ui servers-active-confirmation "Новый сервер не выбран"
if ! grep -Fq 'Новый сервер не выбран' clone-ui-report/servers-active-confirmation.xml; then
    echo 'FAIL: selecting a different server while connected did not show confirmation' >&2
    exit 1
fi

adb logcat -d -s AndroidRuntime:E > clone-ui-report/android-runtime.log
if grep -A 12 'FATAL EXCEPTION' clone-ui-report/android-runtime.log | grep -F 'Process: app.rayclient.clone'; then
    echo 'FAIL: Clone crashed on Android 16 emulator' >&2
    exit 1
fi
echo "PASS: Android 16 x86_64 UI-only onboarding and demo smoke" > clone-ui-report/result.txt
echo "No ARM64 Xray or VPN traffic tested. All demo servers synthetic." >> clone-ui-report/result.txt

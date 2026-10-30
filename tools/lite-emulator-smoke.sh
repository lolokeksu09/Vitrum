#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-report
APK=lite/build/outputs/apk/debug/lite-debug.apk
adb install -r "$APK"
adb shell wm size 800x1280
adb shell am start -n app.rayclient.lite/.LiteActivity
sleep 5

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb shell cat /sdcard/window.xml > "emulator-report/$1.xml"
}
click_text() {
  python3 - "$1" "$2" <<'PY'
import re,sys,xml.etree.ElementTree as ET
name, target=sys.argv[1:3]
root=ET.parse("emulator-report/"+name+".xml").getroot()
for node in root.iter("node"):
    if node.attrib.get("text","")==target:
        nums=list(map(int,re.findall(r'\d+',node.attrib["bounds"])))
        if len(nums)==4:
            print((nums[0]+nums[2])//2,(nums[1]+nums[3])//2)
            break
else:
    raise SystemExit("UI element not found: "+target)
PY
}
tap_node() {
  read -r x y < <(click_text "$1" "$2")
  adb shell input tap "$x" "$y"
}
dump_ui first
grep -q 'Нет сохранённого профиля' emulator-report/first.xml
grep -q 'Vitrum Lite' emulator-report/first.xml
adb exec-out screencap -p > emulator-report/first-launch.png

# Enter synthetic credentials; never a real user subscription.
python3 - <<'PY'
import re,xml.etree.ElementTree as ET
root=ET.parse('emulator-report/first.xml').getroot()
for node in root.iter('node'):
    if node.attrib.get('class')=='android.widget.EditText':
        nums=list(map(int,re.findall(r'\d+',node.attrib['bounds'])))
        with open('emulator-report/input-point.txt','w') as out:
            out.write(f'{(nums[0]+nums[2])//2} {(nums[1]+nums[3])//2}\n')
        break
else:
    raise SystemExit('No input field')
PY
read -r x y < emulator-report/input-point.txt
adb shell input tap "$x" "$y"
echo "Typing synthetic VLESS link (never real credentials)"
adb shell input text 'vless://00000000-0000-0000-0000-000000000001@example.org:443'
adb shell input keyevent 4
sleep 1
dump_ui before-save
echo "Saving synthetic encrypted profile in Android 6 emulator"
tap_node before-save 'Сохранить профиль'
sleep 2
dump_ui saved
grep -q 'Можно подключаться без повторного ввода' emulator-report/saved.xml
! grep -q '00000000-0000-0000-0000-000000000001' emulator-report/saved.xml
adb exec-out screencap -p > emulator-report/saved-profile.png

adb shell am force-stop app.rayclient.lite
adb shell am start -n app.rayclient.lite/.LiteActivity
sleep 3
dump_ui restored
grep -q 'Можно подключаться без повторного ввода' emulator-report/restored.xml
adb exec-out screencap -p > emulator-report/restored-profile.png

# STOP remains reachable even with no in-process CONNECT state.
tap_node restored 'Отключить VPN'
sleep 3
dump_ui stopped
grep -q 'Отключить VPN' emulator-report/stopped.xml
adb exec-out screencap -p > emulator-report/after-stop.png

echo 'PASS: UI launch, save encrypted profile, restart restore, STOP action on Android 6 x86 emulator' | tee emulator-report/result.txt
echo 'UI-only x86 smoke: does not test ARM32 native core or network traffic' >> emulator-report/result.txt

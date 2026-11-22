#!/bin/bash
# Startet Domi TV im Emulator und macht Screenshots.
mkdir -p shots
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell wm size > shots/info.txt
adb shell wm density >> shots/info.txt
adb logcat -c
(adb shell screenrecord --time-limit 9 --size 960x540 /sdcard/intro.mp4 &) ; sleep 0.5
adb shell am start -n at.domi.tvlauncher/.MainActivity --ez fx_test true
shot() { adb exec-out screencap -p > "shots/$1.png"; }
sleep 0.6; shot 01_intro_a
sleep 0.6; shot 02_intro_b
sleep 0.6; shot 03_intro_c
sleep 1.2; shot 04_intro_d
sleep 3.5; shot 05_home
for i in 1 2 3 4; do adb shell input keyevent 22; sleep 1.6; shot "06_right_$i"; done
adb shell input keyevent 21; sleep 0.25; shot 07_mid_switch
sleep 1.5
adb shell input keyevent 20; sleep 1.5; shot 08_down
adb shell input keyevent 20; sleep 1.8; shot 08b_rap
adb shell input keyevent 22; sleep 1.2; shot 08c_rap2
adb shell input keyevent 22; adb shell input keyevent 22; adb shell input keyevent 22; sleep 1.4; shot 08d_rap5
adb shell input keyevent 4; sleep 1.5
adb shell input keyevent 19; sleep 0.8; adb shell input keyevent 19; sleep 1.0; shot 09_top
adb shell input keyevent 20; sleep 1.0
sleep 1; adb pull /sdcard/intro.mp4 shots/intro.mp4
# Aufzieh-Effekt beim Öffnen einer App
adb shell input keyevent 21; adb shell input keyevent 21; adb shell input keyevent 21; adb shell input keyevent 21; sleep 1.5
(adb shell screenrecord --time-limit 5 --size 960x540 /sdcard/launch.mp4 &) ; sleep 0.6
adb shell input keyevent 23
sleep 5.5; adb pull /sdcard/launch.mp4 shots/launch.mp4
adb shell am start -n at.domi.tvlauncher/.MainActivity >/dev/null; sleep 2
# Eigener Player: Ken Carson
adb shell am start -n at.domi.tvlauncher/.PlayerActivity --ei rapper 0; sleep 25; shot 10_player
adb shell input keyevent 22; sleep 15; shot 11_player_next
adb shell input keyevent 20; sleep 3; shot 12_player_audio
adb shell input keyevent 4; sleep 2
adb shell dumpsys gfxinfo at.domi.tvlauncher > shots/gfxinfo.txt
adb logcat -d -s AndroidRuntime:E DomiTV:* > shots/logcat.txt
adb logcat -d | grep -iE "FATAL|Exception|DomiTV" | head -100 > shots/errors.txt
true

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
adb shell input keyevent 20; sleep 1.5; shot 08h_alb
adb shell input keyevent 22; adb shell input keyevent 22; adb shell input keyevent 22; adb shell input keyevent 22; adb shell input keyevent 22; sleep 1.5; shot 08i_alb5
adb shell input keyevent 20; sleep 1.5; shot 08j_sng
for i in 1 2 3 4 5 6 7 8; do adb shell input keyevent 22; done; sleep 1.6; shot 08k_sng8
adb shell input keyevent 20; sleep 1.5; shot 08e_rap2
adb shell input keyevent 21; sleep 1.0; shot 08f_rap2b
adb shell input keyevent 20; sleep 1.5; shot 08m_mood
adb shell input keyevent 22; sleep 1.0; shot 08n_mood2
adb shell input keyevent 19; sleep 1.2
adb shell input keyevent 19; sleep 1.2; shot 08g_back_sng
adb shell input keyevent 4; sleep 1.5
adb shell input keyevent 19; sleep 0.8; adb shell input keyevent 19; sleep 1.0; shot 09_top
adb shell input keyevent 22; sleep 0.6; adb shell input keyevent 23; sleep 0.5; shot 09b_party
adb shell input keyevent 23; sleep 1.0; adb shell input keyevent 21; sleep 0.5
adb shell input keyevent 20; sleep 1.0
# Hype-Intro (Ken Carson): Startanimation + Schläge abfotografieren
adb shell input keyevent 20; sleep 1.2; adb shell input keyevent 20; sleep 1.5
for i in 1 2 3 4 5 6 7 8; do adb shell input keyevent 21; done; sleep 1.5
adb shell input keyevent 23; sleep 1.05; shot 30_hype1
sleep 0.35; shot 31_hype2
sleep 0.45; shot 32_hype3
sleep 3; adb shell input keyevent 4; sleep 2
sleep 1; adb pull /sdcard/intro.mp4 shots/intro.mp4
# Aufzieh-Effekt beim Öffnen einer App
adb shell input keyevent 21; adb shell input keyevent 21; adb shell input keyevent 21; adb shell input keyevent 21; sleep 1.5
(adb shell screenrecord --time-limit 5 --size 960x540 /sdcard/launch.mp4 &) ; sleep 0.6
adb shell input keyevent 23
sleep 5.5; adb pull /sdcard/launch.mp4 shots/launch.mp4
adb shell am start -n at.domi.tvlauncher/.MainActivity >/dev/null; sleep 2
# Songtext-Anzeige testen (Dienst einschalten, Test-Song zeigen)
adb shell settings put secure enabled_accessibility_services at.domi.tvlauncher/at.domi.tvlauncher.HomeWatcherService
adb shell settings put secure accessibility_enabled 1
sleep 4
adb shell am broadcast -a at.domi.tvlauncher.TEST_OVERLAY --ei rapper 0 --es title "'Ken Carson - overseas (Official Music Video)'" --ei at 35000
sleep 8; shot 20_lyrics_a
sleep 6; shot 20_lyrics_b
adb shell am broadcast -a at.domi.tvlauncher.TEST_OVERLAY --ei rapper 1 --es title "'bleood - friend or foe [music video]'" --ei at 50000
sleep 8; shot 21_lyrics_bleood
adb shell am broadcast -a at.domi.tvlauncher.TEST_OVERLAY --ei rapper 4 --es title "'Nettspend - stressed [Official Video]'" --ei at 20000
sleep 8; shot 22_lyrics_plain
adb shell am broadcast -a at.domi.tvlauncher.TEST_OVERLAY --es title wait; sleep 1.2; shot 25_wait
adb shell am broadcast -a at.domi.tvlauncher.TEST_OVERLAY --es title nowait; sleep 1.2; shot 26_nowait
adb shell input keyevent 82; sleep 1; shot 23_mode_audio
adb shell input keyevent 82; sleep 1.5; shot 24_mode_video
adb shell input keyevent 82; sleep 1
adb shell am broadcast -a at.domi.tvlauncher.TEST_OVERLAY --es title hide
sleep 1
# Song-Quiz
adb shell am start -n at.domi.tvlauncher/.MainActivity --ez fx_test true --ez fx_quiz true
sleep 9; shot 40_quiz_a
adb shell input keyevent 22; sleep 0.6; adb shell input keyevent 23; sleep 0.8; shot 41_quiz_answer
sleep 2.5; shot 42_quiz_next
for r in 2 3 4 5 6 7 8 9 10; do adb shell input keyevent 23; sleep 3.2; done; sleep 3; shot 44_quiz_end
adb shell input keyevent 4; sleep 1.5; shot 43_quiz_closed
adb shell dumpsys gfxinfo at.domi.tvlauncher > shots/gfxinfo.txt
adb logcat -d -s AndroidRuntime:E DomiTV:* > shots/logcat.txt
adb logcat -d | grep -iE "FATAL|Exception|DomiTV" | head -100 > shots/errors.txt
true

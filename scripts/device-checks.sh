#!/usr/bin/env bash
# Runs on the CI emulator. Fails on any test failure or any crash/ANR from the app.
set -uo pipefail
apks="${1:-apks}"
out=device-results
mkdir -p "$out/screenshots"
pkg=com.hartmannstudios.psalmwell
status=0

adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
# The emulator's own launcher sometimes raises a "not responding" dialog right after boot; keep system dialogs out of the run.
adb shell settings put global hide_error_dialogs 1 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true

# 1) Instrumented guest journey on the debug build (also writes store screenshots)
adb install -r "$apks/debug/app-debug.apk"
adb install -r "$apks/androidTest/debug/app-debug-androidTest.apk"
adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am instrument -w -r $pkg.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation.txt"
grep -q "OK (" "$out/instrumentation.txt" || { echo "::error::instrumented tests failed"; status=1; }
adb pull /sdcard/Android/data/$pkg/files/screenshots "$out/screenshots" || true
adb logcat -d > "$out/logcat-instrumented.txt"

# 2) Shrunk (R8) build: cold start, then 5000 random events
adb uninstall $pkg.test || true
adb uninstall $pkg || true
adb install -r "$apks/qa/app-qa.apk"
adb logcat -c
adb shell am start -W -n $pkg/.MainActivity | tee "$out/start.txt"
sleep 8
adb shell monkey -p $pkg --pct-syskeys 0 --throttle 120 -s 20261030 -v 5000 > "$out/monkey.txt" 2>&1
tail -5 "$out/monkey.txt"
adb shell am force-stop $pkg
adb shell am start -W -n $pkg/.MainActivity >> "$out/start.txt"
sleep 6
adb logcat -d > "$out/logcat-qa.txt"
if grep -E "FATAL EXCEPTION|ANR in $pkg|CRASH: $pkg|NOT RESPONDING: $pkg" "$out/logcat-qa.txt" "$out/monkey.txt"; then
  echo "::error::crash or ANR in the shrunk build"; status=1
fi
grep -q "Monkey finished" "$out/monkey.txt" || { echo "::error::monkey did not finish"; status=1; }
adb exec-out screencap -p > "$out/qa-after-monkey.png" || true
exit $status

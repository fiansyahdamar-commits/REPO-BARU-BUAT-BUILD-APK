#!/usr/bin/env bash
set -Eeuo pipefail

mkdir -p .
adb wait-for-device

BOOT=""
for attempt in $(seq 1 30); do
  BOOT="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$BOOT" == "1" ]]; then
    break
  fi
  sleep 5
done

if [[ "$BOOT" != "1" ]]; then
  echo "FAIL: emulator did not finish booting" > startup-result.txt
  adb devices -l > startup-logcat.txt 2>&1 || true
  exit 1
fi

# Android can report boot complete before Package Manager is ready.
PACKAGE_MANAGER_READY=0
for attempt in $(seq 1 12); do
  if adb shell cmd package list packages >/dev/null 2>&1; then
    PACKAGE_MANAGER_READY=1
    break
  fi
  adb reconnect || true
  sleep 5
done

if [[ "$PACKAGE_MANAGER_READY" -ne 1 ]]; then
  echo "FAIL: Android Package Manager did not become ready" > startup-result.txt
  adb logcat -d -t 600 > startup-logcat.txt 2>&1 || true
  exit 1
fi

# Retry transient emulator Package Manager / ADB pipe failures.
INSTALLED=0
for attempt in $(seq 1 5); do
  if adb install -r app/build/outputs/apk/debug/app-debug.apk; then
    INSTALLED=1
    break
  fi
  adb reconnect || true
  sleep 8
done

if [[ "$INSTALLED" -ne 1 ]]; then
  echo "FAIL: APK installation failed after 5 attempts" > startup-result.txt
  {
    adb devices -l || true
    adb logcat -d -t 600 || true
  } > startup-logcat.txt 2>&1
  exit 1
fi

adb shell am force-stop com.zallrw.admin
adb shell am start -n com.zallrw.admin/.MainActivity
sleep 25
PID="$(adb shell pidof com.zallrw.admin | tr -d '\r' || true)"
adb logcat -d -t 600 > startup-logcat.txt 2>&1 || true

if [[ -n "$PID" ]]; then
  echo "PASS: com.zallrw.admin survived 25 seconds after launch (pid=$PID)" > startup-result.txt
else
  echo "FAIL: com.zallrw.admin process was not alive 25 seconds after launch" > startup-result.txt
  exit 1
fi

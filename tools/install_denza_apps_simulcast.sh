#!/usr/bin/env bash
set -euo pipefail

# Installs Denza Apps (Simulcast accessibility overlay) on the car.
# The old Simulcast alias APKs are no longer required; the accessibility
# overlay replaced them (see research/simulcast-aliases/). Only this APK,
# overlay permission, and enabling the accessibility service are needed.

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
serial="${ADB_SERIAL:-127.0.0.1:5555}"
adb_cmd=(adb -s "$serial")

state="$("${adb_cmd[@]}" get-state 2>/dev/null || true)"
if [[ "$state" != "device" ]]; then
  echo "ADB device is not ready on $serial; current state: ${state:-missing}" >&2
  exit 2
fi

apps_apk="$repo_root/denza-apps/build/outputs/apk/debug/denza-apps.apk"
if [[ ! -f "$apps_apk" ]]; then
  echo "Missing $apps_apk. Build with ./gradlew :denza-apps:assembleDebug first." >&2
  exit 3
fi

"${adb_cmd[@]}" install -r "$apps_apk"

"${adb_cmd[@]}" shell appops set dev.denza.apps SYSTEM_ALERT_WINDOW allow
"${adb_cmd[@]}" shell am start -n dev.denza.apps/.MainActivity >/dev/null

echo "Installed Denza Apps on $serial."
echo "Overlay app-op:"
"${adb_cmd[@]}" shell appops get dev.denza.apps SYSTEM_ALERT_WINDOW || true
echo "Next: enable the accessibility service for Denza Apps in Android settings."

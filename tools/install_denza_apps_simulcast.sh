#!/usr/bin/env bash
set -euo pipefail

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

shopt -s nullglob
alias_apks=("$repo_root"/simulcast-aliases/launcher/build/outputs/apk/*/debug/simulcast-alias-*.apk)
if (( ${#alias_apks[@]} == 0 )); then
  echo "No alias APKs found. Build with ./gradlew :simulcast-aliases:launcher:assembleDebug first." >&2
  exit 4
fi

for apk in "${alias_apks[@]}"; do
  "${adb_cmd[@]}" install -r "$apk"
done

"${adb_cmd[@]}" shell appops set dev.denza.apps SYSTEM_ALERT_WINDOW allow
"${adb_cmd[@]}" shell am start -n dev.denza.apps/.MainActivity >/dev/null

echo "Installed Denza Apps and ${#alias_apks[@]} Simulcast alias APKs on $serial."
echo "Overlay app-op:"
"${adb_cmd[@]}" shell appops get dev.denza.apps SYSTEM_ALERT_WINDOW || true

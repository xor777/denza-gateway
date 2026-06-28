#!/usr/bin/env bash
set -euo pipefail

serial="${ADB_SERIAL:-127.0.0.1:5555}"
target_package="${1:-com.vk.vkvideo}"
receiver="${2:-screen_hud}"
video_width="${3:-}"
video_height="${4:-}"

adb_cmd=(adb -s "$serial")

state="$("${adb_cmd[@]}" get-state 2>/dev/null || true)"
if [[ "$state" != "device" ]]; then
  echo "ADB device is not ready on $serial; current state: ${state:-missing}" >&2
  exit 2
fi

"${adb_cmd[@]}" logcat -c
start_cmd=(
  shell am broadcast
  -a dev.denza.apps.START_SIMULCAST_TARGET
  -p dev.denza.apps
  --es targetPackage "$target_package"
  --es receiver "$receiver"
)
if [[ -n "$video_width" && -n "$video_height" ]]; then
  start_cmd+=(--ei videoWidth "$video_width" --ei videoHeight "$video_height")
fi
"${adb_cmd[@]}" "${start_cmd[@]}"

sleep 2

echo "=== DiShare displays ==="
"${adb_cmd[@]}" shell dumpsys display \
  | rg -n "BYD-Mirror|com\\.byd\\.dishare|${target_package}|Display [0-9]|mPrimaryDisplayDevice|DisplayInfo" -C 2 || true

echo "=== DiShare windows ==="
"${adb_cmd[@]}" shell dumpsys window displays \
  | rg -n "${target_package}|BYD-Mirror|mDisplayId=|Display:|Task\\{|ActivityRecord" -C 2 || true

echo "=== Denza logs ==="
"${adb_cmd[@]}" logcat -d -t 160 -s DenzaSimulcastOverlay DenzaDiShareBridge

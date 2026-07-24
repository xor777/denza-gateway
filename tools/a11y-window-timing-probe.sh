#!/usr/bin/env bash
# Measures how quickly an accessibility client learns about stock camera
# windows versus the WindowManager addWindow/removeWindow truth.
#
# Motivation (2026-07-24): fast left-to-right mirror switching crashes stock
# com.byd.avc ~95-174 ms after its right alert window is added, while our
# dumpsys polling detects the flip only after 110-600 ms. This probe checks
# whether an accessibility window-push trigger could fire early enough for an
# emergency AVC surface release. First live run measured +30/+35 ms for the
# right alert window and +71 ms for the left PIP activity (uiautomator client,
# no notification throttle; the product service must set notificationTimeout=0
# to match).
#
# Usage:
#   tools/a11y-window-timing-probe.sh [serial] [duration-seconds]
# While it runs, flip turn signals in ISOLATED cycles only (a side on for ~3 s,
# off, 5 s pause). Never fast left-to-right: that is the known vendor crash
# trigger. The report prints, per stock window add/remove, the delay until the
# first accessibility event.
#
# Note: `uiautomator events` may temporarily suppress other accessibility
# services on some builds; on this firmware the Denza service stayed enabled,
# but re-check `dumpsys accessibility` after long runs.

set -euo pipefail

SERIAL="${1:-127.0.0.1:5555}"
DURATION="${2:-60}"
OUT="$(mktemp -d /tmp/a11y-timing-probe.XXXXXX)"

echo "capturing ${DURATION}s to ${OUT} (flip signals now, isolated cycles only)"

adb -s "$SERIAL" shell uiautomator events 2>/dev/null \
  | grep --line-buffered -E 'TYPE_WINDOWS_CHANGED|TYPE_WINDOW_STATE_CHANGED' \
  > "$OUT/a11y.log" &
A11Y_PID=$!

adb -s "$SERIAL" logcat -v time 2>/dev/null \
  | grep --line-buffered -E 'addWindow window:Window\{[^}]+ u0 com\.byd\.avc|removeWindow win:Window\{[^}]+ u0 com\.byd\.avc' \
  > "$OUT/wm.log" &
WM_PID=$!

sleep "$DURATION"
kill "$A11Y_PID" "$WM_PID" 2>/dev/null || true
wait 2>/dev/null || true

awk '
function toms(ts,   a, b) {
  split(ts, a, ":"); split(a[3], b, ".")
  return (a[1] * 3600 + a[2] * 60 + b[1]) * 1000 + b[2]
}
FNR == NR {
  if (/addWindow|removeWindow/) { wm[++n] = toms($2); label[n] = ($0 ~ /addWindow/ ? "add" : "remove") " " $2 }
  next
}
{
  t = toms($2)
  for (i = 1; i <= n; i++) {
    d = t - wm[i]
    if (d >= -50 && d <= 400 && !(i in hit)) {
      hit[i] = 1
      printf "%-22s first a11y event %+4d ms\n", label[i], d
    }
  }
}
END { if (n == 0) print "no stock camera windows captured - flip a signal during the run" }
' "$OUT/wm.log" "$OUT/a11y.log"

echo "raw logs kept in ${OUT}"

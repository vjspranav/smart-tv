#!/bin/sh
# Runs one perf scene on the TV and prints exactly one JSON result line on stdout.
# Usage: tools/perf-run.sh <label> <component> <keysfile> [extra am start args...]
# keysfile lines: "KEYCODE_X <delay ms>" ('#' comments allowed). Keys are replayed by one monkey process.
# Exit codes: 1 usage/bad keys, 2 TV unreachable, 3 TV not awake, 4 parse failure, 5 app not in front.
set -e
[ $# -ge 3 ] || { echo "usage: perf-run.sh <label> <component> <keysfile> [am start args...]" >&2; exit 1; }
here="$(cd "$(dirname "$0")" && pwd)"
adbtv="$here/tv-adb"
label="$1"; component="$2"; keys="$3"; shift 3
pkg="${component%%/*}"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

awake() {
  power="$("$adbtv" shell dumpsys power)" || exit 2
  case "$power" in *mWakefulness=Awake*) ;; *) echo "perf-run: TV not awake" >&2; exit 3 ;; esac
}

python3 "$here/keys_to_monkey.py" "$keys" > "$tmp/keys.monkey" || exit 1
awake
"$adbtv" push "$tmp/keys.monkey" /data/local/tmp/keys.monkey >/dev/null
"$adbtv" shell am start -W -S -n "$component" "$@" >/dev/null
sleep 3                                   # let the scene settle; launch frames must not count
focus="$("$adbtv" shell dumpsys window | grep -m1 mCurrentFocus)" || exit 2
case "$focus" in *"$pkg"*) ;; *) echo "perf-run: app not in front ($focus)" >&2; exit 5 ;; esac
"$adbtv" shell dumpsys gfxinfo "$pkg" reset >/dev/null

start=$(date +%s)
"$adbtv" shell monkey -f /data/local/tmp/keys.monkey 1 >/dev/null 2>&1
sleep 1                                   # let the last animation finish
elapsed=$(( $(date +%s) - start ))
awake                                     # a dimmed or dreaming TV invalidates the run

"$adbtv" shell dumpsys gfxinfo "$pkg" > "$tmp/gfx.txt"
"$adbtv" shell dumpsys meminfo "$pkg" > "$tmp/mem.txt"
python3 "$here/gfxinfo_parse.py" "$tmp/gfx.txt" "$tmp/mem.txt" "$label" "$elapsed" || exit 4

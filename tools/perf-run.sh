#!/bin/sh
# Runs one perf scene on the TV and prints exactly one JSON result line on stdout.
# Usage: tools/perf-run.sh <label> <component> <keysfile> [extra am start args...]
# keysfile lines: "KEYCODE_X <delay ms>". Exit codes: 2 TV unreachable, 3 TV not awake, 4 parse failure.
set -e
here="$(cd "$(dirname "$0")" && pwd)"
adbtv="$here/tv-adb"
label="$1"; component="$2"; keys="$3"; shift 3
pkg="${component%%/*}"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

power="$("$adbtv" shell dumpsys power)" || exit 2
case "$power" in *mWakefulness=Awake*) ;; *) echo "perf-run: TV not awake" >&2; exit 3 ;; esac

"$adbtv" shell am start -W -S -n "$component" "$@" >/dev/null
sleep 3                                   # let the scene settle; launch frames must not count
"$adbtv" shell dumpsys gfxinfo "$pkg" reset >/dev/null

# Replay keys as one on-device script (one adb round-trip instead of one per key)
script=$(awk 'NF >= 1 { printf "input keyevent %s; sleep %.3f; ", $1, ($2 == "" ? 250 : $2) / 1000 }' "$keys")
"$adbtv" shell "$script" >/dev/null
sleep 1                                   # let the last animation finish

"$adbtv" shell dumpsys gfxinfo "$pkg" > "$tmp/gfx.txt"
"$adbtv" shell dumpsys meminfo "$pkg" > "$tmp/mem.txt"
python3 "$here/gfxinfo_parse.py" "$tmp/gfx.txt" "$tmp/mem.txt" "$label" || exit 4

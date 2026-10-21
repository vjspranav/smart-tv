#!/bin/sh
# Opens launcher screens on the TV with check=1, collects FlamingoOverflow lines and screenshots.
# Usage: tools/overflow-check.sh [kittest | stress]
#   LAYOUTS="A" limits the layouts. no arg  → every layout (A G N) × screen (home everything settings) × focus (first last), normal data
#   stress  → the same with stress data (long names, 40 apps, missing art)
#   kittest → the UI kit's planted-problem screen only (expects exactly 4 OVERFLOW lines)
# Exit 0 only if no OVERFLOW lines (kittest: exactly 4). Screenshots: investigations/overflow/<case>.png
here="$(cd "$(dirname "$0")" && pwd)"; root="$(dirname "$here")"
A="$here/tv-adb"; C=com.livingroom.flamingo.launcher/.Home
shots="$root/investigations/overflow"; mkdir -p "$shots"
bad=0; total=0

run_case() {  # name, am extras...
  name="$1"; shift
  "$A" shell logcat -c
  "$A" shell am start -W -S -n $C --ez check true "$@" >/dev/null || return 2
  i=0; done=""
  while [ $i -lt 20 ] && [ -z "$done" ]; do
    sleep 1; i=$((i + 1))
    done="$("$A" shell "logcat -d -s FlamingoOverflow:I" | grep -m1 'DONE')"
  done
  [ -n "$done" ] || { echo "$name: no result (checker did not run)"; bad=$((bad + 1)); return; }
  lines="$("$A" shell "logcat -d -s FlamingoOverflow:W" | grep 'OVERFLOW' | sed 's/^.*FlamingoOverflow: //')"
  n=$(printf '%s' "$lines" | grep -c OVERFLOW)
  "$A" exec-out screencap -p > "$shots/$name.png"
  total=$((total + n))
  echo "$name: $n"
  [ "$n" -gt 0 ] && printf '%s\n' "$lines" | sed 's/^/    /'
}

if [ "$1" = kittest ]; then
  run_case kittest --ez kittest true
  [ "$total" -eq 4 ] && { echo "kittest OK (4 planted problems found, clean copies passed)"; exit 0; }
  echo "kittest FAILED: expected 4, got $total"; exit 1
fi

stress=false; suffix=""
[ "$1" = stress ] && { stress=true; suffix="-stress"; }
for layout in ${LAYOUTS:-A G N}; do
  for screen in home everything allapps settings; do
    for focus in first last; do
      run_case "$layout-$screen-$focus$suffix" --es layout $layout --es screen $screen --es focus $focus --ez stress $stress
    done
  done
done
[ "$total" -eq 0 ] && [ "$bad" -eq 0 ] && { echo "OVERFLOW CHECK PASSED"; exit 0; }
echo "OVERFLOW CHECK FAILED: $total problems, $bad cases without result"; exit 1

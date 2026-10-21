#!/bin/sh
# Diagnostic only: repeats the takeover steps while recording Android's Home state at each moment, then undoes.
here="$(cd "$(dirname "$0")" && pwd)"; A="$here/tv-adb"
F=com.livingroom.flamingo.launcher; GTV=com.google.android.apps.tv.launcherx; WRAITH=com.google.android.tungsten.setupwraith
trap '"$A" shell pm enable "$WRAITH" >/dev/null 2>&1; "$here/undo-home.sh"' EXIT
snap() {
  echo "== $1"
  echo "  role:     $("$A" shell dumpsys role | grep -A1 'name=android.app.role.HOME' | grep holders | tr -s ' ')"
  echo "  resolves: $("$A" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
  echo "  on screen:$("$A" shell dumpsys activity activities | grep -m1 mResumedActivity | sed 's/.*{[^ ]* [^ ]* //; s/ t[0-9]*}//')"
}
snap "before"
"$A" shell pm disable-user "$GTV" >/dev/null; sleep 2; snap "launcherx off"
"$A" shell pm disable-user "$WRAITH" >/dev/null; sleep 2; snap "setupwraith off too"
echo "  role add: $("$A" shell cmd role add-role-holder android.app.role.HOME "$F" 2>&1 | head -1)"; sleep 2; snap "role given to Flamingo"
"$A" shell input keyevent KEYCODE_HOME; sleep 3; snap "Home pressed"
"$A" shell pm enable "$WRAITH" >/dev/null; sleep 3; snap "setupwraith back on (3 s)"
sleep 15; snap "setupwraith back on (18 s)"
"$A" shell input keyevent KEYCODE_HOME; sleep 3; snap "Home pressed again"
echo "  wifi: $("$A" shell cmd wifi status | grep -m1 connected)"

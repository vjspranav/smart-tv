#!/bin/sh
# Gives the Home button back to Google TV: re-enables its launcher (launcherx) and the Setup Wizard (setupwraith),
# and makes launcherx the Home app again (a saved Home choice survives re-enabling, so it must be moved back too).
# Safe to run any time, any number of times.
here="$(cd "$(dirname "$0")" && pwd)"; A="$here/tv-adb"
GTV=com.google.android.apps.tv.launcherx; WRAITH=com.google.android.tungsten.setupwraith
"$A" shell pm enable "$WRAITH"
"$A" shell pm enable "$GTV"
"$A" shell cmd role add-role-holder android.app.role.HOME "$GTV"
"$A" shell cmd package set-home-activity "$GTV/.home.HomeActivity" >/dev/null 2>&1
"$A" shell input keyevent KEYCODE_HOME; sleep 3
if "$A" shell dumpsys activity activities | grep -m1 mResumedActivity | grep -q "$GTV"; then
  echo "undo-home: PASS (Home opens Google TV)"
else
  echo "undo-home: FAIL — Home did not open Google TV:"; "$A" shell dumpsys activity activities | grep -m1 mResumedActivity; exit 1
fi

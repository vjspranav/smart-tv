#!/bin/sh
# Makes Flamingo the TV's Home app: the owner's "safer XDA" method (decided 2026-10-20, after the Wi-Fi RCA).
#   Google TV's launcher (launcherx) is disabled and stays off (frees its memory).
#   The Setup Wizard (setupwraith) is off only for the seconds it takes to press Home, then re-enabled — always,
#   even if this script fails or is interrupted. (Its first-setup Wi-Fi entry was deleted with it on 2026-10-20;
#   the Wi-Fi is now the owner's own entry, checked before and after.)
# Run only with the owner present and their Wi-Fi password at hand. Undo: tools/undo-home.sh.
here="$(cd "$(dirname "$0")" && pwd)"; A="$here/tv-adb"
F=com.livingroom.flamingo.launcher; GTV=com.google.android.apps.tv.launcherx
WRAITH=com.google.android.tungsten.setupwraith; WIFI="John Doe 5G"

# Preflight: nothing is changed unless all pass
"$A" shell pm path "$F" | grep -q package: || { echo "become-home: Flamingo is not installed; nothing changed"; exit 1; }
"$A" shell cmd wifi list-networks | grep -q "$WIFI" || { echo "become-home: '$WIFI' is not saved; nothing changed"; exit 1; }
"$A" shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep -q "$F/.Home" \
  || { echo "become-home: Flamingo is not a Home candidate; nothing changed"; exit 1; }

# Watcher: if the TV stops answering for 60 s, keep trying the undo every 10 s for 30 minutes
(
  down=0; t=0
  while [ $t -lt 1800 ]; do
    [ -f /tmp/.flamingo-home-done ] && exit 0
    if "$A" shell true >/dev/null 2>&1; then down=0; else down=$((down + 10)); fi
    [ $down -ge 60 ] && "$here/undo-home.sh" && exit 0
    sleep 10; t=$((t + 10))
  done
) &
watcher=$!
rm -f /tmp/.flamingo-home-done
finish() { touch /tmp/.flamingo-home-done; kill $watcher 2>/dev/null; }
# The Setup Wizard comes back on whatever happens
trap '"$A" shell pm enable "$WRAITH" >/dev/null 2>&1; finish' EXIT
trap 'exit 130' INT TERM
fail() { echo "become-home: FAIL — $1; undoing"; "$here/undo-home.sh"; exit 1; }

# Takeover: seconds only
"$A" shell pm disable-user "$GTV" || fail "could not turn off Google TV's launcher"
"$A" shell pm disable-user "$WRAITH" || fail "could not turn off the Setup Wizard"
# Hand Flamingo the HOME role while it's the only real candidate: otherwise re-enabling setupwraith (priority 1)
# takes Home back before any choice is saved (seen 2026-10-21)
"$A" shell cmd role add-role-holder android.app.role.HOME "$F" || echo "become-home: note — role assignment refused; relying on the Home press"
"$A" shell cmd package set-home-activity "$F/.Home" >/dev/null 2>&1
"$A" shell input keyevent KEYCODE_HOME; sleep 3
"$A" shell pm enable "$WRAITH" || echo "become-home: WARNING — re-enabling the Setup Wizard failed; re-enable it in Settings › Apps"
# Verify what the Home button really does. (Not `cmd package resolve-activity`: it ignores the HOME role and
# reports setupwraith's RecoveryActivity by priority, while the Home key follows the role — seen 2026-10-21.)
"$A" shell dumpsys role | grep -A1 'name=android.app.role.HOME' | grep -q "holders=$F" || fail "Flamingo does not hold the HOME role"
for i in 1 2 3; do
  "$A" shell am start -n com.android.tv.settings/.MainSettings >/dev/null 2>&1; sleep 3
  "$A" shell input keyevent KEYCODE_HOME; sleep 3
  "$A" shell dumpsys activity activities | grep -m1 mResumedActivity | grep -q "$F/.Home" || fail "Home press $i (from Settings) did not open Flamingo"
done
"$A" shell pm list packages -e | grep -q "$WRAITH" || fail "the Setup Wizard is not enabled"
"$A" shell cmd wifi list-networks | grep -q "$WIFI" || fail "'$WIFI' disappeared"
"$A" shell cmd wifi status | grep -q "$WIFI" || fail "not connected to '$WIFI'"
echo "become-home: PASS — Home opens Flamingo; Setup Wizard on; '$WIFI' saved and connected; Google TV's launcher off. Undo: tools/undo-home.sh"

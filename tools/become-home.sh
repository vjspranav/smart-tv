#!/bin/sh
# Makes Flamingo the TV's Home app: the XDA method (owner's decision, 2026-10-21).
#   Google TV's launcher (launcherx) and the Setup Wizard (setupwraith) are both disabled and stay off.
#   setupwraith must stay off: its RecoveryActivity is a direct-boot-aware HOME (priority 1), so with it enabled
#   every boot lands on its black recovery screen instead of Flamingo (seen 2026-10-21). With it off, boot uses
#   Settings' FallbackHome, which hands over to Flamingo once the TV unlocks.
#   Wi-Fi: on 2026-10-20 disabling setupwraith deleted the Wizard-created network; the owner's own "John Doe 5G"
#   entry survived three setupwraith toggles on 2026-10-21. It is checked before and after.
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
trap finish EXIT
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
# Verify what the Home button really does. (Not `cmd package resolve-activity`: it ignores the HOME role and
# reports setupwraith's RecoveryActivity by priority, while the Home key follows the role — seen 2026-10-21.)
"$A" shell dumpsys role | grep -A1 'name=android.app.role.HOME' | grep -q "holders=$F" || fail "Flamingo does not hold the HOME role"
for i in 1 2 3; do
  "$A" shell am start -n com.android.tv.settings/.MainSettings >/dev/null 2>&1; sleep 3
  "$A" shell input keyevent KEYCODE_HOME; sleep 3
  "$A" shell dumpsys activity activities | grep -m1 mResumedActivity | grep -q "$F/.Home" || fail "Home press $i (from Settings) did not open Flamingo"
done
"$A" shell pm list packages -d | grep -q "$WRAITH" || fail "the Setup Wizard is not off"
"$A" shell cmd wifi list-networks | grep -q "$WIFI" || fail "'$WIFI' disappeared"
"$A" shell cmd wifi status | grep -q "$WIFI" || fail "not connected to '$WIFI'"
echo "become-home: PASS — Home opens Flamingo; '$WIFI' saved and connected; Google TV's launcher and Setup Wizard off. Undo: tools/undo-home.sh"

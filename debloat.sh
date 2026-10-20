#!/bin/sh
# Turns off ads, tracking, demo modes and unused apps on the Living Room TV.
# Everything is disabled (not uninstalled), so it is reversible:
#   ./debloat.sh            disable the list
#   ./debloat.sh restore    re-enable the list
# Runs from the Mac (adb via Homebrew). The TV only trusts the Pi's key, so start adb with it:
#   adb kill-server; ADB_VENDOR_KEYS=$HOME/.android/pi_adbkey adb start-server
# Uninstalled outright (not reversible here; reinstall from Play Store if wanted):
#   com.overdevs.at4k (AT4K Launcher, 2026-10-21: user found it laggy)
#   com.livingroom.preview (throwaway layout preview spike, 2026-10-20)
TV="${TV:-192.168.0.12:5555}"

PACKAGES="
tv.samba.ssm.ui                     Samba TV viewing tracking (ACR)
com.tcl.waterfall.overseas          TCL Channel (ad-supported channels)
com.tcl.partnercustomizer           TCL Channel
com.tcl.messagebox                  TCL promo messages
com.tcl.esticker                    Store show mode
com.tcl.exhibit                     Store exhibition mode
com.tcl.hotelmenu                   Hotel mode
com.tcl.ocean.instructions          User guide
com.tcl.repairguide                 Diagnosis
com.tcl.logkit                      Log collection
com.google.android.videos           Google Play Movies
com.google.android.play.games       Google Play Games
com.google.android.feedback         Market Feedback Agent
com.android.printspooler            Printing
tv.airtel.xstream.tvapp             Airtel Xstream (user asked to turn off)
com.sonyliv                         SonyLIV (user asked to turn off)
com.suntv.sunnxt                    Sun NXT (user asked to turn off)
com.tcl.guard                       TCL Safety Guard (~12 MB, 2026-10-21)
com.tcl.ttvs                        TCL TCSCore cloud service (~41 MB, 2026-10-21)
"

adb connect "$TV" >/dev/null
action="disable-user --user 0"
[ "$1" = restore ] && action="enable --user 0"
echo "$PACKAGES" | while read -r pkg desc; do
  [ -z "$pkg" ] && continue
  printf '%-32s %-40s ' "$pkg" "$desc"
  adb -s "$TV" shell pm $action "$pkg" </dev/null 2>&1 | tr -d '\r' | tail -1
done

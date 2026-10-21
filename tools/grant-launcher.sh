#!/bin/sh
# One-time grants the Flamingo launcher needs on the TV (re-run after reinstalling from scratch).
A="$(cd "$(dirname "$0")" && pwd)/tv-adb"
P=com.livingroom.flamingo.launcher
set -e
"$A" shell pm grant $P android.permission.READ_TV_LISTINGS
"$A" shell pm grant $P android.permission.ACCESS_FINE_LOCATION   # Wi-Fi network name only
"$A" shell appops set $P GET_USAGE_STATS allow
"$A" shell appops set $P APP_AUTO_START allow     # TCL blocks binding to the listener without it
"$A" shell cmd notification allow_listener $P/$P.data.MediaListener
# Remote buttons (Settings, Netflix, YouTube): add Flamingo's key service next to any already enabled (the Pi bridge's)
S="$P/$P.KeyService"
cur="$("$A" shell settings get secure enabled_accessibility_services | tr -d '\r')"
case "$cur" in
  *"$S"*) ;;
  ""|null) "$A" shell settings put secure enabled_accessibility_services "$S" ;;
  *) "$A" shell settings put secure enabled_accessibility_services "$cur:$S" ;;
esac
"$A" shell settings put secure accessibility_enabled 1
echo "grants applied"

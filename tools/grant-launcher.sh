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
echo "grants applied"

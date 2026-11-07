#!/bin/sh
# Build the TV companion app on this Mac, install it on the TV through the Pi, and re-apply its grants.
# Usage: ./deploy.sh [user@host]
set -e
cd "$(dirname "$0")"
PI="${1:-flamingo@flamingo.local}"
./build.sh
rsync -a --exclude build ./ "$PI:tv-remote/tv-bridge/"
ssh "$PI" 'cd ~/tv-remote
export LD_LIBRARY_PATH=$PWD/runtime/usr/lib/aarch64-linux-gnu/android:$PWD/runtime/usr/lib/aarch64-linux-gnu
A="runtime/usr/bin/adb -s $(cat tv-address.txt 2>/dev/null || echo 192.168.0.34:5555)"
$A install -r tv-bridge/tv-bridge.apk
$A shell cmd notification allow_listener com.livingroom.bridge/com.livingroom.bridge.Listener
$A shell appops set com.livingroom.bridge APP_AUTO_START allow
$A shell appops set com.livingroom.bridge SYSTEM_ALERT_WINDOW allow
$A shell appops set com.livingroom.bridge GET_USAGE_STATS allow
$A shell ime enable com.livingroom.bridge/.Keyboard
$A shell settings put secure enabled_accessibility_services com.livingroom.bridge/com.livingroom.bridge.A11y
$A shell settings put secure accessibility_enabled 1
$A forward tcp:8770 tcp:8770 >/dev/null
sleep 3; curl -s -m 5 http://127.0.0.1:8770/state | head -c 200; echo'

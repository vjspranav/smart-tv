#!/bin/sh
# Copy the remote app to the Pi and restart it. Usage: ./deploy.sh [user@host]
# Never touches the Pi's runtime/, certs, saved shortcuts (remote-config.json) or the TV address file.
set -e
cd "$(dirname "$0")"
PI="${1:-flamingo@flamingo.local}"
rsync -a --exclude tv-bridge --exclude tools --exclude deploy --exclude certs --exclude runtime --exclude devices.json --exclude 'test_*.py' --exclude 'test_*.mjs' --exclude '*.sh' --exclude .gitignore ./ "$PI:tv-remote/"
ssh "$PI" 'systemctl --user restart tv-remote && systemctl --user is-active tv-remote'

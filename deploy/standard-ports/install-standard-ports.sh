#!/bin/sh
set -eu
if [ "$(id -u)" -ne 0 ]; then
  echo 'Run with sudo to enable ports 80 and 443.' >&2
  exit 1
fi
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
for unit in tv-remote-http.socket tv-remote-http.service tv-remote-https.socket tv-remote-https.service; do
  install -o root -g root -m 0644 "$SCRIPT_DIR/$unit" "/etc/systemd/system/$unit"
done
systemctl daemon-reload
systemctl enable --now tv-remote-http.socket tv-remote-https.socket
systemctl --no-pager status tv-remote-http.socket tv-remote-https.socket

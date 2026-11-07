# Living Room TV remote

A phone web app served by the Pi (`~/tv-remote`, user service `tv-remote`) that controls the TCL Google TV at 192.168.0.34.

## How it fits together

    phone  ──HTTP/HTTPS──▶  Pi: server.py (8765 / 8443, also 80/443 via socket proxies)
                              ├─ adb ▶ TV: one open shell for keys, inputs, keyboard; power; dumpsys fallback
                              └─ adb forward tcp:8770 ▶ TV: Remote Bridge app (127.0.0.1:8770 on the TV)

**Remote Bridge** (`tv-bridge/`, package `com.livingroom.bridge`) is a tiny companion app on the TV.
It only listens on the TV's own localhost. It provides:
- full media session: title, artist, duration, live position, artwork, queue; play/pause/seek/skip/queue
- launchable apps with TV banners and last-used times; launching apps, links and Google TV search
- recent TV notifications
- an invisible keyboard (`.Keyboard`) that the Pi switches to only while someone types from the phone,
  then switches back to TCL's keyboard (watchdog restores it after 90 s of inactivity)

TV-side grants it needs (already applied; re-run after a factory reset):

    adb shell cmd notification allow_listener com.livingroom.bridge/com.livingroom.bridge.Listener
    adb shell appops set com.livingroom.bridge APP_AUTO_START allow       # TCL blocks binding without it
    adb shell appops set com.livingroom.bridge SYSTEM_ALERT_WINDOW allow  # lets it open apps/links
    adb shell appops set com.livingroom.bridge GET_USAGE_STATS allow      # "recently used" apps
    adb shell ime enable com.livingroom.bridge/.Keyboard
    adb shell settings put secure enabled_accessibility_services com.livingroom.bridge/com.livingroom.bridge.A11y
    adb shell settings put secure accessibility_enabled 1   # player buttons (captions etc.)

Rebuild and install from the Mac with `tv-bridge/deploy.sh` (build only: `tv-bridge/build.sh`) (needs `brew install openjdk@17` and Android build-tools 34 + platform 34).
Keep `tv-bridge/bridge.keystore`: updates must be signed with the same key.

## Files

- `server.py`: API, polling, power, media, apps, keyboard sessions, routines, sleep timer, share target
- `state_parser.py`: parses `dumpsys` output (fallback when the bridge is unavailable)
- `index.html`, `app.css`, `app.js`: the remote (Now playing / Remote / Apps pages, TV controls and remote settings sheets)
- `setup.html`, `setup.js`: install guide (Android installs from https://192.168.0.48)
- `remote-config.json`: shortcuts and routines (created on first save; shared by every phone)
- `icon-cache/`: TV app banners cached for a week

## Deploying from the Mac

    ./deploy.sh              # app files to ~/tv-remote, restart the service
    tv-bridge/deploy.sh      # build + install the TV companion app, re-apply grants
    cd tools/qa && npm i playwright && node layouts.js   # visual/layout checks (stubbed TV)

Service units live in `deploy/` (copy to `~/.config/systemd/user/` on the Pi); `deploy/standard-ports/` holds the
root-owned port 80/443 proxies (`sudo sh install-standard-ports.sh`).

The TV's address is discovered automatically (Chromecast name "Living Room TV", then MAC 14:14:16:24:ac:66)
and saved on the Pi in `tv-address.txt`.

## Player buttons (accessibility)

The bridge's accessibility service (`A11y.java`) can read the foreground app's on-screen elements
(`/a11y/dump`) and press one by id or label (`/a11y/press`). Findings so far:

- YouTube: draws its own player (no readable elements). The subtitles key (KEYCODE_CAPTIONS, 175) opens
  its captions panel. Quality is not reachable.
- Hotstar: not mapped yet.

## Operations

    systemctl --user status tv-remote tv-remote-mdns
    systemctl --user restart tv-remote
    journalctl --user -u tv-remote -n 50 --no-pager
    curl -s http://127.0.0.1:8765/api/context

Backup taken before the 2026-10-19 rebuild: `~/tv-remote-backup-2026-10-19.tgz`.

## Load on the TV and speed (changed 2026-10-21)

- **Live updates.** Phones subscribe to `/api/events` (server-sent events): the full context once, then again only
  when something visible changes (volume, power, app, play state, track, queue, notifications, or a position jump).
  `/api/context` polling every 2 s is only the fallback while the stream is down. Phones drop the stream when the
  remote is in the background, unless lock-screen controls are on and something is playing.
- **Long poll to the TV.** While a phone is watching, the Pi waits on the bridge's `/wait?sig=…`, which answers the
  moment its signature changes (media callbacks, notifications, and a 400 ms re-check of volume, mute, power and the
  foreground app). With nobody watching, the Pi does a plain `/state` every 30 s.
- **One open TV shell** (`TvShell` in `server.py`) runs short commands: keys, inputs, opening apps and links,
  keyboard switches. Commands are read on fd 3 and run with stdin from /dev/null, because `cmd` and `settings` read
  stdin and would otherwise swallow the next command. Key injection blocks for ~14 s while the TV is in standby.
- Volume is set by the bridge (`/volume?set=N`, ~40 ms); the phone sends only the latest slider value while dragging.
- Trackpad steps are batched into one `/api/key {keys: [...]}` request.
- HTTP keep-alive (one TLS connection per phone), gzip for text over 1.4 KB, and ETags with `no-cache` on page files
  (an unchanged file costs a 304).
- dumpsys runs only as a fallback, plus tv_input at most every 15 s while the TV shows an HDMI input.
- When the TV is in deep standby, Wi-Fi still answers ping but adb stays "offline"; the remote shows "TV is off" and
  reconnects with backoff (adb disconnect + connect) instead of every 10 s.

## Approved phones only (added 2026-10-20)

- **One address for home and away:** `https://192.168.0.48`. `remote.local` and plain `http://` redirect there, except
  `/setup` and the CA downloads. Away from home it works over Tailscale because the Pi advertises the route
  `192.168.0.48/32`.
- **Every request needs an approved phone** (an HttpOnly `remote_device` cookie), and that includes the Home API.
  Unapproved phones get the pairing page (`pair.html`), and API calls get 401. `devices.json` holds only token
  hashes, mode 600, and deploys never overwrite it.
- **New phone:** on an approved phone, open TV controls → Remote settings → Phones → Add a phone, then type the 6-digit code
  (valid 10 min) on the new one. First phone, or if every phone was removed:
  `ssh flamingo@flamingo.local 'cd ~/tv-remote && python3 access.py code'`.
  It uses `runtime/local-secret`, because the port-80/443 forwarders make every phone look like localhost.
- **Away without Tailscale:** the service worker shows the cached app after 2.5 s, and `connect.js` shows
  "Can't reach home — turn on Tailscale". The card clears itself when `/api/ping` answers.

## Widgets and long-press shortcuts (added 2026-10-21)

- **Android long-press menu:** approved phones get a manifest whose `shortcuts` are the first scenes, then room lights
  (`server.manifest_with_shortcuts`, at most 4). They open `/?run=<scene>` or `/?toggle=<group>`, which
  `home-overview.js` acts on once. Android refreshes them when it next updates the installed app (can take a day).
- **Widget keys:** Quick settings → Phones → Add a widget key. The key is shown once; only its hash is stored
  (`kind: "widget"` in `devices.json`). It is sent as `Authorization: Bearer <key>` from iPhone Shortcuts or Android
  HTTP Shortcuts and may only use `WIDGET_ALLOWED` in `server.py`: read state/scenes, run a scene, set a device,
  and `/api/home/toggle` (smart-home: anything on → all off, else all on). Remove the key in Phones to switch its
  widgets off. The sheet lists every action with the body to paste.

## House view (added 2026-10-21)

Home overview → **View house** opens the flat as a live 3D model, full screen.
It opens with the whole flat centred and as big as the screen allows.

**Floor plan in the overview** (2026-10-25): Home overview → Rooms shows the flat as a flat 2D plan, drawn from
`house-plan.json` (walls, glass, furniture). Every device sits where it really is: tap to switch it, hold for its
controls, tap a room's name to open that room. Rooms glow with their lights; a cool edge means its AC is on. Devices
that aren't in the home (paused ones, say) don't appear. **Tiles** switches back to the room tiles (kept per phone).

- **Tap** a light, the lamp, an AC, a fan or the TV to switch it. **Press and hold** for its full controls; for the TV,
  that's the full remote.
- **2D / 3D** button (or a two-finger drag, as in Google Maps: down flattens, up stands it back up) tilts between the 3D dollhouse and a flat plan. **Walk** puts you
  inside at eye level: drag to look around (a flick glides to a stop), tap the floor to walk there along a real
  path (through doorways, around furniture; a ring shows where), or use the joystick bottom left (it slides along walls). Tapping the floor in 2D or 3D also walks you
  in at that spot.
- **Doors and curtains:** tap a door to swing it open or shut (the main door starts shut, room doors open). Tap the
  dining curtains to draw them and slide the balcony door; then glide out onto the balcony. Closed ones stop a glide.
- **Outside** (walk mode only): the view from the dining balcony, from the user's photos: the mud lot, the school
  across it (its windows light up at night), the tree, the pole and wires, the tower far off.
- **Easter eggs** (none of them touch a real device): the record player on the sideboard plays `easter/record.mp3`
  on the phone while the record spins; the bean bag squishes; the recliner reclines; the balcony chair folds; the
  bookshelf picks tonight's game (from `games` in the plan); the arc lamp switches a pretend light; outside, the big
  tree's birds fly off and school windows switch at night.
- Lighting follows the time of day. It opens in the living room the first time, then where you left it (per phone).
- Nothing 3D loads until it's opened. Drawing happens only when something changes; spinning fans run at about 30 fps.

Files: `house.js` (the scene), `house-models.js` (furniture shapes, picked by `model` in the plan, and the outside), `house-logic.js` (plain rules, tested by `node --test test_house_logic.mjs`),
`house-plan.json` (rooms, walls, doors, cupboards, furniture and device positions in feet; x east, y south),
`house.css`, and three.js 0.160.0 in `vendor/three/` (see its README for the one local change). The plan's mockups
and history are in `smart-home/docs/house-mockups/`.

**Moving a device or a wall:** edit `house-plan.json` and run `python3.13 -m unittest test_house_plan`. It checks that
every smart-home device is placed exactly once, on a wall that exists. A new smart-home device that isn't on the plan
shows under the model as "Not on the plan".

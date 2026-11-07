import gzip, hashlib, json, mimetypes, os, re, select, shlex, socket, ssl, subprocess, threading, time
import urllib.error, urllib.parse, urllib.request
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlsplit, parse_qs
from state_parser import parse_context, parse_power, parse_inputs, app_for, TV_PACKAGES
from http.cookies import SimpleCookie
import access

ROOT = Path(__file__).resolve().parent
TV_FILE = Path(__file__).resolve().parent / 'tv-address.txt'
TV_NAME = os.environ.get('TV_NAME', 'Living Room TV')
try:
    TV = TV_FILE.read_text().strip() or os.environ.get('TV_ADDRESS', '192.168.0.34:5555')
except OSError:
    TV = os.environ.get('TV_ADDRESS', '192.168.0.34:5555')
TV_MAC = os.environ.get('TV_MAC', '14:14:16:24:ac:66')
ADB = os.environ.get('ADB_PATH', '/opt/homebrew/bin/adb')
HOME_PORT = int(os.environ.get('SMART_HOME_PORT', 8780))
HOME = f'http://127.0.0.1:{HOME_PORT}'   # smart-home service (see ../smart-home)
BRIDGE = 'http://127.0.0.1:8770'  # TV companion app, reached through `adb forward`
BRIDGE_IME = 'com.livingroom.bridge/.Keyboard'
CONFIG = ROOT / 'remote-config.json'
ICONS = ROOT / 'icon-cache'
APPS_FILE = ROOT / 'apps-cache.json'
KEYS = dict(up=19, down=20, left=21, right=22, ok=23, back=4, home=3,
            volume_up=24, volume_down=25, mute=164, play_pause=85,
            rewind=89, forward=90, previous=88, next=87,
            input=178, settings=176, captions=175, search=84, guide=172, info=165)
ACTIONS = {'sound': ['shell', 'am', 'start', '-a', 'android.settings.SOUND_SETTINGS'],
           'picture': ['shell', 'am', 'start', '-n', 'com.android.tv.settings/.device.displaysound.DisplaySoundActivity'],
           'settings': ['shell', 'am', 'start', '-a', 'android.settings.SETTINGS'],
           'wifi': ['shell', 'am', 'start', '-a', 'android.settings.WIFI_SETTINGS']}
HDMI_INPUTS = {1: 'com.tcl.tvinput/.TvPassThroughService/HW15', 2: 'com.tcl.tvinput/.TvPassThroughService/HW16',
               3: 'com.tcl.tvinput/.TvPassThroughService/HW17'}
DISPATCH = dict(toggle='play-pause', play='play', pause='pause', next='next', previous='previous')

# Apps we know get their real logo and a fixed category; everything else uses the TV's own banner.
KNOWN = {
    'com.netflix.ninja': ('Streaming', 'netflix.svg'), 'com.amazon.amazonvideo.livingroom': ('Streaming', 'prime.svg'),
    'in.startv.hotstar': ('Streaming', 'hotstar.jpg'), 'com.google.android.youtube.tv': ('Streaming', 'youtube.svg'),
    'com.spotify.tv.android': ('Music', 'spotify.svg'),
    'com.apple.atve.androidtv.appletv': ('Streaming', None), 'com.graymatrix.did': ('Streaming', None),
    'com.sonyliv': ('Streaming', None), 'com.suntv.sunnxt': ('Streaming', None), 'tv.airtel.xstream.tvapp': ('Streaming', None),
    'com.google.android.videos': ('Streaming', None), 'com.google.android.youtube.tvmusic': ('Music', None),
    'org.videolan.vlc': ('Players', None), 'com.mxtech.videoplayer.television': ('Players', None),
    'org.xbmc.kodi': ('Players', None), 'com.tcl.ui_mediaCenter': ('Players', None),
    'com.google.android.play.games': ('Games', None),
}
CATEGORY_BY_ANDROID = {0: 'Games', 1: 'Music', 2: 'Streaming', 3: 'Players'}
CATEGORY_ORDER = ['Streaming', 'Music', 'Players', 'Games', 'More apps', 'TV tools']
PKG_RE = re.compile(r'[A-Za-z][\w.]{2,120}')

control_lock = threading.Lock()
state_lock = threading.Lock()
art_lock = threading.Lock()
config_lock = threading.Lock()
refresh = threading.Event()
state = dict(connected=False, power=None, app='home', app_name='Living Room TV', package=None,
             media=None, volume=None, volume_min=0, volume_max=100, ps5_port=None, hdmi_port=None,
             bridge=False, updated_at=0, sleep_at=None, routine=None, notifications=[], keyboard=False)
art_cache = (None, None, None)
apps_cache = (0, None)
keyboard = dict(previous=None, last=0)
changes = threading.Condition()   # phones on /api/events wait on this
state_version = [0]
last_print = [None]
bridge_seen = dict(sig=None, notes=None)
client_seen = [0.0]          # last time a phone asked for state; polling is fast only while someone is looking
inputs_cache = [0.0, (None, None, None)]
polls = [0]


ASLEEP = 'The TV is in deep standby and is not on the network, so it cannot be woken from here yet. Use the TV remote this time.'


def adb(*args, binary=False, timeout=14):
    result = subprocess.run([ADB, '-s', TV, *args], capture_output=True, timeout=timeout)
    if result.returncode:
        message = (result.stderr or result.stdout).decode(errors='replace').strip()
        if re.search(r"not found|offline|no devices|cannot connect|Connection refused", message, re.I):
            raise RuntimeError(ASLEEP)  # adb's own wording means nothing to someone holding a phone
        raise RuntimeError(message[:240] or 'TV unavailable')
    return result.stdout if binary else result.stdout.decode(errors='replace').strip()


def bridge(path, params=None, timeout=3, raw=False):
    url = BRIDGE + path + ('?' + urllib.parse.urlencode(params) if params else '')
    with urllib.request.urlopen(url, timeout=timeout) as response:
        body = response.read()
        return (body, response.headers.get('Content-Type')) if raw else json.loads(body)


def bridge_error(error):
    try: return json.loads(error.read()).get('error')
    except Exception: return None


CONTEXT_COMMAND = ("dumpsys -t 2 activity activities | grep -E 'mResumedActivity|topResumedActivity'; "
                   "echo __MEDIA__; dumpsys -t 2 media_session; echo __INPUT__; dumpsys -t 2 tv_input; "
                   "echo __VOLUME__; cmd media_session volume --stream 3 --get; "
                   "echo __POWER__; dumpsys power | grep -m1 'mWakefulness='")


def bridge_media():
    data = bridge('/state', timeout=2)
    if not data.get('session'):
        return dict(package=None)
    duration = data['duration_ms'] if data.get('duration_ms', -1) > 0 else None
    position = data['position_ms'] if data.get('position_ms', -1) >= 0 else None
    return dict(package=data.get('package'), title=data.get('title'), artist=data.get('artist'),
                album=data.get('album'), playing=data.get('state') == 3,
                position_ms=position, duration_ms=duration, speed=data.get('speed') or 1.0,
                can_seek=bool(data.get('actions', 0) & 256),
                art_id=data.get('art_id') or (('u' + str(abs(hash(data['art_uri'])))) if data.get('art_uri') else None),
                art_uri=data.get('art_uri'), queue=data.get('queue') or [], active_queue_id=data.get('active_queue_id', -1))


def fingerprint(snapshot):
    """What a phone would redraw for. The playback position only counts when it jumps (seek, new
    item), not while it ticks along as expected."""
    snap = {k: v for k, v in snapshot.items() if k not in ('updated_at',)}
    media = snap.get('media')
    if media and media.get('position_ms') is not None:
        moving = media.get('playing') and (media.get('speed') or 1)
        base = media['position_ms'] - (snapshot.get('updated_at', 0) * 1000 * moving if moving else 0)
        snap['media'] = {**media, 'position_ms': round(base / 3000)}
    return json.dumps(snap, sort_keys=True, default=str)


def publish():
    """Wake phones on /api/events, but only when something visible changed."""
    with state_lock: snapshot = json.loads(json.dumps(state, default=str))
    mark = fingerprint(snapshot)
    if mark == last_print[0]: return
    last_print[0] = mark
    with changes:
        state_version[0] += 1
        changes.notify_all()


def poll_bridge(wait=False):
    """One cheap call to the TV companion app: power, volume, foreground app and media.
    wait=True long-polls: the app answers the moment something changes (or after 15 s)."""
    if wait and bridge_seen['sig']:
        data = bridge('/wait', {'sig': bridge_seen['sig'], 'ms': 15000}, timeout=20)
    else:
        data = bridge('/state', timeout=3)
    bridge_seen['sig'] = data.get('sig')
    pkg = data.get('foreground')
    ps5_port, hdmi, input_id = None, None, None
    if pkg in TV_PACKAGES:
        # Only look at HDMI inputs when the TV is actually showing one, and not on every poll.
        if time.time() - inputs_cache[0] > 15:
            inputs_cache[:] = [time.time(), parse_inputs(adb('shell', 'dumpsys -t 2 tv_input', timeout=8))]
        ps5_port, hdmi, input_id = inputs_cache[1]
    app, name = app_for(pkg, ps5_port, hdmi)
    media = None
    if data.get('session') and data.get('title'):
        duration = data['duration_ms'] if data.get('duration_ms', -1) > 0 else None
        position = data['position_ms'] if data.get('position_ms', -1) >= 0 else None
        media = dict(package=data.get('package'), title=data.get('title'), artist=data.get('artist'),
                     album=data.get('album'), playing=data.get('state') == 3,
                     position_ms=position, duration_ms=duration, speed=data.get('speed') or 1.0,
                     can_seek=bool(data.get('actions', 0) & 256),
                     art_id=data.get('art_id') or (('u' + str(abs(hash(data['art_uri'])))) if data.get('art_uri') else None),
                     art_uri=data.get('art_uri'), queue=data.get('queue') or [], active_queue_id=data.get('active_queue_id', -1))
    update = dict(connected=True, standby=False, bridge=True, updated_at=time.time(), package=pkg, app=app, app_name=name,
                  power='on' if data.get('interactive') else 'off', volume=data.get('volume'),
                  volume_min=0, volume_max=data.get('volume_max') or 100, muted=bool(data.get('muted')),
                  media=media, ps5_port=ps5_port, hdmi_port=hdmi, input_id=input_id)
    polls[0] += 1
    notes = data.get('notes')
    if notes is None and polls[0] % 5 == 1 or notes is not None and notes != bridge_seen['notes']:
        try:
            update['notifications'] = bridge('/notifications', timeout=2).get('notifications', [])[:5]
            bridge_seen['notes'] = notes
        except Exception: pass
    with state_lock:
        state.update(update)


def poll_once(wait=False):
    try:
        return poll_bridge(wait)
    except Exception:
        pass  # companion app unavailable: fall back to dumpsys
    raw = adb('shell', CONTEXT_COMMAND, timeout=12)
    if '__MEDIA__' not in raw:
        raise RuntimeError('No TV state available')
    raw, _, power_text = raw.partition('__POWER__')
    data = parse_context(raw)
    data['power'] = parse_power(power_text)
    try:
        media, data['bridge'] = bridge_media(), True
        data['notifications'] = bridge('/notifications', timeout=2).get('notifications', [])[:5]
    except Exception:
        media, data['bridge'] = dict(package=data['package'], title=data['title'], artist=data['artist'],
                                     playing=data['playing'], position_ms=data['position_ms'],
                                     duration_ms=data['duration_ms'], can_seek=False, art_id=None, queue=[]), False
    data['media'] = media if media.get('title') else None
    for key in ('title', 'artist', 'playing', 'position_ms', 'duration_ms'):
        data.pop(key, None)
    data.update(connected=True, standby=False, updated_at=time.time())
    with state_lock:
        state.update(data)


def poll_state():
    while True:
        active = time.time() - client_seen[0] < 20
        began = time.time()
        try:
            poll_once(wait=active)
        except Exception:
            # Wi-Fi still answers pings in deep standby while Android itself is suspended: that is "off", not "lost".
            standby = tv_pingable()
            with state_lock:
                state.update(connected=False, standby=standby, power='off' if standby else None,
                             media=None, volume=None, updated_at=time.time())
        publish()
        with state_lock: live = state.get('bridge') and state.get('connected')
        if active and live:
            # The companion app's long poll paces this loop; never spin if it answers instantly.
            time.sleep(max(0, 0.15 - (time.time() - began)))
            refresh.clear()
            continue
        # 2 s while a phone has the remote open; otherwise a slow heartbeat so the TV is left alone.
        refresh.wait(2 if active else 30)
        refresh.clear()


def tv_pingable():
    # A ping alone can't tell the TV from another device that took its old address, so check its MAC too.
    host = TV.split(':')[0]
    try:
        if subprocess.run(['ping', '-c', '1', '-W', '1', host], capture_output=True, timeout=3).returncode != 0:
            return False
        neigh = subprocess.run(['ip', 'neigh', 'show', host], capture_output=True, timeout=3, text=True).stdout.lower()
        return TV_MAC.lower() in neigh
    except Exception:
        return False



def keep_connected():
    failures = 0
    while True:
        try:
            if adb('get-state', timeout=5) != 'device':
                raise RuntimeError('Offline')
            if 'tcp:8770' not in adb('forward', '--list', timeout=5):
                adb('forward', 'tcp:8770', 'tcp:8770', timeout=5)
            failures = 0
        except Exception:
            failures += 1
            # Back off while the TV sleeps; retry quickly when someone has the remote open.
            if failures <= 3 or failures % 6 == 0 or time.time() - client_seen[0] < 20:
                if failures % 3 == 0:
                    ip = discover_tv()     # the TV may have a new address (router restart / DHCP)
                    if ip: use_address(ip)
                reconnect()
        time.sleep(10)


def discover_tv():
    """Find the TV after a router restart or DHCP change: by its Chromecast name, then by its MAC."""
    found = None
    try:
        out = subprocess.run(['avahi-browse', '-rpt', '_googlecast._tcp'], capture_output=True, timeout=8, text=True).stdout
        for line in out.splitlines():
            f = line.split(';')
            if line.startswith('=') and len(f) > 9 and f[2] == 'IPv4' and f'fn={TV_NAME}' in f[9]:
                found = f[7]; break
    except Exception:
        pass
    if not found:
        try:
            out = subprocess.run(['ip', 'neigh'], capture_output=True, timeout=3, text=True).stdout
            m = re.search(r'^(\d+\.\d+\.\d+\.\d+) .*lladdr ' + re.escape(TV_MAC.lower()), out, re.M)
            if m: found = m.group(1)
        except Exception:
            pass
    return found


def use_address(ip):
    global TV
    new = f'{ip}:5555'
    if new == TV:
        return False
    subprocess.run([ADB, 'disconnect', TV], capture_output=True, timeout=5)
    TV = new
    try: TV_FILE.write_text(TV + '\n')
    except OSError: pass
    print(f'TV found at {TV}', flush=True)
    return True


class TvShell:
    """One long-lived `adb shell` for short commands: key presses, opening apps and inputs, switching keyboards.
    Opening a new adb session costs ~150-200 ms each time; through this shell a command starts in a few ms,
    and a batch of key steps lands together in one command."""
    # Commands arrive on fd 3; everything they run gets /dev/null as stdin, so a command that reads
    # stdin (`cmd`, `settings`) can never swallow the commands queued after it.
    LOOP = 'exec 3<&0 </dev/null; while IFS= read -r line <&3; do eval "$line"; done'

    def __init__(self):
        self.proc, self.target, self.lock, self.n = None, None, threading.Lock(), 0

    def close(self):
        if self.proc:
            try: self.proc.kill()
            except Exception: pass
        self.proc = None

    def sh(self, *args, timeout=6):
        """Like adb('shell', *args): returns the output, raises RuntimeError if the command fails."""
        with self.lock:
            try:
                command = ' '.join(shlex.quote(str(a)) for a in args)
                if '\n' in command: raise ValueError('one line per command')
                self._start()
                code, out = self._run(command, timeout)
            except TimeoutError:
                self.close()             # don't retry: the command may already have run
                raise RuntimeError('The TV did not respond')
            except (OSError, EOFError, ValueError):
                self.close()
                return adb('shell', *map(str, args), timeout=max(timeout, 5))   # slow path, still correct
            if code: raise RuntimeError(out or 'TV command failed')
            return out

    def press(self, codes):
        self.sh('cmd', 'input', 'keyevent', *codes, timeout=4)

    def _start(self):
        if not self.proc or self.proc.poll() is not None or self.target != TV:
            self.close()
            self.proc = subprocess.Popen([ADB, '-s', TV, 'shell', self.LOOP], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                         stderr=subprocess.STDOUT, bufsize=0)
            self.target = TV

    def _run(self, command, timeout):
        self.n += 1
        marker = re.compile(rb'__k%d (\d+)\r?\n' % self.n)
        self.proc.stdin.write(b'%s 2>&1; r=$?; echo; echo __k%d $r\n' % (command.encode(), self.n))
        out, deadline, fd = b'', time.time() + timeout, self.proc.stdout.fileno()
        while not (m := marker.search(out)):
            left = deadline - time.time()
            if left <= 0 or not select.select([fd], [], [], left)[0]: raise TimeoutError
            chunk = os.read(fd, 65536)
            if not chunk: raise EOFError('TV shell closed')
            out += chunk
        return int(m.group(1)), out[:m.start()].decode(errors='replace').strip()


tv = TvShell()
key_shell = tv   # key presses


def reconnect():
    # A stale session stays listed as "offline"; `adb connect` alone never revives it.
    tv.close()
    try:
        subprocess.run([ADB, 'disconnect', TV], capture_output=True, timeout=5)
        subprocess.run([ADB, 'connect', TV], capture_output=True, timeout=8)
        if adb('get-state', timeout=5) == 'device':
            adb('forward', 'tcp:8770', 'tcp:8770', timeout=5)
            refresh.set()
    except Exception:
        pass


# ---- Power ---------------------------------------------------------------------------------------

def wake_on_lan():
    packet = b'\xff' * 6 + bytes.fromhex(TV_MAC.replace(':', '')) * 16
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        for _ in range(3):
            for target in (('255.255.255.255', 9), ('192.168.0.255', 9), ('192.168.0.255', 7)):
                try: sock.sendto(packet, target)
                except OSError: pass
            time.sleep(0.2)


def current_power():
    try:
        return 'on' if bridge('/state', timeout=3).get('interactive') else 'off'
    except Exception:
        pass
    try:
        return parse_power(adb('shell', "dumpsys power | grep -m1 'mWakefulness='", timeout=5))
    except Exception:
        return None


def set_power(target):
    """Turn the TV on or off and wait until Android confirms it."""
    power = current_power()
    if power == target:
        return dict(ok=True, power=power, message='TV is on' if target == 'on' else 'TV is off')
    if target == 'off':
        adb('shell', 'input', 'keyevent', '223')
    else:
        wake_on_lan()
        try: adb('shell', 'input', 'keyevent', '224', timeout=5)
        except Exception: pass
    deadline = time.time() + (6 if target == 'off' else 15)
    while time.time() < deadline:
        time.sleep(1)
        if target == 'on' and power is None:
            reconnect()
            try: adb('shell', 'input', 'keyevent', '224', timeout=4)
            except Exception: pass
        now = current_power()
        if now == target:
            with state_lock: state['power'] = now
            refresh.set()
            return dict(ok=True, power=now, message='TV is on' if now == 'on' else 'TV is off')
    refresh.set()
    if target == 'on':
        raise RuntimeError(ASLEEP if not tv_pingable() else 'The TV did not wake. Try again in a few seconds.')
    raise RuntimeError('TV did not confirm sleep')


def toggle_power():
    return set_power('off' if current_power() == 'on' else 'on')


# ---- Media -------------------------------------------------------------------------------------------

def media_command(action, ms=None):
    params = {'a': action}
    if ms is not None: params['ms'] = int(ms)
    try:
        bridge('/cmd', params, timeout=3)
        return
    except urllib.error.HTTPError as error:
        reason = bridge_error(error)
        if reason and reason not in ('no media session', 'notification access not granted'):
            raise RuntimeError(reason[0].upper() + reason[1:])
    except Exception:
        pass
    if action in DISPATCH:
        tv.sh('cmd', 'media_session', 'dispatch', DISPATCH[action])
    elif action == 'skip':
        tv.press([90 if ms and ms > 0 else 89])
    else:
        raise RuntimeError('This needs the TV companion app')


def artwork():
    global art_cache
    with state_lock:
        media = state.get('media') or {}
    key = media.get('art_id')
    if not key:
        raise RuntimeError('No artwork')
    with art_lock:
        if art_cache[0] == key:
            return art_cache[1], art_cache[2]
        try:
            body, kind = bridge('/art', timeout=4, raw=True)
        except Exception:
            uri = media.get('art_uri')
            if not uri:
                raise
            request = urllib.request.Request(uri, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(request, timeout=6) as response:
                body, kind = response.read(4_000_000), response.headers.get('Content-Type', 'image/jpeg')
            if not kind.startswith('image/'):
                raise RuntimeError('Artwork is not an image')
        art_cache = (key, body, kind)
        return body, kind


# ---- Apps, links, inputs -------------------------------------------------------------------------------------------

def list_apps(force=False):
    global apps_cache
    if not force and apps_cache[1] and time.time() - apps_cache[0] < 300:
        return apps_cache[1]
    try:
        try:
            raw = bridge('/apps', timeout=8)['apps']
        except Exception:
            time.sleep(0.5)
            raw = bridge('/apps', timeout=8)['apps']
    except Exception:
        # TV off or unreachable: serve the last list we saw so names and logos still show.
        if apps_cache[1]: return apps_cache[1]
        if APPS_FILE.exists():
            apps_cache = (0, json.loads(APPS_FILE.read_text()))
            return apps_cache[1]
        raise
    apps = []
    for item in raw:
        pkg = item['package']
        category, logo = KNOWN.get(pkg, (None, None))
        if not category:
            if pkg.startswith(('com.tcl.', 'com.android.', 'com.google.android.tv', 'com.overdevs.', 'com.livingroom.')) or pkg == 'com.android.vending':
                category = 'TV tools'
            else:
                category = CATEGORY_BY_ANDROID.get(item.get('category'), 'More apps')
        apps.append(dict(package=pkg, name=item['label'], category=category,
                         logo='/assets/' + logo if logo else None,
                         icon='/api/icon?pkg=' + urllib.parse.quote(pkg), last_used=item.get('last_used', 0)))
    apps.sort(key=lambda a: (CATEGORY_ORDER.index(a['category']), -a['last_used'], a['name'].lower()))
    apps_cache = (time.time(), apps)
    try: APPS_FILE.write_text(json.dumps(apps))
    except OSError: pass
    return apps


def app_icon(pkg):
    ICONS.mkdir(exist_ok=True)
    path = ICONS / (pkg + '.png')
    if path.exists() and time.time() - path.stat().st_mtime < 7 * 86400:
        return path.read_bytes()
    try:
        body, _ = bridge('/icon', {'pkg': pkg}, timeout=6, raw=True)
    except Exception:
        if path.exists(): return path.read_bytes()   # stale beats nothing while the TV is away
        raise
    path.write_bytes(body)
    return body


def launch(pkg):
    if not PKG_RE.fullmatch(pkg or '') or pkg not in {a['package'] for a in list_apps()}:
        raise RuntimeError('That app is not on the TV')
    try:
        bridge('/launch', {'pkg': pkg}, timeout=5)
    except urllib.error.HTTPError as error:
        raise RuntimeError(bridge_error(error) or 'App could not be opened')
    except Exception:
        output = tv.sh('monkey', '-p', pkg, '-c', 'android.intent.category.LEANBACK_LAUNCHER', '1', timeout=10)
        if 'Events injected: 1' not in output: raise RuntimeError('App could not be opened')


def tv_link(url):
    """Map a shared link to something the TV can open, in the right app."""
    parts = urlsplit(url)
    host = (parts.hostname or '').lower().removeprefix('www.').removeprefix('m.')
    if host in ('youtube.com', 'youtu.be', 'music.youtube.com'):
        vid = parts.path.strip('/') if host == 'youtu.be' else (parse_qs(parts.query).get('v') or [None])[0]
        if not vid and parts.path.startswith('/shorts/'): vid = parts.path.split('/')[2]
        if vid and re.fullmatch(r'[\w-]{6,20}', vid):
            return 'https://www.youtube.com/watch?v=' + vid, 'com.google.android.youtube.tv'
    if host == 'netflix.com':
        m = re.search(r'/(?:title|watch)/(\d+)', parts.path)
        if m: return 'https://www.netflix.com/watch/' + m.group(1), 'com.netflix.ninja'
    if host == 'open.spotify.com':
        return url, 'com.spotify.tv.android'
    if host in ('primevideo.com', 'amazon.com', 'amazon.in'):
        return url, 'com.amazon.amazonvideo.livingroom'
    if host in ('hotstar.com', 'jiohotstar.com'):
        return url, 'in.startv.hotstar'
    return url, None


def open_link(url):
    if not re.match(r'https?://', url or '') or len(url) > 2000:
        raise RuntimeError('Share a web link (http or https)')
    target, pkg = tv_link(url)
    params = {'uri': target}
    if pkg: params['pkg'] = pkg
    try:
        bridge('/view', params, timeout=5)
    except urllib.error.HTTPError as error:
        if pkg:  # app missing or refused: let any app on the TV try
            try:
                bridge('/view', {'uri': target}, timeout=5); return
            except urllib.error.HTTPError as again:
                error = again
        raise RuntimeError((bridge_error(error) or 'The TV cannot open this link').capitalize())


def switch_input(port):
    input_id = HDMI_INPUTS.get(port)
    if not input_id:
        raise RuntimeError('Unknown input')
    uri = 'content://android.media.tv/passthrough/' + urllib.parse.quote(input_id, safe='')
    try:
        bridge('/view', {'uri': uri}, timeout=5)
    except Exception:
        tv.sh('am', 'start', '-a', 'android.intent.action.VIEW', '-d', uri)


def global_search(text):
    try:
        bridge('/search', {'q': text}, timeout=5)
    except urllib.error.HTTPError as error:
        raise RuntimeError((bridge_error(error) or 'Search could not open').capitalize())


# ---- Phone keyboard session: switch to the bridge keyboard only while typing --------------------------------

def keyboard_open():
    current = tv.sh('settings', 'get', 'secure', 'default_input_method', timeout=5)
    if current != BRIDGE_IME:
        keyboard['previous'] = current
        tv.sh('ime', 'set', BRIDGE_IME, timeout=5)
    keyboard['last'] = time.time()
    with state_lock: state['keyboard'] = True
    for _ in range(10):
        try:
            data = bridge('/ime/state', timeout=2)
            if data.get('ready') and data.get('active'):
                return data
        except Exception:
            pass
        time.sleep(0.25)
    return dict(ready=True, active=False, text=None, hint=None)


def keyboard_close():
    previous = keyboard.get('previous')
    keyboard['previous'] = None
    with state_lock: state['keyboard'] = False
    if previous and previous != BRIDGE_IME:
        tv.sh('ime', 'set', previous, timeout=5)


def keyboard_watchdog():
    # Never leave the TV on the invisible keyboard if the phone goes away mid-typing.
    while True:
        time.sleep(10)
        if keyboard.get('previous') and time.time() - keyboard['last'] > 90:
            try: keyboard_close()
            except Exception: pass


# ---- Shortcuts, routines, sleep timer -------------------------------------------------------------------------------------------

DEFAULT_CONFIG = {
    'shortcuts': [
        {'type': 'app', 'package': 'com.netflix.ninja'},
        {'type': 'app', 'package': 'com.google.android.youtube.tv'},
        {'type': 'app', 'package': 'com.amazon.amazonvideo.livingroom'},
        {'type': 'app', 'package': 'in.startv.hotstar'},
        {'type': 'app', 'package': 'com.spotify.tv.android'},
        {'type': 'input', 'port': 1, 'name': 'PlayStation 5'},
        {'type': 'routine', 'id': 'movie-night'},
        {'type': 'action', 'action': 'picture'},
    ],
    'routines': [
        {'id': 'movie-night', 'name': 'Movie night', 'steps': [
            {'do': 'power_on'}, {'do': 'volume', 'value': 22}, {'do': 'app', 'package': 'com.netflix.ninja'}]},
        {'id': 'play-ps5', 'name': 'Play PS5', 'steps': [{'do': 'power_on'}, {'do': 'input', 'port': 1}]},
        {'id': 'bedtime', 'name': 'Bedtime', 'steps': [{'do': 'volume', 'value': 10}, {'do': 'sleep_timer', 'minutes': 30}]},
    ],
}
STEP_TYPES = {'power_on', 'power_off', 'volume', 'app', 'input', 'key', 'wait', 'open', 'sleep_timer', 'action'}


def load_config():
    with config_lock:
        try: return json.loads(CONFIG.read_text())
        except Exception: return json.loads(json.dumps(DEFAULT_CONFIG))


def validate_config(data):
    if not isinstance(data, dict): raise ValueError('Invalid settings')
    shortcuts, routines = data.get('shortcuts', []), data.get('routines', [])
    if not isinstance(shortcuts, list) or not isinstance(routines, list) or len(shortcuts) > 16 or len(routines) > 20:
        raise ValueError('Too many shortcuts or routines')
    clean_s, clean_r = [], []
    for s in shortcuts:
        if not isinstance(s, dict): continue
        kind = s.get('type')
        if kind == 'app' and PKG_RE.fullmatch(str(s.get('package', ''))): clean_s.append({'type': 'app', 'package': s['package']})
        elif kind == 'action' and s.get('action') in ACTIONS: clean_s.append({'type': 'action', 'action': s['action']})
        elif kind == 'input' and s.get('port') in HDMI_INPUTS:
            clean_s.append({'type': 'input', 'port': s['port'], 'name': str(s.get('name') or f"HDMI {s['port']}")[:30]})
        elif kind == 'routine' and isinstance(s.get('id'), str): clean_s.append({'type': 'routine', 'id': s['id'][:40]})
    for r in routines:
        if not isinstance(r, dict): continue
        steps = [st for st in r.get('steps', []) if isinstance(st, dict) and st.get('do') in STEP_TYPES][:12]
        name = str(r.get('name', '')).strip()[:40]
        rid = re.sub(r'[^a-z0-9-]', '', str(r.get('id') or name.lower().replace(' ', '-')))[:40]
        if name and rid and steps: clean_r.append({'id': rid, 'name': name, 'steps': steps})
    return {'shortcuts': clean_s, 'routines': clean_r}


def save_config(data):
    clean = validate_config(data)
    with config_lock:
        tmp = CONFIG.with_suffix('.tmp')
        tmp.write_text(json.dumps(clean, indent=1))
        tmp.replace(CONFIG)
    return clean


def set_volume(value):
    with state_lock: maximum, minimum = state.get('volume_max', 100), state.get('volume_min', 0)
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError('Volume out of range')
    try:
        bridge('/volume', {'set': value}, timeout=3)     # direct, ~40 ms
    except Exception:
        output = tv.sh('cmd', 'media_session', 'volume', '--stream', '3', '--set', str(value))
        if '[E]' in output or 'Error' in output: raise RuntimeError('Could not set TV volume')
    with state_lock: state['volume'] = value


def set_sleep(minutes):
    with state_lock:
        state['sleep_at'] = time.time() + minutes * 60 if minutes else None


def sleep_watch():
    while True:
        time.sleep(10)
        with state_lock: due = state.get('sleep_at')
        if due and time.time() >= due:
            with state_lock: state['sleep_at'] = None
            try: set_power('off')
            except Exception: pass


def run_step(step):
    kind = step['do']
    if kind == 'power_on': set_power('on'); time.sleep(2)
    elif kind == 'power_off': set_power('off')
    elif kind == 'volume': set_volume(int(step.get('value', 20)))
    elif kind == 'app': launch(step.get('package')); time.sleep(1.5)
    elif kind == 'input': switch_input(int(step.get('port', 1))); time.sleep(1.5)
    elif kind == 'key' and step.get('key') in KEYS: tv.press([KEYS[step['key']]])
    elif kind == 'wait': time.sleep(min(60, max(0, float(step.get('seconds', 1)))))
    elif kind == 'open': open_link(step.get('url'))
    elif kind == 'action' and step.get('action') in ACTIONS: adb(*ACTIONS[step['action']])
    elif kind == 'sleep_timer': set_sleep(max(0, min(240, int(step.get('minutes', 30)))))


def run_routine(routine_id):
    routine = next((r for r in load_config()['routines'] if r['id'] == routine_id), None)
    if not routine:
        raise RuntimeError('Routine not found')
    with state_lock:
        if state.get('routine'): raise RuntimeError('Another routine is running')
        state['routine'] = dict(id=routine['id'], name=routine['name'], step=0, total=len(routine['steps']), error=None)

    def worker():
        try:
            for i, step in enumerate(routine['steps']):
                with state_lock: state['routine']['step'] = i + 1
                if step['do'] in ('power_on', 'power_off', 'wait'): run_step(step)
                else:
                    with control_lock: run_step(step)
                refresh.set()
            time.sleep(2)
        except Exception as error:
            with state_lock: state['routine']['error'] = str(error)
            time.sleep(4)
        finally:
            with state_lock: state['routine'] = None
    threading.Thread(target=worker, daemon=True).start()


# ---- HTTP -------------------------------------------------------------------------------------------

PAGE_FILES = ('index.html', 'app.js', 'app.css', 'home.js', 'home.css', 'home-overview.js', 'connect.js', 'sw.js', 'pair.html', 'pair.js',
              'house-plan.json', 'house-logic.js', 'house.js', 'house-models.js', 'house.css')


def page_version():
    """Short hash of the page's own files: changes whenever a deploy changes what phones should be running."""
    digest = hashlib.sha256()
    for name in PAGE_FILES:
        try: digest.update(ROOT.joinpath(name).read_bytes())
        except OSError: pass
    return digest.hexdigest()[:12]


def manifest_with_shortcuts():
    """Android long-press menu on the Home icon: the first scenes, then room lights (one tap toggles).
    Only approved phones get these (index.html asks for the manifest with credentials)."""
    manifest = json.loads(ROOT.joinpath('manifest.webmanifest').read_text())
    try:
        with urllib.request.urlopen(HOME + '/api/home/routines', timeout=3) as response:
            store = json.load(response)
    except (OSError, ValueError):
        return manifest
    icon = [{'src': '/icon-192.png', 'sizes': '192x192'}]
    scenes = [{'name': r['name'], 'url': f"/?run={urllib.parse.quote(r['id'])}", 'icons': icon} for r in store.get('routines', [])]
    lights = [{'name': g['name'], 'url': f"/?toggle={urllib.parse.quote(g['id'])}", 'icons': icon}
              for g in store.get('builtin', []) if g['id'].endswith('-lights') and g['id'] != 'all-lights']
    manifest['shortcuts'] = (scenes + lights)[:4] or manifest.get('shortcuts', [])
    return manifest


# ---- Approved phones only (see access.py) ---------------------------------------------------------------
ACCESS = access.Access(ROOT / 'devices.json')
CANONICAL = os.environ.get('CANONICAL_ORIGIN', 'https://192.168.0.48')   # one address that works at home and over Tailscale
KEEP_HOSTS = {urlsplit(CANONICAL).hostname, *os.environ.get('TAILSCALE_HOSTS', '100.110.26.84').split(',')}   # addresses phones may stay on
COOKIE = 'remote_device'


def cookie_for(token):
    # Lax, not Strict: Android's installed app opens from the home screen as a cross-site visit, and Chrome leaves a
    # Strict cookie out of it, so the phone looked unpaired after every reload. Changes still need X-TV-Remote.
    return f'{COOKIE}={token}; Path=/; Max-Age=157680000; HttpOnly; Secure; SameSite=Lax'
OPEN_GET = {'/pair.js', '/app.css', '/manifest.webmanifest', '/sw.js', '/setup', '/setup.js', '/install-check', '/api/ping',
            '/local-ca.crt', '/local-ca.mobileconfig', '/icon-180.png', '/icon-192.png', '/icon-512.png', '/icon-maskable-512.png'}
OPEN_POST = {'/api/pair', '/api/local-code'}
PLAIN_HTTP_OK = {'/setup', '/setup.js', '/install-check', '/local-ca.crt', '/local-ca.mobileconfig', '/api/ping'}   # before the CA is trusted
# A widget key (iPhone Shortcuts, Android HTTP Shortcuts) may only do these: no TV, no adding phones, no editing scenes
WIDGET_ALLOWED = {('GET', '/api/home/state'), ('GET', '/api/home/routines'),
                  ('POST', '/api/home/run'), ('POST', '/api/home/set'), ('POST', '/api/home/toggle')}
LOCAL_SECRET = ROOT / 'runtime' / 'local-secret'     # lets `python3 access.py code` on the Pi itself ask for a first code


def local_secret():
    if not LOCAL_SECRET.exists():
        LOCAL_SECRET.parent.mkdir(exist_ok=True)
        LOCAL_SECRET.write_text(__import__('secrets').token_hex(16))
        os.chmod(LOCAL_SECRET, 0o600)
    return LOCAL_SECRET.read_text().strip()


COMPRESSIBLE = re.compile(r'(text/|application/(json|javascript|manifest\+json)|image/svg)')
gzip_cache = {}


def gzipped(data, key=None):
    """Page files are compressed once per version (key = their ETag); API replies on the fly."""
    if key and key in gzip_cache: return gzip_cache[key]
    packed = gzip.compress(data, compresslevel=6 if key else 4)
    if key:
        if len(gzip_cache) > 64: gzip_cache.clear()
        gzip_cache[key] = packed
    return packed


def context_payload():
    with state_lock: data = json.loads(json.dumps(state, default=str))
    if data.get('media'): data['media'].pop('art_uri', None)
    data['device'] = 'Living Room TV'
    data['server_time'] = time.time()
    return data


class Handler(BaseHTTPRequestHandler):
    secure = False          # SecureHandler (the HTTPS port) says True
    protocol_version = 'HTTP/1.1'   # keep-alive: a phone reuses one TLS connection instead of a handshake per request
    timeout = 75                    # idle keep-alive connections are closed after this

    def log_message(self, *args): pass

    def reply(self, code, data, kind='application/json', cache='no-store', headers=(), etag=None):
        data = data if isinstance(data, bytes) else json.dumps(data).encode()
        if etag and code == 200:
            headers = [*headers, ('ETag', etag)]
            if etag in (self.headers.get('If-None-Match') or ''):
                code, data = 304, b''          # the phone's copy is current: send nothing
        if len(data) > 1400 and COMPRESSIBLE.match(kind) and 'gzip' in (self.headers.get('Accept-Encoding') or ''):
            data = gzipped(data, etag)
            headers = [*headers, ('Content-Encoding', 'gzip'), ('Vary', 'Accept-Encoding')]
        self.send_response(code)
        for name, value in headers: self.send_header(name, value)
        self.send_header('Content-Type', kind)
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', cache)
        if self.command == 'POST' and code >= 400:
            # the request body may be unread: don't let it be parsed as the next request
            self.send_header('Connection', 'close'); self.close_connection = True
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Security-Policy', "default-src 'self'; img-src 'self' data: blob:; script-src 'self'; style-src 'self' 'unsafe-inline'; media-src 'self' data:; frame-ancestors 'none'")
        self.end_headers()
        if self.command != 'HEAD': self.wfile.write(data)

    def redirect(self, location):
        self.send_response(303); self.send_header('Location', location); self.send_header('Content-Length', '0'); self.end_headers()

    def do_HEAD(self): self.do_GET()

    def device(self):
        """The approved phone or widget key: the cookie from the app, or `Authorization: Bearer` from a widget."""
        auth = self.headers.get('Authorization') or ''
        if auth.startswith('Bearer '): return ACCESS.device_for(auth.removeprefix('Bearer ').strip())
        jar = SimpleCookie(self.headers.get('Cookie', ''))
        return ACCESS.device_for(jar[COOKIE].value) if COOKIE in jar else None

    def gate(self, method):
        """Redirect to the one canonical address, then let only approved phones through. True = carry on."""
        path = urlsplit(self.path).path
        host = (self.headers.get('Host') or '').split(':')[0]
        if method == 'GET' and host not in ('127.0.0.1', 'localhost') and path not in PLAIN_HTTP_OK \
                and (host not in KEEP_HOSTS or not self.secure):
            # LAN names go to the one home address; the Tailscale address stays itself, just over HTTPS (for the cookie)
            target = f'https://{host}' if host in KEEP_HOSTS else CANONICAL
            self.send_response(301); self.send_header('Location', target + self.path)
            self.send_header('Content-Length', '0'); self.end_headers()
            return False
        if path in OPEN_GET if method == 'GET' else path in OPEN_POST:
            return True
        device = self.device()
        if device and device.get('kind') == 'widget' and (method, path) not in WIDGET_ALLOWED:
            self.reply(403, dict(error='A widget key can only run scenes and switch devices'))
            return False
        if device:
            return True
        if method == 'GET' and path == '/':
            self.reply(200, ROOT.joinpath('pair.html').read_bytes(), 'text/html; charset=utf-8', headers=[('X-Pair-Page', '1')])
        else:
            self.reply(401, dict(error="This phone isn't approved yet", pair=True))
        return False

    def do_GET(self):
        if not self.gate('GET'): return
        path = urlsplit(self.path).path
        if path == '/api/ping':       # also renews a paired phone's cookie with today's flags
            jar = SimpleCookie(self.headers.get('Cookie', ''))
            token = jar[COOKIE].value if COOKIE in jar else None
            renew = [('Set-Cookie', cookie_for(token))] if token and ACCESS.device_for(token) else []
            return self.reply(200, dict(ok=True, paired=bool(renew)), headers=renew)
        if path == '/api/devices':
            mine = self.device()
            return self.reply(200, dict(devices=[{**d, 'this': d['id'] == mine['id']} for d in ACCESS.devices()]))
        return self.get_page()

    def events(self):
        """Server-sent events: the full context once, then again whenever something visible changes.
        A comment line every 15 s keeps the connection (and the "someone is looking" mark) alive."""
        if time.time() - client_seen[0] > 20: refresh.set()
        client_seen[0] = time.time()
        self.close_connection = True
        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Connection', 'close')
        self.end_headers()
        seen, ends = None, time.time() + 300      # reconnect every 5 min so a phone that vanished silently is let go
        try:
            while time.time() < ends:
                client_seen[0] = time.time()
                with changes:
                    if state_version[0] == seen: changes.wait(15)
                    version = state_version[0]
                if version != seen:
                    seen = version
                    self.wfile.write(b'data: ' + json.dumps(context_payload()).encode() + b'\n\n')
                else:
                    self.wfile.write(b': ping\n\n')
                self.wfile.flush()
        except OSError:
            pass

    def account_post(self):
        """Pairing and the Phones sheet. Returns True if it handled the request."""
        if self.path not in ('/api/pair', '/api/local-code', '/api/devices'): return False
        try:
            size = int(self.headers.get('Content-Length', 0) or 0)
            body = json.loads(self.rfile.read(size) or b'{}') if 0 <= size <= 2048 else None
            if not isinstance(body, dict): raise ValueError('Invalid request')
            if self.path == '/api/local-code':
                if self.headers.get('X-Local-Secret') != local_secret(): return self.reply(403, dict(error='Invalid request')) or True
                return self.reply(200, dict(code=ACCESS.new_code(), expires_in=access.CODE_TTL)) or True
            if self.path == '/api/pair':
                token, device = ACCESS.redeem(body.get('code', ''), body.get('name', ''))
                return self.reply(200, dict(ok=True, name=device['name']), headers=[('Set-Cookie', cookie_for(token))]) or True
            action = body.get('action')
            if action == 'code': return self.reply(200, dict(code=ACCESS.new_code(), expires_in=access.CODE_TTL)) or True
            if action == 'key':      # shown once on the phone that made it; only its hash is kept
                key, device = ACCESS.new_key(body.get('name'))
                return self.reply(200, dict(key=key, id=device['id'], name=device['name'])) or True
            if action == 'remove': ACCESS.remove(str(body.get('id'))); return self.reply(200, dict(ok=True)) or True
            if action == 'rename': ACCESS.rename(str(body.get('id')), body.get('name')); return self.reply(200, dict(ok=True)) or True
            raise ValueError('Unknown action')
        except ValueError as error:
            self.reply(400, dict(error=str(error)))
        return True

    def get_page(self):
        parts = urlsplit(self.path)
        path, query = parts.path, parse_qs(parts.query)
        html = 'text/html; charset=utf-8'
        files = {'/': ('index.html', html), '/setup': ('setup.html', html),
                 '/manifest.webmanifest': ('manifest.webmanifest', 'application/manifest+json'),
                 '/sw.js': ('sw.js', 'application/javascript'),
                 '/app.js': ('app.js', 'application/javascript'),
                 '/setup.js': ('setup.js', 'application/javascript'),
                 '/home.js': ('home.js', 'application/javascript'),
                 '/home-overview.js': ('home-overview.js', 'application/javascript'),
                 '/home.css': ('home.css', 'text/css'),
                 '/pair.js': ('pair.js', 'application/javascript'),
                 '/connect.js': ('connect.js', 'application/javascript'),
                 '/house-plan.json': ('house-plan.json', 'application/json'),
                 '/house-logic.js': ('house-logic.js', 'application/javascript'),
                 '/house.js': ('house.js', 'application/javascript'),
                 '/house-models.js': ('house-models.js', 'application/javascript'),
                 '/house.css': ('house.css', 'text/css'),
                 '/easter/record.mp3': ('easter/record.mp3', 'audio/mpeg'),     # the house view's record player
                 '/app.css': ('app.css', 'text/css'),
                 '/local-ca.crt': ('certs/ca.crt', 'application/x-x509-ca-cert'),
                 '/local-ca.mobileconfig': ('certs/ca.mobileconfig', 'application/x-apple-aspen-config')}
        for name in ('icon-180.png', 'icon-192.png', 'icon-512.png', 'icon-maskable-512.png'):
            files['/' + name] = (name, 'image/png')
        if path.startswith('/assets/'):
            name = path.removeprefix('/assets/')
            if re.fullmatch(r'[a-z0-9_-]+\.(svg|jpg|png)', name):
                files[path] = ('assets/' + name, mimetypes.guess_type(name)[0] or 'application/octet-stream')
        if path.startswith('/vendor/three/'):      # three.js for the house view, served here so it never needs a CDN
            name = path.removeprefix('/vendor/three/')
            if re.fullmatch(r'(addons/[a-z]+/[A-Za-z]+|three\.module\.min)\.js', name) and ROOT.joinpath('vendor/three', name).is_file():
                files[path] = ('vendor/three/' + name, 'application/javascript')
        if path == '/install-check':
            return self.redirect('/setup')
        if path == '/manifest.webmanifest' and self.device():
            body = json.dumps(manifest_with_shortcuts()).encode()
            return self.reply(200, body, 'application/manifest+json', cache='no-cache', etag='"%s"' % hashlib.sha1(body).hexdigest()[:16])
        if path in files:
            filename, kind = files[path]
            try:
                body = ROOT.joinpath(filename).read_bytes()
                if filename == 'index.html':      # the page knows which version it is (see home.js checkVersion)
                    body = body.replace(b'__PAGE_VERSION__', page_version().encode())
                # no-cache = keep a copy but check first; the check costs a tiny 304 when nothing changed
                return self.reply(200, body, kind, cache='no-cache', etag='"%s"' % hashlib.sha1(body).hexdigest()[:16])
            except FileNotFoundError: return self.reply(404, dict(error='File not found'))
        if path in ('/api/status', '/api/context'):
            if time.time() - client_seen[0] > 20: refresh.set()   # someone just opened the remote: poll now
            client_seen[0] = time.time()
            return self.reply(200, context_payload())
        if path == '/api/events':
            return self.events()
        if path == '/api/art':
            try:
                body, kind = artwork()
                return self.reply(200, body, kind, cache='private, max-age=3600')
            except Exception:
                return self.reply(404, dict(error='No artwork'))
        if path == '/api/apps':
            try: return self.reply(200, dict(apps=list_apps(force='refresh' in query)))
            except Exception: return self.reply(503, dict(error='App list unavailable. Is the TV on the network?'))
        if path == '/api/icon':
            pkg = (query.get('pkg') or [''])[0]
            if not PKG_RE.fullmatch(pkg): return self.reply(400, dict(error='Bad package'))
            try: return self.reply(200, app_icon(pkg), 'image/png', cache='private, max-age=604800')
            except Exception: return self.reply(404, dict(error='No icon'))
        if path == '/api/version':      # open pages poll this and reload themselves after a deploy
            return self.reply(200, dict(version=page_version()))
        if path == '/api/config':
            return self.reply(200, load_config())
        if path.startswith('/api/home/'):
            return self.proxy_home('GET')
        if path == '/share':
            # Web Share Target: Android's share sheet sends links here.
            text = ' '.join((query.get(k) or [''])[0] for k in ('url', 'text', 'title'))
            link = re.search(r'https?://\S+', text)
            try:
                if not link: raise RuntimeError('No link found in what was shared')
                with control_lock: open_link(link.group(0).rstrip('.,)'))
                return self.redirect('/?shared=ok')
            except Exception as error:
                return self.redirect('/?shared=' + urllib.parse.quote(str(error)[:120]))
        self.reply(404, dict(error='Not found'))

    def proxy_home(self, method):
        """Pass /api/home/* to the smart-home service (localhost only). Never touches TV state or locks."""
        size = int(self.headers.get('Content-Length', 0) or 0)
        if size > 32768: return self.reply(400, dict(error='Invalid request size'))
        body = self.rfile.read(size) if size else None
        request = urllib.request.Request(HOME + self.path, data=body, method=method,
                                         headers={'X-TV-Remote': self.headers.get('X-TV-Remote', ''),
                                                  'Content-Type': 'application/json'})
        try:
            # Connect fast so a stopped service fails quickly; then allow for a long-poll or a slow device.
            connection = socket.create_connection(('127.0.0.1', HOME_PORT), timeout=1.5)
            connection.close()
            timeout = 30 if self.path.startswith('/api/home/wait') else 12
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return self.reply(response.status, response.read())
        except urllib.error.HTTPError as error:
            return self.reply(error.code, error.read())
        except (OSError, urllib.error.URLError):
            return self.reply(503, dict(error="Home controls aren't reachable"))

    def do_POST(self):
        if self.headers.get('X-TV-Remote') != '1': return self.reply(403, dict(error='Invalid request'))
        if not self.gate('POST'): return
        if self.account_post(): return
        if self.path.startswith('/api/home/'): return self.proxy_home('POST')
        try:
            size = int(self.headers.get('Content-Length', 0))
            limit = 16384 if self.path == '/api/config' else 2048
            if not 0 < size <= limit: return self.reply(400, dict(error='Invalid request size'))
            body = json.loads(self.rfile.read(size))
            if not isinstance(body, dict): return self.reply(400, dict(error='Invalid request'))
            # These wait for the TV or only touch Pi-side state, so they don't hold the control lock.
            if self.path == '/api/power':
                target = body.get('target')
                return self.reply(200, set_power(target) if target in ('on', 'off') else toggle_power())
            if self.path == '/api/config':
                return self.reply(200, save_config(body))
            if self.path == '/api/routine':
                run_routine(str(body.get('id', '')))
                return self.reply(200, dict(ok=True))
            if self.path == '/api/sleep':
                minutes = body.get('minutes')
                if type(minutes) is not int or not 0 <= minutes <= 240: return self.reply(400, dict(error='Choose 0 to 240 minutes'))
                set_sleep(minutes)
                return self.reply(200, dict(ok=True))
            if self.path == '/api/keyboard':
                op = body.get('op')
                keyboard['last'] = time.time()
                if op == 'open': return self.reply(200, keyboard_open())
                if op == 'close': keyboard_close(); return self.reply(200, dict(ok=True))
                if op == 'set' and isinstance(body.get('text'), str) and len(body['text']) <= 500:
                    return self.reply(200, bridge('/ime/set', {'text': body['text']}, timeout=3))
                if op == 'submit':
                    data = bridge('/ime/submit', timeout=3); keyboard_close(); return self.reply(200, data)
                if op == 'state': return self.reply(200, bridge('/ime/state', timeout=3))
                return self.reply(400, dict(error='Unknown keyboard action'))
            with control_lock:
                if self.path == '/api/key' and (body.get('key') in KEYS or isinstance(body.get('keys'), list)):
                    names = body['keys'] if isinstance(body.get('keys'), list) else [body['key']]
                    if not 0 < len(names) <= 12 or any(n not in KEYS for n in names):
                        return self.reply(400, dict(error='Unknown key'))
                    key_shell.press([KEYS[n] for n in names])
                elif self.path == '/api/media' and body.get('action') in ('toggle', 'play', 'pause', 'next', 'previous', 'seek', 'skip', 'queue'):
                    ms = body.get('ms')
                    if body['action'] in ('seek', 'skip', 'queue') and (type(ms) is not int or abs(ms) > 10**12):
                        return self.reply(400, dict(error='Invalid position'))
                    media_command(body['action'], ms)
                elif self.path == '/api/launch':
                    launch(str(body.get('package', '')))
                elif self.path == '/api/app':  # short names used by older cached pages
                    legacy = dict(netflix='com.netflix.ninja', prime='com.amazon.amazonvideo.livingroom',
                                  youtube='com.google.android.youtube.tv', spotify='com.spotify.tv.android', hotstar='in.startv.hotstar')
                    launch(legacy.get(body.get('app'), ''))
                elif self.path == '/api/open':
                    open_link(str(body.get('url', '')))
                elif self.path == '/api/search':
                    text = str(body.get('text', '')).strip()
                    if not 0 < len(text) <= 200: return self.reply(400, dict(error='Say or type something to search'))
                    global_search(text)
                elif self.path == '/api/input':
                    switch_input(body.get('port'))
                elif self.path == '/api/action' and body.get('action') in ACTIONS:
                    output = adb(*ACTIONS[body['action']])
                    if 'Error:' in output or 'Exception' in output: raise RuntimeError('TV could not open this settings page')
                elif self.path == '/api/volume':
                    set_volume(body.get('value'))
                elif self.path == '/api/reconnect':
                    ip = discover_tv()
                    if ip: use_address(ip)
                    reconnect()
                    if adb('get-state', timeout=5) != 'device': raise RuntimeError(ASLEEP)
                else: return self.reply(400, dict(error='Unknown action'))
            refresh.set()
            self.reply(200, dict(ok=True))
        except (ValueError, TypeError) as error: self.reply(400, dict(error=str(error) or 'Invalid request'))
        except urllib.error.URLError: self.reply(503, dict(error='The TV companion app is not responding'))
        except Exception as error: self.reply(503, dict(error=str(error)))


def secure_server(address, handler, context, handshake_timeout=10):
    """HTTPS server that does each TLS handshake in that connection's own thread, with a time limit.
    Wrapping the listening socket instead handshakes inside accept(): one phone stalling on a weak
    connection then blocks every other request until a restart."""
    class Server(ThreadingHTTPServer):
        def finish_request(self, request, client_address):
            request.settimeout(handshake_timeout)
            try:
                tls = context.wrap_socket(request, server_side=True)
            except OSError:      # includes ssl.SSLError and the timeout
                return
            try:
                tls.settimeout(None)
                self.RequestHandlerClass(tls, client_address, self)
            finally:
                tls.close()
    return Server(address, handler)


if __name__ == '__main__':
    local_secret()      # create it now so `python3 access.py code` works before any phone is approved
    for worker in (keep_connected, poll_state, keyboard_watchdog, sleep_watch):
        threading.Thread(target=worker, daemon=True).start()
    certs = ROOT / 'certs'
    if (certs / 'remote.crt').exists():
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(certs / 'remote.crt', certs / 'remote.key')
        class SecureHandler(Handler): secure = True
        secure = secure_server((os.environ.get('LISTEN_HOST', '127.0.0.1'), 8443), SecureHandler, context)
        threading.Thread(target=secure.serve_forever, daemon=True).start()
        print('HTTPS remote listening on port 8443', flush=True)
    print('TV remote listening on port 8765', flush=True)
    ThreadingHTTPServer((os.environ.get('LISTEN_HOST', '127.0.0.1'), 8765), Handler).serve_forever()

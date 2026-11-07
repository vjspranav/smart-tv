import re

PACKAGES = {
    'com.netflix.ninja': ('netflix', 'Netflix'),
    'com.amazon.amazonvideo.livingroom': ('prime', 'Prime Video'),
    'com.google.android.youtube.tv': ('youtube', 'YouTube'),
    'com.spotify.tv.android': ('spotify', 'Spotify'),
    'in.startv.hotstar': ('hotstar', 'JioHotstar'),
    'com.google.android.apps.tv.launcherx': ('home', 'Google TV'),
    'com.overdevs.at4k': ('home', 'Home'),
    'com.livingroom.flamingo.launcher': ('home', 'Home'),   # our own launcher
}

TV_PACKAGES = ('com.tcl.tv', 'com.tcl.tvinput')


def parse_inputs(inputs):
    """(ps5_port, active_hdmi_port, input_id) from `dumpsys tv_input`."""
    ps5 = re.search(r'display_name:\s*PlayStation\s*5[^\n]*?port_id:\s*(\d+)', inputs, re.I)
    ps5_port = int(ps5.group(1)) if ps5 else None
    # Consider only active input sessions, not the list of installed input services.
    sessions = inputs.partition('sessionStateMap:')[2].partition('mCallbacks:')[0]
    active_input = re.search(r'(?:inputId|mInputId)\s*[=:]\s*([^\s,}\n]+)', sessions)
    input_id = active_input.group(1) if active_input else None
    hdmi = None
    if input_id:
        hw = re.search(r'/HW(15|16|17)\b', input_id)
        if hw: hdmi = int(hw.group(1)) - 14
        elif '/HDMI100004' in input_id and ps5_port: hdmi = ps5_port
    return ps5_port, hdmi, input_id


def app_for(package, ps5_port=None, hdmi=None):
    """(app id, display name) for a foreground package."""
    if package in TV_PACKAGES:
        if ps5_port is not None and hdmi == ps5_port:
            return 'ps5', 'PlayStation 5'
        return 'tv', 'TV'
    return PACKAGES.get(package, ('home', 'Living Room TV'))


def parse_context(raw):
    focus, _, rest = raw.partition('__MEDIA__')
    media, _, rest = rest.partition('__INPUT__')
    inputs, _, volume_text = rest.partition('__VOLUME__')
    match = re.search(r'(?:mResumedActivity|topResumedActivity)[^\n]*?\bu\d+\s+([\w.]+)/', focus)
    package = match.group(1) if match else None
    app, name = PACKAGES.get(package, ('tv' if package in ('com.tcl.tv', 'com.tcl.tvinput') else 'home', 'TV' if package in ('com.tcl.tv', 'com.tcl.tvinput') else 'Living Room TV'))
    ps5_port, hdmi, input_id = parse_inputs(inputs)
    is_game = package in ('com.tcl.tv', 'com.tcl.tvinput') and ps5_port is not None and hdmi == ps5_port
    if is_game: app, name = 'ps5', 'PlayStation 5'
    title = artist = None
    duration = position = None
    playing = None
    # dumpsys groups session blocks at ownerPid; choose a matching active session only.
    for chunk in re.split(r'(?=\n\s+ownerPid=)', media):
        if not package or not re.search(r'package=' + re.escape(package) + r'(?:\s|$)', chunk): continue
        if not re.search(r'active=true\b', chunk): continue
        state = re.search(r'state=PlaybackState\s*\{state=(\d+),\s*position=(-?\d+)', chunk)
        if state:
            playing = int(state.group(1)) == 3
            position = max(0, int(state.group(2)))
        description = re.search(r'metadata:[^\n]*?description=(.*)', chunk)
        if description:
            parts = [x.strip() for x in description.group(1).strip().split(',')]
            if parts and parts[0] not in ('null', 'None', ''): title = parts[0][:240]
            if len(parts) > 1 and parts[1] not in ('null', 'None', ''): artist = parts[1][:160]
        explicit_title = re.search(r'(?:android\.media\.metadata\.TITLE|METADATA_KEY_TITLE)\s*[=:]\s*([^\n]+)', chunk)
        if explicit_title: title = explicit_title.group(1).strip()[:240]
        explicit_artist = re.search(r'(?:android\.media\.metadata\.ARTIST|METADATA_KEY_ARTIST)\s*[=:]\s*([^\n]+)', chunk)
        if explicit_artist: artist = explicit_artist.group(1).strip()[:160]
        d = re.search(r'(?:android\.media\.metadata\.DURATION|duration)\s*[=:]\s*(\d+)', chunk, re.I)
        if d: duration = int(d.group(1))
        break
    volume = re.search(r'volume is (\d+) in range \[(\d+)\.\.(\d+)\]', volume_text)
    return dict(app=app, app_name=name, package=package, title=title, artist=artist,
                playing=playing, position_ms=position, duration_ms=duration,
                hdmi_port=hdmi, input_id=input_id, ps5_port=ps5_port,
                volume=int(volume.group(1)) if volume else None,
                volume_min=int(volume.group(2)) if volume else 0,
                volume_max=int(volume.group(3)) if volume else 100)


def parse_power(text):
    """'on' when the TV screen is awake (or showing a screensaver), 'off' when asleep, None if unknown."""
    match = re.search(r'mWakefulness=(\w+)', text or '')
    if not match:
        return None
    return 'on' if match.group(1) in ('Awake', 'Dreaming') else 'off'

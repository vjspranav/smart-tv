"""Turn a key file ("KEYCODE_X <ms>" per line) into a monkey script.

One `monkey -f` process replays every key, instead of one `input` JVM per key, so the
replay itself doesn't load the TV while frames are measured.
CLI: python3 keys_to_monkey.py <keysfile>  → prints the script.
"""
import sys

CODES = {'KEYCODE_UNKNOWN': 0, 'KEYCODE_HOME': 3, 'KEYCODE_BACK': 4,
         'KEYCODE_DPAD_UP': 19, 'KEYCODE_DPAD_DOWN': 20, 'KEYCODE_DPAD_LEFT': 21,
         'KEYCODE_DPAD_RIGHT': 22, 'KEYCODE_DPAD_CENTER': 23}


def script(lines):
    events = []
    for line in lines:
        parts = line.split()
        if not parts or parts[0].startswith('#'):
            continue
        if parts[0] not in CODES:
            raise ValueError('unknown key: ' + parts[0])
        code, wait = CODES[parts[0]], int(parts[1]) if len(parts) > 1 else 250
        events += ['DispatchKey(0,0,0,%d,0,0,0,0)' % code,
                   'DispatchKey(0,0,1,%d,0,0,0,0)' % code,
                   'UserWait(%d)' % wait]
    if not events:
        raise ValueError('no keys')
    return 'type= raw events\ncount= %d\nspeed= 1.0\nstart data >>\n%s\n' % (len(events), '\n'.join(events))


if __name__ == '__main__':
    with open(sys.argv[1]) as f:
        sys.stdout.write(script(f.read().splitlines()))

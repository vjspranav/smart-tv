"""Which phones may use the remote. Each approved phone holds a long random token in a cookie; the Pi keeps only
its hash in devices.json (chmod 600, never deployed over). New phones join with a 6-digit code from an approved one.
A widget key (kind "widget") is made from an approved phone for iPhone Shortcuts / Android HTTP Shortcuts: it may
only read the house, run scenes and switch devices (server.WIDGET_ALLOWED).

    python3 access.py code      # print a code for the very first phone (over ssh)
"""
import hashlib, json, os, secrets, sys, threading, time
from pathlib import Path

CODE_TTL = 600          # a code works for 10 minutes
MAX_TRIES = 5           # this many wrong codes cancels every open code
SEEN_EVERY = 3600       # write last-used at most hourly (SD card)
BAD_CODE = "That code didn't work. Ask for a new one."


def _hash(token):
    return hashlib.sha256(token.encode()).hexdigest()


class Access:
    def __init__(self, path):
        self.path = Path(path)
        self.lock = threading.Lock()
        self.codes = {}         # code -> {'expires': t}; kept in memory only
        self.wrong = 0

    def _load(self):
        try:
            return json.loads(self.path.read_text())
        except (OSError, ValueError):
            return []

    def _save(self, devices):
        tmp = self.path.with_suffix('.tmp')
        tmp.write_text(json.dumps(devices, indent=1))
        os.chmod(tmp, 0o600)
        os.replace(tmp, self.path)

    def device_for(self, token):
        if not token:
            return None
        digest = _hash(token)
        with self.lock:
            devices = self._load()
            for d in devices:
                if secrets.compare_digest(d['token_hash'], digest):
                    if time.time() - d.get('last_seen', 0) > SEEN_EVERY:
                        d['last_seen'] = int(time.time())
                        self._save(devices)
                    return d
        return None

    def new_code(self):
        with self.lock:
            now = time.time()
            self.codes = {c: v for c, v in self.codes.items() if v['expires'] > now}
            code = f'{secrets.randbelow(1_000_000):06d}'
            while code in self.codes:
                code = f'{secrets.randbelow(1_000_000):06d}'
            self.codes[code] = dict(expires=now + CODE_TTL)
            self.wrong = 0
            return code

    def redeem(self, code, name):
        name = (name or '').strip()[:40]
        if not name:
            raise ValueError('Give this phone a name')
        with self.lock:
            entry = self.codes.pop(str(code).strip(), None)
            if not entry or entry['expires'] < time.time():
                self.wrong += 1
                if self.wrong >= MAX_TRIES:
                    self.codes.clear()       # someone is guessing: every open code stops working
                raise ValueError(BAD_CODE)
            token = secrets.token_urlsafe(32)
            device = dict(id=secrets.token_hex(4), name=name, token_hash=_hash(token),
                          created=int(time.time()), last_seen=int(time.time()))
            devices = self._load()
            devices.append(device)
            self._save(devices)
            return token, device

    def new_key(self, name):
        name = (name or '').strip()[:40]
        if not name:
            raise ValueError('Give this widget key a name')
        with self.lock:
            token = secrets.token_urlsafe(32)
            device = dict(id=secrets.token_hex(4), name=name, kind='widget', token_hash=_hash(token),
                          created=int(time.time()), last_seen=0)
            devices = self._load()
            devices.append(device)
            self._save(devices)
            return token, device

    def devices(self):
        return [{'kind': 'phone', **{k: v for k, v in d.items() if k != 'token_hash'}} for d in self._load()]

    def remove(self, device_id):
        with self.lock:
            self._save([d for d in self._load() if d['id'] != device_id])

    def rename(self, device_id, name):
        name = (name or '').strip()[:40]
        if not name:
            raise ValueError('Give this phone a name')
        with self.lock:
            devices = self._load()
            for d in devices:
                if d['id'] == device_id: d['name'] = name
            self._save(devices)


if __name__ == '__main__' and sys.argv[1:] == ['code']:
    # The server keeps codes in memory, so ask the running server for one (localhost only).
    import urllib.request
    port = int(os.environ.get('PORT', 8765))
    secret = (Path(__file__).resolve().parent / 'runtime' / 'local-secret').read_text().strip()   # only this user can read it
    request = urllib.request.Request(f'http://127.0.0.1:{port}/api/local-code', data=b'{}', method='POST',
                                     headers={'X-TV-Remote': '1', 'Content-Type': 'application/json',
                                              'X-Local-Secret': secret, 'Host': '127.0.0.1'})
    with urllib.request.urlopen(request, timeout=5) as response:
        print('Code for the new phone (10 minutes):', json.load(response)['code'])

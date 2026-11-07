"""Widget keys (iPhone Shortcuts, Android HTTP Shortcuts) and Android long-press shortcuts.
Run: python3 -m unittest test_widgets -v"""
import json, tempfile, threading, unittest, urllib.error, urllib.request
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path

import access
import server

ROUTINES = {'routines': [{'id': 'good-night', 'name': 'Good night', 'icon': 'moon', 'steps': []},
                         {'id': 'movie', 'name': 'Movie', 'icon': 'tv', 'steps': []}],
            'groups': [], 'builtin': [{'id': 'living-lights', 'name': 'Living lights', 'devices': ['living-light']}]}


class Upstream(BaseHTTPRequestHandler):
    """Stands in for the smart-home service."""
    def log_message(self, *args): pass

    def answer(self, data):
        body = json.dumps(data).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.answer(ROUTINES if self.path == '/api/home/routines' else dict(path=self.path))

    def do_POST(self):
        self.rfile.read(int(self.headers.get('Content-Length') or 0))
        self.answer(dict(ok=True, path=self.path))


def call(port, path, body=None, bearer=None, cookie=None):
    headers = {'X-TV-Remote': '1', 'Content-Type': 'application/json'}
    if bearer: headers['Authorization'] = f'Bearer {bearer}'
    if cookie: headers['Cookie'] = f'remote_device={cookie}'
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=data, method='POST' if data else 'GET', headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as r: return r.status, r.read()
    except urllib.error.HTTPError as e: return e.code, e.read()


class WidgetKeys(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dir = tempfile.TemporaryDirectory()
        cls.saved = server.ACCESS, server.HOME, server.HOME_PORT
        server.ACCESS = access.Access(Path(cls.dir.name) / 'devices.json')
        cls.up = ThreadingHTTPServer(('127.0.0.1', 0), Upstream)
        threading.Thread(target=cls.up.serve_forever, daemon=True).start()
        server.HOME_PORT = cls.up.server_address[1]
        server.HOME = f'http://127.0.0.1:{server.HOME_PORT}'
        cls.http = ThreadingHTTPServer(('127.0.0.1', 0), server.Handler)
        threading.Thread(target=cls.http.serve_forever, daemon=True).start()
        cls.port = cls.http.server_address[1]
        cls.phone, _ = server.ACCESS.redeem(server.ACCESS.new_code(), 'VJS OnePlus')
        cls.key, cls.widget = server.ACCESS.new_key('VJS iPhone widgets')

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown(); cls.up.shutdown(); cls.up.server_close()
        server.ACCESS, server.HOME, server.HOME_PORT = cls.saved
        cls.dir.cleanup()

    def test_key_is_a_widget_and_stored_hashed(self):
        self.assertEqual(self.widget['kind'], 'widget')
        self.assertNotIn(self.key, (Path(self.dir.name) / 'devices.json').read_text())
        listed = {d['id']: d for d in server.ACCESS.devices()}
        self.assertEqual(listed[self.widget['id']]['kind'], 'widget')

    def test_widget_key_name_required(self):
        with self.assertRaises(ValueError):
            server.ACCESS.new_key(' ')

    def test_phone_token_works_as_bearer(self):
        status, _ = call(self.port, '/api/version', bearer=self.phone)
        self.assertEqual(status, 200)

    def test_widget_key_can_run_scenes_and_switch_devices(self):
        self.assertEqual(call(self.port, '/api/home/state', bearer=self.key)[0], 200)
        self.assertEqual(call(self.port, '/api/home/routines', bearer=self.key)[0], 200)
        for path, body in (('/api/home/run', {'routine': 'good-night'}), ('/api/home/toggle', {'target': 'living-lights'}),
                           ('/api/home/set', {'device': 'living-ac', 'changes': {'power': True}})):
            status, reply = call(self.port, path, body, bearer=self.key)
            self.assertEqual(status, 200, path)
            self.assertEqual(json.loads(reply)['path'], path)

    def test_widget_key_cannot_do_anything_else(self):
        for path, body in (('/', None), ('/api/context', None), ('/api/devices', None), ('/api/devices', {'action': 'code'}),
                           ('/api/key', {'key': 'ok'}), ('/api/home/routines', {'routines': []})):
            status, _ = call(self.port, path, body, bearer=self.key)
            self.assertEqual(status, 403, (path, body))
            status, _ = call(self.port, path, body, cookie=self.key)
            self.assertEqual(status, 403, ('cookie', path, body))

    def test_wrong_key_is_401(self):
        self.assertEqual(call(self.port, '/api/home/state', bearer='nonsense')[0], 401)

    def test_phones_sheet_makes_a_key(self):
        status, reply = call(self.port, '/api/devices', {'action': 'key', 'name': 'Snehal widgets'}, cookie=self.phone)
        self.assertEqual(status, 200)
        data = json.loads(reply)
        self.assertEqual(server.ACCESS.device_for(data['key'])['kind'], 'widget')
        self.assertEqual(call(self.port, '/api/home/state', bearer=data['key'])[0], 200)

    def test_manifest_lists_scenes_for_approved_phones_only(self):
        status, body = call(self.port, '/manifest.webmanifest', cookie=self.phone)
        self.assertEqual(status, 200)
        shortcuts = json.loads(body)['shortcuts']
        self.assertEqual([s['name'] for s in shortcuts][:3], ['Good night', 'Movie', 'Living lights'])
        self.assertEqual(shortcuts[0]['url'], '/?run=good-night')
        self.assertEqual(shortcuts[2]['url'], '/?toggle=living-lights')
        self.assertLessEqual(len(shortcuts), 4)
        status, body = call(self.port, '/manifest.webmanifest')
        self.assertEqual(status, 200)
        self.assertNotIn('Good night', body.decode())


if __name__ == '__main__':
    unittest.main()

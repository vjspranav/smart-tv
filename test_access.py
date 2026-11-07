"""Approved phones only. Run: python3 -m unittest test_access -v"""
import json, os, socket, stat, tempfile, threading, time, unittest, urllib.error, urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path

import access


class AccessTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.path = Path(self.dir.name) / 'devices.json'
        self.access = access.Access(self.path)

    def tearDown(self):
        self.dir.cleanup()

    def test_code_redeems_once(self):
        code = self.access.new_code()
        self.assertRegex(code, r'^\d{6}$')
        token, device = self.access.redeem(code, "VJS's phone")
        self.assertEqual(device['name'], "VJS's phone")
        self.assertEqual(self.access.device_for(token)['id'], device['id'])
        with self.assertRaises(ValueError) as error:
            self.access.redeem(code, 'Again')
        self.assertEqual(str(error.exception), "That code didn't work. Ask for a new one.")

    def test_tokens_stored_hashed_and_private(self):
        token, _ = self.access.redeem(self.access.new_code(), 'Phone')
        text = self.path.read_text()
        self.assertNotIn(token, text)
        self.assertEqual(stat.S_IMODE(os.stat(self.path).st_mode), 0o600)

    def test_code_expires(self):
        code = self.access.new_code()
        self.access.codes[code]['expires'] = time.time() - 1
        with self.assertRaises(ValueError) as error:
            self.access.redeem(code, 'Late')
        self.assertEqual(str(error.exception), "That code didn't work. Ask for a new one.")

    def test_five_wrong_tries_cancel_codes(self):
        code = self.access.new_code()
        wrong = '000000' if code != '000000' else '111111'
        for _ in range(access.MAX_TRIES):
            with self.assertRaises(ValueError):
                self.access.redeem(wrong, 'Guess')
        with self.assertRaises(ValueError) as error:
            self.access.redeem(code, 'Real')
        self.assertEqual(str(error.exception), "That code didn't work. Ask for a new one.")

    def test_remove_and_rename(self):
        token, device = self.access.redeem(self.access.new_code(), 'Old name')
        self.access.rename(device['id'], 'New name')
        self.assertEqual(self.access.devices()[0]['name'], 'New name')
        self.access.remove(device['id'])
        self.assertIsNone(self.access.device_for(token))
        self.assertIsNone(self.access.device_for('nonsense'))

    def test_name_required(self):
        with self.assertRaises(ValueError) as error:
            self.access.redeem(self.access.new_code(), '  ')
        self.assertEqual(str(error.exception), 'Give this phone a name')


def request(port, path, body=None, cookie=None, host=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {'X-TV-Remote': '1', 'Content-Type': 'application/json'}
    if cookie: headers['Cookie'] = f'remote_device={cookie}'
    if host: headers['Host'] = host
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=data, method='POST' if data else 'GET', headers=headers)
    opener = urllib.request.build_opener(NoRedirect)
    try:
        r = opener.open(req, timeout=10)
        return r.status, dict(r.headers), r.read()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs): return None


class GateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import server
        cls.server = server
        cls.dir = tempfile.TemporaryDirectory()
        server.ACCESS = access.Access(Path(cls.dir.name) / 'devices.json')
        cls.http = ThreadingHTTPServer(('127.0.0.1', 0), server.Handler)
        threading.Thread(target=cls.http.serve_forever, daemon=True).start()
        cls.port = cls.http.server_address[1]
        cls.token, _ = server.ACCESS.redeem(server.ACCESS.new_code(), 'Test phone')

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown(); cls.dir.cleanup()

    def test_unpaired_api_is_401(self):
        for path in ('/api/context', '/api/home/state', '/api/apps', '/home.js'):
            status, _, _ = request(self.port, path)
            self.assertEqual(status, 401, path)
        status, _, _ = request(self.port, '/api/key', {'key': 'ok'})
        self.assertEqual(status, 401)

    def test_unpaired_page_is_pairing_page(self):
        status, headers, body = request(self.port, '/')
        self.assertEqual(status, 200)
        self.assertIn(b"isn't connected to the home yet", body)

    def test_open_paths_without_cookie(self):
        for path in ('/pair.js', '/manifest.webmanifest', '/sw.js', '/icon-192.png', '/setup', '/api/ping', '/local-ca.crt'):
            status, _, _ = request(self.port, path)
            self.assertEqual(status, 200, path)

    def test_paired_cookie_gets_through(self):
        status, _, body = request(self.port, '/', cookie=self.token)
        self.assertEqual(status, 200)
        self.assertNotIn(b"isn't connected to the home yet", body)
        status, _, _ = request(self.port, '/api/version', cookie=self.token)
        self.assertEqual(status, 200)

    def test_pair_sets_cookie(self):
        code = self.server.ACCESS.new_code()
        status, headers, body = request(self.port, '/api/pair', {'code': code, 'name': 'Snehal phone'})
        self.assertEqual(status, 200)
        cookie = headers['Set-Cookie']
        for flag in ('remote_device=', 'HttpOnly', 'Secure', 'SameSite=Lax', 'Max-Age=157680000', 'Path=/'):
            self.assertIn(flag, cookie)
        status, _, body = request(self.port, '/api/pair', {'code': code, 'name': 'Again'})
        self.assertEqual(status, 400)
        self.assertEqual(json.loads(body)['error'], "That code didn't work. Ask for a new one.")

    def test_ping_renews_an_old_cookie_as_lax(self):
        # Android's installed app opens from the home screen as a cross-site visit: Chrome leaves a Strict cookie out
        status, headers, _ = request(self.port, '/api/ping', cookie=self.token)
        self.assertEqual(status, 200)
        self.assertIn(f'remote_device={self.token};', headers.get('Set-Cookie', ''))
        self.assertIn('SameSite=Lax', headers['Set-Cookie'])
        self.assertIs(json.loads(_)['paired'], True)          # the pairing page reloads into the app when it sees this
        for cookie in (None, 'not-a-phone'):
            _, headers, body = request(self.port, '/api/ping', cookie=cookie)
            self.assertNotIn('Set-Cookie', headers)
            self.assertIs(json.loads(body)['paired'], False)

    def test_devices_list_add_remove(self):
        status, _, body = request(self.port, '/api/devices', cookie=self.token)
        data = json.loads(body)
        self.assertEqual(status, 200)
        self.assertTrue(any(d['this'] for d in data['devices']))
        status, _, body = request(self.port, '/api/devices', {'action': 'code'}, cookie=self.token)
        self.assertRegex(json.loads(body)['code'], r'^\d{6}$')
        other, device = self.server.ACCESS.redeem(self.server.ACCESS.new_code(), 'Temp')
        status, _, _ = request(self.port, '/api/devices', {'action': 'remove', 'id': device['id']}, cookie=self.token)
        self.assertEqual(status, 200)
        status, _, _ = request(self.port, '/api/context', cookie=other)
        self.assertEqual(status, 401)

    def test_local_code_needs_the_pi_only_secret(self):
        status, _, _ = request(self.port, '/api/local-code', {})          # what a phone on the Wi-Fi would get
        self.assertEqual(status, 403)
        req = urllib.request.Request(f'http://127.0.0.1:{self.port}/api/local-code', data=b'{}', method='POST',
                                     headers={'X-TV-Remote': '1', 'X-Local-Secret': self.server.local_secret()})
        with urllib.request.urlopen(req, timeout=5) as r:
            self.assertRegex(json.load(r)['code'], r'^\d{6}$')

    def test_lan_name_redirects_to_canonical(self):
        status, headers, _ = request(self.port, '/?x=1', host='remote.local')
        self.assertEqual(status, 301)
        self.assertEqual(headers['Location'], 'https://192.168.0.48/?x=1')
        status, _, _ = request(self.port, '/setup', host='remote.local')      # CA install still works over plain HTTP
        self.assertEqual(status, 200)

    def test_tailscale_address_stays_on_tailscale(self):
        # A phone that only has the Pi shared to it reaches it at its Tailscale IP: keep it there (HTTPS for the cookie)
        status, headers, _ = request(self.port, '/', host='100.110.26.84')
        self.assertEqual(status, 301)
        self.assertEqual(headers['Location'], 'https://100.110.26.84/')


if __name__ == '__main__':
    unittest.main()

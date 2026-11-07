"""The /api/home/* proxy to the smart-home service.  Run: python3 -m unittest test_home_proxy -v"""
import json, os, socket, threading, time, unittest, urllib.error, urllib.request
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler


def free_port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]


UPSTREAM = free_port()
os.environ['SMART_HOME_PORT'] = str(UPSTREAM)
import server  # noqa: E402  (reads SMART_HOME_PORT at import)
import access, tempfile  # noqa: E402
_dir = tempfile.TemporaryDirectory()
ACCESS = access.Access(f'{_dir.name}/devices.json')
TOKEN, _ = ACCESS.redeem(ACCESS.new_code(), 'Test phone')   # these tests run as an approved phone


class Stub(BaseHTTPRequestHandler):
    def log_message(self, *args): pass

    def answer(self, code, data):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.answer(200, dict(path=self.path))

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        if body.get('bad'): return self.answer(400, dict(error='Temperature must be between 16° and 30°'))
        self.answer(200, dict(got=body, header=self.headers.get('X-TV-Remote')))


def request(port, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=data, method='POST' if data else 'GET',
                                 headers={'X-TV-Remote': '1', 'Content-Type': 'application/json', 'Cookie': f'remote_device={TOKEN}'})
    try:
        with urllib.request.urlopen(req, timeout=10) as r: return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read())


class ProxyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        server.ACCESS = ACCESS
        cls.remote = ThreadingHTTPServer(('127.0.0.1', 0), server.Handler)
        threading.Thread(target=cls.remote.serve_forever, daemon=True).start()
        cls.port = cls.remote.server_address[1]

    @classmethod
    def tearDownClass(cls):
        cls.remote.shutdown()

    def with_upstream(self):
        up = ThreadingHTTPServer(('127.0.0.1', UPSTREAM), Stub)
        threading.Thread(target=up.serve_forever, daemon=True).start()
        self.addCleanup(lambda: (up.shutdown(), up.server_close()))

    def test_proxy_get(self):
        self.with_upstream()
        status, data = request(self.port, '/api/home/wait?after=3')
        self.assertEqual((status, data), (200, dict(path='/api/home/wait?after=3')))

    def test_proxy_post_passes_status(self):
        self.with_upstream()
        status, data = request(self.port, '/api/home/set', dict(device='vjs-ac', changes=dict(power=True)))
        self.assertEqual(status, 200)
        self.assertEqual(data['header'], '1')
        self.assertEqual(data['got']['device'], 'vjs-ac')
        status, data = request(self.port, '/api/home/set', dict(bad=True))
        self.assertEqual(status, 400)
        self.assertEqual(data['error'], 'Temperature must be between 16° and 30°')

    def test_proxy_upstream_down(self):
        started = time.monotonic()
        status, data = request(self.port, '/api/home/state')
        self.assertEqual((status, data), (503, dict(error="Home controls aren't reachable")))
        self.assertLess(time.monotonic() - started, 2)

    def test_version_changes_with_page_files(self):
        status, data = request(self.port, '/api/version')
        self.assertEqual(status, 200)
        self.assertRegex(data['version'], r'^[0-9a-f]{12}$')
        self.assertEqual(data['version'], server.page_version())

    def test_tv_routes_untouched(self):
        status, _ = request(self.port, '/api/nothing-here')
        self.assertEqual(status, 404)


if __name__ == '__main__':
    unittest.main()

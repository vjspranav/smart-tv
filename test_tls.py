import socket, ssl, subprocess, tempfile, threading, time, unittest, urllib.request
from pathlib import Path

import server


class StalledHandshake(unittest.TestCase):
    """A phone on a bad connection that opens a connection and goes quiet must not block everyone else."""

    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        crt, key = Path(cls.tmp.name) / 'c.crt', Path(cls.tmp.name) / 'c.key'
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1', '-subj', '/CN=127.0.0.1',
                        '-keyout', key, '-out', crt], check=True, capture_output=True)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(crt, key)
        class SecureHandler(server.Handler): secure = True
        cls.httpd = server.secure_server(('127.0.0.1', 0), SecureHandler, context, handshake_timeout=1)
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()
        cls.port = cls.httpd.server_address[1]

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()
        cls.tmp.cleanup()

    def ping(self):
        insecure = ssl.create_default_context()
        insecure.check_hostname, insecure.verify_mode = False, ssl.CERT_NONE
        with urllib.request.urlopen(f'https://127.0.0.1:{self.port}/api/ping', context=insecure, timeout=3) as r:
            return r.status

    def test_ping_answers_while_another_client_stalls(self):
        stalled = socket.create_connection(('127.0.0.1', self.port))   # connects, never starts the handshake
        try:
            time.sleep(0.2)
            started = time.monotonic()
            self.assertEqual(self.ping(), 200)
            self.assertLess(time.monotonic() - started, 2)
        finally:
            stalled.close()

    def test_stalled_client_is_dropped(self):
        stalled = socket.create_connection(('127.0.0.1', self.port))
        stalled.settimeout(4)
        try:
            self.assertEqual(stalled.recv(1), b'')   # the server gives up on it after the handshake timeout
        finally:
            stalled.close()


if __name__ == '__main__':
    unittest.main()

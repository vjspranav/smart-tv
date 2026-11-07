"""The house view's floor plan (house-plan.json) and the files the house view needs.
Run: python3 -m unittest test_house_plan -v"""
import json, tempfile, threading, unittest, urllib.error, urllib.request
from collections import Counter
from http.server import ThreadingHTTPServer
from pathlib import Path

import access

ROOT = Path(__file__).resolve().parent
SERVICE = ROOT.parent / 'smart-home' / 'devices.json'


def plan():
    return json.loads((ROOT / 'house-plan.json').read_text())


def on_wall(span, wall, slack=0.01):
    return (wall['axis'] == span['axis'] and abs(wall['at'] - span['at']) < slack
            and span['from'] >= wall['from'] - slack and span['to'] <= wall['to'] + slack)


class Plan(unittest.TestCase):
    def test_every_smart_home_device_placed_once(self):
        if not SERVICE.exists():
            self.skipTest('smart-home/devices.json is not next to tv-remote (as on the Pi)')
        data = json.loads(SERVICE.read_text())
        service = [d['id'] for d in (data['devices'] if isinstance(data, dict) else data)]
        placed = Counter(d['id'] for d in plan()['devices'])
        self.assertEqual(placed.pop('tv'), 1)
        self.assertEqual(placed, Counter(service))
        self.assertEqual(len(plan()['devices']), 17)

    def test_wall_devices_sit_on_walls(self):
        p = plan()
        for d in p['devices']:
            if 'wall' not in d: continue
            self.assertIn(d['wall']['face'], (1, -1), d['id'])
            self.assertTrue(any(on_wall(d['wall'], w) for w in p['walls']), d['id'])

    def test_features_inside_their_wall(self):
        for w in plan()['walls']:
            for f in w.get('features', []):
                self.assertTrue(w['from'] <= f['from'] < f['to'] <= w['to'], (w, f))
                if f['kind'] == 'door': self.assertIn(f.get('swing'), (1, -1), (w, f))

    def test_known_answers(self):
        p = plan()
        ac = next(d for d in p['devices'] if d['id'] == 'living-ac')
        self.assertEqual(ac['wall'], dict(axis='v', at=28, **{'from': 31.2, 'to': 34.8}, face=-1))
        doors = [(w, f) for w in p['walls'] for f in w.get('features', []) if w['axis'] == 'v' and w['at'] == 40 and f['kind'] == 'door']
        self.assertEqual([(f['from'], f['to'], f['swing']) for _, f in doors], [(8, 10.6, 1)])
        self.assertEqual(len(p['cupboards']), 4)
        rooms = {r['id']: r['rect'] for r in p['rooms'] if 'rect' in r}
        self.assertEqual(rooms['kitchen'], [28, 0, 12, 13])                 # 12 ft kitchen, 5 ft balcony (user, 2026-10-22)
        self.assertEqual(rooms['balc2'], [40, 0, 5, 13])
        self.assertFalse(any(w['axis'] == 'v' and w['at'] == 37 for w in p['walls']))
        self.assertTrue(any(w['axis'] == 'v' and w['at'] == 28 and w['from'] == 7 and w['to'] == 15.6 for w in p['walls']))

    def test_living_room_matches_photos(self):
        # agreed in smart-home/docs/house-mockups/living-v2.html
        p = plan()
        f = {x['name']: x for x in p['furniture']}
        self.assertEqual(f['TV unit']['box'][1:4:2], [29.6, 37.4])
        self.assertEqual(f['shoe rack']['box'][1:4:2], [37.6, 39.4])
        sofa = [x for x in p['furniture'] if x.get('model') == 'sofa']
        self.assertEqual(len(sofa), 2)
        self.assertTrue(any(x['box'][2] >= 27.7 and x['facing'] == '-x' for x in sofa))        # long part on the AC wall
        self.assertTrue(any(x['box'][3] >= 39 and x['facing'] == '-y' for x in sofa))          # short part by the door wall
        for name in ('sideboard', 'wash basin'):                                                # the kitchen pillar wall
            self.assertGreaterEqual(f[name]['box'][2], 27.7, name)
            self.assertTrue(7 <= f[name]['box'][1] and f[name]['box'][3] <= 15.6, name)
        for name in ('recliner', 'coffee table', 'bean bag', 'study desk', 'arc lamp', 'narrow table', 'bookshelf', 'folding chair'):
            self.assertIn(name, f)
        self.assertFalse(any(x['name'] in ('curtains', 'table leg') for x in p['furniture']))
        dev = {d['id']: d for d in p['devices']}
        self.assertEqual((dev['tv']['wall']['from'], dev['tv']['wall']['to']), (31.2, 35.8))
        self.assertEqual((dev['living-light']['wall']['from'], dev['living-light']['wall']['to']), (31.5, 35.5))
        self.assertEqual(dev['living-lamp']['at'], [15.2, 28.8])

    def test_models_are_known(self):
        known = {'tv-unit', 'shoe-rack', 'desk', 'sofa', 'table', 'recliner', 'beanbag', 'arc-lamp', 'armchair',
                 'drawer-table', 'chair', 'shelf', 'basin', 'sideboard', 'folding-chair', 'record-player'}
        for x in plan()['furniture']:
            if 'model' in x: self.assertIn(x['model'], known, x['name'])
            if 'facing' in x: self.assertIn(x['facing'], ('+x', '-x', '+y', '-y'), x['name'])

    def test_walk_starts_on_open_floor(self):
        p = plan(); s = p['start']
        for x in p['furniture']:
            x1, y1, x2, y2 = x['box'][:4]
            self.assertFalse(x1 - 0.8 <= s['x'] <= x2 + 0.8 and y1 - 0.8 <= s['y'] <= y2 + 0.8, x['name'])

    def test_curtains_and_balcony(self):
        p = plan()
        c = p['curtains']['wall']
        wall = next(w for w in p['walls'] if on_wall(c, w))
        self.assertTrue(any(f['kind'] == 'slider' and f['from'] == c['from'] and f['to'] == c['to'] for f in wall['features']))
        self.assertTrue(any(w['axis'] == 'h' and w['at'] == 0 and w['from'] == 14 and w.get('parapet') for w in p['walls']))
        self.assertEqual(next(r for r in p['rooms'] if r['id'] == 'balc1')['floor'], 'deck')

    def test_easter_eggs(self):
        p = plan()
        eggs = [x['egg'] for x in p['furniture'] if 'egg' in x]
        self.assertEqual(sorted(eggs), sorted(['record', 'beanbag', 'recliner', 'folding-chair', 'shelf', 'arc-lamp']))
        f = {x['name']: x for x in p['furniture']}
        rec, side = f['record player']['box'], f['sideboard']['box']
        self.assertEqual(rec[4], side[5])                                                     # sits on the sideboard
        self.assertTrue(side[0] <= rec[0] and rec[2] <= side[2] and side[1] <= rec[1] and rec[3] <= side[3])
        self.assertIsInstance(p['games'], list)


class Served(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        global server
        import server       # here, not at the top: test_home_proxy sets SMART_HOME_PORT before server is first imported
        cls.dir = tempfile.TemporaryDirectory()
        cls.saved = server.ACCESS
        server.ACCESS = access.Access(Path(cls.dir.name) / 'devices.json')
        cls.http = ThreadingHTTPServer(('127.0.0.1', 0), server.Handler)
        threading.Thread(target=cls.http.serve_forever, daemon=True).start()
        cls.port = cls.http.server_address[1]
        cls.token, _ = server.ACCESS.redeem(server.ACCESS.new_code(), 'Test phone')

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown(); server.ACCESS = cls.saved; cls.dir.cleanup()

    def get(self, path, cookie=None):
        headers = {'Cookie': f'remote_device={cookie}'} if cookie else {}
        try:
            with urllib.request.urlopen(urllib.request.Request(f'http://127.0.0.1:{self.port}{path}', headers=headers), timeout=10) as r:
                return r.status, r.headers.get('Content-Type'), r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers.get('Content-Type'), e.read()

    def test_record_served_to_approved_phones_only(self):
        self.assertEqual(self.get('/easter/record.mp3')[0], 401)
        status, kind, body = self.get('/easter/record.mp3', self.token)
        self.assertEqual(status, 200)
        self.assertEqual(kind, 'audio/mpeg')
        self.assertGreater(len(body), 100000)

    def test_served_to_approved_phones_only(self):
        self.assertEqual(self.get('/house-plan.json')[0], 401)
        status, kind, body = self.get('/house-plan.json', self.token)
        self.assertEqual(status, 200)
        self.assertTrue(kind.startswith('application/json'))
        self.assertEqual(json.loads(body)['size'], [45, 41])


class VendorFiles(Served):
    """three.js is served from the Pi (never a CDN), only to approved phones, and only the files that exist."""
    def test_vendor_served_to_approved_phones(self):
        self.assertEqual(self.get('/vendor/three/three.module.min.js')[0], 401)
        status, kind, _ = self.get('/vendor/three/three.module.min.js', self.token)
        self.assertEqual(status, 200)
        self.assertTrue(kind.startswith('application/javascript'))
        self.assertEqual(self.get('/vendor/three/addons/controls/OrbitControls.js', self.token)[0], 200)

    def test_vendor_paths_are_strict(self):
        for path in ('/vendor/three/../server.py', '/vendor/three/addons/../../server.py', '/vendor/three/x.py',
                     '/vendor/three/addons/controls/Missing.js', '/vendor/three/%2e%2e/server.py'):
            self.assertEqual(self.get(path, self.token)[0], 404, path)

    def test_addons_need_no_import_map(self):
        # the page's CSP blocks inline scripts, so there's no import map: add-ons import three by its path
        import re
        for f in (ROOT / 'vendor/three/addons').rglob('*.js'):
            for spec in re.findall(r"from '([^']+)'", f.read_text()):
                self.assertTrue(spec.startswith('.') or spec == '/vendor/three/three.module.min.js', (f.name, spec))

    def test_vendor_version(self):
        _, _, body = self.get('/vendor/three/three.module.min.js', self.token)
        self.assertIn(b'"160"', body[:4000] + body[-200000:])


if __name__ == '__main__':
    unittest.main()

import os
import unittest

import gfxinfo_parse as g

HERE = os.path.dirname(os.path.abspath(__file__))


def sample(name):
    with open(os.path.join(HERE, 'testdata', name)) as f:
        return f.read()


def synthetic(janky_pct, p95):
    return ('Total frames rendered: 200\n'
            f'Janky frames: 1 ({janky_pct}%)\n'
            'Janky frames (legacy): 90 (45.00%)\n'
            '50th percentile: 8ms\n90th percentile: 12ms\n'
            f'95th percentile: {p95}ms\n99th percentile: 20ms\n'
            '95th gpu percentile: 99ms\n')


class ParseGfx(unittest.TestCase):
    def test_parses_sample(self):
        r = g.parse_gfx(sample('gfxinfo-sample.txt'))
        self.assertEqual(r, {'frames': 147, 'janky': 33, 'janky_pct': 22.45,
                             'p50': 27, 'p90': 81, 'p95': 113, 'p99': 950})

    def test_ignores_legacy_and_gpu_lines(self):
        r = g.parse_gfx(synthetic('0.50', 16))
        self.assertEqual((r['janky_pct'], r['p95']), (0.5, 16))

    def test_pss(self):
        self.assertEqual(g.parse_pss_kb(sample('meminfo-sample.txt')), 162642)

    def test_pass_flag(self):
        self.assertTrue(g.result('x', g.parse_gfx(synthetic('0.50', 16)), 1)['pass'])
        self.assertFalse(g.result('x', g.parse_gfx(synthetic('0.50', 17)), 1)['pass'])
        self.assertFalse(g.result('x', g.parse_gfx(synthetic('1.00', 16)), 1)['pass'])

    def test_zero_frames_raise(self):
        # gfxinfo reports 4950ms percentiles when nothing rendered; that is not a measurement
        with self.assertRaises(ValueError):
            g.parse_gfx(synthetic('0.00', 4950).replace('rendered: 200', 'rendered: 0'))

    def test_missing_fields_raise(self):
        with self.assertRaises(ValueError):
            g.parse_gfx('')
        with self.assertRaises(ValueError):
            g.parse_pss_kb('')


if __name__ == '__main__':
    unittest.main()

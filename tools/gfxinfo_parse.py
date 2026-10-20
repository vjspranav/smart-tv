"""Parse `dumpsys gfxinfo` / `dumpsys meminfo` output into one perf result line.

CLI: python3 gfxinfo_parse.py <gfx.txt> <meminfo.txt> <label>  → prints one JSON line.
Pass = under 1% janky frames and 95th-percentile frame time at or below 16 ms.
"""
import json
import re
import sys


def _num(pattern, text):
    m = re.search(pattern, text, re.M)
    if not m:
        raise ValueError('gfxinfo: missing ' + pattern)
    return m


def parse_gfx(text):
    janky = _num(r'^Janky frames: (\d+) \(([\d.]+)%\)', text)
    out = {'frames': int(_num(r'^Total frames rendered: (\d+)', text).group(1)),
           'janky': int(janky.group(1)), 'janky_pct': float(janky.group(2))}
    if out['frames'] == 0:
        raise ValueError('gfxinfo: no frames rendered')
    for p in (50, 90, 95, 99):
        out['p%d' % p] = int(_num(r'^%dth percentile: (\d+)ms' % p, text).group(1))
    return out


def parse_pss_kb(text):
    return int(_num(r'TOTAL PSS:\s+(\d+)', text).group(1))


def result(label, gfx, pss_kb):
    r = {'label': label}
    r.update(gfx)
    r['pss_kb'] = pss_kb
    r['pass'] = gfx['janky_pct'] < 1.0 and gfx['p95'] <= 16
    return r


if __name__ == '__main__':
    gfx_path, mem_path, label = sys.argv[1:4]
    with open(gfx_path) as f:
        gfx = parse_gfx(f.read())
    with open(mem_path) as f:
        pss = parse_pss_kb(f.read())
    print(json.dumps(result(label, gfx, pss)))

// The house view's plain rules, without three.js. Run: node --test test_house_logic.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { daylight, wallPieces, standing, isTap, glideLength, reconcile, loadPlace, savePlace, curtainPanels, walkGrid, findPath, slide } from './house-logic.js';
import { readFileSync } from 'node:fs';

const at = hm => { const [h, m] = hm.split(':').map(Number); const d = new Date(2026, 9, 21, h, m); return d; };

test('daylight follows sunrise and sunset with a dusk blend', () => {
  assert.equal(daylight(at('13:00')), 1);
  assert.equal(daylight(at('23:00')), 0);
  assert.equal(daylight(at('18:20')), 0.5);
  assert.equal(daylight(at('06:15')), 0.5);
  assert.equal(daylight(at('17:35')), 1);
  assert.equal(daylight(at('19:05')), 0);
});

test('wallPieces splits a wall around a window', () => {
  const pieces = wallPieces({ axis: 'h', at: 0, from: 0, to: 14, features: [{ kind: 'window', from: 4, to: 10 }] }, 9);
  const key = p => `${p.from}-${p.to} ${p.kind} ${p.y1}-${p.y2}`;
  assert.deepEqual(pieces.map(key).sort(), ['0-4 wall 0-9', '10-14 wall 0-9', '4-10 glass 3-7', '4-10 wall 0-3', '4-10 wall 7-9'].sort());
});

test('wallPieces: doors, glass doors and openings keep a lintel; a railing is one low piece', () => {
  const door = wallPieces({ from: 0, to: 4, features: [{ kind: 'door', from: 1, to: 3, swing: 1 }] }, 9);
  assert.deepEqual(door.filter(p => p.from === 1).map(p => [p.kind, p.y1, p.y2]), [['wall', 7, 9]]);
  const glass = wallPieces({ from: 0, to: 4, features: [{ kind: 'glass', from: 1, to: 3 }] }, 9);
  assert.deepEqual(glass.filter(p => p.from === 1).map(p => [p.kind, p.y1, p.y2]).sort(), [['glass', 0, 7], ['wall', 7, 9]]);
  const open = wallPieces({ from: 0, to: 4, features: [{ kind: 'open', from: 1, to: 3 }] }, 9);
  assert.deepEqual(open.filter(p => p.from === 1).map(p => [p.kind, p.y1, p.y2]), [['wall', 7, 9]]);
  assert.deepEqual(wallPieces({ from: 0, to: 6, rail: true }, 9), [{ from: 0, to: 6, y1: 0, y2: 3.4, kind: 'rail' }]);
});

test('standing: flat when looking straight down, upright from about 20 degrees', () => {
  assert.equal(standing(0), 0);
  assert.equal(standing(0.5), 1);
  const mid = standing(0.2);
  assert.ok(mid > 0 && mid < 1);
});

test('isTap: small and quick', () => {
  assert.equal(isTap({ x: 0, y: 0, t: 0 }, { x: 3, y: 4, t: 200 }), true);
  assert.equal(isTap({ x: 0, y: 0, t: 0 }, { x: 9, y: 0, t: 200 }), false);
  assert.equal(isTap({ x: 0, y: 0, t: 0 }, { x: 0, y: 0, t: 460 }), false);
});

test('glideLength stops 1.2 ft short of a wall', () => {
  assert.equal(glideLength(10, null), 10);
  assert.equal(glideLength(10, 6), 4.8);
  assert.equal(glideLength(10, 0.5), 0);
  assert.equal(glideLength(10, 30), 10);
});

test('reconcile pairs plan places with devices and lists the rest', () => {
  const plan = [{ id: 'a' }, { id: 'b' }, { id: 'tv' }];
  const snap = [{ id: 'a', name: 'A' }, { id: 'c', name: 'C' }];
  const { placed, unplaced } = reconcile(plan, snap);
  assert.deepEqual(placed.map(p => [p.place.id, p.device && p.device.id]), [['a', 'a'], ['b', null], ['tv', null]]);
  assert.deepEqual(unplaced.map(d => d.id), ['c']);
});

test('place round-trips, and bad storage gives null', () => {
  const box = {}, storage = { getItem: k => box[k] ?? null, setItem: (k, v) => { box[k] = v; } };
  const place = { view: 'walk', camera: { pos: [1, 2, 3], target: [0, 0, 0] }, walk: { x: 21, y: 30, yaw: 1, pitch: 0 } };
  savePlace(storage, place);
  assert.deepEqual(loadPlace(storage), place);
  assert.equal(loadPlace({ getItem: () => { throw new Error('blocked'); } }), null);
  assert.equal(loadPlace({ getItem: () => '{nope' }), null);
  assert.equal(loadPlace({ getItem: () => JSON.stringify({ view: 'sideways' }) }), null);
  savePlace({ setItem: () => { throw new Error('full'); } }, place);   // must not throw
});

test('wallPieces: a sliding door is an opening under a lintel (its panes are drawn apart); a parapet is low wall plus granite', () => {
  const slider = wallPieces({ from: 0, to: 14, features: [{ kind: 'slider', from: 2, to: 12 }] }, 9);
  assert.deepEqual(slider.filter(p => p.from === 2).map(p => [p.kind, p.y1, p.y2]), [['wall', 7, 9]]);
  assert.deepEqual(wallPieces({ from: 0, to: 14, parapet: true }, 9),
    [{ from: 0, to: 14, y1: 0, y2: 3.1, kind: 'wall' }, { from: 0, to: 14, y1: 3.1, y2: 3.3, kind: 'granite' }]);
});

test('curtainPanels: closed they meet in the middle, open they bunch at the ends', () => {
  assert.deepEqual(curtainPanels(16, 26, 0), [{ from: 16, to: 21 }, { from: 21, to: 26 }]);
  assert.deepEqual(curtainPanels(16, 26, 1), [{ from: 16, to: 16.9 }, { from: 25.1, to: 26 }]);
  const half = curtainPanels(16, 26, 0.5);
  assert.ok(half[0].to > 16.9 && half[0].to < 21 && half[1].from > 21 && half[1].from < 25.1);
  assert.deepEqual(curtainPanels(16, 26, 2), curtainPanels(16, 26, 1));          // clamped
});

const PLAN = JSON.parse(readFileSync(new URL('./house-plan.json', import.meta.url)));
const roomDoors = { 'door:h41:15': false };          // the main door shut, the rest open (as the view starts)

test('walkGrid: open floor is free; walls, furniture and closed doors are not', () => {
  const g = walkGrid(PLAN, { doors: roomDoors, curtains: false });
  assert.equal(g.free(19.5, 34.5), true);              // living room floor in front of the TV
  assert.equal(g.free(28, 30), false);                 // in the living/Green wall
  assert.equal(g.free(27.6, 30), false);               // too close to it
  assert.equal(g.free(26.6, 35), false);               // on the sofa
  assert.equal(g.free(11.7, 23), true);                // VJS's open door
  assert.equal(walkGrid(PLAN, { doors: { ...roomDoors, 'door:h23:10.4': false } }).free(11.7, 23), false);
  assert.equal(g.free(21, 7), false);                  // the balcony slider, curtains closed
  assert.equal(walkGrid(PLAN, { doors: roomDoors, curtains: true }).free(24, 7), true);
  assert.equal(g.free(21, 45), false);                 // outside the flat
});

test('findPath goes through doorways, never through walls', () => {
  const g = walkGrid(PLAN, { doors: roomDoors, curtains: false });
  const path = findPath(g, [20.5, 34.5], [32.5, 7]);   // living room to the kitchen
  assert.ok(path && path.length >= 2);
  assert.ok(Math.hypot(path.at(-1)[0] - 32.5, path.at(-1)[1] - 7) < 1.2);
  for (let i = 1; i < path.length; i++) {               // every straight leg stays on free floor
    const [a, b] = [path[i - 1], path[i]], n = Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / 0.2);
    for (let k = 0; k <= n; k++) assert.ok(g.free(a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n), `leg ${i} at ${k}`);
  }
  const cross = path.findIndex((p, i) => i && (path[i - 1][1] - 13) * (p[1] - 13) <= 0 && path[i - 1][0] > 28);
  assert.ok(cross > 0);                                 // it crosses into the kitchen...
  const [a, b] = [path[cross - 1], path[cross]], x = a[0] + (b[0] - a[0]) * (13 - a[1]) / ((b[1] - a[1]) || 1);
  assert.ok(x > 29.2 && x < 32.6, `through the kitchen door, not the wall: ${x}`);
  assert.ok(path.length < 12);                          // smoothed into a few legs
});

test('findPath: a blocked target snaps to the nearest open floor; no way there is null', () => {
  const g = walkGrid(PLAN, { doors: roomDoors, curtains: false });
  const onSofa = findPath(g, [20.5, 34.5], [26.6, 35]);
  assert.ok(onSofa && g.free(...onSofa.at(-1)) && Math.hypot(onSofa.at(-1)[0] - 26.6, onSofa.at(-1)[1] - 35) < 2.5);
  assert.equal(findPath(g, [20.5, 34.5], [22, 3.5]), null);                  // the balcony, curtains closed
  assert.ok(findPath(walkGrid(PLAN, { doors: roomDoors, curtains: true }), [20.5, 34.5], [22, 3.5]));
});

test('slide: the joystick moves freely, and slides along a wall instead of into it', () => {
  const g = walkGrid(PLAN, { doors: roomDoors, curtains: false });
  assert.deepEqual(slide(g, [20, 34], [0.5, 0]), [20.5, 34]);
  const [x, y] = slide(g, [19.5, 34], [-3, 0.5]);       // towards the TV wall at a slant
  assert.ok(g.free(x, y) && x > 15.4 && y > 34);         // stopped short of it, still slid along it
  assert.deepEqual(slide(g, [20, 34], [0, 0]), [20, 34]);
});

test('every room can be walked to from the living room (the dining balcony once the curtains are open)', () => {
  for (const curtains of [false, true]) {
    const g = walkGrid(PLAN, { doors: roomDoors, curtains });
    for (const r of PLAN.rooms) {
      if (r.id === 'balc1' && !curtains) continue;
      const [x, y] = r.label || [r.rect[0] + r.rect[2] / 2, r.rect[1] + r.rect[3] / 2];     // even when that's on a bed
      assert.ok(findPath(g, [20.5, 34.5], [x, y]), r.id);
    }
  }
});

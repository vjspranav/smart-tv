/* House view: the plain rules, kept apart from three.js so they can be tested with node (test_house_logic.mjs).
   Plan units are feet; see house-plan.json. */

const minutes = hm => { const [h, m] = hm.split(':').map(Number); return h * 60 + m; };

/* 0 at night, 1 in the day, blending over `dusk` minutes either side of sunrise and sunset. */
export function daylight(date, { sunrise = '06:15', sunset = '18:20', dusk = 45 } = {}) {
  const now = date.getHours() * 60 + date.getMinutes() + date.getSeconds() / 60;
  const up = minutes(sunrise), down = minutes(sunset);
  const ramp = (t, edge) => Math.min(1, Math.max(0, (t - (edge - dusk)) / (2 * dusk)));
  return Math.min(ramp(now, up), 1 - ramp(now, down));
}

/* The boxes a wall is drawn with, H feet tall: solid stretches, lintels over every opening, sills and glass for
   windows, glass for fixed glass, a low railing or a low wall with a granite top for balconies. A sliding door
   ('slider') is only the opening: house.js draws its panes so they can slide. */
export function wallPieces(wall, H) {
  if (wall.rail) return [{ from: wall.from, to: wall.to, y1: 0, y2: 3.4, kind: 'rail' }];
  if (wall.parapet) return [{ from: wall.from, to: wall.to, y1: 0, y2: 3.1, kind: 'wall' }, { from: wall.from, to: wall.to, y1: 3.1, y2: 3.3, kind: 'granite' }];
  const out = [], top = Math.min(7, H);
  const add = (from, to, y1, y2, kind = 'wall') => { if (to - from > 0.001 && y2 - y1 > 0.001) out.push({ from, to, y1, y2, kind }); };
  let at = wall.from;
  for (const f of [...(wall.features || [])].sort((a, b) => a.from - b.from)) {
    add(at, f.from, 0, H);
    add(f.from, f.to, top, H);                                 // lintel over every opening
    if (f.kind === 'window') { add(f.from, f.to, 0, Math.min(3, H)); add(f.from, f.to, Math.min(3, H), top, 'glass'); }
    if (f.kind === 'glass') add(f.from, f.to, 0, top, 'glass');
    at = f.to;
  }
  add(at, wall.to, 0, H);
  return out;
}

/* Where the two curtain panels hang across a window from..to: closed (0) they meet in the middle, open (1) each is
   bunched into 9% of the width at its end. */
export function curtainPanels(from, to, open) {
  const k = Math.min(1, Math.max(0, open)), mid = (from + to) / 2, shut = (to - from) * 0.09;
  const reach = (mid - from) - k * ((mid - from) - shut);
  const r = n => Math.round(n * 1000) / 1000;
  return [{ from, to: r(from + reach) }, { from: r(to - reach), to }];
}

/* How upright the house stands for a camera tilt (radians from straight down): 0 is the flat plan, 1 is full 3D. */
export function standing(polar) {
  const t = Math.min(1, Math.max(0, (polar - 0.05) / 0.3));
  return t * t * (3 - 2 * t);
}

/* A tap is short and still: under 8 px of movement and under the 450 ms that makes a hold. */
export function isTap(down, up) {
  return Math.hypot(up.x - down.x, up.y - down.y) < 8 && up.t - down.t < 450;
}

/* How far a glide goes: all the way, or 1.2 ft short of the first wall in the way. */
export function glideLength(dist, wallHit) {
  if (wallHit == null || wallHit > dist + 1.2) return dist;
  return Math.max(0, wallHit - 1.2);
}

/* Match the plan's places with the devices the service reports. A place without a device is drawn greyed ("Not set
   up"); a device without a place is listed under the view. The TV isn't a smart-home device, so it never matches. */
export function reconcile(planDevices, snapDevices) {
  const byId = new Map(snapDevices.map(d => [d.id, d]));
  const ids = new Set(planDevices.map(p => p.id));
  return { placed: planDevices.map(place => ({ place, device: byId.get(place.id) || null })),
           unplaced: snapDevices.filter(d => !ids.has(d.id)) };
}

/* Where the house view was left on this phone. Anything unreadable counts as never opened. */
const KEY = 'house-place';
const vec = v => Array.isArray(v) && v.length === 3 && v.every(Number.isFinite);
export function loadPlace(storage) {
  try {
    const p = JSON.parse(storage.getItem(KEY));
    if (!p || !['overview', 'walk'].includes(p.view) || !p.camera || !vec(p.camera.pos) || !vec(p.camera.target)) return null;
    if (p.walk != null && !['x', 'y', 'yaw', 'pitch'].every(k => Number.isFinite(p.walk[k]))) return null;
    if (p.view === 'walk' && p.walk == null) return null;
    return p;
  } catch { return null; }
}
export function savePlace(storage, place) {
  try { storage.setItem(KEY, JSON.stringify(place)); } catch {}
}

/* ---- walking: where you can stand, and the way from here to there ------------------------------------------
   The plan becomes a grid of half-foot cells. A cell is free when it's inside a room and clear of walls (with
   `clear` ft to spare), furniture, cupboards and counters (`near` ft), open door leaves, closed doors and the
   balcony slider while the curtains are closed. doors: { 'door:h41:15': false, ... } (missing = open). */
export function walkGrid(plan, { doors = {}, curtains = false, cell = 0.5, clear = 0.8, near = 0.5, H = 9, T = 0.45 } = {}) {
  const [W, D] = plan.size, cols = Math.ceil(W / cell), rows = Math.ceil(D / cell);
  const blocked = new Uint8Array(cols * rows), inside = new Uint8Array(cols * rows);
  const centre = (i, j) => [(i + 0.5) * cell, (j + 0.5) * cell];
  const inPoly = (pts, x, y) => { let c = false; for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
    const [xi, yi] = pts[i], [xj, yj] = pts[j]; if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c; } return c; };
  for (const r of plan.rooms) {
    const pts = r.poly || [[r.rect[0], r.rect[1]], [r.rect[0] + r.rect[2], r.rect[1]], [r.rect[0] + r.rect[2], r.rect[1] + r.rect[3]], [r.rect[0], r.rect[1] + r.rect[3]]];
    for (let j = 0; j < rows; j++) for (let i = 0; i < cols; i++) if (inPoly(pts, ...centre(i, j))) inside[j * cols + i] = 1;
  }
  const block = (x1, y1, x2, y2, pad) => {     // every cell whose middle is within pad of the rectangle
    const [ax, bx] = [Math.min(x1, x2), Math.max(x1, x2)], [ay, by] = [Math.min(y1, y2), Math.max(y1, y2)];
    for (let j = Math.max(0, Math.floor((ay - pad) / cell)); j < Math.min(rows, Math.ceil((by + pad) / cell)); j++)
      for (let i = Math.max(0, Math.floor((ax - pad) / cell)); i < Math.min(cols, Math.ceil((bx + pad) / cell)); i++) {
        const [x, y] = centre(i, j), dx = Math.max(ax - x, 0, x - bx), dy = Math.max(ay - y, 0, y - by);
        if (Math.hypot(dx, dy) <= pad) blocked[j * cols + i] = 1;
      }
  };
  const span = (w, from, to, pad) => w.axis === 'h' ? block(from, w.at - T / 2, to, w.at + T / 2, pad) : block(w.at - T / 2, from, w.at + T / 2, to, pad);
  for (const w of plan.walls) {
    for (const p of wallPieces(w, H)) if (p.y1 < 4) span(w, p.from, p.to, clear);     // anything you'd walk into
    for (const f of w.features || []) {
      if (f.kind === 'slider' && !curtains) span(w, f.from, f.to, clear);
      if (f.kind !== 'door') continue;
      if (doors[`door:${w.axis}${w.at}:${f.from}`] === false) { span(w, f.from, f.to, clear); continue; }
      const out = (f.to - f.from) * f.swing;   // an open leaf stands across the room by the hinge
      if (w.axis === 'h') block(f.from, w.at, f.from + 0.15, w.at + out, near); else block(w.at, f.from, w.at + out, f.from + 0.15, near);
    }
  }
  for (const f of plan.furniture) if (f.box[4] < 5) block(f.box[0], f.box[1], f.box[2], f.box[3], near);
  for (const c of plan.cupboards) { const [x, y, w, h] = c.rect; block(x, y, x + w, y + h, near); }
  for (const [x, y, w, h] of plan.counters) block(x, y, x + w, y + h, near);
  if (plan.curtains && curtains) {             // open curtains bunch at the ends of their window
    const c = plan.curtains.wall, k = (c.to - c.from) * 0.09;
    block(c.from, c.at, c.from + k, c.at + 0.6 * plan.curtains.side, near); block(c.to - k, c.at, c.to, c.at + 0.6 * plan.curtains.side, near);
  }
  const ok = (i, j) => i >= 0 && j >= 0 && i < cols && j < rows && inside[j * cols + i] && !blocked[j * cols + i];
  return { cell, cols, rows, ok, free: (x, y) => ok(Math.floor(x / cell), Math.floor(y / cell)), centre };
}

const sightOf = (g, a, b) => {                 // a straight walk from a to b stays on free floor
  const n = Math.max(1, Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / (g.cell * 0.4)));
  for (let k = 0; k <= n; k++) if (!g.free(a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n)) return false;
  return true;
};
const nearestFree = (g, [x, y], reach) => {    // the free cell closest to a point, within reach ft
  if (g.free(x, y)) return [x, y];
  let best = null, bestD = Infinity;
  const r = Math.ceil(reach / g.cell), ci = Math.floor(x / g.cell), cj = Math.floor(y / g.cell);
  for (let j = cj - r; j <= cj + r; j++) for (let i = ci - r; i <= ci + r; i++) if (g.ok(i, j)) {
    const c = g.centre(i, j), d = Math.hypot(c[0] - x, c[1] - y);
    if (d < bestD && d <= reach) { best = c; bestD = d; }
  }
  return best;
};

/* The way from `from` to `to` (plan feet) as a few straight legs, or null when there's no way there. A blocked
   start or end moves to the nearest free floor first. */
export function findPath(g, from, to) {
  const a = nearestFree(g, from, 3), b = nearestFree(g, to, 5);      // a tap on a bed lands beside it
  if (!a || !b) return null;
  const key = (i, j) => j * g.cols + i, start = [Math.floor(a[0] / g.cell), Math.floor(a[1] / g.cell)], goal = [Math.floor(b[0] / g.cell), Math.floor(b[1] / g.cell)];
  const cost = new Float32Array(g.cols * g.rows).fill(Infinity), came = new Int32Array(g.cols * g.rows).fill(-1);
  const open = [[0, ...start]]; cost[key(...start)] = 0;
  const h = (i, j) => { const dx = Math.abs(i - goal[0]), dy = Math.abs(j - goal[1]); return Math.max(dx, dy) + 0.414 * Math.min(dx, dy); };
  let found = false;
  while (open.length) {
    let m = 0; for (let k = 1; k < open.length; k++) if (open[k][0] < open[m][0]) m = k;
    const [, i, j] = open[m]; open[m] = open[open.length - 1]; open.pop();
    if (i === goal[0] && j === goal[1]) { found = true; break; }
    for (const [di, dj] of [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]]) {
      const ni = i + di, nj = j + dj;
      if (!g.ok(ni, nj) || (di && dj && (!g.ok(i + di, j) || !g.ok(i, j + dj)))) continue;   // no cutting corners
      const c = cost[key(i, j)] + (di && dj ? 1.414 : 1);
      if (c < cost[key(ni, nj)]) { cost[key(ni, nj)] = c; came[key(ni, nj)] = key(i, j); open.push([c + h(ni, nj), ni, nj]); }
    }
  }
  if (!found) return null;
  const cells = [];
  for (let k = key(...goal); k !== -1; k = came[k]) cells.push(g.centre(k % g.cols, Math.floor(k / g.cols)));
  cells.reverse(); cells[0] = a; cells[cells.length - 1] = b;
  const out = [cells[0]];                      // pull the string: keep only the corners you can't see past
  let i = 0;
  while (i < cells.length - 1) {
    let j = cells.length - 1;
    while (j > i + 1 && !sightOf(g, cells[i], cells[j])) j--;
    out.push(cells[j]); i = j;
  }
  return out;
}

/* Where a joystick step from `pos` by `delta` ends: as far as it can go, sliding along whatever is in the way. */
export function slide(g, pos, delta) {
  let [x, y] = pos;
  const n = Math.max(1, Math.ceil(Math.hypot(...delta) / 0.15));
  for (let k = 0; k < n; k++) {
    const dx = delta[0] / n, dy = delta[1] / n;
    if (g.free(x + dx, y + dy)) { x += dx; y += dy; }
    else if (dx && g.free(x + dx, y)) x += dx;
    else if (dy && g.free(x, y + dy)) y += dy;
  }
  const r = v => Math.round(v * 1e6) / 1e6;
  return [r(x), r(y)];
}

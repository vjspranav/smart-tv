'use strict';
/* Home → Overview: a map of the house where each room glows the way it really is (amber light, icy AC,
   spinning fans), routines as one-tap scenes, and one control for a whole group of lights.
   Routines and groups live on the Pi (/api/home/routines) so every phone sees the same ones.
   Uses home, device, shown, change, openDevice, pollHome, tileText, esc, ICONS, SWATCHES, LABEL, ROOM_TITLE,
   setRoom from home.js and buzz, toast, openSheet, closeSheet from app.js. */

const ov = { store: null, loadedAt: 0, target: null, light: null, routine: null, group: null };
try { ov.target = localStorage.getItem('remote-light-group'); } catch {}
ov.view = 'plan'; ov.plan = null;            // rooms as the floor plan (default) or as tiles, per phone
try { if (localStorage.getItem('remote-rooms-view') === 'tiles') ov.view = 'tiles'; } catch {}
const firstLights = () => orderedRooms().map(r => `${r.id}-lights`).find(id => groupById(id)) || 'all-lights';

const SCENE_ICONS = {
  spark: '<svg viewBox="0 0 24 24"><path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6"/></svg>',
  sun: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>',
  moon: '<svg viewBox="0 0 24 24"><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z"/></svg>',
  film: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M7 5v14M17 5v14M3 9.5h4M3 14.5h4M17 9.5h4M17 14.5h4"/></svg>',
  book: '<svg viewBox="0 0 24 24"><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5zM4 20.5A2.5 2.5 0 0 0 6.5 23H20v-5"/></svg>',
  sofa: '<svg viewBox="0 0 24 24"><path d="M4 11V8a3 3 0 0 1 3-3h10a3 3 0 0 1 3 3v3M3 17v-4a2 2 0 0 1 4 0v1h10v-1a2 2 0 0 1 4 0v4zM5 17v2M19 17v2"/></svg>',
  power: '<svg viewBox="0 0 24 24"><path d="M12 3v8M6.3 6.3a8 8 0 1 0 11.4 0"/></svg>',
  leaf: '<svg viewBox="0 0 24 24"><path d="M5 19c0-8 6-14 15-14 0 9-6 15-14 15M5 19l7-7"/></svg>',
};
const GLYPH = {
  timer: '<svg viewBox="0 0 24 24"><circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2M9 2h6"/></svg>',
  off: '<svg viewBox="0 0 24 24"><path d="M12 3v8M6.3 6.3a8 8 0 1 0 11.4 0"/></svg>',
};
const KIND_OF = { ac: 'ac', fan: 'fan', light: 'light' };

/* Every setting a routine step can change, by the kind of device it applies to */
const SETTINGS = {
  power: { label: 'Power', kinds: ['light', 'fan', 'ac'], type: 'onoff' },
  brightness: { label: 'Brightness', kinds: ['light'], type: 'range', min: 1, max: 100, unit: '%' },
  color_temp: { label: 'Warmth', kinds: ['light'], type: 'warmth' },
  color: { label: 'Colour', kinds: ['light'], type: 'color' },
  target_temp: { label: 'Temperature', kinds: ['ac'], type: 'number', min: 16, max: 31, unit: '°' },
  mode: { label: 'Mode', kinds: ['ac'], type: 'select', options: () => Object.entries(LABEL.mode) },
  fan_speed: { label: 'AC fan', kinds: ['ac'], type: 'select', options: () => ['auto', 'low', 'medium', 'high'].map(v => [v, LABEL.fan_speed[v]]) },
  swing_v: { label: 'Swing', kinds: ['ac'], type: 'onoff' },
  eco: { label: 'Eco', kinds: ['ac'], type: 'onoff' },
  turbo: { label: 'Turbo', kinds: ['ac'], type: 'onoff' },
  off_timer_min: { label: 'Turn off after', kinds: ['ac'], type: 'select',
    options: () => [[0, 'No timer'], [30, '30 min'], [60, '1 h'], [90, '1 h 30'], [120, '2 h'], [180, '3 h'], [240, '4 h'], [360, '6 h'], [480, '8 h']] },
  speed: { label: 'Fan speed', kinds: ['fan'], type: 'select', options: () => [1, 2, 3, 4, 5].map(v => [v, `${v}`]) },
  boost: { label: 'Boost', kinds: ['fan'], type: 'onoff' },
  sleep: { label: 'Sleep mode', kinds: ['fan'], type: 'onoff' },
  timer_h: { label: 'Fan off after', kinds: ['fan'], type: 'select', options: () => Object.entries(LABEL.timer_h).map(([v, l]) => [+v, l === 'Off' ? 'No timer' : l]) },
};
const DEFAULT_VALUE = { power: true, brightness: 60, color_temp: 30, color: '#ff9500', target_temp: 24, mode: 'cool', fan_speed: 'auto',
  swing_v: true, eco: true, turbo: false, off_timer_min: 120, speed: 2, boost: false, sleep: true, timer_h: 2 };

/* Suggestions for the empty state: aimed at one room (VJS), never the whole house */
const TEMPLATES = {
  morning: { name: 'Good morning', icon: 'sun', steps: [
    { group: 'vjs-lights', changes: { power: true, brightness: 100, color_temp: 70 } },
    { devices: ['vjs-ac'], changes: { power: false } },
    { devices: ['vjs-fan'], changes: { power: true, speed: 2 } }] },
  night: { name: 'Good night', icon: 'moon', steps: [
    { group: 'vjs-lights', changes: { power: true, brightness: 10, color_temp: 0 } },
    { devices: ['vjs-ac'], changes: { power: true, target_temp: 24, off_timer_min: 120 } },
    { devices: ['vjs-fan'], changes: { power: true, speed: 2 } }] },
};

const postHome = async (path, body) => {
  const r = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-TV-Remote': '1' }, body: JSON.stringify(body) });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(data.error || "Home controls aren't reachable");
  return data;
};
const allGroups = () => [...(ov.store?.builtin || []), ...(ov.store?.groups || [])];
const groupById = id => allGroups().find(g => g.id === id);
const stepDevices = step => step.devices || groupById(step.group)?.devices || [];
const stepKinds = step => new Set(stepDevices(step).map(id => device(id)?.kind).filter(Boolean));
const slug = (name, taken) => {
  const base = (name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'item').slice(0, 30);
  let id = base, n = 2;
  while (taken.has(id)) id = `${base}-${n++}`;
  return id;
};
const hours = m => m >= 60 ? `${m / 60 % 1 ? (m / 60).toFixed(1) : m / 60}h` : `${m}m`;

async function loadStore(force = false) {
  if (!force && ov.store && Date.now() - ov.loadedAt < 30000) return;
  try {
    const r = await fetch('/api/home/routines', { cache: 'no-store' });
    if (r.ok) { ov.store = await r.json(); ov.loadedAt = Date.now(); }
  } catch {}
  renderHome();
}

async function saveStore(next) {
  ov.store = await postHome('/api/home/routines', { groups: next.groups, routines: next.routines });
  ov.loadedAt = Date.now();
  renderHome();
}

async function runSteps(body, label) {
  try {
    const result = await postHome('/api/home/run', body);
    const failed = Object.keys(result.failed || {}).map(id => { const d = device(id); return d ? `${roomName(d.room)} ${d.name}` : id; });
    if (failed.length) toast(`${failed.join(', ')} didn't respond`, true);
    else if (label) toast(`${label} done`);
  } catch (error) { toast(error.message, true); }
  pollHome();
}
const roomName = id => { const r = home.snap?.rooms.find(x => x.id === id); return r ? r.name : id; };

/* What a routine does, as a few icon + value glyphs (not a sentence) */
function glyphs(routine) {
  const out = [];
  for (const step of routine.steps) {
    const kinds = stepKinds(step), c = step.changes;
    const kind = kinds.has('light') ? 'light' : kinds.has('ac') ? 'ac' : 'fan';
    let value = '';
    if (c.power === false) value = 'off';
    else if (kind === 'light') value = c.brightness != null ? `${c.brightness}%` : c.color ? '' : 'on';
    else if (kind === 'ac') value = c.target_temp != null ? `${c.target_temp}°` : 'on';
    else value = c.speed != null ? `${c.speed}` : 'on';
    out.push(`<span class="glyph ${kind}${c.power === false ? ' is-off' : ''}">${ICONS[kind]}${c.color ? `<i class="glyph-dot" style="--c:${c.color}"></i>` : ''}${value ? `<b>${value}</b>` : ''}</span>`);
    const timer = c.off_timer_min || (c.timer_h ? c.timer_h * 60 : 0);
    if (timer) out.push(`<span class="glyph timer">${GLYPH.timer}<b>${hours(timer)}</b></span>`);
  }
  return out.slice(0, 5).join('') + (out.length > 5 ? `<span class="glyph more">+${out.length - 5}</span>` : '');
}

/* ---- Overview page ---------------------------------------------------------------------------------- */
let ovBuilt = '';
function renderOverview(root) {
  if (!root || !home.snap) return;
  if (!ov.store) { loadStore(); return; }
  const builtinIds = new Set(ov.store.builtin.map(g => g.id));
  // Room light groups and the person's own groups only: nothing here switches the whole house in one tap
  const roomRank = id => orderedRooms().findIndex(r => `${r.id}-lights` === id);
  const lightGroups = allGroups().filter(g => builtinIds.has(g.id) ? g.id.endsWith('-lights') && g.id !== 'all-lights' : g.devices.some(id => device(id)?.kind === 'light'))
    .sort((a, b) => (builtinIds.has(a.id) ? roomRank(a.id) : 999) - (builtinIds.has(b.id) ? roomRank(b.id) : 999));
  if (!lightGroups.some(g => g.id === ov.target)) ov.target = lightGroups[0]?.id;
  if (ov.view === 'plan' && ov.plan === null) loadPlan();
  const key = JSON.stringify([ov.store.routines, lightGroups.map(g => [g.id, g.name]), ov.target, orderedRooms().map(r => r.id), ov.view]);
  if (key !== ovBuilt) {
    ovBuilt = key;
    root.innerHTML = `
      <section class="scenes-block" aria-label="Routines">
        <h3 class="quiet-head">Routines</h3>
        <div class="scenes">${ov.store.routines.map(r => `
          <div class="scene" data-run="${r.id}" role="button" tabindex="0" aria-label="Run ${esc(r.name)}">
            <span class="scene-icon">${SCENE_ICONS[r.icon] || SCENE_ICONS.spark}</span>
            <strong>${esc(r.name)}</strong>
            <span class="glyphs">${glyphs(r)}</span>
            <button type="button" class="scene-edit" data-edit="${r.id}" aria-label="Edit ${esc(r.name)}">${ICONS.edit}</button>
          </div>`).join('')}
          ${ov.store.routines.some(r => /morning/i.test(r.name)) ? '' : `<button type="button" class="scene suggest" data-template="morning"><span class="scene-icon">${SCENE_ICONS.sun}</span><strong>Good morning</strong><span class="suggest-note">Set up</span></button>`}
          ${ov.store.routines.some(r => /night/i.test(r.name)) ? '' : `<button type="button" class="scene suggest" data-template="night"><span class="scene-icon">${SCENE_ICONS.moon}</span><strong>Good night</strong><span class="suggest-note">Set up</span></button>`}
          <button type="button" class="scene add" data-act="new-routine" aria-label="New routine">${ICONS.plus}</button>
        </div>
      </section>
      <section class="house" aria-label="House">
        <div class="house-head"><h3 class="quiet-head">Rooms</h3><span class="ov-links"><button type="button" class="ov-link" data-act="rooms-view" aria-label="${ov.view === 'plan' ? 'Show rooms as tiles' : 'Show the floor plan'}">${ov.view === 'plan' ? 'Tiles' : 'Plan'}</button><button type="button" class="ov-link" data-act="house">View house</button><button type="button" class="ov-link" data-act="reorder">Reorder</button></span></div>
        <div class="house-map" id="house-map"></div>
      </section>
      <section class="lights-block" aria-label="Lights">
        <h3 class="quiet-head">Lights</h3>
        <div class="group-row">${lightGroups.map(g => `<button type="button" class="chip" data-target="${g.id}" aria-pressed="${g.id === ov.target}">${esc(g.name.replace(/ lights$/, ''))}</button>`).join('')}
          <button type="button" class="chip ghost" data-act="new-group" aria-label="New group">${ICONS.plus}</button>
          ${ov.store.groups.some(g => g.id === ov.target) ? `<button type="button" class="chip ghost" data-act="edit-group" aria-label="Edit group">${ICONS.edit}</button>` : ''}</div>
        <div class="light-rig">
          <button type="button" class="rig-power" data-act="group-power" aria-label="Turn the group on or off">${ICONS.light}</button>
          <label class="bar bright"><input type="range" min="1" max="100" step="1" data-group-slide="brightness" aria-label="Brightness"><span class="bar-fill"></span><output id="ov-bright"></output></label>
        </div>
        <label class="bar warmth"><input type="range" min="0" max="100" step="1" data-group-slide="color_temp" aria-label="Warm to cool"><span class="bar-knob"></span></label>
        <div class="swatch-row" id="ov-colours">${SWATCHES.map(v => `<button type="button" class="swatch" data-group-color="${v}" style="--c:${v}" aria-label="Colour ${v}"></button>`).join('')}</div>
      </section>`;
  }
  paintOverview(root);
}

function paintOverview(root) {
  const rooms = orderedRooms(), { devices } = home.snap;

  // The house: one tile per room, lit like the room is
  const order = { ac: 0, fan: 1, light: 2 };
  const tiles = rooms.map(r => {
    const mine = devices.filter(d => d.room === r.id).sort((a, b) => order[a.kind] - order[b.kind]);
    const on = mine.filter(d => d.online && shown(d).power);
    const lights = on.filter(d => d.kind === 'light');
    const glow = lights.length ? Math.max(...lights.map(d => (shown(d).brightness ?? 100) / 100)) : 0;
    const tint = lights.map(d => shown(d).color).find(Boolean) || '#ffc979';
    const ice = on.some(d => d.kind === 'ac') ? 1 : 0;
    return { r, mine, on, style: `--glow:${(0.25 + glow * 0.75) * (lights.length ? 1 : 0)};--tint:${tint};--ice:${ice}` };
  });
  const usePlan = ov.view === 'plan' && ov.plan;
  let column = 0;
  const html = usePlan ? planHtml(tiles) : tiles.map((t, i) => {
    let wide = t.mine.length >= 4 || (column === 0 && i === tiles.length - 1);
    column = wide ? 0 : (column + 1) % 2;
    const minis = t.mine.map(d => {
      const s = shown(d), on = d.online && s.power;
      const value = !on ? '' : d.kind === 'ac' ? `${s.target_temp}°` : d.kind === 'fan' ? (s.boost ? '6' : `${s.speed}`) : '';
      const spin = d.kind === 'fan' && on ? `style="--spin:${(1.6 - (s.boost ? 6 : s.speed) * 0.22).toFixed(2)}s"` : '';
      const dot = d.kind === 'light' && on ? `style="--level:${(s.brightness ?? 100) / 100};--c:${s.color || '#ffc979'}"` : '';
      return `<button type="button" class="dmini ${d.kind}${on ? ' on' : ''}${d.online ? '' : ' offline'}" data-mini="${d.id}" ${spin || dot}
        aria-label="${esc(d.name)}, ${esc(tileText(d, s))}" ${d.online ? '' : 'disabled'}>${ICONS[d.kind]}${value ? `<b>${value}</b>` : ''}</button>`;
    }).join('');
    return `<article class="room-cell${wide ? ' wide' : ''}${t.on.length ? ' lit' : ''}" style="${t.style}">
      <button type="button" class="room-go" data-goto="${t.r.id}">${esc(t.r.name)}<span aria-hidden="true">${ICONS.chevron}</span></button>
      <div class="minis">${minis}</div></article>`;
  }).join('');
  const map = root.querySelector('#house-map');
  if (map.dataset.html !== html) { map.innerHTML = html; map.dataset.html = html; }
  map.classList.toggle('as-plan', !!usePlan);

  // Lights rig for the chosen group
  const group = groupById(ov.target), lights = (group?.devices || []).map(device).filter(d => d?.kind === 'light');
  const lit = lights.filter(d => d.online && shown(d).power);
  const avg = k => { const v = lit.map(d => shown(d)[k]).filter(x => x != null); return v.length ? Math.round(v.reduce((a, b) => a + b, 0) / v.length) : null; };
  const touched = ov.light && Date.now() - ov.light.t < 2500;
  for (const k of ['brightness', 'color_temp']) {
    const input = root.querySelector(`[data-group-slide="${k}"]`), v = avg(k);
    if (!(touched && ov.light.key === k) && v != null) input.value = v;
    input.closest('.bar').style.setProperty('--v', (input.value - input.min) / (input.max - input.min));
  }
  root.querySelector('#ov-bright').textContent = lit.length ? `${root.querySelector('[data-group-slide="brightness"]').value}%` : 'Off';
  root.querySelector('.rig-power').classList.toggle('on', lit.length > 0);
  root.querySelector('.light-rig').classList.toggle('dark', !lit.length);
  root.querySelector('#ov-colours').hidden = !lights.some(d => d.capabilities.color);
}

/* ---- The floor plan: house-plan.json (feet; x east, y south) drawn flat, devices where they really are ---- */
async function loadPlan() {
  ov.plan = false;                                 // asked once; tiles stand in until it's here (or if it never is)
  try {
    const plan = await (await fetch('/house-plan.json', { cache: 'no-cache' })).json();
    ov.plan = { ...plan, svg: planSvg(plan) };
  } catch { return; }
  renderHome();
}

const PLAN_PAD = 0.6, ft = v => +v.toFixed(2);
const roomPath = r => {
  const pts = r.poly || [[r.rect[0], r.rect[1]], [r.rect[0] + r.rect[2], r.rect[1]], [r.rect[0] + r.rect[2], r.rect[1] + r.rect[3]], [r.rect[0], r.rect[1] + r.rect[3]]];
  return `M${pts.map(p => p.join(' ')).join('L')}Z`;
};
function planSvg(plan) {
  const [W, D] = plan.size, seg = (w, a, b, cls) => w.axis === 'h'
    ? `<line class="${cls}" x1="${a}" y1="${w.at}" x2="${b}" y2="${w.at}"/>` : `<line class="${cls}" x1="${w.at}" y1="${a}" x2="${w.at}" y2="${b}"/>`;
  const walls = plan.walls.map(w => {
    if (w.rail || w.parapet) return seg(w, w.from, w.to, 'wall low');
    let at = w.from, out = '';
    for (const f of [...(w.features || [])].sort((a, b) => a.from - b.from)) {
      if (f.from > at) out += seg(w, at, f.from, 'wall');
      if (f.kind !== 'door') out += seg(w, f.from, f.to, 'win');     // windows and the balcony slider are glass; doors are gaps
      at = f.to;
    }
    return out + (w.to > at ? seg(w, at, w.to, 'wall') : '');
  }).join('');
  const box = (x, y, w, h) => `<rect x="${ft(x)}" y="${ft(y)}" width="${ft(w)}" height="${ft(h)}" rx="0.3"/>`;
  const furniture = [...plan.furniture.map(f => box(f.box[0], f.box[1], f.box[2] - f.box[0], f.box[3] - f.box[1])),
                     ...plan.counters.map(c => box(...c)), ...plan.cupboards.map(c => box(...c.rect))].join('');
  return `<svg viewBox="${-PLAN_PAD} ${-PLAN_PAD} ${W + 2 * PLAN_PAD} ${D + 2 * PLAN_PAD}" aria-hidden="true">
    ${plan.rooms.map(r => `<path class="floor fl-${r.floor}" d="${roomPath(r)}"/>`).join('')}
    <!--glows--><g class="furn">${furniture}</g>${walls}</svg>`;
}
/* Where a device sits on the plan. On a wall: 0.8 ft into the room, the chip's edge there so it stays in its room
   however wide it gets. Standing: its spot, kept 2.2 ft clear of its room's walls so the chip doesn't cross them. */
function planSpot(p, rooms) {
  const w = p.wall;
  if (w) return w.axis === 'v' ? { x: w.at + w.face * 0.8, y: (w.from + w.to) / 2, side: w.face > 0 ? 'e' : 'w' }
                               : { x: (w.from + w.to) / 2, y: w.at + w.face * 0.8, side: w.face > 0 ? 's' : 'n' };
  const r = rooms.find(r => r.rect && r.rect[0] <= p.at[0] && p.at[0] <= r.rect[0] + r.rect[2] && r.rect[1] <= p.at[1] && p.at[1] <= r.rect[1] + r.rect[3]);
  const keep = (v, lo, size) => Math.min(Math.max(v, lo + 2.2), lo + size - 2.2);
  return r ? { x: keep(p.at[0], r.rect[0], r.rect[2]), y: keep(p.at[1], r.rect[1], r.rect[3]) } : { x: p.at[0], y: p.at[1] };
}
function planHtml(tiles) {
  const plan = ov.plan, [W, D] = plan.size, pct = (v, size) => `${ft((v + PLAN_PAD) / (size + 2 * PLAN_PAD) * 100)}%`;
  const looks = new Map(tiles.map(t => [t.r.id, t]));
  const glows = plan.rooms.filter(r => looks.has(r.id)).map(r => `<path class="glow" data-room="${r.id}" d="${roomPath(r)}" style="${looks.get(r.id).style}"/>`).join('');
  const names = plan.rooms.filter(r => r.name).map(r => {
    const known = looks.has(r.id), [x, y] = r.label || (known ? [r.rect[0] + 0.8, r.rect[1] + 0.7] : [r.rect[0] + r.rect[2] / 2, r.rect[1] + r.rect[3] / 2]);
    const where = `style="left:${pct(x, W)};top:${pct(y, D)}"`, cls = `plan-name${r.label || !known ? ' mid' : ''}${r.rect && r.rect[2] < 7 && r.rect[3] > r.rect[2] * 1.5 ? ' tall' : ''}`;
    return known ? `<button type="button" class="${cls}" data-goto="${r.id}" ${where}>${esc(looks.get(r.id).r.name)}</button>` : `<span class="${cls} quiet" ${where}>${esc(r.name)}</span>`;
  }).join('');
  const minis = plan.devices.map(p => [p, device(p.id)]).filter(([, d]) => d).map(([p, d]) => {
    const s = shown(d), on = d.online && s.power, { x, y, side } = planSpot(p, plan.rooms);
    const value = on && d.kind === 'ac' ? `<b>${s.target_temp}°</b>` : '';
    const look = d.kind === 'fan' && on ? `;--spin:${(1.6 - (s.boost ? 6 : s.speed) * 0.22).toFixed(2)}s`
      : d.kind === 'light' && on ? `;--level:${(s.brightness ?? 100) / 100};--c:${s.color || '#ffc979'}` : '';
    return `<button type="button" class="dmini ${d.kind}${on ? ' on' : ''}${d.online ? '' : ' offline'}${side ? ` to-${side}` : ''}" data-mini="${d.id}" style="left:${pct(x, W)};top:${pct(y, D)}${look}"
      aria-label="${esc(d.name)}, ${esc(tileText(d, s))}" ${d.online ? '' : 'disabled'}>${ICONS[d.kind]}${value}</button>`;
  }).join('');
  return `<div class="plan" style="aspect-ratio:${W + 2 * PLAN_PAD}/${D + 2 * PLAN_PAD}">${plan.svg.replace('<!--glows-->', glows)}${names}${minis}</div>`;
}

/* Lights rig: one change goes to every light in the chosen group, 400 ms after the last touch */
let groupTimer = 0;
function groupChange(changes, now = false) {
  clearTimeout(groupTimer);
  const go = () => runSteps({ steps: [{ group: ov.target, changes }] });
  if (now) go(); else groupTimer = setTimeout(go, 400);
}

/* House map gestures: tap a device to switch it, hold it for every control */
let miniHold = null;
$('#home').addEventListener('pointerdown', e => {
  const m = e.target.closest('[data-mini]');
  if (!m || m.disabled) return;
  miniHold = { id: m.dataset.mini, x: e.clientX, y: e.clientY, fired: false,
    timer: setTimeout(() => { miniHold.fired = true; buzz(14); openDevice(m.dataset.mini); }, 450) };
});
['pointerup', 'pointercancel'].forEach(t => $('#home').addEventListener(t, () => miniHold && clearTimeout(miniHold.timer)));
$('#home').addEventListener('pointermove', e => {
  if (miniHold && !miniHold.fired && Math.hypot(e.clientX - miniHold.x, e.clientY - miniHold.y) > 10) { clearTimeout(miniHold.timer); miniHold = null; }
}, { passive: true });

$('#home').addEventListener('click', e => {
  if (!e.target.closest('#overview')) return;
  const t = sel => e.target.closest(sel);
  if (t('[data-mini]')) {
    if (miniHold?.fired) { miniHold = null; return; }
    const d = device(t('[data-mini]').dataset.mini);
    buzz(); return change(d.id, { power: !shown(d).power });
  }
  if (t('[data-goto]')) { buzz(); return setRoom(t('[data-goto]').dataset.goto); }
  if (t('[data-edit]')) { buzz(); return openRoutine(t('[data-edit]').dataset.edit); }
  if (t('[data-template]')) { buzz(); return openRoutine(null, TEMPLATES[t('[data-template]').dataset.template]); }
  if (t('[data-run]')) {
    const card = t('[data-run]'), r = ov.store.routines.find(x => x.id === card.dataset.run);
    card.classList.remove('ran'); void card.offsetWidth; card.classList.add('ran');
    buzz(12); return runSteps({ routine: r.id }, r.name);
  }
  if (t('[data-target]')) {
    ov.target = t('[data-target]').dataset.target;
    try { localStorage.setItem('remote-light-group', ov.target); } catch {}
    buzz(); return renderHome();
  }
  if (t('[data-group-color]')) { buzz(); return groupChange({ power: true, color: t('[data-group-color]').dataset.groupColor }, true); }
  const act = t('[data-act]')?.dataset.act;
  if (act === 'group-power') {
    const lit = (groupById(ov.target)?.devices || []).map(device).some(d => d?.kind === 'light' && d.online && shown(d).power);
    buzz(); return groupChange({ power: !lit }, true);
  }
  if (act === 'new-routine') return openRoutine(null);
  if (act === 'reorder') return openOrder();
  if (act === 'house') { buzz(); return openHouse(); }
  if (act === 'rooms-view') {
    ov.view = ov.view === 'plan' ? 'tiles' : 'plan';
    try { localStorage.setItem('remote-rooms-view', ov.view); } catch {}
    buzz(); return renderHome();
  }
  if (act === 'new-group') return openGroup(null);
  if (act === 'edit-group') return openGroup(ov.target);
});
$('#home').addEventListener('input', e => {
  const s = e.target.closest('[data-group-slide]');
  if (!s) return;
  ov.light = { key: s.dataset.groupSlide, t: Date.now() };
  paintOverview($('#overview'));
  groupChange({ power: true, [s.dataset.groupSlide]: +s.value });
});
$('#home').addEventListener('keydown', e => {
  const card = e.target.closest('.scene[role=button]');
  if (card && (e.key === 'Enter' || e.key === ' ') && e.target === card) { e.preventDefault(); card.click(); }
});

/* ---- Editors (routine, group): bottom sheets built here so index.html stays small -------------------- */
function makeSheet(id, title) {
  const d = document.createElement('dialog');
  d.className = 'sheet editor-sheet'; d.id = id;
  d.innerHTML = `<form class="sheet-body" method="dialog"><header class="sheet-head"><h2>${title}</h2>
    <button type="button" class="x" data-close aria-label="Close"><svg><use href="#i-close"/></svg></button></header><div class="ed-body"></div></form>`;
  document.body.append(d);
  d.addEventListener('click', e => { if (e.target === d) closeSheet(d); });
  d.addEventListener('cancel', e => { e.preventDefault(); closeSheet(d); });
  d.querySelector('[data-close]').addEventListener('click', () => closeSheet(d));
  return d;
}
const routineSheet = makeSheet('routine-sheet', 'Routine');
const groupSheet = makeSheet('group-sheet', 'Group');

function openRoutine(id, template) {
  const found = ov.store.routines.find(r => r.id === id);
  const base = found || template || { name: '', icon: 'spark', steps: [{ group: firstLights(), changes: { power: true } }] };
  ov.routine = { id: found?.id || null, ...JSON.parse(JSON.stringify(base)) };
  ov.routine.steps = ov.routine.steps.filter(s => stepDevices(s).length);     // a template may name a room that isn't here
  routineSheet.querySelector('h2').textContent = found ? 'Edit routine' : 'New routine';
  drawRoutine();
  openSheet(routineSheet);
}

function targetOptions(step) {
  const value = step.devices ? `dev:${step.devices[0]}` : step.group;
  const opt = (v, l) => `<option value="${v}" ${v === value ? 'selected' : ''}>${esc(l)}</option>`;
  const builtin = ov.store.builtin;
  const rooms = orderedRooms();
  return `<optgroup label="Single device">${rooms.map(r => home.snap.devices.filter(d => d.room === r.id)
            .map(d => opt(`dev:${d.id}`, `${r.name} ${d.name}`)).join('')).join('')}</optgroup>
    <optgroup label="Lights">${builtin.filter(g => g.id.endsWith('lights')).map(g => opt(g.id, g.name)).join('')}</optgroup>
    <optgroup label="Whole room">${rooms.map(r => builtin.find(g => g.id === r.id)).filter(Boolean).map(g => opt(g.id, `${g.name} (everything)`)).join('')}</optgroup>
    <optgroup label="Every…">${builtin.filter(g => g.id === 'all-acs' || g.id === 'all-fans').map(g => opt(g.id, g.name)).join('')}</optgroup>
    ${ov.store.groups.length ? `<optgroup label="Your groups">${ov.store.groups.map(g => opt(g.id, g.name)).join('')}</optgroup>` : ''}`;
}

function settingControl(i, key, value) {
  const s = SETTINGS[key], name = `data-step="${i}" data-set="${key}"`;
  switch (s.type) {
    case 'onoff': return `<div class="segmented mini-seg"><button type="button" ${name} data-v="true" aria-checked="${value === true}">On</button><button type="button" ${name} data-v="false" aria-checked="${value === false}">Off</button></div>`;
    case 'range': return `<input class="ds-slider" type="range" min="${s.min}" max="${s.max}" value="${value}" ${name}><output>${value}${s.unit}</output>`;
    case 'warmth': return `<input class="ds-slider warmth" type="range" min="0" max="100" value="${value}" ${name}>`;
    case 'color': return `<input type="color" value="${value}" ${name} class="ed-color">`;
    case 'number': return `<div class="ed-stepper"><button type="button" ${name} data-delta="-1" aria-label="Lower">${ICONS.minus}</button><output>${value}${s.unit}</output><button type="button" ${name} data-delta="1" aria-label="Higher">${ICONS.plus}</button></div>`;
    case 'select': return `<select ${name}>${s.options().map(([v, l]) => `<option value="${v}" ${String(v) === String(value) ? 'selected' : ''}>${l}</option>`).join('')}</select>`;
  }
  return '';
}

function drawRoutine() {
  const r = ov.routine;
  routineSheet.querySelector('.ed-body').innerHTML = `
    <label class="ed-field"><span>Name</span><input type="text" maxlength="40" placeholder="Good night" value="${esc(r.name)}" data-field="name"></label>
    <div class="icon-pick" role="radiogroup" aria-label="Icon">${Object.entries(SCENE_ICONS).map(([k, svg]) =>
      `<button type="button" data-icon="${k}" role="radio" aria-checked="${r.icon === k}" aria-label="${k}">${svg}</button>`).join('')}</div>
    ${r.steps.map((step, i) => {
      const kinds = stepKinds(step);
      const missing = Object.keys(SETTINGS).filter(k => !(k in step.changes) && SETTINGS[k].kinds.some(x => kinds.has(x)));
      return `<div class="ed-step">
        <div class="ed-step-head"><span class="ed-kind">${ICONS[kinds.has('light') ? 'light' : kinds.has('ac') ? 'ac' : 'fan']}</span>
          <select data-step="${i}" data-field="target" aria-label="What this step controls">${targetOptions(step)}</select>
          <button type="button" class="x" data-remove-step="${i}" aria-label="Remove step"><svg><use href="#i-close"/></svg></button></div>
        ${Object.entries(step.changes).filter(([k]) => SETTINGS[k]).map(([k, v]) => `<div class="ed-setting"><span>${SETTINGS[k].label}</span><div class="ed-control">${settingControl(i, k, v)}</div>
          <button type="button" class="ed-del" data-step="${i}" data-unset="${k}" aria-label="Remove ${SETTINGS[k].label}">×</button></div>`).join('')}
        ${missing.length ? `<select class="ed-add" data-step="${i}" data-field="add" aria-label="Add a setting"><option value="">Add a setting</option>${missing.map(k => `<option value="${k}">${SETTINGS[k].label}</option>`).join('')}</select>` : ''}
      </div>`;
    }).join('')}
    <button type="button" class="ghost-btn" data-ed="add-step">${ICONS.plus} Add a device or group</button>
    <div class="sheet-actions">${r.id ? '<button type="button" class="ghost-btn danger" data-ed="delete">Delete</button>' : ''}
      <button type="button" class="ghost-btn" data-ed="try">Try it</button><button type="button" class="accent-btn wide" data-ed="save">Save routine</button></div>`;
}

routineSheet.addEventListener('input', e => {
  const el = e.target, r = ov.routine, i = +el.dataset.step;
  if (el.dataset.field === 'name') { r.name = el.value; return; }
  if (el.dataset.set && el.tagName !== 'SELECT') {
    const k = el.dataset.set;
    r.steps[i].changes[k] = SETTINGS[k].type === 'color' ? el.value : +el.value;
    if (SETTINGS[k].type === 'range') el.nextElementSibling.textContent = `${el.value}${SETTINGS[k].unit}`;
  }
});
routineSheet.addEventListener('change', e => {
  const el = e.target, r = ov.routine, i = +el.dataset.step;
  if (el.dataset.field === 'target') {
    const step = r.steps[i], v = el.value;
    if (v.startsWith('dev:')) { delete step.group; step.devices = [v.slice(4)]; } else { delete step.devices; step.group = v; }
    const kinds = stepKinds(step);       // keep only settings that still make sense for the new target
    for (const k of Object.keys(step.changes)) if (!SETTINGS[k]?.kinds.some(x => kinds.has(x))) delete step.changes[k];
    if (!Object.keys(step.changes).length) step.changes.power = true;
    return drawRoutine();
  }
  if (el.dataset.field === 'add' && el.value) { r.steps[i].changes[el.value] = DEFAULT_VALUE[el.value]; return drawRoutine(); }
  if (el.dataset.set && el.tagName === 'SELECT') {
    const k = el.dataset.set, raw = el.value;
    r.steps[i].changes[k] = typeof SETTINGS[k].options()[0][0] === 'number' ? +raw : raw;
  }
});
routineSheet.addEventListener('click', async e => {
  const el = e.target.closest('button'), r = ov.routine;
  if (!el) return;
  if (el.dataset.icon) { r.icon = el.dataset.icon; return drawRoutine(); }
  if (el.dataset.v) { r.steps[+el.dataset.step].changes[el.dataset.set] = el.dataset.v === 'true'; return drawRoutine(); }
  if (el.dataset.delta) {
    const k = el.dataset.set, s = SETTINGS[k], c = r.steps[+el.dataset.step].changes;
    c[k] = Math.min(s.max, Math.max(s.min, c[k] + +el.dataset.delta)); return drawRoutine();
  }
  if (el.dataset.unset) { delete r.steps[+el.dataset.step].changes[el.dataset.unset]; return drawRoutine(); }
  if (el.dataset.removeStep) { r.steps.splice(+el.dataset.removeStep, 1); return drawRoutine(); }
  const act = el.dataset.ed;
  if (act === 'add-step') { r.steps.push({ group: firstLights(), changes: { power: true } }); return drawRoutine(); }
  if (act === 'try') return r.steps.length ? runSteps({ steps: r.steps }, r.name || 'Routine') : toast('Add something for it to do', true);
  if (act === 'save' || act === 'delete') {
    const routines = ov.store.routines.filter(x => x.id !== r.id);
    if (act === 'save') {
      if (!r.name.trim()) return toast('Give the routine a name', true);
      if (!r.steps.length) return toast('Add something for it to do', true);
      r.id ||= slug(r.name, new Set(routines.map(x => x.id)));
      const at = ov.store.routines.findIndex(x => x.id === r.id);
      routines.splice(at < 0 ? routines.length : at, 0, r);
    }
    try { await saveStore({ groups: ov.store.groups, routines }); closeSheet(routineSheet); toast(act === 'save' ? 'Routine saved' : 'Routine deleted'); }
    catch (error) { toast(error.message, true); }
  }
});

function openGroup(id) {
  const found = ov.store.groups.find(g => g.id === id);
  ov.group = found ? JSON.parse(JSON.stringify(found)) : { id: null, name: '', devices: [] };
  groupSheet.querySelector('h2').textContent = found ? 'Edit group' : 'New group';
  groupSheet.querySelector('.ed-body').innerHTML = `
    <label class="ed-field"><span>Name</span><input type="text" maxlength="40" placeholder="Bedroom lights" value="${esc(ov.group.name)}" data-field="gname"></label>
    ${orderedRooms().map(r => `<div class="ed-room"><h3>${esc(ROOM_TITLE[r.id] || r.name)}</h3><div class="ds-chips">${home.snap.devices.filter(d => d.room === r.id).map(d =>
      `<button type="button" class="chip" data-pick-device="${d.id}" aria-pressed="${ov.group.devices.includes(d.id)}">${ICONS[d.kind]}${esc(d.name)}</button>`).join('')}</div></div>`).join('')}
    <div class="sheet-actions">${found ? '<button type="button" class="ghost-btn danger" data-ed="gdelete">Delete</button>' : ''}<button type="button" class="accent-btn wide" data-ed="gsave">Save group</button></div>`;
  openSheet(groupSheet);
}
groupSheet.addEventListener('input', e => { if (e.target.dataset.field === 'gname') ov.group.name = e.target.value; });
groupSheet.addEventListener('click', async e => {
  const el = e.target.closest('button'), g = ov.group;
  if (!el) return;
  if (el.dataset.pickDevice) {
    const id = el.dataset.pickDevice, on = !g.devices.includes(id);
    g.devices = on ? [...g.devices, id] : g.devices.filter(x => x !== id);
    el.setAttribute('aria-pressed', String(on)); return buzz();
  }
  if (el.dataset.ed === 'gsave' || el.dataset.ed === 'gdelete') {
    const groups = ov.store.groups.filter(x => x.id !== g.id);
    if (el.dataset.ed === 'gsave') {
      if (!g.name.trim()) return toast('Give the group a name', true);
      if (!g.devices.length) return toast('Pick at least one device', true);
      g.id ||= slug(g.name, new Set(allGroups().map(x => x.id)));
      groups.push(g);
    }
    try {
      await saveStore({ groups, routines: ov.store.routines });
      ov.target = el.dataset.ed === 'gsave' ? g.id : firstLights();
      closeSheet(groupSheet); toast(el.dataset.ed === 'gsave' ? 'Group saved' : 'Group deleted'); renderHome();
    } catch (error) { toast(error.message.startsWith('Unknown group') ? 'A routine still uses this group' : error.message, true); }
  }
});

/* Room order for this phone: move rooms up or down; saved on the phone, not shared */
const orderSheet = makeSheet('order-sheet', 'Room order');
function openOrder() { drawOrder(); openSheet(orderSheet); }
function drawOrder() {
  const rooms = orderedRooms();
  orderSheet.querySelector('.ed-body').innerHTML = `<p class="muted order-note">Only on this phone. Your tabs, swipes and the house map follow this order.</p>
    <ol class="order-list">${rooms.map((r, i) => `<li><span>${esc(ROOM_TITLE[r.id] || r.name)}</span>
      <button type="button" class="round-btn" data-move="${i}" data-by="-1" aria-label="Move ${esc(r.name)} up" ${i ? '' : 'disabled'}><svg viewBox="0 0 24 24"><path d="m6 15 6-6 6 6"/></svg></button>
      <button type="button" class="round-btn" data-move="${i}" data-by="1" aria-label="Move ${esc(r.name)} down" ${i < rooms.length - 1 ? '' : 'disabled'}><svg viewBox="0 0 24 24"><path d="m6 9 6 6 6-6"/></svg></button></li>`).join('')}</ol>
    <div class="sheet-actions"><button type="button" class="accent-btn wide" data-close-order>Done</button></div>`;
}
orderSheet.addEventListener('click', e => {
  const b = e.target.closest('button');
  if (!b) return;
  if (b.dataset.closeOrder !== undefined) return closeSheet(orderSheet);
  if (b.dataset.move === undefined) return;
  const ids = orderedRooms().map(r => r.id), i = +b.dataset.move, j = i + +b.dataset.by;
  [ids[i], ids[j]] = [ids[j], ids[i]];
  buzz(); setRoomOrder(ids); drawOrder();
  orderSheet.querySelector(`[data-move="${j}"][data-by="${b.dataset.by}"]`)?.focus();
});

document.addEventListener('visibilitychange', () => { if (!document.hidden) loadStore(); });
setInterval(() => { if (home.mode === 'home') loadStore(); }, 30000);

/* Android long-press shortcuts on the Home icon (server.manifest_with_shortcuts): /?run=<scene> and /?toggle=<lights> */
(async function fromShortcut() {
  const query = new URLSearchParams(location.search), run = query.get('run'), target = query.get('toggle');
  if (!run && !target) return;
  history.replaceState(null, '', '/');
  setMode('home');
  if (run) {
    await loadStore(true);
    return runSteps({ routine: run }, ov.store?.routines.find(r => r.id === run)?.name || 'Scene');
  }
  try {
    const result = await postHome('/api/home/toggle', { target });
    const name = groupById(target)?.name || device(target)?.name || 'Lights';
    toast(Object.keys(result.failed || {}).length ? `${name} didn't all respond` : `${name} ${result.power ? 'on' : 'off'}`, !!Object.keys(result.failed || {}).length);
  } catch (error) { toast(error.message, true); }
  pollHome();
})();

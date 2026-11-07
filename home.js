'use strict';
/* Home mode: room tabs, device tiles and full device sheets, talking to the smart-home service through
   /api/home/* on this same server. Uses $, $$, buzz, toast, openSheet, closeSheet and ctx from app.js. */

const ICONS = {
  ac: '<svg viewBox="0 0 24 24"><path d="M12 2v20M4.9 6.5l14.2 11M19.1 6.5 4.9 17.5M9 3.5l3 2.5 3-2.5M9 20.5l3-2.5 3 2.5"/></svg>',
  fan: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="1.6"/><path d="M12 10.4c-.6-3.4.3-6.4 3-6.9 2.4-.4 3.4 2.4 1.4 4.3-1.2 1.2-2.8 2-4.4 2.6M13.4 12.8c2.7 2.2 3.8 5.1 1.9 7-1.7 1.7-4.2.1-3.7-2.6.3-1.6 1.2-3.2 2.1-4.6M10.6 12.6c-3.2 1.3-6.3 1-7.1-1.6-.7-2.3 2.2-3.6 4.2-1.8 1.2 1.1 2.1 2.5 2.9 3.4"/></svg>',
  light: '<svg viewBox="0 0 24 24"><path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.6 10.8c.7.6 1.1 1.3 1.1 2.2h5c0-.9.4-1.6 1.1-2.2A6 6 0 0 0 12 3Z"/></svg>',
  tv: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="12" rx="2.5"/><path d="M8 20h8"/></svg>',
  more: '<svg viewBox="0 0 24 24"><path d="M5 12h.01M12 12h.01M19 12h.01" stroke-width="3"/></svg>',
  chevron: '<svg viewBox="0 0 24 24"><path d="m9 6 6 6-6 6"/></svg>',
  minus: '<svg viewBox="0 0 24 24"><path d="M6 12h12"/></svg>',
  plus: '<svg viewBox="0 0 24 24"><path d="M12 6v12M6 12h12"/></svg>',
  living: '<svg viewBox="0 0 24 24"><path d="M4 11V8a3 3 0 0 1 3-3h10a3 3 0 0 1 3 3v3M3 17v-4a2 2 0 0 1 4 0v1h10v-1a2 2 0 0 1 4 0v4zM5 17v2M19 17v2"/></svg>',
  bed: '<svg viewBox="0 0 24 24"><path d="M3 19V6M3 15h18v4M21 15v-3a3 3 0 0 0-3-3h-7v6M7 12.5a1.5 1.5 0 1 0 0-.01"/></svg>',
  dining: '<svg viewBox="0 0 24 24"><path d="M7 3v8M5 3v4a2 2 0 0 0 4 0V3M7 11v10M17 21V3c-2 1.5-3 4-3 7h3"/></svg>',
  grid: '<svg viewBox="0 0 24 24"><rect x="4" y="4" width="6.5" height="6.5" rx="2"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="2"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="2"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="2"/></svg>',
  edit: '<svg viewBox="0 0 24 24"><path d="M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4"/></svg>',
  moon: '<svg viewBox="0 0 24 24"><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z"/></svg>',
};
const ROOM_ICON = { living: 'living', dining: 'dining', kitchen: 'dining' };
const ROOM_TITLE = { living: 'Living Room', dining: 'Dining Room', overview: 'Overview' };

const LABEL = {
  mode: { cool: 'Cool', dry: 'Dry', fan: 'Fan', auto: 'Auto', heat: 'Heat' },
  fan_speed: { auto: 'Auto', silent: 'Silent', low: 'Low', medium: 'Medium', high: 'High', full: 'Full' },
  timer_h: { 0: 'Off', 1: '1 h', 2: '2 h', 3: '3 h', 6: '6 h' },
};
const TOGGLES = { swing_v: 'Swing ↕', swing_h: 'Swing ↔', eco: 'Eco', turbo: 'Turbo', sleep: 'Sleep', display: 'Display',
                  boost: 'Boost', light: 'LED' };
const DEBOUNCE = 400, HOLD = 450, TOUCH_GRACE = 1500;
const SWATCHES = ['#ff3b30', '#ff9500', '#ffcc00', '#34c759', '#00c7be', '#0a84ff', '#5e5ce6', '#bf5af2', '#ff2d92'];

const home = {
  mode: 'tv', room: 'living', snap: null, failed: false, loading: false,
  overlay: {},      // device id -> key -> { v, t }: values the user just set, shown until the server agrees
  outbox: {},       // device id -> { changes, timer }
  sheet: null,      // device id of the open sheet
};
try { home.mode = localStorage.getItem('remote-mode') === 'home' ? 'home' : 'tv'; } catch {}
try { home.room = localStorage.getItem('remote-room') || 'living'; } catch {}

const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const deg = v => (Math.round(v * 2) / 2).toString().replace(/\.0$/, '');
const device = id => home.snap?.devices.find(d => d.id === id);

/* What the page shows for a device: the server's state with the user's own recent changes on top. */
function shown(d) {
  const state = { ...d.state }, layer = home.overlay[d.id] || {}, now = Date.now();
  const busy = home.outbox[d.id];
  for (const [k, o] of Object.entries(layer)) {
    if (busy || now - o.t < TOUCH_GRACE) state[k] = o.v;
    else delete layer[k];
  }
  return state;
}

/* ---- Mode ------------------------------------------------------------------------------------------- */
function setMode(mode) {
  home.mode = mode === 'home' ? 'home' : 'tv';
  try { localStorage.setItem('remote-mode', home.mode); } catch {}
  $('#app').classList.toggle('mode-home', home.mode === 'home');
  $$('#mode-switch button').forEach(b => b.setAttribute('aria-checked', String(b.dataset.mode === home.mode)));
  $('#home').hidden = home.mode !== 'home';
  $('#home-tabbar').hidden = home.mode !== 'home' || !home.snap;
  if (home.mode === 'home') { renderHome(); pollHome(); }
  else dispatchEvent(new Event('resize'));       // the TV pager was hidden: let app.js re-measure it
}
window.setMode = setMode;
$$('#mode-switch button').forEach(b => b.addEventListener('click', () => { buzz(); setMode(b.dataset.mode); }));

/* Pages: Overview first, then one per room. Swipe between them or tap a tab. */
/* Each phone keeps its own room order (your room first, say): tabs, swiping and the house map all follow it. */
let roomOrder = [];
try { roomOrder = JSON.parse(localStorage.getItem('remote-room-order') || '[]'); } catch {}
function orderedRooms() {
  const rooms = home.snap?.rooms || [];
  const rank = r => { const i = roomOrder.indexOf(r.id); return i < 0 ? 1000 + rooms.indexOf(r) : i; };
  return [...rooms].sort((a, b) => rank(a) - rank(b));
}
function setRoomOrder(ids) {
  roomOrder = ids;
  try { localStorage.setItem('remote-room-order', JSON.stringify(ids)); } catch {}
  lastPages = ''; lastTabs = '';
  renderHome();
}
const pageIds = () => ['overview', ...orderedRooms().map(r => r.id)];
function setRoom(id, { instant = false } = {}) {
  const pager = $('#home .home-pager'), i = pageIds().indexOf(id);
  if (!pager || i < 0) { home.room = id; return renderHome(); }
  pager.scrollTo({ left: i * pager.clientWidth, behavior: instant ? 'instant' : 'smooth' });
}
function onSwipe() {
  const pager = $('#home .home-pager'), tabs = $('#home-tabbar'), ids = pageIds();
  if (!pager) return;
  const p = pager.scrollLeft / (pager.clientWidth || 1), width = (tabs.clientWidth - 10) / ids.length;
  $('#home-ink').style.transform = `translateX(${p * width}px)`;
  const id = ids[Math.round(p)];
  if (id && id !== home.room) {
    home.room = id;
    try { localStorage.setItem('remote-room', id); } catch {}
    paintTitle();
    tabs.querySelectorAll('[data-room]').forEach(b => { b.classList.toggle('current', b.dataset.room === id); b.setAttribute('aria-current', String(b.dataset.room === id)); });
  }
}
let swipeFrame = 0;
function paintTitle() {
  const room = home.snap?.rooms.find(r => r.id === home.room);
  $('#home-room-name').textContent = home.room === 'overview' ? 'Overview' : room ? (ROOM_TITLE[room.id] || room.name) : 'Home';
  if (home.snap) $('#home-summary').textContent = summary(home.snap.devices);
}

/* ---- Server ----------------------------------------------------------------------------------------- */
async function pollHome() {
  if (home.mode !== 'home' || document.hidden || home.loading) return;
  home.loading = true;
  try {
    const r = await fetch('/api/home/state', { cache: 'no-store', signal: AbortSignal.timeout(6000) });   // a stalled request must not stop polling
    if (!r.ok) throw new Error();
    home.snap = await r.json();
    home.failed = false;
  } catch {
    home.failed = true;
  } finally {
    home.loading = false;
  }
  renderHome();
}
setInterval(pollHome, 2000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) pollHome(); });

/* Remember the change, show it now, and send everything for this device 400 ms after the last touch. */
function change(id, changes) {
  const layer = home.overlay[id] ||= {}, now = Date.now();
  for (const [k, v] of Object.entries(changes)) layer[k] = { v, t: now };
  const box = home.outbox[id] ||= { changes: {}, timer: 0 };
  Object.assign(box.changes, changes);
  clearTimeout(box.timer);
  box.timer = setTimeout(() => send(id), DEBOUNCE);
  renderHome();
}

async function send(id) {
  const box = home.outbox[id];
  if (!box) return;
  delete home.outbox[id];
  const changes = box.changes;
  try {
    const r = await fetch('/api/home/set', {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-TV-Remote': '1' },
      body: JSON.stringify({ device: id, changes }),
    });
    const data = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(data.error || "Couldn't reach that device");
    const i = home.snap.devices.findIndex(d => d.id === id);
    if (i >= 0 && data.device) home.snap.devices[i] = data.device;
  } catch (error) {
    const layer = home.overlay[id] || {};
    for (const k of Object.keys(changes)) delete layer[k];   // back to what the device really says
    toast(error.message === 'Failed to fetch' ? "Home controls aren't reachable" : error.message, true);
  }
  renderHome();
}

/* ---- Rendering -------------------------------------------------------------------------------------- */
function summary(devices) {
  const on = k => devices.filter(d => d.kind === k && d.online && shown(d).power).length;
  const parts = [];
  const ac = on('ac'), fan = on('fan'), light = on('light');
  if (ac) parts.push(`${ac} AC${ac > 1 ? 's' : ''} on`);
  if (fan) parts.push(`${fan} fan${fan > 1 ? 's' : ''} on`);
  if (light) parts.push(`${light} light${light > 1 ? 's' : ''} on`);
  return parts.join(' · ') || "Everything's off";
}

const offText = d => d.kind === 'light' ? 'Off at switch' : 'Offline';   // a light with no power is usually its wall switch
function tileText(d, s) {
  if (!d.online) return offText(d);
  if (!s.power) return 'Off';
  if (d.kind === 'fan') return [s.boost ? 'Boost' : `Speed ${s.speed}`, s.sleep && 'Sleep', s.timer_h && `${s.timer_h} h timer`].filter(Boolean).join(' · ');
  if (d.kind === 'light') return [s.brightness != null ? `On · ${s.brightness}%` : 'On', s.color && 'colour'].filter(Boolean).join(' · ');
  return [LABEL.mode[s.mode], s.fan_speed && `Fan ${LABEL.fan_speed[s.fan_speed].toLowerCase()}`,
          s.swing_v && 'Swing ↕', s.swing_h && 'Swing ↔', s.eco && 'Eco', s.turbo && 'Turbo'].filter(Boolean).join(' · ');
}

function tileHtml(d) {
  const s = shown(d), on = d.online && s.power;
  const cls = `dev ${d.kind}${on ? ' on' : ''}${d.online ? '' : ' offline'}`;
  const more = `<span class="more" data-more aria-hidden="true">${ICONS.more}</span>`;
  const label = `${d.name}, ${tileText(d, s)}`;
  if (d.kind === 'ac') {
    const room = s.indoor_temp != null ? `Room ${deg(s.indoor_temp)}°` : '';
    return `<button type="button" class="${cls}" data-id="${d.id}" aria-label="${esc(label)}" ${d.online ? '' : 'disabled'}>
      <span class="dev-icon">${ICONS.ac}</span>${more}
      <strong>${esc(d.name)}</strong>
      <span class="ac-row"><span class="ac-temp">${s.target_temp != null ? deg(s.target_temp) : '–'}<sup>°</sup></span>
        <span class="ac-room">${esc(room)}</span></span>
      <small>${esc(tileText(d, s))}</small></button>`;
  }
  return `<button type="button" class="${cls}" data-id="${d.id}" aria-label="${esc(label)}" ${d.online ? '' : 'disabled'}>
    <span class="dev-icon">${ICONS[d.kind]}</span>${more}
    <strong>${esc(d.name)}</strong><small>${esc(tileText(d, s))}</small></button>`;
}

function tvCardHtml() {
  const c = typeof ctx === 'object' && ctx ? ctx : {};
  let title = 'TV', detail = 'Tap for the full remote';
  if (!c.connected && c.power !== 'on') title = 'TV is off';
  else if (c.media?.title) title = `${c.app_name || 'TV'} · ${c.media.title}`;
  else if (c.app_name) title = c.app_name;
  return `<button type="button" class="tv-card" id="tv-card"><span class="dev-icon">${ICONS.tv}</span>
    <span><strong>${esc(title)}</strong><small>${esc(detail)} ›</small></span>${ICONS.chevron}</button>`;
}

let lastTabs = '', lastPages = '';
function renderHome() {
  house?.update();
  if (home.mode !== 'home') return;
  const root = $('#home'), tabs = $('#home-tabbar');
  if (!home.snap) {
    tabs.hidden = true; lastPages = '';
    $('#home-room-name').textContent = 'Home';
    $('#home-summary').textContent = home.failed ? '' : 'Connecting';
    root.innerHTML = home.failed ? `<div class="home-empty"><strong>Home controls aren't reachable.</strong>
      <span>The TV remote still works.</span><button type="button" class="accent-btn" id="home-retry">Retry</button></div>` : '';
    return;
  }
  const rooms = orderedRooms(), { devices } = home.snap, ids = pageIds();
  if (!ids.includes(home.room)) home.room = 'overview';

  // Tabs: rebuilt only when rooms or their "something is on" dots change
  const tabsHtml = [{ id: 'overview', name: 'All' }, ...rooms].map(r => {
    const active = r.id !== 'overview' && devices.some(d => d.room === r.id && d.online && shown(d).power);
    const icon = ICONS[r.id === 'overview' ? 'grid' : ROOM_ICON[r.id] || 'bed'];
    return `<button type="button" data-room="${r.id}" class="${r.id === home.room ? 'current' : ''}" aria-current="${r.id === home.room}" aria-label="${r.id === 'overview' ? 'Overview' : esc(r.name)}">
      ${icon}<span>${esc(r.name)}</span>${active ? '<i class="on-dot" aria-hidden="true"></i>' : ''}</button>`;
  }).join('');
  if (tabsHtml !== lastTabs) {
    lastTabs = tabsHtml;
    tabs.querySelectorAll('button').forEach(b => b.remove());
    tabs.insertAdjacentHTML('beforeend', tabsHtml);
  }
  tabs.style.setProperty('--rooms', ids.length);
  tabs.hidden = false;

  // Pages: built once, then each page updates in place
  if (lastPages !== ids.join() || !root.querySelector('.home-pager')) {
    lastPages = ids.join();
    root.innerHTML = `<div class="home-pager">${ids.map(id => `<section class="home-page" data-page="${id}">${id === 'overview'
      ? '<div class="overview" id="overview"></div>' : `<div class="room-grid" data-room="${id}"></div>`}</section>`).join('')}</div>`;
    root.querySelector('.home-pager').addEventListener('scroll', () => { if (!swipeFrame) swipeFrame = requestAnimationFrame(() => { swipeFrame = 0; onSwipe(); }); }, { passive: true });
    requestAnimationFrame(() => { setRoom(home.room, { instant: true }); onSwipe(); });
  }
  paintTitle();
  const index = ids.indexOf(home.room), width = (tabs.clientWidth - 10) / ids.length;
  $('#home-ink').style.transform = `translateX(${index * width}px)`;

  // Tiles: keyed by device id, each one only rewritten when its own text changes
  const order = { ac: 0, fan: 1, light: 2 };
  for (const room of rooms) {
    const grid = root.querySelector(`.room-grid[data-room="${room.id}"]`);
    if (room.id === 'living') {
      const card = grid.querySelector('#tv-card'), html = tvCardHtml();
      if (!card) grid.insertAdjacentHTML('afterbegin', html);
      else if (card.outerHTML.replace(/\s+/g, ' ') !== html.replace(/\s+/g, ' ')) card.outerHTML = html;
    }
    devices.filter(d => d.room === room.id).sort((a, b) => order[a.kind] - order[b.kind]).forEach(d => {
      const html = tileHtml(d);
      let el = grid.querySelector(`[data-id="${d.id}"]`);
      if (!el) { grid.insertAdjacentHTML('beforeend', html); el = grid.querySelector(`[data-id="${d.id}"]`); }
      else if (el.dataset.html !== html) { el.outerHTML = html; el = grid.querySelector(`[data-id="${d.id}"]`); }
      el.dataset.html = html;
    });
  }
  if (typeof renderOverview === 'function') renderOverview($('#overview'));
  syncSheet();     // poll ticks refresh the open sheet too
}

/* ---- Tile gestures: tap toggles power, hold or ••• opens every control ------------------------------ */
let hold = null;
$('#home').addEventListener('pointerdown', e => {
  const tile = e.target.closest('.dev');
  if (!tile || tile.disabled) return;
  hold = { id: tile.dataset.id, x: e.clientX, y: e.clientY, fired: false, timer: setTimeout(() => { hold.fired = true; buzz(14); openDevice(tile.dataset.id); }, HOLD) };
});
const cancelHold = () => { if (hold) clearTimeout(hold.timer); };
$('#home').addEventListener('pointermove', e => {     // scrolling the room is not a long press
  if (hold && !hold.fired && Math.hypot(e.clientX - hold.x, e.clientY - hold.y) > 10) { cancelHold(); hold = null; }
}, { passive: true });
['pointerup', 'pointercancel', 'pointerleave'].forEach(t => $('#home').addEventListener(t, cancelHold));
$('#home').addEventListener('contextmenu', e => { if (e.target.closest('.dev')) e.preventDefault(); });
$('#home').addEventListener('click', e => {
  if (e.target.closest('#home-retry')) { home.failed = false; renderHome(); return pollHome(); }
  if (e.target.closest('#tv-card')) { buzz(); return setMode('tv'); }
  const tile = e.target.closest('.dev');
  if (!tile || tile.disabled) return;
  if (hold?.fired) { hold = null; return; }
  if (e.target.closest('[data-more]')) { buzz(); return openDevice(tile.dataset.id); }
  const d = device(tile.dataset.id);
  if (!d?.capabilities.power) return openDevice(d.id);
  buzz();
  change(d.id, { power: !shown(d).power });
});
$('#home-tabbar').addEventListener('click', e => {
  const b = e.target.closest('[data-room]');
  if (b) { buzz(); setRoom(b.dataset.room); }
});

/* ---- House view: the flat as a live 3D model (house.js), loaded only the first time it's opened ---------- */
let house = null, houseOpening = false, houseCancelled = false;
async function openHouse() {
  if (house || houseOpening) return;
  houseOpening = true; houseCancelled = false;
  const root = document.createElement('div');
  root.className = 'house-view'; root.id = 'house-view';
  // the first open downloads three.js: say so, and let Close (or Back) leave before it's done
  root.innerHTML = `<div class="house-bar"><button type="button" class="house-btn" data-house="close">Close</button></div>
    <p class="house-loading">Loading the house…</p>`;
  root.querySelector('[data-house="close"]').addEventListener('click', () => { buzz(); closeHouse(); });
  document.body.append(root);
  history.pushState({ house: true }, '');            // Back closes the view instead of leaving the app
  try {
    const view = await import('/house.js');
    if (houseCancelled) return;
    const mounted = await view.mount(root, { snap: () => home.snap, shown, change, openDevice, tv: () => ctx, logo: logoFor, togglePower, setMode, buzz, toast, close: closeHouse });
    if (houseCancelled) mounted.close(); else house = mounted;
  } catch (error) {
    root.remove();
    if (houseCancelled) return;
    if (history.state?.house) history.back();
    toast("The house view couldn't load", true);
  } finally { houseOpening = false; }
}
window.openHouse = openHouse;
function closeHouse({ fromBack = false } = {}) {
  if (!house && !houseOpening) return;
  if (house) { house.close(); house = null; } else houseCancelled = true;     // still loading: openHouse drops it
  $('#house-view')?.remove();
  if (!fromBack && history.state?.house) history.back();
}
addEventListener('popstate', () => {
  if (!house && !houseOpening) return;
  if ($('#device-sheet').open) {                    // one Back closes one thing: the sheet first, then the view
    closeSheet($('#device-sheet'));
    history.pushState({ house: true }, '');
    return;
  }
  closeHouse({ fromBack: true });
});

/* ---- Device sheet: built from the device's capabilities, so it never offers what the device can't do ---- */
const LAYOUT = {
  ac: ['power', 'target_temp', 'mode', 'fan_speed', 'swing', 'extras', 'off_timer_min'],
  fan: ['power', 'speed', 'timer_h', 'extras'],
  light: ['power', 'brightness', 'color_temp', 'color'],
};
const EXTRAS = { ac: ['eco', 'turbo', 'sleep', 'display'], fan: ['boost', 'sleep', 'light'] };
const timerText = m => !m ? 'Off' : m < 60 ? `${m} min` : `${Math.floor(m / 60)} h${m % 60 ? ` ${m % 60} min` : ''}`;

function controlHtml(d, part) {
  const c = d.capabilities, row = (label, body, out = '') =>
    `<div class="ds-row"><div class="ds-label"><span>${label}</span>${out}</div>${body}</div>`;
  const chips = (key, values, labels) => `<div class="ds-chips" role="radiogroup">${values.map(v =>
    `<button type="button" class="chip" role="radio" data-key="${key}" data-value="${esc(JSON.stringify(v))}">${esc(labels?.[v] ?? v)}</button>`).join('')}</div>`;
  const toggles = keys => keys.filter(k => c[k] && !c[k].readonly).map(k =>
    `<button type="button" class="chip" data-toggle="${k}" aria-pressed="false">${TOGGLES[k]}</button>`).join('');
  switch (part) {
    case 'power':
      return c.power ? `<div class="ds-power"><span class="muted" data-out="state"></span><button type="button" data-toggle="power"></button></div>` : '';
    case 'target_temp':
      if (!c.target_temp) return '';
      return `<div class="stepper big" data-step="target_temp"><button type="button" data-delta="-1" aria-label="Cooler">${ICONS.minus}</button>
        <output data-out="target_temp"></output><button type="button" data-delta="1" aria-label="Warmer">${ICONS.plus}</button></div>
        ${c.indoor_temp ? '<div class="ds-room-temp" data-out="indoor_temp"></div>' : ''}`;
    case 'mode': return c.mode ? row('Mode', chips('mode', c.mode.values, LABEL.mode)) : '';
    case 'fan_speed': return c.fan_speed ? row('Fan speed', chips('fan_speed', c.fan_speed.values, LABEL.fan_speed)) : '';
    case 'swing': { const t = toggles(['swing_v', 'swing_h']); return t ? row('Swing', `<div class="ds-chips">${t}</div>`) : ''; }
    case 'extras': { const t = toggles(EXTRAS[d.kind]); return t ? row('Extras', `<div class="ds-chips">${t}</div>`) : ''; }
    case 'off_timer_min':
      return c.off_timer_min ? row('Turn off in', `<div class="stepper" data-step="off_timer_min"><button type="button" data-delta="-1" aria-label="Less">${ICONS.minus}</button>
        <output data-out="off_timer_min"></output><button type="button" data-delta="1" aria-label="More">${ICONS.plus}</button></div>`) : '';
    case 'speed': {
      if (!c.speed) return '';
      const speeds = []; for (let v = c.speed.min; v <= c.speed.max; v += c.speed.step) speeds.push(v);
      return row('Speed', chips('speed', speeds));
    }
    case 'timer_h': return c.timer_h ? row('Timer', chips('timer_h', c.timer_h.values, LABEL.timer_h)) : '';
    case 'brightness':
      return c.brightness ? row('Brightness', `<input class="ds-slider" type="range" data-slide="brightness" min="${c.brightness.min}" max="${c.brightness.max}" step="1" aria-label="Brightness">`,
        '<output data-out="brightness"></output>') : '';
    case 'color':
      return c.color ? row('Colour', `<div class="ds-chips swatches">${SWATCHES.map(v =>
        `<button type="button" class="swatch" data-key="color" data-value="${esc(JSON.stringify(v))}" style="--c:${v}" aria-label="Colour ${v}"></button>`).join('')}
        <label class="swatch custom" aria-label="Pick a colour"><input type="color" data-pick="color"></label></div>`) : '';
    case 'color_temp':
      return c.color_temp ? row('Warmth', `<input class="ds-slider warmth" type="range" data-slide="color_temp" min="${c.color_temp.min}" max="${c.color_temp.max}" step="1" aria-label="Warm to cool">`,
        '<output data-out="color_temp"></output>') : '';
  }
  return '';
}

function openDevice(id) {
  const d = device(id);
  if (!d) return;
  home.sheet = id;
  const room = home.snap.rooms.find(r => r.id === d.room);
  $('#ds-title').textContent = d.name;
  $('#ds-sub').textContent = room ? (ROOM_TITLE[room.id] || room.name) : '';
  $('#ds-controls').innerHTML = LAYOUT[d.kind].map(p => controlHtml(d, p)).join('');
  openSheet($('#device-sheet'));
  syncSheet();
}
$('#device-sheet').addEventListener('close', () => { home.sheet = null; });

/* Put current values into the open sheet without rebuilding it (a finger may be on a slider). */
function syncSheet() {
  const sheet = $('#device-sheet');
  const d = home.sheet && device(home.sheet);
  if (!d || !sheet.open) return;
  const s = shown(d), box = $('#ds-controls');
  sheet.classList.toggle('offline', !d.online);
  const power = box.querySelector('[data-toggle="power"]');
  if (power) {
    power.className = s.power ? 'ghost-btn' : 'accent-btn';
    power.textContent = s.power ? 'Turn off' : 'Turn on';
    box.querySelector('[data-out="state"]').textContent = d.online ? (s.power ? tileText(d, s) : 'Off') : d.kind === 'light' ? 'Off at the wall switch. Turn the switch on to control it here.' : d.error || 'Offline';
  }
  box.querySelectorAll('[data-toggle]:not([data-toggle="power"])').forEach(b => b.setAttribute('aria-pressed', String(!!s[b.dataset.toggle])));
  box.querySelectorAll('[data-key]').forEach(b => b.setAttribute('aria-checked', String(JSON.stringify(s[b.dataset.key]) === b.dataset.value)));
  box.querySelectorAll('[data-out]').forEach(o => {
    const k = o.dataset.out, v = s[k];
    if (k === 'state') return;
    if (k === 'target_temp') o.textContent = v != null ? `${deg(v)}°` : '–';
    else if (k === 'indoor_temp') o.textContent = v != null ? `Room is ${deg(v)}°` : '';
    else if (k === 'off_timer_min') o.textContent = timerText(v);
    else if (k === 'brightness') o.textContent = `${v ?? '–'}%`;
    else if (k === 'color_temp') o.textContent = v == null ? '' : v < 34 ? 'Warm' : v > 66 ? 'Cool' : 'Neutral';
  });
  const now = Date.now();
  box.querySelectorAll('[data-slide]').forEach(r => {
    const k = r.dataset.slide, touched = home.overlay[d.id]?.[k];
    if (document.activeElement === r && touched && now - touched.t < TOUCH_GRACE) return;
    if (s[k] != null) r.value = s[k];
  });
}

$('#ds-controls').addEventListener('click', e => {
  const d = device(home.sheet);
  if (!d) return;
  const s = shown(d), c = d.capabilities;
  const t = e.target.closest('[data-toggle]'), k = e.target.closest('[data-key]'), step = e.target.closest('[data-delta]');
  if (t) { buzz(); change(d.id, { [t.dataset.toggle]: !s[t.dataset.toggle] }); }
  else if (k) { buzz(); change(d.id, { [k.dataset.key]: JSON.parse(k.dataset.value) }); }
  else if (step) {
    const key = step.closest('[data-step]').dataset.step, cap = c[key];
    const next = Math.min(cap.max, Math.max(cap.min, (s[key] ?? cap.min) + cap.step * +step.dataset.delta));
    if (next !== s[key]) { buzz(); change(d.id, { [key]: next }); }
  }
});
$('#ds-controls').addEventListener('input', e => {
  const r = e.target.closest('[data-slide]'), pick = e.target.closest('[data-pick]');
  if (r && home.sheet) change(home.sheet, { [r.dataset.slide]: +r.value });
  if (pick && home.sheet) change(home.sheet, { [pick.dataset.pick]: pick.value });
});

addEventListener('resize', () => { if (home.mode === 'home') renderHome(); });
setMode(home.mode);

/* A newer version on the Pi? Reload into it, but never while someone is using a sheet. */
async function checkVersion() {
  const mine = document.documentElement.dataset.version;
  try {
    const { version } = await (await fetch('/api/version', { cache: 'no-store' })).json();
    if (version && mine && mine !== '__PAGE_VERSION__' && version !== mine && !document.hidden && !document.querySelector('dialog[open]')) location.reload();
  } catch {}
}
setInterval(checkVersion, 60000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) checkVersion(); });
checkVersion();

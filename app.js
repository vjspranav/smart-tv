'use strict';
const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
const root = document.documentElement;
const pageEls = [$('#p-now'), $('#p-remote'), $('#p-apps')];

/* ---- Per-phone preferences (localStorage is only a convenience; defaults always work) ---------------- */
const PREF_DEFAULTS = { mode: 'dpad', speed: 'normal', haptics: true, notify: true, lock: false, tipSeen: false };
const prefs = { ...PREF_DEFAULTS };
try { Object.assign(prefs, JSON.parse(localStorage.getItem('remote-prefs') || '{}')); } catch {}
function savePrefs() { try { localStorage.setItem('remote-prefs', JSON.stringify(prefs)); } catch {} }

/* ---- Known apps: real logos and brand colours ----------------------------------------------------- */
const KNOWN = {
  'com.netflix.ninja': { color: '#e5334b' },
  'com.amazon.amazonvideo.livingroom': { color: '#1fa3e0' },
  'in.startv.hotstar': { color: '#9b5de5', square: true },
  'com.google.android.youtube.tv': { color: '#ff3d3d' },
  'com.spotify.tv.android': { color: '#1ed760', music: true },
  'com.google.android.youtube.tvmusic': { color: '#ff3d3d', music: true },
};
const DEFAULT_ACCENT = '#b8aaff';
const ACTION_INFO = { picture: ['Picture settings', 'i-picture'], sound: ['Sound settings', 'i-sound'], settings: ['TV settings', 'i-settings'], wifi: ['Wi-Fi settings', 'i-wifi'] };
const KEY_NAMES = { back: 'Back', home: 'Home', ok: 'OK', play_pause: 'Play/pause', mute: 'Mute', captions: 'Subtitles', input: 'Input menu' };

let ctx = {};
let receivedAt = 0;
let apps = [];
try { apps = JSON.parse(localStorage.getItem('remote-apps') || '[]'); if (!Array.isArray(apps)) apps = []; } catch { apps = []; }
let config = { shortcuts: [], routines: [] };
let pendingPlay = null;
let scrubbing = null;
let volumeEditing = false;
let powerBusy = false;
let artKey = null, artColor = null;
let page = 1;

/* ---- Requests ------------------------------------------------------------------------------------- */
let queue = Promise.resolve(), queued = 0;
function post(path, body = {}, { timeout = 15000, quiet = false, direct = false } = {}) {
  const run = async () => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
      const r = await fetch('/api/' + path, {
        method: 'POST', signal: controller.signal,
        headers: { 'Content-Type': 'application/json', 'X-TV-Remote': '1' }, body: JSON.stringify(body),
      });
      const data = await r.json().catch(() => ({}));
      if (!r.ok) throw new Error(data.error || 'The TV did not respond');
      return data;
    } catch (error) {
      if (!quiet) toast(error.name === 'AbortError' ? 'The TV did not respond' : error.message, true);
      return null;
    } finally {
      clearTimeout(timer);
      if (!live) setTimeout(poll, 250);
    }
  };
  if (direct) return run();                       // independent of the key queue (power, keyboard, config)
  if (queued >= 6) return Promise.resolve(null);  // don't pile up key presses
  queued++;
  const p = queue.then(run).finally(() => queued--);
  queue = p.catch(() => null);
  return p;
}
async function getJSON(path, timeout = 8000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const r = await fetch(path, { signal: controller.signal, cache: 'no-store' });
    if (!r.ok) throw new Error((await r.json().catch(() => ({}))).error || 'Unavailable');
    return await r.json();
  } finally { clearTimeout(timer); }
}
// Keys pressed in quick succession (a long trackpad swipe, a flick) go to the TV together in one request,
// so three steps land at once instead of trickling in one round trip at a time.
let keyBatch = [], keyFlushing = false;
function key(name) {
  if (keyBatch.length >= 12) return;   // TV not keeping up: don't let presses pile up and overshoot
  keyBatch.push(name);
  if (!keyFlushing) { keyFlushing = true; queueMicrotask(flushKeys); }
}
async function flushKeys() {
  while (keyBatch.length) {
    const keys = keyBatch.splice(0, 12);
    await post('key', keys.length === 1 ? { key: keys[0] } : { keys }, { direct: true });
  }
  keyFlushing = false;
}
const media = (action, ms) => post('media', ms === undefined ? { action } : { action, ms });
const buzz = (ms = 8) => prefs.haptics && navigator.vibrate && navigator.vibrate(ms);

let toastTimer;
function toast(text, error = false) {
  const t = $('#toast');
  t.hidden = true; void t.offsetWidth;            // restart the drop-in animation
  t.textContent = text; t.classList.toggle('error', error); t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), error ? 3800 : 2200);
}

/* ---- Sheets: spring up, slide away ------------------------------------------------------------------- */
function openSheet(d) { d.classList.remove('closing'); if (!d.open) d.showModal(); }
function closeSheet(d) {
  if (!d.open || d.classList.contains('closing')) return;
  d.classList.add('closing');
  setTimeout(() => { d.close(); d.classList.remove('closing'); }, 270);
}
$$('dialog.sheet').forEach(d => {
  d.addEventListener('click', e => { if (e.target === d) closeSheet(d); });
  d.addEventListener('cancel', e => { e.preventDefault(); closeSheet(d); });
  d.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click', () => closeSheet(d)));
});

/* ---- Polling -------------------------------------------------------------------------------------- */
let polling = false;
async function poll() {
  if (polling || document.hidden || live) return;   // the live stream already keeps ctx current
  polling = true;
  try {
    ctx = await getJSON('/api/context', 6000);
    receivedAt = performance.now();
  } catch {
    ctx = { ...ctx, connected: false };
  } finally {
    polling = false;
    render();
  }
}

/* ---- Derived state -------------------------------------------------------------------------------- */
const appByPkg = pkg => apps.find(a => a.package === pkg);
const logoFor = pkg => appByPkg(pkg)?.logo || null;
function isPlaying() {
  const m = ctx.media;
  if (!m) return false;
  if (pendingPlay) {
    if (m.playing === pendingPlay.value || performance.now() > pendingPlay.until) pendingPlay = null;
    else return pendingPlay.value;
  }
  return !!m.playing;
}
function livePosition() {
  const m = ctx.media;
  if (!m || m.position_ms == null) return null;
  let pos = m.position_ms;
  if (isPlaying()) pos += (Math.max(0, (ctx.server_time - ctx.updated_at) * 1000) + performance.now() - receivedAt) * (m.speed || 1);
  return m.duration_ms ? Math.min(pos, m.duration_ms) : pos;
}
function fmt(ms) {
  const s = Math.max(0, Math.floor(ms / 1000)), h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60, ss = String(s % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${ss}` : `${m}:${ss}`;
}

/* ---- Accent colour from artwork or app ---------------------------------------------------------- */
let lastAccent = null;
function setAccent(hex) {
  const light = matchMedia('(prefers-color-scheme: light)').matches;
  if (hex + light === lastAccent) return;
  lastAccent = hex + light;
  const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16));
  const lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
  const mix = light ? (lum > 0.55 ? -0.4 : 0) : (lum < 0.45 ? 0.38 : 0);
  const adj = c => Math.round(mix > 0 ? c + (255 - c) * mix : c * (1 + mix));
  const [R, G, B] = [adj(r), adj(g), adj(b)];
  const L = (0.2126 * R + 0.7152 * G + 0.0722 * B) / 255;
  root.style.setProperty('--accent', `rgb(${R} ${G} ${B})`);
  root.style.setProperty('--accent-ink', L > 0.6 ? '#12142a' : '#ffffff');
  root.style.setProperty('--glow', `rgb(${r} ${g} ${b} / ${light ? 0.28 : 0.36})`);
}
function colorFromImage(img) {
  try {
    const c = document.createElement('canvas'); c.width = c.height = 24;
    const g = c.getContext('2d', { willReadFrequently: true });
    g.drawImage(img, 0, 0, 24, 24);
    const d = g.getImageData(0, 0, 24, 24).data;
    let best = null, bestScore = -1;
    const sum = [0, 0, 0];
    for (let i = 0; i < d.length; i += 4) {
      const r = d[i], gg = d[i + 1], b = d[i + 2], max = Math.max(r, gg, b), min = Math.min(r, gg, b);
      const score = (max ? (max - min) / max : 0) * (max / 255);
      if (score > bestScore) { bestScore = score; best = [r, gg, b]; }
      sum[0] += r; sum[1] += gg; sum[2] += b;
    }
    const pick = bestScore > 0.25 ? best : sum.map(v => v / (d.length / 4));
    return '#' + pick.map(v => Math.round(v).toString(16).padStart(2, '0')).join('');
  } catch { return null; }
}

/* ---- Pager: pages follow the finger, tab ink glides with them --------------------------------------- */
const pager = $('#pager');
/* Layouts: one column (swipe pages), two (remote + Now playing or Apps), three (everything at once). */
const appEl = $('#app');
let layout = 'one', side = 0;
function computeLayout() {
  const w = innerWidth, h = innerHeight;
  const next = w >= 1100 ? 'three' : (w >= 700 || (w > h && w >= 600)) ? 'two' : 'one';
  if (next === layout && appEl.dataset.layout) return;
  layout = next;
  appEl.dataset.layout = layout;
  pageEls.forEach(el => { el.style.transform = ''; el.style.opacity = ''; });
  if (layout === 'one') requestAnimationFrame(() => { goPage(page, { instant: true }); paintPager(); });
  else paintTabs();
}
function setSide(i) {
  side = i; appEl.dataset.side = i === 2 ? 'apps' : 'now';
  if (i === 2 && !apps.length) loadApps();
  paintTabs();
}
function paintTabs() {
  if (layout === 'one') return paintPager();
  const current = layout === 'two' ? side : -1;
  $$('#tabbar button').forEach(b => b.classList.toggle('current', +b.dataset.page === current));
  const bar = $('#tabbar'), w = (bar.clientWidth - 10) / 2;
  $('#tab-ink').style.transform = `translateX(${current === 2 ? w : 0}px)`;
  $('#tab-ink').style.width = w + 'px';
}
function goPage(i, { instant = false } = {}) {
  if (layout !== 'one') { if (i !== 1) setSide(i); return; }
  pager.scrollTo({ left: i * pager.clientWidth, behavior: instant ? 'instant' : 'smooth' });
}
let pagerFrame = 0;
function paintPager() {
  pagerFrame = 0;
  if (layout !== 'one') return;
  $('#tab-ink').style.width = '';
  const p = pager.scrollLeft / (pager.clientWidth || 1);
  pageEls.forEach((el, i) => {
    const d = Math.min(1, Math.abs(p - i));
    el.style.transform = d ? `scale(${1 - 0.06 * d}) translateY(${d * 10}px)` : '';
    el.style.opacity = String(1 - 0.5 * d);
  });
  const bar = $('#tabbar'), w = (bar.clientWidth - 10) / 3;
  $('#tab-ink').style.transform = `translateX(${p * w}px)`;
  const now = Math.round(p);
  if (now !== page) { page = now; onPage(); }
  $$('#tabbar button').forEach((b, i) => b.classList.toggle('current', i === page));
}
pager.addEventListener('scroll', () => { if (!pagerFrame) pagerFrame = requestAnimationFrame(paintPager); }, { passive: true });
window.addEventListener('resize', () => { computeLayout(); if (layout === 'one') { goPage(page, { instant: true }); paintPager(); } else paintTabs(); });
$$('#tabbar button').forEach(b => b.addEventListener('click', () => { buzz(); goPage(+b.dataset.page); }));
function onPage() {
  if (page === 2 && !apps.length) loadApps();
}

/* ---- Render ----------------------------------------------------------------------------------------- */
function render() {
  const online = !!ctx.connected;
  const off = online && ctx.power === 'off';
  const m = online && !off ? ctx.media : null;
  const fg = ctx.app === 'home' ? null : appByPkg(ctx.package);   // the launcher is the home screen, not an app
  const mediaApp = m ? appByPkg(m.package) : null;

  const app = $('#app');
  app.classList.toggle('offline', !online);
  app.classList.toggle('tv-off', off || !online);

  // Header
  $('#dot').className = 'dot' + (online ? (off ? ' standby' : ' on') : ctx.standby ? ' standby' : '');
  $('#app-name').textContent = !online || off ? 'Living Room TV' : fg?.name || (ctx.app === 'ps5' ? 'PlayStation 5' : ctx.app === 'tv' ? 'TV input' : 'Home screen');
  const limited = online && !off && !m && fg && ['Streaming', 'Music', 'Players'].includes(fg.category) ? fg : null;
  $('#app-detail').textContent = !online ? (ctx.standby ? 'TV is off' : 'Not reachable. Tap to reconnect.')
    : limited ? 'Open. This app doesn\u2019t share what\u2019s playing.'
    : off ? 'TV is off'
    : m ? `${isPlaying() ? 'Playing' : 'Paused'}: ${m.title}`
    : ctx.hdmi_port ? `HDMI ${ctx.hdmi_port}` : 'Nothing playing';

  const power = $('#power');
  power.classList.toggle('on', online && !off);
  power.classList.toggle('off', !online || off);
  power.classList.toggle('busy', powerBusy);
  power.setAttribute('aria-label', online && !off ? 'Turn TV off' : 'Turn TV on');
  $('#wake-layer').hidden = !(off || !online);
  $('#wake').classList.toggle('busy', powerBusy);
  $('#wake span').textContent = powerBusy ? 'Turning on' : 'Turn on TV';

  renderNow(m, mediaApp, limited);
  renderMini(m, mediaApp, limited);

  setAccent((m && artColor) || KNOWN[m?.package]?.color || KNOWN[ctx.package]?.color || DEFAULT_ACCENT);

  // Volume (two sliders share state)
  $$('[data-volume-input]').forEach(input => {
    if (Number.isFinite(ctx.volume) && !volumeEditing) {
      input.min = ctx.volume_min ?? 0; input.max = ctx.volume_max ?? 100; input.value = ctx.volume;
    }
    input.disabled = !online || off || !Number.isFinite(ctx.volume);
    paintRange(input);
  });
  $$('[data-volume-value]').forEach(o => (o.textContent = online && Number.isFinite(ctx.volume) ? (volumeEditing ? o.textContent : ctx.volume) : '–'));

  $$('.keys button, .volume > button, .shortcut:not(.edit)').forEach(b => (b.disabled = !online || off));

  // Routine progress pill
  const r = ctx.routine, pill = $('#routine-run');
  pill.hidden = !r;
  if (r) $('#routine-text').textContent = r.error ? `${r.name}: ${r.error}` : `${r.name}: step ${r.step} of ${r.total}`;

  // Sleep timer note
  const note = $('#sleep-note');
  note.textContent = ctx.sleep_at ? 'Off at ' + new Date(ctx.sleep_at * 1000).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) : '';
  $$('#sleep-row .chip').forEach(c => c.classList.toggle('on', c.dataset.sleep === '0' && !ctx.sleep_at));

  showNotifications();
  updateLockScreen(m);
}

function renderNow(m, mediaApp, limited) {
  $('#now-full').hidden = !m;
  $('#now-limited').hidden = !limited;
  $('#now-empty').hidden = !!m || !!limited;
  if (limited) {
    const box = $('#limited-logo');
    if (box.dataset.pkg !== limited.package) { box.dataset.pkg = limited.package; box.replaceChildren(appImage(limited)); }
    $('#limited-title').textContent = limited.name + ' is open';
  }
  if (!m) { artKey = null; artColor = null; return; }
  $('#title').textContent = m.title;
  $('#artist').textContent = m.artist || m.album || mediaApp?.name || '';
  const art = $('#art'), logo = $('#art-logo'), frame = $('#big-art');
  if (m.art_id !== artKey) {
    artKey = m.art_id; artColor = null;
    if (m.art_id) {
      art.hidden = true;
      art.onload = () => {
        art.hidden = false; logo.hidden = true;
        frame.classList.toggle('wide', art.naturalWidth / art.naturalHeight > 1.25);
        artColor = colorFromImage(art);
        $('#mini-img').src = art.src; $('#mini-img').className = ''; $('#mini-img').hidden = false;
        render();
      };
      art.onerror = () => { art.hidden = true; };
      art.src = '/api/art?id=' + encodeURIComponent(m.art_id);
    } else { art.hidden = true; frame.classList.remove('wide'); }
  }
  if (!m.art_id || art.hidden) {
    const src = mediaApp?.logo || mediaApp?.icon;
    logo.hidden = !src;
    if (src && logo.getAttribute('src') !== src) logo.src = src;
  }
  frame.classList.toggle('paused', !isPlaying());

  const music = !!KNOWN[m.package]?.music;
  $('#t-prev-icon').setAttribute('href', music ? '#i-prev' : '#i-back10');
  $('#t-next-icon').setAttribute('href', music ? '#i-next' : '#i-fwd10');
  $('#t-prev-label').hidden = $('#t-next-label').hidden = music;
  $('#t-prev').setAttribute('aria-label', music ? 'Previous track' : 'Back 10 seconds');
  $('#t-next').setAttribute('aria-label', music ? 'Next track' : 'Forward 10 seconds');
  $('#t-prev').dataset.music = $('#t-next').dataset.music = music ? '1' : '';
  const playing = isPlaying();
  $('#t-play-icon').setAttribute('href', playing ? '#i-pause' : '#i-play');
  $('#t-play').setAttribute('aria-label', playing ? 'Pause' : 'Play');
  $('#seek').classList.toggle('locked', !m.can_seek || !m.duration_ms);
  $('#track').hidden = !m.duration_ms;

  // Up next
  const q = (m.queue || []);
  $('#queue').hidden = q.length < 2;
  const list = $('#queue-list');
  const sig = q.map(i => i.id).join(',') + '|' + m.active_queue_id;
  if (list.dataset.sig !== sig) {
    list.dataset.sig = sig;
    list.replaceChildren(...q.slice(0, 30).map(item => {
      const li = document.createElement('li');
      if (item.id === m.active_queue_id) li.className = 'current';
      const b = document.createElement('button'); b.type = 'button';
      b.innerHTML = '<span class="q-title"></span><span class="q-sub"></span>';
      b.querySelector('.q-title').textContent = item.title || 'Untitled';
      b.querySelector('.q-sub').textContent = item.subtitle || '';
      b.addEventListener('click', () => { buzz(); media('queue', item.id); });
      li.append(b);
      return li;
    }));
  }
  tick();
}

function renderMini(m, mediaApp, limited) {
  const mini = $('#mini');
  mini.hidden = !m && !limited;
  mini.classList.toggle('limited', !!limited);
  if (!m && limited) {
    $('#mini-title').textContent = limited.name;
    $('#mini-sub').textContent = 'Playback controls';
    $('#mini-play-icon').setAttribute('href', '#i-play');
    const img = $('#mini-img'), src = limited.logo || limited.icon;
    if (img.getAttribute('src') !== src) { img.src = src; img.className = limited.logo ? 'logo' : ''; }
    img.hidden = false; $('#mini-fill').style.width = '0';
    return;
  }
  if (!m) return;
  $('#mini-title').textContent = m.title;
  $('#mini-sub').textContent = m.artist || mediaApp?.name || (isPlaying() ? 'Playing' : 'Paused');
  $('#mini-play-icon').setAttribute('href', isPlaying() ? '#i-pause' : '#i-play');
  const img = $('#mini-img');
  if (!m.art_id || $('#art').hidden) {
    const src = mediaApp?.logo || mediaApp?.icon;
    img.hidden = !src;
    if (src && img.getAttribute('src') !== src) { img.src = src; img.className = 'logo'; }
  }
}

function tick() {
  const m = ctx.media;
  if (!m) return;
  const pos = scrubbing ? scrubbing.ms : livePosition(), dur = m.duration_ms;
  const pct = dur && pos != null ? Math.min(100, Math.max(0, (pos / dur) * 100)) : 0;
  $('#mini-fill').style.width = pct + '%';
  if ($('#now-full').hidden) return;
  $('#elapsed').textContent = pos == null ? '' : fmt(pos);
  $('#remaining').textContent = dur && pos != null ? '-' + fmt(dur - pos) : '';
  if (dur && pos != null) {
    $('#track-fill').style.width = pct + '%';
    $('#track-thumb').style.left = pct + '%';
    const t = $('#track');
    t.setAttribute('aria-valuemax', String(Math.round(dur / 1000)));
    t.setAttribute('aria-valuenow', String(Math.round(pos / 1000)));
    t.setAttribute('aria-valuetext', `${fmt(pos)} of ${fmt(dur)}`);
  }
}
(function frame() {
  if (ctx.media && isPlaying() && !document.hidden) tick();
  requestAnimationFrame(frame);
})();

function paintRange(input) {
  const min = +input.min, max = +input.max, v = +input.value;
  input.style.setProperty('--p', ((v - min) / (max - min || 1)) * 100 + '%');
}

/* ---- Mini player grows into Now playing (shared-element morph) ----------------------------------------- */
function morphToNow() {
  buzz();
  const miniArt = $('#mini-art'), bigArt = $('#big-art');
  if (!document.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches || layout === 'three') { goPage(0); return; }
  miniArt.style.viewTransitionName = 'np-art';
  $('#mini-title').style.viewTransitionName = 'np-title';
  const t = document.startViewTransition(() => {
    miniArt.style.viewTransitionName = ''; $('#mini-title').style.viewTransitionName = '';
    bigArt.style.viewTransitionName = 'np-art'; $('#title').style.viewTransitionName = 'np-title';
    goPage(0, { instant: true }); paintPager();
  });
  t.finished.finally(() => { bigArt.style.viewTransitionName = ''; $('#title').style.viewTransitionName = ''; });
}
$('#mini').addEventListener('click', e => { if (!e.target.closest('#mini-play')) morphToNow(); });
$('#mini-play').addEventListener('click', e => { e.stopPropagation(); if (ctx.media) togglePlay(); else { buzz(); key('play_pause'); } });

/* ---- Transport ------------------------------------------------------------------------------------- */
let lastToggle = 0;
function togglePlay() {
  const now = performance.now();
  if (now - lastToggle < 450 || !ctx.media) return;   // ignore accidental double taps
  lastToggle = now;
  const next = !isPlaying();
  ctx.media.position_ms = livePosition(); ctx.server_time = ctx.updated_at; receivedAt = now;
  pendingPlay = { value: next, until: now + 5000 };   // hold until the TV confirms
  buzz(); render();
  media(next ? 'play' : 'pause');
}
$('#t-play').addEventListener('click', togglePlay);
$('#t-prev').addEventListener('click', () => { buzz(); $('#t-prev').dataset.music ? media('previous') : skip(-10000); });
$('#t-next').addEventListener('click', () => { buzz(); $('#t-next').dataset.music ? media('next') : skip(10000); });
function skip(delta) {
  const m = ctx.media;
  if (m && m.position_ms != null) { m.position_ms = Math.max(0, livePosition() + delta); ctx.server_time = ctx.updated_at; receivedAt = performance.now(); tick(); }
  media('skip', delta);
}
const track = $('#track');
const msAt = x => { const r = track.getBoundingClientRect(); return Math.round(Math.min(1, Math.max(0, (x - r.left) / r.width)) * (ctx.media?.duration_ms || 0)); };
track.addEventListener('pointerdown', e => {
  if (!ctx.media?.can_seek || !ctx.media.duration_ms) return;
  track.setPointerCapture(e.pointerId);
  scrubbing = { ms: msAt(e.clientX) };
  $('#seek').classList.add('scrubbing'); $('#scrub-hint').hidden = false; $('#scrub-hint').textContent = fmt(scrubbing.ms);
  buzz(4); tick();
});
track.addEventListener('pointermove', e => { if (!scrubbing) return; scrubbing.ms = msAt(e.clientX); $('#scrub-hint').textContent = fmt(scrubbing.ms); tick(); });
function endScrub(commit) {
  if (!scrubbing) return;
  const target = scrubbing.ms;
  scrubbing = null; $('#seek').classList.remove('scrubbing'); $('#scrub-hint').hidden = true;
  if (commit && ctx.media) { ctx.media.position_ms = target; ctx.server_time = ctx.updated_at; receivedAt = performance.now(); buzz(); media('seek', target); }
  tick();
}
track.addEventListener('pointerup', () => endScrub(true));
track.addEventListener('pointercancel', () => endScrub(false));
track.addEventListener('keydown', e => {
  if (ctx.media?.can_seek && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) { e.preventDefault(); e.stopPropagation(); skip(e.key === 'ArrowLeft' ? -10000 : 10000); }
});

/* ---- D-pad: tap an edge, swipe, hold to repeat -------------------------------------------------------- */
const pad = $('#pad');
let touch = null, repeatTimer = null;
function light(dir) {
  const el = dir === 'ok' ? $('#ok') : pad.querySelector('.edge.' + dir);
  if (!el) return;
  el.classList.add('lit'); setTimeout(() => el.classList.remove('lit'), 160);
}
function press(dir) { light(dir); buzz(); key(dir); }
function zoneAt(x, y) {
  const r = pad.getBoundingClientRect(), dx = x - (r.left + r.width / 2), dy = y - (r.top + r.height / 2);
  if (Math.hypot(dx, dy) < r.width * 0.2) return 'ok';
  return Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up');
}
pad.addEventListener('pointerdown', e => {
  pad.setPointerCapture(e.pointerId); pad.classList.add('pressed');
  const r = pad.getBoundingClientRect(), ripple = $('#ripple');
  ripple.style.left = e.clientX - r.left + 'px'; ripple.style.top = e.clientY - r.top + 'px';
  ripple.classList.remove('go'); void ripple.offsetWidth; ripple.classList.add('go');
  touch = { x: e.clientX, y: e.clientY, zone: zoneAt(e.clientX, e.clientY), moved: false, repeated: false };
  if (touch.zone !== 'ok') {
    repeatTimer = setTimeout(function repeat() {
      if (!touch || touch.moved) return;
      touch.repeated = true; press(touch.zone);
      repeatTimer = setTimeout(repeat, 170);
    }, 420);
  }
});
pad.addEventListener('pointermove', e => { if (touch && Math.hypot(e.clientX - touch.x, e.clientY - touch.y) > 14) touch.moved = true; });
pad.addEventListener('pointerup', e => {
  clearTimeout(repeatTimer); pad.classList.remove('pressed');
  if (!touch) return;
  const dx = e.clientX - touch.x, dy = e.clientY - touch.y;
  if (Math.hypot(dx, dy) > 26) press(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up'));
  else if (!touch.repeated) press(touch.zone);
  touch = null;
});
pad.addEventListener('pointercancel', () => { clearTimeout(repeatTimer); pad.classList.remove('pressed'); touch = null; });

/* ---- Trackpad: drag moves step by step, flick moves further, tap selects ---------------------------- */
const tp = $('#trackpad'), blob = $('#blob');
const STEPS = { slow: 76, normal: 58, fast: 44 };
let tpTouch = null;
function blobAt(x, y) { const r = tp.getBoundingClientRect(); blob.style.left = x - r.left + 'px'; blob.style.top = y - r.top + 'px'; }
tp.addEventListener('pointerdown', e => {
  tp.setPointerCapture(e.pointerId); tp.classList.add('touched');
  tpTouch = { x: e.clientX, y: e.clientY, ax: e.clientX, ay: e.clientY, t: performance.now(), moved: false, trail: [[e.clientX, e.clientY, performance.now()]] };
  blobAt(e.clientX, e.clientY); blob.classList.add('on');
});
tp.addEventListener('pointermove', e => {
  if (!tpTouch) return;
  blobAt(e.clientX, e.clientY);
  tpTouch.trail.push([e.clientX, e.clientY, performance.now()]);
  tpTouch.lx = e.clientX; tpTouch.ly = e.clientY;
  if (tpTouch.trail.length > 8) tpTouch.trail.shift();
  // Acceleration: a quick swipe takes each step over less distance, so one swipe can travel further than
  // the pad is long. A slow, deliberate drag keeps the full step for precision.
  const tr = tpTouch.trail, [px, py, pt] = tr[Math.max(0, tr.length - 4)], now = performance.now();
  const v = now - pt > 0 ? Math.hypot(e.clientX - px, e.clientY - py) / (now - pt) : 0;
  const gain = Math.min(2, Math.max(1, 1 + (v - 0.45) * 1.2));
  const STEP = (STEPS[prefs.speed] || STEPS.normal) / gain;
  let dx = e.clientX - tpTouch.ax, dy = e.clientY - tpTouch.ay;
  // The first step needs a little more travel, so a slightly wobbly tap never moves the selection.
  const first = tpTouch.moved ? 1 : 1.25;
  // Axis lock: once moving along one axis, small drift on the other is ignored.
  if (!tpTouch.axis && (Math.abs(dx) > STEP * 0.6 || Math.abs(dy) > STEP * 0.6)) tpTouch.axis = Math.abs(dx) >= Math.abs(dy) ? 'x' : 'y';
  if (tpTouch.axis === 'x' && Math.abs(dy) > STEP * 1.6) { tpTouch.axis = 'y'; tpTouch.ax = e.clientX; }
  else if (tpTouch.axis === 'y' && Math.abs(dx) > STEP * 1.6) { tpTouch.axis = 'x'; tpTouch.ay = e.clientY; }
  while (tpTouch.axis === 'x' ? Math.abs(dx) >= STEP * first : tpTouch.axis === 'y' ? Math.abs(dy) >= STEP * first : false) {
    tpTouch.moved = true;
    if (tpTouch.axis === 'x') { key(dx > 0 ? 'right' : 'left'); tpTouch.ax += Math.sign(dx) * STEP; }
    else { key(dy > 0 ? 'down' : 'up'); tpTouch.ay += Math.sign(dy) * STEP; }
    buzz(5);
    dx = e.clientX - tpTouch.ax; dy = e.clientY - tpTouch.ay;
    if (Math.abs(tpTouch.axis === 'x' ? dx : dy) < STEP) break;
  }
  edgeCheck(e);
});
// Holding the finger at the edge after a swipe keeps moving that way, faster the longer it's held.
const EDGE = 34;
function edgeDir(e) {
  if (!tpTouch?.moved || !tpTouch.axis) return null;
  const r = tp.getBoundingClientRect();
  if (tpTouch.axis === 'y') return e.clientY > r.bottom - EDGE ? 'down' : e.clientY < r.top + EDGE ? 'up' : null;
  return e.clientX > r.right - EDGE ? 'right' : e.clientX < r.left + EDGE ? 'left' : null;
}
function edgeStop() { clearTimeout(tpTouch?.edgeTimer); if (tpTouch) tpTouch.edge = null; tp.classList.remove('edging'); }
function edgeCheck(e) {
  const dir = edgeDir(e);
  if (dir === tpTouch.edge) return;
  edgeStop();
  if (!dir) return;
  tpTouch.edge = dir; tp.classList.add('edging');
  let delay = 260;
  const tick = () => {
    if (!tpTouch || tpTouch.edge !== dir) return;
    key(dir); buzz(5);
    // keep the step anchor at the finger so moving back away from the edge doesn't fire extra steps
    tpTouch.ax = tpTouch.lx; tpTouch.ay = tpTouch.ly;
    delay = Math.max(90, delay * 0.8);
    tpTouch.edgeTimer = setTimeout(tick, delay);
  };
  tpTouch.edgeTimer = setTimeout(tick, 320);   // a short pause first, so ending a swipe at the edge doesn't overshoot
}
tp.addEventListener('pointerup', e => {
  if (!tpTouch) return;
  const wasEdging = !!tpTouch.edge; edgeStop();
  const total = Math.hypot(e.clientX - tpTouch.x, e.clientY - tpTouch.y);
  if (!tpTouch.moved && total < 12 && performance.now() - tpTouch.t < 400) {
    blob.classList.add('tap'); setTimeout(() => blob.classList.remove('tap'), 140);
    buzz(10); key('ok');
  } else {
    // Flick: carry on a couple of steps when the finger leaves quickly.
    const [x0, y0, t0] = tpTouch.trail[0], vx = (e.clientX - x0) / (performance.now() - t0 || 1), vy = (e.clientY - y0) / (performance.now() - t0 || 1);
    const v = Math.hypot(vx, vy);
    const flick = { slow: 1.8, normal: 1.5, fast: 1.2 }[prefs.speed] || 1.5;
    if (v > flick && !wasEdging) {
      const dir = Math.abs(vx) > Math.abs(vy) ? (vx > 0 ? 'right' : 'left') : (vy > 0 ? 'down' : 'up');
      const extra = v > flick * 2 ? 3 : 1;
      for (let i = 0; i < extra; i++) key(dir);
    }
  }
  setTimeout(() => blob.classList.remove('on'), 120);
  tpTouch = null;
});
tp.addEventListener('pointercancel', () => { edgeStop(); blob.classList.remove('on'); tpTouch = null; });

function applyMode() {
  const track = prefs.mode === 'trackpad';
  $('#pad').hidden = track; $('#trackpad').hidden = !track;
  $$('#mode-seg button').forEach(b => b.setAttribute('aria-checked', String(b.dataset.mode === prefs.mode)));
  $$('#speed-seg button').forEach(b => b.setAttribute('aria-checked', String(b.dataset.speed === prefs.speed)));
  $('#speed-block').hidden = !track;
  $('#tip').hidden = prefs.tipSeen || track;
}
$$('#mode-seg button').forEach(b => b.addEventListener('click', () => {
  buzz(); prefs.mode = b.dataset.mode; prefs.tipSeen = true; savePrefs(); applyMode();
}));
$$('#speed-seg button').forEach(b => b.addEventListener('click', () => { buzz(); prefs.speed = b.dataset.speed; savePrefs(); applyMode(); }));
$('#tip-try').addEventListener('click', () => { prefs.mode = 'trackpad'; prefs.tipSeen = true; savePrefs(); applyMode(); toast('Trackpad on. Switch back any time in remote settings.'); });
$('#tip-close').addEventListener('click', () => { prefs.tipSeen = true; savePrefs(); applyMode(); });

/* ---- Keys, volume, power ------------------------------------------------------------------------- */
$$('[data-key]').forEach(b => b.addEventListener('click', () => { buzz(); key(b.dataset.key); }));
$$('[data-volume-input]').forEach(input => {
  input.addEventListener('pointerdown', () => (volumeEditing = true));
  input.addEventListener('input', () => {
    $$('[data-volume-input]').forEach(o => { o.value = input.value; paintRange(o); });
    $$('[data-volume-value]').forEach(o => (o.textContent = input.value));
    sendVolume(Number(input.value));   // the TV follows the thumb while dragging
  });
  input.addEventListener('change', async () => { await sendVolume(Number(input.value)); volumeEditing = false; });
  ['pointercancel', 'blur'].forEach(ev => input.addEventListener(ev, () => (volumeEditing = false)));
});
// Only one volume request at a time; values reached while it's in flight collapse into the latest one.
let volumeWanted = null, volumeSending = null;
function sendVolume(value) {
  volumeWanted = value;
  return volumeSending ||= (async () => {
    while (volumeWanted !== null) {
      const v = volumeWanted; volumeWanted = null;
      if (v === ctx.volume) continue;
      if (await post('volume', { value: v }, { direct: true })) ctx.volume = v;
    }
    volumeSending = null;
  })();
}
async function togglePower() {
  if (powerBusy) return;
  powerBusy = true; buzz(14); render();
  const result = await post('power', {}, { timeout: 25000, direct: true });
  powerBusy = false;
  if (result) { ctx.power = result.power; ctx.connected = true; toast(result.message); }
  render();
}
$('#power').addEventListener('click', togglePower);
$('#wake').addEventListener('click', togglePower);
$('#status').addEventListener('click', async () => {
  if (ctx.connected) { goPage(ctx.media ? 0 : 1); return; }
  toast('Reconnecting to the TV');
  if (await post('reconnect', {}, { direct: true })) toast('Connected');
});
$('#qs-reconnect').addEventListener('click', async () => { if (await post('reconnect', {}, { direct: true })) toast('Connected'); });

/* ---- Apps ----------------------------------------------------------------------------------------- */
async function loadApps(force = false) {
  try {
    const data = await getJSON('/api/apps' + (force ? '?refresh=1' : ''), 12000);
    if (!Array.isArray(data.apps)) throw new Error();
    apps = data.apps;
    try { localStorage.setItem('remote-apps', JSON.stringify(apps)); } catch {}
    renderApps(); renderShortcuts(); renderRecent(); render();
  } catch {
    if (apps.length) { renderApps(); renderShortcuts(); renderRecent(); render(); }
    else {
      $('#apps-list').innerHTML = '<p class="muted">Getting the app list from the TV. This retries on its own.</p>';
      setTimeout(() => loadApps(), 15000);
    }
  }
}
function appImage(a, cls = '') {
  const img = new Image();
  img.alt = ''; img.loading = 'lazy'; img.decoding = 'async';
  if (a.logo) { img.src = a.logo; img.className = 'logo' + (KNOWN[a.package]?.square ? ' square' : '') + (cls ? ' ' + cls : ''); }
  else { img.src = a.icon; img.className = 'banner' + (cls ? ' ' + cls : ''); }
  img.onerror = () => { img.remove(); };   // the tile or chip still shows the app's name
  return img;
}
const isPinned = pkg => config.shortcuts.some(s => s.type === 'app' && s.package === pkg);
function renderApps() {
  const q = $('#app-search').value.trim().toLowerCase();
  const list = $('#apps-list');
  const filtered = apps.filter(a => !q || a.name.toLowerCase().includes(q));
  if (!filtered.length) { list.innerHTML = `<p class="muted">No apps match “${q.replace(/[<>&]/g, '')}”.</p>`; return; }
  const groups = {};
  if (!q) groups['Recently used'] = [...apps].filter(a => a.last_used && a.category !== 'TV tools' && !a.package.startsWith('com.livingroom.')).sort((a, b) => b.last_used - a.last_used).slice(0, 6);
  for (const a of filtered) (groups[a.category] ||= []).push(a);
  let n = 0;
  list.replaceChildren(...Object.entries(groups).filter(([, v]) => v.length).map(([title, items]) => {
    const sec = document.createElement('section'); sec.className = 'cat';
    const h = document.createElement('h3'); h.textContent = title;
    const grid = document.createElement('div'); grid.className = 'grid';
    for (const a of items) grid.append(appTile(a, n++));
    sec.append(h, grid);
    return sec;
  }));
}
function appTile(a, i) {
  const b = document.createElement('button');
  b.type = 'button'; b.className = 'tile' + (isPinned(a.package) ? ' pinned' : ''); b.style.setProperty('--i', Math.min(i, 24));
  b.setAttribute('aria-label', a.name);
  b.append(appImage(a));
  if (!a.logo) { const l = document.createElement('span'); l.className = 'label'; l.textContent = a.name; b.append(l); }
  let hold = null, held = false;
  b.addEventListener('pointerdown', () => { held = false; hold = setTimeout(() => { held = true; buzz(20); openAppSheet(a); }, 480); });
  ['pointerup', 'pointerleave', 'pointercancel'].forEach(ev => b.addEventListener(ev, () => clearTimeout(hold)));
  b.addEventListener('contextmenu', e => e.preventDefault());
  b.addEventListener('click', () => { if (!held) launchApp(a, b); });
  return b;
}
async function launchApp(a, el) {
  buzz();
  if (el) { el.classList.remove('launching'); void el.offsetWidth; el.classList.add('launching'); }
  if (await post('launch', { package: a.package })) toast('Opening ' + a.name);
}
$('#app-search').addEventListener('input', renderApps);
let sheetApp = null;
function openAppSheet(a) {
  sheetApp = a;
  $('#as-title').textContent = a.name;
  $('#as-shortcut').textContent = isPinned(a.package) ? 'Remove from shortcuts' : 'Add to shortcuts';
  openSheet($('#app-sheet'));
}
$('#as-open').addEventListener('click', () => { closeSheet($('#app-sheet')); launchApp(sheetApp); });
$('#as-shortcut').addEventListener('click', async () => {
  const pkg = sheetApp.package;
  const shortcuts = isPinned(pkg) ? config.shortcuts.filter(s => !(s.type === 'app' && s.package === pkg)) : [...config.shortcuts, { type: 'app', package: pkg }];
  closeSheet($('#app-sheet'));
  if (await saveConfig({ ...config, shortcuts })) toast(isPinned(pkg) ? `${sheetApp.name} added to shortcuts` : `${sheetApp.name} removed from shortcuts`);
});
function renderRecent() {
  const recent = [...apps].filter(a => a.last_used && a.category !== 'TV tools' && !a.package.startsWith('com.livingroom.')).sort((a, b) => b.last_used - a.last_used).slice(0, 4);
  $('#recent-row').replaceChildren(...recent.map((a, i) => appTile(a, i)));
}

/* ---- Shortcuts --------------------------------------------------------------------------------------- */
function describeShortcut(s) {
  if (s.type === 'app') { const a = appByPkg(s.package) || { package: s.package, name: s.package, icon: '/api/icon?pkg=' + encodeURIComponent(s.package) }; return { name: a.name, app: a }; }
  if (s.type === 'input') return { name: s.name || `HDMI ${s.port}`, icon: s.port === 1 ? 'i-gamepad' : 'i-input' };
  if (s.type === 'action') return { name: ACTION_INFO[s.action]?.[0] || s.action, icon: ACTION_INFO[s.action]?.[1] || 'i-settings' };
  if (s.type === 'routine') { const r = config.routines.find(x => x.id === s.id); return { name: r?.name || 'Routine', icon: 'i-routine', missing: !r }; }
  return { name: '?' };
}
const iconSvg = id => `<svg><use href="#${id}"/></svg>`;
function runShortcut(s, el) {
  buzz();
  if (s.type === 'app') { const a = appByPkg(s.package); return a ? launchApp(a) : post('launch', { package: s.package }); }
  if (s.type === 'input') return post('input', { port: s.port }).then(ok => ok && toast('Switching to ' + (s.name || 'HDMI ' + s.port)));
  if (s.type === 'action') return post('action', { action: s.action });
  if (s.type === 'routine') return runRoutine(s.id, el);
}
function renderShortcuts() {
  const row = $('#shortcuts');
  const items = config.shortcuts.map(s => {
    const d = describeShortcut(s);
    if (d.missing) return null;
    const b = document.createElement('button'); b.type = 'button'; b.className = 'shortcut';
    b.setAttribute('aria-label', d.name);
    if (d.app?.logo) b.append(appImage(d.app));
    else if (d.app) { const img = appImage(d.app); img.onerror = () => { img.remove(); b.textContent = d.name; }; b.append(img); }
    else { b.innerHTML = iconSvg(d.icon); b.append(document.createTextNode(d.name)); }
    b.addEventListener('click', () => runShortcut(s, b));
    return b;
  }).filter(Boolean);
  const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'shortcut edit';
  edit.setAttribute('aria-label', 'Edit shortcuts'); edit.innerHTML = iconSvg('i-pencil');
  edit.addEventListener('click', openShortcutEditor);
  row.replaceChildren(...items, edit);
}
async function loadConfig() {
  try {
    const data = await getJSON('/api/config');
    if (Array.isArray(data.shortcuts) && Array.isArray(data.routines)) { config = data; renderShortcuts(); renderRoutines(); }
  } catch { setTimeout(loadConfig, 10000); }
}
async function saveConfig(next) {
  const saved = await post('config', next, { direct: true });
  if (saved && Array.isArray(saved.shortcuts)) { config = saved; renderShortcuts(); renderRoutines(); renderShortcutEditor(); if (apps.length) renderApps(); }
  return saved;
}

function openShortcutEditor() { buzz(); renderShortcutEditor(); openSheet($('#shortcut-sheet')); }
function renderShortcutEditor() {
  const list = $('#sc-list');
  list.replaceChildren(...config.shortcuts.map((s, i) => {
    const d = describeShortcut(s), li = document.createElement('li');
    const name = document.createElement('span'); name.className = 'name';
    if (d.app) name.append(appImage(d.app)); else name.innerHTML = iconSvg(d.icon || 'i-spark');
    name.append(document.createTextNode(d.name));
    li.append(name);
    const btn = (icon, label, fn) => { const b = document.createElement('button'); b.type = 'button'; b.innerHTML = iconSvg(icon); b.setAttribute('aria-label', label); b.addEventListener('click', fn); li.append(b); };
    btn('i-up', 'Move up', () => move(i, -1));
    btn('i-down', 'Move down', () => move(i, 1));
    btn('i-trash', 'Remove', () => saveConfig({ ...config, shortcuts: config.shortcuts.filter((_, j) => j !== i) }));
    return li;
  }));
  function move(i, d) {
    const s = [...config.shortcuts], j = i + d;
    if (j < 0 || j >= s.length) return;
    [s[i], s[j]] = [s[j], s[i]];
    saveConfig({ ...config, shortcuts: s });
  }
  // Add options: apps not yet pinned, inputs, settings pages, routines
  const add = $('#sc-add'), opts = [];
  const has = s2 => config.shortcuts.some(s => JSON.stringify(s) === JSON.stringify(s2));
  for (const r of config.routines) { const s = { type: 'routine', id: r.id }; if (!has(s)) opts.push([s, r.name, 'i-routine']); }
  for (const p of [1, 2, 3]) { const s = { type: 'input', port: p, name: p === 1 ? 'PlayStation 5' : `HDMI ${p}` }; if (!config.shortcuts.some(x => x.type === 'input' && x.port === p)) opts.push([s, s.name, p === 1 ? 'i-gamepad' : 'i-input']); }
  for (const [a, [label, icon]] of Object.entries(ACTION_INFO)) { const s = { type: 'action', action: a }; if (!has(s)) opts.push([s, label, icon]); }
  for (const a of [...apps].sort((x, y) => (y.last_used - x.last_used))) { const s = { type: 'app', package: a.package }; if (!isPinned(a.package)) opts.push([s, a.name, null, a]); }
  add.replaceChildren(...opts.map(([s, label, icon, a]) => {
    const c = document.createElement('button'); c.type = 'button'; c.className = 'chip';
    if (a) c.append(appImage(a)); else c.innerHTML = iconSvg(icon);
    c.append(document.createTextNode(label));
    c.addEventListener('click', () => { buzz(); if (config.shortcuts.length >= 16) return toast('Up to 16 shortcuts', true); saveConfig({ ...config, shortcuts: [...config.shortcuts, s] }); });
    return c;
  }));
}
$('#edit-shortcuts').addEventListener('click', () => { closeSheet($('#remote-sheet')); setTimeout(openShortcutEditor, 280); });

/* ---- Routines ------------------------------------------------------------------------------------- */
const STEP_LABELS = {
  power_on: 'Turn on TV', power_off: 'Turn off TV', volume: 'Set volume', app: 'Open app', input: 'Switch input',
  wait: 'Wait', sleep_timer: 'Sleep timer', key: 'Press a key', action: 'Open settings', open: 'Open a link',
};
function describeStep(s) {
  switch (s.do) {
    case 'volume': return `Volume ${s.value}`;
    case 'app': return 'Open ' + (appByPkg(s.package)?.name || 'app');
    case 'input': return `Switch to HDMI ${s.port}`;
    case 'wait': return `Wait ${s.seconds}s`;
    case 'sleep_timer': return `Sleep in ${s.minutes} min`;
    case 'key': return 'Press ' + (KEY_NAMES[s.key] || s.key);
    case 'action': return 'Open ' + (ACTION_INFO[s.action]?.[0] || s.action).toLowerCase();
    case 'open': return 'Open link';
    default: return STEP_LABELS[s.do] || s.do;
  }
}
function renderRoutines() {
  $('#routine-list').replaceChildren(...config.routines.map(r => {
    const row = document.createElement('div'); row.className = 'routine';
    const t = document.createElement('strong'); t.textContent = r.name;
    const sm = document.createElement('small'); sm.textContent = r.steps.map(describeStep).join(', ');
    t.append(sm);
    const run = document.createElement('button'); run.type = 'button'; run.className = 'run'; run.textContent = 'Run';
    run.addEventListener('click', () => { closeSheet($('#settings-sheet')); runRoutine(r.id); });
    const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'edit'; edit.textContent = 'Edit';
    edit.addEventListener('click', () => openRoutineEditor(r));
    row.append(t, edit, run);
    return row;
  }));
}
async function runRoutine(id, el) {
  buzz(14);
  el?.classList.add('running');
  const ok = await post('routine', { id }, { direct: true });
  setTimeout(() => el?.classList.remove('running'), 1500);
  if (ok) poll();
}
let editing = null;
function openRoutineEditor(r) {
  editing = r ? JSON.parse(JSON.stringify(r)) : { id: '', name: '', steps: [{ do: 'power_on' }] };
  $('#rt-name').value = editing.name;
  $('#rt-delete').hidden = !r;
  renderSteps();
  const qs = $('#settings-sheet');
  if (qs.open) { closeSheet(qs); setTimeout(() => openSheet($('#routine-sheet')), 280); } else openSheet($('#routine-sheet'));
}
function stepField(step) {
  const sel = (options, prop) => {
    const s = document.createElement('select');
    for (const [v, l] of options) { const o = document.createElement('option'); o.value = v; o.textContent = l; s.append(o); }
    s.value = String(step[prop] ?? options[0][0]);
    step[prop] = isNaN(+s.value) || prop === 'package' || prop === 'key' || prop === 'action' ? s.value : +s.value;
    s.addEventListener('change', () => { step[prop] = prop === 'port' ? +s.value : s.value; });
    return s;
  };
  const num = (prop, min, max, def) => {
    const i = document.createElement('input'); i.type = 'number'; i.inputMode = 'numeric'; i.min = min; i.max = max;
    step[prop] ??= def; i.value = step[prop];
    i.addEventListener('input', () => { step[prop] = Math.max(min, Math.min(max, +i.value || 0)); });
    return i;
  };
  switch (step.do) {
    case 'volume': return num('value', 0, 100, 20);
    case 'wait': return num('seconds', 1, 60, 3);
    case 'sleep_timer': return num('minutes', 5, 240, 30);
    case 'input': return sel([[1, 'HDMI 1 (PS5)'], [2, 'HDMI 2'], [3, 'HDMI 3']], 'port');
    case 'app': return sel(apps.map(a => [a.package, a.name]).concat(apps.length ? [] : [['com.netflix.ninja', 'Netflix']]), 'package');
    case 'key': return sel(Object.entries(KEY_NAMES), 'key');
    case 'action': return sel(Object.entries(ACTION_INFO).map(([k, [l]]) => [k, l]), 'action');
    case 'open': { const i = document.createElement('input'); i.type = 'url'; i.placeholder = 'https://youtube.com/…'; i.value = step.url || ''; i.addEventListener('input', () => (step.url = i.value.trim())); return i; }
    default: return null;
  }
}
function renderSteps() {
  $('#rt-steps').replaceChildren(...editing.steps.map((step, i) => {
    const li = document.createElement('li'); li.className = 'step';
    const name = document.createElement('span'); name.className = 'name'; name.textContent = STEP_LABELS[step.do];
    li.append(name);
    const field = stepField(step); if (field) li.append(field);
    const btn = (icon, label, fn) => { const b = document.createElement('button'); b.type = 'button'; b.innerHTML = iconSvg(icon); b.setAttribute('aria-label', label); b.addEventListener('click', fn); li.append(b); };
    btn('i-up', 'Move up', () => { if (i) { [editing.steps[i - 1], editing.steps[i]] = [editing.steps[i], editing.steps[i - 1]]; renderSteps(); } });
    btn('i-trash', 'Remove step', () => { editing.steps.splice(i, 1); renderSteps(); });
    return li;
  }));
  $('#rt-add').replaceChildren(...Object.entries(STEP_LABELS).map(([k, l]) => {
    const c = document.createElement('button'); c.type = 'button'; c.className = 'chip'; c.textContent = l;
    c.addEventListener('click', () => { if (editing.steps.length >= 12) return toast('Up to 12 steps', true); editing.steps.push({ do: k }); renderSteps(); });
    return c;
  }));
}
$('#new-routine').addEventListener('click', () => openRoutineEditor(null));
$('#rt-save').addEventListener('click', async () => {
  editing.name = $('#rt-name').value.trim();
  if (!editing.name) return toast('Give the routine a name', true);
  if (!editing.steps.length) return toast('Add at least one step', true);
  if (editing.steps.some(s => s.do === 'open' && !/^https?:\/\//.test(s.url || ''))) return toast('Links must start with https://', true);
  editing.id ||= editing.name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') + '-' + Date.now().toString(36).slice(-4);
  const routines = config.routines.some(r => r.id === editing.id) ? config.routines.map(r => (r.id === editing.id ? editing : r)) : [...config.routines, editing];
  if (await saveConfig({ ...config, routines })) { closeSheet($('#routine-sheet')); toast('Saved ' + editing.name); }
});
$('#rt-delete').addEventListener('click', async () => {
  const id = editing.id;
  const next = { routines: config.routines.filter(r => r.id !== id), shortcuts: config.shortcuts.filter(s => !(s.type === 'routine' && s.id === id)) };
  if (await saveConfig(next)) { closeSheet($('#routine-sheet')); toast('Deleted'); }
});

/* ---- TV controls and remote settings ------------------------------------------------------------------------------- */
$('#open-settings').addEventListener('click', () => { buzz(); renderRoutines(); openSheet($('#settings-sheet')); });
function openRemoteSettings() {
  applyMode();
  $('#pref-haptics').checked = prefs.haptics; $('#pref-notify').checked = prefs.notify; $('#pref-lock').checked = prefs.lock;
  openSheet($('#remote-sheet'));
}
$('#open-remote-settings').addEventListener('click', () => { buzz(); closeSheet($('#settings-sheet')); setTimeout(openRemoteSettings, 280); });
$('#rs-back').addEventListener('click', () => { buzz(); closeSheet($('#remote-sheet')); setTimeout(() => { renderRoutines(); openSheet($('#settings-sheet')); }, 280); });
$$('[data-tv-action]').forEach(b => b.addEventListener('click', async () => {
  buzz(); closeSheet($('#settings-sheet'));
  if (await post('action', { action: b.dataset.tvAction })) toast(b.textContent.trim() + ' opened on the TV');
}));
$('#pref-haptics').addEventListener('change', e => { prefs.haptics = e.target.checked; savePrefs(); buzz(); });
$('#pref-notify').addEventListener('change', e => { prefs.notify = e.target.checked; savePrefs(); });
$('#pref-lock').addEventListener('change', e => { prefs.lock = e.target.checked; savePrefs(); setupLockScreen(); });
$$('[data-input]').forEach(b => b.addEventListener('click', async () => {
  buzz(); closeSheet($('#settings-sheet'));
  if (await post('input', { port: +b.dataset.input })) toast('Switching to ' + b.textContent.trim());
}));
$$('[data-sleep]').forEach(b => b.addEventListener('click', async () => {
  buzz();
  const minutes = +b.dataset.sleep;
  if (await post('sleep', { minutes }, { direct: true })) { toast(minutes ? `TV turns off in ${b.textContent}` : 'Sleep timer off'); poll(); }
}));

/* ---- Keyboard: type on the phone, the TV's text box follows live ------------------------------------- */
const kbSheet = $('#keyboard-sheet'), kbInput = $('#kb-text');
let kbTimer = null, kbPoll = null;
function kbStatus(text, live = false) { $('#kb-status').textContent = text; $('#kb-status').classList.toggle('live', live); }
async function kbOpen() {
  buzz(); openSheet(kbSheet); kbInput.value = ''; kbStatus('Connecting to the TV keyboard');
  setTimeout(() => kbInput.focus(), 120);
  const state = await post('keyboard', { op: 'open' }, { direct: true, quiet: true });
  kbApply(state);
}
function kbApply(state) {
  clearTimeout(kbPoll);
  if (!kbSheet.open) return;
  if (state?.active) {
    if (!kbInput.value && state.text) kbInput.value = state.text;
    kbStatus(state.password ? 'Typing into a password field' : 'Typing live on the TV' + (state.hint ? ` (${state.hint})` : ''), true);
  } else {
    kbStatus('Select a text box on the TV. You can also search all of Google TV below.');
    kbPoll = setTimeout(async () => kbApply(await post('keyboard', { op: 'state' }, { direct: true, quiet: true })), 1200);
  }
}
kbInput.addEventListener('input', () => {
  clearTimeout(kbTimer);
  kbTimer = setTimeout(() => post('keyboard', { op: 'set', text: kbInput.value }, { direct: true, quiet: true }), 180);
});
$('#keyboard-form').addEventListener('submit', async e => {
  e.preventDefault(); clearTimeout(kbTimer);
  await post('keyboard', { op: 'set', text: kbInput.value }, { direct: true, quiet: true });
  await post('keyboard', { op: 'submit' }, { direct: true, quiet: true });
  closeSheet(kbSheet);
});
$('#kb-global').addEventListener('click', async () => {
  const text = kbInput.value.trim();
  if (!text) return toast('Type what to search for', true);
  closeSheet(kbSheet);
  if (await post('search', { text }, { direct: true })) toast(`Searching for “${text}”`);
});
kbSheet.addEventListener('close', () => { clearTimeout(kbPoll); post('keyboard', { op: 'close' }, { direct: true, quiet: true }); });
$('#open-keyboard').addEventListener('click', kbOpen);

/* ---- Voice search ------------------------------------------------------------------------------------- */
const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
let recognizer = null;
async function voiceSearch(text) {
  closeSheet($('#voice-sheet'));
  if (await post('search', { text }, { direct: true })) toast(`Searching for “${text}”`);
}
$('#open-voice').addEventListener('click', () => {
  buzz(); openSheet($('#voice-sheet'));
  const orb = $('#voice-orb'), out = $('#voice-text'), form = $('#voice-form');
  form.hidden = true;
  if (!Recognition) {
    orb.classList.remove('listening'); out.textContent = 'This browser cannot listen. Type your search instead.'; form.hidden = false;
    setTimeout(() => $('#voice-input').focus(), 150); return;
  }
  recognizer = new Recognition();
  recognizer.lang = navigator.language || 'en-US'; recognizer.interimResults = true; recognizer.maxAlternatives = 1;
  let finalText = '';
  recognizer.onstart = () => { orb.classList.add('listening'); out.textContent = 'Listening'; };
  recognizer.onresult = e => {
    const text = [...e.results].map(r => r[0].transcript).join('');
    out.textContent = text;
    if (e.results[e.results.length - 1].isFinal) finalText = text.trim();
  };
  recognizer.onerror = e => { orb.classList.remove('listening'); out.textContent = e.error === 'not-allowed' ? 'Microphone access is blocked. Type instead.' : 'Didn’t catch that. Type instead, or try again.'; form.hidden = false; };
  recognizer.onend = () => { orb.classList.remove('listening'); if (finalText) voiceSearch(finalText); };
  try { recognizer.start(); } catch { out.textContent = 'Voice search could not start. Type instead.'; form.hidden = false; }
});
$('#voice-sheet').addEventListener('close', () => { try { recognizer?.abort(); } catch {} });
$('#voice-form').addEventListener('submit', e => { e.preventDefault(); const t = $('#voice-input').value.trim(); if (t) voiceSearch(t); });

/* ---- TV notifications on the phone -------------------------------------------------------------------- */
const seenNotes = new Set();
let notesPrimed = false, bannerTimer = null;
function showNotifications() {
  const list = ctx.notifications || [];
  if (!notesPrimed) { list.forEach(n => seenNotes.add(n.key)); notesPrimed = !!ctx.connected; return; }
  const fresh = list.filter(n => !seenNotes.has(n.key));
  fresh.forEach(n => seenNotes.add(n.key));
  if (!prefs.notify || !fresh.length) return;
  const n = fresh[0];
  $('#banner-title').textContent = [n.app, n.title].filter(Boolean).join(': ');
  $('#banner-text').textContent = n.text || '';
  const b = $('#banner'); b.hidden = true; void b.offsetWidth; b.hidden = false;
  clearTimeout(bannerTimer); bannerTimer = setTimeout(() => (b.hidden = true), 5500);
}
$('#banner').addEventListener('click', () => ($('#banner').hidden = true));

/* ---- Lock screen controls (experimental): a silent loop keeps a media session alive ------------------ */
function silentWav() {
  // Chrome on Android only shows media controls for media of 5 s or more; use 20 s of silence.
  const rate = 4000, n = rate * 20, buf = new Uint8Array(44 + n);
  const w = (o, s) => [...s].forEach((c, i) => (buf[o + i] = c.charCodeAt(0)));
  const u32 = (o, v) => { buf[o] = v; buf[o + 1] = v >> 8; buf[o + 2] = v >> 16; buf[o + 3] = v >> 24; };
  w(0, 'RIFF'); u32(4, 36 + n); w(8, 'WAVEfmt '); u32(16, 16); buf[20] = 1; buf[22] = 1; u32(24, rate); u32(28, rate); buf[32] = 1; buf[34] = 8; w(36, 'data'); u32(40, n);
  buf.fill(128, 44);
  let s = ''; buf.forEach(b => (s += String.fromCharCode(b)));
  return 'data:audio/wav;base64,' + btoa(s);
}
const lockAudio = $('#lock-audio');
function setupLockScreen() {
  if (!('mediaSession' in navigator)) { if (prefs.lock) toast('This browser has no lock screen controls', true); return; }
  if (prefs.lock) {
    // The silent track is only loaded while the TV has media (see updateLockScreen).
    const ms = navigator.mediaSession;
    ms.setActionHandler('play', () => { if (!isPlaying()) togglePlay(); });
    ms.setActionHandler('pause', () => { if (isPlaying()) togglePlay(); });
    ms.setActionHandler('nexttrack', () => (KNOWN[ctx.media?.package]?.music ? media('next') : skip(10000)));
    ms.setActionHandler('previoustrack', () => (KNOWN[ctx.media?.package]?.music ? media('previous') : skip(-10000)));
    try { ms.setActionHandler('seekto', e => media('seek', Math.round(e.seekTime * 1000))); } catch {}
  } else {
    unloadLockAudio();
    ['play', 'pause', 'nexttrack', 'previoustrack', 'seekto'].forEach(a => { try { navigator.mediaSession.setActionHandler(a, null); } catch {} });
    navigator.mediaSession.metadata = null;
  }
}
let lockSig = '';
function unloadLockAudio() {
  // Removing the source (not just pausing) is what makes Android drop the "Chrome is playing" notification.
  if (!lockAudio.getAttribute('src')) return;
  lockAudio.pause(); lockAudio.removeAttribute('src'); lockAudio.load();
  if ('mediaSession' in navigator) { navigator.mediaSession.metadata = null; navigator.mediaSession.playbackState = 'none'; }
  lockSig = '';
}
function updateLockScreen(m) {
  if (!prefs.lock || !('mediaSession' in navigator)) return;
  if (!m) { unloadLockAudio(); return; }
  if (!lockAudio.getAttribute('src')) { lockAudio.src = silentWav(); lockAudio.volume = 0.01; }
  const sig = m ? m.title + m.artist + m.art_id : '';
  if (sig !== lockSig) {
    lockSig = sig;
    navigator.mediaSession.metadata = m ? new MediaMetadata({ title: m.title, artist: m.artist || appByPkg(m.package)?.name || 'Living Room TV',
      artwork: m.art_id ? [{ src: '/api/art?id=' + encodeURIComponent(m.art_id), sizes: '512x512' }] : [{ src: '/icon-512.png', sizes: '512x512', type: 'image/png' }] }) : null;
  }
  navigator.mediaSession.playbackState = m ? (isPlaying() ? 'playing' : 'paused') : 'none';
  // Keep the phone's (silent) player in the same state as the TV.
  if (isPlaying() && lockAudio.paused) lockAudio.play().catch(() => {});
  if (!isPlaying() && !lockAudio.paused) lockAudio.pause();
  if (m && m.duration_ms && navigator.mediaSession.setPositionState) {
    try { navigator.mediaSession.setPositionState({ duration: m.duration_ms / 1000, position: Math.min(m.duration_ms, livePosition() || 0) / 1000, playbackRate: isPlaying() ? 1 : 1 }); } catch {}
  }
}

/* ---- Hardware keyboard ------------------------------------------------------------------------------ */
const KEYMAP = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right', Enter: 'ok', Escape: 'back', Backspace: 'back', h: 'home', m: 'mute', '+': 'volume_up', '=': 'volume_up', '-': 'volume_down' };
document.addEventListener('keydown', e => {
  if (document.querySelector('dialog[open]') || ['INPUT', 'SELECT', 'TEXTAREA'].includes(e.target.tagName) || e.metaKey || e.ctrlKey || e.altKey) return;
  if (e.key === ' ') { e.preventDefault(); ctx.media ? togglePlay() : key('play_pause'); return; }
  const k = KEYMAP[e.key];
  if (!k || !ctx.connected) return;
  if (e.target.tagName === 'BUTTON' && e.key === 'Enter') return;
  e.preventDefault();
  if (['up', 'down', 'left', 'right', 'ok'].includes(k)) light(k);
  key(k);
});

/* ---- Live updates: the Pi pushes the context the moment the TV changes; polling is only the fallback ---- */
let events = null, live = false;
function connectEvents() {
  if (events || !window.EventSource) return;
  events = new EventSource('/api/events');
  events.onmessage = e => {
    try { ctx = JSON.parse(e.data); receivedAt = performance.now(); live = true; render(); } catch {}
  };
  events.onerror = () => {
    live = false;
    poll();   // also lets connect.js notice "away from home" or a removed phone
    if (events?.readyState === EventSource.CLOSED) { events = null; setTimeout(() => { if (!document.hidden) connectEvents(); }, 3000); }
  };
}
function disconnectEvents() { events?.close(); events = null; live = false; }
document.addEventListener('visibilitychange', () => {
  // Stay subscribed in the background only for lock-screen controls while something plays.
  if (document.hidden) { if (!(prefs.lock && ctx.media)) disconnectEvents(); }
  else connectEvents();
});

/* ---- Start ------------------------------------------------------------------------------------------- */
const standalone = matchMedia('(display-mode: standalone)').matches || navigator.standalone;
$('#install-pill').hidden = !!standalone;
const shared = new URLSearchParams(location.search).get('shared');
if (shared) { toast(shared === 'ok' ? 'Opened on your TV' : shared, shared !== 'ok'); history.replaceState(null, '', '/'); }
if ('serviceWorker' in navigator && isSecureContext) navigator.serviceWorker.register('/sw.js').catch(() => {});
document.addEventListener('visibilitychange', () => { if (!document.hidden) poll(); });
window.addEventListener('online', poll);
matchMedia('(prefers-color-scheme: light)').addEventListener('change', () => { lastAccent = null; render(); });
applyMode();
computeLayout();
setSide(0);
requestAnimationFrame(() => { goPage(location.hash === '#apps' ? 2 : 1, { instant: true }); paintPager(); });
render();
poll();
connectEvents();
loadConfig();
loadApps();
setInterval(poll, 2000);
setInterval(() => loadApps(), 5 * 60 * 1000);
if (prefs.lock) document.addEventListener('pointerdown', setupLockScreen, { once: true });

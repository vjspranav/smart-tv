// Home mode checks with every API stubbed (no TV, no devices).
// Serve the page first: run tv-remote locally (LISTEN_HOST=127.0.0.1, ADB_PATH=/usr/bin/false), then:
//   QA_SECRET=<that copy's runtime/local-secret> node home.js [base]
// The checks pair themselves as an approved phone first (the page itself is only served to approved phones).
const { chromium } = require('playwright');
const fs = require('fs');
const BASE = process.argv[2] || 'http://127.0.0.1:8765/';
const FIXTURE = JSON.parse(fs.readFileSync('home-state.json'));
const ROUTINES = JSON.parse(fs.readFileSync('home-routines.json'));
const TV = { connected: true, power: 'on', app_name: 'YouTube', package: 'com.google.android.youtube.tv', volume: 20,
             volume_min: 0, volume_max: 100, notifications: [], media: { title: 'Lo-fi beats', playing: true } };

let TOKEN = null;
async function pairOnce() {
  const secret = fs.readFileSync(process.env.QA_SECRET, 'utf8').trim();
  const headers = { 'Content-Type': 'application/json', 'X-TV-Remote': '1' };
  const { code } = await (await fetch(new URL('/api/local-code', BASE), { method: 'POST', headers: { ...headers, 'X-Local-Secret': secret }, body: '{}' })).json();
  const r = await fetch(new URL('/api/pair', BASE), { method: 'POST', headers, body: JSON.stringify({ code, name: 'QA' }) });
  TOKEN = /remote_device=([^;]+)/.exec(r.headers.get('set-cookie'))[1];
}

function failures(errors, name) {
  return msg => errors.push(`${name}: ${msg}`);
}

async function open(browser, size, scheme, { down = false, mode = 'home', paired = true, at = '', mutate = null, time = null, reduced = false } = {}) {
  const [w, h] = size;
  const context = await browser.newContext({ viewport: { width: w, height: h }, colorScheme: scheme, deviceScaleFactor: 2,
                                       hasTouch: w < 1100, isMobile: w < 1100 });
  if (paired) await context.addCookies([{ name: 'remote_device', value: TOKEN, url: BASE }]);
  const page = await context.newPage();
  if (time) await page.clock.setFixedTime(time);
  if (reduced) await page.emulateMedia({ reducedMotion: 'reduce' });
  page.on('close', () => context.close());
  page.pingOk = true;
  const house = JSON.parse(JSON.stringify(FIXTURE));
  if (mutate) mutate(house);
  page.house = house; page.sets = []; page.runs = []; page.toggles = [];
  await page.route('**/api/**', async route => {
    const req = route.request(), url = new URL(req.url());
    if (url.pathname === '/api/ping' && page.pingDelay) await new Promise(r => setTimeout(r, page.pingDelay));
    if (url.pathname === '/api/ping') return page.pingOk ? route.fulfill({ json: { ok: true } }) : route.abort('internetdisconnected');
    if (url.pathname === '/api/pair' || url.pathname === '/api/local-code') return route.continue();
    if (url.pathname.startsWith('/api/home/')) {
      if (down) return route.fulfill({ status: 503, json: { error: "Home controls aren't reachable" } });
      if (url.pathname === '/api/home/state') {
        page.stateCalls = (page.stateCalls || 0) + 1;
        if (page.hangState) { page.hangState = false; return; }   // never answered, like a stalled connection
        if (page.stateDelay) { const wait = page.stateDelay; page.stateDelay = 0; await new Promise(r => setTimeout(r, wait)); }
        return route.fulfill({ json: house });
      }
      if (url.pathname === '/api/home/routines') return route.fulfill({ json: ROUTINES });
      if (url.pathname === '/api/home/run') { page.runs.push(req.postDataJSON()); return route.fulfill({ json: { ok: [], failed: {}, skipped: [] } }); }
      if (url.pathname === '/api/home/toggle') { page.toggles.push(req.postDataJSON()); return route.fulfill({ json: { ok: ['living-light'], failed: {}, skipped: [], power: true } }); }
      if (url.pathname === '/api/home/set') {
        const body = req.postDataJSON(); page.sets.push(body);
        const d = house.devices.find(x => x.id === body.device);
        Object.assign(d.state, body.changes); house.version++;
        return route.fulfill({ json: { ok: true, device: d } });
      }
    }
    if (url.pathname === '/api/devices' && req.method() === 'POST' && req.postDataJSON().action === 'key')
      return route.fulfill({ json: { key: 'qa-widget-key-123', id: 'k1', name: req.postDataJSON().name } });
    if (url.pathname === '/api/devices') return route.fulfill({ json: { devices: [
      { id: 'p1', name: 'VJS OnePlus', kind: 'phone', last_seen: Date.now() / 1000, this: true },
      { id: 'k0', name: 'iPhone widgets', kind: 'widget', last_seen: 0, this: false }] } });
    if (req.method() === 'POST') return route.fulfill({ json: { ok: true } });
    if (url.pathname === '/api/context') return route.fulfill({ json: { ...TV, updated_at: Date.now() / 1000, server_time: Date.now() / 1000 } });
    if (url.pathname === '/api/apps') return route.fulfill({ json: { apps: [] } });
    if (url.pathname === '/api/config') return route.fulfill({ json: { shortcuts: [], routines: [] } });
    return route.fulfill({ status: 404, json: {} });
  });
  await page.addInitScript(m => { try { localStorage.setItem('remote-mode', m); localStorage.setItem('remote-room', 'overview'); } catch {} }, mode);
  await page.goto(new URL(at || '/', BASE).href, { waitUntil: 'networkidle' });
  await page.waitForTimeout(900);
  return page;
}

const screenOf = (page, id) => page.evaluate(i => document.querySelector('#house-view')?.screenOf?.(i) ?? null, id);
async function hold(page, at) {
  await page.mouse.move(at.x, at.y); await page.mouse.down(); await page.waitForTimeout(650); await page.mouse.up();
}
const houseDevices = page => page.evaluate(() => { try { return JSON.parse(document.querySelector('#house-view')?.dataset.devices || 'null'); } catch { return null; } });

async function openHouseView(page) {
  const button = await page.$('#overview [data-act="house"]');
  if (!button) return false;
  await button.click();
  try { await page.waitForSelector('#house-view canvas', { timeout: 8000 }); return true; } catch { return false; }
}

const metrics = page => page.evaluate(() => {
  const vw = innerWidth, bad = [];
  document.querySelectorAll('#app *').forEach(e => {
    const r = e.getBoundingClientRect();
    const page = e.closest('.home-page'), visible = !page || Math.abs(page.getBoundingClientRect().left - page.parentElement.getBoundingClientRect().left) < 2;
    if (r.width && e.offsetParent && visible && !e.classList.contains('home-page') && r.right > vw + 1
        && !e.closest('.shortcuts,.apps-scroll,.pager,.chip-row,.group-chips,.group-row,.swatch-row,.scenes')) bad.push(e.className || e.tagName);
  });
  return { hScroll: document.scrollingElement.scrollWidth - vw, bad: [...new Set(bad)].slice(0, 5) };
});

(async () => {
  await pairOnce();
  const browser = await chromium.launch({ channel: 'chrome' });
  const errors = [], results = [];
  const ok = (name, cond, detail = '') => { results.push(`${cond ? 'OK  ' : 'FAIL'} ${name}${detail ? ' ' + detail : ''}`); if (!cond) errors.push(name); };
  const quick = !!process.env.QA_QUICK;        // QA_QUICK=1: one phone size, dark only, for iterating (the full run gates)
  const sizes = quick ? [[390, 844]] : [[390, 844], [844, 390], [1024, 768], [1440, 900]];

  for (const size of sizes) for (const scheme of quick ? ['dark'] : ['dark', 'light']) {
    const tag = `${size[0]}x${size[1]} ${scheme}`;

    // rooms: every room tab renders its devices without spilling sideways
    let page = await open(browser, size, scheme);
    page.on('pageerror', e => failures(errors, tag)(e.message));
    for (const room of FIXTURE.rooms) {
      await page.click(`#home-tabbar [data-room="${room.id}"]`); await page.waitForTimeout(700);
      const tiles = await page.$$eval(`.home-page[data-page="${room.id}"] .dev`, els => els.map(e => e.dataset.id));
      const inView = await page.$eval(`.home-page[data-page="${room.id}"]`, el => Math.abs(el.getBoundingClientRect().left - el.parentElement.getBoundingClientRect().left) < 2);
      const want = FIXTURE.devices.filter(d => d.room === room.id).map(d => d.id).sort();
      const m = await metrics(page);
      ok(`rooms ${tag} ${room.id}`, inView && JSON.stringify(tiles.sort()) === JSON.stringify(want) && m.hScroll === 0 && !m.bad.length,
         `${inView ? '' : 'not in view '}${m.bad.length ? `offscreen=${m.bad}` : ''}`);
    }
    if (size[0] === 390) await page.screenshot({ path: `H-${scheme}-room.png` });

    // offline: Snehal's AC is disabled and says so
    await page.click('#home-tabbar [data-room="snehal"]'); await page.waitForTimeout(700);
    const off = await page.$eval('[data-id="snehal-ac"]', e => ({ disabled: e.disabled, text: e.textContent }));
    ok(`offline ${tag}`, off.disabled && off.text.includes('Offline'));

    // ac-sheet: every capability gets a control
    await page.click('#home-tabbar [data-room="vjs"]'); await page.waitForTimeout(700);
    await page.click('[data-id="vjs-ac"] [data-more]'); await page.waitForTimeout(800);
    const caps = Object.keys(FIXTURE.devices.find(d => d.id === 'vjs-ac').capabilities).filter(k => k !== 'indoor_temp');
    const missing = await page.evaluate(keys => keys.filter(k => !document.querySelector(
      `#ds-controls [data-key="${k}"], #ds-controls [data-toggle="${k}"], #ds-controls [data-step="${k}"], #ds-controls [data-slide="${k}"]`)), caps);
    ok(`ac-sheet ${tag}`, !missing.length, missing.length ? `missing=${missing}` : '');
    if (size[0] === 390) await page.screenshot({ path: `H-${scheme}-ac-sheet.png` });

    // rapid input: six taps on + become one request with the final value
    if (size[0] === 390 && scheme === 'dark') {
      const before = page.house.devices.find(d => d.id === 'vjs-ac').state.target_temp;
      for (let i = 0; i < 6; i++) { await page.click('[data-step="target_temp"] [data-delta="1"]'); await page.waitForTimeout(60); }
      await page.waitForTimeout(900);
      const sent = page.sets.filter(s => s.device === 'vjs-ac');
      ok('debounce', sent.length === 1 && sent[0].changes.target_temp === before + 3, JSON.stringify(sent));

      // external-change: someone uses the IR remote while the sheet is open
      await page.waitForTimeout(1600);
      page.house.devices.find(d => d.id === 'vjs-ac').state.target_temp = 19; page.house.version++;
      await page.waitForTimeout(3000);
      const shown = await page.$eval('[data-out="target_temp"]', e => e.textContent);
      ok('external-change', shown === '19°', `shows ${shown}`);
    }
    await page.close();

    // overview: routines run with one tap, the lights panel sends one change for the whole group
    page = await open(browser, size, scheme);
    const ovm = await metrics(page);
    const cards = await page.$$eval('.scene[data-run]', els => els.length);
    await page.click('.scene[data-run="good-night"]'); await page.waitForTimeout(400);
    await page.$eval('[data-group-slide="brightness"]', el => { for (const v of [20, 30, 40]) { el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })); } });
    await page.waitForTimeout(900);
    const runs = page.runs;
    ok(`overview ${tag}`, cards === ROUTINES.routines.length && ovm.hScroll === 0 && !ovm.bad.length && runs[0]?.routine === 'good-night'
       && runs.length === 2 && runs[1].steps[0].group === 'living-lights' && runs[1].steps[0].changes.brightness === 40, JSON.stringify(runs) + (ovm.bad.length ? ` offscreen=${ovm.bad}` : ''));
    if (size[0] === 390) await page.screenshot({ path: `H-${scheme}-overview.png` });
    const wholeHouse = await page.$$eval('#overview [data-act="house-off"], #overview [data-target="all-lights"]', els => els.length);
    ok(`no whole-house buttons ${tag}`, wholeHouse === 0, `${wholeHouse} found`);
    // swipe: a real finger swipe that starts on the content moves to the next page and updates the tab
    if (size[0] < 1100) {
      const cdp = await page.context().newCDPSession(page);
      const box = await page.$eval('.home-pager', el => { const r = el.getBoundingClientRect(); return { x: r.left + r.width * 0.75, y: r.top + r.height * 0.5, w: r.width }; });
      await cdp.send('Input.synthesizeScrollGesture', { x: box.x, y: box.y, xDistance: -Math.round(box.w * 0.6), yDistance: 0, gestureSourceType: 'touch', speed: 1200 });
      await page.waitForTimeout(900);
      const cur = await page.$eval('#home-tabbar .current', el => el.dataset.room);
      ok(`swipe ${tag}`, cur === 'living', cur);
    }
    await page.close();

    // Rooms as a flat floor plan (the default): the real layout, every device where it is, tap to switch
    page = await open(browser, size, scheme);
    const plan = await page.evaluate(() => {
      const p = document.querySelector('#house-map .plan'); if (!p) return null;
      const r = p.getBoundingClientRect(), minis = [...p.querySelectorAll('[data-mini]')];
      const over = minis.some(m => { const b = m.getBoundingClientRect(); return b.left < r.left - 1 || b.right > r.right + 1 || b.top < r.top - 1 || b.bottom > r.bottom + 1; });
      return { w: r.width, vw: innerWidth, ratio: +(r.height / r.width).toFixed(2), rooms: [...p.querySelectorAll('[data-goto]')].map(b => b.dataset.goto).sort().join(),
               minis: minis.length, walls: p.querySelectorAll('svg .wall').length, over };
    });
    ok(`rooms plan ${tag}`, plan && plan.rooms === 'dining,green,kitchen,living,snehal,vjs' && plan.minis === 16 && plan.walls > 20 && !plan.over && plan.w <= plan.vw && plan.ratio > 0.8 && plan.ratio < 1,
       JSON.stringify(plan));
    await page.click('#house-map .plan [data-mini="living-light"]'); await page.waitForTimeout(500);
    const planLit = await page.evaluate(() => ({ on: document.querySelector('#house-map .plan [data-mini="living-light"]').classList.contains('on'),
      glow: getComputedStyle(document.querySelector('#house-map .plan [data-room="living"]')).getPropertyValue('--glow').trim() }));
    ok(`rooms plan tap ${tag}`, page.sets.some(x => x.device === 'living-light' && x.changes.power === true) && planLit.on && Number(planLit.glow) > 0.2, JSON.stringify({ sets: page.sets, planLit }));
    await page.click('#house-map .plan [data-goto="green"]'); await page.waitForTimeout(700);
    const went = await page.$eval('#home-tabbar [aria-current="true"]', b => b.dataset.room).catch(() => null);
    ok(`rooms plan goes to room ${tag}`, went === 'green', went);
    if (size[0] === 390 && scheme === 'dark') {
      await page.click('#home-tabbar [data-room="overview"]'); await page.waitForTimeout(600);
      await page.click('#overview [data-act="rooms-view"]'); await page.waitForTimeout(300);
      await page.reload({ waitUntil: 'networkidle' }); await page.waitForTimeout(900);
      const view = await page.evaluate(() => ({ tiles: document.querySelectorAll('#house-map .room-cell').length, plan: !!document.querySelector('#house-map .plan') }));
      ok('rooms view kept after reload', view.tiles === 6 && !view.plan, JSON.stringify(view));
    }
    await page.close();

    // per-phone room order: VJS first moves its tab, page and house tile to the front
    if (size[0] === 390 && scheme === 'dark') {
      page = await open(browser, size, scheme);
      await page.click('#overview [data-act="rooms-view"]'); await page.waitForTimeout(300);   // tiles: they follow the order
      await page.click('[data-act="reorder"]'); await page.waitForTimeout(600);
      await page.click('#order-sheet [data-move="1"][data-by="-1"]'); await page.waitForTimeout(300);
      const order = await page.evaluate(() => ({ tabs: [...document.querySelectorAll('#home-tabbar [data-room]')].map(b => b.dataset.room).slice(0, 3),
        map: [...document.querySelectorAll('.room-go')].map(b => b.dataset.goto)[0], saved: localStorage.getItem('remote-room-order') }));
      ok('room order', order.tabs.join() === 'overview,vjs,living' && order.map === 'vjs' && order.saved.startsWith('["vjs","living"'), JSON.stringify(order));
      await page.reload({ waitUntil: 'networkidle' }); await page.waitForTimeout(900);
      const after = await page.$$eval('#home-tabbar [data-room]', els => els.map(b => b.dataset.room).slice(0, 2).join());
      ok('room order kept after reload', after === 'overview,vjs', after);
      await page.close();
    }

    // approved phones only: without the cookie the page is the pairing screen
    if (scheme === 'dark') {
      page = await open(browser, size, scheme, { paired: false });
      const pairing = await page.evaluate(() => ({ title: document.querySelector('h1')?.textContent, app: !!document.querySelector('#app') }));
      const pm = await metrics(page);
      ok(`unpaired sees pairing ${tag}`, pairing.title === "This phone isn't connected to the home yet" && !pairing.app && pm.hScroll === 0, JSON.stringify(pairing));
      await page.close();
    }

    // away from home: the Pi doesn't answer, a card asks for Tailscale, and it clears itself when home answers
    if (scheme === 'dark') {
      page = await open(browser, size, scheme);
      page.pingOk = false;
      await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
      await page.waitForTimeout(900);
      const shown = await page.$eval('.away', el => !el.hidden && el.textContent.includes('turn on Tailscale') && !!el.querySelector('a[href="https://100.110.26.84/"]'));
      page.pingOk = true;
      await page.waitForTimeout(4500);
      const cleared = await page.$eval('.away', el => el.hidden).catch(() => true);
      ok(`away card ${tag}`, shown && cleared, JSON.stringify({ shown, cleared }));
      // weak signal over Tailscale: a ping that takes 4 s is slow, not away
      page.pingDelay = 4000;
      await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
      await page.waitForTimeout(5000);
      ok(`slow ping is not away ${tag}`, await page.$eval('.away', el => el.hidden));
      // a state request that never answers must not stop the app updating
      page.hangState = true;
      await page.waitForTimeout(2500);
      const before = page.stateCalls;
      await page.waitForTimeout(9000);
      ok(`polling survives a hung request ${tag}`, page.stateCalls > before + 1, JSON.stringify({ before, after: page.stateCalls }));
      await page.close();
    }

    // Android long-press shortcuts open /?run= and /?toggle=: they act once, then the address is plain again
    if (scheme === 'dark') {
      page = await open(browser, size, scheme, { at: '/?run=good-night', mode: 'tv' });
      const ran = page.runs.some(r => r.routine === 'good-night'), inHome = await page.$eval('#home', e => !e.hidden);
      ok(`shortcut runs a scene ${tag}`, ran && inHome && new URL(page.url()).search === '', JSON.stringify({ runs: page.runs, inHome, url: page.url() }));
      await page.close();
      page = await open(browser, size, scheme, { at: '/?toggle=living-lights' });
      ok(`shortcut toggles lights ${tag}`, page.toggles.length === 1 && page.toggles[0].target === 'living-lights', JSON.stringify(page.toggles));
      await page.close();

      // Phones sheet: widget keys are listed as such, and a new key is shown once with what to type
      page = await open(browser, size, scheme);
      await page.evaluate(() => document.querySelector('#qs-phones').click());
      await page.waitForTimeout(500);
      const listed = await page.$eval('#phones-sheet', e => e.textContent.includes('Widget key'));
      page.once('dialog', d => d.accept('VJS iPhone'));
      await page.click('#phones-sheet [data-add-key]');
      await page.waitForTimeout(700);
      const shown = await page.$eval('#phones-sheet', e => ({ key: e.textContent.includes('qa-widget-key-123'),
        scene: e.textContent.includes('good-night'), lights: e.textContent.includes('living-lights'), url: e.textContent.includes('/api/home/toggle') }));
      if (process.env.SHOT && size[0] < 500) { await page.$eval('#new-key', e => e.querySelectorAll('details').forEach(d => d.open = true)); await page.screenshot({ path: `${process.env.SHOT}/widget-key.png`, fullPage: false }); await page.$eval('#new-key .widget-actions', e => e.scrollIntoView()); await page.screenshot({ path: `${process.env.SHOT}/widget-list.png` }); }
      const wide = await page.$eval('#phones-sheet', e => [...e.querySelectorAll('*')].filter(x => x.getBoundingClientRect().right > innerWidth + 1).map(x => x.className || x.tagName).slice(0, 3));
      ok(`widget key shown with setup ${tag}`, listed && shown.key && shown.scene && shown.lights && shown.url && !wide.length, JSON.stringify({ listed, ...shown, wide }));
      await page.close();
    }

    // house view: nothing 3D loads until it's opened; it fills the screen; Close and Back both leave it
    page = await open(browser, size, scheme);
    const houseReqs = [];
    page.on('request', r => { if (/\/(house[.-]|vendor\/three\/)/.test(new URL(r.url()).pathname)) houseReqs.push(new URL(r.url()).pathname); });
    page.on('pageerror', e => failures(errors, `house ${tag}`)(e.message));
    await page.waitForTimeout(400);
    ok(`house lazy ${tag}`, houseReqs.length === 0, houseReqs.join(','));
    const opened = await openHouseView(page);
    const fill = await page.evaluate(() => {
      const c = document.querySelector('#house-view canvas'); if (!c) return null;
      const r = c.getBoundingClientRect();
      return { full: Math.abs(r.width - innerWidth) < 2 && Math.abs(r.height - innerHeight) < 2, hScroll: document.scrollingElement.scrollWidth - innerWidth };
    });
    ok(`house opens ${tag}`, opened && fill?.full && fill.hScroll === 0 && houseReqs.includes('/house.js') && houseReqs.includes('/vendor/three/three.module.min.js'),
       JSON.stringify({ opened, fill, reqs: houseReqs.length }));
    if (size[0] === 390 && opened) await page.screenshot({ path: `H-${scheme}-house.png` });
    if (opened) await page.click('#house-view [data-house="close"]');
    await page.waitForTimeout(400);
    const closedByButton = await page.$('#house-view') === null;
    const reopened = await openHouseView(page);
    if (reopened) await page.goBack();
    await page.waitForTimeout(600);
    const back = await page.evaluate(() => ({ gone: !document.querySelector('#house-view'), home: !document.querySelector('#home').hidden }));
    ok(`house closes ${tag}`, closedByButton && reopened && back.gone && back.home, JSON.stringify({ closedByButton, reopened, back }));

    // house model: what it draws matches every device, and follows changes from elsewhere
    await openHouseView(page); await page.waitForTimeout(1500);
    const drawn = await houseDevices(page);
    const wrong = FIXTURE.devices.filter(d => !drawn?.[d.id] || drawn[d.id].on !== !!(d.online && d.state.power) || drawn[d.id].online !== d.online).map(d => d.id);
    ok(`house state ${tag}`, drawn && Object.keys(drawn).length === 17 && !wrong.length && drawn.tv?.on === true && drawn['snehal-ac'].online === false,
       JSON.stringify({ count: drawn && Object.keys(drawn).length, wrong, tv: drawn?.tv }));
    const dining = page.house.devices.find(d => d.id === 'dining-light'); dining.state.power = true; page.house.version++;
    let live = false;
    for (let i = 0; i < 12 && !live; i++) { await page.waitForTimeout(300); live = (await houseDevices(page))?.['dining-light']?.on === true; }
    ok(`house live ${tag}`, live);
    await page.close();

    // tap switches, hold opens full controls, for lights, ACs and the TV
    page = await open(browser, size, scheme);
    page.on('pageerror', e => failures(errors, `house use ${tag}`)(e.message));
    const powers = []; page.on('request', r => { if (r.method() === 'POST' && new URL(r.url()).pathname === '/api/power') powers.push(1); });
    await openHouseView(page); await page.waitForTimeout(1500);
    const lightAt = await screenOf(page, 'living-light');
    if (lightAt) await page.mouse.click(lightAt.x, lightAt.y);
    await page.waitForTimeout(900);
    const wasOn = FIXTURE.devices.find(d => d.id === 'living-light').state.power;
    const tvAt = await screenOf(page, 'tv');
    if (tvAt) await page.mouse.click(tvAt.x, tvAt.y);
    await page.waitForTimeout(600);
    ok(`house tap ${tag}`, page.sets.some(b => b.device === 'living-light' && b.changes.power === !wasOn) && powers.length === 1,
       JSON.stringify({ lightAt, tvAt, sets: page.sets, powers: powers.length }));
    const acAt = await screenOf(page, 'vjs-ac');
    if (acAt) await hold(page, acAt);
    await page.waitForTimeout(700);
    const sheet = await page.evaluate(() => ({ open: document.querySelector('#device-sheet').open, title: document.querySelector('#ds-title').textContent }));
    await page.keyboard.press('Escape'); await page.waitForTimeout(400);
    const tvHoldAt = await screenOf(page, 'tv');
    if (tvHoldAt) await hold(page, tvHoldAt);
    await page.waitForTimeout(900);
    const toTv = await page.evaluate(() => ({ gone: !document.querySelector('#house-view'), homeHidden: document.querySelector('#home').hidden }));
    ok(`house hold ${tag}`, sheet.open && sheet.title === 'AC' && toTv.gone && toTv.homeHidden, JSON.stringify({ acAt, sheet, toTv }));
    await page.close();

    // views: 2D/3D by button (and by a two-finger drag on touch screens), walking, walls block taps
    page = await open(browser, size, scheme);
    page.on('pageerror', e => failures(errors, `house views ${tag}`)(e.message));
    await page.evaluate(() => localStorage.removeItem('house-place'));
    await openHouseView(page); await page.waitForTimeout(1200);
    const framed = await page.evaluate(() => {   // the whole flat in view, in the middle, filling the screen's width or height
      const hv = document.querySelector('#house-view'), pts = [[0, 0], [45, 0], [0, 41], [45, 41]].map(([x, y]) => hv.screenAtPlan(x, y));
      if (pts.some(p => !p)) return { pts };
      const xs = pts.map(p => p.x), ys = pts.map(p => p.y), r = hv.querySelector('canvas').getBoundingClientRect();
      const fill = Math.max((Math.max(...xs) - Math.min(...xs)) / r.width, (Math.max(...ys) - Math.min(...ys)) / r.height);
      const off = Math.hypot(((Math.max(...xs) + Math.min(...xs)) / 2 - (r.left + r.width / 2)) / r.width, ((Math.max(...ys) + Math.min(...ys)) / 2 - (r.top + r.height / 2)) / r.height);
      return { fill: +fill.toFixed(2), off: +off.toFixed(3) };
    });
    const centred = framed.fill >= 0.8 && framed.off < 0.09;   // the floor sits a little low: the whole house, walls and all, is what is centred
    const st = async () => Number(await page.$eval('#house-view', e => e.dataset.standing));
    const before = await st();
    await page.click('#house-view [data-house="flat"]'); await page.waitForTimeout(1500);
    const flat = await st();
    await page.click('#house-view [data-house="flat"]'); await page.waitForTimeout(1500);
    const up = await st();
    let tilted = null;
    if (size[0] < 1100) {
      const cdp = await page.context().newCDPSession(page), cx = size[0] / 2, cy = size[1] / 2;
      const touch = async (type, dy) => cdp.send('Input.dispatchTouchEvent', { type, touchPoints: type === 'touchEnd' ? [] : [{ x: cx - 60, y: cy + dy, id: 1 }, { x: cx + 60, y: cy + dy, id: 2 }] });
      await touch('touchStart', 0);
      for (let i = 1; i <= 8; i++) { await touch('touchMove', 18 * i); await page.waitForTimeout(16); }   // down flattens, like Google Maps
      await touch('touchEnd', 0); await page.waitForTimeout(400);
      tilted = await st();
    }
    ok(`house 2d3d ${tag}`, before === 1 && flat === 0 && up === 1 && (tilted === null || tilted < 0.99) && centred,
       JSON.stringify({ before, flat, up, tilted, framed }));
    if (tilted !== null) { await page.click('#house-view [data-house="flat"]'); await page.waitForTimeout(300); await page.click('#house-view [data-house="flat"]'); await page.waitForTimeout(1500); }
    await page.click('#house-view [data-house="walk"]'); await page.waitForTimeout(1900);
    const walkView = await page.$eval('#house-view', e => e.dataset.view);
    const startAt = await page.evaluate(() => document.querySelector('#house-view')?.cameraAt?.() ?? [99, 99]);
    const blocked = await screenOf(page, 'vjs-ac');                 // behind the TV wall from the sofa
    const box2 = await page.locator('#house-view canvas').boundingBox();      // turn to face the dining room (north), then tap its floor
    await page.mouse.move(box2.x + 350, box2.y + box2.height / 2); await page.mouse.down();        // 314 px left = a quarter turn right
    await page.mouse.move(box2.x + 36, box2.y + box2.height / 2, { steps: 12 }); await page.waitForTimeout(150); await page.mouse.up();   // still before lifting: no flick
    await page.waitForTimeout(300);
    const floorAt = await page.evaluate(() => document.querySelector('#house-view')?.screenAtPlan?.(21.5, 20) ?? null);
    if (floorAt) await page.mouse.click(floorAt.x, floorAt.y);
    const camAt = () => page.evaluate(() => document.querySelector('#house-view')?.cameraAt?.() ?? [99, 99]);
    let movedAt = await camAt();
    for (let i = 0; i < 40; i++) {                  // a walk takes a few seconds: wait until it arrives
      await page.waitForTimeout(200); const next = await camAt();
      if (i > 3 && Math.hypot(next[0] - movedAt[0], next[1] - movedAt[1]) < 0.005) break;
      movedAt = next;
    }
    const moved = Math.hypot(movedAt[0] - startAt[0], movedAt[1] - startAt[1]);
    ok(`house walk ${tag}`, walkView === 'walk' && blocked === null && moved > 3 && movedAt[0] > 14.8, JSON.stringify({ walkView, startAt, movedAt, blocked, floorAt, facing: await page.evaluate(() => document.querySelector('#house-view')?.screenAtPlan?.(21.5, 20) ?? null) }));
    ok(`house wall blocks ${tag}`, blocked === null && !page.sets.some(b => b.device === 'vjs-ac'));
    await page.click('#house-view [data-house="close"]'); await page.waitForTimeout(400);
    await openHouseView(page); await page.waitForTimeout(1200);
    const again = await page.evaluate(() => ({ view: document.querySelector('#house-view').dataset.view, at: document.querySelector('#house-view')?.cameraAt?.() ?? [99, 99] }));
    ok(`house remembers ${tag}`, again.view === 'walk' && Math.hypot(again.at[0] - movedAt[0], again.at[1] - movedAt[1]) < 0.5, JSON.stringify(again));
    await page.click('#house-view [data-house="walk"]'); await page.waitForTimeout(1600);     // back to the overview

    // rotate the phone while it's open: taps still land on the right device
    await page.setViewportSize({ width: size[1], height: size[0] }); await page.waitForTimeout(900);
    const rotatedAt = await screenOf(page, 'living-light');
    const setsBefore = page.sets.length;
    if (rotatedAt) await page.mouse.click(rotatedAt.x, rotatedAt.y);
    await page.waitForTimeout(900);
    ok(`house resize ${tag}`, rotatedAt && rotatedAt.x > 0 && rotatedAt.x < size[1] && rotatedAt.y > 0 && rotatedAt.y < size[0]
       && page.sets.slice(setsBefore).some(b => b.device === 'living-light'), JSON.stringify({ rotatedAt }));
    await page.close();

    // time of day: daylight at 1 pm, night at 11 pm
    const today = h => { const d = new Date(); d.setHours(h, 0, 0, 0); return d; };
    const dayOf = async h => { const pg = await open(browser, size, scheme, { time: today(h) }); await openHouseView(pg); await pg.waitForTimeout(800);
      const v = Number(await pg.$eval('#house-view', e => e.dataset.daylight)); await pg.close(); return v; };
    const noon = await dayOf(13), night = await dayOf(23);
    ok(`house daylight ${tag}`, noon === 1 && night === 0, JSON.stringify({ noon, night }));

    // curtains: tapping them (or the balcony behind them) draws them open; then you can glide out onto the balcony,
    // where the outside is drawn (only while walking). At midday, so the screenshot shows it
    page = await open(browser, size, scheme, { time: today(13) });
    page.on('pageerror', e => failures(errors, `house balcony ${tag}`)(e.message));
    await page.evaluate(() => localStorage.removeItem('house-place'));
    await openHouseView(page); await page.waitForTimeout(1200);
    const hv = (fn, ...a) => page.evaluate(([f, args]) => { const r = document.querySelector('#house-view'); return r?.[f]?.(...args) ?? null; }, [fn, a]);
    const data = k => page.$eval('#house-view', (e, key) => e.dataset[key], k);
    const outsideOverview = await data('outside');
    await hv('walkAt', 24.5, 10.5, 0, -0.45); await page.waitForTimeout(400);
    const outsideWalk = await data('outside'), shut = await data('curtains');
    let spot = await hv('screenAtPlan', 24.2, 3.5); const spot0 = spot; let covered = spot && await hv('deviceAt', spot.x, spot.y);
    if (spot) await page.mouse.click(spot.x, spot.y);
    await page.waitForTimeout(1800);
    const drawn2 = await data('curtains'), stayed = await hv('cameraAt');
    spot = await hv('screenAtPlan', 24.2, 3.5);
    if (spot) await page.mouse.click(spot.x, spot.y);
    await page.waitForTimeout(1800);
    const outAt = await hv('cameraAt');
    if (size[0] === 390) await page.screenshot({ path: `H-${scheme}-balcony.png` });
    // doors: tap one to swing it; the main door starts closed, room doors open
    await page.click('#house-view [data-house="walk"]'); await page.waitForTimeout(1600);     // back to the overview
    const doors0 = JSON.parse(await data('doors') || '{}'), mainAt = await hv('screenOf', 'door:h41:15');
    if (mainAt) await page.mouse.click(mainAt.x, mainAt.y);
    await page.waitForTimeout(1200);
    const doors1 = JSON.parse(await data('doors') || '{}');
    ok(`house doors ${tag}`, doors0['door:h41:15'] === 'closed' && doors0['door:h23:10.4'] === 'open' && doors1['door:h41:15'] === 'open'
       && Object.keys(doors0).length === 10, JSON.stringify({ mainAt, n: Object.keys(doors0).length, main: [doors0['door:h41:15'], doors1['door:h41:15']] }));
    ok(`house curtains ${tag}`, shut === 'closed' && covered === 'curtains' && drawn2 === 'open' && stayed?.[1] > 9.5,
       JSON.stringify({ shut, covered, drawn2, stayed, spot0, cam: await hv('cameraAt'), view: await data('view') }));
    ok(`house balcony ${tag}`, outAt?.[1] < 6.8 && outAt?.[1] > 0.5 && outsideOverview !== 'on' && outsideWalk === 'on',
       JSON.stringify({ outAt, outsideOverview, outsideWalk }));
    await page.close();

    // walking: night is readable; a tap walks a real path (through the kitchen door, never into a wall); the
    // joystick moves and slides along walls; the floor ring shows where you'll go; a flicked look glides to a stop
    page = await open(browser, size, scheme, { time: today(21) });
    page.on('pageerror', e => failures(errors, `house walking ${tag}`)(e.message));
    await page.evaluate(() => localStorage.removeItem('house-place'));
    await openHouseView(page); await page.waitForTimeout(1500);
    const hvw = (fn, ...a) => page.evaluate(([f, args]) => document.querySelector('#house-view')?.[f]?.(...args) ?? null, [fn, a]);
    const brightness = async () => { const png = (await page.locator('#house-view canvas').screenshot()).toString('base64');
      return page.evaluate(async src => { const img = new Image(); img.src = 'data:image/png;base64,' + src; await img.decode();
        const c = document.createElement('canvas'); c.width = 48; c.height = 48; const g = c.getContext('2d'); g.drawImage(img, 0, 0, 48, 48);
        const d = g.getImageData(8, 4, 32, 26).data; let t = 0; for (let i = 0; i < d.length; i += 4) t += (d[i] + d[i + 1] + d[i + 2]) / 3; return Math.round(t / (d.length / 4)); }, png); };
    const nightOverview = await brightness();
    await hvw('walkAt', 20.5, 34.5, 0, -0.2); await page.waitForTimeout(500);
    const nightWalk = await brightness();
    ok(`house night readable ${tag}`, nightOverview >= 45 && nightWalk >= 45, JSON.stringify({ nightOverview, nightWalk }));
    await hvw('goTo', 32.5, 7);
    const trail = [];
    for (let i = 0; i < 90; i++) { await page.waitForTimeout(80); const at = await hvw('cameraAt'); trail.push(at); if (i > 5 && Math.hypot(at[0] - trail[i - 3][0], at[1] - trail[i - 3][1]) < 0.01) break; }
    const end = trail.at(-1), walls = await page.evaluate(t => t.filter(([x, y]) => !document.querySelector('#house-view').walkable?.(x, y)).length, trail);
    const cross = trail.findIndex((p, i) => i && (trail[i - 1][1] - 13) * (p[1] - 13) <= 0 && p[0] > 28);
    const doorX = cross > 0 ? trail[cross][0] : null;
    ok(`house walks a path ${tag}`, Math.hypot(end[0] - 32.5, end[1] - 7) < 1.3 && walls === 0 && doorX > 29.2 && doorX < 32.6,
       JSON.stringify({ end, walls, doorX, steps: trail.length }));
    // the joystick: forward towards the TV wall, then keep pushing: it stops short and stays on free floor
    await hvw('walkAt', 20.5, 34.5, Math.PI / 2, -0.1); await page.waitForTimeout(300);
    const stick = await page.locator('#house-view .house-stick').boundingBox().catch(() => null);
    let stickMoved = null, stickEnd = null, stickFree = null;
    if (stick) {
      const sx = stick.x + stick.width / 2, sy = stick.y + stick.height / 2;
      await page.mouse.move(sx, sy); await page.mouse.down(); await page.mouse.move(sx, sy - 40, { steps: 4 });
      await page.waitForTimeout(700); stickMoved = await hvw('cameraAt');
      await page.waitForTimeout(2500); stickEnd = await hvw('cameraAt'); await page.mouse.up();
      stickFree = await hvw('walkable', ...stickEnd);
    }
    ok(`house joystick ${tag}`, !!stick && stickMoved[0] < 19.3 && stickFree === true && stickEnd[0] > 15.5, JSON.stringify({ stick: !!stick, stickMoved, stickEnd, stickFree }));
    // the floor ring while a finger is down, and a flick that glides to a stop
    await hvw('walkAt', 20.5, 34.5, Math.PI / 2, -0.6); await page.waitForTimeout(300);
    const floorPt = await hvw('screenAtPlan', 16.8, 34.5);
    let ring = null;
    if (floorPt) { await page.mouse.move(floorPt.x, floorPt.y); await page.mouse.down(); await page.waitForTimeout(150); ring = await page.$eval('#house-view', e => e.dataset.ring); await page.mouse.move(floorPt.x + 30, floorPt.y, { steps: 2 }); await page.mouse.up(); }
    await page.waitForTimeout(800);
    const cbox = await page.locator('#house-view canvas').boundingBox(), fy = cbox.y + cbox.height * 0.3;
    await page.mouse.move(cbox.x + cbox.width * 0.3, fy); await page.mouse.down();
    for (let i = 1; i <= 4; i++) { await page.mouse.move(cbox.x + cbox.width * 0.3 + i * 40, fy); await page.waitForTimeout(16); }
    await page.mouse.up();
    const y0 = ((await hvw('lookAt')) || [NaN])[0]; await page.waitForTimeout(250); const y1 = ((await hvw('lookAt')) || [NaN])[0];
    await page.waitForTimeout(1500); const y2 = ((await hvw('lookAt')) || [NaN])[0]; await page.waitForTimeout(300); const y3 = ((await hvw('lookAt')) || [NaN])[0];
    ok(`house ring and glide ${tag}`, ring === "shown" && Math.abs(y1 - y0) > 0.02 && Math.abs(y3 - y2) < 0.002, JSON.stringify({ ring, floorPt, y0, y1, y2, y3 }));
    await page.close();

    // easter eggs: the record player plays, furniture moves, the shelf picks a game, outside comes alive; no device changes
    page = await open(browser, size, scheme, { time: today(21) });
    page.on('pageerror', e => failures(errors, `house eggs ${tag}`)(e.message));
    await page.evaluate(() => localStorage.removeItem('house-place'));
    await openHouseView(page); await page.waitForTimeout(1500);
    const egg = page => page.evaluate(() => JSON.parse(document.querySelector('#house-view')?.dataset.eggs || '{}'));
    const hve = (fn, ...a) => page.evaluate(([f, args]) => document.querySelector('#house-view')?.[f]?.(...args) ?? null, [fn, a]);
    const tapEgg = async (id, wait = 900) => { const at = await screenOf(page, id); if (at) await page.mouse.click(at.x, at.y); await page.waitForTimeout(wait); return !!at; };
    const found = {};
    for (const e of ['arc-lamp', 'shelf']) found[e] = await tapEgg('egg:' + e);
    const shelfToast = await page.$eval('#toast', t => t.hidden ? '' : t.textContent);
    found.record = await tapEgg('egg:record', 400);
    let e1 = await egg(page);
    for (let i = 0; i < 15 && e1.record !== 'playing'; i++) { await page.waitForTimeout(300); e1 = await egg(page); }
    const tvSong = await page.$eval('#house-view', e => e.dataset.tvShows);
    await tapEgg('egg:record', 400);
    const tvAfter = await page.$eval('#house-view', e => e.dataset.tvShows);
    const e2 = await egg(page);
    // the bean bag hides behind the main-door wall from above: tap it from inside
    await hve('walkAt', 20, 33, Math.PI, -0.5); await page.waitForTimeout(400);
    found.beanbag = await tapEgg('egg:beanbag');
    // the recliner sits under the living-room fan, which covers it from the whole-house view
    await hve('walkAt', 22, 35.5, 0, -0.45); await page.waitForTimeout(400);
    found.recliner = await tapEgg('egg:recliner');
    // outside: the balcony chair folds, the tree's birds fly off, a lit school window goes dark
    await hve('walkAt', 24, 3, Math.PI / 2, -0.3); await page.waitForTimeout(400);
    found.chair = await tapEgg('egg:folding-chair');
    await hve('walkAt', 24, 2, 1.0, 0.25); await page.waitForTimeout(400);
    found.tree = await tapEgg('egg:tree', 300);
    const birds = (await egg(page)).birds;
    await hve('walkAt', 24, 2, 0, 0.12); await page.waitForTimeout(400);
    const lit0 = (await egg(page)).windows, win = await hve('screenOfWindow', 9);
    if (win) await page.mouse.click(win.x, win.y);
    await page.waitForTimeout(500);
    const e3 = await egg(page);
    ok(`house eggs ${tag}`, Object.values(found).every(Boolean) && e3.recliner === 'reclined' && e1['arc-lamp'] === 'on' && e3.beanbag === 1
       && typeof e1.shelf === 'string' && shelfToast.length > 0 && e1.record === 'playing' && e2.record === 'stopped' && tvSong === 'song' && tvAfter !== 'song'
       && e3['folding-chair'] === 'folded' && birds === 'flying' && e3.windows === lit0 - 1 && page.sets.length === 0,
       JSON.stringify({ tvSong, tvAfter, found, e1, e2, e3, birds, lit0, win, shelfToast, sets: page.sets.length }));
    await page.close();

    // one impossible pixel (some phone GPUs make them mid-animation) must not black out the picture through the glow
    page = await open(browser, size, scheme, { time: today(13) });
    page.on('pageerror', e => failures(errors, `house bad pixel ${tag}`)(e.message));
    await openHouseView(page); await page.waitForTimeout(1500);
    const lit = async () => { const png = (await page.locator('#house-view canvas').screenshot()).toString('base64');
      return page.evaluate(async src => { const img = new Image(); img.src = 'data:image/png;base64,' + src; await img.decode();
        const c = document.createElement('canvas'); c.width = 64; c.height = 64; const g = c.getContext('2d'); g.drawImage(img, 0, 0, 64, 64);
        const d = g.getImageData(0, 0, 64, 64).data; let n = 0; for (let i = 0; i < d.length; i += 4) if (d[i] + d[i + 1] + d[i + 2] > 30) n++; return n / 4096; }, png); };
    const litBefore = await lit();
    await page.evaluate(() => document.querySelector('#house-view').spoilPixel()); await page.waitForTimeout(600);
    const litAfter = await lit();
    ok(`house bad pixel ${tag}`, litBefore > 0.3 && litAfter > litBefore * 0.8, JSON.stringify({ litBefore, litAfter }));
    await page.close();

    // drawing only when something changes; spinning fans at most about 30 fps; nothing once closed
    const fans = on => h => h.devices.forEach(d => { if (d.kind === 'fan') d.state.power = on && d.id === 'living-fan'; });
    const framesOver = async (pg, ms) => { const a = Number(await pg.$eval('#house-view', e => e.dataset.frames)); await pg.waitForTimeout(ms); return Number(await pg.$eval('#house-view', e => e.dataset.frames)) - a; };
    page = await open(browser, size, scheme, { mutate: fans(false) });
    await openHouseView(page); await page.waitForTimeout(2000);
    const idle = await framesOver(page, 4000);         // the 2 s poll with nothing changed draws nothing
    await page.close();
    page = await open(browser, size, scheme, { mutate: fans(true) });
    await openHouseView(page); await page.waitForTimeout(2000);
    const spinning = await framesOver(page, 2000);
    ok(`house idle ${tag}`, idle < 2 && spinning > 10 && spinning < 75, JSON.stringify({ idle, spinning }));
    // a drag with a fan spinning leaves one drawing loop, not several
    const raf = () => page.evaluate(() => window.__houseRaf || 0);
    const box3 = await page.locator('#house-view canvas').boundingBox(), mx = box3.x + box3.width / 2, my = box3.y + box3.height / 2;
    for (let n = 0; n < 3; n++) {
      await page.mouse.move(mx, my); await page.mouse.down();
      for (let i = 1; i <= 6; i++) await page.mouse.move(mx + 15 * i, my + 4 * i);
      await page.mouse.up();
    }
    await page.waitForTimeout(1500);
    const ra = await raf(); await page.waitForTimeout(1000); const loops = await raf() - ra;
    ok(`house one loop ${tag}`, loops <= 70, JSON.stringify({ loops }));
    // the glow stays: spinning fans alone are slow on purpose and don't count against the phone
    await page.waitForTimeout(3000);
    const glow = await page.$eval('#house-view', e => e.dataset.bloom);
    ok(`house keeps glow ${tag}`, glow === 'on', JSON.stringify({ glow }));

    // a lost WebGL context (iOS after a while in the background) comes back by itself
    await page.evaluate(() => { const gl = document.querySelector('#house-view canvas').getContext('webgl2'); const x = gl.getExtension('WEBGL_lose_context'); x.loseContext(); setTimeout(() => x.restoreContext(), 500); });
    await page.waitForTimeout(3000);
    const after = await framesOver(page, 1000), backAt = await screenOf(page, 'living-light');
    ok(`house context lost ${tag}`, after > 0 && !!backAt, JSON.stringify({ after, backAt }));
    await page.evaluate(() => { window.__houseCanvas = document.querySelector('#house-view canvas'); });
    await page.click('#house-view [data-house="close"]'); await page.waitForTimeout(300);
    const r1 = await raf(); await page.waitForTimeout(800); const r2 = await raf();
    const freed = await page.evaluate(() => window.__houseCanvas.getContext('webgl2')?.isContextLost() ?? null);
    ok(`house closed stops ${tag}`, r1 === r2, JSON.stringify({ r1, r2 }));
    ok(`house frees gpu ${tag}`, freed === true, JSON.stringify({ freed }));
    await page.close();

    // reduced motion: fans don't spin, the 2D switch is instant
    page = await open(browser, size, scheme, { mutate: fans(true), reduced: true });
    await openHouseView(page); await page.waitForTimeout(2000);
    const still = await framesOver(page, 2000);
    await page.click('#house-view [data-house="flat"]'); await page.waitForTimeout(100);
    const instant = Number(await page.$eval('#house-view', e => e.dataset.standing));
    ok(`house reduced motion ${tag}`, still < 5 && instant === 0, JSON.stringify({ still, instant }));
    await page.close();

    // opened before the first state arrives: the model shows, then fills in
    page = await open(browser, size, scheme);
    page.on('pageerror', e => failures(errors, `house early ${tag}`)(e.message));
    page.stateDelay = 2500;
    await page.evaluate(() => { home.snap = null; openHouse(); });
    const shell = await page.waitForSelector('#house-view canvas', { timeout: 8000 }).then(() => true, () => false);
    let filled = false;
    for (let i = 0; i < 25 && !filled; i++) { await page.waitForTimeout(300); filled = Object.values((await houseDevices(page)) || {}).some(d => d.online); }
    ok(`house early ${tag}`, shell && filled);
    await page.close();

    // Back while the house view is still loading: it shows it's loading, and Back leaves for good
    page = await open(browser, size, scheme);
    page.on('pageerror', e => failures(errors, `house back early ${tag}`)(e.message));
    await page.context().route('**/house.js', async r => { await new Promise(w => setTimeout(w, 1500)); await r.continue(); });
    await page.click('#overview [data-act="house"]'); await page.waitForTimeout(300);
    const loading = await page.evaluate(() => ({ text: document.querySelector('#house-view')?.textContent || '', close: !!document.querySelector('#house-view [data-house="close"]') }));
    await page.goBack(); await page.waitForTimeout(3000);
    const gone = await page.evaluate(() => ({ view: !!document.querySelector('#house-view'), home: !document.querySelector('#home').hidden, state: !!history.state?.house }));
    ok(`house back early ${tag}`, /Loading the house/.test(loading.text) && loading.close && !gone.view && gone.home && !gone.state, JSON.stringify({ loading, gone }));
    await page.close();

    // plan and service disagree: a planned device the service doesn't report, and a device not on the plan
    page = await open(browser, size, scheme, { mutate: h => {
      h.devices = h.devices.filter(d => d.id !== 'kitchen-light');
      h.devices.push({ ...JSON.parse(JSON.stringify(FIXTURE.devices.find(d => d.id === 'dining-light'))), id: 'garage-light', name: 'Garage light', room: 'living' });
    } });
    page.on('pageerror', e => failures(errors, `house mismatch ${tag}`)(e.message));
    await openHouseView(page); await page.waitForTimeout(1500);
    const mixed = await houseDevices(page), note = await page.$eval('.house-unplaced', e => e.textContent).catch(() => '');
    ok(`house mismatch ${tag}`, mixed?.['kitchen-light']?.placed === true && mixed['kitchen-light'].online === false && /Garage/.test(note), JSON.stringify({ k: mixed?.['kitchen-light'], note }));
    await page.close();

    // home-down: the TV still works
    page = await open(browser, size, scheme, { down: true });
    const empty = await page.$eval('#home', e => e.textContent);
    await page.click('#mode-switch [data-mode="tv"]'); await page.waitForTimeout(500);
    const tvVisible = await page.$eval('#tabbar', e => getComputedStyle(e).display !== 'none' || document.querySelector('#app').dataset.layout === 'three');
    ok(`home-down ${tag}`, empty.includes("Home controls aren't reachable") && tvVisible);
    await page.close();

    // tv-unchanged: TV mode has its pages, no Home pieces, nothing spills
    page = await open(browser, size, scheme, { mode: 'tv' });
    const tv = await page.evaluate(() => ({ home: !document.querySelector('#home').hidden || !document.querySelector('#home-tabbar').hidden,
      pad: !!document.querySelector('#pad')?.getBoundingClientRect().width, power: !!document.querySelector('#power').offsetParent }));
    const m = await metrics(page);
    ok(`tv-unchanged ${tag}`, !tv.home && tv.pad && tv.power && m.hScroll === 0, `bad=${m.bad}`);
    if (size[0] === 390) await page.screenshot({ path: `H-${scheme}-tv.png` });
    await page.close();
  }

  console.log(results.join('\n'));
  console.log('ERRORS:', errors.join('\n') || 'none');
  await browser.close();
  process.exit(errors.length ? 1 : 0);
})();

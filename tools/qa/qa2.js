const { chromium } = require('playwright');
const fs = require('fs');
const APPS = JSON.parse(fs.readFileSync('apps.json'));
const CONFIG = { shortcuts: [{ type: 'app', package: 'com.netflix.ninja' }, { type: 'app', package: 'com.google.android.youtube.tv' }, { type: 'app', package: 'com.amazon.amazonvideo.livingroom' }, { type: 'app', package: 'in.startv.hotstar' }, { type: 'app', package: 'com.spotify.tv.android' }, { type: 'input', port: 1, name: 'PlayStation 5' }, { type: 'routine', id: 'movie-night' }, { type: 'action', action: 'picture' }],
  routines: [{ id: 'movie-night', name: 'Movie night', steps: [{ do: 'power_on' }, { do: 'volume', value: 22 }, { do: 'app', package: 'com.netflix.ninja' }] }, { id: 'bedtime', name: 'Bedtime', steps: [{ do: 'volume', value: 10 }, { do: 'sleep_timer', minutes: 30 }] }] };
const base = { connected: true, power: 'on', volume: 24, volume_min: 0, volume_max: 100, bridge: true, notifications: [], routine: null, sleep_at: null };
const now = Date.now() / 1000;
const S = {
  music: { ...base, app: 'spotify', package: 'com.spotify.tv.android', media: { package: 'com.spotify.tv.android', title: 'Midnight City', artist: 'M83', playing: true, position_ms: 61000, duration_ms: 243000, speed: 1, can_seek: true, art_id: 'a2', queue: [{ id: 1, title: 'Midnight City', subtitle: 'M83' }, { id: 2, title: 'Wait', subtitle: 'M83' }, { id: 3, title: 'Reunion', subtitle: 'M83' }], active_queue_id: 1 } },
  idle: { ...base, app: 'home', package: 'com.google.android.apps.tv.launcherx', media: null },
  off: { ...base, power: 'off', media: null },
};
const art = { a2: `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><defs><radialGradient id="g"><stop offset="0" stop-color="#ff8ad4"/><stop offset="1" stop-color="#2a2a8a"/></radialGradient></defs><rect width="400" height="400" fill="url(#g)"/></svg>` };
(async () => {
  const browser = await chromium.launch({ channel: 'chrome' });
  const errors = [];
  const shots = process.env.SHOTS ? JSON.parse(process.env.SHOTS) : [];
  for (const scheme of ['dark', 'light']) for (const [w, h] of [[375, 667], [390, 844]]) for (const [name, state] of Object.entries(S)) {
    const page = await browser.newPage({ viewport: { width: w, height: h }, colorScheme: scheme, deviceScaleFactor: 2, hasTouch: true, isMobile: true });
    page.on('pageerror', e => errors.push(`${name}: ${e.message}`));
    page.on('console', m => { if (m.type() === 'error' && !m.text().includes('404')) errors.push(`${name} console: ${m.text()}`); });
    let posts = [];
    await page.route('**/api/**', async route => {
      const u = new URL(route.request().url());
      if (route.request().method() === 'POST') { posts.push(u.pathname); return route.fulfill({ json: u.pathname === '/api/config' ? CONFIG : { ok: true } }); }
      if (u.pathname === '/api/context') return route.fulfill({ json: { ...state, updated_at: now, server_time: now } });
      if (u.pathname === '/api/apps') return route.fulfill({ json: APPS });
      if (u.pathname === '/api/icon') return route.continue();
      if (u.pathname === '/api/config') return route.fulfill({ json: CONFIG });
      if (u.pathname === '/api/art') return route.fulfill({ contentType: 'image/svg+xml', body: art[u.searchParams.get('id')] });
      return route.continue();  // icons: proxy to real server? fall through
    });
    await page.goto('http://remote.local/', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);
    const m = await page.evaluate(() => {
      const r = document.querySelector('#p-remote'), vh = innerHeight, over = [];
      [...r.children].filter(c => !c.hidden && getComputedStyle(c).display !== 'none').forEach(c => { const b = c.getBoundingClientRect(); if (b.bottom > r.getBoundingClientRect().bottom + 1) over.push(c.className); });
      return { docScroll: document.scrollingElement.scrollHeight - vh, remoteOverflow: over, pad: Math.round(document.querySelector('.pad').getBoundingClientRect().width), page: Math.round(document.querySelector('#pager').scrollLeft / document.querySelector('#pager').clientWidth) };
    });
    console.log(`${scheme} ${w}x${h} ${name}: docScroll=${m.docScroll} overflow=${m.remoteOverflow.join(',') || 'none'} pad=${m.pad} page=${m.page} posts=${posts.length}`);
    if (shots.includes(`${scheme}-${w}-${name}`)) {
      await page.screenshot({ path: `v2-${scheme}-${w}-${name}-remote.png` });
      await page.click('#tabbar [data-page="0"]'); await page.waitForTimeout(700); await page.screenshot({ path: `v2-${scheme}-${w}-${name}-now.png` });
      await page.click('#tabbar [data-page="2"]'); await page.waitForTimeout(900); await page.screenshot({ path: `v2-${scheme}-${w}-${name}-apps.png` });
      await page.click('#tabbar [data-page="1"]'); await page.waitForTimeout(600);
      await page.click('#open-settings'); await page.waitForTimeout(800); await page.screenshot({ path: `v2-${scheme}-${w}-${name}-qs.png` });
    }
    await page.close();
  }
  console.log('ERRORS:', errors.length ? errors.join('\n') : 'none');
  await browser.close();
})();

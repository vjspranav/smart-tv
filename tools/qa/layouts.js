const { chromium } = require('playwright');
const fs = require('fs');
const APPS = JSON.parse(fs.readFileSync('apps.json'));
const base = { connected: true, power: 'on', volume: 24, volume_min: 0, volume_max: 100, notifications: [], routine: null };
const S = {
  music: { ...base, package: 'com.spotify.tv.android', media: { package: 'com.spotify.tv.android', title: 'Midnight City', artist: 'M83', playing: true, position_ms: 61000, duration_ms: 243000, speed: 1, can_seek: true, art_id: 'a2', queue: [{ id: 1, title: 'Midnight City', subtitle: 'M83' }, { id: 2, title: 'Wait', subtitle: 'M83' }], active_queue_id: 1 } },
  limited: { ...base, package: 'com.netflix.ninja', app: 'netflix', media: null },
  idle: { ...base, package: 'com.google.android.apps.tv.launcherx', media: null },
  standby: { connected: false, standby: true, power: 'off', media: null },
};
const art = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><defs><radialGradient id="g"><stop offset="0" stop-color="#ff8ad4"/><stop offset="1" stop-color="#2a2a8a"/></radialGradient></defs><rect width="400" height="400" fill="url(#g)"/></svg>`;
(async () => {
  const b = await chromium.launch({ channel: 'chrome' });
  const errors = [];
  const sizes = [[390, 844], [844, 390], [1024, 768], [1440, 900]];
  for (const [w, h] of sizes) for (const [name, st] of Object.entries(S)) {
    const p = await b.newPage({ viewport: { width: w, height: h }, colorScheme: 'dark', deviceScaleFactor: 2, hasTouch: w < 1100, isMobile: w < 1100 });
    p.on('pageerror', e => errors.push(`${w}x${h} ${name}: ${e.message}`));
    await p.route('**/api/**', r => { const u = new URL(r.request().url());
      if (r.request().method() === 'POST') return r.fulfill({ json: { ok: true } });
      if (u.pathname === '/api/context') return r.fulfill({ json: { ...st, updated_at: Date.now() / 1000, server_time: Date.now() / 1000 } });
      if (u.pathname === '/api/apps') return r.fulfill({ json: APPS });
      if (u.pathname === '/api/art') return r.fulfill({ contentType: 'image/svg+xml', body: art });
      return r.continue(); });
    await p.goto('http://remote.local/', { waitUntil: 'networkidle' }); await p.waitForTimeout(1200);
    const m = await p.evaluate(() => {
      const vw = innerWidth, vh = innerHeight, bad = [];
      document.querySelectorAll('#app *').forEach(e => { const r = e.getBoundingClientRect(); if (r.width && getComputedStyle(e).visibility !== 'hidden' && e.offsetParent && (r.right > vw + 1) && !e.closest('.shortcuts,.apps-scroll,.pager')) bad.push(e.className || e.tagName); });
      return { layout: document.querySelector('#app').dataset.layout, docScroll: document.scrollingElement.scrollHeight - vh, hScroll: document.scrollingElement.scrollWidth - vw, bad: [...new Set(bad)].slice(0, 5), pad: Math.round(document.querySelector('#pad').getBoundingClientRect().width), detail: document.querySelector('#app-detail').textContent };
    });
    console.log(`${w}x${h} ${name}: layout=${m.layout} docScroll=${m.docScroll} hScroll=${m.hScroll} pad=${m.pad} detail="${m.detail}" offscreen=${m.bad.join(',') || 'none'}`);
    if (['music', 'limited'].includes(name) || (name === 'idle' && w === 390)) {
      if (w === 390) { await p.click('#tabbar [data-page="0"]'); await p.waitForTimeout(800); }
      await p.screenshot({ path: `L-${w}x${h}-${name}.png` });
    }
    await p.close();
  }
  console.log('ERRORS:', errors.join('\n') || 'none');
  await b.close();
})();

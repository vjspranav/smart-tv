const { chromium } = require('playwright');
const fs = require('fs');
const APPS = JSON.parse(fs.readFileSync('apps.json'));
(async () => {
  const b = await chromium.launch({ channel: 'chrome' });
  for (const [w, h] of [[375, 667], [360, 740], [844, 390]]) {
    const p = await b.newPage({ viewport: { width: w, height: h }, isMobile: true, hasTouch: true, colorScheme: 'dark', deviceScaleFactor: 2 });
    await p.route('**/api/**', r => { const u = new URL(r.request().url()); if (r.request().method() === 'POST') return r.fulfill({ json: { ok: true } });
      if (u.pathname === '/api/context') return r.fulfill({ json: { connected: true, power: 'on', volume: 20, app: 'home', media: null, updated_at: 1, server_time: 1 } });
      if (u.pathname === '/api/apps') return r.fulfill({ json: APPS }); return r.continue(); });
    await p.goto('http://remote.local/', { waitUntil: 'networkidle' });
    await p.click('#tabbar [data-page="0"]'); await p.waitForTimeout(900);
    const o = await p.evaluate(() => [...document.querySelectorAll('#now-empty *')].filter(e => e.scrollWidth > e.clientWidth + 1 || e.getBoundingClientRect().right > innerWidth).map(e => e.className || e.tagName));
    console.log(w, h, 'overflowing:', o.join(', ') || 'none');
    await p.screenshot({ path: `empty-${w}x${h}.png` });
    await p.close();
  }
  await b.close();
})();

// Dev aid: open Home on the local copy, open the house view, print console errors and failed requests.
const { chromium } = require('playwright');
const fs = require('fs');
const BASE = 'http://127.0.0.1:8765/';
(async () => {
  const secret = fs.readFileSync(process.env.QA_SECRET, 'utf8').trim(), headers = { 'Content-Type': 'application/json', 'X-TV-Remote': '1' };
  const { code } = await (await fetch(BASE + 'api/local-code', { method: 'POST', headers: { ...headers, 'X-Local-Secret': secret }, body: '{}' })).json();
  const r = await fetch(BASE + 'api/pair', { method: 'POST', headers, body: JSON.stringify({ code, name: 'debug' }) });
  const token = /remote_device=([^;]+)/.exec(r.headers.get('set-cookie'))[1];
  const browser = await chromium.launch({ channel: 'chrome' });
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
  await context.addCookies([{ name: 'remote_device', value: token, url: BASE }]);
  const page = await context.newPage();
  page.on('console', m => console.log('console', m.type(), m.text()));
  page.on('pageerror', e => console.log('pageerror', e.message));
  page.on('requestfailed', q => console.log('failed', q.url(), q.failure()?.errorText));
  page.on('response', q => { if (q.status() >= 400) console.log('status', q.status(), q.url()); });
  await page.addInitScript(() => { localStorage.setItem('remote-mode', 'home'); localStorage.setItem('remote-room', 'overview'); });
  await page.goto(BASE, { waitUntil: 'load' }); await page.waitForTimeout(2500);
  const b = await page.$('#overview [data-act="house"]'); console.log('button', !!b);
  if (b) { await b.click(); await page.waitForTimeout(4000); }
  console.log('canvas', !!(await page.$('#house-view canvas')));
  for (const device of ['vjs-light', 'living-lamp']) await page.evaluate(d => fetch('/api/home/set', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-TV-Remote': '1' }, body: JSON.stringify({ device: d, changes: { power: true } }) }), device);
  await page.waitForTimeout(2500);
  await page.click('#house-view [data-house="flat"]'); await page.waitForTimeout(1600);
  console.log('devices', await page.$eval('#house-view', e => e.dataset.devices));
  if (process.argv[2]) await page.screenshot({ path: process.argv[2] });
  await browser.close();
})();

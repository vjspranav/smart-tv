const { chromium } = require('playwright');
const fs = require('fs');
const APPS = JSON.parse(fs.readFileSync('apps.json'));
let CONFIG = { shortcuts: [{ type: 'app', package: 'com.netflix.ninja' }, { type: 'routine', id: 'movie-night' }], routines: [{ id: 'movie-night', name: 'Movie night', steps: [{ do: 'power_on' }, { do: 'volume', value: 22 }] }] };
const now = Date.now() / 1000;
const state = { connected: true, power: 'on', volume: 24, volume_min: 0, volume_max: 100, app: 'spotify', package: 'com.spotify.tv.android', notifications: [],
  media: { package: 'com.spotify.tv.android', title: 'Midnight City', artist: 'M83', playing: true, position_ms: 61000, duration_ms: 243000, speed: 1, can_seek: true, art_id: null, queue: [], active_queue_id: -1 } };
(async () => {
  const b = await chromium.launch({ channel: 'chrome' });
  const p = await b.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, colorScheme: 'dark' });
  const errors = [], posts = [];
  p.on('pageerror', e => errors.push(e.message));
  await p.route('**/api/**', async route => {
    const u = new URL(route.request().url()), req = route.request();
    if (req.method() === 'POST') { const body = JSON.parse(req.postData() || '{}'); posts.push(u.pathname + ' ' + JSON.stringify(body)); if (u.pathname === '/api/config') { CONFIG = body; return route.fulfill({ json: body }); } return route.fulfill({ json: { ok: true, active: false } }); }
    if (u.pathname === '/api/context') return route.fulfill({ json: { ...state, updated_at: now, server_time: now } });
    if (u.pathname === '/api/apps') return route.fulfill({ json: APPS });
    if (u.pathname === '/api/config') return route.fulfill({ json: CONFIG });
    return route.continue();
  });
  await p.goto('http://remote.local/', { waitUntil: 'networkidle' }); await p.waitForTimeout(800);
  const step = async (name, fn) => { try { await fn(); await p.waitForTimeout(700); console.log('ok  ', name); } catch (e) { console.log('FAIL', name, e.message.split('\n')[0]); } };
  await step('play/pause optimistic flip', async () => { await p.click('#mini-play'); const icon = await p.getAttribute('#mini-play-icon', 'href'); if (icon !== '#i-play') throw new Error('icon ' + icon); });
  await step('mini morphs to Now playing', async () => { await p.click('#mini .mini-meta'); await p.waitForTimeout(900); const pg = await p.evaluate(() => Math.round(pager.scrollLeft / pager.clientWidth)); if (pg !== 0) throw new Error('page ' + pg); });
  await step('back to remote via tab', async () => { await p.click('#tabbar [data-page="1"]'); });
  await step('pad tap OK', async () => { const r = await p.locator('#pad').boundingBox(); await p.mouse.click(r.x + r.width / 2, r.y + r.height / 2); });
  await step('try trackpad from tip', async () => { await p.click('#tip-try'); if (await p.isHidden('#trackpad')) throw new Error('trackpad hidden'); });
  await step('trackpad drag right', async () => { const r = await p.locator('#trackpad').boundingBox(); await p.mouse.move(r.x + 60, r.y + 100); await p.mouse.down(); for (let i = 1; i <= 10; i++) await p.mouse.move(r.x + 60 + i * 12, r.y + 100); await p.mouse.up(); });
  await step('keyboard sheet opens/closes', async () => { await p.click('#open-keyboard'); await p.waitForTimeout(500); await p.fill('#kb-text', 'stranger'); await p.waitForTimeout(400); await p.click('#keyboard-sheet [data-close]'); });
  await step('quick settings -> new routine -> save', async () => { await p.click('#open-settings'); await p.waitForTimeout(600); await p.click('#new-routine'); await p.waitForTimeout(700); await p.fill('#rt-name', 'Test night'); await p.click('#rt-add .chip:nth-child(4)'); await p.click('#rt-save'); });
  await step('shortcut editor add HDMI 2', async () => { await p.click('#shortcuts .edit'); await p.waitForTimeout(700); await p.click('#sc-add .chip:has-text("HDMI 2")'); await p.click('#shortcut-sheet [data-close]'); });
  await step('apps long-press sheet', async () => { await p.click('#tabbar [data-page="2"]'); await p.waitForTimeout(800); const t = p.locator('.tile').nth(4); const bb = await t.boundingBox(); await p.mouse.move(bb.x + 20, bb.y + 20); await p.mouse.down(); await p.waitForTimeout(650); await p.mouse.up(); await p.waitForTimeout(500); if (!(await p.isVisible('#app-sheet'))) throw new Error('no sheet'); await p.click('#as-shortcut'); });
  await step('voice sheet fallback/open', async () => { await p.click('#tabbar [data-page="1"]'); await p.click('#open-voice'); await p.waitForTimeout(500); await p.click('#voice-sheet [data-close]'); });
  console.log('POSTS:\n ' + posts.join('\n '));
  console.log('SHORTCUTS NOW:', JSON.stringify(CONFIG.shortcuts));
  console.log('ROUTINES NOW:', CONFIG.routines.map(r => r.name + ':' + r.steps.map(s => s.do).join('>')).join(' | '));
  console.log('ERRORS:', errors.length ? errors.join('\n') : 'none');
  await b.close();
})();

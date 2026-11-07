const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', args: ['--use-gl=angle', '--enable-webgl', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 980 } });
  const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') errors.push(m.type() + ': ' + m.text()); });
  await page.goto(process.argv[2]);
  await page.waitForTimeout(6000);
  await page.screenshot({ path: process.argv[3] });
  console.log(errors.length ? errors.join('\n') : 'ERRORS: none');
  await browser.close();
})();

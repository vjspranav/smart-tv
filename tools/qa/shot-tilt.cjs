const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', args: ['--use-gl=angle', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 980 } });
  const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' && !m.text().includes('404')) errors.push(m.text()); });
  await page.goto(process.argv[2]);
  await page.waitForTimeout(5000);
  const shot = n => page.locator('#view').screenshot({ path: `${process.argv[3]}-${n}.png` });
  await shot(1);
  await page.click('#flat'); await page.waitForTimeout(1500); await shot(2);
  const label2 = await page.textContent('#flat');
  const box = await page.locator('#view canvas').boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.keyboard.down('Shift');
  for (let i = 0; i < 6; i++) { await page.mouse.wheel(0, 20); await page.waitForTimeout(60); }
  await page.keyboard.up('Shift');
  await page.waitForTimeout(400); await shot(3);
  await page.click('#flat'); await page.waitForTimeout(1500); await shot(4);
  console.log(errors.length ? errors.join('\n') : 'ERRORS: none', '| after 2D press the button says', label2, '| at the end', await page.textContent('#flat'));
  await browser.close();
})();

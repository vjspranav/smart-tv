const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', args: ['--use-gl=angle', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 980 } });
  const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' && !m.text().includes('404')) errors.push(m.text()); });
  await page.goto(process.argv[2]);
  await page.waitForTimeout(5000);
  await page.click('#walk');
  await page.waitForTimeout(2200);
  await page.screenshot({ path: process.argv[3] + '-1.png' });
  const box = await page.locator('#view canvas').boundingBox();
  // drag to look left, then tap the floor ahead-right to glide
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down(); await page.mouse.move(box.x + box.width / 2 - 300, box.y + box.height / 2, { steps: 10 }); await page.mouse.up();
  await page.waitForTimeout(500);
  await page.screenshot({ path: process.argv[3] + '-2.png' });
  await page.mouse.click(box.x + box.width / 2, box.y + box.height * 0.8);
  await page.waitForTimeout(2000);
  await page.screenshot({ path: process.argv[3] + '-3.png' });
  // walk in from the plan: click Green's room on the floor plan
  const plan = await page.locator('#plan svg').boundingBox();
  await page.mouse.click(plan.x + plan.width * 0.8, plan.y + plan.height * 0.75);
  await page.waitForTimeout(2200);
  await page.screenshot({ path: process.argv[3] + '-4.png' });
  await page.click('#walk');
  await page.waitForTimeout(1800);
  console.log(errors.length ? errors.join('\n') : 'ERRORS: none', '| mode button:', await page.textContent('#walk'));
  await browser.close();
})();

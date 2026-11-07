const { chromium } = require('playwright');
(async () => {
  for (const [label, url, args] of [
    ['http://remote.local', 'http://remote.local/', []],
    ['http://IP:8765', 'http://192.168.0.48:8765/', []],
    ['https://remote.local (cert trusted)', 'https://remote.local/', ['--ignore-certificate-errors-spki-list=+620JkQo0Q+JZ7VgoiC/liLVr3ieFMrueNO2tCaSTfM=']],
    ['https://192.168.0.48 (cert trusted)', 'https://192.168.0.48/', ['--ignore-certificate-errors-spki-list=+620JkQo0Q+JZ7VgoiC/liLVr3ieFMrueNO2tCaSTfM=']],
  ]) {
    const ctx = await chromium.launchPersistentContext('', { channel: 'chrome', headless: true, args });
    const page = await ctx.newPage();
    try {
      await page.goto(url, { waitUntil: 'networkidle', timeout: 15000 });
      await page.waitForTimeout(2500);
      const cdp = await ctx.newCDPSession(page);
      const errs = await cdp.send('Page.getInstallabilityErrors');
      const man = await cdp.send('Page.getAppManifest');
      const sw = await page.evaluate(async () => ({ secure: isSecureContext, controlled: !!navigator.serviceWorker?.controller, regs: (await navigator.serviceWorker?.getRegistrations?.() || []).length }));
      console.log(label, JSON.stringify({ ...sw, manifestErrors: man.errors, installabilityErrors: errs.installabilityErrors.map(e => e.errorId) }));
    } catch (e) { console.log(label, 'ERR', e.message.slice(0, 120)); }
    await ctx.close();
  }
})();

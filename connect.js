'use strict';
/* Reaching home and who may use the remote.
   - If the Pi doesn't answer (away from home with Tailscale off), a card asks to turn Tailscale on and closes by
     itself as soon as home answers again.
   - If the Pi says this phone isn't approved (it was removed), reload: the Pi then shows the pairing page.
   - Remote settings → Phones: see, rename and remove phones, and make a code for a new one. */
(() => {
  const ANDROID = /Android/i.test(navigator.userAgent);
  const TAILSCALE = ANDROID ? 'intent://#Intent;package=com.tailscale.ipn;end' : 'https://apps.apple.com/app/tailscale/id1470499037';
  const realFetch = window.fetch.bind(window);
  const TAILSCALE_ADDRESS = 'https://100.110.26.84/';      // the Pi itself on Tailscale: works for phones it's shared with
  const onTailscaleAddress = location.origin + '/' === TAILSCALE_ADDRESS;

  const card = document.createElement('div');
  card.className = 'away'; card.hidden = true; card.setAttribute('role', 'alertdialog'); card.setAttribute('aria-labelledby', 'away-title');
  card.innerHTML = `<div class="away-card">
      <span class="away-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 10.5 12 4l9 6.5M5.5 9v10h13V9"/><path d="M10 19v-5h4v5"/></svg></span>
      <h2 id="away-title">Can't reach home</h2>
      <p>If you're out, turn on Tailscale. This closes by itself once home answers.</p>
      <a class="accent-btn" href="${TAILSCALE}" rel="noopener">Open Tailscale</a>
      ${onTailscaleAddress ? '' : `<a class="ghost-btn" href="${TAILSCALE_ADDRESS}">Tailscale on? Use the Tailscale address</a>`}
      <button type="button" class="ghost-btn" id="away-retry">Try again</button>
    </div>`;
  document.body.append(card);

  async function ping() {
    const stop = new AbortController(), timer = setTimeout(() => stop.abort(), 6000);   // weak mobile signal over Tailscale can take seconds
    try { return (await realFetch('/api/ping', { cache: 'no-store', signal: stop.signal })).ok; }
    catch { return false; }
    finally { clearTimeout(timer); }
  }
  let retry = 0, checking = false;
  async function check() {
    if (checking) return;
    checking = true;
    const ok = await ping();
    checking = false;
    clearTimeout(retry);
    if (ok && !card.hidden) return location.reload();       // back home: start fresh with live state
    card.hidden = ok;
    if (!ok) retry = setTimeout(check, 3000);
  }
  card.querySelector('#away-retry').addEventListener('click', check);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) check(); });
  window.addEventListener('online', check);

  // Every request the app makes: a network failure means "maybe away", a 401 means "this phone was removed"
  window.fetch = async (...args) => {
    try {
      const response = await realFetch(...args);
      if (response.status === 401) location.reload();
      return response;
    } catch (error) {
      if (error.name !== 'AbortError') check();
      throw error;
    }
  };
  check();

  /* ---- Phones sheet ---------------------------------------------------------------------------------- */
  let sheet = null, countdown = 0;
  const api = async (body) => {
    const r = await realFetch('/api/devices', body ? { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-TV-Remote': '1' }, body: JSON.stringify(body) } : { cache: 'no-store' });
    const data = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(data.error || "Couldn't reach home");
    return data;
  };
  const ago = t => { const m = Math.round((Date.now() / 1000 - t) / 60); return m < 60 ? 'used in the last hour' : m < 1440 ? `used ${Math.round(m / 60)} h ago` : `used ${Math.round(m / 1440)} d ago`; };

  async function draw() {
    const { devices } = await api();
    sheet.querySelector('.ed-body').innerHTML = `
      <ul class="phones">${devices.map(d => `<li data-id="${d.id}">
        <span class="phone-name"><strong>${esc(d.name)}</strong><small>${d.this ? 'This phone' : d.kind === 'widget' ? `Widget key, ${d.last_seen ? ago(d.last_seen) : 'not used yet'}` : ago(d.last_seen)}</small></span>
        <button type="button" class="ghost-btn" data-rename="${d.id}">Rename</button>
        <button type="button" class="ghost-btn danger" data-remove="${d.id}">${d.this ? 'Leave' : 'Remove'}</button></li>`).join('')}</ul>
      <div class="new-code" id="new-code" hidden></div>
      <button type="button" class="accent-btn" data-add>Add a phone</button>
      <div class="new-key" id="new-key" hidden></div>
      <button type="button" class="ghost-btn" data-add-key>Add a widget key</button>`;
  }

  // A widget key lets iPhone Shortcuts or Android HTTP Shortcuts run scenes and switch devices (nothing else)
  const WIDGET_ADDRESS = 'https://192.168.0.48';
  async function widgetActions() {
    const get = async path => { const r = await realFetch(path, { cache: 'no-store' }); if (!r.ok) throw new Error("Couldn't reach home"); return r.json(); };
    const [store, snap] = await Promise.all([get('/api/home/routines'), get('/api/home/state')]);
    const room = id => snap.rooms.find(r => r.id === id)?.name || id;
    return [
      ...store.routines.map(r => ({ name: r.name, path: '/api/home/run', body: { routine: r.id } })),
      ...store.builtin.filter(g => g.id.endsWith('-lights') && g.id !== 'all-lights').map(g => ({ name: `${g.name} on/off`, path: '/api/home/toggle', body: { target: g.id } })),
      ...snap.devices.map(d => ({ name: `${room(d.room)} ${d.name} on/off`, path: '/api/home/toggle', body: { target: d.id } })),
    ];
  }
  function keyHtml(key, actions) {
    const copy = (text, label) => `<button type="button" class="ghost-btn copy" data-copy="${esc(text)}">${label}</button>`;
    return `<p><strong>Copy this key now.</strong> It's shown only once. Remove it here to switch its widgets off.</p>
      <div class="copy-row"><code>${esc(key)}</code>${copy(key, 'Copy key')}</div>
      <details><summary>iPhone: Shortcuts app</summary><ol>
        <li>In Shortcuts, tap <b>+</b> and add the action <b>Get Contents of URL</b>.</li>
        <li>Address: <code>${WIDGET_ADDRESS}</code> followed by the path from the list below.</li>
        <li>Under <b>Show More</b>: Method <b>POST</b>. Headers: <b>Authorization</b> = <code>Bearer</code>, a space, then the key; <b>X-TV-Remote</b> = <code>1</code>.</li>
        <li>Request Body <b>JSON</b>: add a Text field with the name and value from the list, for example <code>routine</code> = <code>good-night</code>.</li>
        <li>Name the shortcut, then add the <b>Shortcuts</b> widget to your Home Screen or Lock Screen. "Hey Siri" plus its name works too.</li></ol></details>
      <details><summary>Android: HTTP Shortcuts app</summary><ol>
        <li>Install <b>HTTP Shortcuts</b>, tap <b>+</b>, then <b>Regular Shortcut</b>.</li>
        <li>Method <b>POST</b>, address <code>${WIDGET_ADDRESS}</code> followed by the path from the list below.</li>
        <li>Request headers: <b>Authorization</b> = <code>Bearer</code>, a space, then the key; <b>X-TV-Remote</b> = <code>1</code>.</li>
        <li>Request body: <b>Custom text</b>, type <code>application/json</code>, and paste the line from the list.</li>
        <li>If it says the certificate isn't trusted, allow it in the shortcut's advanced settings.</li>
        <li>Long-press your home screen, tap <b>Widgets</b>, and add HTTP Shortcuts.</li></ol></details>
      <p>Away from home, Tailscale needs to be on, just like for the app.</p>
      <ul class="widget-actions">${actions.map(a => { const body = JSON.stringify(a.body); return `<li><span><strong>${esc(a.name)}</strong>
        <small>${esc(a.path)}</small><code>${esc(body)}</code></span>${copy(body, 'Copy')}</li>`; }).join('')}</ul>`;
  }
  async function openPhones() {
    sheet ||= makeSheet('phones-sheet', 'Phones');
    try { await draw(); openSheet(sheet); } catch (error) { toast(error.message, true); }
  }
  document.addEventListener('click', async e => {
    if (e.target.closest('#qs-phones')) { closeSheet(document.querySelector('#settings-sheet')); return openPhones(); }
    if (!sheet || !e.target.closest('#phones-sheet')) return;
    const b = e.target.closest('button');
    if (!b) return;
    try {
      if (b.dataset.add !== undefined) {
        const { code, expires_in } = await api({ action: 'code' });
        const box = sheet.querySelector('#new-code'), end = Date.now() + expires_in * 1000;
        box.hidden = false;
        clearInterval(countdown);
        const tick = () => {
          const left = Math.max(0, Math.round((end - Date.now()) / 1000));
          box.innerHTML = left ? `<span>On the new phone, open the remote and type</span><output>${code.slice(0, 3)} ${code.slice(3)}</output><small>Works for ${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}</small>`
                               : '<span>That code ran out. Tap Add a phone for a new one.</span>';
          if (!left) clearInterval(countdown);
        };
        tick(); countdown = setInterval(tick, 1000);
      } else if (b.dataset.addKey !== undefined) {
        const name = prompt('Name for this widget key (whose phone?)', 'Widgets');
        if (!name || !name.trim()) return;
        const [{ key }, actions] = await Promise.all([api({ action: 'key', name }), widgetActions()]);
        await draw();
        const box = sheet.querySelector('#new-key');
        box.innerHTML = keyHtml(key, actions); box.hidden = false;
        box.scrollIntoView({ block: 'start', behavior: 'smooth' });
      } else if (b.dataset.copy !== undefined) {
        try { await navigator.clipboard.writeText(b.dataset.copy); toast('Copied'); }
        catch { toast("Couldn't copy. Press and hold the text instead.", true); }
      } else if (b.dataset.remove) {
        if (b.dataset.sure !== '1') { b.dataset.sure = '1'; b.textContent = 'Tap again'; return; }
        await api({ action: 'remove', id: b.dataset.remove });
        if (b.textContent && b.closest('li').querySelector('small').textContent === 'This phone') return location.reload();
        toast('Phone removed'); await draw();
      } else if (b.dataset.rename) {
        const li = b.closest('li'), current = li.querySelector('strong').textContent;
        const name = prompt('Name for this phone', current);
        if (name && name.trim() && name !== current) { await api({ action: 'rename', id: b.dataset.rename, name }); await draw(); }
      }
    } catch (error) { toast(error.message, true); }
  });
})();

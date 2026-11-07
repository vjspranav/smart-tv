'use strict';
/* Pairing page: trade a 6-digit code from an approved phone for this phone's own access cookie. */
const form = document.getElementById('pair-form'), error = document.getElementById('error');
const code = document.getElementById('code'), name = document.getElementById('name');
const iOS = /iPhone|iPad|iPod/.test(navigator.userAgent);
if (iOS && navigator.standalone === false) document.getElementById('ios-first').hidden = false;   // Safari and the Home Screen app keep separate cookies
try { name.value = localStorage.getItem('remote-phone-name') || ''; } catch {}
// Already approved but the page came without the cookie (an older Android cookie on a home-screen launch): the ping
// renews it, so go back to the app. Once per visit, so a phone that really isn't approved never loops.
fetch('/api/ping', { cache: 'no-store' }).then(r => r.json()).then(data => {
  if (!data.paired) return;
  try { if (sessionStorage.getItem('remote-pair-retry')) return; sessionStorage.setItem('remote-pair-retry', '1'); } catch { return; }
  location.replace('/');
}).catch(() => {});
code.addEventListener('input', () => { code.value = code.value.replace(/\D/g, '').slice(0, 6); error.textContent = ''; });

form.addEventListener('submit', async e => {
  e.preventDefault();
  error.textContent = '';
  const button = form.querySelector('button');
  button.disabled = true;
  try {
    const r = await fetch('/api/pair', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-TV-Remote': '1' },
                                        body: JSON.stringify({ code: code.value, name: name.value }) });
    const data = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(data.error || "Couldn't reach home. Try again.");
    try { localStorage.setItem('remote-phone-name', name.value); } catch {}
    location.replace('/');
  } catch (err) {
    error.textContent = err.message === 'Failed to fetch' ? "Couldn't reach home. Are you on the home Wi-Fi or Tailscale?" : err.message;
    button.disabled = false;
  }
});

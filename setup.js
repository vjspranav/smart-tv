'use strict';
const $ = s => document.querySelector(s);
const ios = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const android = /Android/.test(navigator.userAgent);
const standalone = matchMedia('(display-mode: standalone)').matches || navigator.standalone;

function mark(id, good, text) {
  const el = $(id);
  el.className = 'check ' + (good ? 'good' : 'bad');
  el.querySelector('span').textContent = text;
}

// Put the steps for this phone first.
if (ios) $('#ios').classList.add('mine');
if (android) { $('#android').classList.add('mine'); $('#android').after($('#ios')); }

if (isSecureContext) {
  mark('#c-secure', true, 'Secure connection works. This device can install the app.');
} else {
  const target = 'https://' + location.hostname;
  mark('#c-secure', false, `Not secure yet. Follow the steps below, then open ${target}`);
}
if (android && location.hostname !== '192.168.0.48') $('#go-ip').hidden = false;
mark('#c-installed', !!standalone, standalone ? 'Running as an installed app' : 'Not installed on this device yet');

let prompt;
window.addEventListener('beforeinstallprompt', e => {
  e.preventDefault();
  prompt = e;
  $('#install-now').hidden = false;
});
$('#install-button').addEventListener('click', async () => {
  if (!prompt) return;
  await prompt.prompt();
  prompt = null;
  $('#install-now').hidden = true;
});
window.addEventListener('appinstalled', () => mark('#c-installed', true, 'Installed'));
if ('serviceWorker' in navigator && isSecureContext) navigator.serviceWorker.register('/sw.js').catch(() => {});

// App shell, plus the house view's files once it has been opened. TV and home state (/api/*) always go to the network.
const CACHE = 'tv-remote-v18';
const THREE_CACHE = 'three-0.160.0';     // the house view's 3D library: versioned, so it survives app updates
const HOUSE = ['/house.js', '/house-models.js', '/house-logic.js', '/house.css'];   // kept once fetched (the stylesheet loads with the page)
const SHELL = ['/', '/app.css', '/app.js', '/connect.js', '/home.css', '/home.js', '/home-overview.js', '/house-plan.json', '/manifest.webmanifest', '/icon-180.png', '/icon-192.png', '/icon-512.png',
  '/assets/netflix.svg', '/assets/prime.svg', '/assets/hotstar.jpg', '/assets/youtube.svg', '/assets/spotify.svg', '/assets/ps5.svg'];
const WAIT = 2500;   // away from home without Tailscale the Pi never answers: show the cached app (and its "turn on Tailscale" card) instead of hanging
self.addEventListener('install', e => e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())));
self.addEventListener('activate', e => e.waitUntil(caches.keys()
  .then(keys => Promise.all(keys.filter(k => (k.startsWith('tv-remote-') && k !== CACHE) || (k.startsWith('three-') && k !== THREE_CACHE)).map(k => caches.delete(k))))
  .then(() => self.clients.claim())));
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  if (url.pathname.startsWith('/vendor/three/')) {        // cache first: the path names the version
    e.respondWith(caches.open(THREE_CACHE).then(c => c.match(e.request).then(hit => hit || fetch(e.request).then(r => {
      if (r.ok) c.put(e.request, r.clone());
      return r;
    }))));
    return;
  }
  if (!SHELL.includes(url.pathname) && !HOUSE.includes(url.pathname)) return;
  // Network first so updates show up immediately; the cached copy if the Pi is slow or unreachable.
  const network = fetch(e.request).then(r => {
    if (r.ok && !r.headers.has('X-Pair-Page')) { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); }
    return r;
  });
  const cached = new Promise(resolve => setTimeout(resolve, WAIT)).then(() => caches.match(e.request));
  e.respondWith(Promise.race([network.catch(() => caches.match(e.request)), cached.then(r => r || network)])
    .then(r => r || new Response('Remote unavailable', { status: 503 })));
});

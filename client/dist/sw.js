/*
 * Jarvis PWA service worker — makes the HUD installable + usable on a phone.
 * - Caches the app shell so it opens offline (falls back to the cached index for navigations).
 * - NEVER caches /api/* (always live data); network-first there.
 * - Handles Web Push (display) + notification clicks, so the full push pipeline only needs the
 *   backend VAPID send side wired later — the client side is ready.
 */
const CACHE = 'jarvis-shell-v1';
const SHELL = ['/', '/index.html', '/favicon.svg', '/manifest.webmanifest'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // API + SSE: always live, never cached.
  if (url.pathname.startsWith('/api/')) return;

  // Navigations: serve the app shell offline.
  if (req.mode === 'navigate') {
    event.respondWith(fetch(req).catch(() => caches.match('/index.html')));
    return;
  }

  // Static assets: cache-first, then update in the background.
  event.respondWith(
    caches.match(req).then((hit) =>
      hit || fetch(req).then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(req, copy)).catch(() => {});
        return res;
      }).catch(() => hit)
    )
  );
});

// Web Push (display side ready; backend VAPID send is the follow-on).
self.addEventListener('push', (event) => {
  let data = { title: 'Jarvis', body: 'You have a new notification.' };
  try { if (event.data) data = { ...data, ...event.data.json() }; } catch (_) {}
  event.waitUntil(
    self.registration.showNotification(data.title || 'Jarvis', {
      body: data.body || '',
      icon: '/favicon.svg',
      badge: '/favicon.svg',
      tag: data.tag || 'jarvis',
      data: data.url || '/',
    })
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const target = event.notification.data || '/';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((cls) => {
      for (const c of cls) { if ('focus' in c) return c.focus(); }
      return self.clients.openWindow(target);
    })
  );
});

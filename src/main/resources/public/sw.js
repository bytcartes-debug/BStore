// BStore Service Worker - Network First with Cache Fallback
const CACHE_NAME = 'bstore-cache-v1';

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) return caches.delete(key);
        })
      )
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  // Ignora requisições de API para não cachear dados transacionais de forma descontrolada
  if (request.url.includes('/api/')) {
    return;
  }

  // Apenas métodos GET
  if (request.method !== 'GET') {
    return;
  }

  event.respondWith(
    fetch(request)
      .then((networkResponse) => {
        if (networkResponse && networkResponse.status === 200) {
          const responseToCache = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(request, responseToCache);
          });
        }
        return networkResponse;
      })
      .catch(async () => {
        const cachedResponse = await caches.match(request);
        if (cachedResponse) {
          return cachedResponse;
        }
        if (request.mode === 'navigate') {
          return caches.match('/');
        }
        return new Response('Sem conexão', { status: 503, statusText: 'Offline' });
      })
  );
});

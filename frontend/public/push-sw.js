/*
 * 폰 알림(Web Push)을 받아 알림창에 띄우고, 누르면 그 요청을 연다.
 *
 * vite-plugin-pwa 가 만드는 서비스 워커가 importScripts 로 이 파일을 불러온다(vite.config.ts).
 * 워커를 직접 쓰지 않는 것은 앱 껍데기 캐시 규칙을 플러그인 한 곳에 두기 위해서다.
 *
 * 여기서 API 를 부르지 않는다. 본문에 이미 알림함과 같은 문구가 들어 있다 —
 * 알림을 띄우려고 서버에 다시 물으면 원내망 밖이거나 토큰이 없을 때 알림이 안 뜬다.
 */
self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    // 풀 수 없는 본문이어도 무언가 왔다는 것은 알린다. 조용히 삼키면 놓친 줄도 모른다.
  }

  const title = data.title || '새 알림';
  event.waitUntil(
    self.registration.showNotification(title, {
      body: data.body || '',
      icon: '/icon-192.png',
      badge: '/icon-192.png',
      // 같은 요청의 다음 알림이 앞의 것을 덮는다. 한 건에 알림이 여러 개 쌓이면 알림창이 목록이 된다.
      tag: data.tag,
      // 덮더라도 소리·진동은 다시 낸다. 상태가 바뀐 것을 모르고 지나가면 안 된다.
      renotify: Boolean(data.tag),
      data: { url: data.url || '/' },
      lang: 'ko',
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = new URL(event.notification.data?.url || '/', self.location.origin).href;

  event.waitUntil(
    (async () => {
      // 앱이 이미 열려 있으면 새 창을 띄우지 않고 그 창을 앞으로 가져와 옮긴다
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
      for (const client of windows) {
        if (new URL(client.url).origin === self.location.origin) {
          await client.focus();
          return client.navigate(url);
        }
      }
      return self.clients.openWindow(url);
    })(),
  );
});

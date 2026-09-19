import { api } from '@/shared/api/client';

/**
 * 이 기기에서 폰 알림(Web Push)을 받을지.
 *
 * 서비스 워커가 있어야 받는다. 개발 서버는 워커를 끄므로(vite.config.ts) 여기서는 "지원 안 함" 으로 나온다 —
 * 확인은 `npm run build && npm run preview` 로 한다. iOS 는 홈 화면에 설치한 앱에서만 된다.
 */
export type PushState =
  | 'unsupported' // 브라우저가 못 하거나 워커가 없다
  | 'unavailable' // 서버에 키가 없다
  | 'denied' // 사용자가 알림 권한을 막았다 — 브라우저 설정에서만 풀 수 있다
  | 'off'
  | 'on';

async function registration(): Promise<ServiceWorkerRegistration | null> {
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
    return null;
  }
  // ready 는 워커가 없으면 영영 끝나지 않는다. 있는지 먼저 본다.
  return (await navigator.serviceWorker.getRegistration()) ?? null;
}

async function publicKey(): Promise<string | null> {
  return (await api.get<{ publicKey: string | null }>('/push/public-key')).data.publicKey;
}

export async function pushState(): Promise<PushState> {
  const reg = await registration();
  if (!reg) return 'unsupported';
  if (!(await publicKey())) return 'unavailable';
  if (Notification.permission === 'denied') return 'denied';
  const subscription = await reg.pushManager.getSubscription();
  return subscription ? 'on' : 'off';
}

export async function enablePush(): Promise<PushState> {
  const reg = await registration();
  const key = await publicKey();
  if (!reg || !key) return 'unsupported';

  // 권한 창은 사용자가 버튼을 누른 그 순간에만 띄운다. 화면을 열자마자 묻으면 대부분 막고, 다시 물을 수 없다.
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') return permission === 'denied' ? 'denied' : 'off';

  const subscription =
    (await reg.pushManager.getSubscription()) ??
    (await reg.pushManager.subscribe({
      // 서버에서 받은 알림만 띄우겠다는 약속. 크롬은 이게 없으면 구독을 거절한다.
      userVisibleOnly: true,
      applicationServerKey: base64UrlToBytes(key),
    }));

  await api.post('/push/subscriptions', subscription.toJSON());
  return 'on';
}

export async function disablePush(): Promise<PushState> {
  const reg = await registration();
  const subscription = await reg?.pushManager.getSubscription();
  if (subscription) {
    // 서버에서 먼저 뺀다. 브라우저 구독만 풀고 서버에 남으면 푸시 서비스가 410 을 줄 때까지 헛걸음을 한다.
    await api.delete('/push/subscriptions', { data: { endpoint: subscription.endpoint } }).catch(() => {});
    await subscription.unsubscribe();
  }
  return 'off';
}

/**
 * 로그아웃할 때 부른다. 병동 폰은 돌려 쓴다 — 빼지 않으면 다음 사람이 앞사람 환자 알림을 받는다.
 * 실패해도 로그아웃은 막지 않는다. 서버는 같은 기기로 다음 사람이 등록하면 넘겨주기도 한다.
 */
export async function releasePushOnLogout(): Promise<void> {
  try {
    await disablePush();
  } catch {
    // 로그아웃은 무조건 되어야 한다
  }
}

function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const base64 = (value + '='.repeat((4 - (value.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i += 1) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

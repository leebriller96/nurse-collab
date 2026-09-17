import { useEffect, useState } from 'react';
import { disablePush, enablePush, pushState, type PushState } from '@/shared/lib/push';
import { useToast } from '@/shared/ui/toast';

/**
 * 이 기기에서 폰 알림 받기.
 *
 * 켤 수 없는 까닭이 있으면 버튼을 감추지 않고 말한다. 감추면 "알림이 안 온다" 는 말만 남고
 * 무엇을 해야 하는지 아무도 모른다. 서버에 키가 없는 것만은 감춘다 — 간호사가 할 수 있는 일이 없다.
 */
export default function PushToggle() {
  const [state, setState] = useState<PushState | null>(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  useEffect(() => {
    let alive = true;
    pushState()
      .then((s) => alive && setState(s))
      .catch(() => alive && setState('unavailable'));
    return () => {
      alive = false;
    };
  }, []);

  if (state === null || state === 'unavailable') return null;

  const toggle = async () => {
    setBusy(true);
    try {
      const next = state === 'on' ? await disablePush() : await enablePush();
      setState(next);
      if (next === 'on') toast.show('이 기기에서 알림을 받습니다', { tone: 'success' });
    } catch {
      toast.show('알림 설정을 바꾸지 못했습니다', { body: '잠시 뒤 다시 시도해 주세요.', tone: 'info' });
    } finally {
      setBusy(false);
    }
  };

  if (state === 'unsupported') {
    return (
      <p className="mt-2 text-xs text-slate-500">
        이 브라우저에서는 폰 알림을 받을 수 없습니다. 아이폰은 홈 화면에 추가한 앱에서만 됩니다.
      </p>
    );
  }
  if (state === 'denied') {
    return (
      <p className="mt-2 text-xs text-slate-500">
        알림 권한이 막혀 있습니다. 브라우저 설정에서 이 사이트의 알림을 허용해 주세요.
      </p>
    );
  }

  return (
    <label className="mt-2 flex items-center justify-between rounded-lg bg-white px-3 py-2 text-sm ring-1 ring-slate-200">
      <span className="text-slate-700">
        이 기기에서 알림 받기
        <span className="block text-xs text-slate-400">로그아웃하면 이 기기에서 꺼집니다</span>
      </span>
      <input
        type="checkbox"
        className="h-5 w-5 accent-sky-600"
        checked={state === 'on'}
        disabled={busy}
        onChange={() => void toggle()}
      />
    </label>
  );
}

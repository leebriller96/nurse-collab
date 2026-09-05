import { useCallback, useState } from 'react';
import { useAuth } from '@/shared/hooks/useAuth';
import { useRealtime } from '@/shared/hooks/useRealtime';
import type { RealtimeEvent } from '@/shared/hooks/useRealtime';
import { statusLabel } from '@/shared/ui/badges';

interface Toast {
  key: number;
  title: string;
  body: string;
  emphasis: boolean;
}

function describe(event: RealtimeEvent): Toast {
  const who = `${event.actorDepartmentName} ${event.actorName}`;
  const patient = `${event.roomNo}호 ${event.patientName} · ${event.examName}`;

  if (event.eventType === 'TRANSFER_CREATED') {
    return {
      key: Date.now() + Math.random(),
      title: `새 요청 (${event.priority === 'EMERGENCY' ? '응급' : event.priority === 'URGENT' ? '긴급' : '일반'})`,
      body: `${patient} — ${who}`,
      emphasis: event.priority === 'EMERGENCY',
    };
  }
  if (event.eventType === 'MESSAGE_CREATED') {
    return { key: Date.now() + Math.random(), title: '새 메시지', body: `${patient} — ${who}`, emphasis: false };
  }
  return {
    key: Date.now() + Math.random(),
    title: event.toStatus ? statusLabel(event.toStatus) : '상태 변경',
    body: `${patient} — ${who}`,
    emphasis: false,
  };
}

/**
 * 파트 채널을 구독하고, 들어온 변화를 잠깐 띄운다.
 * 화면 갱신 자체는 useRealtime 이 캐시를 무효화해서 처리한다. 여기는 알림만 담당한다.
 */
export default function RealtimeToasts() {
  const { staff } = useAuth();
  const [toasts, setToasts] = useState<Toast[]>([]);

  const push = useCallback((event: RealtimeEvent) => {
    const toast = describe(event);
    setToasts((prev) => [...prev, toast].slice(-3));
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.key !== toast.key));
    }, 6000);
  }, []);

  const { connected } = useRealtime(staff?.department.id, push);

  return (
    <>
      <div className="pointer-events-none fixed inset-x-0 top-2 z-50 mx-auto flex max-w-md flex-col gap-2 px-3">
        {toasts.map((toast) => (
          <div
            key={toast.key}
            role="status"
            className={`rounded-xl px-4 py-3 shadow-lg ring-1 ${
              toast.emphasis
                ? 'bg-red-600 text-white ring-red-700'
                : 'bg-slate-900/95 text-white ring-slate-700'
            }`}
          >
            <p className="text-sm font-bold">{toast.title}</p>
            <p className="text-xs opacity-90">{toast.body}</p>
          </div>
        ))}
      </div>

      <span
        title={connected ? '실시간 연결됨' : '연결 끊김 — 재연결 중'}
        className={`fixed bottom-1 right-1 z-50 h-2 w-2 rounded-full ${
          connected ? 'bg-emerald-500' : 'bg-slate-300'
        }`}
      />
    </>
  );
}

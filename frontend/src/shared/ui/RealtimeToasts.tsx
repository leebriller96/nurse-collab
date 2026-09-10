import { useCallback } from 'react';
import { useAuth } from '@/shared/hooks/useAuth';
import { useRealtime } from '@/shared/hooks/useRealtime';
import type { RealtimeEvent } from '@/shared/hooks/useRealtime';
import { priorityLabel, statusLabel } from '@/shared/ui/badges';
import { useToast, type ToastTone } from '@/shared/ui/toast';

function describe(event: RealtimeEvent): { title: string; body: string; tone: ToastTone } {
  const who = `${event.actorDepartmentName} ${event.actorName}`;
  const patient = `${event.roomNo}호 ${event.patientName} · ${event.examName}`;

  if (event.eventType === 'TRANSFER_CREATED') {
    return {
      title: `새 요청 (${priorityLabel(event.priority)})`,
      body: `${patient} — ${who}`,
      tone: event.priority === 'EMERGENCY' ? 'urgent' : 'info',
    };
  }
  if (event.eventType === 'MESSAGE_CREATED') {
    return { title: '새 메시지', body: `${patient} — ${who}`, tone: 'info' };
  }
  return {
    title: event.toStatus ? statusLabel(event.toStatus) : '상태 변경',
    body: `${patient} — ${who}`,
    tone: 'info',
  };
}

/**
 * 파트 채널을 구독하고, 들어온 변화를 잠깐 띄운다.
 * 화면 갱신 자체는 useRealtime 이 캐시를 무효화해서 처리한다. 여기는 알림만 담당한다.
 *
 * 띄우는 일은 ToastProvider 가 한다. 내 동작 확인과 같은 자리에 뜨므로
 * 따로 그리면 둘이 겹친다.
 */
export default function RealtimeToasts() {
  const { staff } = useAuth();
  const toast = useToast();

  const push = useCallback(
    (event: RealtimeEvent) => {
      // 내가 한 일은 나에게 알리지 않는다.
      // 방송은 파트 채널로 나가므로 누른 사람에게도 되돌아온다. 그대로 두면
      // "환자 출발 처리했습니다" 확인과 "이송중 — 3병동 김간호" 알림이 동시에 뜬다.
      // 알림함이 이미 지키는 규칙과 같다.
      if (event.actorId === staff?.id) return;

      const { title, body, tone } = describe(event);
      toast.show(title, { body, tone });
    },
    [toast, staff?.id],
  );

  const { connected } = useRealtime(staff?.department.id, push);

  return (
    <span
      title={connected ? '실시간으로 받는 중' : '연결을 다시 맺는 중'}
      className={`fixed bottom-1 right-1 z-50 h-2 w-2 rounded-full ${
        connected ? 'bg-emerald-500' : 'bg-slate-300'
      }`}
    />
  );
}

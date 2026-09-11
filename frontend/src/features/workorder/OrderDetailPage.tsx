import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import type { Message, OrderDetail, OrderEvent, OrderStatus } from '@/shared/api/types';
import { AlertBadge, PriorityBadge, StatusBadge, actionLabel, statusLabel } from '@/shared/ui/badges';
import LoadFailed from '@/shared/ui/LoadFailed';
import { useToast } from '@/shared/ui/toast';
import { DetailSkeleton } from '@/shared/ui/Skeleton';

/** 전이마다 무엇을 더 받아야 하는지. 서버 규칙과 짝을 이룬다. */
const NEEDS_REASON: OrderStatus[] = ['ON_HOLD', 'CANCELLED'];
const NEEDS_SCHEDULE: OrderStatus[] = ['ACCEPTED'];

/**
 * 한 번 더 묻는 전이.
 *
 * 앞으로 나아가는 동작은 대부분 바로 실행한다. 이동 중에 한 손으로 누르는 화면이라
 * 매번 확인을 받으면 오히려 방해가 된다.
 *
 * 완료만 예외다. 종료 상태여서 여기서 나가는 전이가 규칙표에 없다.
 * 잘못 누르면 되돌릴 방법이 없고, 이력은 지우지 않으므로 흔적도 남는다.
 * 취소는 사유를 받으므로 이미 한 단계를 거친다.
 */
const NEEDS_CONFIRM: OrderStatus[] = ['COMPLETED'];

const time = (iso: string) =>
  new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });

/** W-05 / E-02 요청 상세. 병동과 검사실이 같은 화면을 쓴다. */
export default function OrderDetailPage() {
  const { id } = useParams();
  const requestId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();

  const [pending, setPending] = useState<OrderStatus | null>(null);
  const [reason, setReason] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [draft, setDraft] = useState('');
  const [error, setError] = useState<string | null>(null);

  const detail = useQuery({
    queryKey: ['transfer-request', requestId],
    queryFn: async () => (await api.get<OrderDetail>(`/work-orders/${requestId}`)).data,
    // 실시간 알림이 주 경로다. 폴링은 알림을 놓쳤을 때를 위한 보조 장치로만 남긴다.
    refetchInterval: 60_000,
  });

  const events = useQuery({
    queryKey: ['transfer-request', requestId, 'events'],
    queryFn: async () => (await api.get<OrderEvent[]>(`/work-orders/${requestId}/events`)).data,
  });

  const messages = useQuery({
    queryKey: ['transfer-request', requestId, 'messages'],
    queryFn: async () => (await api.get<Message[]>(`/work-orders/${requestId}/messages`)).data,
    // 실시간 알림이 주 경로다. 폴링은 알림을 놓쳤을 때를 위한 보조 장치로만 남긴다.
    refetchInterval: 60_000,
  });

  const refreshAll = () => {
    void queryClient.invalidateQueries({ queryKey: ['transfer-request', requestId] });
    void queryClient.invalidateQueries({ queryKey: ['transfer-requests'] });
  };

  const transition = useMutation({
    // 무엇을 눌렀는지 확인 문구에 쓰려면 기억해 둬야 한다.
    // 성공한 시점에는 이미 상태가 바뀌어 있어서 다시 만들 수 없다.
    onMutate: (toStatus: OrderStatus) => ({
      label: actionLabel(toStatus, detail.data?.status ?? 'REQUESTED'),
    }),
    mutationFn: async (toStatus: OrderStatus) => {
      const { data } = await api.post(`/work-orders/${requestId}/transitions`, {
        toStatus,
        reason: NEEDS_REASON.includes(toStatus) ? reason.trim() : null,
        scheduledAt: NEEDS_SCHEDULE.includes(toStatus) ? new Date(scheduledAt).toISOString() : null,
        version: detail.data?.version,
      });
      return data;
    },
    onSuccess: (_data, _toStatus, context) => {
      setPending(null);
      setReason('');
      setScheduledAt('');
      setError(null);
      refreshAll();
      // 폰에서는 누른 버튼이 화면 아래에 있고 상태 뱃지는 맨 위에 있다.
      // 이동 중에 한 손으로 누르면 바뀐 것을 못 보고 한 번 더 누르게 된다.
      toast.show(`${context.label} 처리했습니다`, { tone: 'success' });
    },
    // 서버가 준 문장을 그대로 띄운다. "다른 사용자가 먼저 처리했습니다" 같은 것들이다.
    onError: (e) => setError(messageOf(e, '처리에 실패했습니다.')),
  });

  const sendMessage = useMutation({
    mutationFn: async () => {
      await api.post(`/work-orders/${requestId}/messages`, { content: draft.trim() });
    },
    onSuccess: () => {
      setDraft('');
      void queryClient.invalidateQueries({ queryKey: ['transfer-request', requestId, 'messages'] });
    },
    onError: (e) => setError(messageOf(e, '전송에 실패했습니다.')),
  });

  if (detail.isPending) return <DetailSkeleton />;
  if (detail.isError) {
    return <LoadFailed error={detail.error} onRetry={() => void detail.refetch()} />;
  }

  const d = detail.data;
  const needsInput =
    pending &&
    (NEEDS_REASON.includes(pending) ||
      NEEDS_SCHEDULE.includes(pending) ||
      NEEDS_CONFIRM.includes(pending));
  const canSubmit =
    pending &&
    (!NEEDS_REASON.includes(pending) || reason.trim().length > 0) &&
    (!NEEDS_SCHEDULE.includes(pending) || scheduledAt.length > 0);

  return (
    <div className="mx-auto max-w-3xl pb-28">
      <header className="sticky top-0 z-10 flex items-center gap-2 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <button type="button" onClick={() => navigate(-1)} className="text-slate-500">
          ←
        </button>
        <span className="font-mono text-sm text-slate-600">{d.requestNo}</span>
        <PriorityBadge priority={d.priority} />
        <StatusBadge status={d.status} />
      </header>

      {d.checklistWarnings.length > 0 && (
        <section className="mx-3 rounded-xl bg-red-50 p-3.5 ring-1 ring-red-300">
          {d.checklistWarnings.map((w) => (
            <p key={w.alertType} className="text-sm font-semibold text-red-800">
              ⚠ {w.message}
            </p>
          ))}
          <div className="mt-2 flex flex-wrap gap-1">
            {d.alerts.map((a) => (
              <AlertBadge key={a.id} type={a.alertType} severity={a.severity} />
            ))}
          </div>
        </section>
      )}

      <section className="mx-3 mt-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        <div className="flex items-baseline gap-2">
          <span className="font-bold text-slate-900">
            {d.encounter.roomNo}-{d.encounter.bedNo}
          </span>
          <span className="font-semibold text-slate-800">{d.patient.name}</span>
          <span className="text-sm text-slate-500">
            {d.patient.sex}/{d.patient.age}
          </span>
          {!d.encounter.isMobile && (
            <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">거동 불가</span>
          )}
        </div>
        <dl className="mt-3 grid grid-cols-2 gap-y-2 text-sm">
          <dt className="text-slate-500">검사</dt>
          <dd className="text-slate-800">{d.serviceItem.name}</dd>
          <dt className="text-slate-500">요청한 곳</dt>
          <dd className="text-slate-800">
            {d.fromDepartment.name} · {d.requestedBy.name}
          </dd>
          <dt className="text-slate-500">검사하는 곳</dt>
          <dd className="text-slate-800">{d.toDepartment.name}</dd>
          {d.scheduledAt && (
            <>
              <dt className="text-slate-500">검사 예정</dt>
              <dd className="font-semibold text-slate-900">{time(d.scheduledAt)}</dd>
            </>
          )}
        </dl>
        {d.serviceItem.prepInstruction && (
          <p className="mt-2 rounded-lg bg-amber-50 px-2.5 py-1.5 text-xs text-amber-900">
            {d.serviceItem.prepInstruction}
          </p>
        )}
        {d.note && <p className="mt-2 text-sm text-slate-600">메모: {d.note}</p>}
        {d.holdReason && (
          <p className="mt-2 text-sm text-amber-800">사유: {d.holdReason}</p>
        )}
      </section>

      <section className="mx-3 mt-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        <h2 className="mb-2 text-sm font-medium text-slate-600">진행 기록</h2>
        <ol className="space-y-2.5">
          {events.data?.map((e) => (
            <li key={e.id} className="flex gap-2.5 text-sm">
              <span className="w-11 shrink-0 tabular-nums text-slate-400">{time(e.occurredAt)}</span>
              <div className="min-w-0">
                <span className="font-medium text-slate-900">{statusLabel(e.toStatus)}</span>
                <span className="ml-2 text-slate-500">
                  {e.actor.name} ({e.actor.departmentName})
                </span>
                {e.reason && <p className="text-slate-600">사유: {e.reason}</p>}
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className="mx-3 mt-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        <h2 className="mb-2 text-sm font-medium text-slate-600">대화</h2>
        <ul className="space-y-2">
          {messages.data?.map((m) => (
            <li key={m.id} className="text-sm">
              <span className="font-medium text-slate-800">{m.sender.name}</span>
              <span className="ml-1.5 text-xs text-slate-400">
                {m.sender.departmentName} · {time(m.createdAt)}
              </span>
              <p className="text-slate-700">{m.content}</p>
            </li>
          ))}
          {messages.data?.length === 0 && <li className="text-sm text-slate-400">아직 대화가 없습니다.</li>}
        </ul>
        <div className="mt-3 flex gap-2">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            maxLength={1000}
            placeholder="메시지 입력"
            className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-base outline-none focus:border-sky-500"
          />
          <button
            type="button"
            disabled={!draft.trim() || sendMessage.isPending}
            onClick={() => sendMessage.mutate()}
            className="rounded-lg bg-slate-700 px-4 text-sm font-semibold text-white disabled:bg-slate-300"
          >
            전송
          </button>
        </div>
      </section>

      {error && (
        <p className="mx-3 mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          {error}
        </p>
      )}

      {d.availableTransitions.length > 0 && (
        <div className="fixed inset-x-0 bottom-[var(--app-bottom-bar,0px)] z-10 mx-auto max-w-3xl border-t border-slate-200 bg-white p-3">
          {needsInput && (
            <div className="mb-2 space-y-2">
              {NEEDS_CONFIRM.includes(pending) && (
                <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
                  환자가 병동에 도착한 것이 맞습니까? 확정하면 이 요청은 끝나고
                  되돌릴 수 없습니다.
                </p>
              )}
              {NEEDS_SCHEDULE.includes(pending) && (
                <input
                  type="datetime-local"
                  value={scheduledAt}
                  onChange={(e) => setScheduledAt(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-base"
                />
              )}
              {NEEDS_REASON.includes(pending) && (
                <input
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="사유 (필수)"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-base"
                />
              )}
              <div className="flex gap-2">
                {/*
                  "취소" 라고 쓰면 안 된다. 이 앱에서 취소는 요청 취소라는 상태 이름이다.
                  요청을 취소하려고 사유를 적는 중이면 "취소" 옆에 "요청 취소 확정" 이
                  나란히 놓여 어느 쪽이 무엇인지 알 수 없게 된다.
                */}
                <button
                  type="button"
                  onClick={() => setPending(null)}
                  className="flex-1 rounded-lg bg-slate-100 py-2.5 text-sm font-semibold text-slate-600"
                >
                  그만두기
                </button>
                <button
                  type="button"
                  disabled={!canSubmit || transition.isPending}
                  onClick={() => transition.mutate(pending)}
                  className="flex-1 rounded-lg bg-sky-600 py-2.5 text-sm font-bold text-white disabled:bg-slate-300"
                >
                  {actionLabel(pending, d.status)} 확정
                </button>
              </div>
            </div>
          )}

          {!needsInput && (
            /* 버튼 목록은 서버가 내려준 availableTransitions 그대로다.
               전이 규칙을 프론트에 다시 구현하지 않는다. */
            <div className="flex flex-wrap gap-2">
              {d.availableTransitions.map((status) => (
                <button
                  key={status}
                  type="button"
                  disabled={transition.isPending}
                  onClick={() => {
                    setError(null);
                    if (
                      NEEDS_REASON.includes(status) ||
                      NEEDS_SCHEDULE.includes(status) ||
                      NEEDS_CONFIRM.includes(status)
                    ) {
                      setPending(status);
                    } else {
                      transition.mutate(status);
                    }
                  }}
                  className={`flex-1 rounded-lg py-3 text-sm font-bold ${
                    status === 'CANCELLED'
                      ? 'bg-slate-100 text-slate-600'
                      : status === 'ON_HOLD'
                        ? 'bg-amber-100 text-amber-900'
                        : 'bg-sky-600 text-white'
                  }`}
                >
                  {actionLabel(status, d.status)}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

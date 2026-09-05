import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import type { Message, TransferDetail, TransferEvent, TransferStatus } from '@/shared/api/types';
import { AlertBadge, PriorityBadge, StatusBadge, actionLabel, statusLabel } from '@/shared/ui/badges';

/** 전이마다 무엇을 더 받아야 하는지. 서버 규칙과 짝을 이룬다. */
const NEEDS_REASON: TransferStatus[] = ['ON_HOLD', 'CANCELLED'];
const NEEDS_SCHEDULE: TransferStatus[] = ['ACCEPTED'];

const time = (iso: string) =>
  new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });

/** W-05 / E-02 요청 상세. 병동과 검사실이 같은 화면을 쓴다. */
export default function TransferDetailPage() {
  const { id } = useParams();
  const requestId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [pending, setPending] = useState<TransferStatus | null>(null);
  const [reason, setReason] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [draft, setDraft] = useState('');
  const [error, setError] = useState<string | null>(null);

  const detail = useQuery({
    queryKey: ['transfer-request', requestId],
    queryFn: async () => (await api.get<TransferDetail>(`/transfer-requests/${requestId}`)).data,
    // 실시간 알림이 주 경로다. 폴링은 알림을 놓쳤을 때를 위한 보조 장치로만 남긴다.
    refetchInterval: 60_000,
  });

  const events = useQuery({
    queryKey: ['transfer-request', requestId, 'events'],
    queryFn: async () => (await api.get<TransferEvent[]>(`/transfer-requests/${requestId}/events`)).data,
  });

  const messages = useQuery({
    queryKey: ['transfer-request', requestId, 'messages'],
    queryFn: async () => (await api.get<Message[]>(`/transfer-requests/${requestId}/messages`)).data,
    // 실시간 알림이 주 경로다. 폴링은 알림을 놓쳤을 때를 위한 보조 장치로만 남긴다.
    refetchInterval: 60_000,
  });

  const refreshAll = () => {
    void queryClient.invalidateQueries({ queryKey: ['transfer-request', requestId] });
    void queryClient.invalidateQueries({ queryKey: ['transfer-requests'] });
  };

  const transition = useMutation({
    mutationFn: async (toStatus: TransferStatus) => {
      const { data } = await api.post(`/transfer-requests/${requestId}/transitions`, {
        toStatus,
        reason: NEEDS_REASON.includes(toStatus) ? reason.trim() : null,
        scheduledAt: NEEDS_SCHEDULE.includes(toStatus) ? new Date(scheduledAt).toISOString() : null,
        version: detail.data?.version,
      });
      return data;
    },
    onSuccess: () => {
      setPending(null);
      setReason('');
      setScheduledAt('');
      setError(null);
      refreshAll();
    },
    // 서버가 준 문장을 그대로 띄운다. "다른 사용자가 먼저 처리했습니다" 같은 것들이다.
    onError: (e) => setError(messageOf(e, '처리에 실패했습니다.')),
  });

  const sendMessage = useMutation({
    mutationFn: async () => {
      await api.post(`/transfer-requests/${requestId}/messages`, { content: draft.trim() });
    },
    onSuccess: () => {
      setDraft('');
      void queryClient.invalidateQueries({ queryKey: ['transfer-request', requestId, 'messages'] });
    },
    onError: (e) => setError(messageOf(e, '전송에 실패했습니다.')),
  });

  if (detail.isPending) return <p className="p-6 text-sm text-slate-500">불러오는 중…</p>;
  if (detail.isError) {
    return <p className="p-6 text-sm text-red-600">{messageOf(detail.error)}</p>;
  }

  const d = detail.data;
  const needsInput = pending && (NEEDS_REASON.includes(pending) || NEEDS_SCHEDULE.includes(pending));
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
          <dd className="text-slate-800">{d.examType.name}</dd>
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
        {d.examType.prepInstruction && (
          <p className="mt-2 rounded-lg bg-amber-50 px-2.5 py-1.5 text-xs text-amber-900">
            {d.examType.prepInstruction}
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
                <button
                  type="button"
                  onClick={() => setPending(null)}
                  className="flex-1 rounded-lg bg-slate-100 py-2.5 text-sm font-semibold text-slate-600"
                >
                  취소
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
                    if (NEEDS_REASON.includes(status) || NEEDS_SCHEDULE.includes(status)) {
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

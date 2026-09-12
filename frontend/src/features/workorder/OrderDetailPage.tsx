import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import type {
  Message, OrderDetail, OrderEvent, OrderStatus, TransitionOption,
} from '@/shared/api/types';
import { AlertBadge, PriorityBadge, StatusBadge, statusLabel } from '@/shared/ui/badges';
import LoadFailed from '@/shared/ui/LoadFailed';
import { useToast } from '@/shared/ui/toast';
import { DetailSkeleton } from '@/shared/ui/Skeleton';
import { useChecklist, useSubject } from '@/shared/api/phi';

/*
 * 무엇을 더 받아야 하는지는 서버가 버튼마다 알려준다(reasonRequired / scheduleRequired).
 * 예전에는 여기 표를 따로 두었는데, 그 표는 이송 하나에만 맞았다.
 * 업무 종류마다 요구하는 것이 달라서(검체는 예정시각을 받지 않고, 장비 수리는
 * 부품대기에 사유가 필요하다) 화면이 표를 들고 있으면 종류를 더할 때마다 어긋난다.
 */

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

/** 이 버튼을 누르기 전에 무언가를 더 받아야 하는가 */
const needsInputFor = (option: TransitionOption) =>
  option.reasonRequired || option.scheduleRequired || NEEDS_CONFIRM.includes(option.status);

const time = (iso: string) =>
  new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });

/** W-05 / E-02 요청 상세. 병동과 검사실이 같은 화면을 쓴다. */
export default function OrderDetailPage() {
  const { id } = useParams();
  const requestId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();

  const [pending, setPending] = useState<TransitionOption | null>(null);
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
    onMutate: (option: TransitionOption) => ({ label: option.actionLabel }),
    mutationFn: async (option: TransitionOption) => {
      const { data } = await api.post(`/work-orders/${requestId}/transitions`, {
        toStatus: option.status,
        reason: option.reasonRequired ? reason.trim() : null,
        scheduledAt: option.scheduleRequired ? new Date(scheduledAt).toISOString() : null,
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

  // 이름·진단명·주의사항은 업무 응답에 없다. 가명으로 원내에 따로 묻는다.
  // 훅은 조기 반환보다 위에 있어야 한다. 아래에 두면 로딩이 끝나는 순간
  // 훅 순서가 달라져 React 가 상태를 잘못 물려준다.
  const subjectRef = detail.data?.episode?.subjectRef;
  const { subject, unavailable: subjectDown } = useSubject(subjectRef);
  const checklist = useChecklist(subjectRef, detail.data?.serviceItem.requiredAlerts ?? []);
  const phiUnavailable = subjectDown || checklist.unavailable;

  if (detail.isPending) return <DetailSkeleton />;
  if (detail.isError) {
    return <LoadFailed error={detail.error} onRetry={() => void detail.refetch()} />;
  }

  const d = detail.data;
  const needsInput = pending != null && needsInputFor(pending);
  const canSubmit =
    pending != null &&
    (!pending.reasonRequired || reason.trim().length > 0) &&
    (!pending.scheduleRequired || scheduledAt.length > 0);

  return (
    <div className="mx-auto max-w-3xl pb-28">
      <header className="sticky top-0 z-10 flex items-center gap-2 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <button type="button" onClick={() => navigate(-1)} className="text-slate-500">
          ←
        </button>
        <span className="font-mono text-sm text-slate-600">{d.requestNo}</span>
        <PriorityBadge priority={d.priority} />
        <StatusBadge status={d.status} label={d.statusLabel} />
      </header>

      {/*
        경고와 주의사항은 업무 응답에 없다. 진료정보라 원내에서 따로 받는다.
        원내에 닿지 못하면 경고를 감추는 대신 못 받았다고 말한다.
        "경고가 없다" 와 "경고를 못 받았다" 를 같게 보여주면, 금기 환자를
        그냥 검사실로 보내게 된다. 이 화면에서 제일 위험한 실수다.
      */}
      {phiUnavailable && d.episode && (
        <section className="mx-3 rounded-xl bg-amber-50 p-3.5 ring-1 ring-amber-300">
          <p className="text-sm font-semibold text-amber-900">
            ⚠ 주의사항을 확인하지 못했습니다
          </p>
          <p className="mt-1 text-sm text-amber-800">
            환자 정보는 원내망에서만 조회됩니다. 확인 항목이 있는지 알 수 없으니
            검사 전에 병동에 확인해 주세요.
          </p>
        </section>
      )}

      {checklist.warnings.length > 0 && (
        <section className="mx-3 rounded-xl bg-red-50 p-3.5 ring-1 ring-red-300">
          {checklist.warnings.map((w) => (
            <p key={w.alertType} className="text-sm font-semibold text-red-800">
              ⚠ {w.message}
            </p>
          ))}
          <div className="mt-2 flex flex-wrap gap-1">
            {(subject?.alerts ?? []).map((a) => (
              <AlertBadge key={a.id} type={a.alertType} severity={a.severity} />
            ))}
          </div>
        </section>
      )}

      <section className="mx-3 mt-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        {/* 장비 수리처럼 대상 환자가 없는 업무가 있다. 그때는 이 줄을 통째로 접는다. */}
        {d.episode && (
          <div className="flex items-baseline gap-2">
            <span className="font-bold text-slate-900">
              {d.episode.roomNo}-{d.episode.bedNo}
            </span>
            {subject ? (
              <>
                <span className="font-semibold text-slate-800">{subject.name}</span>
                <span className="text-sm text-slate-500">
                  {subject.sex}/{subject.age}
                </span>
                {!subject.mobile && (
                  <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
                    거동 불가
                  </span>
                )}
              </>
            ) : (
              <span className={`text-sm ${phiUnavailable ? 'text-amber-700' : 'text-slate-400'}`}>
                {phiUnavailable ? '원내망에서만 조회됩니다' : '불러오는 중…'}
              </span>
            )}
          </div>
        )}
        <dl className="mt-3 grid grid-cols-2 gap-y-2 text-sm">
          <dt className="text-slate-500">업무</dt>
          <dd className="text-slate-800">
            {d.serviceItem.name}
            <span className="ml-1.5 text-xs text-slate-500">{d.orderTypeLabel}</span>
          </dd>
          <dt className="text-slate-500">요청한 곳</dt>
          <dd className="text-slate-800">
            {d.fromDepartment.name} · {d.requestedBy.name}
          </dd>
          <dt className="text-slate-500">수행하는 곳</dt>
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
              {NEEDS_CONFIRM.includes(pending.status) && (
                <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
                  환자가 병동에 도착한 것이 맞습니까? 확정하면 이 요청은 끝나고
                  되돌릴 수 없습니다.
                </p>
              )}
              {pending.scheduleRequired && (
                <input
                  type="datetime-local"
                  value={scheduledAt}
                  onChange={(e) => setScheduledAt(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-base"
                />
              )}
              {pending.reasonRequired && (
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
                  {pending.actionLabel} 확정
                </button>
              </div>
            </div>
          )}

          {!needsInput && (
            /* 버튼 목록은 서버가 내려준 availableTransitions 그대로다.
               전이 규칙을 프론트에 다시 구현하지 않는다. */
            <div className="flex flex-wrap gap-2">
              {d.availableTransitions.map((option) => (
                <button
                  key={option.status}
                  type="button"
                  disabled={transition.isPending}
                  onClick={() => {
                    setError(null);
                    if (needsInputFor(option)) {
                      setPending(option);
                    } else {
                      transition.mutate(option);
                    }
                  }}
                  className={`flex-1 rounded-lg py-3 text-sm font-bold ${
                    option.status === 'CANCELLED'
                      ? 'bg-slate-100 text-slate-600'
                      : option.status === 'ON_HOLD'
                        ? 'bg-amber-100 text-amber-900'
                        : 'bg-sky-600 text-white'
                  }`}
                >
                  {option.actionLabel}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

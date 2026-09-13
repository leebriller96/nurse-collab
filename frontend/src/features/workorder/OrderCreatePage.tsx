import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import type {
  OrderPriority, OrderType, ServiceItem,
} from '@/shared/api/types';
import { AlertBadge } from '@/shared/ui/badges';
import { useSubject } from '@/shared/api/phi';

const PRIORITIES: { value: OrderPriority; label: string; style: string }[] = [
  { value: 'ROUTINE', label: '일반', style: 'bg-slate-600' },
  { value: 'URGENT', label: '긴급', style: 'bg-amber-500' },
  { value: 'EMERGENCY', label: '응급', style: 'bg-red-600' },
];

/**
 * W-03 업무 요청 등록.
 * 종류 → 항목 → 우선순위 → 등록. 몇 번 안에 끝나야 한다.
 * 희망시각과 메모는 접어 두고, 필요한 사람만 펼치게 한다.
 *
 * 종류 탭은 서버가 내려준 항목에서 뽑는다. 목록을 손으로 적어 두면
 * 새 종류를 쓰기 시작한 날 그 탭만 없어서 아무도 요청을 못 넣는다.
 */
export default function OrderCreatePage() {
  const [params] = useSearchParams();
  const subjectRef = params.get('subjectRef');
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [orderType, setOrderType] = useState<OrderType | null>(null);
  const [serviceItemId, setServiceItemId] = useState<number | null>(null);
  const [priority, setPriority] = useState<OrderPriority>('ROUTINE');
  const [note, setNote] = useState('');
  const [showMore, setShowMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 대상은 가명으로 들어온다. 이름과 주의사항은 원내에서 받아 채운다.
  const { subject, unavailable: phiDown } = useSubject(subjectRef);

  const { data: examTypes } = useQuery({
    queryKey: ['service-items'],
    queryFn: async () => (await api.get<ServiceItem[]>('/service-items')).data,
    staleTime: 5 * 60_000,
  });

  const selected = examTypes?.find((e) => e.id === serviceItemId);

  // 있는 종류만 탭으로 띄운다. 항목이 하나도 없는 종류를 보여 주면 빈 목록만 나온다.
  const types: OrderType[] = [];
  for (const item of examTypes ?? []) {
    if (!types.includes(item.orderType)) types.push(item.orderType);
  }
  const activeType = orderType ?? types[0] ?? null;
  const visibleItems = (examTypes ?? []).filter((e) => e.orderType === activeType);
  const typeLabelOf = (t: OrderType) =>
    (examTypes ?? []).find((e) => e.orderType === t)?.orderTypeLabel ?? t;

  // 환자가 필요한 업무인데 어느 환자인지 모르면 보낼 수 없다.
  // 병동 보드에서 환자를 고르고 들어와야 가명이 붙는다.
  const needsPatient = selected?.patientRequired ?? false;
  const missingPatient = needsPatient && !subjectRef;

  // 고른 검사의 필수 확인 항목과 이 환자의 주의사항이 겹치면 등록 전에 알려준다
  const warnings =
    selected && subject
      ? subject.alerts.filter((a) => selected.requiredAlerts.includes(a.alertType))
      : [];

  const create = useMutation({
    mutationFn: async () => {
      const { data } = await api.post<{ id: number }>('/work-orders', {
        // 업무 쪽에 넘기는 것은 가명뿐이다. 재원 id 도 이름도 넘기지 않는다.
        // 환자를 붙일 수 없는 업무에 붙이면 서버가 ORD-007 로 막는다.
        // 환자를 고르고 들어왔다가 장비 수리로 바꾸는 일이 실제로 생긴다.
        subjectRef: needsPatient ? subjectRef : null,
        serviceItemId,
        priority,
        note: note.trim() || null,
      });
      return data;
    },
    onSuccess: (data) => {
      void queryClient.invalidateQueries({ queryKey: ['care-episodes'] });
      void queryClient.invalidateQueries({ queryKey: ['transfer-requests'] });
      navigate(`/ward/requests/${data.id}`, { replace: true });
    },
    onError: (e) => setError(messageOf(e, '요청 등록에 실패했습니다.')),
  });

  return (
    <div className="pb-28">
      <header className="sticky top-0 z-10 flex items-center gap-2 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <button type="button" onClick={() => navigate(-1)} className="text-slate-500">
          ←
        </button>
        <h1 className="text-lg font-bold text-slate-900">업무 요청</h1>
      </header>

      {/* 환자가 필요 없는 업무를 고르면 환자 카드를 접는다. 요청과 상관없는 정보다. */}
      {subjectRef && (activeType === null || needsPatient || serviceItemId === null) && (
        <section className="mx-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
          <div className="flex items-baseline gap-2">
            {subject ? (
              <>
                <span className="font-semibold text-slate-800">{subject.name}</span>
                <span className="text-sm text-slate-500">
                  {subject.sex}/{subject.age}
                </span>
              </>
            ) : (
              <span className={`text-sm ${phiDown ? 'text-amber-700' : 'text-slate-400'}`}>
                {phiDown ? '원내망에서만 조회됩니다' : '불러오는 중…'}
              </span>
            )}
          </div>
          {subject?.diagnosis && <p className="mt-1 text-sm text-slate-600">{subject.diagnosis}</p>}
          {phiDown && (
            <p className="mt-1 text-sm text-amber-800">
              주의사항을 확인하지 못했습니다. 요청 전에 병동에 확인해 주세요.
            </p>
          )}
          {subject && subject.alerts.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1">
              {subject.alerts.map((a) => (
                <AlertBadge key={a.id} type={a.alertType} severity={a.severity} />
              ))}
            </div>
          )}
        </section>
      )}

      {types.length > 1 && (
        <section className="mt-4 px-3">
          <div className="flex gap-1.5 overflow-x-auto">
            {types.map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => { setOrderType(t); setServiceItemId(null); }}
                className={`shrink-0 rounded-full px-4 py-1.5 text-sm font-semibold ring-1 ${
                  activeType === t
                    ? 'bg-slate-900 text-white ring-transparent'
                    : 'bg-white text-slate-600 ring-slate-200'
                }`}
              >
                {typeLabelOf(t)}
              </button>
            ))}
          </div>
        </section>
      )}

      <section className="mt-4 px-3">
        <h2 className="mb-2 px-1 text-sm font-medium text-slate-600">업무 선택</h2>
        <div className="space-y-2">
          {visibleItems.map((exam) => (
            <button
              key={exam.id}
              type="button"
              onClick={() => setServiceItemId(exam.id)}
              className={`w-full rounded-xl p-3.5 text-left ring-1 transition ${
                serviceItemId === exam.id
                  ? 'bg-sky-50 ring-2 ring-sky-500'
                  : 'bg-white ring-slate-200'
              }`}
            >
              <div className="flex items-baseline justify-between">
                <span className="font-semibold text-slate-900">{exam.name}</span>
                <span className="text-xs text-slate-500">{exam.department.name}</span>
              </div>
              <p className="mt-0.5 text-xs text-slate-500">
                약 {exam.defaultDuration}분{exam.prepInstruction ? ` · ${exam.prepInstruction}` : ''}
              </p>
            </button>
          ))}
        </div>
      </section>

      {warnings.length > 0 && (
        <section className="mx-3 mt-4 rounded-xl bg-red-50 p-3.5 ring-1 ring-red-200">
          <p className="text-sm font-semibold text-red-800">이 업무 전에 확인이 필요합니다</p>
          <ul className="mt-1.5 space-y-1">
            {warnings.map((w) => (
              <li key={w.id} className="text-sm text-red-700">
                · {w.label} — {w.content}
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs text-red-600">
            요청은 그대로 보낼 수 있습니다. 수행 파트에서도 같은 안내를 봅니다.
          </p>
        </section>
      )}

      <section className="mt-4 px-3">
        <h2 className="mb-2 px-1 text-sm font-medium text-slate-600">우선순위</h2>
        <div className="grid grid-cols-3 gap-2">
          {PRIORITIES.map((p) => (
            <button
              key={p.value}
              type="button"
              onClick={() => setPriority(p.value)}
              className={`rounded-xl py-3 text-sm font-bold ring-1 transition ${
                priority === p.value
                  ? `${p.style} text-white ring-transparent`
                  : 'bg-white text-slate-600 ring-slate-200'
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>
      </section>

      <section className="mt-4 px-3">
        <button
          type="button"
          onClick={() => setShowMore((v) => !v)}
          className="px-1 text-sm text-slate-500"
        >
          {showMore ? '메모 접기' : '메모 추가'}
        </button>
        {showMore && (
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={500}
            rows={3}
            placeholder="휠체어 이송 필요, 보호자 동반 등"
            className="mt-2 w-full rounded-xl border border-slate-300 p-3 text-base outline-none focus:border-sky-500"
          />
        )}
      </section>

      {error && (
        <p className="mx-3 mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          {error}
        </p>
      )}

      <div className="fixed inset-x-0 bottom-[var(--app-bottom-bar,0px)] z-10 mx-auto max-w-md border-t border-slate-200 bg-white p-3">
        {missingPatient && (
          <p className="mb-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
            이 업무는 대상 환자가 필요합니다. 병동 보드에서 환자를 먼저 골라 주세요.
          </p>
        )}
        <button
          type="button"
          disabled={!serviceItemId || missingPatient || create.isPending}
          onClick={() => create.mutate()}
          className="w-full rounded-xl bg-sky-600 py-3.5 text-base font-bold text-white disabled:bg-slate-300"
        >
          {create.isPending ? '등록 중…' : '요청 등록'}
        </button>
      </div>
    </div>
  );
}

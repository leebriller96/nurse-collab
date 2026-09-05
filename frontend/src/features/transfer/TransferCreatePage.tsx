import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import type { EncounterFullView, ExamType, TransferPriority } from '@/shared/api/types';
import { AlertBadge } from '@/shared/ui/badges';

const PRIORITIES: { value: TransferPriority; label: string; style: string }[] = [
  { value: 'ROUTINE', label: '일반', style: 'bg-slate-600' },
  { value: 'URGENT', label: '긴급', style: 'bg-amber-500' },
  { value: 'EMERGENCY', label: '응급', style: 'bg-red-600' },
];

/**
 * W-03 이송 요청 등록.
 * 검사 선택 → 우선순위 → 등록. 3탭 안에 끝나야 한다.
 * 희망시각과 메모는 접어 두고, 필요한 사람만 펼치게 한다.
 */
export default function TransferCreatePage() {
  const [params] = useSearchParams();
  const encounterId = Number(params.get('encounterId'));
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [examTypeId, setExamTypeId] = useState<number | null>(null);
  const [priority, setPriority] = useState<TransferPriority>('ROUTINE');
  const [note, setNote] = useState('');
  const [showMore, setShowMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { data: encounter } = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn: async () => (await api.get<EncounterFullView>(`/encounters/${encounterId}`)).data,
    enabled: Number.isFinite(encounterId) && encounterId > 0,
  });

  const { data: examTypes } = useQuery({
    queryKey: ['exam-types'],
    queryFn: async () => (await api.get<ExamType[]>('/exam-types')).data,
    staleTime: 5 * 60_000,
  });

  const selected = examTypes?.find((e) => e.id === examTypeId);

  // 고른 검사의 필수 확인 항목과 이 환자의 주의사항이 겹치면 등록 전에 알려준다
  const warnings =
    selected && encounter
      ? encounter.alerts.filter((a) => selected.requiredAlerts.includes(a.alertType))
      : [];

  const create = useMutation({
    mutationFn: async () => {
      const { data } = await api.post<{ id: number }>('/transfer-requests', {
        encounterId,
        examTypeId,
        priority,
        note: note.trim() || null,
      });
      return data;
    },
    onSuccess: (data) => {
      void queryClient.invalidateQueries({ queryKey: ['encounters'] });
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
        <h1 className="text-lg font-bold text-slate-900">이송 요청</h1>
      </header>

      {encounter && (
        <section className="mx-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
          <div className="flex items-baseline gap-2">
            <span className="font-bold text-slate-900">
              {encounter.roomNo}-{encounter.bedNo}
            </span>
            <span className="font-semibold text-slate-800">{encounter.patient.name}</span>
            <span className="text-sm text-slate-500">
              {encounter.patient.sex}/{encounter.patient.age}
            </span>
          </div>
          {encounter.diagnosis && <p className="mt-1 text-sm text-slate-600">{encounter.diagnosis}</p>}
          {encounter.alerts.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1">
              {encounter.alerts.map((a) => (
                <AlertBadge key={a.id} type={a.alertType} severity={a.severity} />
              ))}
            </div>
          )}
        </section>
      )}

      <section className="mt-4 px-3">
        <h2 className="mb-2 px-1 text-sm font-medium text-slate-600">검사 선택</h2>
        <div className="space-y-2">
          {examTypes?.map((exam) => (
            <button
              key={exam.id}
              type="button"
              onClick={() => setExamTypeId(exam.id)}
              className={`w-full rounded-xl p-3.5 text-left ring-1 transition ${
                examTypeId === exam.id
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
          <p className="text-sm font-semibold text-red-800">이 검사의 확인 항목에 걸립니다</p>
          <ul className="mt-1.5 space-y-1">
            {warnings.map((w) => (
              <li key={w.id} className="text-sm text-red-700">
                · {w.label} — {w.content}
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs text-red-600">
            등록은 가능합니다. 검사실에서도 같은 경고를 보게 됩니다.
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
        <button
          type="button"
          disabled={!examTypeId || create.isPending}
          onClick={() => create.mutate()}
          className="w-full rounded-xl bg-sky-600 py-3.5 text-base font-bold text-white disabled:bg-slate-300"
        >
          {create.isPending ? '등록 중…' : '요청 등록'}
        </button>
      </div>
    </div>
  );
}

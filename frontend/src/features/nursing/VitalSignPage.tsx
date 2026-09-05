import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import type { EncounterFullView, PageResponse, VitalSign } from '@/shared/api/types';

/** 입력 칸 정의를 한곳에 모은다. 칸이 늘거나 순서가 바뀌어도 여기만 고치면 된다. */
const FIELDS = [
  { key: 'temperature', label: '체온', unit: '℃', mode: 'decimal', step: '0.1' },
  { key: 'pulse', label: '맥박', unit: '회/분', mode: 'numeric' },
  { key: 'respiration', label: '호흡', unit: '회/분', mode: 'numeric' },
  { key: 'sbp', label: '수축기 혈압', unit: 'mmHg', mode: 'numeric' },
  { key: 'dbp', label: '이완기 혈압', unit: 'mmHg', mode: 'numeric' },
  { key: 'spo2', label: '산소포화도', unit: '%', mode: 'numeric' },
  { key: 'painScore', label: '통증', unit: '0~10', mode: 'numeric' },
] as const;

type FieldKey = (typeof FIELDS)[number]['key'];

const time = (iso: string) =>
  new Date(iso).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });

/** 입력창에 넣을 현재 시각. 초는 버린다. */
function nowForInput() {
  const now = new Date();
  now.setSeconds(0, 0);
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

/** W-06 활력징후. 이동 중에 한 손으로 쓰는 화면이라 숫자 키패드가 바로 올라와야 한다. */
export default function VitalSignPage() {
  const { id } = useParams();
  const encounterId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [measuredAt, setMeasuredAt] = useState(nowForInput);
  const [values, setValues] = useState<Record<FieldKey, string>>({
    temperature: '', pulse: '', respiration: '', sbp: '', dbp: '', spo2: '', painScore: '',
  });
  const [error, setError] = useState<string | null>(null);

  const encounter = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn: async () => (await api.get<EncounterFullView>(`/encounters/${encounterId}`)).data,
  });

  const history = useQuery({
    queryKey: ['vital-signs', encounterId],
    queryFn: async () =>
      (await api.get<PageResponse<VitalSign>>(`/encounters/${encounterId}/vital-signs`,
        { params: { page: 0, size: 20 } })).data,
  });

  const save = useMutation({
    mutationFn: async () => {
      const body: Record<string, unknown> = { measuredAt: new Date(measuredAt).toISOString() };
      for (const field of FIELDS) {
        const raw = values[field.key].trim();
        body[field.key] = raw === '' ? null : Number(raw);
      }
      await api.post(`/encounters/${encounterId}/vital-signs`, body);
    },
    onSuccess: () => {
      setValues({ temperature: '', pulse: '', respiration: '', sbp: '', dbp: '', spo2: '', painScore: '' });
      setMeasuredAt(nowForInput());
      setError(null);
      void queryClient.invalidateQueries({ queryKey: ['vital-signs', encounterId] });
    },
    onError: (e) => setError(messageOf(e, '저장하지 못했습니다.')),
  });

  const anyFilled = FIELDS.some((f) => values[f.key].trim() !== '');

  return (
    <div className="pb-28">
      <header className="sticky top-0 z-10 flex items-center gap-2 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <button type="button" onClick={() => navigate(-1)} className="text-slate-500">
          ←
        </button>
        <h1 className="text-lg font-bold text-slate-900">활력징후</h1>
        {encounter.data && (
          <span className="text-sm text-slate-500">
            {encounter.data.roomNo}-{encounter.data.bedNo} {encounter.data.patient.name}
          </span>
        )}
      </header>

      <section className="mx-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        <label className="block text-sm font-medium text-slate-700" htmlFor="measuredAt">
          측정 시각
        </label>
        <input
          id="measuredAt"
          type="datetime-local"
          value={measuredAt}
          onChange={(e) => setMeasuredAt(e.target.value)}
          className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-base"
        />

        <div className="mt-4 space-y-3">
          {FIELDS.map((field) => (
            <div key={field.key} className="flex items-center gap-3">
              <label className="w-24 shrink-0 text-sm text-slate-700" htmlFor={field.key}>
                {field.label}
              </label>
              <input
                id={field.key}
                inputMode={field.mode}
                step={'step' in field ? field.step : undefined}
                value={values[field.key]}
                onChange={(e) => setValues((v) => ({ ...v, [field.key]: e.target.value }))}
                className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2.5 text-right text-lg tabular-nums outline-none focus:border-sky-500"
              />
              <span className="w-14 shrink-0 text-sm text-slate-400">{field.unit}</span>
            </div>
          ))}
        </div>
      </section>

      {error && (
        <p className="mx-3 mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          {error}
        </p>
      )}

      <section className="mx-3 mt-4 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        <h2 className="mb-2 text-sm font-medium text-slate-600">최근 기록</h2>
        {history.data?.content.length === 0 ? (
          <p className="py-4 text-center text-sm text-slate-400">아직 기록이 없습니다.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {history.data?.content.map((v) => (
              <li key={v.id} className="py-2.5 text-sm">
                <div className="flex justify-between text-xs text-slate-400">
                  <span>{time(v.measuredAt)}</span>
                  <span>{v.recordedBy.name}</span>
                </div>
                <div className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 tabular-nums text-slate-700">
                  {v.temperature != null && <span>체온 {v.temperature}</span>}
                  {v.pulse != null && <span>맥박 {v.pulse}</span>}
                  {v.respiration != null && <span>호흡 {v.respiration}</span>}
                  {v.sbp != null && v.dbp != null && <span>혈압 {v.sbp}/{v.dbp}</span>}
                  {v.spo2 != null && <span>산소 {v.spo2}%</span>}
                  {v.painScore != null && <span>통증 {v.painScore}</span>}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="fixed inset-x-0 bottom-[var(--app-bottom-bar,0px)] z-10 mx-auto max-w-md border-t border-slate-200 bg-white p-3">
        <button
          type="button"
          disabled={!anyFilled || save.isPending}
          onClick={() => save.mutate()}
          className="w-full rounded-xl bg-sky-600 py-3.5 text-base font-bold text-white disabled:bg-slate-300"
        >
          {save.isPending ? '저장 중…' : '저장'}
        </button>
      </div>
    </div>
  );
}

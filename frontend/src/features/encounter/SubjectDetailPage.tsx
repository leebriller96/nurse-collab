import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import { phi, useSubject } from '@/shared/api/phi';
import type { AlertSeverity, AlertType, CareEpisodeDetail } from '@/shared/api/types';
import { AlertBadge, StatusBadge } from '@/shared/ui/badges';
import LoadFailed from '@/shared/ui/LoadFailed';
import { useToast } from '@/shared/ui/toast';
import { DetailSkeleton } from '@/shared/ui/Skeleton';

/** 화면에 보여줄 순서. 자주 쓰는 것을 앞에 둔다. */
const ALERT_TYPES: { value: AlertType; label: string }[] = [
  { value: 'FALL_RISK', label: '낙상 위험' },
  { value: 'ISOLATION', label: '격리' },
  { value: 'NPO', label: '금식' },
  { value: 'DRUG_ALLERGY', label: '약물 알레르기' },
  { value: 'CONTRAST_ALLERGY', label: '조영제 알레르기' },
  { value: 'METAL_IMPLANT', label: '체내 금속물' },
  { value: 'CLAUSTROPHOBIA', label: '폐소공포' },
  { value: 'OXYGEN', label: '산소' },
];

const SEVERITIES: { value: AlertSeverity; label: string; hint: string }[] = [
  { value: 'INFO', label: '참고', hint: '알아두면 좋은 것' },
  { value: 'WARN', label: '주의', hint: '확인이 필요한 것' },
  { value: 'CRITICAL', label: '중대', hint: '넘어가면 위험한 것' },
];

/**
 * 주의사항 남기기.
 *
 * 여기 남긴 것이 업무 항목의 확인 항목과 만나 수행 파트 화면의 경고가 된다.
 * 그래서 위험도를 사람 말로 풀어 뒀다. INFO/WARN/CRITICAL 로는 무엇을 고를지 알 수 없다.
 */
function AlertForm({ subjectRef, onDone }: { subjectRef: string; onDone: () => void }) {
  const toast = useToast();
  const [alertType, setAlertType] = useState<AlertType>('FALL_RISK');
  const [severity, setSeverity] = useState<AlertSeverity>('WARN');
  const [content, setContent] = useState('');
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: async () => {
      await phi.post(`/subjects/${subjectRef}/alerts`, {
        alertType,
        severity,
        content: content.trim() || null,
      });
    },
    onSuccess: () => {
      toast.show('주의사항을 남겼습니다', { body: '수행 파트에서도 함께 보입니다', tone: 'success' });
      onDone();
    },
    onError: (e) => setError(messageOf(e, '남기지 못했습니다.')),
  });

  return (
    <div className="mb-3 rounded-xl bg-slate-50 p-3">
      <div className="flex flex-wrap gap-1.5">
        {ALERT_TYPES.map((t) => (
          <button
            key={t.value}
            type="button"
            onClick={() => setAlertType(t.value)}
            className={`rounded-lg px-2.5 py-1.5 text-sm font-medium ring-1 ${
              alertType === t.value
                ? 'bg-slate-900 text-white ring-transparent'
                : 'bg-white text-slate-600 ring-slate-200'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="mt-2 grid grid-cols-3 gap-1.5">
        {SEVERITIES.map((s) => (
          <button
            key={s.value}
            type="button"
            onClick={() => setSeverity(s.value)}
            className={`rounded-lg px-2 py-1.5 text-sm font-semibold ring-1 ${
              severity === s.value
                ? 'bg-sky-600 text-white ring-transparent'
                : 'bg-white text-slate-600 ring-slate-200'
            }`}
          >
            {s.label}
            <span className="block text-[11px] font-normal opacity-75">{s.hint}</span>
          </button>
        ))}
      </div>

      <input
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="자세히 (선택) — 예: 좌측 고관절 인공관절 2019년 삽입"
        className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-base outline-none focus:border-sky-500"
      />

      {error && (
        <p className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          {error}
        </p>
      )}

      <button
        type="button"
        disabled={save.isPending}
        onClick={() => save.mutate()}
        className="mt-2 w-full rounded-lg bg-sky-600 py-2.5 text-sm font-bold text-white disabled:bg-slate-300"
      >
        남기기
      </button>
    </div>
  );
}

/**
 * W-02 환자 상세. 여기서 바로 업무 요청을 걸 수 있어야 한다.
 *
 * 화면 한 장이 두 곳에서 온다.
 *   - 업무 쪽: 침대와 진행중 요청
 *   - 원내: 이름, 진단명, 거동 여부, 주의사항
 *
 * 원내에 닿지 못하면 침대와 요청은 그대로 보이고 사람만 사라진다.
 * 주의사항은 "없음" 이 아니라 "확인하지 못했다" 로 말한다 —
 * 둘을 같게 보여주면 금기를 모른 채 요청을 걸게 된다.
 */
export default function SubjectDetailPage() {
  const { subjectRef } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [adding, setAdding] = useState(false);

  const episode = useQuery({
    queryKey: ['care-episode', subjectRef],
    queryFn: async () => (await api.get<CareEpisodeDetail>(`/care-episodes/${subjectRef}`)).data,
    enabled: !!subjectRef,
  });

  const { subject, unavailable: phiDown } = useSubject(subjectRef);

  const deactivate = useMutation({
    mutationFn: async (alertId: number) => {
      await phi.patch(`/alerts/${alertId}/deactivate`);
    },
    onSuccess: () => {
      // 지우는 것이 아니라 내리는 것이다. 지난 요청은 이것을 보고 판단했다.
      toast.show('주의사항을 내렸습니다', { body: '지난 기록에는 그대로 남습니다', tone: 'success' });
      void queryClient.invalidateQueries({ queryKey: ['phi', 'subject', subjectRef] });
    },
  });

  if (episode.isPending) return <DetailSkeleton />;
  if (episode.isError) {
    return <LoadFailed error={episode.error} onRetry={() => void episode.refetch()} />;
  }

  const bed = episode.data;

  return (
    <div className="pb-28">
      <header className="sticky top-0 z-10 flex items-center gap-2 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <button type="button" onClick={() => navigate(-1)} className="text-slate-500">
          ←
        </button>
        <h1 className="text-lg font-bold text-slate-900">
          {bed.roomNo}-{bed.bedNo}{' '}
          {subject ? (
            subject.name
          ) : (
            <span className={`text-base font-normal ${phiDown ? 'text-amber-700' : 'text-slate-400'}`}>
              {phiDown ? '원내망에서만 조회됩니다' : '불러오는 중…'}
            </span>
          )}
        </h1>
      </header>

      <section className="mx-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        <dl className="grid grid-cols-3 gap-y-2 text-sm">
          <dt className="text-slate-500">등록번호</dt>
          <dd className="col-span-2 font-mono text-slate-800">{subject?.patientNo ?? '—'}</dd>
          <dt className="text-slate-500">나이/성별</dt>
          <dd className="col-span-2 text-slate-800">
            {subject ? `${subject.age} / ${subject.sex}` : '—'}
          </dd>
          <dt className="text-slate-500">진단명</dt>
          <dd className="col-span-2 text-slate-800">{subject?.diagnosis ?? '—'}</dd>
          <dt className="text-slate-500">거동</dt>
          <dd className="col-span-2 text-slate-800">
            {subject ? (subject.mobile ? '가능' : '불가') : '—'}
          </dd>
        </dl>
      </section>

      {/*
        비어 있어도 구역을 보여준다. 없을 때 감춰 버리면 남길 자리가 없다.
        여기 쌓인 것이 수행 파트 화면의 "이 업무 전에 확인이 필요합니다" 가 된다.
      */}
      <section className="mx-3 mt-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        <div className="mb-2 flex items-baseline justify-between">
          <h2 className="text-sm font-medium text-slate-600">주의사항</h2>
          {subject && (
            <button
              type="button"
              onClick={() => setAdding((v) => !v)}
              className="text-sm font-semibold text-sky-600"
            >
              {adding ? '닫기' : '+ 추가'}
            </button>
          )}
        </div>

        {adding && subject && (
          <AlertForm
            subjectRef={subjectRef!}
            onDone={() => {
              setAdding(false);
              void queryClient.invalidateQueries({ queryKey: ['phi', 'subject', subjectRef] });
            }}
          />
        )}

        {!subject ? (
          <p className={`py-3 text-center text-sm ${phiDown ? 'text-amber-700' : 'text-slate-400'}`}>
            {phiDown ? '원내망에서만 조회됩니다. 없다는 뜻이 아닙니다.' : '불러오는 중…'}
          </p>
        ) : subject.alerts.length === 0 ? (
          <p className="py-3 text-center text-sm text-slate-400">남긴 주의사항이 없습니다.</p>
        ) : (
          <ul className="space-y-1.5">
            {subject.alerts.map((a) => (
              <li key={a.id} className="flex items-start gap-2 text-sm">
                <AlertBadge type={a.alertType} severity={a.severity} />
                <span className="text-slate-700">{a.content}</span>
                <button
                  type="button"
                  onClick={() => deactivate.mutate(a.id)}
                  className="ml-auto shrink-0 text-xs text-slate-400"
                >
                  내리기
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {subject && (
        <section className="mx-3 mt-3 grid grid-cols-2 gap-2">
          <Link
            to={`/ward/subjects/${subjectRef}/vitals`}
            className="rounded-xl bg-white py-3 text-center text-sm font-semibold text-slate-700 shadow-sm ring-1 ring-slate-200"
          >
            활력징후
          </Link>
          <Link
            to={`/ward/subjects/${subjectRef}/notes`}
            className="rounded-xl bg-white py-3 text-center text-sm font-semibold text-slate-700 shadow-sm ring-1 ring-slate-200"
          >
            간호기록
          </Link>
        </section>
      )}

      <section className="mx-3 mt-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        <h2 className="mb-2 text-sm font-medium text-slate-600">진행중 요청</h2>
        {bed.activeRequests.length === 0 ? (
          <p className="text-sm text-slate-400">없습니다.</p>
        ) : (
          <ul className="space-y-2">
            {bed.activeRequests.map((r) => (
              <li key={r.id}>
                <Link to={`/ward/requests/${r.id}`} className="flex items-center gap-2 text-sm">
                  <StatusBadge status={r.status} label={r.statusLabel} />
                  <span className="text-slate-800">{r.itemName}</span>
                  <span className="ml-auto font-mono text-xs text-slate-400">{r.requestNo}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="fixed inset-x-0 bottom-[var(--app-bottom-bar,0px)] z-10 mx-auto max-w-md border-t border-slate-200 bg-white p-3">
        <Link
          to={`/ward/requests/new?subjectRef=${subjectRef}`}
          className="block w-full rounded-xl bg-sky-600 py-3.5 text-center text-base font-bold text-white"
        >
          + 업무 요청
        </Link>
      </div>
    </div>
  );
}

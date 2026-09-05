import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import type { EncounterFullView } from '@/shared/api/types';
import { AlertBadge, StatusBadge } from '@/shared/ui/badges';

/** W-02 환자 상세. 여기서 바로 요청을 걸 수 있어야 한다. */
export default function EncounterDetailPage() {
  const { id } = useParams();
  const encounterId = Number(id);
  const navigate = useNavigate();

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn: async () => (await api.get<EncounterFullView>(`/encounters/${encounterId}`)).data,
  });

  if (isPending) return <p className="p-4 text-sm text-slate-500">불러오는 중…</p>;
  if (isError) return <p className="p-4 text-sm text-red-600">{messageOf(error)}</p>;

  return (
    <div className="pb-28">
      <header className="sticky top-0 z-10 flex items-center gap-2 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <button type="button" onClick={() => navigate(-1)} className="text-slate-500">
          ←
        </button>
        <h1 className="text-lg font-bold text-slate-900">
          {data.roomNo}-{data.bedNo} {data.patient.name}
        </h1>
      </header>

      <section className="mx-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        <dl className="grid grid-cols-3 gap-y-2 text-sm">
          <dt className="text-slate-500">등록번호</dt>
          <dd className="col-span-2 font-mono text-slate-800">{data.patient.patientNo}</dd>
          <dt className="text-slate-500">나이/성별</dt>
          <dd className="col-span-2 text-slate-800">
            {data.patient.age} / {data.patient.sex}
          </dd>
          <dt className="text-slate-500">진단명</dt>
          <dd className="col-span-2 text-slate-800">{data.diagnosis ?? '-'}</dd>
          <dt className="text-slate-500">거동</dt>
          <dd className="col-span-2 text-slate-800">{data.isMobile ? '가능' : '불가'}</dd>
        </dl>
      </section>

      {data.alerts.length > 0 && (
        <section className="mx-3 mt-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
          <h2 className="mb-2 text-sm font-medium text-slate-600">주의사항</h2>
          <ul className="space-y-1.5">
            {data.alerts.map((a) => (
              <li key={a.id} className="flex items-start gap-2 text-sm">
                <AlertBadge type={a.alertType} severity={a.severity} />
                <span className="text-slate-700">{a.content}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="mx-3 mt-3 grid grid-cols-2 gap-2">
        <Link to={`/ward/encounters/${data.encounterId}/vitals`} className="rounded-xl bg-white py-3 text-center text-sm font-semibold text-slate-700 shadow-sm ring-1 ring-slate-200">활력징후</Link>
        <Link to={`/ward/encounters/${data.encounterId}/notes`} className="rounded-xl bg-white py-3 text-center text-sm font-semibold text-slate-700 shadow-sm ring-1 ring-slate-200">간호기록</Link>
      </section>

      <section className="mx-3 mt-3 rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
        <h2 className="mb-2 text-sm font-medium text-slate-600">진행중 요청</h2>
        {data.activeRequests.length === 0 ? (
          <p className="text-sm text-slate-400">없습니다.</p>
        ) : (
          <ul className="space-y-2">
            {data.activeRequests.map((r) => (
              <li key={r.id}>
                <Link to={`/ward/requests/${r.id}`} className="flex items-center gap-2 text-sm">
                  <StatusBadge status={r.status} />
                  <span className="text-slate-800">{r.examName}</span>
                  <span className="ml-auto font-mono text-xs text-slate-400">{r.requestNo}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="fixed inset-x-0 bottom-[var(--app-bottom-bar,0px)] z-10 mx-auto max-w-md border-t border-slate-200 bg-white p-3">
        <Link
          to={`/ward/requests/new?encounterId=${data.encounterId}`}
          className="block w-full rounded-xl bg-sky-600 py-3.5 text-center text-base font-bold text-white"
        >
          + 이송 요청
        </Link>
      </div>
    </div>
  );
}

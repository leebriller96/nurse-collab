import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '@/shared/api/client';
import type { EncounterSummary, PageResponse } from '@/shared/api/types';
import { AlertBadge } from '@/shared/ui/badges';
import { useAuth } from '@/shared/hooks/useAuth';

/**
 * W-01 환자 보드. 모바일 우선.
 * 카드 하나가 환자 하나이고, 병실 순으로 늘어놓는다.
 * 간호사가 실제로 도는 동선과 순서를 맞추기 위해서다.
 */
export default function WardBoardPage() {
  const { staff } = useAuth();

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['encounters'],
    queryFn: async () => {
      const res = await api.get<PageResponse<EncounterSummary>>('/encounters', {
        params: { page: 0, size: 50 },
      });
      return res.data;
    },
  });

  if (isPending) {
    return <p className="p-4 text-sm text-slate-500">불러오는 중…</p>;
  }
  if (isError) {
    return <p className="p-4 text-sm text-red-600">{(error as Error).message}</p>;
  }

  return (
    <div className="pb-24">
      <div className="sticky top-0 z-10 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <h1 className="text-lg font-bold text-slate-900">{staff?.department.name}</h1>
        <p className="text-xs text-slate-500">재원 {data.totalElements}명</p>
      </div>

      <ul className="space-y-2 px-3">
        {data.content.map((encounter) => (
          <li key={encounter.encounterId}>
            <Link to={`/ward/encounters/${encounter.encounterId}`} className="block rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
              <div className="flex items-baseline justify-between gap-2">
                <div className="flex items-baseline gap-2">
                  <span className="text-base font-bold text-slate-900">
                    {encounter.roomNo}-{encounter.bedNo}
                  </span>
                  <span className="text-base font-semibold text-slate-800">{encounter.name}</span>
                  <span className="text-sm text-slate-500">
                    {encounter.sex}/{encounter.age}
                  </span>
                </div>
                {encounter.activeRequestCount > 0 && (
                  <span className="shrink-0 rounded-full bg-sky-100 px-2 py-0.5 text-xs font-semibold text-sky-700">
                    요청 {encounter.activeRequestCount}
                  </span>
                )}
              </div>

              {encounter.diagnosis && (
                <p className="mt-1 text-sm text-slate-600">{encounter.diagnosis}</p>
              )}

              {encounter.alertSummary.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1">
                  {encounter.alertSummary.map((alert) => (
                    <AlertBadge key={alert.alertType} type={alert.alertType} severity={alert.severity} />
                  ))}
                </div>
              )}
            </Link>
          </li>
        ))}
      </ul>

      {data.content.length === 0 && (
        <p className="px-4 py-10 text-center text-sm text-slate-500">재원 중인 환자가 없습니다.</p>
      )}
    </div>
  );
}

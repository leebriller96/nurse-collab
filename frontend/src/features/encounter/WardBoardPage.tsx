import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '@/shared/api/client';
import { useSubjectBriefs } from '@/shared/api/phi';
import type { CareEpisodeSummary } from '@/shared/api/types';
import { AlertBadge } from '@/shared/ui/badges';
import { useAuth } from '@/shared/hooks/useAuth';
import LoadFailed from '@/shared/ui/LoadFailed';
import PullToRefresh from '@/shared/ui/PullToRefresh';
import { CardListSkeleton } from '@/shared/ui/Skeleton';

/**
 * W-01 환자 보드. 모바일 우선.
 * 카드 하나가 침대 하나이고, 병실 순으로 늘어놓는다.
 * 간호사가 실제로 도는 동선과 순서를 맞추기 위해서다.
 *
 * 카드 한 장이 두 곳에서 온다.
 *   - 업무 쪽: 침대와 진행중 요청 수
 *   - 원내: 이름, 나이, 진단명, 주의사항
 *
 * 원내에 닿지 못하면 침대와 요청 수는 그대로 보이고 사람만 사라진다.
 * "302-1에 누군가 있고 요청이 2건 걸려 있다" 까지는 밖에서도 알 수 있다.
 */
export default function WardBoardPage() {
  const { staff } = useAuth();

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['care-episodes'],
    queryFn: async () => (await api.get<CareEpisodeSummary[]>('/care-episodes')).data,
  });

  const briefs = useSubjectBriefs((data ?? []).map((e) => e.subjectRef));

  if (isPending) {
    return <CardListSkeleton />;
  }
  if (isError) {
    return <LoadFailed error={error} onRetry={() => void refetch()} />;
  }

  return (
    <PullToRefresh onRefresh={refetch}>
    <div className="pb-24">
      <div className="sticky top-0 z-10 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <h1 className="text-lg font-bold text-slate-900">{staff?.department.name}</h1>
        <p className="text-xs text-slate-500">재원 {data.length}명</p>
      </div>

      <ul className="space-y-2 px-3">
        {data.map((episode) => {
          const brief = briefs.byRef.get(episode.subjectRef);
          return (
            <li key={episode.subjectRef}>
              <Link
                to={`/ward/subjects/${episode.subjectRef}`}
                className="block rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200"
              >
                <div className="flex items-baseline justify-between gap-2">
                  <div className="flex items-baseline gap-2">
                    <span className="text-base font-bold text-slate-900">
                      {episode.roomNo}-{episode.bedNo}
                    </span>
                    {brief ? (
                      <>
                        <span className="text-base font-semibold text-slate-800">{brief.name}</span>
                        <span className="text-sm text-slate-500">
                          {brief.sex}/{brief.age}
                        </span>
                      </>
                    ) : (
                      <span
                        className={`text-sm ${briefs.unavailable ? 'text-amber-700' : 'text-slate-400'}`}
                      >
                        {briefs.unavailable ? '원내망에서만 조회됩니다' : '불러오는 중…'}
                      </span>
                    )}
                  </div>
                  {episode.activeRequestCount > 0 && (
                    <span className="shrink-0 rounded-full bg-sky-100 px-2 py-0.5 text-xs font-semibold text-sky-700">
                      요청 {episode.activeRequestCount}
                    </span>
                  )}
                </div>

                {brief?.diagnosis && (
                  <p className="mt-1 text-sm text-slate-600">{brief.diagnosis}</p>
                )}

                {brief && brief.alerts.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1">
                    {brief.alerts.map((alert) => (
                      <AlertBadge
                        key={alert.alertType}
                        type={alert.alertType}
                        severity={alert.severity}
                      />
                    ))}
                  </div>
                )}
              </Link>
            </li>
          );
        })}
      </ul>

      {data.length === 0 && (
        <p className="px-4 py-10 text-center text-sm text-slate-500">재원 중인 환자가 없습니다.</p>
      )}
    </div>
    </PullToRefresh>
  );
}

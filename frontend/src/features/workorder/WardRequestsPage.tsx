import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '@/shared/api/client';
import type { PageResponse, OrderSummary } from '@/shared/api/types';
import { PriorityBadge, StatusBadge } from '@/shared/ui/badges';
import LoadFailed from '@/shared/ui/LoadFailed';
import PullToRefresh from '@/shared/ui/PullToRefresh';
import { CardListSkeleton } from '@/shared/ui/Skeleton';
import { useSubjectBriefs } from '@/shared/api/phi';
import OrderSubject from '@/features/workorder/OrderSubject';

/** W-04 내 요청 현황. 우리 병동이 보낸 요청만 본다. */
export default function WardRequestsPage() {
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['transfer-requests', 'OUTBOUND'],
    queryFn: async () => {
      const res = await api.get<PageResponse<OrderSummary>>('/work-orders', {
        params: { direction: 'OUTBOUND', page: 0, size: 50 },
      });
      return res.data;
    },
    // 실시간 알림이 주 경로다. 폴링은 알림을 놓쳤을 때를 위한 보조 장치로만 남긴다.
    refetchInterval: 60_000,
  });

  // 업무 응답에는 이름이 없다. 가명으로 원내에 물어 채운다.
  const briefs = useSubjectBriefs((data?.content ?? []).map((r) => r.subjectRef));

  if (isPending) return <CardListSkeleton />;
  if (isError) return <LoadFailed error={error} onRetry={() => void refetch()} />;

  return (
    <PullToRefresh onRefresh={refetch}>
    <div className="pb-24">
      <div className="sticky top-0 z-10 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <h1 className="text-lg font-bold text-slate-900">내 요청 현황</h1>
        <p className="text-xs text-slate-500">진행중 {data.totalElements}건</p>
      </div>

      <ul className="space-y-2 px-3">
        {data.content.map((r) => (
          <li key={r.id}>
            <Link
              to={`/ward/requests/${r.id}`}
              className="block rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200"
            >
              <div className="flex items-center gap-2">
                <PriorityBadge priority={r.priority} />
                <StatusBadge status={r.status} label={r.statusLabel} />
                <span className="ml-auto text-xs text-slate-400">{r.waitingMinutes}분 경과</span>
              </div>
              <div className="mt-2 flex items-baseline gap-2">
                <OrderSubject
                  row={r}
                  brief={briefs.byRef.get(r.subjectRef ?? '')}
                  unavailable={briefs.unavailable}
                />
              </div>
              <p className="mt-0.5 text-sm text-slate-600">
                {r.itemName} · {r.counterpartDepartment.name}
              </p>
            </Link>
          </li>
        ))}
      </ul>

      {data.content.length === 0 && (
        <p className="px-4 py-10 text-center text-sm text-slate-500">보낸 요청이 없습니다.</p>
      )}
    </div>
    </PullToRefresh>
  );
}

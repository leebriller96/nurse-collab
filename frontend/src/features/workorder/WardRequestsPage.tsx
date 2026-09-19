import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '@/shared/api/client';
import type { PageResponse, OrderSummary } from '@/shared/api/types';
import { PriorityBadge, StatusBadge } from '@/shared/ui/badges';
import LoadFailed from '@/shared/ui/LoadFailed';
import PullToRefresh from '@/shared/ui/PullToRefresh';
import { CardListSkeleton } from '@/shared/ui/Skeleton';
import { useSubjectBriefs, type SubjectBriefs } from '@/shared/api/phi';
import OrderSubject from '@/features/workorder/OrderSubject';

/**
 * W-04 내 요청 현황 · 인수인계. 우리 병동이 보낸 안 끝난 요청을 본다.
 *
 * "우리 차례" 와 "기다리는 중" 으로 나눈다. 교대할 때 넘기는 말이 다르기 때문이다 —
 * 수령 확인을 안 누른 약, 채혈 안 한 검체, 출발 안 시킨 이송은 다음 근무자가 해야 할 일이고
 * 나머지는 상대 파트를 기다리는 것이다. 섞여 있으면 인수인계 때 한 줄씩 열어 봐야 한다.
 */
export default function WardRequestsPage() {
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['transfer-requests', 'OUTBOUND'],
    queryFn: async () => {
      const res = await api.get<PageResponse<OrderSummary>>('/work-orders', {
        params: { direction: 'OUTBOUND', page: 0, size: 100 },
      });
      return res.data;
    },
    // 실시간 알림이 주 경로다. 폴링은 알림을 놓쳤을 때를 위한 보조 장치로만 남긴다.
    refetchInterval: 60_000,
  });

  // 업무 응답에는 이름이 없다. 가명으로 원내에 물어 채운다.
  const briefs = useSubjectBriefs((data?.content ?? []).map((r) => r.subjectRef));

  if (isPending) return <CardListSkeleton section />;
  if (isError) return <LoadFailed error={error} onRetry={() => void refetch()} />;

  const ours = data.content.filter((r) => r.myTurn);
  const waiting = data.content.filter((r) => !r.myTurn);

  return (
    <PullToRefresh onRefresh={refetch}>
    <div className="pb-24">
      <div className="sticky top-0 z-10 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <h1 className="text-lg font-bold text-slate-900">내 요청 현황</h1>
        <p className="text-xs text-slate-500">
          진행중 {data.totalElements}건 · 우리 차례 {ours.length}건
        </p>
      </div>

      {data.content.length === 0 ? (
        <p className="px-4 py-10 text-center text-sm text-slate-500">보낸 요청이 없습니다.</p>
      ) : (
        <>
          <Section
            title="우리 차례"
            hint="병동이 눌러야 다음으로 넘어갑니다. 교대 때 다음 근무자에게 넘길 일입니다."
            rows={ours}
            briefs={briefs}
            empty="병동이 멈춰 세운 요청이 없습니다."
            highlight
          />
          <Section
            title="기다리는 중"
            hint="상대 파트가 처리할 차례입니다."
            rows={waiting}
            briefs={briefs}
            empty="상대 파트를 기다리는 요청이 없습니다."
          />
        </>
      )}
    </div>
    </PullToRefresh>
  );
}

function Section({
  title,
  hint,
  rows,
  briefs,
  empty,
  highlight = false,
}: {
  title: string;
  hint: string;
  rows: OrderSummary[];
  briefs: SubjectBriefs;
  empty: string;
  highlight?: boolean;
}) {
  return (
    <section className="mt-2">
      <div className="px-4 pb-1.5 pt-2">
        <h2 className="text-sm font-semibold text-slate-800">
          {title} <span className="font-normal text-slate-500">{rows.length}</span>
        </h2>
        <p className="text-xs text-slate-500">{hint}</p>
      </div>

      {rows.length === 0 ? (
        <p className="px-4 py-3 text-sm text-slate-400">{empty}</p>
      ) : (
        <ul className="space-y-2 px-3">
          {rows.map((r) => (
            <li key={r.id}>
              <Link
                to={`/ward/requests/${r.id}`}
                className={`block rounded-xl bg-white p-3.5 shadow-sm ring-1 ${
                  highlight ? 'ring-amber-300' : 'ring-slate-200'
                }`}
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
      )}
    </section>
  );
}

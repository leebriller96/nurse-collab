import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '@/shared/api/client';
import type { PageResponse, TransferSummary } from '@/shared/api/types';
import { PriorityBadge, StatusBadge } from '@/shared/ui/badges';
import { useAuth } from '@/shared/hooks/useAuth';

/** 대기가 길어질수록 행이 진해진다. 숫자만으로는 눈에 들어오지 않기 때문이다. */
function waitingStyle(minutes: number) {
  if (minutes >= 60) return 'bg-red-50';
  if (minutes >= 30) return 'bg-amber-50';
  return '';
}

/**
 * E-01 요청 큐. PC 우선.
 * 검사실 간호사는 고정 자리에서 여러 건을 동시에 본다. 촘촘한 테이블이 맞다.
 */
export default function ExamQueuePage() {
  const { staff } = useAuth();
  const navigate = useNavigate();

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['transfer-requests', 'INBOUND'],
    queryFn: async () => {
      const res = await api.get<PageResponse<TransferSummary>>('/transfer-requests', {
        params: { direction: 'INBOUND', page: 0, size: 50 },
      });
      return res.data;
    },
    // 실시간 알림이 주 경로다. 폴링은 알림을 놓쳤을 때를 위한 보조 장치로만 남긴다.
    refetchInterval: 60_000,
  });

  if (isPending) {
    return <p className="p-6 text-sm text-slate-500">불러오는 중…</p>;
  }
  if (isError) {
    return <p className="p-6 text-sm text-red-600">{(error as Error).message}</p>;
  }

  return (
    <div className="p-6">
      <div className="mb-4 flex items-baseline justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">{staff?.department.name} 요청 큐</h1>
          <p className="text-sm text-slate-500">진행중 {data.totalElements}건</p>
        </div>
        <span className="text-xs text-slate-400">10초마다 자동 갱신</span>
      </div>

      <div className="overflow-x-auto rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
        <table className="w-full min-w-[860px] text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
            <tr>
              <th className="px-3 py-2.5 font-medium">우선</th>
              <th className="px-3 py-2.5 font-medium">요청번호</th>
              <th className="px-3 py-2.5 font-medium">병동</th>
              <th className="px-3 py-2.5 font-medium">환자</th>
              <th className="px-3 py-2.5 font-medium">검사</th>
              <th className="px-3 py-2.5 font-medium">대기</th>
              <th className="px-3 py-2.5 font-medium">상태</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {data.content.map((row) => (
              <tr
                key={row.id}
                onClick={() => navigate(`/exam/requests/${row.id}`)}
                className={`cursor-pointer hover:bg-slate-50 ${waitingStyle(row.waitingMinutes)}`}
              >
                <td className="px-3 py-3">
                  <PriorityBadge priority={row.priority} />
                </td>
                <td className="px-3 py-3 font-mono text-xs text-slate-600">{row.requestNo}</td>
                <td className="px-3 py-3 text-slate-700">{row.counterpartDepartment.name}</td>
                <td className="px-3 py-3">
                  <span className="font-medium text-slate-900">{row.patient.name}</span>
                  <span className="ml-1.5 text-slate-500">
                    {row.patient.sex}/{row.patient.age}
                  </span>
                  <span className="ml-1.5 text-slate-400">{row.roomNo}호</span>
                  {row.criticalAlertCount > 0 && (
                    <span className="ml-1.5 rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold text-red-700">
                      !주의 {row.criticalAlertCount}
                    </span>
                  )}
                </td>
                <td className="px-3 py-3 text-slate-700">{row.examName}</td>
                <td className="px-3 py-3 tabular-nums text-slate-700">{row.waitingMinutes}분</td>
                <td className="px-3 py-3">
                  <StatusBadge status={row.status} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {data.content.length === 0 && (
          <p className="px-4 py-12 text-center text-sm text-slate-500">들어온 요청이 없습니다.</p>
        )}
      </div>
    </div>
  );
}

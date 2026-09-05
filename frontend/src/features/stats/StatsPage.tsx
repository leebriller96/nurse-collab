import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, messageOf } from '@/shared/api/client';
import { useAuth } from '@/shared/hooks/useAuth';

interface WaitingTimeStats {
  period: { from: string; to: string };
  overall: { totalRequests: number; avgWaitingMinutes: number | null; avgTotalMinutes: number | null };
  byDepartment: {
    departmentId: number;
    departmentName: string;
    requestCount: number;
    avgWaitingMinutes: number | null;
    holdCount: number;
  }[];
  byHour: { hour: number; requestCount: number }[];
}

/**
 * toISOString() 은 UTC 날짜를 준다. 서버는 이 값을 현지 시간대로 해석하므로
 * UTC 보다 앞선 지역에서는 하루가 밀린다. 새벽에 들어온 요청이 통째로 빠지게 된다.
 */
const localDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const today = () => localDate(new Date());
const daysAgo = (n: number) => localDate(new Date(Date.now() - n * 86400000));

function Metric({ label, value, unit }: { label: string; value: number | null; unit: string }) {
  return (
    <div className="rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-bold tabular-nums text-slate-900">
        {value ?? '-'}
        <span className="ml-1 text-sm font-normal text-slate-500">{unit}</span>
      </p>
    </div>
  );
}

/** A-01 통계 대시보드. 수간호사는 자기 파트만, 관리자는 전체를 본다. */
export default function StatsPage() {
  const { staff } = useAuth();
  const [from, setFrom] = useState(daysAgo(7));
  const [to, setTo] = useState(today());

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['stats', from, to],
    queryFn: async () =>
      (await api.get<WaitingTimeStats>('/stats/waiting-time', { params: { from, to } })).data,
  });

  if (isPending) return <p className="p-6 text-sm text-slate-500">불러오는 중…</p>;
  if (isError) return <p className="p-6 text-sm text-red-600">{messageOf(error)}</p>;

  const peak = Math.max(1, ...data.byHour.map((h) => h.requestCount));
  const busiest = data.byHour.reduce((a, b) => (b.requestCount > a.requestCount ? b : a));

  return (
    <div className="mx-auto max-w-5xl p-6">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-900">대기시간 통계</h1>
          <p className="text-sm text-slate-500">
            {staff?.role === 'ADMIN' ? '전체 파트' : `${staff?.department.name} 기준`}
          </p>
        </div>
        <div className="flex items-center gap-2 text-sm">
          <input
            type="date"
            value={from}
            max={to}
            onChange={(e) => setFrom(e.target.value)}
            className="rounded-lg border border-slate-300 px-2 py-1.5"
          />
          <span className="text-slate-400">~</span>
          <input
            type="date"
            value={to}
            min={from}
            onChange={(e) => setTo(e.target.value)}
            className="rounded-lg border border-slate-300 px-2 py-1.5"
          />
        </div>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <Metric label="전체 요청 건수" value={data.overall.totalRequests} unit="건" />
        <Metric label="요청부터 접수까지" value={data.overall.avgWaitingMinutes} unit="분" />
        <Metric label="요청부터 검사 완료까지" value={data.overall.avgTotalMinutes} unit="분" />
      </div>

      <section className="mt-5 rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
        <h2 className="mb-3 text-sm font-medium text-slate-600">파트별</h2>
        {data.byDepartment.length === 0 ? (
          <p className="py-6 text-center text-sm text-slate-400">해당 기간에 요청이 없습니다.</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="border-b border-slate-200 text-left text-xs text-slate-500">
              <tr>
                <th className="pb-2 font-medium">파트</th>
                <th className="pb-2 font-medium">요청</th>
                <th className="pb-2 font-medium">평균 대기</th>
                <th className="pb-2 font-medium">보류</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.byDepartment.map((row) => (
                <tr key={row.departmentId}>
                  <td className="py-2.5 font-medium text-slate-900">{row.departmentName}</td>
                  <td className="py-2.5 tabular-nums text-slate-700">{row.requestCount}건</td>
                  <td className="py-2.5 tabular-nums font-semibold text-slate-900">
                    {row.avgWaitingMinutes ?? '-'}분
                  </td>
                  <td className="py-2.5 tabular-nums text-slate-700">{row.holdCount}건</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="mt-5 rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
        <div className="mb-3 flex items-baseline justify-between">
          <h2 className="text-sm font-medium text-slate-600">시간대별 요청</h2>
          {busiest.requestCount > 0 && (
            <span className="text-xs text-slate-500">
              가장 몰리는 시간 {busiest.hour}시 ({busiest.requestCount}건)
            </span>
          )}
        </div>
        {/*
          막대는 높이가 정해진 칸(h-32)의 직계 자식이어야 한다.
          중간에 높이가 auto 인 열을 끼우면 퍼센트 높이가 기준을 잃고 전부 최소 높이로 깔린다.
          그래서 눈금은 막대와 같은 칸에 넣지 않고 아래 줄로 뺀다.
        */}
        <div className="flex h-32 items-end gap-[3px]">
          {data.byHour.map((h) => (
            <div
              key={h.hour}
              title={`${h.hour}시 ${h.requestCount}건`}
              style={{ height: `${(h.requestCount / peak) * 100}%` }}
              className={`min-h-[2px] flex-1 rounded-t ${h.requestCount > 0 ? 'bg-sky-500' : 'bg-slate-100'}`}
            />
          ))}
        </div>
        <div className="mt-1 flex gap-[3px]">
          {data.byHour.map((h) => (
            <span key={h.hour} className="flex-1 text-center text-[10px] text-slate-400">
              {h.hour % 3 === 0 ? h.hour : ''}
            </span>
          ))}
        </div>
      </section>
    </div>
  );
}

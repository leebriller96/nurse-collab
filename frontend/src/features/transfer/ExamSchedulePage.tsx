import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import { useAuth } from '@/shared/hooks/useAuth';
import type { PageResponse, TransferSummary } from '@/shared/api/types';
import { PriorityBadge, StatusBadge } from '@/shared/ui/badges';

/** 기본은 검사실이 실제로 움직이는 시간대. 새벽 칸이 화면 절반을 먹으면 읽기 어렵다. */
const DEFAULT_START = 7;
const DEFAULT_END = 21;
const ROW_HEIGHT = 56;

const localDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const hhmm = (iso: string) =>
  new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });

/**
 * 보여줄 시간 범위. 기본 구간을 쓰되 잡힌 일정이 밖에 있으면 그만큼 넓힌다.
 * 범위를 고정하면 "8건" 이라고 적어 놓고 화면에는 1건만 보이는 일이 생긴다.
 */
function hourRange(times: string[]) {
  let start = DEFAULT_START;
  let end = DEFAULT_END;
  for (const iso of times) {
    const h = new Date(iso).getHours();
    start = Math.min(start, h);
    end = Math.max(end, h + 1);
  }
  return Array.from({ length: end - start }, (_, i) => start + i);
}

/** 예정 시각을 보드 위 세로 위치(px)로 바꾼다. */
function offsetOf(iso: string, startHour: number) {
  const d = new Date(iso);
  return (d.getHours() - startHour + d.getMinutes() / 60) * ROW_HEIGHT;
}

/**
 * E-03 일정 보드.
 *
 * 큐는 "무엇이 밀려 있나" 를 보여주고, 이 화면은 "언제 무엇이 잡혀 있나" 를 보여준다.
 * 접수하면서 시간을 정할 때 앞뒤가 비어 있는지 한눈에 봐야 한다.
 */
export default function ExamSchedulePage() {
  const { staff } = useAuth();
  const navigate = useNavigate();
  const [date, setDate] = useState(localDate(new Date()));

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['exam-schedule', date],
    queryFn: async () =>
      (await api.get<PageResponse<TransferSummary>>('/transfer-requests', {
        params: { direction: 'INBOUND', from: date, to: date, page: 0, size: 100 },
      })).data,
    refetchInterval: 60_000,
  });

  if (isPending) return <p className="p-6 text-sm text-slate-500">불러오는 중…</p>;
  if (isError) return <p className="p-6 text-sm text-red-600">{messageOf(error)}</p>;

  const scheduled = data.content.filter((r) => r.scheduledAt);
  const unscheduled = data.content.filter((r) => !r.scheduledAt);
  const hours = hourRange(scheduled.map((r) => r.scheduledAt!));

  return (
    <div className="mx-auto max-w-5xl p-6">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-900">{staff?.department.name} 일정</h1>
          <p className="text-sm text-slate-500">
            시각이 잡힌 {scheduled.length}건, 아직 안 잡힌 {unscheduled.length}건
          </p>
        </div>
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          className="rounded-lg border border-slate-300 px-2 py-1.5 text-sm"
        />
      </div>

      {unscheduled.length > 0 && (
        <section className="mb-4 rounded-xl bg-amber-50 p-3.5 ring-1 ring-amber-200">
          <h2 className="mb-2 text-sm font-semibold text-amber-900">시각이 안 잡힌 요청</h2>
          <div className="flex flex-wrap gap-2">
            {unscheduled.map((r) => (
              <button
                key={r.id}
                type="button"
                onClick={() => navigate(`/exam/requests/${r.id}`)}
                className="rounded-lg bg-white px-3 py-2 text-left text-sm shadow-sm ring-1 ring-amber-200"
              >
                <span className="font-medium text-slate-900">{r.patient.name}</span>
                <span className="ml-1.5 text-slate-500">{r.examName}</span>
                <span className="ml-1.5 text-xs text-slate-400">{r.waitingMinutes}분 대기</span>
              </button>
            ))}
          </div>
        </section>
      )}

      <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
        <div className="relative flex">
          {/* 시간 눈금 */}
          <div className="w-14 shrink-0 border-r border-slate-200">
            {hours.map((h) => (
              <div
                key={h}
                style={{ height: ROW_HEIGHT }}
                className="border-b border-slate-100 pr-2 pt-1 text-right text-xs tabular-nums text-slate-400"
              >
                {h}:00
              </div>
            ))}
          </div>

          {/* 배치 영역 */}
          <div className="relative flex-1">
            {hours.map((h) => (
              <div key={h} style={{ height: ROW_HEIGHT }} className="border-b border-slate-100" />
            ))}

            {scheduled.map((r) => {
              const top = offsetOf(r.scheduledAt!, hours[0]);

              return (
                <button
                  key={r.id}
                  type="button"
                  onClick={() => navigate(`/exam/requests/${r.id}`)}
                  style={{ top, height: ROW_HEIGHT - 6 }}
                  className={`absolute left-2 right-2 flex items-center gap-2 overflow-hidden rounded-lg px-3 text-left text-sm shadow-sm ring-1 ${
                    r.priority === 'EMERGENCY'
                      ? 'bg-red-50 ring-red-300'
                      : r.priority === 'URGENT'
                        ? 'bg-amber-50 ring-amber-300'
                        : 'bg-sky-50 ring-sky-200'
                  }`}
                >
                  <span className="shrink-0 font-semibold tabular-nums text-slate-900">
                    {hhmm(r.scheduledAt!)}
                  </span>
                  <span className="truncate font-medium text-slate-900">{r.patient.name}</span>
                  <span className="truncate text-slate-500">{r.examName}</span>
                  <span className="shrink-0 text-xs text-slate-400">{r.roomNo}호</span>
                  {r.criticalAlertCount > 0 && (
                    <span className="shrink-0 rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold text-red-700">
                      !주의
                    </span>
                  )}
                  <span className="ml-auto flex shrink-0 items-center gap-1.5">
                    {r.priority !== 'ROUTINE' && <PriorityBadge priority={r.priority} />}
                    <StatusBadge status={r.status} />
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {scheduled.length === 0 && (
        <p className="mt-4 text-center text-sm text-slate-500">이 날짜에 잡힌 검사가 없습니다.</p>
      )}
    </div>
  );
}

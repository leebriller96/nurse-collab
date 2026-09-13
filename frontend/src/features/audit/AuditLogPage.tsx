import { useState } from 'react';
import axios from 'axios';
import { useQuery } from '@tanstack/react-query';
import { phi } from '@/shared/api/phi';
import type { PageResponse, PhiAccessLogEntry } from '@/shared/api/types';
import LoadFailed from '@/shared/ui/LoadFailed';
import { useUrlParam } from '@/shared/hooks/useUrlParam';
import { TableSkeleton } from '@/shared/ui/Skeleton';

const ACTION_LABEL: Record<string, string> = {
  VIEW: '환자 열람',
  CHECKLIST: '확인 경고 조회',
  VITAL_VIEW: '활력징후 열람',
  VITAL_CREATE: '활력징후 기록',
  NOTE_VIEW: '간호기록 열람',
  NOTE_CREATE: '간호기록 작성',
  NOTE_EDIT: '간호기록 수정',
  ALERT_CREATE: '주의사항 남김',
  ALERT_DEACTIVATE: '주의사항 내림',
};

const DENIED_LABEL: Record<string, string> = {
  NOT_RELATED: '관계 없음',
  RATE_LIMITED: '조회량 초과',
};

const localDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const stamp = (iso: string) =>
  new Date(iso).toLocaleString('ko-KR', {
    month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
  });

/**
 * A-05 환자 정보 열람 기록. 누가 어떤 환자 정보를 열어봤는지 확인한다.
 *
 * 원내 경로(/phi)에서 읽는다. 진료정보가 실제로 나간 곳이 원내이므로 기록도 원내에 있고,
 * 원내망 밖에서는 이 화면이 열리지 않는다. 그때는 빈 표 대신 이유를 띄운다 —
 * 빈 표는 "아무도 열지 않았다" 로 읽힌다.
 *
 * 거절된 시도도 함께 보인다. 조사할 때 제일 보고 싶은 것이 그것이다.
 */
export default function AuditLogPage() {
  // 조회 조건은 주소에 담는다. 기간을 맞춰 놓고 다른 탭에 다녀와도 살아 있어야 한다.
  const [from, setFrom] = useUrlParam('from', localDate(new Date(Date.now() - 6 * 86400000)));
  const [to, setTo] = useUrlParam('to', localDate(new Date()));
  const [patientNo, setPatientNo] = useUrlParam('patientNo');
  const [pageParam, setPageParam] = useUrlParam('page', '0');
  // 입력 중인 글자는 주소에 올리지 않는다. 한 자 칠 때마다 조회가 나간다.
  const [draft, setDraft] = useState(patientNo);

  const page = Number(pageParam) || 0;

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['phi-access-logs', from, to, patientNo, page],
    queryFn: async () =>
      (await phi.get<PageResponse<PhiAccessLogEntry>>('/access-logs', {
        params: { from, to, page, size: 30, ...(patientNo ? { patientNo } : {}) },
      })).data,
    retry: 1,
  });

  if (isPending) {
    return (
      <div className="p-6">
        <TableSkeleton columns={6} header />
      </div>
    );
  }
  if (isError) {
    // 응답 자체가 없으면 원내에 닿지 못한 것이다. 서버가 답한 오류와 구별해서 말한다.
    if (axios.isAxiosError(error) && !error.response) {
      return (
        <div className="mx-auto max-w-5xl p-6">
          <h1 className="text-xl font-bold text-slate-900">환자 정보 열람 기록</h1>
          <div className="mt-6 rounded-xl bg-amber-50 p-5 ring-1 ring-amber-200">
            <p className="text-sm font-semibold text-amber-900">원내망에서만 조회됩니다</p>
            <p className="mt-1 text-sm text-amber-800">
              열람 기록은 병원 안에만 남습니다. 기록이 없다는 뜻이 아닙니다.
            </p>
          </div>
        </div>
      );
    }
    return <LoadFailed error={error} onRetry={() => void refetch()} />;
  }

  return (
    <div className="mx-auto max-w-5xl p-6">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-900">환자 정보 열람 기록</h1>
          <p className="text-sm text-slate-500">
            열어본 것과 막힌 시도가 원내에 남습니다. 이 기간 {data.totalElements}건
          </p>
        </div>
        <form
          className="flex flex-wrap items-center gap-2 text-sm"
          onSubmit={(e) => {
            e.preventDefault();
            setPatientNo(draft.trim(), { page: '' });
          }}
        >
          <input
            type="date" value={from} max={to}
            onChange={(e) => setFrom(e.target.value, { page: '' })}
            className="rounded-lg border border-slate-300 px-2 py-1.5"
          />
          <span className="text-slate-400">~</span>
          <input
            type="date" value={to} min={from}
            onChange={(e) => setTo(e.target.value, { page: '' })}
            className="rounded-lg border border-slate-300 px-2 py-1.5"
          />
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="등록번호로 좁히기"
            className="rounded-lg border border-slate-300 px-3 py-1.5"
          />
          <button type="submit" className="rounded-lg bg-slate-800 px-4 py-1.5 font-semibold text-white">
            찾기
          </button>
        </form>
      </div>

      <div className="overflow-x-auto rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
        <table className="w-full min-w-[820px] text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
            <tr>
              <th className="px-3 py-2.5 font-medium">시각</th>
              <th className="px-3 py-2.5 font-medium">행위</th>
              <th className="px-3 py-2.5 font-medium">결과</th>
              <th className="px-3 py-2.5 font-medium">환자</th>
              <th className="px-3 py-2.5 font-medium">누가</th>
              <th className="px-3 py-2.5 font-medium">접속 주소</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {data.content.map((row) => (
              <tr key={row.id} className={row.granted ? '' : 'bg-red-50/60'}>
                <td className="whitespace-nowrap px-3 py-2.5 tabular-nums text-slate-500">
                  {stamp(row.occurredAt)}
                </td>
                <td className="px-3 py-2.5 text-slate-700">
                  {ACTION_LABEL[row.action] ?? row.action}
                  {row.detail && (
                    <details className="mt-1 text-xs text-slate-500">
                      <summary className="cursor-pointer">바뀐 내용</summary>
                      <pre className="mt-1 whitespace-pre-wrap break-all font-sans">
                        {JSON.stringify(row.detail, null, 2)}
                      </pre>
                    </details>
                  )}
                </td>
                <td className="px-3 py-2.5">
                  {row.granted ? (
                    <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">
                      허용
                    </span>
                  ) : (
                    <span className="rounded bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-800">
                      거절 · {DENIED_LABEL[row.deniedReason ?? ''] ?? row.deniedReason}
                    </span>
                  )}
                </td>
                <td className="px-3 py-2.5">
                  {row.patient ? (
                    <>
                      <span className="font-medium text-slate-900">{row.patient.name}</span>
                      <span className="ml-1.5 font-mono text-xs text-slate-400">
                        {row.patient.patientNo}
                      </span>
                    </>
                  ) : (
                    <span className="text-slate-300">-</span>
                  )}
                </td>
                <td className="px-3 py-2.5 font-mono text-xs text-slate-700">{row.actor.loginId}</td>
                <td className="px-3 py-2.5 font-mono text-xs text-slate-400">
                  {row.ipAddress ?? '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {data.content.length === 0 && (
          <p className="px-4 py-12 text-center text-sm text-slate-500">해당 기간에 기록이 없습니다.</p>
        )}
      </div>

      {data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3 text-sm">
          <button
            type="button" disabled={page === 0}
            onClick={() => setPageParam(String(page - 1))}
            className="rounded-lg px-3 py-1.5 text-slate-600 disabled:text-slate-300"
          >
            이전
          </button>
          <span className="tabular-nums text-slate-500">{page + 1} / {data.totalPages}</span>
          <button
            type="button" disabled={page + 1 >= data.totalPages}
            onClick={() => setPageParam(String(page + 1))}
            className="rounded-lg px-3 py-1.5 text-slate-600 disabled:text-slate-300"
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
}

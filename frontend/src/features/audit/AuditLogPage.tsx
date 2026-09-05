import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, messageOf } from '@/shared/api/client';
import type { AuditLogEntry, PageResponse } from '@/shared/api/types';

const ACTION_LABEL: Record<string, string> = {
  VIEW: '열람',
  CREATE: '작성',
  UPDATE: '수정',
  DELETE: '삭제',
  LOGIN: '로그인',
};

const TARGET_LABEL: Record<string, string> = {
  ENCOUNTER: '환자 정보',
  TRANSFER_REQUEST: '이송 요청',
  NURSING_NOTE: '간호기록',
};

const ACTION_STYLE: Record<string, string> = {
  VIEW: 'bg-slate-100 text-slate-700',
  CREATE: 'bg-emerald-100 text-emerald-800',
  UPDATE: 'bg-amber-100 text-amber-900',
  DELETE: 'bg-red-100 text-red-800',
  LOGIN: 'bg-sky-100 text-sky-800',
};

const localDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const stamp = (iso: string) =>
  new Date(iso).toLocaleString('ko-KR', {
    month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
  });

/** A-05 접근 기록. 누가 어떤 환자 정보를 열어봤는지 확인한다. */
export default function AuditLogPage() {
  const [from, setFrom] = useState(localDate(new Date(Date.now() - 6 * 86400000)));
  const [to, setTo] = useState(localDate(new Date()));
  const [patientNo, setPatientNo] = useState('');
  const [page, setPage] = useState(0);

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['audit-logs', from, to, page],
    queryFn: async () =>
      (await api.get<PageResponse<AuditLogEntry>>('/audit-logs',
        { params: { from, to, page, size: 30 } })).data,
  });

  if (isPending) return <p className="p-6 text-sm text-slate-500">불러오는 중…</p>;
  if (isError) return <p className="p-6 text-sm text-red-600">{messageOf(error)}</p>;

  // 등록번호 검색은 화면에서 거른다. 서버는 환자 식별자로만 받기 때문이다.
  const rows = patientNo.trim()
    ? data.content.filter((r) => r.patient?.patientNo.includes(patientNo.trim()))
    : data.content;

  return (
    <div className="mx-auto max-w-5xl p-6">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-900">접근 기록</h1>
          <p className="text-sm text-slate-500">
            환자 정보를 열어본 것도 남습니다. 이 기간 {data.totalElements}건
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <input
            type="date" value={from} max={to}
            onChange={(e) => { setFrom(e.target.value); setPage(0); }}
            className="rounded-lg border border-slate-300 px-2 py-1.5"
          />
          <span className="text-slate-400">~</span>
          <input
            type="date" value={to} min={from}
            onChange={(e) => { setTo(e.target.value); setPage(0); }}
            className="rounded-lg border border-slate-300 px-2 py-1.5"
          />
          <input
            value={patientNo}
            onChange={(e) => setPatientNo(e.target.value)}
            placeholder="등록번호로 좁히기"
            className="rounded-lg border border-slate-300 px-3 py-1.5"
          />
        </div>
      </div>

      <div className="overflow-x-auto rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
        <table className="w-full min-w-[820px] text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
            <tr>
              <th className="px-3 py-2.5 font-medium">시각</th>
              <th className="px-3 py-2.5 font-medium">행위</th>
              <th className="px-3 py-2.5 font-medium">대상</th>
              <th className="px-3 py-2.5 font-medium">환자</th>
              <th className="px-3 py-2.5 font-medium">누가</th>
              <th className="px-3 py-2.5 font-medium">접속 주소</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((row) => (
              <tr key={row.id}>
                <td className="whitespace-nowrap px-3 py-2.5 tabular-nums text-slate-500">
                  {stamp(row.occurredAt)}
                </td>
                <td className="px-3 py-2.5">
                  <span className={`rounded px-2 py-0.5 text-xs font-semibold ${
                    ACTION_STYLE[row.action] ?? 'bg-slate-100 text-slate-700'
                  }`}>
                    {ACTION_LABEL[row.action] ?? row.action}
                  </span>
                </td>
                <td className="px-3 py-2.5 text-slate-700">
                  {TARGET_LABEL[row.targetType] ?? row.targetType}
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
                <td className="px-3 py-2.5 text-slate-700">
                  {row.actor ? `${row.actor.departmentName} ${row.actor.name}` : '-'}
                </td>
                <td className="px-3 py-2.5 font-mono text-xs text-slate-400">
                  {row.ipAddress ?? '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {rows.length === 0 && (
          <p className="px-4 py-12 text-center text-sm text-slate-500">해당 기간에 기록이 없습니다.</p>
        )}
      </div>

      {data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3 text-sm">
          <button
            type="button" disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="rounded-lg px-3 py-1.5 text-slate-600 disabled:text-slate-300"
          >
            이전
          </button>
          <span className="tabular-nums text-slate-500">{page + 1} / {data.totalPages}</span>
          <button
            type="button" disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-lg px-3 py-1.5 text-slate-600 disabled:text-slate-300"
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
}

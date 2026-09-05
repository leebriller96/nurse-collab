import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import { useAuth } from '@/shared/hooks/useAuth';
import type { PageResponse, TransferStatus, TransferSummary } from '@/shared/api/types';
import { PriorityBadge, StatusBadge } from '@/shared/ui/badges';

const FINISHED: TransferStatus[] = ['COMPLETED', 'CANCELLED'];

const localDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const stamp = (iso: string) =>
  new Date(iso).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });

/**
 * E-04 지난 요청 조회.
 *
 * 큐와 현황은 오늘 진행중인 것만 보여준다.
 * 어제 무슨 일이 있었는지 확인할 곳이 따로 있어야 한다.
 */
export default function TransferHistoryPage() {
  const { staff } = useAuth();
  const navigate = useNavigate();

  const [from, setFrom] = useState(localDate(new Date(Date.now() - 13 * 86400000)));
  const [to, setTo] = useState(localDate(new Date()));
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [onlyFinished, setOnlyFinished] = useState(true);
  const [page, setPage] = useState(0);

  const inbound = staff?.department.deptType === 'EXAM';

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['transfer-history', from, to, query, onlyFinished, page],
    queryFn: async () =>
      (await api.get<PageResponse<TransferSummary>>('/transfer-requests', {
        params: {
          direction: inbound ? 'INBOUND' : 'OUTBOUND',
          from, to, page, size: 30,
          ...(query ? { keyword: query } : {}),
          ...(onlyFinished ? { status: FINISHED } : {}),
        },
        paramsSerializer: { indexes: null },
      })).data,
  });

  const detailPath = inbound ? '/exam/requests' : '/ward/requests';

  return (
    <div className="mx-auto max-w-5xl p-6">
      <div className="mb-5">
        <h1 className="text-xl font-bold text-slate-900">지난 요청</h1>
        <p className="text-sm text-slate-500">
          {inbound ? '우리 검사실로 들어온' : '우리 병동이 보낸'} 요청을 기간으로 찾습니다.
        </p>
      </div>

      <form
        className="mb-4 flex flex-wrap items-center gap-2 text-sm"
        onSubmit={(e) => {
          e.preventDefault();
          setQuery(keyword.trim());
          setPage(0);
        }}
      >
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
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="환자명 또는 요청번호"
          className="w-52 rounded-lg border border-slate-300 px-3 py-1.5"
        />
        <button type="submit" className="rounded-lg bg-slate-800 px-4 py-1.5 font-semibold text-white">
          찾기
        </button>
        <label className="ml-2 flex items-center gap-1.5 text-slate-600">
          <input
            type="checkbox"
            checked={onlyFinished}
            onChange={(e) => { setOnlyFinished(e.target.checked); setPage(0); }}
          />
          끝난 것만
        </label>
      </form>

      {isPending && <p className="text-sm text-slate-500">불러오는 중…</p>}
      {isError && <p className="text-sm text-red-600">{messageOf(error)}</p>}

      {data && (
        <>
          <p className="mb-2 text-sm text-slate-500">{data.totalElements}건</p>
          <div className="overflow-x-auto rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
            <table className="w-full min-w-[820px] text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
                <tr>
                  <th className="px-3 py-2.5 font-medium">요청 시각</th>
                  <th className="px-3 py-2.5 font-medium">요청번호</th>
                  <th className="px-3 py-2.5 font-medium">환자</th>
                  <th className="px-3 py-2.5 font-medium">검사</th>
                  <th className="px-3 py-2.5 font-medium">{inbound ? '병동' : '검사실'}</th>
                  <th className="px-3 py-2.5 font-medium">소요</th>
                  <th className="px-3 py-2.5 font-medium">결과</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((row) => (
                  <tr
                    key={row.id}
                    onClick={() => navigate(`${detailPath}/${row.id}`)}
                    className="cursor-pointer hover:bg-slate-50"
                  >
                    <td className="whitespace-nowrap px-3 py-2.5 tabular-nums text-slate-500">
                      {stamp(row.requestedAt)}
                    </td>
                    <td className="px-3 py-2.5 font-mono text-xs text-slate-600">{row.requestNo}</td>
                    <td className="px-3 py-2.5">
                      <span className="font-medium text-slate-900">{row.patient.name}</span>
                      <span className="ml-1.5 text-slate-500">
                        {row.patient.sex}/{row.patient.age}
                      </span>
                      <span className="ml-1.5 text-slate-400">{row.roomNo}호</span>
                    </td>
                    <td className="px-3 py-2.5 text-slate-700">{row.examName}</td>
                    <td className="px-3 py-2.5 text-slate-700">{row.counterpartDepartment.name}</td>
                    <td className="px-3 py-2.5 tabular-nums text-slate-700">{row.waitingMinutes}분</td>
                    <td className="px-3 py-2.5">
                      <div className="flex items-center gap-1.5">
                        <StatusBadge status={row.status} />
                        {row.priority !== 'ROUTINE' && <PriorityBadge priority={row.priority} />}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {data.content.length === 0 && (
              <p className="px-4 py-12 text-center text-sm text-slate-500">
                조건에 맞는 요청이 없습니다.
              </p>
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
        </>
      )}
    </div>
  );
}

import { useState } from 'react';
import axios from 'axios';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { phi } from '@/shared/api/phi';
import type { AuditLogEntry, PageResponse, PhiAccessLogEntry } from '@/shared/api/types';
import LoadFailed from '@/shared/ui/LoadFailed';
import { useUrlParam } from '@/shared/hooks/useUrlParam';
import { TableSkeleton } from '@/shared/ui/Skeleton';

const PHI_ACTION_LABEL: Record<string, string> = {
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

const WORK_ACTION_LABEL: Record<string, string> = {
  LOGIN: '로그인',
  LOGIN_FAILED: '로그인 실패',
  LOGOUT: '로그아웃',
  CREATE: '추가',
  UPDATE: '수정',
  DEACTIVATE: '비활성화',
};

const TARGET_LABEL: Record<string, string> = {
  STAFF: '직원',
  DEPARTMENT: '부서',
  SERVICE_ITEM: '업무 항목',
};

const FAILURE_LABEL: Record<string, string> = {
  UNKNOWN_ID: '없는 아이디',
  BAD_PASSWORD: '비밀번호 틀림',
  INACTIVE: '비활성 계정',
};

const TABS = [
  { key: 'phi', label: '환자 정보 열람' },
  { key: 'work', label: '업무 기록' },
] as const;

const localDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const stamp = (iso: string) =>
  new Date(iso).toLocaleString('ko-KR', {
    month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
  });

/**
 * A-05 접근 기록. 탭이 둘이고 사는 곳이 다르다.
 *
 * - 환자 정보 열람: 원내(/phi). 진료정보가 실제로 나간 곳이 원내이므로 기록도 원내에 있고,
 *   원내망 밖에서는 열리지 않는다. 그때는 빈 표 대신 이유를 띄운다 — 빈 표는 "아무도 열지 않았다" 로 읽힌다.
 * - 업무 기록: 업무 서버. 로그인·로그인 실패와 기준 정보 변경. 계정이 털렸는지는 병원 밖에서도 봐야 한다.
 *
 * 탭과 기간은 주소에 담는다. 다른 화면에 다녀와도 보던 곳으로 돌아와야 한다.
 */
export default function AuditLogPage() {
  const [tab, setTab] = useUrlParam('tab', 'phi');
  // 기본 기간은 처음 그릴 때 한 번 정한다. 렌더마다 시계를 읽으면 자정을 넘는 순간 기본값이 바뀐다.
  const [defaultPeriod] = useState(() => ({
    from: localDate(new Date(Date.now() - 6 * 86400000)),
    to: localDate(new Date()),
  }));
  const [from, setFrom] = useUrlParam('from', defaultPeriod.from);
  const [to, setTo] = useUrlParam('to', defaultPeriod.to);
  const [pageParam, setPageParam] = useUrlParam('page', '0');
  const page = Number(pageParam) || 0;

  const period = { from, to, setFrom, setTo, page, setPage: (p: number) => setPageParam(String(p)) };

  return (
    <div className="mx-auto max-w-5xl p-6">
      <h1 className="text-xl font-bold text-slate-900">접근 기록</h1>

      <div role="tablist" className="mt-3 mb-5 flex gap-1 border-b border-slate-200">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            // 탭을 바꾸면 쪽 번호와 탭 전용 조건은 처음으로 돌린다. 기간은 둔다.
            onClick={() => setTab(t.key, { page: '', patientNo: '' })}
            className={`-mb-px border-b-2 px-3 py-2 text-sm font-semibold ${
              tab === t.key
                ? 'border-slate-800 text-slate-900'
                : 'border-transparent text-slate-400 hover:text-slate-600'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'work' ? <WorkAuditTab {...period} /> : <PhiAccessTab {...period} />}
    </div>
  );
}

interface PeriodProps {
  from: string;
  to: string;
  setFrom: (value: string, extra?: Record<string, string>) => void;
  setTo: (value: string, extra?: Record<string, string>) => void;
  page: number;
  setPage: (page: number) => void;
}

function PeriodInputs({ from, to, setFrom, setTo }: PeriodProps) {
  return (
    <>
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
    </>
  );
}

function Pager({ page, totalPages, setPage }: { page: number; totalPages: number; setPage: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-4 flex items-center justify-center gap-3 text-sm">
      <button
        type="button" disabled={page === 0}
        onClick={() => setPage(page - 1)}
        className="rounded-lg px-3 py-1.5 text-slate-600 disabled:text-slate-300"
      >
        이전
      </button>
      <span className="tabular-nums text-slate-500">{page + 1} / {totalPages}</span>
      <button
        type="button" disabled={page + 1 >= totalPages}
        onClick={() => setPage(page + 1)}
        className="rounded-lg px-3 py-1.5 text-slate-600 disabled:text-slate-300"
      >
        다음
      </button>
    </div>
  );
}

// ── 환자 정보 열람 (원내) ───────────────────────────────────

function PhiAccessTab(props: PeriodProps) {
  const { from, to, page, setPage } = props;
  const [patientNo, setPatientNo] = useUrlParam('patientNo');
  // 입력 중인 글자는 주소에 올리지 않는다. 한 자 칠 때마다 조회가 나간다.
  const [draft, setDraft] = useState(patientNo);

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['phi-access-logs', from, to, patientNo, page],
    queryFn: async () =>
      (await phi.get<PageResponse<PhiAccessLogEntry>>('/access-logs', {
        params: { from, to, page, size: 30, ...(patientNo ? { patientNo } : {}) },
      })).data,
    retry: 1,
  });

  if (isPending) return <TableSkeleton columns={6} header />;
  if (isError) {
    // 응답 자체가 없으면 원내에 닿지 못한 것이다. 서버가 답한 오류와 구별해서 말한다.
    if (axios.isAxiosError(error) && !error.response) {
      return (
        <div className="rounded-xl bg-amber-50 p-5 ring-1 ring-amber-200">
          <p className="text-sm font-semibold text-amber-900">원내망에서만 조회됩니다</p>
          <p className="mt-1 text-sm text-amber-800">
            열람 기록은 병원 안에만 남습니다. 기록이 없다는 뜻이 아닙니다.
          </p>
        </div>
      );
    }
    return <LoadFailed error={error} onRetry={() => void refetch()} />;
  }

  return (
    <>
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <p className="text-sm text-slate-500">
          열어본 것과 막힌 시도가 원내에 남습니다. 이 기간 {data.totalElements}건
        </p>
        <form
          className="flex flex-wrap items-center gap-2 text-sm"
          onSubmit={(e) => {
            e.preventDefault();
            setPatientNo(draft.trim(), { page: '' });
          }}
        >
          <PeriodInputs {...props} />
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
                  {PHI_ACTION_LABEL[row.action] ?? row.action}
                  {row.detail && <DetailToggle detail={row.detail} />}
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
                <td className="px-3 py-2.5 font-mono text-xs text-slate-400">{row.ipAddress ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>

        {data.content.length === 0 && (
          <p className="px-4 py-12 text-center text-sm text-slate-500">해당 기간에 기록이 없습니다.</p>
        )}
      </div>

      <Pager page={page} totalPages={data.totalPages} setPage={setPage} />
    </>
  );
}

// ── 업무 기록 (업무 서버) ───────────────────────────────────

function WorkAuditTab(props: PeriodProps) {
  const { from, to, page, setPage } = props;

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['audit-logs', from, to, page],
    queryFn: async () =>
      (await api.get<PageResponse<AuditLogEntry>>('/audit-logs', {
        params: { from, to, page, size: 30 },
      })).data,
  });

  if (isPending) return <TableSkeleton columns={5} header />;
  if (isError) return <LoadFailed error={error} onRetry={() => void refetch()} />;

  return (
    <>
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <p className="text-sm text-slate-500">
          로그인과 기준 정보 변경이 남습니다. 이 기간 {data.totalElements}건
        </p>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <PeriodInputs {...props} />
        </div>
      </div>

      <div className="overflow-x-auto rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
        <table className="w-full min-w-[820px] text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
            <tr>
              <th className="px-3 py-2.5 font-medium">시각</th>
              <th className="px-3 py-2.5 font-medium">행위</th>
              <th className="px-3 py-2.5 font-medium">대상</th>
              <th className="px-3 py-2.5 font-medium">누가</th>
              <th className="px-3 py-2.5 font-medium">접속 주소</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {data.content.map((row) => {
              const failed = row.action === 'LOGIN_FAILED';
              const attempted = typeof row.detail?.loginId === 'string' ? row.detail.loginId : null;
              const reason = typeof row.detail?.reason === 'string' ? row.detail.reason : null;
              const changed = row.detail && 'before' in row.detail ? row.detail : null;
              return (
                <tr key={row.id} className={failed ? 'bg-red-50/60' : ''}>
                  <td className="whitespace-nowrap px-3 py-2.5 tabular-nums text-slate-500">
                    {stamp(row.occurredAt)}
                  </td>
                  <td className="px-3 py-2.5 text-slate-700">
                    {WORK_ACTION_LABEL[row.action] ?? row.action}
                    {failed && reason && (
                      <span className="ml-1.5 rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold text-red-800">
                        {FAILURE_LABEL[reason] ?? reason}
                      </span>
                    )}
                    {changed && <DetailToggle detail={changed} />}
                  </td>
                  <td className="px-3 py-2.5 text-slate-700">
                    {TARGET_LABEL[row.targetType] ?? row.targetType}
                    {row.targetId != null && (
                      <span className="ml-1 font-mono text-xs text-slate-400">#{row.targetId}</span>
                    )}
                    {attempted && (
                      <span className="ml-1.5 font-mono text-xs text-slate-600">{attempted}</span>
                    )}
                  </td>
                  <td className="px-3 py-2.5 text-slate-700">
                    {row.actor ? (
                      <>
                        {row.actor.name}
                        <span className="ml-1 text-xs text-slate-400">{row.actor.departmentName}</span>
                      </>
                    ) : (
                      // 없는 아이디로 시도하면 누구인지 모른다. 모른다고 적는다.
                      <span className="text-slate-400">알 수 없음</span>
                    )}
                  </td>
                  <td className="px-3 py-2.5 font-mono text-xs text-slate-400">{row.ipAddress ?? '-'}</td>
                </tr>
              );
            })}
          </tbody>
        </table>

        {data.content.length === 0 && (
          <p className="px-4 py-12 text-center text-sm text-slate-500">해당 기간에 기록이 없습니다.</p>
        )}
      </div>

      <Pager page={page} totalPages={data.totalPages} setPage={setPage} />
    </>
  );
}

function DetailToggle({ detail }: { detail: Record<string, unknown> }) {
  return (
    <details className="mt-1 text-xs text-slate-500">
      <summary className="cursor-pointer">바뀐 내용</summary>
      <pre className="mt-1 whitespace-pre-wrap break-all font-sans">
        {JSON.stringify(detail, null, 2)}
      </pre>
    </details>
  );
}

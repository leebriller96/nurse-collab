import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, messageOf } from '@/shared/api/client';
import type {
  AlertType, DepartmentSummary, DeptType, ExamType, StaffRole,
} from '@/shared/api/types';

interface DepartmentRow extends DepartmentSummary {
  phone: string | null;
}

interface StaffRow {
  id: number;
  loginId: string;
  employeeNo: string;
  name: string;
  role: StaffRole;
  department: DepartmentSummary;
  phone: string | null;
  active: boolean;
  lastLoginAt: string | null;
}

const DEPT_TYPE_LABEL: Record<DeptType, string> = {
  WARD: '병동', EXAM: '검사실', OR: '수술실', ICU: '중환자실', ER: '응급실', ADMIN: '관리부서',
};

const ROLE_LABEL: Record<StaffRole, string> = {
  NURSE: '간호사', HEAD_NURSE: '수간호사', ADMIN: '관리자',
};

const ALERT_LABEL: Record<AlertType, string> = {
  METAL_IMPLANT: '금속물', CONTRAST_ALLERGY: '조영제', DRUG_ALLERGY: '약물', ISOLATION: '격리',
  FALL_RISK: '낙상', NPO: '금식', OXYGEN: '산소', CLAUSTROPHOBIA: '폐소공포',
};

const TABS = [
  { key: 'departments', label: '부서' },
  { key: 'staff', label: '직원' },
  { key: 'exam-types', label: '검사 종류' },
] as const;

type TabKey = (typeof TABS)[number]['key'];

const field = 'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-sky-500';

/**
 * A-02~04 마스터 관리.
 *
 * 어느 것도 지우지 않는다. 부서나 검사 종류를 삭제하면 그것을 참조하던
 * 지난 요청의 이력을 읽을 수 없게 된다. 기록은 남기고 새로 고르지만 못하게 한다.
 */
export default function MasterAdminPage() {
  const [tab, setTab] = useState<TabKey>('departments');
  const [error, setError] = useState<string | null>(null);
  const queryClient = useQueryClient();

  const departments = useQuery({
    queryKey: ['admin', 'departments'],
    queryFn: async () => (await api.get<DepartmentRow[]>('/departments')).data,
  });
  const staff = useQuery({
    queryKey: ['admin', 'staff'],
    queryFn: async () => (await api.get<StaffRow[]>('/staff')).data,
  });
  const examTypes = useQuery({
    queryKey: ['admin', 'exam-types'],
    queryFn: async () => (await api.get<ExamType[]>('/exam-types')).data,
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin'] });
    void queryClient.invalidateQueries({ queryKey: ['exam-types'] });
  };

  const create = useMutation({
    mutationFn: async ({ path, body }: { path: string; body: unknown }) => {
      await api.post(path, body);
    },
    onSuccess: () => { setError(null); refresh(); },
    onError: (e) => setError(messageOf(e, '저장하지 못했습니다.')),
  });

  const deactivate = useMutation({
    mutationFn: async (path: string) => { await api.patch(path); },
    onSuccess: () => { setError(null); refresh(); },
    onError: (e) => setError(messageOf(e, '처리하지 못했습니다.')),
  });

  return (
    <div className="mx-auto max-w-5xl p-6">
      <h1 className="text-xl font-bold text-slate-900">기준 정보 관리</h1>
      <p className="mt-1 text-sm text-slate-500">
        지우는 대신 사용 중지합니다. 지난 요청 이력이 이 정보를 참조하고 있습니다.
      </p>

      <div className="mt-4 flex gap-2">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => { setTab(t.key); setError(null); }}
            className={`rounded-full px-4 py-1.5 text-sm font-medium ${
              tab === t.key ? 'bg-slate-900 text-white' : 'bg-white text-slate-600 ring-1 ring-slate-200'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && (
        <p className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          {error}
        </p>
      )}

      {tab === 'departments' && (
        <Section
          title="부서"
          rows={departments.data ?? []}
          columns={['코드', '이름', '유형', '내선']}
          renderRow={(d: DepartmentRow) => [d.code, d.name, DEPT_TYPE_LABEL[d.deptType], d.phone ?? '-']}
          onDeactivate={(d: DepartmentRow) => deactivate.mutate(`/departments/${d.id}/deactivate`)}
          form={<DepartmentForm onSubmit={(body) => create.mutate({ path: '/departments', body })} />}
        />
      )}

      {tab === 'staff' && (
        <Section
          title="직원"
          rows={staff.data ?? []}
          columns={['아이디', '사번', '이름', '역할', '소속', '상태']}
          renderRow={(s: StaffRow) => [
            s.loginId, s.employeeNo, s.name, ROLE_LABEL[s.role], s.department.name,
            s.active ? '사용 중' : '중지',
          ]}
          onDeactivate={(s: StaffRow) => deactivate.mutate(`/staff/${s.id}/deactivate`)}
          form={
            <StaffForm
              departments={departments.data ?? []}
              onSubmit={(body) => create.mutate({ path: '/staff', body })}
            />
          }
        />
      )}

      {tab === 'exam-types' && (
        <Section
          title="검사 종류"
          rows={examTypes.data ?? []}
          columns={['코드', '이름', '검사실', '소요', '확인 항목']}
          renderRow={(e: ExamType) => [
            e.code, e.name, e.department.name, `${e.defaultDuration}분`,
            e.requiredAlerts.map((a) => ALERT_LABEL[a]).join(', ') || '없음',
          ]}
          onDeactivate={(e: ExamType) => deactivate.mutate(`/exam-types/${e.id}/deactivate`)}
          form={
            <ExamTypeForm
              departments={(departments.data ?? []).filter((d) => d.deptType === 'EXAM')}
              onSubmit={(body) => create.mutate({ path: '/exam-types', body })}
            />
          }
        />
      )}
    </div>
  );
}

function Section<T extends { id: number }>({
  title, rows, columns, renderRow, onDeactivate, form,
}: {
  title: string;
  rows: T[];
  columns: string[];
  renderRow: (row: T) => (string | number)[];
  onDeactivate: (row: T) => void;
  form: React.ReactNode;
}) {
  return (
    <>
      <section className="mt-4 rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
        <h2 className="mb-3 text-sm font-medium text-slate-600">{title} 추가</h2>
        {form}
      </section>

      <div className="mt-4 overflow-x-auto rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
        <table className="w-full text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
            <tr>
              {columns.map((c) => <th key={c} className="px-3 py-2.5 font-medium">{c}</th>)}
              <th className="px-3 py-2.5" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rows.map((row) => (
              <tr key={row.id}>
                {renderRow(row).map((cell, i) => (
                  <td key={i} className="px-3 py-2.5 text-slate-700">{cell}</td>
                ))}
                <td className="px-3 py-2.5 text-right">
                  <button
                    type="button"
                    onClick={() => onDeactivate(row)}
                    className="text-xs text-slate-400 hover:text-red-600"
                  >
                    사용 중지
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && (
          <p className="px-4 py-10 text-center text-sm text-slate-500">등록된 항목이 없습니다.</p>
        )}
      </div>
    </>
  );
}

function DepartmentForm({ onSubmit }: { onSubmit: (body: unknown) => void }) {
  const [form, setForm] = useState({ code: '', name: '', deptType: 'WARD', location: '', phone: '' });
  const set = (k: string, v: string) => setForm((f) => ({ ...f, [k]: v }));

  return (
    <form
      className="grid grid-cols-5 gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(form);
        setForm({ code: '', name: '', deptType: 'WARD', location: '', phone: '' });
      }}
    >
      <input className={field} placeholder="코드" value={form.code} onChange={(e) => set('code', e.target.value)} />
      <input className={field} placeholder="이름" value={form.name} onChange={(e) => set('name', e.target.value)} />
      <select className={field} value={form.deptType} onChange={(e) => set('deptType', e.target.value)}>
        {(Object.keys(DEPT_TYPE_LABEL) as DeptType[]).map((t) => (
          <option key={t} value={t}>{DEPT_TYPE_LABEL[t]}</option>
        ))}
      </select>
      <input className={field} placeholder="내선" value={form.phone} onChange={(e) => set('phone', e.target.value)} />
      <button type="submit" disabled={!form.code || !form.name}
        className="rounded-lg bg-sky-600 px-4 py-2 text-sm font-semibold text-white disabled:bg-slate-300">
        추가
      </button>
    </form>
  );
}

function StaffForm({ departments, onSubmit }: {
  departments: DepartmentRow[];
  onSubmit: (body: unknown) => void;
}) {
  const empty = { loginId: '', password: '', employeeNo: '', name: '', role: 'NURSE', departmentId: '', phone: '' };
  const [form, setForm] = useState(empty);
  const set = (k: string, v: string) => setForm((f) => ({ ...f, [k]: v }));

  return (
    <form
      className="grid grid-cols-4 gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit({ ...form, departmentId: Number(form.departmentId) });
        setForm(empty);
      }}
    >
      <input className={field} placeholder="아이디" value={form.loginId} onChange={(e) => set('loginId', e.target.value)} />
      <input className={field} type="password" placeholder="초기 비밀번호 (8자 이상)" value={form.password} onChange={(e) => set('password', e.target.value)} />
      <input className={field} placeholder="사번" value={form.employeeNo} onChange={(e) => set('employeeNo', e.target.value)} />
      <input className={field} placeholder="이름" value={form.name} onChange={(e) => set('name', e.target.value)} />
      <select className={field} value={form.role} onChange={(e) => set('role', e.target.value)}>
        {(Object.keys(ROLE_LABEL) as StaffRole[]).map((r) => (
          <option key={r} value={r}>{ROLE_LABEL[r]}</option>
        ))}
      </select>
      <select className={field} value={form.departmentId} onChange={(e) => set('departmentId', e.target.value)}>
        <option value="">소속 선택</option>
        {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
      </select>
      <input className={field} placeholder="내선" value={form.phone} onChange={(e) => set('phone', e.target.value)} />
      <button type="submit" disabled={!form.loginId || !form.name || !form.departmentId || form.password.length < 8}
        className="rounded-lg bg-sky-600 px-4 py-2 text-sm font-semibold text-white disabled:bg-slate-300">
        추가
      </button>
    </form>
  );
}

function ExamTypeForm({ departments, onSubmit }: {
  departments: DepartmentRow[];
  onSubmit: (body: unknown) => void;
}) {
  const empty = { code: '', name: '', departmentId: '', defaultDuration: '30', prepInstruction: '' };
  const [form, setForm] = useState(empty);
  const [alerts, setAlerts] = useState<AlertType[]>([]);
  const set = (k: string, v: string) => setForm((f) => ({ ...f, [k]: v }));

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit({
          ...form,
          departmentId: Number(form.departmentId),
          defaultDuration: Number(form.defaultDuration),
          requiredAlerts: alerts,
        });
        setForm(empty);
        setAlerts([]);
      }}
    >
      <div className="grid grid-cols-5 gap-2">
        <input className={field} placeholder="코드" value={form.code} onChange={(e) => set('code', e.target.value)} />
        <input className={field} placeholder="검사명" value={form.name} onChange={(e) => set('name', e.target.value)} />
        <select className={field} value={form.departmentId} onChange={(e) => set('departmentId', e.target.value)}>
          <option value="">검사실 선택</option>
          {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
        </select>
        <input className={field} inputMode="numeric" placeholder="소요(분)" value={form.defaultDuration} onChange={(e) => set('defaultDuration', e.target.value)} />
        <input className={field} placeholder="사전 준비사항" value={form.prepInstruction} onChange={(e) => set('prepInstruction', e.target.value)} />
      </div>

      <div className="mt-3">
        <p className="mb-1.5 text-xs text-slate-500">
          검사 전 확인 항목 — 여기서 고른 것이 검사실 화면의 경고가 됩니다.
        </p>
        <div className="flex flex-wrap gap-1.5">
          {(Object.keys(ALERT_LABEL) as AlertType[]).map((a) => (
            <button
              key={a}
              type="button"
              onClick={() => setAlerts((prev) => prev.includes(a) ? prev.filter((x) => x !== a) : [...prev, a])}
              className={`rounded-full px-3 py-1 text-xs font-medium ring-1 ${
                alerts.includes(a) ? 'bg-red-600 text-white ring-transparent' : 'bg-white text-slate-600 ring-slate-200'
              }`}
            >
              {ALERT_LABEL[a]}
            </button>
          ))}
        </div>
      </div>

      <button type="submit" disabled={!form.code || !form.name || !form.departmentId}
        className="mt-3 rounded-lg bg-sky-600 px-5 py-2 text-sm font-semibold text-white disabled:bg-slate-300">
        추가
      </button>
    </form>
  );
}

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { messageOf } from '@/shared/api/client';
import { useAuth } from '@/shared/hooks/useAuth';

/** 데모용. 실제 병원 배포에서는 없어져야 하는 블록이다. */
const DEMO_ACCOUNTS = [
  { loginId: 'ward01', label: '3병동 김간호' },
  { loginId: 'mri01', label: 'MRI실 박간호' },
  { loginId: 'ward02', label: '5병동 이간호' },
  { loginId: 'ct01', label: 'CT실 최간호' },
  { loginId: 'head01', label: '3병동 정수간호' },
  { loginId: 'admin01', label: '관리자' },
];

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (id: string, pw: string) => {
    setError(null);
    setSubmitting(true);
    try {
      const staff = await login(id, pw);
      // 소속 파트 유형으로 홈 화면이 갈린다
      const home =
        staff.department.deptType === 'ADMIN'
          ? '/admin/stats'
          : staff.department.deptType === 'EXAM'
            ? '/exam/queue'
            : '/ward/board';
      navigate(home, { replace: true });
    } catch (e) {
      setError(messageOf(e, '로그인에 실패했습니다.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-full items-center justify-center p-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-bold text-slate-900">간호 협업 시스템</h1>
          <p className="mt-1 text-sm text-slate-500">병동 · 검사실 이송 요청</p>
        </div>

        <form
          className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200"
          onSubmit={(e) => {
            e.preventDefault();
            void submit(loginId, password);
          }}
        >
          <label className="block text-sm font-medium text-slate-700" htmlFor="loginId">
            아이디
          </label>
          <input
            id="loginId"
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-base outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-200"
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
            autoComplete="username"
            autoCapitalize="none"
          />

          <label className="mt-4 block text-sm font-medium text-slate-700" htmlFor="password">
            비밀번호
          </label>
          <input
            id="password"
            type="password"
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-base outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-200"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />

          {error && (
            <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting || !loginId || !password}
            className="mt-5 w-full rounded-lg bg-sky-600 py-3 text-base font-semibold text-white disabled:bg-slate-300"
          >
            {submitting ? '로그인 중…' : '로그인'}
          </button>
        </form>

        <div className="mt-6 rounded-xl bg-white/70 p-4 ring-1 ring-slate-200">
          <p className="mb-2 text-xs font-medium text-slate-500">데모 계정 (비밀번호 nurse1234!)</p>
          <div className="grid grid-cols-2 gap-2">
            {DEMO_ACCOUNTS.map((account) => (
              <button
                key={account.loginId}
                type="button"
                disabled={submitting}
                onClick={() => {
                  setLoginId(account.loginId);
                  setPassword('nurse1234!');
                  void submit(account.loginId, 'nurse1234!');
                }}
                className="rounded-lg border border-slate-200 bg-white px-2 py-2 text-left text-xs hover:bg-slate-50 disabled:opacity-50"
              >
                <span className="block font-semibold text-slate-800">{account.label}</span>
                <span className="text-slate-400">{account.loginId}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

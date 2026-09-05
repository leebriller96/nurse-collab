import type { CSSProperties } from 'react';
import { Outlet } from 'react-router-dom';
import { useAuth } from '@/shared/hooks/useAuth';

/** 관리자 화면. 데스크톱에서 본다. */
export default function AdminLayout() {
  const { staff, logout } = useAuth();

  return (
    <div className="flex min-h-full flex-col" style={{ '--app-bottom-bar': '0px' } as CSSProperties}>
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
        <span className="font-semibold text-slate-900">간호 협업 시스템 · 관리</span>
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-600">
            {staff?.name} <span className="text-slate-400">· {staff?.department.name}</span>
          </span>
          <button
            type="button"
            onClick={() => void logout()}
            className="rounded-lg px-3 py-1.5 text-sm text-slate-500 hover:bg-slate-100"
          >
            로그아웃
          </button>
        </div>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}

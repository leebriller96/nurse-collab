import type { CSSProperties } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '@/shared/hooks/useAuth';
import { useUnreadCount } from '@/shared/hooks/useUnreadCount';
import RealtimeToasts from '@/shared/ui/RealtimeToasts';

/** 검사실은 PC 우선. 앉아서 여러 건을 동시에 본다. */
export default function ExamLayout() {
  const { staff, logout } = useAuth();
  const unread = useUnreadCount(!!staff);

  return (
    <div className="flex min-h-full flex-col" style={{ '--app-bottom-bar': '0px' } as CSSProperties}>
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
        <div className="flex items-center gap-5">
          <span className="font-semibold text-slate-900">간호 협업 시스템</span>
          <nav className="flex gap-4 text-sm">
            {[{ to: '/exam/queue', label: '들어온 요청' }, { to: '/exam/schedule', label: '일정' }, { to: '/exam/history', label: '지난 요청' }].map((t) => (
              <NavLink
                key={t.to}
                to={t.to}
                className={({ isActive }) =>
                  isActive ? 'font-semibold text-sky-600' : 'text-slate-500 hover:text-slate-700'
                }
              >
                {t.label}
              </NavLink>
            ))}
          </nav>
        </div>
        <div className="flex items-center gap-3">
          <NavLink
            to="/exam/notifications"
            className={({ isActive }) =>
              `flex items-center gap-1 text-sm ${isActive ? 'font-semibold text-sky-600' : 'text-slate-500 hover:text-slate-700'}`
            }
          >
            알림
            {unread > 0 && (
              <span className="rounded-full bg-red-500 px-1.5 py-0.5 text-[10px] font-bold text-white">
                {unread > 99 ? '99+' : unread}
              </span>
            )}
          </NavLink>
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

      <RealtimeToasts />

      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}

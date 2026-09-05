import type { CSSProperties } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '@/shared/hooks/useAuth';
import RealtimeToasts from '@/shared/ui/RealtimeToasts';

const TABS = [
  { to: '/ward/board', label: '환자' },
  { to: '/ward/requests', label: '요청' },
];

/** 병동은 모바일 우선. 한 손으로, 이동 중에, 짧게 쓴다. */
export default function WardLayout() {
  const { staff, logout } = useAuth();
  // 일반 간호사에게는 통계 탭 자체를 보여주지 않는다
  const tabs = staff?.role === 'NURSE'
    ? TABS
    : [...TABS, { to: '/admin/stats', label: '통계' }];

  return (
    // 화면마다 하단 고정 버튼이 있는데 탭바와 겹친다.
    // 탭바 높이를 변수로 내려보내 버튼이 그 위에 앉게 한다.
    <div
      className="mx-auto flex min-h-full max-w-md flex-col"
      style={{ '--app-bottom-bar': '76px' } as CSSProperties}
    >
      <RealtimeToasts />

      <main className="flex-1">
        <Outlet />
      </main>

      <nav className="sticky bottom-0 z-20 h-[76px] border-t border-slate-200 bg-white">
        <div className="flex">
          {tabs.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              className={({ isActive }) =>
                `flex-1 py-3 text-center text-sm font-semibold ${
                  isActive ? 'text-sky-600' : 'text-slate-400'
                }`
              }
            >
              {tab.label}
            </NavLink>
          ))}
        </div>
        <div className="flex items-center justify-between border-t border-slate-100 px-4 py-2">
          <span className="text-xs text-slate-500">
            {staff?.name} · {staff?.department.name}
          </span>
          <button
            type="button"
            onClick={() => void logout()}
            className="text-xs text-slate-400 hover:text-slate-600"
          >
            로그아웃
          </button>
        </div>
      </nav>
    </div>
  );
}

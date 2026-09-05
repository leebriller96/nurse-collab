import { Outlet } from 'react-router-dom';
import { useAuth } from '@/shared/hooks/useAuth';

/** 병동은 모바일 우선. 한 손으로, 이동 중에, 짧게 쓴다. */
export default function WardLayout() {
  const { staff, logout } = useAuth();

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col">
      <main className="flex-1">
        <Outlet />
      </main>

      <nav className="sticky bottom-0 flex items-center justify-between border-t border-slate-200 bg-white px-4 py-3">
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
      </nav>
    </div>
  );
}

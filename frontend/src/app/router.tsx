import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import LoginPage from '@/features/auth/LoginPage';
import WardBoardPage from '@/features/encounter/WardBoardPage';
import ExamQueuePage from '@/features/transfer/ExamQueuePage';
import WardLayout from '@/layouts/WardLayout';
import ExamLayout from '@/layouts/ExamLayout';
import { useAuth } from '@/shared/hooks/useAuth';

function RequireAuth() {
  const { staff, loading } = useAuth();
  if (loading) {
    return <p className="p-6 text-sm text-slate-500">확인 중…</p>;
  }
  return staff ? <Outlet /> : <Navigate to="/login" replace />;
}

/** 로그인한 사람의 소속 파트 유형이 홈을 정한다 */
function Home() {
  const { staff, loading } = useAuth();
  if (loading) return null;
  if (!staff) return <Navigate to="/login" replace />;
  return <Navigate to={staff.department.deptType === 'EXAM' ? '/exam/queue' : '/ward/board'} replace />;
}

export default function Router() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route path="/" element={<Home />} />
        <Route path="/ward" element={<WardLayout />}>
          <Route path="board" element={<WardBoardPage />} />
        </Route>
        <Route path="/exam" element={<ExamLayout />}>
          <Route path="queue" element={<ExamQueuePage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

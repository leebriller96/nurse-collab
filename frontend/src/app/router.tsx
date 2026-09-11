import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import LoginPage from '@/features/auth/LoginPage';
import WardBoardPage from '@/features/encounter/WardBoardPage';
import EncounterDetailPage from '@/features/encounter/EncounterDetailPage';
import ServiceQueuePage from '@/features/workorder/ServiceQueuePage';
import WardRequestsPage from '@/features/workorder/WardRequestsPage';
import OrderCreatePage from '@/features/workorder/OrderCreatePage';
import OrderDetailPage from '@/features/workorder/OrderDetailPage';
import WardLayout from '@/layouts/WardLayout';
import ServiceLayout from '@/layouts/ServiceLayout';
import AdminLayout from '@/layouts/AdminLayout';
import StatsPage from '@/features/stats/StatsPage';
import VitalSignPage from '@/features/nursing/VitalSignPage';
import NursingNotePage from '@/features/nursing/NursingNotePage';
import AuditLogPage from '@/features/audit/AuditLogPage';
import NotificationPage from '@/features/notification/NotificationPage';
import OrderHistoryPage from '@/features/workorder/OrderHistoryPage';
import MasterAdminPage from '@/features/master/MasterAdminPage';
import ServiceSchedulePage from '@/features/workorder/ServiceSchedulePage';
import { useAuth } from '@/shared/hooks/useAuth';

function RequireAuth() {
  const { staff, loading } = useAuth();
  if (loading) {
    return <p className="p-6 text-sm text-slate-500">확인 중…</p>;
  }
  return staff ? <Outlet /> : <Navigate to="/login" replace />;
}

/**
 * 로그인한 사람의 소속 파트 유형이 홈을 정한다.
 *
 * 요청하는 쪽은 병동뿐이고 나머지는 전부 수행하는 쪽이다.
 * 수행 파트를 하나씩 나열하면 부서 유형을 늘릴 때마다 여기를 고쳐야 하고,
 * 빠뜨리면 그 부서 사람만 로그인 후 빈 화면을 보게 된다.
 */
function Home() {
  const { staff, loading } = useAuth();
  if (loading) return null;
  if (!staff) return <Navigate to="/login" replace />;
  if (staff.department.deptType === 'ADMIN') return <Navigate to="/admin/stats" replace />;
  return <Navigate to={staff.department.deptType === 'WARD' ? '/ward/board' : '/service/queue'} replace />;
}

export default function Router() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route path="/" element={<Home />} />

        <Route path="/ward" element={<WardLayout />}>
          <Route path="board" element={<WardBoardPage />} />
          <Route path="encounters/:id" element={<EncounterDetailPage />} />
          <Route path="encounters/:id/vitals" element={<VitalSignPage />} />
          <Route path="encounters/:id/notes" element={<NursingNotePage />} />
          <Route path="requests" element={<WardRequestsPage />} />
          <Route path="requests/new" element={<OrderCreatePage />} />
          <Route path="requests/:id" element={<OrderDetailPage />} />
          <Route path="notifications" element={<NotificationPage />} />
          <Route path="history" element={<OrderHistoryPage />} />
        </Route>

        {/* 통계는 수간호사 이상만 볼 수 있다. 서버가 403 으로 막지만
            병동 레이아웃에서도 역할에 따라 탭 자체를 숨긴다. */}
        <Route path="/admin" element={<AdminLayout />}>
          <Route path="stats" element={<StatsPage />} />
          <Route path="audit-logs" element={<AuditLogPage />} />
          <Route path="master" element={<MasterAdminPage />} />
        </Route>

        <Route path="/service" element={<ServiceLayout />}>
          <Route path="queue" element={<ServiceQueuePage />} />
          <Route path="schedule" element={<ServiceSchedulePage />} />
          <Route path="requests/:id" element={<OrderDetailPage />} />
          <Route path="notifications" element={<NotificationPage />} />
          <Route path="history" element={<OrderHistoryPage />} />
          <Route path="stats" element={<StatsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

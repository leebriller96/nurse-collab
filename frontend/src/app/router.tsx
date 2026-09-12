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
import { homeFor } from '@/shared/lib/home';

function RequireAuth() {
  const { staff, loading } = useAuth();
  if (loading) {
    return <p className="p-6 text-sm text-slate-500">확인 중…</p>;
  }
  return staff ? <Outlet /> : <Navigate to="/login" replace />;
}

/** 로그인한 사람의 소속 파트 유형이 홈을 정한다. 판정은 homeFor 한 곳에만 있다. */
function Home() {
  const { staff, loading } = useAuth();
  if (loading) return null;
  if (!staff) return <Navigate to="/login" replace />;
  return <Navigate to={homeFor(staff.department.deptType)} replace />;
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

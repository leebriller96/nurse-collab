import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import Router from '@/app/router';
import { AuthProvider } from '@/shared/hooks/useAuth';
import ErrorBoundary from '@/shared/ui/ErrorBoundary';
import OfflineBanner from '@/shared/ui/OfflineBanner';
import { ToastProvider } from '@/shared/ui/toast';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 병원 와이파이는 자주 끊긴다. 창으로 돌아오면 다시 받아온다.
      refetchOnWindowFocus: true,
      // 끊겼다 붙으면 손대지 않아도 최신값으로 맞춘다.
      // 오프라인 배너를 내리면서 화면은 낡은 값인 상태를 없애기 위해서다.
      refetchOnReconnect: true,
      retry: 1,
      staleTime: 5_000,
    },
  },
});

export default function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            {/* 실시간 알림과 내 동작 확인이 같은 자리에 뜬다. 한 곳에서 그려야 겹치지 않는다. */}
            <ToastProvider>
              {/* 로그인 전에도 보여야 한다. 끊긴 채로 로그인 버튼을 누르는 일이 많다. */}
              <OfflineBanner />
              <Router />
            </ToastProvider>
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}

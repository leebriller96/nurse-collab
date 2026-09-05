import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import Router from '@/app/router';
import { AuthProvider } from '@/shared/hooks/useAuth';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 병원 와이파이는 자주 끊긴다. 창으로 돌아오면 다시 받아온다.
      refetchOnWindowFocus: true,
      retry: 1,
      staleTime: 5_000,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Router />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

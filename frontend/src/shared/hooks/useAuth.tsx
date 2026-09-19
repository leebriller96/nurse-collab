import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api, tokenStore } from '@/shared/api/client';
import type { LoginResponse, Staff } from '@/shared/api/types';
import { releasePushOnLogout } from '@/shared/lib/push';

interface AuthValue {
  staff: Staff | null;
  loading: boolean;
  login: (loginId: string, password: string) => Promise<Staff>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [staff, setStaff] = useState<Staff | null>(null);
  // 토큰이 없으면 처음부터 로딩이 아니다. effect 에서 다시 내리면 한 번 더 그린다.
  const [loading, setLoading] = useState(() => tokenStore.access() !== null);

  // 새로고침해도 로그인이 유지되어야 한다. 토큰이 남아 있으면 내 정보를 다시 받아온다.
  useEffect(() => {
    if (!tokenStore.access()) return;
    api
      .get<Staff>('/auth/me')
      .then((res) => setStaff(res.data))
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (loginId: string, password: string) => {
    const { data } = await api.post<LoginResponse>('/auth/login', { loginId, password });
    tokenStore.save(data.accessToken, data.refreshToken);
    setStaff(data.staff);
    return data.staff;
  }, []);

  const logout = useCallback(async () => {
    // 토큰이 살아 있을 때 이 기기의 폰 알림부터 뺀다. 병동 폰은 돌려 쓴다.
    await releasePushOnLogout();
    try {
      await api.post('/auth/logout');
    } finally {
      // 서버 호출이 실패해도 이 기기에서는 반드시 로그아웃되어야 한다
      tokenStore.clear();
      setStaff(null);
    }
  }, []);

  const value = useMemo(() => ({ staff, loading, login, logout }), [staff, loading, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// 컨텍스트와 그것을 읽는 훅은 한 파일에 둔다. 갈라 두면 컨텍스트를 export 해야 하고,
// 그러면 Provider 를 거치지 않고 읽는 길이 생긴다. 잃는 것은 이 파일의 빠른 새로고침뿐이다.
// oxlint-disable-next-line react/only-export-components
export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('AuthProvider 안에서만 쓸 수 있습니다.');
  }
  return value;
}

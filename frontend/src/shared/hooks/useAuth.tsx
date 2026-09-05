import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api, tokenStore } from '@/shared/api/client';
import type { LoginResponse, Staff } from '@/shared/api/types';

interface AuthValue {
  staff: Staff | null;
  loading: boolean;
  login: (loginId: string, password: string) => Promise<Staff>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [staff, setStaff] = useState<Staff | null>(null);
  const [loading, setLoading] = useState(true);

  // 새로고침해도 로그인이 유지되어야 한다. 토큰이 남아 있으면 내 정보를 다시 받아온다.
  useEffect(() => {
    if (!tokenStore.access()) {
      setLoading(false);
      return;
    }
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

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('AuthProvider 안에서만 쓸 수 있습니다.');
  }
  return value;
}

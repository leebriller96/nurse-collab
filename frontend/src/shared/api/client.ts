import axios, { AxiosError } from 'axios';
import type { ApiError, LoginResponse } from './types';

const ACCESS_TOKEN_KEY = 'nc.accessToken';
const REFRESH_TOKEN_KEY = 'nc.refreshToken';

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_TOKEN_KEY),
  refresh: () => localStorage.getItem(REFRESH_TOKEN_KEY),
  save(accessToken: string, refreshToken: string) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};

export const api = axios.create({ baseURL: '/api/v1' });

api.interceptors.request.use((config) => {
  const token = tokenStore.access();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * 갱신이 진행 중일 때 들어온 요청들이 각자 갱신을 호출하면
 * 토큰이 여러 번 회전해서 마지막 하나만 살아남는다. 한 번만 부르고 나머지는 기다린다.
 */
let refreshing: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStore.refresh();
  if (!refreshToken) {
    throw new Error('갱신 토큰이 없습니다.');
  }
  const { data } = await axios.post<LoginResponse>('/api/v1/auth/refresh', { refreshToken });
  tokenStore.save(data.accessToken, data.refreshToken);
  return data.accessToken;
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const original = error.config;
    const isExpired = error.response?.status === 401 && error.response.data?.code === 'AUTH-002';
    const alreadyRetried = (original as { _retried?: boolean } | undefined)?._retried;

    // 로그인/갱신 자체가 401 인 경우까지 다시 시도하면 무한 루프가 된다
    const isAuthCall = original?.url?.includes('/auth/login') || original?.url?.includes('/auth/refresh');

    if (isExpired && original && !alreadyRetried && !isAuthCall) {
      try {
        refreshing = refreshing ?? refreshAccessToken();
        const token = await refreshing;
        refreshing = null;

        (original as { _retried?: boolean })._retried = true;
        original.headers = original.headers ?? {};
        original.headers.Authorization = `Bearer ${token}`;
        return api.request(original);
      } catch {
        refreshing = null;
        tokenStore.clear();
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  },
);

/** 서버가 준 한국어 문장을 그대로 쓴다. 프론트가 에러코드별 문구를 따로 관리하지 않는다. */
export function messageOf(error: unknown, fallback = '알 수 없는 오류가 발생했습니다.'): string {
  if (axios.isAxiosError<ApiError>(error)) {
    return error.response?.data?.message ?? fallback;
  }
  return fallback;
}

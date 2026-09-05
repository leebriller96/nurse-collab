import { useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import type { NotificationsResponse } from '@/shared/api/types';

/** 뱃지용 미읽음 수. 실시간 알림이 오면 캐시가 무효화돼 다시 받아온다. */
export function useUnreadCount(enabled: boolean) {
  const { data } = useQuery({
    queryKey: ['notification-count'],
    queryFn: async () =>
      (await api.get<NotificationsResponse>('/notifications',
        { params: { unreadOnly: true, page: 0, size: 1 } })).data.unreadCount,
    enabled,
    refetchInterval: 60_000,
  });
  return data ?? 0;
}

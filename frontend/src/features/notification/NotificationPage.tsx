import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, messageOf } from '@/shared/api/client';
import { useAuth } from '@/shared/hooks/useAuth';
import type { NotificationsResponse } from '@/shared/api/types';

const ago = (iso: string) => {
  const minutes = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return '방금';
  if (minutes < 60) return `${minutes}분 전`;
  if (minutes < 24 * 60) return `${Math.floor(minutes / 60)}시간 전`;
  return new Date(iso).toLocaleDateString('ko-KR', { month: 'numeric', day: 'numeric' });
};

/**
 * C-02 알림함.
 *
 * 실시간 알림은 그 순간 화면을 보고 있어야 닿는다.
 * 병동 간호사는 폰을 주머니에 넣고 다니므로 놓친 것을 나중에 확인할 곳이 필요하다.
 */
export default function NotificationPage() {
  const { staff } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [unreadOnly, setUnreadOnly] = useState(false);

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['notifications', unreadOnly],
    queryFn: async () =>
      (await api.get<NotificationsResponse>('/notifications',
        { params: { unreadOnly, page: 0, size: 50 } })).data,
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    void queryClient.invalidateQueries({ queryKey: ['notification-count'] });
  };

  const readAll = useMutation({
    mutationFn: async () => { await api.post('/notifications/read-all'); },
    onSuccess: refresh,
  });

  const open = useMutation({
    mutationFn: async (id: number) => { await api.patch(`/notifications/${id}/read`); },
    onSuccess: refresh,
  });

  if (isPending) return <p className="p-4 text-sm text-slate-500">불러오는 중…</p>;
  if (isError) return <p className="p-4 text-sm text-red-600">{messageOf(error)}</p>;

  const detailPath = staff?.department.deptType === 'EXAM' ? '/exam/requests' : '/ward/requests';

  return (
    <div className="pb-24">
      <div className="sticky top-0 z-10 bg-slate-100/95 px-4 py-3 backdrop-blur">
        <div className="flex items-baseline justify-between">
          <h1 className="text-lg font-bold text-slate-900">알림</h1>
          {data.unreadCount > 0 && (
            <button
              type="button"
              onClick={() => readAll.mutate()}
              className="text-sm text-sky-600"
            >
              모두 읽음
            </button>
          )}
        </div>
        <div className="mt-2 flex gap-2">
          {[
            { value: false, label: '전체' },
            { value: true, label: `안 읽음 ${data.unreadCount}` },
          ].map((tab) => (
            <button
              key={String(tab.value)}
              type="button"
              onClick={() => setUnreadOnly(tab.value)}
              className={`rounded-full px-3 py-1 text-sm font-medium ${
                unreadOnly === tab.value ? 'bg-slate-900 text-white' : 'bg-white text-slate-600'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      <ul className="space-y-2 px-3">
        {data.page.content.map((noti) => (
          <li key={noti.id}>
            <button
              type="button"
              onClick={() => {
                if (!noti.readAt) open.mutate(noti.id);
                navigate(`${detailPath}/${noti.refId}`);
              }}
              className={`w-full rounded-xl p-3.5 text-left shadow-sm ring-1 ${
                noti.readAt ? 'bg-white ring-slate-200' : 'bg-sky-50 ring-sky-200'
              }`}
            >
              <div className="flex items-baseline gap-2">
                {!noti.readAt && <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-sky-500" />}
                <span className="font-semibold text-slate-900">{noti.title}</span>
                <span className="ml-auto shrink-0 text-xs text-slate-400">{ago(noti.createdAt)}</span>
              </div>
              {noti.body && <p className="mt-1 text-sm text-slate-600">{noti.body}</p>}
            </button>
          </li>
        ))}
      </ul>

      {data.page.content.length === 0 && (
        <p className="px-4 py-10 text-center text-sm text-slate-500">
          {unreadOnly ? '안 읽은 알림이 없습니다.' : '알림이 없습니다.'}
        </p>
      )}
    </div>
  );
}

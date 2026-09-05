import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { useQueryClient } from '@tanstack/react-query';
import { tokenStore } from '@/shared/api/client';
import type { TransferPriority, TransferStatus } from '@/shared/api/types';

export interface RealtimeEvent {
  eventType: 'TRANSFER_CREATED' | 'TRANSFER_STATUS_CHANGED' | 'MESSAGE_CREATED';
  requestId: number;
  requestNo: string;
  fromStatus: TransferStatus | null;
  toStatus: TransferStatus | null;
  priority: TransferPriority;
  patientName: string;
  roomNo: string;
  examName: string;
  actorName: string;
  actorDepartmentName: string;
  occurredAt: string;
}

function wsUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${protocol}://${window.location.host}/ws`;
}

/**
 * 파트 채널을 구독해 화면을 최신으로 유지한다.
 *
 * 받은 메시지로 화면을 직접 고치지 않는다. 조회 캐시를 무효화해 서버에서 다시 받아온다.
 * 이렇게 하면 메시지를 놓쳐도, 순서가 뒤바뀌어도 화면은 서버 상태와 일치한다.
 * 실시간은 "빠른 갱신"일 뿐이고 진실의 원천은 REST 조회다.
 */
export function useRealtime(departmentId: number | undefined, onEvent?: (e: RealtimeEvent) => void) {
  const queryClient = useQueryClient();
  const [connected, setConnected] = useState(false);
  const handlerRef = useRef(onEvent);
  handlerRef.current = onEvent;

  useEffect(() => {
    if (!departmentId) return;

    const client = new Client({
      brokerURL: wsUrl(),
      // 브라우저 WebSocket 은 핸드셰이크에 헤더를 넣을 수 없다. CONNECT 프레임으로 보낸다.
      connectHeaders: { Authorization: `Bearer ${tokenStore.access() ?? ''}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        setConnected(true);
        // 끊겨 있던 동안 놓친 변화가 있을 수 있다. 재연결 직후 전부 다시 받아온다.
        void queryClient.invalidateQueries();

        client.subscribe(`/topic/department/${departmentId}`, (frame) => {
          const event = JSON.parse(frame.body) as RealtimeEvent;

          void queryClient.invalidateQueries({ queryKey: ['transfer-requests'] });
          void queryClient.invalidateQueries({ queryKey: ['transfer-request', event.requestId] });
          void queryClient.invalidateQueries({ queryKey: ['encounters'] });
          void queryClient.invalidateQueries({ queryKey: ['notifications'] });
          void queryClient.invalidateQueries({ queryKey: ['notification-count'] });

          handlerRef.current?.(event);
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
    });

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [departmentId, queryClient]);

  return { connected };
}

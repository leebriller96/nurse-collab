import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { useQueryClient } from '@tanstack/react-query';
import { freshAccessToken } from '@/shared/api/client';
import type { OrderPriority, OrderStatus } from '@/shared/api/types';

export interface RealtimeEvent {
  eventType: 'ORDER_CREATED' | 'ORDER_STATUS_CHANGED' | 'MESSAGE_CREATED' | 'ORDER_DELAYED';
  requestId: number;
  requestNo: string;
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus | null;
  /** 종류가 부르는 이름(검사중 / 조제중 / 수리중). toStatus 가 없으면 같이 없다 */
  toStatusLabel: string | null;
  priority: OrderPriority;
  /**
   * 대상 재원 건의 가명. 환자가 없는 업무에서는 비어 온다.
   * 이름은 실시간 방송에도 싣지 않는다 — 이 채널을 구독하는 브라우저가
   * 원내망 밖에 있을 수도 있다.
   */
  subjectRef: string | null;
  roomNo: string | null;
  itemName: string;
  /** 접수 지연(ORDER_DELAYED)은 서버가 보내므로 행위자 칸이 비어 온다 */
  actorId: number | null;
  actorName: string | null;
  actorDepartmentName: string | null;
  /** 접수 지연일 때만. 몇 분째 기다렸나 */
  waitingMinutes: number | null;
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
  // 렌더 중에 ref 를 쓰지 않는다. 커밋된 뒤에 최신 핸들러로 바꿔 둔다.
  const handlerRef = useRef(onEvent);
  useEffect(() => {
    handlerRef.current = onEvent;
  });

  useEffect(() => {
    if (!departmentId) return;

    const client = new Client({
      brokerURL: wsUrl(),
      // 브라우저 WebSocket 은 핸드셰이크에 헤더를 넣을 수 없다. CONNECT 프레임으로 보낸다.
      // 붙을 때마다 토큰을 다시 읽는다. 처음 한 번만 박아 두면 30분 뒤 와이파이가 한 번
      // 끊긴 순간부터 만료된 토큰으로만 재연결을 시도하고, 서버는 그걸 전부 거절한다.
      beforeConnect: async () => {
        client.connectHeaders = { Authorization: `Bearer ${(await freshAccessToken()) ?? ''}` };
      },
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
          // 병동 보드의 침대별 요청 수, 환자 상세의 요청 목록, 검사실 일정 보드
          void queryClient.invalidateQueries({ queryKey: ['care-episodes'] });
          void queryClient.invalidateQueries({ queryKey: ['care-episode'] });
          void queryClient.invalidateQueries({ queryKey: ['exam-schedule'] });
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

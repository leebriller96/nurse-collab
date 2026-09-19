import type { OrderPriority } from '@/shared/api/types';

/**
 * 우선순위 이름. 종류와 무관하게 셋뿐이라 화면이 든다.
 * 상태 이름은 여기 없다 — 종류마다 달라서 서버가 응답마다 실어 준다.
 */
export const PRIORITY_LABEL: Record<OrderPriority, string> = {
  ROUTINE: '일반',
  URGENT: '긴급',
  EMERGENCY: '응급',
};

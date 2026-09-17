import type { DeptType } from '@/shared/api/types';

/**
 * 로그인한 사람이 처음 볼 화면.
 *
 * 이 판정이 두 군데 있었다. 로그인 직후에 한 번, 주소창으로 "/" 를 열었을 때 한 번.
 * 부서 유형을 늘렸을 때 한쪽만 고쳐졌고, 의공학팀 계정은 로그인하면
 * 자기 일이 하나도 없는 병동 보드로 떨어졌다. 그래서 한 곳으로 합쳤다.
 *
 * 요청하는 쪽은 병동뿐이고 나머지는 전부 수행하는 쪽이다.
 * 수행 파트를 하나씩 나열하면 부서 유형을 늘릴 때마다 여기를 고쳐야 하고,
 * 빠뜨리면 그 부서 사람만 엉뚱한 화면을 보게 된다.
 */
export function homeFor(deptType: DeptType): string {
  if (deptType === 'ADMIN') return '/admin/stats';
  return deptType === 'WARD' ? '/ward/board' : '/service/queue';
}

/**
 * 요청 한 건의 상세 화면. 소속에 따라 병동 화면이냐 수행 파트 화면이냐가 갈린다.
 *
 * 알림함이 `deptType === 'EXAM'` 으로 따로 판정하고 있었다. 검사실만 수행 파트였던 때의 흔적이라
 * 진단검사의학과·약제부·의공학팀은 알림을 누르면 병동 화면으로 갔다. 홈과 같은 기준을 쓴다.
 */
export function orderPathFor(deptType: DeptType, orderId: number | string): string {
  return deptType === 'WARD' ? `/ward/requests/${orderId}` : `/service/requests/${orderId}`;
}

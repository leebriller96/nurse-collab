import { useOnline } from '@/shared/hooks/useOnline';

/**
 * 연결이 끊겼을 때 화면 맨 위에 띄운다.
 *
 * 구석의 작은 점(실시간 표시)으로는 부족하다. 그건 "실시간 알림이 오고 있나" 를
 * 보여주는 것이고, 이건 "지금 누른 게 저장이 안 된다" 는 뜻이라 무게가 다르다.
 * 끊긴 줄 모르고 요청을 등록한 뒤 검사실이 받았으려니 하고 넘어가는 것이 제일 위험하다.
 *
 * 캐시해 둔 값을 대신 보여주지 않는 이유는 지난 활력징후나 낡은 이송 상태가
 * 지금 값처럼 보이면 안 되기 때문이다. 모르는 것보다 틀리게 아는 것이 나쁘다.
 */
export default function OfflineBanner() {
  const online = useOnline();

  if (online) return null;

  return (
    // fixed 가 아니라 sticky 다. fixed 면 검사실·관리자 화면의 상단 메뉴를 덮어
    // 끊긴 동안 로그아웃도 탭 이동도 못 하게 된다. sticky 는 자리를 차지해 아래를
    // 밀어내면서, 스크롤을 내려도 계속 보인다.
    <div
      role="alert"
      className="sticky top-0 z-[60] bg-amber-500 px-4 py-2 text-center text-sm font-semibold text-white shadow-md"
    >
      연결이 끊겼습니다 · 지금은 저장되지 않습니다
    </div>
  );
}

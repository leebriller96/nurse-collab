import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * 목록 맨 위에서 아래로 당기면 다시 불러온다.
 *
 * 실시간과 60초 폴링이 있어도 사람은 "지금 다시 받아오고 싶다" 고 느낀다.
 * 특히 앱으로 설치하면 브라우저의 당겨서 새로고침이 사라져서 방법이 아예 없어진다.
 *
 * 손가락으로만 동작한다. 마우스에는 새로고침(F5)이 이미 있고,
 * 마우스까지 받으면 드래그로 글자를 고르는 것과 부딪힌다.
 *
 * 맨 위(scrollY 0)에서 시작한 것만 센다. 목록 중간에서 아래로 쓸어내리는 것은
 * 그냥 스크롤이지 새로고침 요청이 아니다.
 */

/** 이만큼 당겨야 새로고침으로 친다. 너무 짧으면 스크롤하다 실수로 걸린다. */
const THRESHOLD_PX = 70;

/** 당긴 만큼 그대로 따라오면 화면이 과하게 늘어난다. 저항을 준다. */
const RESISTANCE = 0.4;

export function usePullToRefresh(onRefresh: () => Promise<unknown> | unknown) {
  const [pulled, setPulled] = useState(0);
  const [refreshing, setRefreshing] = useState(false);

  /**
   * 당긴 거리와 새로고침 여부를 ref 로도 들고 있는다.
   *
   * 상태만 쓰면 손가락이 움직일 때마다 effect 가 다시 돌아 리스너를 떼었다 붙인다.
   * 한 번 당기는 동안 수십 번이다. 게다가 touchend 가 읽는 값이 그리기용 상태라
   * 아직 반영되지 않은 값을 보고 "덜 당겼다" 고 판단하는 일이 생긴다.
   */
  const pulledRef = useRef(0);
  const refreshingRef = useRef(false);
  const onRefreshRef = useRef(onRefresh);
  onRefreshRef.current = onRefresh;

  const setDistance = useCallback((next: number) => {
    pulledRef.current = next;
    setPulled(next);
  }, []);

  useEffect(() => {
    let startY: number | null = null;

    const onTouchStart = (e: TouchEvent) => {
      // 맨 위가 아니면 시작하지 않는다
      startY = window.scrollY <= 0 ? e.touches[0].clientY : null;
    };

    const onTouchMove = (e: TouchEvent) => {
      if (startY === null || refreshingRef.current) return;
      const distance = e.touches[0].clientY - startY;
      // 위로 올리는 것은 평범한 스크롤이다
      setDistance(distance > 0 ? distance * RESISTANCE : 0);
    };

    const onTouchEnd = async () => {
      const enough = pulledRef.current >= THRESHOLD_PX;
      startY = null;
      setDistance(0);
      if (!enough || refreshingRef.current) return;

      refreshingRef.current = true;
      setRefreshing(true);
      try {
        await onRefreshRef.current();
      } finally {
        refreshingRef.current = false;
        setRefreshing(false);
      }
    };

    // passive: 스크롤을 막지 않는다. 막으면 목록이 뻑뻑해진다.
    window.addEventListener('touchstart', onTouchStart, { passive: true });
    window.addEventListener('touchmove', onTouchMove, { passive: true });
    window.addEventListener('touchend', onTouchEnd);
    return () => {
      window.removeEventListener('touchstart', onTouchStart);
      window.removeEventListener('touchmove', onTouchMove);
      window.removeEventListener('touchend', onTouchEnd);
    };
    // 리스너는 한 번만 붙인다. 바뀌는 값은 전부 ref 로 읽는다.
  }, [setDistance]);

  return {
    /** 지금 얼마나 당겨져 있는지(px). 표시를 그리는 데 쓴다 */
    pulled: Math.min(pulled, THRESHOLD_PX * 1.5),
    /** 놓으면 새로고침될 만큼 당겨졌는지 */
    ready: pulled >= THRESHOLD_PX,
    refreshing,
  };
}

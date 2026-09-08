import { useEffect, useState } from 'react';

/**
 * 브라우저가 네트워크에 닿는지.
 *
 * 병동 간호사는 엘리베이터와 지하 검사실을 오간다. 끊기는 것이 예외가 아니라 일상이다.
 * 끊긴 줄 모르고 버튼을 누르면 "저장됐겠지" 하고 넘어가게 되는데, 그게 제일 위험하다.
 *
 * navigator.onLine 은 "랜선이 꽂혀 있나" 수준이라 와이파이에 붙었지만 인터넷이 안 되는
 * 경우를 못 잡는다. 그건 요청이 실패할 때 각 화면이 잡는다. 여기서는 확실히 끊긴 것만 본다.
 */
export function useOnline() {
  const [online, setOnline] = useState(() => navigator.onLine);

  useEffect(() => {
    const up = () => setOnline(true);
    const down = () => setOnline(false);
    window.addEventListener('online', up);
    window.addEventListener('offline', down);
    return () => {
      window.removeEventListener('online', up);
      window.removeEventListener('offline', down);
    };
  }, []);

  return online;
}

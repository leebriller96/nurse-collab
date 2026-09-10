import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * 화면의 조회 조건을 주소에 담는다.
 *
 * 컴포넌트 안에만 두면 목록에서 기간과 검색어를 맞춰 놓고 한 건을 열어본 뒤
 * 돌아왔을 때 조건이 전부 처음으로 돌아간다. 스무 건을 훑어보는 사람은
 * 매번 다시 맞춰야 한다.
 *
 * 주소에 담으면 뒤로가기가 조건까지 되돌리고, 찾아낸 화면을 링크로 넘길 수도 있다.
 *
 * `replace` 로 바꾸는 이유:
 * 조건을 고칠 때마다 방문 기록이 쌓이면 뒤로가기를 눌렀을 때 이전 화면이 아니라
 * 이전 검색 조건으로 돌아간다. 날짜를 세 번 고쳤으면 세 번 눌러야 목록을 빠져나간다.
 */
export function useUrlParam(
  key: string,
  fallback = '',
): [string, (value: string, extra?: Record<string, string>) => void] {
  const [params, setParams] = useSearchParams();
  const value = params.get(key) ?? fallback;

  const set = useCallback(
    (next: string, extra?: Record<string, string>) => {
      setParams(
        (prev) => {
          // prev 를 그대로 고쳐야 한 번의 렌더에서 여러 조건을 바꿔도 서로 지우지 않는다.
          // 조건을 바꾸면 보통 첫 쪽으로 돌아가야 하는데, 그것도 여기서 같이 넘긴다.
          const merged = new URLSearchParams(prev);
          if (next === fallback) merged.delete(key);
          else merged.set(key, next);

          for (const [k, v] of Object.entries(extra ?? {})) {
            if (v === '') merged.delete(k);
            else merged.set(k, v);
          }
          return merged;
        },
        { replace: true },
      );
    },
    [key, fallback, setParams],
  );

  return [value, set];
}

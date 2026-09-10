import type { ReactNode } from 'react';
import { usePullToRefresh } from '@/shared/hooks/usePullToRefresh';

/**
 * 목록을 감싸 당겨서 새로고침을 붙인다.
 *
 * 표시는 화면 위에 겹쳐 띄운다. 내용을 아래로 밀면 당기는 동안 목록 전체가
 * 출렁이는데, 한 손으로 들고 보는 화면에서는 그 움직임이 거슬린다.
 */
export default function PullToRefresh({
  onRefresh,
  children,
}: {
  onRefresh: () => Promise<unknown> | unknown;
  children: ReactNode;
}) {
  const { pulled, ready, refreshing } = usePullToRefresh(onRefresh);
  const showing = pulled > 0 || refreshing;

  return (
    <>
      {showing && (
        <div
          role="status"
          aria-live="polite"
          className="pointer-events-none fixed inset-x-0 top-0 z-30 flex justify-center"
          style={{ transform: `translateY(${refreshing ? 12 : Math.min(pulled, 60)}px)` }}
        >
          <span className="rounded-full bg-slate-900/90 px-3 py-1.5 text-xs font-semibold text-white shadow-lg">
            {refreshing ? '새로 받는 중' : ready ? '놓으면 새로고침' : '당겨서 새로고침'}
          </span>
        </div>
      )}
      {children}
    </>
  );
}

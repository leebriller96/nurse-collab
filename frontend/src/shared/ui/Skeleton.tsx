import type { ReactNode } from 'react';

/**
 * 불러오는 동안 자리를 잡아 두는 회색 블록.
 *
 * "불러오는 중…" 한 줄로 두면 화면이 통째로 사라졌다가 나타난다.
 * 목록을 열 때마다 레이아웃이 접혔다 펴지면 어수선하고, 무엇을 기다리는지도 알 수 없다.
 *
 * 대신 **실제 화면과 같은 모양**이어야 한다. 크기가 안 맞는 회색 상자는
 * 값이 들어오는 순간 다시 밀려나므로 글자 한 줄보다 나쁘다.
 * 그래서 화면 종류별로 따로 만들었다.
 *
 * 움직임을 줄여 달라고 설정한 사람에게는 깜빡이지 않는다(motion-safe).
 * 어지럼증이나 전정기관 문제로 애니메이션을 꺼 둔 사람이 있다.
 */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div aria-hidden className={`rounded bg-slate-200 motion-safe:animate-pulse ${className}`} />;
}

/**
 * 화면을 읽어 주는 도구에는 회색 블록이 아무 의미가 없다.
 * 눈으로 보는 사람에게는 모양으로, 듣는 사람에게는 말로 같은 것을 알린다.
 */
function Loading({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div role="status" aria-live="polite" className={className}>
      <span className="sr-only">불러오는 중</span>
      {children}
    </div>
  );
}

/**
 * 화면 위쪽의 제목 자리.
 *
 * 이걸 빼먹으면 값이 들어오는 순간 제목이 생기면서 아래가 통째로 밀린다.
 * 밀림을 없애려고 만든 것이 밀림을 만들게 된다.
 */
function HeaderSkeleton({ compact = false }: { compact?: boolean }) {
  return (
    <div className={compact ? 'px-4 py-3' : 'mb-4'}>
      <Skeleton className={compact ? 'h-5 w-24' : 'h-6 w-44'} />
      <Skeleton className={`mt-2 ${compact ? 'h-3 w-20' : 'h-3.5 w-28'}`} />
    </div>
  );
}

/** 병동 화면의 카드 목록 (환자 보드, 내 요청, 알림함) */
export function CardListSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <Loading>
      <HeaderSkeleton compact />
      <div className="space-y-2 px-3">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="rounded-xl bg-white p-3.5 shadow-sm ring-1 ring-slate-200">
          <div className="flex items-baseline gap-2">
            <Skeleton className="h-4 w-14" />
            <Skeleton className="h-4 w-16" />
            <Skeleton className="h-3 w-10" />
          </div>
          <Skeleton className="mt-2 h-3 w-32" />
          <div className="mt-2 flex gap-1.5">
            <Skeleton className="h-5 w-12 rounded-md" />
            <Skeleton className="h-5 w-10 rounded-md" />
          </div>
        </div>
      ))}
      </div>
    </Loading>
  );
}

/**
 * 검사실·관리자 화면의 표 (들어온 요청, 지난 요청, 접근 기록)
 *
 * `header` 는 화면 전체를 대신할 때만 켠다. 지난 요청은 검색 폼 아래에
 * 표만 끼워 넣으므로 제목을 또 그리면 두 개가 된다.
 */
export function TableSkeleton({
  rows = 6,
  columns = 7,
  header = false,
}: { rows?: number; columns?: number; header?: boolean }) {
  return (
    <Loading>
      {header && <HeaderSkeleton />}
      <div className="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
        {/* 표 머리글 줄. 실제 화면에서 회색 띠라 이것도 자리를 잡아 둬야 한다. */}
        <div className="flex items-center gap-4 border-b border-slate-200 bg-slate-50 px-4 py-2.5">
          {Array.from({ length: columns }, (_, c) => (
            <Skeleton key={c} className={`h-3 bg-slate-300 ${c === 1 ? 'w-24' : 'w-12'}`} />
          ))}
        </div>
        {Array.from({ length: rows }, (_, r) => (
          <div
            key={r}
            className="flex items-center gap-4 border-b border-slate-100 px-4 py-3.5 last:border-0"
          >
            {Array.from({ length: columns }, (_, c) => (
              // 열마다 폭을 달리해야 표처럼 보인다. 같은 폭이면 격자무늬가 된다.
              <Skeleton key={c} className={`h-4 ${c === 0 ? 'w-12' : c === 1 ? 'w-32' : 'w-20'}`} />
            ))}
          </div>
        ))}
      </div>
    </Loading>
  );
}

/** 카드 몇 장이 쌓인 상세 화면 (환자 상세, 요청 상세) */
export function DetailSkeleton() {
  return (
    <Loading className="space-y-3 p-3">
      <div className="rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
        <div className="flex items-baseline gap-2">
          <Skeleton className="h-5 w-16" />
          <Skeleton className="h-5 w-20" />
        </div>
        <Skeleton className="mt-3 h-3 w-40" />
        <Skeleton className="mt-2 h-3 w-28" />
      </div>
      <div className="rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
        <Skeleton className="h-3 w-20" />
        <Skeleton className="mt-3 h-3 w-full" />
        <Skeleton className="mt-2 h-3 w-5/6" />
        <Skeleton className="mt-2 h-3 w-4/6" />
      </div>
    </Loading>
  );
}

import { messageOf } from '@/shared/api/client';
import { useOnline } from '@/shared/hooks/useOnline';

/**
 * 조회에 실패했을 때 보여주는 자리.
 *
 * 문구만 띄우고 끝내면 안 된다. 병원 와이파이는 엘리베이터와 지하에서 자주 끊기는데,
 * 그때마다 화면이 오류 문구로 굳으면 간호사는 앱을 껐다 켜는 수밖에 없다.
 * 근무 중에 그러고 있을 시간이 없다.
 *
 * 끊긴 것이 확실할 때는 서버가 준 문장 대신 그 사실을 말한다.
 * "요청을 처리할 수 없습니다" 보다 "연결이 끊겼습니다" 가 다음에 할 일을 알려 준다.
 */
interface Props {
  error: unknown;
  onRetry: () => void;
  /** 목록 안에 끼워 넣을 때처럼 여백을 줄여야 하는 경우 */
  compact?: boolean;
}

export default function LoadFailed({ error, onRetry, compact = false }: Props) {
  const online = useOnline();

  return (
    <div
      role="alert"
      className={`flex flex-col items-center gap-3 text-center ${compact ? 'py-6' : 'px-6 py-12'}`}
    >
      <p className="text-sm text-slate-600">
        {online ? messageOf(error, '불러오지 못했습니다.') : '연결이 끊겨 불러오지 못했습니다.'}
      </p>
      {/*
        페이지 배경이 slate-100 이라 같은 색 버튼은 굵은 글씨로만 보인다.
        하나뿐인 빠져나갈 길이므로 눌러도 되는 것처럼 보여야 한다.
        흰 바탕에 테두리를 둬서 흰 카드 안에 들어가는 경우에도 살아 있게 한다.
      */}
      <button
        type="button"
        onClick={onRetry}
        className="rounded-lg border border-slate-300 bg-white px-5 py-2.5 text-sm font-semibold text-slate-700 shadow-sm"
      >
        다시 시도
      </button>
    </div>
  );
}

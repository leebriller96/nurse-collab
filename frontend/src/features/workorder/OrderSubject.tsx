import type { SubjectBrief } from '@/shared/api/phi';
import type { OrderSummary } from '@/shared/api/types';

/**
 * 목록에서 "누구" 자리.
 *
 * 이 자리는 세 가지 모습을 가진다.
 *   1. 대상 환자가 없는 업무(장비 수리) — 그렇다고 말한다
 *   2. 원내망에 닿지 못함 — <b>빈칸으로 두지 않고 그렇다고 말한다</b>
 *   3. 정상 — 이름과 나이, 주의사항 수
 *
 * 2번을 빈칸으로 두면 "이 요청에는 환자가 없다" 와 구별되지 않는다.
 * 모르는 것보다 틀리게 아는 것이 나쁘다 — 오프라인에서 값을 내놓지 않기로 한 것과 같다.
 */
export default function OrderSubject({
  row,
  brief,
  unavailable,
  showRoom = true,
}: {
  row: OrderSummary;
  brief?: SubjectBrief;
  unavailable?: boolean;
  showRoom?: boolean;
}) {
  if (!row.subjectRef) {
    return <span className="text-slate-500">대상 환자 없음</span>;
  }

  const room = showRoom && row.roomNo ? (
    <span className="ml-1.5 text-slate-400">{row.roomNo}호</span>
  ) : null;

  if (!brief) {
    return (
      <>
        <span className={unavailable ? 'text-amber-700' : 'text-slate-400'}>
          {unavailable ? '원내망에서만 조회됩니다' : '불러오는 중…'}
        </span>
        {room}
      </>
    );
  }

  return (
    <>
      <span className="font-medium text-slate-900">{brief.name}</span>
      <span className="ml-1.5 text-slate-500">
        {brief.sex}/{brief.age}
      </span>
      {room}
      {brief.criticalAlertCount > 0 && (
        <span className="ml-1.5 rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold text-red-700">
          !주의 {brief.criticalAlertCount}
        </span>
      )}
    </>
  );
}

import type { OrderSummary } from '@/shared/api/types';

/**
 * 목록에서 "누구" 자리.
 *
 * 환자가 없는 업무(장비 수리)가 있어서 이 자리는 빌 수 있다.
 * 목록마다 각자 null 을 확인하게 두면 한 군데는 빠뜨리고,
 * 그 화면만 첫 장비 요청이 들어온 날 백지가 된다.
 */
export default function OrderSubject({
  row,
  showRoom = true,
}: {
  row: OrderSummary;
  showRoom?: boolean;
}) {
  if (!row.patient) {
    return <span className="text-slate-500">대상 환자 없음</span>;
  }

  return (
    <>
      <span className="font-medium text-slate-900">{row.patient.name}</span>
      <span className="ml-1.5 text-slate-500">
        {row.patient.sex}/{row.patient.age}
      </span>
      {showRoom && row.roomNo && <span className="ml-1.5 text-slate-400">{row.roomNo}호</span>}
    </>
  );
}

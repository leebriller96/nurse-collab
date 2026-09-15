import type { AlertSeverity, AlertType, OrderPriority, OrderStatus } from '@/shared/api/types';

const ALERT_LABEL: Record<AlertType, string> = {
  METAL_IMPLANT: '금속물',
  CONTRAST_ALLERGY: '조영제',
  DRUG_ALLERGY: '약물',
  ISOLATION: '격리',
  FALL_RISK: '낙상',
  NPO: '금식',
  OXYGEN: '산소',
  CLAUSTROPHOBIA: '폐소공포',
};

const SEVERITY_STYLE: Record<AlertSeverity, string> = {
  CRITICAL: 'bg-red-100 text-red-800 ring-red-300',
  WARN: 'bg-amber-100 text-amber-900 ring-amber-300',
  INFO: 'bg-slate-100 text-slate-700 ring-slate-300',
};

export function AlertBadge({ type, severity }: { type: AlertType; severity: AlertSeverity }) {
  return (
    <span
      className={`inline-flex items-center gap-0.5 rounded px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset ${SEVERITY_STYLE[severity]}`}
    >
      {severity === 'CRITICAL' ? '!' : ''}
      {ALERT_LABEL[type]}
    </span>
  );
}

/**
 * 상태의 기본 이름.
 *
 * 업무 종류마다 같은 상태를 다르게 부르므로(검사중 / 조제중 / 수리중)
 * **서버가 내려주는 statusLabel 이 있으면 그쪽이 우선이다.**
 * 이 표는 이력 타임라인처럼 종류를 알 수 없는 자리에서만 쓴다.
 */
const STATUS_LABEL: Record<OrderStatus, string> = {
  REQUESTED: '요청됨',
  ACCEPTED: '접수됨',
  READY: '준비완료',
  IN_TRANSIT: '이송중',
  IN_PROGRESS: '진행중',
  RETURNED: '복귀중',
  COLLECTED: '채취완료',
  RESULTED: '결과등록',
  DISPENSED: '조제완료',
  DELIVERED: '불출완료',
  AWAITING_PARTS: '부품대기',
  COMPLETED: '완료',
  ON_HOLD: '보류',
  CANCELLED: '취소',
};

const STATUS_STYLE: Record<OrderStatus, string> = {
  REQUESTED: 'bg-slate-200 text-slate-800',
  ACCEPTED: 'bg-sky-100 text-sky-800',
  READY: 'bg-indigo-100 text-indigo-800',
  IN_TRANSIT: 'bg-violet-100 text-violet-800',
  IN_PROGRESS: 'bg-blue-100 text-blue-800',
  RETURNED: 'bg-teal-100 text-teal-800',
  COLLECTED: 'bg-indigo-100 text-indigo-800',
  RESULTED: 'bg-teal-100 text-teal-800',
  DISPENSED: 'bg-indigo-100 text-indigo-800',
  DELIVERED: 'bg-teal-100 text-teal-800',
  AWAITING_PARTS: 'bg-orange-100 text-orange-900',
  COMPLETED: 'bg-emerald-100 text-emerald-800',
  ON_HOLD: 'bg-amber-100 text-amber-900',
  CANCELLED: 'bg-slate-200 text-slate-500 line-through',
};

export const statusLabel = (status: OrderStatus) => STATUS_LABEL[status];

/** label 을 주면 그것을 쓴다. 종류마다 부르는 이름이 달라서 서버가 함께 내려준다. */
export function StatusBadge({ status, label }: { status: OrderStatus; label?: string }) {
  return (
    <span className={`inline-flex rounded px-2 py-0.5 text-xs font-semibold ${STATUS_STYLE[status]}`}>
      {label ?? STATUS_LABEL[status]}
    </span>
  );
}

const PRIORITY_LABEL: Record<OrderPriority, string> = {
  ROUTINE: '일반',
  URGENT: '긴급',
  EMERGENCY: '응급',
};

const PRIORITY_STYLE: Record<OrderPriority, string> = {
  ROUTINE: 'bg-slate-100 text-slate-600',
  URGENT: 'bg-amber-500 text-white',
  EMERGENCY: 'bg-red-600 text-white',
};

export const priorityLabel = (priority: OrderPriority) => PRIORITY_LABEL[priority];

export function PriorityBadge({ priority }: { priority: OrderPriority }) {
  return (
    <span className={`inline-flex rounded px-2 py-0.5 text-xs font-bold ${PRIORITY_STYLE[priority]}`}>
      {PRIORITY_LABEL[priority]}
    </span>
  );
}

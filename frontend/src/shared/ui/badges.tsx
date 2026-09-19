import type { AlertSeverity, AlertType, OrderPriority, OrderStatus } from '@/shared/api/types';
import { PRIORITY_LABEL } from '@/shared/lib/labels';

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

/**
 * 이름은 서버가 준다. 종류마다 같은 상태를 다르게 부르므로(검사중 / 조제중 / 수리중)
 * 화면이 이름표를 들면 종류를 더할 때 두 곳을 고쳐야 한다. 여기는 색만 정한다.
 */
export function StatusBadge({ status, label }: { status: OrderStatus; label: string }) {
  return (
    <span className={`inline-flex rounded px-2 py-0.5 text-xs font-semibold ${STATUS_STYLE[status]}`}>
      {label}
    </span>
  );
}

const PRIORITY_STYLE: Record<OrderPriority, string> = {
  ROUTINE: 'bg-slate-100 text-slate-600',
  URGENT: 'bg-amber-500 text-white',
  EMERGENCY: 'bg-red-600 text-white',
};

export function PriorityBadge({ priority }: { priority: OrderPriority }) {
  return (
    <span className={`inline-flex rounded px-2 py-0.5 text-xs font-bold ${PRIORITY_STYLE[priority]}`}>
      {PRIORITY_LABEL[priority]}
    </span>
  );
}

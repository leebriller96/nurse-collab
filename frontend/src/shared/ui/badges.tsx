import type { AlertSeverity, AlertType, TransferPriority, TransferStatus } from '@/shared/api/types';

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

const STATUS_LABEL: Record<TransferStatus, string> = {
  REQUESTED: '요청됨',
  ACCEPTED: '접수됨',
  READY: '준비완료',
  IN_TRANSIT: '이송중',
  IN_PROGRESS: '검사중',
  RETURNED: '복귀중',
  COMPLETED: '완료',
  ON_HOLD: '보류',
  CANCELLED: '취소',
};

const STATUS_STYLE: Record<TransferStatus, string> = {
  REQUESTED: 'bg-slate-200 text-slate-800',
  ACCEPTED: 'bg-sky-100 text-sky-800',
  READY: 'bg-indigo-100 text-indigo-800',
  IN_TRANSIT: 'bg-violet-100 text-violet-800',
  IN_PROGRESS: 'bg-blue-100 text-blue-800',
  RETURNED: 'bg-teal-100 text-teal-800',
  COMPLETED: 'bg-emerald-100 text-emerald-800',
  ON_HOLD: 'bg-amber-100 text-amber-900',
  CANCELLED: 'bg-slate-200 text-slate-500 line-through',
};

export const statusLabel = (status: TransferStatus) => STATUS_LABEL[status];

export function StatusBadge({ status }: { status: TransferStatus }) {
  return (
    <span className={`inline-flex rounded px-2 py-0.5 text-xs font-semibold ${STATUS_STYLE[status]}`}>
      {STATUS_LABEL[status]}
    </span>
  );
}

const PRIORITY_LABEL: Record<TransferPriority, string> = {
  ROUTINE: '일반',
  URGENT: '긴급',
  EMERGENCY: '응급',
};

const PRIORITY_STYLE: Record<TransferPriority, string> = {
  ROUTINE: 'bg-slate-100 text-slate-600',
  URGENT: 'bg-amber-500 text-white',
  EMERGENCY: 'bg-red-600 text-white',
};

export const priorityLabel = (priority: TransferPriority) => PRIORITY_LABEL[priority];

export function PriorityBadge({ priority }: { priority: TransferPriority }) {
  return (
    <span className={`inline-flex rounded px-2 py-0.5 text-xs font-bold ${PRIORITY_STYLE[priority]}`}>
      {PRIORITY_LABEL[priority]}
    </span>
  );
}

/**
 * 버튼에 쓰는 표현.
 *
 * 상태명은 "이미 그렇게 된 것" 을 가리키는 말이라 버튼에 그대로 쓰면 어색하다.
 * "복귀중" 이라고 적힌 버튼을 누르라고 하면 무슨 뜻인지 한 번 더 생각해야 한다.
 * 간호사가 실제로 하는 행동으로 적는다.
 */
const ACTION_LABEL: Record<TransferStatus, string> = {
  REQUESTED: '요청',
  ACCEPTED: '접수',
  READY: '준비 완료',
  IN_TRANSIT: '환자 출발',
  IN_PROGRESS: '검사 시작',
  RETURNED: '검사 종료',
  COMPLETED: '병동 도착',
  ON_HOLD: '보류',
  CANCELLED: '취소',
};

export function actionLabel(target: TransferStatus, current: TransferStatus): string {
  // 보류에서 원래 상태로 되돌리는 것은 "접수" 가 아니라 "보류 해제" 다
  if (current === 'ON_HOLD' && target !== 'CANCELLED') {
    return '보류 해제';
  }
  return ACTION_LABEL[target];
}

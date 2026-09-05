export type DeptType = 'WARD' | 'EXAM' | 'OR' | 'ICU' | 'ER' | 'ADMIN';
export type StaffRole = 'NURSE' | 'HEAD_NURSE' | 'ADMIN';
export type Sex = 'M' | 'F';

export type TransferStatus =
  | 'REQUESTED' | 'ACCEPTED' | 'READY' | 'IN_TRANSIT'
  | 'IN_PROGRESS' | 'RETURNED' | 'COMPLETED' | 'ON_HOLD' | 'CANCELLED';

export type TransferPriority = 'ROUTINE' | 'URGENT' | 'EMERGENCY';
export type AlertSeverity = 'INFO' | 'WARN' | 'CRITICAL';
export type AlertType =
  | 'METAL_IMPLANT' | 'CONTRAST_ALLERGY' | 'DRUG_ALLERGY' | 'ISOLATION'
  | 'FALL_RISK' | 'NPO' | 'OXYGEN' | 'CLAUSTROPHOBIA';

export interface DepartmentSummary {
  id: number;
  code: string;
  name: string;
  deptType: DeptType;
}

export interface Staff {
  id: number;
  name: string;
  role: StaffRole;
  department: DepartmentSummary;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  staff: Staff;
}

export interface AlertSummary {
  alertType: AlertType;
  severity: AlertSeverity;
}

export interface AlertResponse extends AlertSummary {
  id: number;
  label: string;
  content: string;
  createdAt: string;
}

export interface EncounterSummary {
  encounterId: number;
  patientNo: string;
  name: string;
  birthDate: string;
  age: number;
  sex: Sex;
  roomNo: string;
  bedNo: string;
  admittedAt: string;
  diagnosis: string | null;
  alertSummary: AlertSummary[];
  activeRequestCount: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ExamType {
  id: number;
  code: string;
  name: string;
  department: { id: number; name: string };
  defaultDuration: number;
  prepInstruction: string | null;
  requiredAlerts: AlertType[];
}

export interface TransferSummary {
  id: number;
  requestNo: string;
  status: TransferStatus;
  priority: TransferPriority;
  patient: { patientNo: string; name: string; age: number; sex: Sex };
  roomNo: string;
  examName: string;
  counterpartDepartment: DepartmentSummary;
  requestedAt: string;
  scheduledAt: string | null;
  waitingMinutes: number;
  criticalAlertCount: number;
  version: number;
}

/** 서버가 내려주는 에러 형식. message 는 화면에 그대로 띄울 수 있는 문장이다. */
export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; reason: string }[];
}

export interface ChecklistWarning {
  alertType: AlertType;
  message: string;
}

export interface EncounterFullView {
  encounterId: number;
  patient: { patientNo: string; name: string; birthDate: string; age: number; sex: Sex };
  department: DepartmentSummary;
  roomNo: string;
  bedNo: string;
  admittedAt: string;
  diagnosis: string | null;
  isMobile: boolean;
  alerts: AlertResponse[];
  activeRequests: {
    id: number;
    requestNo: string;
    examName: string;
    status: TransferStatus;
    scheduledAt: string | null;
  }[];
}

export interface TransferDetail {
  id: number;
  requestNo: string;
  status: TransferStatus;
  priority: TransferPriority;
  encounter: { encounterId: number; roomNo: string; bedNo: string; isMobile: boolean };
  patient: { patientNo: string; name: string; age: number; sex: Sex };
  examType: {
    id: number;
    code: string;
    name: string;
    defaultDuration: number;
    prepInstruction: string | null;
  };
  fromDepartment: DepartmentSummary;
  toDepartment: DepartmentSummary;
  requestedBy: { id: number; name: string };
  requestedAt: string;
  desiredAt: string | null;
  scheduledAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  note: string | null;
  holdReason: string | null;
  alerts: AlertResponse[];
  checklistWarnings: ChecklistWarning[];
  availableTransitions: TransferStatus[];
  version: number;
}

export interface TransferEvent {
  id: number;
  fromStatus: TransferStatus | null;
  toStatus: TransferStatus;
  actor: { id: number; name: string; departmentName: string };
  occurredAt: string;
  reason: string | null;
}

export interface Message {
  id: number;
  sender: { id: number; name: string; departmentName: string };
  content: string;
  createdAt: string;
}

export interface TransitionResponse {
  id: number;
  status: TransferStatus;
  scheduledAt: string | null;
  availableTransitions: TransferStatus[];
  version: number;
}

export type NoteType = 'GENERAL' | 'SBAR' | 'HANDOVER';

export interface VitalSign {
  id: number;
  measuredAt: string;
  temperature: number | null;
  pulse: number | null;
  respiration: number | null;
  sbp: number | null;
  dbp: number | null;
  spo2: number | null;
  painScore: number | null;
  recordedBy: { id: number; name: string };
}

export interface NursingNote {
  id: number;
  noteType: NoteType;
  situation: string | null;
  background: string | null;
  assessment: string | null;
  recommendation: string | null;
  content: string | null;
  recordedAt: string;
  recordedBy: { id: number; name: string; departmentName: string };
  createdAt: string;
  /** 서버가 계산해 내려준다. 24시간 규칙을 화면에서 다시 구현하지 않는다. */
  editable: boolean;
}

export interface AuditLogEntry {
  id: number;
  actor: { id: number; name: string; departmentName: string } | null;
  action: string;
  targetType: string;
  targetId: number | null;
  patient: { id: number; patientNo: string; name: string } | null;
  ipAddress: string | null;
  occurredAt: string;
}

export type NotiType = 'TRANSFER_REQUESTED' | 'STATUS_CHANGED' | 'MESSAGE';

export interface NotificationItem {
  id: number;
  notiType: NotiType;
  refType: string;
  refId: number;
  title: string;
  body: string | null;
  readAt: string | null;
  createdAt: string;
}

/** 목록과 미읽음 수를 함께 받는다. 뱃지 때문에 두 번 부르지 않으려는 것이다. */
export interface NotificationsResponse {
  page: PageResponse<NotificationItem>;
  unreadCount: number;
}

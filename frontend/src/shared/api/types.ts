export type DeptType =
  | 'WARD' | 'EXAM' | 'OR' | 'ICU' | 'ER'
  | 'LAB' | 'PHARMACY' | 'BIOMED'
  | 'ADMIN';
export type StaffRole = 'NURSE' | 'HEAD_NURSE' | 'ADMIN';
export type Sex = 'M' | 'F';

export type OrderType = 'TRANSFER' | 'SPECIMEN' | 'PHARMACY' | 'EQUIPMENT';

export type OrderStatus =
  // 공통
  | 'REQUESTED' | 'ACCEPTED' | 'IN_PROGRESS' | 'COMPLETED' | 'ON_HOLD' | 'CANCELLED'
  // 이송
  | 'READY' | 'IN_TRANSIT' | 'RETURNED'
  // 검체
  | 'COLLECTED' | 'RESULTED'
  // 약제
  | 'DISPENSED' | 'DELIVERED'
  // 의공
  | 'AWAITING_PARTS';

/**
 * 지금 누를 수 있는 버튼 하나. 서버가 만들어 준다.
 *
 * 이름도 필수 입력 여부도 업무 종류마다 다르다. 화면이 그 표를 따로 들면
 * 종류를 더할 때 서버와 화면 두 곳을 고쳐야 하고 한쪽만 고쳐지는 날이 온다.
 */
export interface TransitionOption {
  status: OrderStatus;
  label: string;
  actionLabel: string;
  reasonRequired: boolean;
  scheduleRequired: boolean;
}

export type OrderPriority = 'ROUTINE' | 'URGENT' | 'EMERGENCY';
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
  subjectRef: string;
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

export interface ServiceItem {
  id: number;
  code: string;
  name: string;
  orderType: OrderType;
  orderTypeLabel: string;
  /** 거짓이면 대상 환자를 고르지 않는다 (장비 수리) */
  patientRequired: boolean;
  department: { id: number; name: string };
  defaultDuration: number;
  prepInstruction: string | null;
  requiredAlerts: AlertType[];
}

export interface OrderSummary {
  id: number;
  requestNo: string;
  orderType: OrderType;
  status: OrderStatus;
  /** 이 종류에서 이 상태를 부르는 이름 (검사중 / 조제중 / 수리중) */
  statusLabel: string;
  priority: OrderPriority;
  /**
   * 대상 재원 건의 가명. 환자가 없는 업무에서는 비어 있다.
   * 이름은 이 응답에 없다. 이 열쇠로 원내에 따로 물어 채운다.
   */
  subjectRef: string | null;
  roomNo: string | null;
  bedNo: string | null;
  itemName: string;
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
  /** 업무 요청을 걸 때 이 열쇠로 대상을 가리킨다 */
  subjectRef: string;
  patient: { id: number; patientNo: string; name: string; birthDate: string; age: number; sex: Sex };
  /**
   * 현재 병동의 id. 이름이 없는 이유는 부서가 업무 쪽에 있기 때문이다.
   * 필요하면 화면이 부서 목록에서 찾아 채운다.
   */
  departmentId: number;
  roomNo: string;
  bedNo: string;
  admittedAt: string;
  diagnosis: string | null;
  isMobile: boolean;
  alerts: AlertResponse[];
  activeRequests: {
    id: number;
    requestNo: string;
    itemName: string;
    status: OrderStatus;
    scheduledAt: string | null;
  }[];
}

export interface OrderDetail {
  id: number;
  requestNo: string;
  orderType: OrderType;
  orderTypeLabel: string;
  status: OrderStatus;
  statusLabel: string;
  priority: OrderPriority;
  /**
   * 대상 재원 건. 환자가 없는 업무에서는 없다.
   * 여기에는 침대와 병동만 있다. 이름·진단명·주의사항은 원내에서 따로 받는다.
   */
  episode: { subjectRef: string; roomNo: string; bedNo: string } | null;
  serviceItem: {
    id: number;
    code: string;
    name: string;
    defaultDuration: number;
    prepInstruction: string | null;
    /** 이 업무 전에 확인할 항목. 이것을 들고 원내에 물어 경고를 받는다 */
    requiredAlerts: AlertType[];
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
  availableTransitions: TransitionOption[];
  version: number;
}

export interface OrderEvent {
  id: number;
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
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
  status: OrderStatus;
  scheduledAt: string | null;
  availableTransitions: TransitionOption[];
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
  /** 이름은 쓸 때 굳혀 둔 값이다. 그때 그 사람의 이름이어야 한다 */
  recordedBy: { id: number; name: string };
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

export type NotiType = 'ORDER_CREATED' | 'STATUS_CHANGED' | 'MESSAGE';

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

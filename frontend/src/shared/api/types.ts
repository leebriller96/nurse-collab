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

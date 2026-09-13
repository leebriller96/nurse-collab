import axios from 'axios';
import { useQuery } from '@tanstack/react-query';
import { tokenStore } from './client';
import type { AlertResponse, AlertSummary, AlertType, ChecklistWarning, Sex } from './types';

/**
 * 원내에만 있는 것들을 받아오는 통로.
 *
 * 업무 API 와 인스턴스를 나눠 둔 이유는 <b>나중에 다른 서버가 되기 때문</b>이다.
 * 지금은 주소가 같지만, 원내 게이트웨이는 사설망 주소로만 열린다.
 * 그때 바꿀 곳이 이 한 줄이다.
 *
 * 갱신 재시도를 붙이지 않는다. 토큰 갱신은 업무 쪽(인증 서버)이 하고
 * 여기는 검증만 하는 곳이다. 양쪽이 각자 갱신을 시도하면 토큰이 여러 번 회전한다.
 */
export const phi = axios.create({
  baseURL: import.meta.env.VITE_PHI_BASE ?? '/api/v1/phi',
  // 원내에 닿지 못하는 상황이 정상 상태 중 하나다. 오래 매달려 있으면
  // 업무 화면까지 같이 느려진다. 빨리 포기하고 "못 받았다" 고 말한다.
  timeout: 5000,
});

phi.interceptors.request.use((config) => {
  const token = tokenStore.access();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export interface SubjectBrief {
  subjectRef: string;
  name: string;
  age: number;
  sex: Sex;
  criticalAlertCount: number;
  /** 담당 병동이 아니면 비어 있다. 검사를 수행하는 데 진단명은 필요하지 않다 */
  diagnosis: string | null;
  /** 유형과 심각도만. 내용은 담기지 않는다. 담당 병동이 아니면 빈 배열 */
  alerts: AlertSummary[];
}

export interface SubjectPhi {
  subjectRef: string;
  /** 활력징후·간호기록이 이 값에 붙는다. 원내 안에서만 쓰는 식별자다 */
  encounterId: number;
  /** 주의사항은 재원이 아니라 사람에 붙는다 */
  patientId: number;
  patientNo: string;
  name: string;
  age: number;
  sex: Sex;
  diagnosis: string | null;
  mobile: boolean;
  alerts: AlertResponse[];
}

/**
 * 목록의 가명들을 한 번에 사람으로 되돌린다.
 *
 * 되돌리지 못한 것과 "그런 사람이 없다" 는 다르다.
 * 원내에 닿지 못하면 `unavailable` 이 참이 되고, 화면은 빈칸을 보여주는 대신
 * "원내망에서만 조회됩니다" 라고 말한다. 모르는 것보다 틀리게 아는 것이 나쁘다.
 */
export function useSubjectBriefs(subjectRefs: (string | null | undefined)[]) {
  const refs = [...new Set(subjectRefs.filter((r): r is string => !!r))].sort();

  const query = useQuery({
    queryKey: ['phi', 'briefs', refs],
    queryFn: async () => (await phi.post<SubjectBrief[]>('/subjects/brief', refs)).data,
    enabled: refs.length > 0,
    // 원내가 잠깐 끊긴 것을 화면 전체의 실패로 만들지 않는다
    retry: 1,
    staleTime: 30_000,
  });

  const byRef = new Map<string, SubjectBrief>();
  for (const brief of query.data ?? []) byRef.set(brief.subjectRef, brief);

  return {
    byRef,
    unavailable: refs.length > 0 && query.isError,
    loading: query.isPending && refs.length > 0,
  };
}

/** 한 사람을 연다. 이 호출이 원내 감사 로그에 남는다. */
export function useSubject(subjectRef: string | null | undefined) {
  const query = useQuery({
    queryKey: ['phi', 'subject', subjectRef],
    queryFn: async () => (await phi.get<SubjectPhi>(`/subjects/${subjectRef}`)).data,
    enabled: !!subjectRef,
    retry: 1,
  });

  return {
    subject: query.data ?? null,
    unavailable: !!subjectRef && query.isError,
    loading: query.isPending && !!subjectRef,
  };
}

/**
 * 이 업무 전에 확인할 것.
 * 확인 항목은 업무 쪽이 알고 있으므로 이쪽이 들고 가서 묻는다.
 */
export function useChecklist(subjectRef: string | null | undefined, required: AlertType[]) {
  const query = useQuery({
    queryKey: ['phi', 'checklist', subjectRef, [...required].sort()],
    queryFn: async () =>
      (await phi.get<ChecklistWarning[]>(`/subjects/${subjectRef}/checklist`, {
        params: { required: required.join(',') },
      })).data,
    enabled: !!subjectRef && required.length > 0,
    retry: 1,
  });

  return {
    warnings: query.data ?? [],
    unavailable: !!subjectRef && required.length > 0 && query.isError,
  };
}

/**
 * 이름으로 가명을 찾는다.
 *
 * 업무 쪽에는 이름이 없으므로 먼저 여기서 가명을 받아 그것으로 걸러야 한다.
 * 원내에 닿지 못하면 이름 검색만 못 하고, 요청번호 검색은 그대로 된다.
 */
export async function searchSubjectRefs(name: string): Promise<string[]> {
  if (!name.trim()) return [];
  const { data } = await phi.get<string[]>('/subjects/search', { params: { name: name.trim() } });
  return data;
}

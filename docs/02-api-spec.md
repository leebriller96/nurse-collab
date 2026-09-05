# 병원 파트 간 간호 협업 시스템 - REST API 명세 v1

작성: 2026-09-04 (KST)
대상 범위: Phase 0 ~ Phase 2
Base URL: `/api/v1`
인증: Bearer JWT (Access 30분 / Refresh 14일)

---

## 0. 공통 규약

### 0-1. 요청 헤더

```
Authorization: Bearer {accessToken}
Content-Type: application/json
X-Request-Id: {UUID}        -- 선택. 로그 추적용
```

### 0-2. 성공 응답

불필요한 래핑(envelope)을 쓰지 않는다. HTTP 상태코드로 성공/실패를 판단하고
본문에는 데이터만 담는다.

```json
// 단건
{ "id": 101, "requestNo": "TR20260904-0001", "status": "REQUESTED" }
```

```json
// 목록 (Spring Pageable 기준)
{
  "content": [ { "id": 101 }, { "id": 102 } ],
  "page": 0,
  "size": 20,
  "totalElements": 47,
  "totalPages": 3
}
```

### 0-3. 에러 응답 (전 API 공통)

```json
{
  "timestamp": "2026-09-04T22:31:05+09:00",
  "status": 409,
  "code": "TR-002",
  "message": "다른 사용자가 먼저 처리했습니다. 화면을 새로고침해 주세요.",
  "path": "/api/v1/transfer-requests/101/transitions",
  "fieldErrors": [
    { "field": "reason", "reason": "보류 시 사유는 필수입니다." }
  ]
}
```

`message` 는 **화면에 그대로 띄울 수 있는 한국어 문장**으로 작성한다.
프론트가 에러코드별로 문구를 따로 관리하지 않아도 되게 한다.

### 0-4. HTTP 상태코드 사용 기준

| 코드 | 사용 상황 |
|---|---|
| 200 | 조회/수정 성공 |
| 201 | 생성 성공 (Location 헤더 포함) |
| 204 | 삭제 성공 |
| 400 | 입력값 검증 실패 |
| 401 | 토큰 없음/만료 |
| 403 | 권한 없음 (타 파트 자원 접근 등) |
| 404 | 대상 없음 |
| 409 | 상태 전이 불가 / 낙관적 락 충돌 |
| 422 | 비즈니스 규칙 위반 |
| 500 | 서버 오류 |

### 0-5. 에러코드 체계

| 코드 | 의미 |
|---|---|
| AUTH-001 | 아이디 또는 비밀번호 불일치 |
| AUTH-002 | 토큰 만료 |
| AUTH-003 | 비활성 계정 |
| PERM-001 | 소속 파트가 아닌 자원 접근 |
| PERM-002 | 역할 권한 부족 |
| TR-001 | 허용되지 않는 상태 전이 |
| TR-002 | 낙관적 락 충돌 (동시 처리) |
| TR-003 | 보류/취소 사유 누락 |
| TR-004 | 이미 종료된 요청 |
| ENC-001 | 퇴원한 재원 건에 대한 요청 |

---

## 1. 인증

### POST /auth/login

```json
// Request
{ "loginId": "nurse01", "password": "P@ssw0rd!" }
```

```json
// Response 200
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "staff": {
    "id": 12,
    "name": "김간호",
    "role": "NURSE",
    "department": { "id": 3, "code": "W03", "name": "3병동", "deptType": "WARD" }
  }
}
```

`deptType` 을 응답에 포함시키는 이유: 프론트가 **병동 화면과 검사실 화면을 분기**해야 하기 때문.

### POST /auth/refresh

```json
// Request
{ "refreshToken": "eyJhbGci..." }
// Response 200
{ "accessToken": "eyJhbGci...", "refreshToken": "eyJhbGci..." }
```

### POST /auth/logout — 204
### GET /auth/me — 200 (로그인 응답의 `staff` 와 동일 구조)

---

## 2. 마스터 조회

### GET /departments

| 파라미터 | 타입 | 설명 |
|---|---|---|
| deptType | String | WARD/EXAM/OR/ICU/ER (선택) |

```json
[
  { "id": 3, "code": "W03", "name": "3병동", "deptType": "WARD", "phone": "1303" },
  { "id": 7, "code": "MRI", "name": "MRI실", "deptType": "EXAM", "phone": "1707" }
]
```

### GET /exam-types

| 파라미터 | 타입 | 설명 |
|---|---|---|
| departmentId | Long | 특정 검사실의 검사만 (선택) |

```json
[
  {
    "id": 21,
    "code": "MRI_BRAIN",
    "name": "뇌 MRI",
    "department": { "id": 7, "name": "MRI실" },
    "defaultDuration": 40,
    "prepInstruction": "검사 4시간 전부터 금식",
    "requiredAlerts": ["METAL_IMPLANT", "CLAUSTROPHOBIA", "CONTRAST_ALLERGY"]
  }
]
```

---

## 3. 환자 / 재원

### GET /encounters

내 파트의 재원 환자 목록.

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| departmentId | Long | 내 소속 | 관리자만 타 파트 조회 가능 |
| status | String | ADMITTED | ADMITTED/DISCHARGED |
| keyword | String | | 환자명 또는 등록번호 |
| page, size | Int | 0, 20 | |

```json
{
  "content": [
    {
      "encounterId": 501,
      "patientNo": "P0001234",
      "name": "김OO",
      "birthDate": "1958-03-11",
      "age": 68,
      "sex": "M",
      "roomNo": "302",
      "bedNo": "1",
      "admittedAt": "2026-09-01T10:20:00+09:00",
      "diagnosis": "뇌경색",
      "alertSummary": [
        { "alertType": "FALL_RISK", "severity": "WARN" },
        { "alertType": "METAL_IMPLANT", "severity": "CRITICAL" }
      ],
      "activeRequestCount": 1
    }
  ],
  "page": 0, "size": 20, "totalElements": 34, "totalPages": 2
}
```

### GET /encounters/{encounterId}

**이 시스템에서 가장 중요한 API.**
호출자의 소속 파트 유형에 따라 **응답 필드가 달라진다.**

```json
// 병동(WARD) 간호사가 호출했을 때 - 전체 정보
{
  "encounterId": 501,
  "patient": { "patientNo": "P0001234", "name": "김OO", "birthDate": "1958-03-11", "sex": "M" },
  "department": { "id": 3, "name": "3병동" },
  "roomNo": "302", "bedNo": "1",
  "admittedAt": "2026-09-01T10:20:00+09:00",
  "diagnosis": "뇌경색",
  "isMobile": false,
  "alerts": [ /* 전체 alert */ ],
  "latestVitalSign": { "measuredAt": "...", "temperature": 36.8, "pulse": 72 },
  "activeRequests": [ { "id": 101, "examName": "뇌 MRI", "status": "ACCEPTED" } ]
}
```

```json
// 검사실(EXAM) 간호사가 호출했을 때 - 필요한 것만
{
  "encounterId": 501,
  "patient": { "patientNo": "P0001234", "name": "김OO", "age": 68, "sex": "M" },
  "fromDepartment": { "id": 3, "name": "3병동" },
  "roomNo": "302",
  "isMobile": false,
  "alerts": [
    { "alertType": "METAL_IMPLANT", "severity": "CRITICAL", "content": "좌측 고관절 인공관절" }
  ],
  "checklistWarnings": [
    { "alertType": "METAL_IMPLANT", "message": "MRI 금기 가능성. 시행 전 확인 필요." }
  ]
}
```

- 진단명, 활력징후, 상세 간호기록은 **응답 자체에서 빠진다.** 마스킹이 아니라 미포함이다.
- `checklistWarnings` 는 `exam_type.required_alerts` 와 환자 alert 를 교차 계산한 결과다.
- 조회 시점에 `audit_log` 에 VIEW 기록이 남는다.

### GET /encounters/{encounterId}/alerts — 200
### POST /patients/{patientId}/alerts

```json
// Request
{ "alertType": "CONTRAST_ALLERGY", "severity": "CRITICAL", "content": "요오드 조영제 아나필락시스 이력" }
// Response 201
```

---

## 4. 이송 요청 (핵심)

### POST /transfer-requests

```json
// Request
{
  "encounterId": 501,
  "examTypeId": 21,
  "priority": "URGENT",
  "desiredAt": "2026-09-04T15:00:00+09:00",
  "note": "휠체어 이송 필요, 보호자 동반"
}
```

```json
// Response 201  (Location: /api/v1/transfer-requests/101)
{
  "id": 101,
  "requestNo": "TR20260904-0001",
  "status": "REQUESTED",
  "priority": "URGENT",
  "requestedAt": "2026-09-04T14:12:00+09:00",
  "version": 0
}
```

- `toDepartmentId` 는 클라이언트가 보내지 않는다. `examTypeId` 로 서버가 결정한다.
- 생성 즉시 대상 검사실 파트에 WebSocket 알림이 발송된다.

### GET /transfer-requests

| 파라미터 | 타입 | 설명 |
|---|---|---|
| direction | String | **INBOUND**(우리 파트로 온 요청) / **OUTBOUND**(우리가 보낸 요청) |
| status | String[] | 다중 지정 가능. 미지정 시 진행중 전체 |
| date | Date | 기본 오늘 |
| priority | String | ROUTINE/URGENT/EMERGENCY |
| page, size | Int | |

`direction` 하나로 병동 화면과 검사실 화면을 동일 API로 처리한다.
`from_department_id` / `to_department_id` 중 무엇으로 필터링할지를 서버가 판단한다.

```json
{
  "content": [
    {
      "id": 101,
      "requestNo": "TR20260904-0001",
      "status": "ACCEPTED",
      "priority": "URGENT",
      "patient": { "name": "김OO", "patientNo": "P0001234", "age": 68, "sex": "M" },
      "roomNo": "302",
      "examName": "뇌 MRI",
      "counterpartDepartment": { "id": 7, "name": "MRI실" },
      "requestedAt": "2026-09-04T14:12:00+09:00",
      "scheduledAt": "2026-09-04T15:30:00+09:00",
      "waitingMinutes": 18,
      "criticalAlertCount": 1,
      "unreadMessageCount": 2,
      "version": 3
    }
  ]
}
```

`counterpartDepartment` : 병동이 보면 검사실, 검사실이 보면 병동. 상대 파트를 뜻한다.

### GET /transfer-requests/{id}

```json
{
  "id": 101,
  "requestNo": "TR20260904-0001",
  "status": "ACCEPTED",
  "priority": "URGENT",
  "encounter": { "encounterId": 501, "roomNo": "302", "bedNo": "1", "isMobile": false },
  "patient": { "patientNo": "P0001234", "name": "김OO", "age": 68, "sex": "M" },
  "examType": { "id": 21, "name": "뇌 MRI", "defaultDuration": 40, "prepInstruction": "검사 4시간 전부터 금식" },
  "fromDepartment": { "id": 3, "name": "3병동", "phone": "1303" },
  "toDepartment": { "id": 7, "name": "MRI실", "phone": "1707" },
  "requestedBy": { "id": 12, "name": "김간호" },
  "requestedAt": "2026-09-04T14:12:00+09:00",
  "desiredAt": "2026-09-04T15:00:00+09:00",
  "scheduledAt": "2026-09-04T15:30:00+09:00",
  "note": "휠체어 이송 필요, 보호자 동반",
  "alerts": [
    { "alertType": "METAL_IMPLANT", "severity": "CRITICAL", "content": "좌측 고관절 인공관절" }
  ],
  "checklistWarnings": [
    { "alertType": "METAL_IMPLANT", "message": "MRI 금기 가능성. 시행 전 확인 필요." }
  ],
  "availableTransitions": ["READY", "ON_HOLD", "CANCELLED"],
  "version": 3
}
```

**`availableTransitions` 가 핵심이다.**
현재 상태 + 호출자 파트 + 역할을 서버가 계산해서 "지금 누를 수 있는 버튼 목록"을 내려준다.
프론트는 이 배열만 보고 버튼을 렌더링하면 된다. 상태 전이 규칙을 프론트에 중복 구현하지 않는다.

### POST /transfer-requests/{id}/transitions

상태 변경 전용 단일 엔드포인트.

```json
// Request
{
  "toStatus": "ACCEPTED",
  "scheduledAt": "2026-09-04T15:30:00+09:00",
  "reason": null,
  "version": 3
}
```

```json
// Response 200
{
  "id": 101,
  "status": "ACCEPTED",
  "scheduledAt": "2026-09-04T15:30:00+09:00",
  "availableTransitions": ["READY", "ON_HOLD", "CANCELLED"],
  "version": 4
}
```

**왜 `/accept`, `/ready`, `/start` 로 나누지 않는가**

1. 상태가 9개인데 엔드포인트가 9개로 늘어나면 권한 검증 코드가 9곳에 흩어진다
2. 이력(`transfer_event`) 기록 로직이 중복된다
3. 상태를 추가할 때마다 API 문서와 프론트 코드를 같이 고쳐야 한다
4. 하나로 두면 전이 검증 → 권한 검증 → 상태 변경 → 이력 적재 → 알림 발송이 **한 흐름**으로 정리된다

단점은 요청 본문이 상태별로 조금씩 달라진다는 것인데,
`toStatus` 기준으로 검증 규칙을 분기하면 관리 가능한 수준이다.

**필수 규칙**
- `version` 미포함 또는 불일치 → `409 TR-002`
- `ON_HOLD`, `CANCELLED` 인데 `reason` 없음 → `400 TR-003`
- 허용되지 않는 전이 → `409 TR-001`
- `ACCEPTED` 인데 `scheduledAt` 없음 → `400`

### GET /transfer-requests/{id}/events

```json
[
  { "id": 1, "fromStatus": null, "toStatus": "REQUESTED", "actor": { "name": "김간호", "departmentName": "3병동" }, "occurredAt": "2026-09-04T14:12:00+09:00", "reason": null },
  { "id": 2, "fromStatus": "REQUESTED", "toStatus": "ON_HOLD", "actor": { "name": "박간호", "departmentName": "MRI실" }, "occurredAt": "2026-09-04T14:25:00+09:00", "reason": "응급 환자 우선 진행" },
  { "id": 3, "fromStatus": "ON_HOLD", "toStatus": "ACCEPTED", "actor": { "name": "박간호", "departmentName": "MRI실" }, "occurredAt": "2026-09-04T14:30:00+09:00", "reason": null }
]
```

---

## 5. 요청 내 대화

### GET /transfer-requests/{id}/messages — 200 (오름차순)
### POST /transfer-requests/{id}/messages

```json
// Request
{ "content": "환자 지금 준비 완료됐습니다. 바로 출발할까요?" }
// Response 201
{ "id": 88, "sender": { "id": 12, "name": "김간호" }, "content": "...", "createdAt": "..." }
```

---

## 6. 간호기록

### POST /encounters/{encounterId}/vital-signs

```json
{
  "measuredAt": "2026-09-04T14:00:00+09:00",
  "temperature": 36.8, "pulse": 72, "respiration": 18,
  "sbp": 128, "dbp": 78, "spo2": 98, "painScore": 2
}
```

### GET /encounters/{encounterId}/vital-signs?from=&to=&page=&size=

### POST /encounters/{encounterId}/nursing-notes

```json
{
  "noteType": "SBAR",
  "situation": "22시경 어지러움 호소",
  "background": "뇌경색으로 입원 4일차, 낙상 위험 등급 상",
  "assessment": "혈압 98/60, 기립성 저혈압 의심",
  "recommendation": "야간 화장실 이동 시 반드시 동반, 당직의 보고 완료",
  "recordedAt": "2026-09-04T22:10:00+09:00"
}
```

### GET /encounters/{encounterId}/nursing-notes?noteType=&page=&size=

---

## 7. 알림

### GET /notifications?unreadOnly=true&page=0&size=20

```json
{
  "content": [
    {
      "id": 900,
      "notiType": "STATUS_CHANGED",
      "refType": "TRANSFER_REQUEST",
      "refId": 101,
      "title": "MRI실에서 요청을 접수했습니다",
      "body": "302호 김OO / 뇌 MRI / 15:30 예정",
      "readAt": null,
      "createdAt": "2026-09-04T14:30:00+09:00"
    }
  ],
  "unreadCount": 5
}
```

### PATCH /notifications/{id}/read — 204
### POST /notifications/read-all — 204

---

## 8. 통계 (시연용)

### GET /stats/waiting-time?from=2026-09-01&to=2026-09-04

```json
{
  "period": { "from": "2026-09-01", "to": "2026-09-04" },
  "overall": { "totalRequests": 214, "avgWaitingMinutes": 23, "avgTotalMinutes": 71 },
  "byDepartment": [
    { "departmentId": 7, "departmentName": "MRI실", "requestCount": 62, "avgWaitingMinutes": 31, "holdCount": 8 },
    { "departmentId": 8, "departmentName": "CT실", "requestCount": 95, "avgWaitingMinutes": 14, "holdCount": 2 }
  ],
  "byHour": [ { "hour": 9, "requestCount": 18 }, { "hour": 10, "requestCount": 27 } ]
}
```

---

## 9. WebSocket (STOMP)

### 연결

```
엔드포인트 : /ws
핸드셰이크 : Authorization: Bearer {accessToken}
```

### 구독 채널

| 채널 | 대상 | 용도 |
|---|---|---|
| `/topic/department/{departmentId}` | 파트 전체 | 신규 요청, 상태 변경 |
| `/user/queue/notifications` | 개인 | 개인 알림 |

### 수신 페이로드

```json
{
  "eventType": "TRANSFER_STATUS_CHANGED",
  "requestId": 101,
  "requestNo": "TR20260904-0001",
  "fromStatus": "REQUESTED",
  "toStatus": "ACCEPTED",
  "priority": "URGENT",
  "patientName": "김OO",
  "roomNo": "302",
  "examName": "뇌 MRI",
  "actorName": "박간호",
  "occurredAt": "2026-09-04T14:30:00+09:00"
}
```

`eventType` 종류: `TRANSFER_CREATED`, `TRANSFER_STATUS_CHANGED`, `MESSAGE_CREATED`

**중요: 재접속 시 유실 보정**

WebSocket 은 끊길 수 있다. 병원 와이파이면 더 자주 끊긴다.
재연결 직후 무조건 `GET /transfer-requests?direction=INBOUND` 를 다시 호출해서
현재 상태로 화면을 덮어쓴다. 실시간 메시지는 "빠른 갱신"일 뿐, **진실의 원천은 REST 조회**다.

---

## 10. 권한 요약 (상세 매트릭스는 별도 문서)

| 자원 | NURSE | HEAD_NURSE | ADMIN |
|---|---|---|---|
| 자기 파트 재원 목록 | O | O | O |
| 타 파트 재원 목록 | X | X | O |
| 이송 요청 생성 | O (병동만) | O | O |
| 상태 전이 | O (관련 파트만) | O | O |
| 취소 | 본인 요청만 | 파트 내 전체 | O |
| 통계 조회 | X | 자기 파트 | 전체 |
| 감사로그 조회 | X | X | O |

---

## 11. 다음 단계

1. 권한 매트릭스 상세화 (파트 유형 × 역할 × 자원 × 액션)
2. 패키지 구조 및 도메인 서비스 설계
3. 상태 전이 enum 구현 코드
4. 화면 목록 정의 (병동 / 검사실 / 관리자)

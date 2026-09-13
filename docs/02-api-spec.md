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
  "code": "ORD-002",
  "message": "다른 사용자가 먼저 처리했습니다. 화면을 새로고침해 주세요.",
  "path": "/api/v1/work-orders/101/transitions"
}
```

`fieldErrors` 는 **입력값 검증 실패(`VAL-001`)일 때만** 들어간다.
그 외 에러에서는 아예 필드가 빠진다.

```json
{
  "timestamp": "2026-09-04T22:31:05+09:00",
  "status": 400,
  "code": "VAL-001",
  "message": "입력값을 확인해 주세요.",
  "path": "/api/v1/work-orders/101/transitions",
  "fieldErrors": [
    { "field": "toStatus", "reason": "변경할 상태는 필수입니다." }
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

이 표의 원본은 `03-backend-structure.md` 의 `ErrorCode` enum 이다.
코드를 추가할 때는 enum 을 먼저 고치고 이 표를 맞춘다.

| 코드 | HTTP | 의미 |
|---|---|---|
| VAL-001 | 400 | 입력값 검증 실패 (`fieldErrors` 동봉) |
| VAL-002 | 405 | 지원하지 않는 요청 방식 |
| AUTH-001 | 401 | 아이디 또는 비밀번호 불일치 |
| AUTH-002 | 401 | 토큰 만료 |
| AUTH-003 | 401 | 비활성 계정 |
| PERM-001 | 403 | 요청에 관여하지 않는 파트의 접근 |
| PERM-002 | 403 | 상대 파트가 처리해야 할 전이를 시도 |
| PERM-003 | 403 | 역할 권한 부족 (통계·감사로그 등) |
| ORD-000 | 404 | 요청 없음 |
| ORD-001 | 409 | 허용되지 않는 상태 전이 |
| ORD-002 | 409 | 낙관적 락 충돌 (동시 처리) |
| ORD-003 | 400 | 보류/취소 사유 누락 |
| ORD-004 | 409 | 이미 종료된 요청 |
| ORD-005 | 400 | 접수 시 예정시각 누락 (이송만 요구한다) |
| ORD-006 | 400 | 환자가 필요한 업무인데 재원 정보가 없음 |
| ORD-007 | 400 | 환자를 지정할 수 없는 업무에 대상을 보냄 |
| ENC-000 | 404 | 재원 없음 |
| ALT-000 | 404 | 주의사항 없음 |
| ENC-001 | 422 | 퇴원한 재원 건에 대한 요청 |
| SVC-001 | 404 | 업무 항목 없음 |
| STF-001 | 404 | 직원 없음 |
| SYS-001 | 500 | 서버 내부 오류 |

**PERM-002 와 PERM-003 은 다른 상황이다.**
PERM-002 는 "권한은 있는데 지금 누를 쪽이 아닌" 경우다.
병동이 `ACCEPTED` 를 누르려 할 때가 여기 해당한다 — 접수는 검사실이 한다.
PERM-003 은 역할 자체가 모자란 경우다. 일반 간호사가 통계를 조회할 때가 그렇다.

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
| deptType | String | WARD/EXAM/OR/ICU/ER/ADMIN (선택) |

```json
[
  { "id": 3, "code": "W03", "name": "3병동", "deptType": "WARD", "phone": "1303" },
  { "id": 7, "code": "MRI", "name": "MRI실", "deptType": "EXAM", "phone": "1707" }
]
```

### GET /service-items

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
- `checklistWarnings` 는 `service_item.required_alerts` 와 환자 alert 를 교차 계산한 결과다.
- 조회 시점에 `audit_log` 에 VIEW 기록이 남는다.
- 접근 판정은 소속이 아니라 **관계**로 한다. 검사실은 "우리 파트로 온 진행중 요청이 있을 때" 만 볼 수 있고,
  요청이 끝나면 접근 권한도 함께 사라진다. 관계가 없으면 `403 PERM-001`.
- 활력징후(`latestVitalSign`)는 7단계에서 붙인다.

### GET /encounters/{encounterId}/alerts — 200

### POST /patients/{patientId}/alerts

```json
// Request
{ "alertType": "CONTRAST_ALLERGY", "severity": "CRITICAL", "content": "요오드 조영제 아나필락시스 이력" }
// Response 201
```

주의사항은 간호사가 남긴다. 낙상 위험, 폐소공포 이력, 격리처럼
EMR 의 진단명이 아니라 **곁에서 본 것**이기 때문이다.

접근 판정은 환자 조회와 같다. 관계가 없으면 `403 PERM-001`.
남의 병동 환자에게 주의사항을 붙일 수 있으면 안 된다.

### PATCH /patients/alerts/{alertId}/deactivate — 204

지우지 않고 내린다. 이 주의사항을 보고 판단한 지난 요청이 있기 때문이다.
`GET /encounters/{id}/alerts` 는 내려간 것을 빼고 준다.

---

## 4. 이송 요청 (핵심)

### POST /work-orders

```json
// Request
{
  "encounterId": 501,
  "serviceItemId": 21,
  "priority": "URGENT",
  "desiredAt": "2026-09-04T15:00:00+09:00",
  "note": "휠체어 이송 필요, 보호자 동반"
}
```

```json
// Response 201  (Location: /api/v1/work-orders/101)
{
  "id": 101,
  "requestNo": "TR20260904-0001",
  "status": "REQUESTED",
  "priority": "URGENT",
  "requestedAt": "2026-09-04T14:12:00+09:00",
  "version": 0
}
```

- `toDepartmentId` 는 클라이언트가 보내지 않는다. `serviceItemId` 로 서버가 결정한다.
- 생성 즉시 대상 검사실 파트에 WebSocket 알림이 발송된다.

### GET /work-orders

| 파라미터 | 타입 | 설명 |
|---|---|---|
| direction | String | **INBOUND**(우리 파트로 온 요청) / **OUTBOUND**(우리가 보낸 요청) |
| status | String[] | 다중 지정 가능. 미지정 시 진행중 전체 |
| from, to | Date | 요청 시각 기준 기간. 미지정 시 둘 다 오늘 |
| keyword | String | 환자명 또는 요청번호 |
| priority | String | ROUTINE/URGENT/EMERGENCY |
| page, size | Int | |

`direction` 하나로 병동 화면과 검사실 화면을 동일 API로 처리한다.
기간을 열어 두면 같은 API 가 지난 요청 조회(E-04)도 처리한다.
화면마다 엔드포인트를 나누면 권한 검증과 응답 조립 코드가 그만큼 흩어진다.
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
      "version": 3
    }
  ]
}
```

`counterpartDepartment` : 병동이 보면 검사실, 검사실이 보면 병동. 상대 파트를 뜻한다.

`waitingMinutes` : 요청 시각부터 흐른 시간. 진행중이면 지금까지, 끝났으면 완료 시각까지 센다.
검사실 큐에서 오래 기다린 행을 진하게 칠하는 근거라서 "지금 기준" 이어야 한다.

**`unreadMessageCount` 는 뺐다.** `request_message` 에 읽음 상태가 없어서 계산할 수 없다.
누가 어디까지 읽었는지를 담는 테이블이 필요하므로 Phase 3 으로 미룬다.

### GET /work-orders/{id}

```json
{
  "id": 101,
  "requestNo": "TR20260904-0001",
  "status": "ACCEPTED",
  "priority": "URGENT",
  "encounter": { "encounterId": 501, "roomNo": "302", "bedNo": "1", "isMobile": false },
  "patient": { "patientNo": "P0001234", "name": "김OO", "age": 68, "sex": "M" },
  "serviceItem": { "id": 21, "name": "뇌 MRI", "defaultDuration": 40, "prepInstruction": "검사 4시간 전부터 금식" },
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
  "availableTransitions": [
    { "status": "READY",     "label": "준비완료" },
    { "status": "ON_HOLD",   "label": "보류" },
    { "status": "CANCELLED", "label": "취소" }
  ],
  "version": 3
}
```

**`availableTransitions` 가 핵심이다.**
현재 상태 + 호출자 파트 + 역할을 서버가 계산해서 "지금 누를 수 있는 버튼 목록"을 내려준다.
프론트는 이 배열만 보고 버튼을 렌더링하면 된다. 상태 전이 규칙을 프론트에 중복 구현하지 않는다.

**이름까지 서버가 준다.** 상태값만 내려보내면 화면이 `IN_PROGRESS` 를 무엇이라 부를지
스스로 정해야 하는데, 그 이름이 종류마다 다르다 (검사중 / 조제중 / 수리중).
화면이 그 표를 따로 들면 종류를 더할 때 두 곳을 고쳐야 하고 한쪽만 고쳐지는 날이 온다.
현재 상태의 이름은 `statusLabel` 로 함께 내려간다.

**이 응답에는 환자 정보가 하나도 없다.**
이름도, 진단명도, 주의사항도, 확인 경고도 없다. 있는 것은 `episode` 아래의
침대 번호와 가명(`subjectRef`)뿐이다. 사람은 화면이 `/phi` 로 따로 물어 채운다.
환자가 없는 업무(장비 수리)에서는 `episode` 자체가 없다.

### POST /work-orders/{id}/transitions

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
  "availableTransitions": [
    { "status": "READY", "label": "준비완료" },
    { "status": "ON_HOLD", "label": "보류" },
    { "status": "CANCELLED", "label": "취소" }
  ],
  "version": 4
}
```

**왜 `/accept`, `/ready`, `/start` 로 나누지 않는가**

1. 상태가 9개인데 엔드포인트가 9개로 늘어나면 권한 검증 코드가 9곳에 흩어진다
2. 이력(`work_order_event`) 기록 로직이 중복된다
3. 상태를 추가할 때마다 API 문서와 프론트 코드를 같이 고쳐야 한다
4. 하나로 두면 전이 검증 → 권한 검증 → 상태 변경 → 이력 적재 → 알림 발송이 **한 흐름**으로 정리된다

단점은 요청 본문이 상태별로 조금씩 달라진다는 것인데,
`toStatus` 기준으로 검증 규칙을 분기하면 관리 가능한 수준이다.

**필수 규칙**
- `version` 미포함 또는 불일치 → `409 ORD-002`
- `ON_HOLD`, `CANCELLED` 인데 `reason` 없음 → `400 TR-003`
- 허용되지 않는 전이 → `409 ORD-001`
- `ACCEPTED` 인데 `scheduledAt` 없음 → `400`

### GET /work-orders/{id}/events

```json
[
  { "id": 1, "fromStatus": null, "toStatus": "REQUESTED", "actor": { "name": "김간호", "departmentName": "3병동" }, "occurredAt": "2026-09-04T14:12:00+09:00", "reason": null },
  { "id": 2, "fromStatus": "REQUESTED", "toStatus": "ON_HOLD", "actor": { "name": "박간호", "departmentName": "MRI실" }, "occurredAt": "2026-09-04T14:25:00+09:00", "reason": "응급 환자 우선 진행" },
  { "id": 3, "fromStatus": "ON_HOLD", "toStatus": "ACCEPTED", "actor": { "name": "박간호", "departmentName": "MRI실" }, "occurredAt": "2026-09-04T14:30:00+09:00", "reason": null }
]
```

---

## 5. 요청 내 대화

### GET /work-orders/{id}/messages — 200 (오름차순)
### POST /work-orders/{id}/messages

```json
// Request
{ "content": "환자 지금 준비 완료됐습니다. 바로 출발할까요?" }
// Response 201
{ "id": 88, "sender": { "id": 12, "name": "김간호" }, "content": "...", "createdAt": "..." }
```

---

## 6. 간호기록

전부 원내 경로(`/phi`) 아래다. 두 서버로 갈라지면 중계 서버가 경로 앞머리만 보고
원내로 보낼지 정하기 때문이다. 열쇠도 재원 id 가 아니라 가명(`subjectRef`)이다.
주소는 브라우저 기록과 중계 서버 로그에 남는다.

### POST /phi/subjects/{subjectRef}/vital-signs

```json
{
  "measuredAt": "2026-09-04T14:00:00+09:00",
  "temperature": 36.8, "pulse": 72, "respiration": 18,
  "sbp": 128, "dbp": 78, "spo2": 98, "painScore": 2
}
```

### GET /phi/subjects/{subjectRef}/vital-signs?from=&to=&page=&size=

### POST /phi/subjects/{subjectRef}/nursing-notes

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

### GET /phi/subjects/{subjectRef}/nursing-notes?noteType=&page=&size=

### PUT /phi/nursing-notes/{noteId}

작성자 본인이 24시간 안에만 고칠 수 있다. 삭제는 없다.
시간이 지난 기록은 고치는 대신 정정 기록을 새로 남긴다.
고치기 전 내용은 별도 이력 테이블 없이 원내 `phi_access_log.detail` 에 before/after 로 남는다.

| 코드 | HTTP | 상황 |
|---|---|---|
| NN-001 | 403 | 본인이 쓴 기록이 아님 |
| NN-002 | 422 | 작성 후 24시간 경과 |
| NN-003 | 400 | 내용이 비어 있음 |

`editable` 은 서버가 계산해 내려준다. 24시간 규칙을 화면에서 다시 구현하면 서버와 어긋난다.

---

## 7-2. 마스터 관리 (관리자)

조회는 누구나 하지만 변경은 관리자만 한다. 다른 역할은 `403 PERM-003`.

**삭제 대신 비활성화한다.** 부서나 검사 종류를 지우면 그것을 참조하던 지난 요청의
이력이 읽을 수 없게 된다. 기록은 남기고 새 요청에서만 고르지 못하게 한다.

### POST /departments · PUT /departments/{id} · PATCH /departments/{id}/deactivate

```json
{
  "code": "W07",
  "name": "7병동",
  "deptType": "WARD",
  "location": "본관 7층",
  "phone": "1707"
}
```

### POST /staff · PUT /staff/{id} · PATCH /staff/{id}/deactivate

```json
{
  "loginId": "ward07",
  "password": "초기 비밀번호",
  "employeeNo": "E10009",
  "name": "한간호",
  "role": "NURSE",
  "departmentId": 1,
  "phone": "1702"
}
```

비밀번호는 생성할 때만 받는다. 수정에서 다루면 관리자가 남의 비밀번호를 바꿀 수 있게 된다.
초기화가 필요하면 별도 엔드포인트로 분리한다(Phase 3).

### POST /service-items · PUT /service-items/{id} · PATCH /service-items/{id}/deactivate

```json
{
  "code": "MRI_CSPINE",
  "name": "경추 MRI",
  "departmentId": 3,
  "defaultDuration": 35,
  "prepInstruction": "검사 4시간 전부터 금식",
  "requiredAlerts": ["METAL_IMPLANT", "CLAUSTROPHOBIA"]
}
```

`requiredAlerts` 가 검사실 체크리스트 경고의 근거다.
여기를 비우면 그 검사는 아무 경고도 띄우지 않는다.

| 코드 | HTTP | 상황 |
|---|---|---|
| MST-001 | 409 | 코드나 아이디가 이미 있음 |
| MST-002 | 404 | 대상 없음 |

---

## 8-2. 감사 로그 (관리자)

감사 기록은 두 곳에 나뉘어 남는다.

| 경로 | 무엇이 남나 | 사는 곳 |
|---|---|---|
| `GET /phi/access-logs?from=&to=&patientNo=&page=&size=` | 환자 열람, 활력징후·간호기록 열람과 작성, 간호기록 수정 전후, 주의사항 — **거절된 시도 포함** | 원내 |
| `GET /audit-logs?from=&to=&actorId=&page=&size=` | 업무 쪽 행위. 환자 칸이 없다 | 업무 |

A-05 화면은 원내 경로를 읽는다. 간호기록 수정 전 내용은 그 자체가 진료정보라
업무 쪽에 두면 나눈 의미가 사라진다.

```json
{
  "content": [
    {
      "id": 9001,
      "occurredAt": "2026-09-06T02:47:18+09:00",
      "action": "NOTE_EDIT",
      "granted": true,
      "deniedReason": null,
      "actor": { "id": 11, "loginId": "ward01", "departmentId": 1 },
      "patient": { "patientNo": "P0001234", "name": "김OO" },
      "ipAddress": "10.0.0.12",
      "detail": { "noteId": 42, "before": { "content": "..." }, "after": { "content": "..." } }
    }
  ]
}
```

- 누가는 아이디와 소속 id 까지만 담긴다. 직원 이름은 업무 쪽에 있어서, 기록이 쌓일 때
  아이디를 문자열로 굳혀 둔다.
- 등록번호로 좁히는 조건은 서버에서 건다. 화면에서 거르면 한 페이지 안에서만 걸러진다.
- 관리자만 본다(`403 PERM-003`).

---

## 6-1. 원내 전용 — `/api/v1/phi`

환자 정보는 업무 응답에 실리지 않는다. 화면이 가명을 들고 여기로 다시 묻는다.

경로를 나눠 둔 이유는 **나중에 다른 서버가 되기 때문**이다.
지금은 같은 앱이 둘 다 들고 있지만, 원내 게이트웨이는 사설망 주소로만 열린다.
경로가 갈려 있어야 무엇이 원내에만 있어야 하는지 한눈에 보인다.

접근 판정은 업무 쪽과 똑같다. "소속이 검사실이니까" 가 아니라
**"우리 파트로 온 진행중 요청이 이 대상에 걸려 있으니까"** 로 본다.
가명을 손에 넣어도 그것만으로는 아무것도 열리지 않는다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/phi/subjects/{subjectRef}` | 가명 하나를 사람으로. 이 호출이 감사 로그에 남는다 |
| POST | `/phi/subjects/brief` | 목록 한 화면을 한 번에. 몸통에 가명 배열 |
| GET | `/phi/subjects/{subjectRef}/alerts` | 지금 붙어 있는 주의사항 |
| POST | `/phi/subjects/{subjectRef}/alerts` | 주의사항 남기기 |
| PATCH | `/phi/alerts/{alertId}/deactivate` | 내리기 (지우지 않는다) |
| GET | `/phi/subjects/{subjectRef}/checklist?required=` | 이 업무 전에 확인할 것 |
| GET | `/phi/subjects/search?name=` | 이름으로 가명 찾기. 나가는 것은 가명뿐이다 |

### `/encounters` 는 없어졌다

병동 보드와 환자 상세가 이 경로로 재원 목록을 받았고, 그 응답에 이름과 진단명이
실려 있었다. 그래서 **원내망 밖에서 이 화면만 이름이 그대로 보였다.**
다른 화면은 사라지는데 이 화면만 아니면, 어디까지가 원내인지 아무도 기억하지 못한다.

이제 화면 한 장을 두 곳에서 받아 합친다.

| 화면 | 업무 쪽 | 원내 |
|---|---|---|
| W-01 병동 보드 | `GET /care-episodes` — 침대·진행중 요청 수 | `POST /phi/subjects/brief` |
| W-02 환자 상세 | `GET /care-episodes/{ref}` — 침대·진행중 요청 | `GET /phi/subjects/{ref}` |

`/care-episodes` 응답에는 사람이 없다. 감사 로그도 남기지 않는다 —
환자 정보를 열어본 것이 아니기 때문이다.

### 목록 조회가 POST 인 이유

큐 한 화면이면 가명이 스무 개 넘게 붙는다. 주소창에 담으면 길이 제한에 걸리고
중계 서버 접근 로그에 그대로 쌓인다. 가명이라 사람을 알아볼 수는 없지만
굳이 흘려 둘 이유도 없다.

### 목록은 일부만 돌려줄 수 있다

볼 자격이 없는 가명은 예외를 던지지 않고 빠뜨린다.
큐 한 화면에 섞여 들어온 남의 요청 하나 때문에 화면 전체가 못 뜨면
간호사는 자기 일까지 못 본다. 대신 그 줄은 이름이 비어 있게 된다.

### 확인 항목을 부르는 쪽이 들고 오는 이유

무엇을 확인해야 하는지는 업무 쪽(업무 항목의 `requiredAlerts`)이 알고,
그 사람에게 그 항목이 있는지는 원내가 안다. 겹치는 지점이 경고다.
원내는 업무 항목이 무엇인지 모르고, 알 이유도 없다.

### 이름으로 찾기

업무 목록에는 이름이 없으므로 `keyword` 로 이름을 찾을 수 없다.
화면이 먼저 `/phi/subjects/search` 로 가명 목록을 받아
`GET /work-orders?subjectRefs=...` 로 넘긴다.
원내에 닿지 못하면 **이름 검색만** 안 되고 요청번호 검색은 그대로 된다.

### 닿지 못했을 때

화면은 빈칸을 보여주지 않고 "원내망에서만 조회됩니다" 라고 말한다.
특히 확인 경고는 감추지 않는다 — "경고가 없다" 와 "경고를 못 받았다" 를
같게 보여주면 금기 환자를 그냥 검사실로 보내게 된다.

---

## 7. 알림

### GET /notifications?unreadOnly=true&page=0&size=20

```json
{
  "content": [
    {
      "id": 900,
      "notiType": "STATUS_CHANGED",
      "refType": "WORK_ORDER",
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
엔드포인트   : /ws  (STOMP over WebSocket)
CONNECT 헤더 : Authorization: Bearer {accessToken}
```

브라우저의 WebSocket 은 **핸드셰이크에 임의 헤더를 넣을 수 없다.**
그래서 토큰은 STOMP CONNECT 프레임의 네이티브 헤더로 보낸다.
쿼리스트링에 실으면 접속 로그와 프록시 로그에 토큰이 그대로 남는다.

### 구독 채널

| 채널 | 대상 | 용도 |
|---|---|---|
| `/topic/department/{departmentId}` | 파트 전체 | 신규 요청, 상태 변경 |

개인 채널(`/user/queue/notifications`)은 두지 않는다.

알림함의 미읽음 수는 파트 채널이 오면 조회 캐시를 무효화해 다시 받아오고,
그때 서버가 **그 사람의** 미읽음만 세어 준다. 개인 채널을 따로 두어도
같은 결과에 연결만 하나 더 는다.

파트 전체가 아니라 한 사람에게만 보내야 하는 알림이 생기면 그때 추가한다.

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
  "actorId": 5,
  "actorName": "박간호",
  "actorDepartmentName": "MRI실",
  "occurredAt": "2026-09-04T14:30:00+09:00"
}
```

`eventType` 종류: `TRANSFER_CREATED`, `TRANSFER_STATUS_CHANGED`, `MESSAGE_CREATED`

페이로드에 사람이 읽을 수 있는 필드를 함께 싣는 것은 **토스트 문구를 만들기 위해서**다.
화면을 이 내용으로 그리라는 뜻이 아니다. 목록과 상세는 반드시 REST 로 다시 받아온다.

`actorId` 는 **자기가 한 일을 자기에게 알리지 않기 위해** 싣는다.
방송은 파트 채널로 나가므로 누른 사람에게도 되돌아온다.
그대로 두면 버튼을 누른 사람에게 "동작했습니다" 확인과 "누가 무엇을 했다" 알림이
동시에 뜬다. 알림함이 이미 지키는 규칙(행위자 본인은 뺀다)과 같은 이유다.

이름으로 거르지 않는 이유는 동명이인이 있기 때문이다.

**중요: 재접속 시 유실 보정**

WebSocket 은 끊길 수 있다. 병원 와이파이면 더 자주 끊긴다.
재연결 직후 무조건 `GET /work-orders?direction=INBOUND` 를 다시 호출해서
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

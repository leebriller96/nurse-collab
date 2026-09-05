# CLAUDE.md

> 이 파일은 저장소 루트에 `CLAUDE.md` 라는 이름으로 두면
> Claude Code 가 세션 시작 시 자동으로 읽는다.

---

## 프로젝트 개요

병원 파트 간 간호 협업 시스템 (nurse-collab).

병동 간호사와 검사실(MRI/CT 등) 간호사 사이의 **환자 이송 요청**을
전화 대신 시스템으로 처리하고, 모든 과정을 기록으로 남기는 것이 목적이다.

EMR 대체가 아니라 **EMR 옆에 붙는 협업 레이어**로 포지셔닝한다.

## 기술 스택

| 영역 | 스택 |
|---|---|
| 언어/프레임워크 | Java 21, Spring Boot 3.3 |
| 데이터 | PostgreSQL 16, Flyway, Spring Data JPA |
| 캐시/메시징 | Redis (Pub/Sub) |
| 실시간 | Spring WebSocket + STOMP |
| 인증 | Spring Security + JWT |
| 프론트 | React 19, TypeScript, Vite, TanStack Query, Tailwind, shadcn/ui |
| PWA | vite-plugin-pwa |
| 테스트 | JUnit 5, AssertJ, Testcontainers |
| 빌드/배포 | Gradle, Docker Compose, GitHub Actions |

## 설계 문서

작업 전 반드시 참고할 것. `/docs` 에 있다.

| 파일 | 내용 |
|---|---|
| `docs/01-db-schema.md` | 테이블 13개 DDL, 설계 근거 |
| `docs/02-api-spec.md` | REST API 명세, 에러코드 체계, WebSocket 규격 |
| `docs/03-backend-structure.md` | 패키지 구조, 상태 전이 코드 |
| `docs/04-screens-permissions.md` | 화면 17개 정의, 권한 매트릭스 |

문서와 코드가 어긋나면 **문서를 먼저 고치고** 코드를 작성한다.

## 절대 규칙

### 1. 상태 전이 규칙은 `TransferStatus` enum 안에만 존재한다

서비스나 컨트롤러에 `if (status == ...)` 분기를 만들지 않는다.
새 상태나 전이가 필요하면 `TransferStatus.RULES` 에만 추가한다.

### 2. 검증은 엔티티가 한다

`TransferRequest.transitionTo()` 안에서 검증하고 예외를 던진다.
서비스는 조회/저장/이벤트 발행만 담당한다.

### 3. 알림은 반드시 커밋 이후에 발송한다

`@TransactionalEventListener(phase = AFTER_COMMIT)` 만 사용한다.
서비스 안에서 `messagingTemplate.convertAndSend()` 를 직접 호출하지 않는다.

### 4. 환자 정보 접근은 소속이 아니라 관계로 판단한다

```java
// 금지
if (staff.getDepartment().getDeptType() == EXAM) return true;

// 필수
transferRequestRepository.existsByEncounterIdAndToDepartmentIdAndStatusNotIn(...)
```

### 5. 간호기록과 이송 이력은 삭제하지 않는다

`transfer_event`, `nursing_note` 는 append only.
DELETE 쿼리나 삭제 API 를 만들지 않는다.

### 6. 환자 데이터는 전부 가상 인물이다

실명, 실제 등록번호, 실제 병원명을 절대 넣지 않는다.
테스트 픽스처는 `김OO`, `P0001234` 형태로 만든다.

## 코딩 컨벤션

- 주석은 **한글**로 작성한다. "무엇을"이 아니라 **"왜"** 를 쓴다
- 엔티티에 `@Setter` 를 붙이지 않는다. 상태 변경은 의미 있는 메서드로 표현한다
  (`setStatus()` 금지, `transitionTo()` 사용)
- 생성자는 `protected` 로 막고 정적 팩토리 메서드(`create`, `of`)를 쓴다
- DTO 는 `record` 로 만든다
- 연관관계는 전부 `LAZY`
- 응답 DTO 를 엔티티로 직접 반환하지 않는다
- 테스트 메서드명은 한글로 쓴다 (`두_명이_동시에_접수하면_한_명은_실패한다`)

## 작업 방식

- 기능 단위로 작업하고, 끝날 때마다 테스트를 돌린다
- 상태 전이 관련 변경은 **반드시 단위 테스트를 먼저 작성**한다
- 통합 테스트는 Testcontainers 로 진짜 PostgreSQL 을 띄운다. H2 금지
  (JSONB, 파티셔닝 문법이 H2 에서 깨진다)

## 커밋 컨벤션

```
feat: 이송 요청 상태 전이 API 구현
fix: 보류 해제 시 직전 상태 복원 오류 수정
test: 동시 접수 시 낙관적 락 충돌 테스트 추가
docs: API 명세에 통계 엔드포인트 추가
refactor: 상태 전이 규칙을 enum 으로 이동
chore: Testcontainers 의존성 추가
```

한 커밋에 한 가지 목적만 담는다.

## 개발 진행 순서

1. 프로젝트 생성 + Flyway 스키마 (V1, V2)
2. 인증/권한 + 마스터 CRUD
3. **이송 요청 도메인 + 단위 테스트** ← 여기가 프로젝트의 핵심
4. 이송 API + 화면 (병동 W-01~05, 검사실 E-01~02)
5. WebSocket 실시간 연동
6. 동시성 처리 + 통계 화면
7. 간호기록 + 감사 로그

현재 위치: **7단계까지 전부 완료**

- 1단계: Gradle 프로젝트, Flyway, docker-compose, `global` 공통 기반.
- 2단계: JWT 인증, 파트·직원·검사종류, 마스터 조회 API.
- 3단계: 상태 전이 규칙(`TransferStatus`), 이송 요청 도메인, 요청번호 발번(V4).
- 4단계: 백엔드 API 전체 + 화면 7개(C-01 로그인, W-01 보드, W-02 환자상세, W-03 요청등록,
  W-04 현황, W-05/E-02 요청상세, E-01 큐). frontend/ 하위에 React 19 + Vite.
  실시간 갱신은 5단계에서 붙였다.
- 5단계: STOMP WebSocket. 요청 생성·상태 전이·메시지가 양쪽 파트 채널로 즉시 전파된다.
  알림은 AFTER_COMMIT 리스너에서만 나간다. 프론트는 알림으로 화면을 그리지 않고
  조회 캐시를 무효화해 REST 로 다시 받아온다. 폴링은 60초 보조 장치로만 남겼다.
- 6단계: 대기시간 통계 API 와 대시보드(A-01). 집계는 SQL 로 하고, 값은 전부 transfer_event 에서 나온다.
  일반 간호사는 403, 수간호사는 자기 파트가 관여한 요청만, 관리자는 전체를 본다.
  동시성 처리(낙관적 락)는 3단계에서 이미 끝났고 화면에서도 확인했다.
- 7단계(백엔드): 활력징후, 간호기록(SBAR), 감사 로그.
  간호기록은 삭제하지 않는다. 본인이 24시간 안에만 고칠 수 있고, 고치기 전 내용은
  별도 이력 테이블 없이 audit_log.detail 에 before/after 로 남는다.
  환자 정보를 열어본 것도 @Audited + AOP 로 자동 적재된다.
- 7단계(화면): 활력징후 입력(W-06), 간호기록(W-07), 접근 기록 조회(A-05).
  설계 문서의 우선순위 1 화면 10개와 우선순위 2~3 중 위 3개까지 만들었다.
  남은 화면: 일정 보드(E-03), 이력 조회(E-04), 관리자 마스터 CRUD(A-02~A-04), 알림함(C-02).
- 보류 중: 관리자 마스터 CRUD(A-02~A-04)는 `02-api-spec.md` 에 엔드포인트가 없어
  **문서를 먼저 추가한 뒤** 구현한다.

### 구현된 API

| 메서드 | 경로 | 화면 |
|---|---|---|
| POST | `/auth/login` `/auth/refresh` `/auth/logout` | C-01 |
| GET | `/auth/me` | C-03 |
| GET | `/departments` `/exam-types` | W-03, A-04 |
| GET | `/encounters` | W-01 |
| GET | `/encounters/{id}` `/encounters/{id}/alerts` | W-02, E-02 |
| POST | `/transfer-requests` | W-03 |
| GET | `/transfer-requests?direction=` | W-04, E-01 |
| GET | `/transfer-requests/{id}` `/{id}/events` | W-05, E-02 |
| POST | `/transfer-requests/{id}/transitions` | W-05, E-02 |
| GET POST | `/transfer-requests/{id}/messages` | W-05, E-02 |

### 데모 계정

비밀번호는 전부 `nurse1234!` 다. 전부 가상 인물이다.

| 아이디 | 역할 | 소속 |
|---|---|---|
| `admin01` | ADMIN | 전산팀 |
| `head01` | HEAD_NURSE | 3병동 |
| `ward01` | NURSE | 3병동 |
| `ward02` | NURSE | 5병동 |
| `mri01` | NURSE | MRI실 |
| `ct01` | NURSE | CT실 |

## 최종 목표 (시연 시나리오)

폰(병동)과 노트북(MRI실)을 나란히 놓고 3분 안에:

1. 폰에서 이송 요청 등록 → 노트북 큐에 즉시 표시
2. 노트북 상세에 "인공관절 - MRI 금기 가능성" 경고 자동 표시
3. 접수 처리 → 폰 화면이 실시간 갱신
4. 창 두 개로 동시 처리 시도 → "다른 사용자가 먼저 처리했습니다"
5. 관리자 화면에서 평균 대기시간 통계 확인

이 시나리오가 끊김 없이 돌아가는 것이 완료 기준이다.

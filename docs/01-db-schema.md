# 병원 파트 간 간호 협업 시스템 - DB 스키마 설계 v1

작성: 2026-09-04 (KST)
대상 범위: Phase 0 ~ Phase 2 (마스터 / 이송 워크플로우 / 간호기록 / 감사로그)
DBMS: PostgreSQL 16

---

## 1. 테이블 한눈에 보기

| 그룹 | 테이블 | 역할 |
|---|---|---|
| 조직 | `department` | 파트(병동, MRI실, 수술실 등) |
| 조직 | `staff` | 간호사·관리자 계정 |
| 환자 | `patient` | 환자 기본정보 (변하지 않는 것) |
| 환자 | `encounter` | 재원 정보 (입원할 때마다 새로 생김) |
| 환자 | `patient_alert` | 파트별 주의사항 (금속물, 알레르기 등) |
| 이송 | `exam_type` | 검사 종류 마스터 |
| 이송 | `transfer_request` | 이송 요청 (핵심 테이블) |
| 이송 | `transfer_event` | 상태 변경 이력 |
| 소통 | `request_message` | 요청 단위 대화 스레드 |
| 소통 | `notification` | 개인별 알림함 |
| 기록 | `vital_sign` | 활력징후 |
| 기록 | `nursing_note` | 간호기록 (SBAR) |
| 감사 | `audit_log` | 접근·변경 감사 추적 |

---

## 2. 설계 핵심 4가지

### (1) patient 와 encounter 를 분리한다

같은 환자가 3년 뒤에 또 입원할 수 있다.
환자 정보(이름, 생년월일)와 재원 정보(병동, 병실, 입원일)를 한 테이블에 넣으면
재입원 시 이전 기록이 뭉개진다. 그래서 무조건 분리한다.

- `patient` : 사람 그 자체 (1명 = 1행, 평생)
- `encounter` : 이번 입원 건 (입원할 때마다 1행 추가)
- 모든 기록(활력징후, 간호기록, 이송요청)은 `encounter_id` 를 바라본다

### (2) 상태는 컬럼, 이력은 별도 테이블

`transfer_request.status` 에 현재 상태를 두고,
상태가 바뀔 때마다 `transfer_event` 에 한 줄씩 쌓는다.

- 현재 상태 조회 = 빠름 (컬럼 하나)
- "누가 언제 왜 보류시켰나" 추적 = 가능 (이력 테이블)
- 나중에 "평균 대기시간" 통계도 이력 테이블만으로 계산된다

### (3) 낙관적 락(version)으로 동시 접수를 막는다

MRI실 간호사 두 명이 동시에 같은 요청의 "접수" 버튼을 누를 수 있다.
`version` 컬럼을 두고 JPA `@Version` 을 걸면 나중에 누른 쪽이 예외로 튕긴다.
실무에서 반드시 나오는 이슈라 면접에서 설명하기 좋은 포인트다.

### (4) audit_log 는 처음부터 분리해서 만든다

의료 시스템은 "누가 어떤 환자 정보를 열람했는가"까지 남겨야 한다.
나중에 붙이려면 전 코드를 뒤져야 하므로 처음부터 AOP로 자동 기록하게 설계한다.
월 단위 파티셔닝을 염두에 두고 `occurred_at` 을 파티션 키로 잡는다.

---

## 3. 이송 요청 상태 정의

```
REQUESTED    요청됨      병동이 요청 버튼을 누른 직후
ACCEPTED     접수됨      검사실이 받아들임 (예정시각 지정)
READY        준비완료    검사실 준비 끝, 환자 보내도 됨
IN_TRANSIT   이송중      환자가 병동을 출발
IN_PROGRESS  검사중      검사 시작
RETURNED     복귀중      검사 끝, 병동으로 이동 중
COMPLETED    완료        병동 도착 확인
ON_HOLD      보류        사유 필수 (장비 고장, 응급 끼어듦 등)
CANCELLED    취소        사유 필수
```

허용 전이:

| 현재 | 다음 가능 상태 |
|---|---|
| REQUESTED | ACCEPTED, ON_HOLD, CANCELLED |
| ACCEPTED | READY, ON_HOLD, CANCELLED |
| READY | IN_TRANSIT, ON_HOLD, CANCELLED |
| IN_TRANSIT | IN_PROGRESS, ON_HOLD |
| IN_PROGRESS | RETURNED |
| RETURNED | COMPLETED |
| ON_HOLD | 직전 상태로 복귀, CANCELLED |
| COMPLETED / CANCELLED | (종료) |

이 전이표는 애플리케이션 코드에서 enum 으로 강제한다.
DB CHECK 제약으로는 표현이 어려우므로 서비스 레이어에서 검증한다.

---

## 4. DDL (PostgreSQL)

```sql
-- =========================================================
-- V1__init.sql  (Flyway 마이그레이션)
-- 병원 파트 간 간호 협업 시스템 초기 스키마
-- =========================================================

-- ---------------------------------------------------------
-- 1. 조직 : 파트(부서)
-- ---------------------------------------------------------
CREATE TABLE department (
    id              BIGSERIAL    PRIMARY KEY,
    code            VARCHAR(20)  NOT NULL UNIQUE,   -- 부서 코드 (예: W03, MRI)
    name            VARCHAR(100) NOT NULL,          -- 부서명 (예: 3병동, MRI실)
    dept_type       VARCHAR(20)  NOT NULL,          -- WARD/EXAM/OR/ICU/ER
    location        VARCHAR(100),                   -- 물리 위치 (예: 본관 3층)
    phone           VARCHAR(30),                    -- 부서 대표 내선
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE department IS '파트(부서) 마스터';

-- ---------------------------------------------------------
-- 2. 조직 : 직원(간호사) 계정
-- ---------------------------------------------------------
CREATE TABLE staff (
    id              BIGSERIAL    PRIMARY KEY,
    login_id        VARCHAR(50)  NOT NULL UNIQUE,   -- 로그인 아이디
    password_hash   VARCHAR(255) NOT NULL,          -- BCrypt 해시
    employee_no     VARCHAR(30)  NOT NULL UNIQUE,   -- 사번
    name            VARCHAR(50)  NOT NULL,
    role            VARCHAR(20)  NOT NULL,          -- ADMIN/HEAD_NURSE/NURSE
    department_id   BIGINT       NOT NULL REFERENCES department(id),
    phone           VARCHAR(30),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_staff_dept ON staff(department_id) WHERE is_active = TRUE;

COMMENT ON TABLE staff IS '직원(간호사) 계정. 소속 파트 기준으로 권한이 갈린다';

-- ---------------------------------------------------------
-- 3. 환자 : 기본정보 (평생 1행)
-- ---------------------------------------------------------
CREATE TABLE patient (
    id              BIGSERIAL    PRIMARY KEY,
    patient_no      VARCHAR(20)  NOT NULL UNIQUE,   -- 환자 등록번호
    name            VARCHAR(50)  NOT NULL,
    birth_date      DATE         NOT NULL,
    sex             CHAR(1)      NOT NULL,          -- M/F
    phone           VARCHAR(30),
    guardian_phone  VARCHAR(30),                    -- 보호자 연락처
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE patient IS '환자 기본정보. 데모 데이터는 반드시 가상 인물로 채운다';

-- ---------------------------------------------------------
-- 4. 환자 : 재원 정보 (입원할 때마다 1행)
-- ---------------------------------------------------------
CREATE TABLE encounter (
    id              BIGSERIAL    PRIMARY KEY,
    patient_id      BIGINT       NOT NULL REFERENCES patient(id),
    department_id   BIGINT       NOT NULL REFERENCES department(id), -- 현재 병동
    room_no         VARCHAR(10),                    -- 병실 (예: 302)
    bed_no          VARCHAR(10),                    -- 병상 (예: 1)
    admitted_at     TIMESTAMPTZ  NOT NULL,
    discharged_at   TIMESTAMPTZ,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ADMITTED', -- ADMITTED/DISCHARGED
    diagnosis       VARCHAR(200),                   -- 주 진단명 (텍스트, 코드화는 추후)
    is_mobile       BOOLEAN      NOT NULL DEFAULT TRUE,  -- 자가 거동 가능 여부
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_encounter_dept_status ON encounter(department_id, status);
CREATE INDEX idx_encounter_patient     ON encounter(patient_id);

COMMENT ON TABLE encounter IS '재원(입원) 건. 모든 기록은 이 테이블을 기준으로 붙는다';

-- ---------------------------------------------------------
-- 5. 환자 : 주의사항 (파트별 뷰의 핵심)
-- ---------------------------------------------------------
CREATE TABLE patient_alert (
    id              BIGSERIAL    PRIMARY KEY,
    patient_id      BIGINT       NOT NULL REFERENCES patient(id),
    alert_type      VARCHAR(30)  NOT NULL,
    -- METAL_IMPLANT   체내 금속물 (MRI실이 반드시 봐야 함)
    -- CONTRAST_ALLERGY 조영제 알레르기
    -- DRUG_ALLERGY    약물 알레르기
    -- ISOLATION       격리 필요
    -- FALL_RISK       낙상 위험
    -- NPO             금식 중
    -- OXYGEN          산소 필요
    -- CLAUSTROPHOBIA  폐소공포
    severity        VARCHAR(10)  NOT NULL DEFAULT 'INFO', -- INFO/WARN/CRITICAL
    content         VARCHAR(500) NOT NULL,          -- 상세 내용
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by      BIGINT       NOT NULL REFERENCES staff(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_patient ON patient_alert(patient_id) WHERE is_active = TRUE;

COMMENT ON TABLE patient_alert IS
'파트별로 보여줄 주의사항. alert_type 을 파트 유형과 매핑해서 필요한 것만 노출한다';

-- ---------------------------------------------------------
-- 6. 이송 : 검사 종류 마스터
-- ---------------------------------------------------------
CREATE TABLE exam_type (
    id                  BIGSERIAL    PRIMARY KEY,
    code                VARCHAR(20)  NOT NULL UNIQUE,  -- 예: MRI_BRAIN
    name                VARCHAR(100) NOT NULL,         -- 예: 뇌 MRI
    department_id       BIGINT       NOT NULL REFERENCES department(id), -- 담당 검사실
    default_duration    INT          NOT NULL DEFAULT 30, -- 예상 소요시간(분)
    prep_instruction    TEXT,                          -- 사전 준비사항 (금식 등)
    required_alerts     VARCHAR(200),                  -- 이 검사에 필수 확인할 alert_type 목록(CSV)
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE
);

COMMENT ON TABLE exam_type IS '검사 종류. required_alerts 로 파트별 필수 확인 항목을 정의한다';

-- ---------------------------------------------------------
-- 7. 이송 : 요청 (핵심 테이블)
-- ---------------------------------------------------------
CREATE TABLE transfer_request (
    id                  BIGSERIAL    PRIMARY KEY,
    request_no          VARCHAR(30)  NOT NULL UNIQUE, -- 요청번호 (예: TR20260904-0001)
    encounter_id        BIGINT       NOT NULL REFERENCES encounter(id),
    exam_type_id        BIGINT       NOT NULL REFERENCES exam_type(id),
    from_department_id  BIGINT       NOT NULL REFERENCES department(id), -- 요청 파트(병동)
    to_department_id    BIGINT       NOT NULL REFERENCES department(id), -- 수행 파트(검사실)

    status              VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    priority            VARCHAR(10)  NOT NULL DEFAULT 'ROUTINE', -- ROUTINE/URGENT/EMERGENCY

    requested_by        BIGINT       NOT NULL REFERENCES staff(id),
    requested_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    desired_at          TIMESTAMPTZ,                  -- 병동 희망 시각
    scheduled_at        TIMESTAMPTZ,                  -- 검사실이 확정한 시각
    started_at          TIMESTAMPTZ,                  -- 검사 시작
    completed_at        TIMESTAMPTZ,                  -- 최종 완료

    note                VARCHAR(500),                 -- 요청 메모
    hold_reason         VARCHAR(500),                 -- 보류/취소 사유
    version             BIGINT       NOT NULL DEFAULT 0, -- 낙관적 락 (JPA @Version)
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 검사실 화면: "우리 파트로 온 진행중 요청" 조회가 가장 빈번함
CREATE INDEX idx_tr_to_dept_status ON transfer_request(to_department_id, status, requested_at DESC);
-- 병동 화면: "우리가 보낸 요청" 조회
CREATE INDEX idx_tr_from_dept      ON transfer_request(from_department_id, status);
CREATE INDEX idx_tr_encounter      ON transfer_request(encounter_id);

COMMENT ON TABLE transfer_request IS '파트 간 환자 이송/검사 요청. 이 시스템의 심장';

-- ---------------------------------------------------------
-- 8. 이송 : 상태 변경 이력
-- ---------------------------------------------------------
CREATE TABLE transfer_event (
    id              BIGSERIAL    PRIMARY KEY,
    request_id      BIGINT       NOT NULL REFERENCES transfer_request(id),
    from_status     VARCHAR(20),                    -- 최초 생성 시 NULL
    to_status       VARCHAR(20)  NOT NULL,
    actor_id        BIGINT       NOT NULL REFERENCES staff(id),
    actor_dept_id   BIGINT       NOT NULL REFERENCES department(id),
    reason          VARCHAR(500),                   -- 보류/취소 시 필수
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_te_request ON transfer_event(request_id, occurred_at);

COMMENT ON TABLE transfer_event IS
'상태 전이 이력. 대기시간 통계는 이 테이블만으로 계산 가능하다';

-- ---------------------------------------------------------
-- 9. 소통 : 요청 단위 대화 스레드
-- ---------------------------------------------------------
CREATE TABLE request_message (
    id              BIGSERIAL    PRIMARY KEY,
    request_id      BIGINT       NOT NULL REFERENCES transfer_request(id),
    sender_id       BIGINT       NOT NULL REFERENCES staff(id),
    content         VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rm_request ON request_message(request_id, created_at);

COMMENT ON TABLE request_message IS
'요청에 붙는 대화. 전화 통화를 대체하되 기록이 남는 것이 목적';

-- ---------------------------------------------------------
-- 10. 소통 : 개인 알림함
-- ---------------------------------------------------------
CREATE TABLE notification (
    id              BIGSERIAL    PRIMARY KEY,
    recipient_id    BIGINT       NOT NULL REFERENCES staff(id),
    noti_type       VARCHAR(30)  NOT NULL,          -- TRANSFER_REQUESTED/STATUS_CHANGED/MESSAGE
    ref_type        VARCHAR(30)  NOT NULL,          -- TRANSFER_REQUEST 등
    ref_id          BIGINT       NOT NULL,
    title           VARCHAR(100) NOT NULL,
    body            VARCHAR(300),
    read_at         TIMESTAMPTZ,                    -- NULL 이면 미읽음
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 미읽음 알림 조회가 가장 빈번 → 부분 인덱스
CREATE INDEX idx_noti_unread ON notification(recipient_id, created_at DESC) WHERE read_at IS NULL;

-- ---------------------------------------------------------
-- 11. 기록 : 활력징후
-- ---------------------------------------------------------
CREATE TABLE vital_sign (
    id              BIGSERIAL    PRIMARY KEY,
    encounter_id    BIGINT       NOT NULL REFERENCES encounter(id),
    measured_at     TIMESTAMPTZ  NOT NULL,
    temperature     NUMERIC(4,1),                   -- 체온 (36.5)
    pulse           INT,                            -- 맥박
    respiration     INT,                            -- 호흡수
    sbp             INT,                            -- 수축기 혈압
    dbp             INT,                            -- 이완기 혈압
    spo2            INT,                            -- 산소포화도 (%)
    pain_score      INT,                            -- 통증점수 0~10
    recorded_by     BIGINT       NOT NULL REFERENCES staff(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_vs_pain CHECK (pain_score IS NULL OR pain_score BETWEEN 0 AND 10)
);

CREATE INDEX idx_vs_encounter ON vital_sign(encounter_id, measured_at DESC);

-- ---------------------------------------------------------
-- 12. 기록 : 간호기록 (SBAR)
-- ---------------------------------------------------------
CREATE TABLE nursing_note (
    id              BIGSERIAL    PRIMARY KEY,
    encounter_id    BIGINT       NOT NULL REFERENCES encounter(id),
    note_type       VARCHAR(20)  NOT NULL DEFAULT 'GENERAL', -- GENERAL/SBAR/HANDOVER
    -- SBAR 형식 (인수인계용)
    situation       TEXT,                           -- S: 현재 상황
    background      TEXT,                           -- B: 배경
    assessment      TEXT,                           -- A: 평가
    recommendation  TEXT,                           -- R: 제안
    content         TEXT,                           -- 일반 기록일 때 사용
    recorded_at     TIMESTAMPTZ  NOT NULL,
    recorded_by     BIGINT       NOT NULL REFERENCES staff(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_nn_encounter ON nursing_note(encounter_id, recorded_at DESC);

-- ---------------------------------------------------------
-- 13. 감사 : 접근·변경 로그
-- ---------------------------------------------------------
CREATE TABLE audit_log (
    id              BIGSERIAL,
    actor_id        BIGINT,                         -- 행위자 (비로그인 시 NULL)
    action          VARCHAR(30)  NOT NULL,          -- VIEW/CREATE/UPDATE/DELETE/LOGIN
    target_type     VARCHAR(50)  NOT NULL,          -- PATIENT/TRANSFER_REQUEST 등
    target_id       BIGINT,
    patient_id      BIGINT,                         -- 환자정보 접근 추적용 (중요)
    ip_address      INET,
    user_agent      VARCHAR(300),
    detail          JSONB,                          -- 변경 전/후 값 등
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- 월 단위 파티션 예시 (운영 시 자동 생성 스크립트로 관리)
CREATE TABLE audit_log_202609 PARTITION OF audit_log
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');

CREATE INDEX idx_audit_patient ON audit_log(patient_id, occurred_at DESC);
CREATE INDEX idx_audit_actor   ON audit_log(actor_id, occurred_at DESC);

COMMENT ON TABLE audit_log IS
'누가 어떤 환자 정보를 열람/변경했는지 기록. AOP 로 자동 삽입한다';
```

---

## 5. 통계 쿼리 예시 (시연용)

데모 마지막에 보여줄 "평균 대기시간" 은 이 쿼리 하나로 나온다.

```sql
-- 파트별 평균 대기시간 (요청 → 접수)
SELECT
    d.name AS 검사실,
    COUNT(*) AS 요청건수,
    ROUND(AVG(EXTRACT(EPOCH FROM (e.occurred_at - r.requested_at)) / 60)) AS 평균대기분
FROM transfer_request r
JOIN transfer_event e
      ON e.request_id = r.id
     AND e.to_status  = 'ACCEPTED'
JOIN department d ON d.id = r.to_department_id
WHERE r.requested_at >= CURRENT_DATE
GROUP BY d.name
ORDER BY 평균대기분 DESC;
```

---

## 6. 다음 단계 후보

1. REST API 명세 설계 (엔드포인트 + 요청/응답 스펙)
2. 상태 전이를 강제하는 도메인 서비스 설계
3. 패키지 구조 및 프로젝트 뼈대
4. 권한 매트릭스 (파트 유형 × 역할 × 리소스)

---

## 7. 아직 안 넣은 것 (Phase 3 이후)

- `duty_schedule` : 근무표 (D/E/N/OFF)
- `duty_request` : 근무 변경 요청
- `medication_order` / `medication_admin` : 투약 (EMR 확장 시)
- FHIR 리소스 매핑 테이블

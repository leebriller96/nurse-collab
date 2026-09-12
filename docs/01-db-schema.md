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
| 환자 | `encounter` | 재원 정보 (입원할 때마다 새로 생김). **가명 대응표가 여기에만 있다** |
| 업무 | `care_episode` | 업무 흐름이 보는 침대 정보. 사람을 가리키는 것이 없다 |
| 환자 | `patient_alert` | 파트별 주의사항 (금속물, 알레르기 등) |
| 업무 | `service_item` | 업무 항목 마스터 (검사·검체·약제·장비) |
| 업무 | `work_order` | 부서 간 업무 요청 (핵심 테이블) |
| 업무 | `work_order_event` | 상태 변경 이력 |
| 소통 | `request_message` | 요청 단위 대화 스레드 |
| 소통 | `notification` | 개인별 알림함 |
| 기록 | `vital_sign` | 활력징후 |
| 기록 | `nursing_note` | 간호기록 (SBAR) |
| 감사 | `audit_log` | 업무 쪽 접근·변경 추적 |
| 감사 | `phi_access_log` | **원내 자체** 접근 기록. 거절된 시도까지 남긴다 |

---

## 2. 설계 핵심 4가지

### (0) 진료정보와 업무정보를 갈라 둔다

`work_order` 는 환자를 `subject_ref` 라는 **불투명한 UUID** 로만 가리킨다.
그 열쇠를 사람으로 되돌리는 대응표는 `encounter` 에만 있다.

| 사는 곳 | 테이블 |
|---|---|
| 진료 (원내) | `patient`, `encounter`, `patient_alert`, `vital_sign`, `nursing_note`, `phi_access_log` |
| 업무 | `care_episode`, `work_order`, `work_order_event`, `department`, `staff`, `service_item`, `notification`, `audit_log` |

양쪽은 서로를 **외래키로 잇지 않는다**(V11, V13). 두 DB 로 갈라 놓아야 하기 때문이다.
`encounter.department_id` 나 `nursing_note.recorded_by` 처럼 상대를 가리키는 컬럼은 남지만,
DB 가 무결성을 보장해 주지 않는다는 뜻이다. 화면에 이름이 필요한 곳은 두 가지로 나뉜다.

- **기록한 사람의 이름**은 쓸 때 함께 굳힌다. 기록에 찍힌 이름은 그때 그 사람의 이름이어야 한다.
- **병동 이름**은 굳히지 않는다. 전동하면 바뀌는 값이라 굳히면 어긋난다. 화면이 찾아 채운다.

가명은 **사람이 아니라 재원 건**에 붙는다. 같은 사람이 3년 뒤 다시 입원하면
다른 열쇠를 받는다. 사람에 붙이면 업무 데이터만 보고도 "이 사람이 네 번 입원했다" 를
알 수 있고, 그건 가명정보라고 부르기 어렵다.

병실·병상이 업무 쪽(`care_episode`)에 있는 이유는 침대가 사람을 가리키지 않기 때문이다.
이것까지 진료 쪽에 두면 원내망 밖에서 병동 보드가 통째로 비어 업무 자체가 돌아가지 않는다.
반대로 진단명과 거동 여부는 이송 준비물을 정하는 값이더라도 업무 쪽으로 넘기지 않는다.
그건 그 사람의 건강 상태다. 자세한 판정 근거는 `06-hospital-scale.md` 3장에 있다.

### (1) patient 와 encounter 를 분리한다

같은 환자가 3년 뒤에 또 입원할 수 있다.
환자 정보(이름, 생년월일)와 재원 정보(병동, 병실, 입원일)를 한 테이블에 넣으면
재입원 시 이전 기록이 뭉개진다. 그래서 무조건 분리한다.

- `patient` : 사람 그 자체 (1명 = 1행, 평생)
- `encounter` : 이번 입원 건 (입원할 때마다 1행 추가)
- 모든 기록(활력징후, 간호기록, 이송요청)은 `encounter_id` 를 바라본다

### (2) 상태는 컬럼, 이력은 별도 테이블

`work_order.status` 에 현재 상태를 두고,
상태가 바뀔 때마다 `work_order_event` 에 한 줄씩 쌓는다.

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
    dept_type       VARCHAR(20)  NOT NULL,          -- WARD/EXAM/OR/ICU/ER/ADMIN
    location        VARCHAR(100),                   -- 물리 위치 (예: 본관 3층)
    phone           VARCHAR(30),                    -- 부서 대표 내선
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE department IS '파트(부서) 마스터';

-- ADMIN 유형이 필요한 이유:
-- staff.department_id 가 NOT NULL 이라 시스템 관리자도 소속 파트가 있어야 한다.
-- 관리자를 임의의 병동에 넣으면 그 병동의 이송 요청에 요청 파트로 엮여버린다.
-- 진료 파트가 아닌 관리 부서를 따로 둔다.

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
-- 6. 업무 : 업무 항목 마스터
-- ---------------------------------------------------------
-- order_type 이 이 항목의 흐름을 정한다. 규칙표는 OrderType 안에 있고
-- DB 는 어느 종류인지만 들고 있는다. (V9)
CREATE TABLE service_item (
    id                  BIGSERIAL    PRIMARY KEY,
    code                VARCHAR(20)  NOT NULL UNIQUE,  -- 예: MRI_BRAIN
    name                VARCHAR(100) NOT NULL,         -- 예: 뇌 MRI
    order_type          VARCHAR(20)  NOT NULL,         -- 업무 종류 (V9)
    department_id       BIGINT       NOT NULL REFERENCES department(id), -- 수행 파트
    default_duration    INT          NOT NULL DEFAULT 30, -- 예상 소요시간(분)
    prep_instruction    TEXT,                          -- 사전 준비사항 (금식 등)
    required_alerts     VARCHAR(200),                  -- 이 업무 전에 확인할 alert_type 목록(CSV)
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE
);

COMMENT ON TABLE service_item IS
'부서가 제공하는 업무 항목. required_alerts 로 파트별 필수 확인 항목을 정의한다';
COMMENT ON COLUMN service_item.order_type IS
'TRANSFER/SPECIMEN/PHARMACY/EQUIPMENT';

-- ---------------------------------------------------------
-- 7. 업무 : 요청 (핵심 테이블)
-- ---------------------------------------------------------
CREATE TABLE work_order (
    id                  BIGSERIAL    PRIMARY KEY,
    request_no          VARCHAR(30)  NOT NULL UNIQUE, -- 요청번호 (예: TR20260904-0001)
    order_type          VARCHAR(20)  NOT NULL,          -- 업무 종류 (V9)
    -- 재원이 아니라 가명을 본다 (V11). 업무 쪽 테이블 중 진료 쪽을 참조하는 것은
    -- 이제 하나도 없다. 끊어 두었으므로 진료 쪽을 원내 DB 로 통째로 옮길 수 있다.
    -- 장비 수리처럼 환자가 없는 업무가 있어 NULL 을 허용한다.
    subject_ref         UUID                  REFERENCES care_episode(subject_ref),
    service_item_id     BIGINT       NOT NULL REFERENCES service_item(id),
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
CREATE INDEX idx_wo_to_dept_status ON work_order(to_department_id, status, requested_at DESC);
-- 병동 화면: "우리가 보낸 요청" 조회
CREATE INDEX idx_wo_from_dept      ON work_order(from_department_id, status);
CREATE INDEX idx_wo_encounter      ON work_order(encounter_id);

COMMENT ON TABLE work_order IS '부서 간 업무 요청. 이 시스템의 심장';

-- ---------------------------------------------------------
-- 8. 업무 : 상태 변경 이력
-- ---------------------------------------------------------
CREATE TABLE work_order_event (
    id              BIGSERIAL    PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES work_order(id),
    from_status     VARCHAR(20),                    -- 최초 생성 시 NULL
    to_status       VARCHAR(20)  NOT NULL,
    actor_id        BIGINT       NOT NULL REFERENCES staff(id),
    actor_dept_id   BIGINT       NOT NULL REFERENCES department(id),
    reason          VARCHAR(500),                   -- 보류/취소 시 필수
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_woe_order ON work_order_event(order_id, occurred_at);

COMMENT ON TABLE work_order_event IS
'상태 전이 이력. 대기시간 통계는 이 테이블만으로 계산 가능하다';

-- ---------------------------------------------------------
-- 9. 소통 : 요청 단위 대화 스레드
-- ---------------------------------------------------------
CREATE TABLE request_message (
    id              BIGSERIAL    PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES work_order(id),
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
    noti_type       VARCHAR(30)  NOT NULL,          -- ORDER_CREATED/STATUS_CHANGED/MESSAGE
    ref_type        VARCHAR(30)  NOT NULL,          -- WORK_ORDER 등
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
    target_type     VARCHAR(50)  NOT NULL,          -- PATIENT/WORK_ORDER 등
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
FROM work_order r
JOIN work_order_event e
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

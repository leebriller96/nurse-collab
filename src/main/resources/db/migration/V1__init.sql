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

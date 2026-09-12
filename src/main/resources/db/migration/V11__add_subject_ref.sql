-- =========================================================
-- V11__add_subject_ref.sql
-- 업무 흐름이 환자를 "불투명한 열쇠" 로만 가리키게 한다.
--
-- 지금은 아직 한 DB 다. 이 마이그레이션이 하는 일은 업무 쪽 테이블이
-- 진료 쪽 테이블(patient, encounter)을 더 이상 참조하지 않게 끊는 것이다.
-- 끊어 두면 다음 단계에서 진료 쪽을 통째로 원내 DB 로 옮길 수 있다.
--
-- 설계 근거는 docs/06-hospital-scale.md 3~4장.
-- =========================================================

-- ---------------------------------------------------------
-- 1. 재원 건마다 가명을 붙인다
--
--    사람이 아니라 **재원 건**에 붙이는 것이 중요하다.
--    같은 사람이 3년 뒤에 다시 입원하면 다른 열쇠를 받는다.
--    사람에 붙이면 클라우드만 보고도 "이 사람이 네 번 입원했다" 를 알 수 있고,
--    그건 가명정보라고 부르기 어렵다.
-- ---------------------------------------------------------
ALTER TABLE encounter ADD COLUMN subject_ref UUID;

-- gen_random_uuid() 는 PostgreSQL 13부터 기본으로 들어 있다.
UPDATE encounter SET subject_ref = gen_random_uuid() WHERE subject_ref IS NULL;

ALTER TABLE encounter ALTER COLUMN subject_ref SET NOT NULL;
ALTER TABLE encounter ALTER COLUMN subject_ref SET DEFAULT gen_random_uuid();
ALTER TABLE encounter ADD CONSTRAINT uq_encounter_subject_ref UNIQUE (subject_ref);

COMMENT ON COLUMN encounter.subject_ref IS
'업무 쪽이 이 재원 건을 가리키는 불투명 열쇠. 사람으로 되돌리는 대응표는 이 테이블에만 있다';

-- ---------------------------------------------------------
-- 2. 업무 흐름이 보는 재원 정보
--
--    병실과 병상은 침대를 가리키지 사람을 가리키지 않는다.
--    이것까지 진료 쪽에 두면 원내망 밖에서 병동 보드가 통째로 비어
--    업무 자체가 돌아가지 않는다.
--
--    진단명과 거동 여부는 여기 없다. 그건 그 사람의 건강 상태다.
-- ---------------------------------------------------------
CREATE TABLE care_episode (
    subject_ref     UUID         PRIMARY KEY,
    department_id   BIGINT       NOT NULL REFERENCES department(id),  -- 현재 병동
    room_no         VARCHAR(10),
    bed_no          VARCHAR(10),
    status          VARCHAR(20)  NOT NULL,                            -- ADMITTED/DISCHARGED
    admitted_at     TIMESTAMPTZ  NOT NULL,
    discharged_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ce_dept_status ON care_episode(department_id, status);

COMMENT ON TABLE care_episode IS
'업무 흐름이 보는 재원 정보. 사람을 가리키는 것은 여기 없다';

INSERT INTO care_episode
    (subject_ref, department_id, room_no, bed_no, status, admitted_at, discharged_at)
SELECT subject_ref, department_id, room_no, bed_no, status, admitted_at, discharged_at
FROM encounter;

-- ---------------------------------------------------------
-- 3. 업무 요청이 재원이 아니라 가명을 본다
--
--    이 한 줄이 이번 단계의 전부다. 여기까지 오면 업무 쪽 테이블 중
--    진료 쪽을 참조하는 것이 하나도 남지 않는다.
-- ---------------------------------------------------------
ALTER TABLE work_order ADD COLUMN subject_ref UUID REFERENCES care_episode(subject_ref);

UPDATE work_order w
   SET subject_ref = e.subject_ref
  FROM encounter e
 WHERE e.id = w.encounter_id;

-- 환자가 없는 업무(장비 수리)는 원래부터 비어 있었다. 그대로 NULL 로 남는다.
ALTER TABLE work_order DROP COLUMN encounter_id;

CREATE INDEX idx_wo_subject ON work_order(subject_ref);

COMMENT ON COLUMN work_order.subject_ref IS
'대상 재원 건의 가명. 사람으로 되돌리려면 원내에 물어야 한다. 환자 없는 업무에서는 NULL';

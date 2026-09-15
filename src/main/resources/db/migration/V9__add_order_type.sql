-- =========================================================
-- V9__add_order_type.sql
-- 업무 종류를 도입한다. V8 이 이름을 바꿨고, 여기서 동작이 갈린다.
--
-- 종류마다 쓰는 상태와 전이 규칙이 다르다. 규칙표는 OrderType 안에 있고
-- DB 는 어느 종류인지만 들고 있는다.
-- 설계 근거는 docs/06-hospital-scale.md 6장.
-- =========================================================

-- ---------------------------------------------------------
-- 1. 업무 항목이 어떤 흐름을 타는지
-- ---------------------------------------------------------
ALTER TABLE service_item ADD COLUMN order_type VARCHAR(20) NOT NULL DEFAULT 'TRANSFER';
-- 기본값은 기존 행(전부 검사다)을 채우기 위한 것이다.
-- 새로 넣는 행은 종류를 반드시 밝혀야 하므로 바로 떼어 낸다.
ALTER TABLE service_item ALTER COLUMN order_type DROP DEFAULT;

COMMENT ON COLUMN service_item.order_type IS
'TRANSFER/SPECIMEN/PHARMACY/EQUIPMENT. 이 항목을 고르면 요청이 그 흐름을 탄다';

-- ---------------------------------------------------------
-- 2. 요청 자체의 종류
--    업무 항목에서 가져오지만 요청에도 적어 둔다.
--    항목의 종류가 나중에 바뀌어도 이미 진행한 요청의 흐름은 바뀌면 안 된다.
-- ---------------------------------------------------------
ALTER TABLE work_order ADD COLUMN order_type VARCHAR(20) NOT NULL DEFAULT 'TRANSFER';
ALTER TABLE work_order ALTER COLUMN order_type DROP DEFAULT;

-- 장비 수리처럼 환자가 없는 업무가 있다. 이 한 줄이 일반화가 진짜인지를 가른다.
-- 환자 접근 판정은 encounter_id 가 있는 요청에만 걸린다.
ALTER TABLE work_order ALTER COLUMN encounter_id DROP NOT NULL;

COMMENT ON COLUMN work_order.order_type IS
'업무 종류. 종류마다 쓰는 상태와 전이 규칙이 다르다 (OrderType)';
COMMENT ON COLUMN work_order.encounter_id IS
'대상 재원 건. 환자가 없는 업무(장비 수리)에서는 NULL 이다';

-- ---------------------------------------------------------
-- 3. 요청번호 발번을 종류별로 나눈다
--
--    한 통에서 뽑으면 TR-0001 다음에 SP-0002 가 나온다.
--    번호가 비어 보여서 "0001 은 어디 갔나" 를 찾게 된다.
--    종류마다 1번부터 센다.
-- ---------------------------------------------------------
ALTER TABLE request_no_sequence ADD COLUMN order_type VARCHAR(20) NOT NULL DEFAULT 'TRANSFER';
ALTER TABLE request_no_sequence ALTER COLUMN order_type DROP DEFAULT;

ALTER TABLE request_no_sequence DROP CONSTRAINT request_no_sequence_pkey;
ALTER TABLE request_no_sequence ADD PRIMARY KEY (date_key, order_type);

COMMENT ON TABLE request_no_sequence IS
'요청번호 일자별·종류별 일련번호. 트랜잭션 안에서 원자적으로 증가시킨다';

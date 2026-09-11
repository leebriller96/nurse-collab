-- =========================================================
-- V8__generalize_to_work_order.sql
-- 이송 전용 요청을 부서 간 업무 요청 일반으로 넓힌다.
--
-- 이 마이그레이션은 이름만 바꾼다. 행도 동작도 그대로다.
-- 종류(order_type)를 더하는 것은 V9 로 나눴다. 이름과 동작을 한 번에 바꾸면
-- 깨졌을 때 어느 쪽 때문인지 알 수 없어진다.
-- 설계 근거는 docs/06-hospital-scale.md 6장.
-- =========================================================

-- ---------------------------------------------------------
-- 1. 검사 종류 → 업무 항목
--    검사만이 아니라 부서가 제공하는 업무 전반을 가리킨다.
-- ---------------------------------------------------------
ALTER TABLE exam_type RENAME TO service_item;

COMMENT ON TABLE service_item IS
'부서가 제공하는 업무 항목. required_alerts 로 업무 전 필수 확인 항목을 정의한다';

-- ---------------------------------------------------------
-- 2. 이송 요청 → 업무 요청
-- ---------------------------------------------------------
ALTER TABLE transfer_request RENAME TO work_order;
ALTER TABLE work_order RENAME COLUMN exam_type_id TO service_item_id;

ALTER INDEX idx_tr_to_dept_status RENAME TO idx_wo_to_dept_status;
ALTER INDEX idx_tr_from_dept      RENAME TO idx_wo_from_dept;
ALTER INDEX idx_tr_encounter      RENAME TO idx_wo_encounter;

COMMENT ON TABLE work_order IS '부서 간 업무 요청. 이 시스템의 심장';

-- ---------------------------------------------------------
-- 3. 상태 이력 / 대화
-- ---------------------------------------------------------
ALTER TABLE transfer_event RENAME TO work_order_event;
ALTER TABLE work_order_event RENAME COLUMN request_id TO order_id;
ALTER INDEX idx_te_request RENAME TO idx_woe_order;

COMMENT ON TABLE work_order_event IS
'상태 전이 이력. 대기시간 통계는 이 테이블만으로 계산 가능하다';

ALTER TABLE request_message RENAME COLUMN request_id TO order_id;
ALTER INDEX idx_rm_request RENAME TO idx_rm_order;

-- ---------------------------------------------------------
-- 4. 다른 테이블이 문자열로 들고 있던 이름
--    알림함과 감사 로그는 대상 종류를 문자열로 적어 둔다. 같이 바꾸지 않으면
--    이전 알림을 눌렀을 때 어디로 가야 할지 모르게 된다.
-- ---------------------------------------------------------
UPDATE notification SET ref_type = 'WORK_ORDER' WHERE ref_type = 'TRANSFER_REQUEST';
UPDATE audit_log    SET target_type = 'WORK_ORDER' WHERE target_type = 'TRANSFER_REQUEST';

COMMENT ON COLUMN notification.ref_type IS 'WORK_ORDER 등. 눌렀을 때 이동할 대상 종류';

-- 알림 종류도 이송 전용 이름이었다. TRANSFER_REQUESTED 는 "업무가 새로 걸렸다" 는 뜻이다.
UPDATE notification SET noti_type = 'ORDER_CREATED' WHERE noti_type = 'TRANSFER_REQUESTED';

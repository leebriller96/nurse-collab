-- =========================================================
-- V15__drop_work_tables.sql  (원내 서버 DB 전용 — db/split/onprem)
--
-- 원내 DB 에서 업무 쪽 테이블을 걷어낸다.
-- V1~V14 는 합친 이력과 같게 돌고, 그 결과에서 이쪽이 들면 안 되는 것을 지운다.
-- 직원·부서도 여기 없다. 원내는 토큰에 실린 이름과 소속 id 로만 사람을 안다.
-- =========================================================

-- 방금 만든 DB 에서만 가른다. 쓰던 합친 DB 에 붙이면 업무 이력이 통째로 지워진다.
DO $$
BEGIN
    IF (SELECT min(installed_on) FROM flyway_schema_history) < now() - interval '1 hour' THEN
        RAISE EXCEPTION
            '이 DB 는 이미 쓰던 DB 입니다. 역할별 DB 는 새로 만든 DB 에서만 가를 수 있습니다.';
    END IF;
END $$;

-- CASCADE 를 쓰지 않는다. 진료 테이블이 이 테이블들을 참조하고 있으면 여기서 멈춰야 한다.
-- 참조하는 쪽부터 지운다.
DROP TABLE work_order_event;
DROP TABLE request_message;
DROP TABLE notification;
DROP TABLE work_order;
DROP TABLE request_no_sequence;
DROP TABLE service_item;
DROP TABLE care_episode;
DROP TABLE staff;
DROP TABLE department;

-- 파티션도 함께 내려간다. 파티션을 채우던 함수도 여기서는 할 일이 없다.
DROP TABLE audit_log;
DROP FUNCTION ensure_audit_log_partitions;

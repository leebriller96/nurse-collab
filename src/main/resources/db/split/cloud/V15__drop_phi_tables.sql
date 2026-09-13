-- =========================================================
-- V15__drop_phi_tables.sql  (업무 서버 DB 전용 — db/split/cloud)
--
-- 업무 서버 DB 에서 진료 테이블을 걷어낸다.
-- V1~V14 는 합친 이력과 같게 돌고, 그 결과에서 이쪽이 들면 안 되는 것을 지운다.
-- =========================================================

-- 방금 만든 DB 에서만 가른다.
-- 이미 쓰던 합친 DB 에 실수로 cloud 역할을 붙이면 여기서 환자 테이블이 통째로 지워진다.
-- 가짜 환자라도 그 실수는 진짜 병원에서 같은 모양으로 일어난다.
DO $$
BEGIN
    IF (SELECT min(installed_on) FROM flyway_schema_history) < now() - interval '1 hour' THEN
        RAISE EXCEPTION
            '이 DB 는 이미 쓰던 DB 입니다. 역할별 DB 는 새로 만든 DB 에서만 가를 수 있습니다. '
            '합친 DB 를 옮기려면 원내 DB 를 먼저 만들어 진료 데이터를 옮긴 뒤 새 업무 DB 로 시작하세요.';
    END IF;
END $$;

-- CASCADE 를 쓰지 않는다. 업무 쪽 테이블이 이 테이블들을 참조하고 있으면 여기서 멈춰야 한다.
-- 그대로 지우면 끊겼어야 할 연결이 조용히 함께 사라진다.
DROP TABLE vital_sign;
DROP TABLE nursing_note;
DROP TABLE patient_alert;
DROP TABLE phi_access_log;
DROP TABLE encounter;
DROP TABLE patient;

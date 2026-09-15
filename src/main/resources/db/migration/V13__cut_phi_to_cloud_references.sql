-- =========================================================
-- V13__cut_phi_to_cloud_references.sql
-- 원내 기록이 업무 쪽 테이블을 참조하지 않게 끊는다.
--
-- 앞선 V11 이 업무 → 진료 방향을 끊었다. 이번은 반대 방향이다.
-- 양쪽이 서로를 참조하지 않아야 두 DB 로 갈라 놓을 수 있다.
--
-- 끊는 것은 외래키뿐이고 컬럼(id)은 남긴다. 누가 썼는지, 어느 병동인지는
-- 여전히 알아야 하기 때문이다. 다만 그 값이 다른 DB 를 가리킬 뿐이다.
--
-- 설계 근거는 docs/06-hospital-scale.md 7장.
-- =========================================================

-- ---------------------------------------------------------
-- 1. 외래키만 끊는다
-- ---------------------------------------------------------
ALTER TABLE encounter      DROP CONSTRAINT encounter_department_id_fkey;
ALTER TABLE patient_alert  DROP CONSTRAINT patient_alert_created_by_fkey;
ALTER TABLE vital_sign     DROP CONSTRAINT vital_sign_recorded_by_fkey;
ALTER TABLE nursing_note   DROP CONSTRAINT nursing_note_recorded_by_fkey;

COMMENT ON COLUMN encounter.department_id IS
'현재 병동. 업무 쪽 department 를 가리키지만 외래키를 걸지 않는다 (다른 DB 로 갈라진다)';

-- ---------------------------------------------------------
-- 2. 기록한 사람의 이름을 함께 적는다
--
--    이름은 업무 쪽(staff)에 있다. 매번 물어보게 두면 원내망 밖은 물론이고
--    업무 쪽이 잠깐 느려도 간호기록이 안 열린다.
--
--    그리고 더 중요한 이유가 있다. **기록에 찍힌 이름은 그때 그 사람의 이름이어야 한다.**
--    결혼해서 성이 바뀌거나 퇴사해서 계정이 비활성화돼도, 그 기록을 쓴 사람은
--    그때의 그 사람이다. 지금 이름으로 다시 그리면 기록이 조용히 달라진다.
-- ---------------------------------------------------------
ALTER TABLE vital_sign   ADD COLUMN recorded_by_name VARCHAR(50);
ALTER TABLE nursing_note ADD COLUMN recorded_by_name VARCHAR(50);

UPDATE vital_sign v   SET recorded_by_name = s.name FROM staff s WHERE s.id = v.recorded_by;
UPDATE nursing_note n SET recorded_by_name = s.name FROM staff s WHERE s.id = n.recorded_by;

-- 이 시점에 채워지지 않은 행은 없다. 남은 것이 있으면 시드가 어긋난 것이므로
-- NOT NULL 을 걸어 여기서 터지게 한다. 조용히 빈 이름으로 남는 것보다 낫다.
ALTER TABLE vital_sign   ALTER COLUMN recorded_by_name SET NOT NULL;
ALTER TABLE nursing_note ALTER COLUMN recorded_by_name SET NOT NULL;

COMMENT ON COLUMN vital_sign.recorded_by_name IS
'기록한 사람의 그때 이름. 지금 이름으로 다시 그리면 기록이 조용히 달라진다';
COMMENT ON COLUMN nursing_note.recorded_by_name IS
'기록한 사람의 그때 이름. 지금 이름으로 다시 그리면 기록이 조용히 달라진다';

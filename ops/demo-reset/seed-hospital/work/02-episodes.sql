-- =========================================================
-- 02-episodes.sql — 업무 흐름이 보는 침대 (업무 DB)
--
-- 입원 한 건마다 "어느 병동 몇 번 침대가 언제부터 언제까지 찼는가" 만 넣는다.
-- 이름도 진단명도 없다. 원내 DB 의 재원 건과는 가명(subject_ref) 하나로 이어진다 —
-- 두 쪽 다 00-plan.sql 의 같은 계산에서 나온 값이다.
-- =========================================================

INSERT INTO care_episode (subject_ref, department_id, room_no, bed_no, status, admitted_at, discharged_at,
                          created_at, updated_at)
SELECT subject_ref, ward_id, room_no, bed_no,
       CASE WHEN discharged_at IS NULL THEN 'ADMITTED' ELSE 'DISCHARGED' END,
       admitted_at, discharged_at,
       admitted_at, coalesce(discharged_at, admitted_at)
FROM stay_plan
ORDER BY seq;

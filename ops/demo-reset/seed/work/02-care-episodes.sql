-- =========================================================
-- 02-care-episodes.sql — 업무 흐름이 보는 침대 (업무 DB)
--
-- 한 번의 입원은 두 곳에 기록된다. 원내 DB 의 encounter 는 누가 입원했고 진단명이
-- 무엇인지(진료정보), 여기 care_episode 는 어느 병동 몇 번 침대가 찼는지(업무정보).
-- 둘을 잇는 것은 subject_ref 하나뿐이다.
--
-- 예전에는 encounter 에서 그대로 복사해 넣었다. DB 가 둘로 갈라지면 그렇게 할 수 없어서
-- 가명을 고정값으로 적는다. **phi/01-patients.sql 의 가명·병실·병상과 같아야 한다.**
-- 어긋나면 병동 보드에서 침대에 사람이 붙지 않는다(가명으로만 잇기 때문이다).
--
-- 진단명과 거동 여부는 여기 없다. 그건 그 사람의 건강 상태다.
-- 코드에서는 AdmissionService 가 같은 일을 한다.
-- =========================================================

INSERT INTO care_episode (subject_ref, department_id, room_no, bed_no, status, admitted_at)
SELECT c.subject_ref::uuid, d.id, c.room_no, c.bed_no, 'ADMITTED',
       NOW() - (c.days_ago || ' days')::interval
FROM (VALUES
    ('5eed0000-0000-4000-8000-000000000001', 'W03', '302', '1', 4),
    ('5eed0000-0000-4000-8000-000000000002', 'W03', '302', '2', 2),
    ('5eed0000-0000-4000-8000-000000000003', 'W05', '501', '1', 6),
    ('5eed0000-0000-4000-8000-000000000004', 'W05', '503', '2', 1),
    ('5eed0000-0000-4000-8000-000000000005', 'W03', '305', '1', 3)
) AS c(subject_ref, dept_code, room_no, bed_no, days_ago)
JOIN department d ON d.code = c.dept_code;

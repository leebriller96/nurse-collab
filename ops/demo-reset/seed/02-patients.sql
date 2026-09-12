-- =========================================================
-- 02-patients.sql — 데모 초기화가 다시 심는 환자 데이터
--
-- Flyway 의 V5 를 옮겨 적은 것이다. 이유는 01-master.sql 머리말에 있다.
-- 등장 인물은 전부 가상이다. 실명·실제 등록번호를 쓰지 않는다.
-- 302-1 김OO 은 시연 시나리오의 주인공이다.
-- 뇌 MRI 의 필수 확인 항목(METAL_IMPLANT)에 걸리도록 금속물 주의사항을 달아둔다.
-- =========================================================

INSERT INTO patient (patient_no, name, birth_date, sex, guardian_phone) VALUES
    ('P0001234', '김OO', '1958-03-11', 'M', '010-0000-0001'),
    ('P0001235', '이OO', '1952-07-24', 'F', '010-0000-0002'),
    ('P0001236', '박OO', '1971-11-02', 'F', '010-0000-0003'),
    ('P0001237', '정OO', '1965-01-30', 'M', '010-0000-0004'),
    ('P0001238', '최OO', '1980-05-16', 'F', '010-0000-0005');

INSERT INTO encounter (patient_id, department_id, room_no, bed_no, admitted_at, diagnosis, is_mobile)
SELECT p.id, d.id, e.room_no, e.bed_no, NOW() - (e.days_ago || ' days')::interval, e.diagnosis, e.is_mobile
FROM (VALUES
    ('P0001234', 'W03', '302', '1', 4, '뇌경색',       FALSE),
    ('P0001235', 'W03', '302', '2', 2, '폐렴',         TRUE),
    ('P0001236', 'W05', '501', '1', 6, '요추 추간판탈출증', TRUE),
    ('P0001237', 'W05', '503', '2', 1, '당뇨병성 신증', TRUE),
    ('P0001238', 'W03', '305', '1', 3, '급성 담낭염',   TRUE)
) AS e(patient_no, dept_code, room_no, bed_no, days_ago, diagnosis, is_mobile)
JOIN patient p    ON p.patient_no = e.patient_no
JOIN department d ON d.code = e.dept_code;

INSERT INTO patient_alert (patient_id, alert_type, severity, content, created_by)
SELECT p.id, a.alert_type, a.severity, a.content, s.id
FROM (VALUES
    ('P0001234', 'METAL_IMPLANT',    'CRITICAL', '좌측 고관절 인공관절 (2019년 삽입)'),
    ('P0001234', 'FALL_RISK',        'WARN',     '낙상 위험 등급 상. 이동 시 반드시 동반'),
    ('P0001235', 'ISOLATION',        'WARN',     '비말주의 격리 중'),
    ('P0001236', 'CLAUSTROPHOBIA',   'WARN',     '폐소공포 이력. 이전 MRI 중단 경험 있음'),
    ('P0001237', 'CONTRAST_ALLERGY', 'CRITICAL', '요오드 조영제 아나필락시스 이력'),
    ('P0001237', 'NPO',              'INFO',     '검사 대기로 금식 중'),
    ('P0001238', 'DRUG_ALLERGY',     'WARN',     '페니실린 알레르기')
) AS a(patient_no, alert_type, severity, content)
JOIN patient p ON p.patient_no = a.patient_no
JOIN staff s   ON s.login_id = 'ward01';

-- ---------------------------------------------------------
-- 업무 흐름이 보는 침대 정보
--
-- 한 번의 입원은 두 곳에 기록된다. 위의 encounter 는 누가 입원했고 진단명이
-- 무엇인지(진료정보), 아래 care_episode 는 어느 병동 몇 번 침대가 찼는지(업무정보).
-- 둘을 잇는 것은 subject_ref 하나뿐이고, 사람으로 되돌리는 대응표는 encounter 에만 있다.
--
-- 진단명과 거동 여부는 넘어가지 않는다. 그건 그 사람의 건강 상태다.
-- 코드에서는 AdmissionService 가 같은 일을 한다.
-- ---------------------------------------------------------
INSERT INTO care_episode
    (subject_ref, department_id, room_no, bed_no, status, admitted_at, discharged_at)
SELECT subject_ref, department_id, room_no, bed_no, status, admitted_at, discharged_at
FROM encounter;

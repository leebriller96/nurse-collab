-- =========================================================
-- 01-patients.sql — 데모 초기화가 다시 심는 환자 데이터 (원내 DB)
--
-- Flyway 의 V5 를 옮겨 적은 것이다. 이유는 work/01-master.sql 머리말에 있다.
-- 등장 인물은 전부 가상이다. 실명·실제 등록번호를 쓰지 않는다.
-- 302-1 김OO 은 시연 시나리오의 주인공이다.
-- 뇌 MRI 의 필수 확인 항목(METAL_IMPLANT)에 걸리도록 금속물 주의사항을 달아둔다.
--
-- 이 파일은 부서 테이블도 직원 테이블도 없는 DB 에서 돈다. 원내에는 둘 다 없다.
-- 그래서 병동 id 와 기록자 id 를 조인으로 찾지 않고 초기화 스크립트가 넘겨준다
-- (psql 변수 :dept_w03 :dept_w05 :ward01_id). 원내 서버가 소속과 직원을 토큰의
-- id 로만 아는 것과 같은 모양이다.
--
-- 가명(subject_ref)은 고정값이다. work/02-care-episodes.sql 과 같아야 한다.
-- =========================================================

INSERT INTO patient (patient_no, name, birth_date, sex, guardian_phone) VALUES
    ('P0001234', '김OO', '1958-03-11', 'M', '010-0000-0001'),
    ('P0001235', '이OO', '1952-07-24', 'F', '010-0000-0002'),
    ('P0001236', '박OO', '1971-11-02', 'F', '010-0000-0003'),
    ('P0001237', '정OO', '1965-01-30', 'M', '010-0000-0004'),
    ('P0001238', '최OO', '1980-05-16', 'F', '010-0000-0005');

INSERT INTO encounter
    (subject_ref, patient_id, department_id, room_no, bed_no, admitted_at, diagnosis, is_mobile)
SELECT e.subject_ref::uuid, p.id, e.department_id, e.room_no, e.bed_no,
       NOW() - (e.days_ago || ' days')::interval, e.diagnosis, e.is_mobile
FROM (VALUES
    ('5eed0000-0000-4000-8000-000000000001', 'P0001234', :dept_w03, '302', '1', 4, '뇌경색',           FALSE),
    ('5eed0000-0000-4000-8000-000000000002', 'P0001235', :dept_w03, '302', '2', 2, '폐렴',             TRUE),
    ('5eed0000-0000-4000-8000-000000000003', 'P0001236', :dept_w05, '501', '1', 6, '요추 추간판탈출증', TRUE),
    ('5eed0000-0000-4000-8000-000000000004', 'P0001237', :dept_w05, '503', '2', 1, '당뇨병성 신증',     TRUE),
    ('5eed0000-0000-4000-8000-000000000005', 'P0001238', :dept_w03, '305', '1', 3, '급성 담낭염',       TRUE)
) AS e(subject_ref, patient_no, department_id, room_no, bed_no, days_ago, diagnosis, is_mobile)
JOIN patient p ON p.patient_no = e.patient_no;

INSERT INTO patient_alert (patient_id, alert_type, severity, content, created_by)
SELECT p.id, a.alert_type, a.severity, a.content, :ward01_id
FROM (VALUES
    ('P0001234', 'METAL_IMPLANT',    'CRITICAL', '좌측 고관절 인공관절 (2019년 삽입)'),
    ('P0001234', 'FALL_RISK',        'WARN',     '낙상 위험 등급 상. 이동 시 반드시 동반'),
    ('P0001235', 'ISOLATION',        'WARN',     '비말주의 격리 중'),
    ('P0001236', 'CLAUSTROPHOBIA',   'WARN',     '폐소공포 이력. 이전 MRI 중단 경험 있음'),
    ('P0001237', 'CONTRAST_ALLERGY', 'CRITICAL', '요오드 조영제 아나필락시스 이력'),
    ('P0001237', 'NPO',              'INFO',     '검사 대기로 금식 중'),
    ('P0001238', 'DRUG_ALLERGY',     'WARN',     '페니실린 알레르기')
) AS a(patient_no, alert_type, severity, content)
JOIN patient p ON p.patient_no = a.patient_no;

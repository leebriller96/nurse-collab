-- =========================================================
-- V3__seed_master_data.sql
-- 시연·개발용 마스터 데이터
--
-- 등장하는 사람은 전부 가상 인물이다. 실명, 실제 사번, 실제 병원명을 넣지 않는다.
-- 데모 계정 비밀번호는 모두 nurse1234! 이며 BCrypt 해시로 저장한다.
-- =========================================================

INSERT INTO department (code, name, dept_type, location, phone) VALUES
    ('W03', '3병동',  'WARD',  '본관 3층',      '1303'),
    ('W05', '5병동',  'WARD',  '본관 5층',      '1505'),
    ('MRI', 'MRI실',  'EXAM',  '별관 지하 1층', '1707'),
    ('CT',  'CT실',   'EXAM',  '별관 지하 1층', '1708'),
    ('ADM', '전산팀', 'ADMIN', '본관 1층',      '1100');

INSERT INTO staff (login_id, password_hash, employee_no, name, role, department_id, phone)
SELECT s.login_id, s.password_hash, s.employee_no, s.name, s.role, d.id, s.phone
FROM (VALUES
    ('admin01', '$2a$10$naNhgWTuWPLelkIZSbuUbuMaCx1hbl4NwXe/wFeEU.L1/bxIwJmvO', 'E90001', '관리자',  'ADMIN',      'ADM', '1100'),
    ('head01',  '$2a$10$IN346N30MnWNXKhces0oQO3RjDcNcTqpcewEUky4rNh4DRIFuepsO', 'E10001', '정수간호', 'HEAD_NURSE', 'W03', '1301'),
    ('ward01',  '$2a$10$XaCZJ4SH0nJk80U7/npa8.U8gpjSbyEy2MzzFKSIkb9.VvpU5/O9K', 'E10002', '김간호',  'NURSE',      'W03', '1302'),
    ('ward02',  '$2a$10$17a9WG7qGiA3KZ4E36Fx3uipFVlzPbHMHjcfQbYG.nbWY21576w2y', 'E10003', '이간호',  'NURSE',      'W05', '1502'),
    ('mri01',   '$2a$10$5jMtm7fk94cZvO8/M5hp5e4Oo9Y3N8fvDnjP57WhN1j3/gWFPJcrS', 'E20001', '박간호',  'NURSE',      'MRI', '1701'),
    ('ct01',    '$2a$10$obdpaqvPDH9zRRbVNkX1XOj0dAHmIc4U1.htWt6xtTRzNlmTeBOcC', 'E20002', '최간호',  'NURSE',      'CT',  '1801')
) AS s(login_id, password_hash, employee_no, name, role, dept_code, phone)
JOIN department d ON d.code = s.dept_code;

INSERT INTO exam_type (code, name, department_id, default_duration, prep_instruction, required_alerts)
SELECT e.code, e.name, d.id, e.default_duration, e.prep_instruction, e.required_alerts
FROM (VALUES
    ('MRI_BRAIN',  '뇌 MRI',    'MRI', 40, '검사 4시간 전부터 금식',
     'METAL_IMPLANT,CLAUSTROPHOBIA,CONTRAST_ALLERGY'),
    ('MRI_LSPINE', '요추 MRI',  'MRI', 35, '검사 4시간 전부터 금식',
     'METAL_IMPLANT,CLAUSTROPHOBIA'),
    ('CT_CHEST',   '흉부 CT',   'CT',  15, '조영제 사용 시 6시간 금식',
     'CONTRAST_ALLERGY,DRUG_ALLERGY'),
    ('CT_ABDOMEN', '복부 CT',   'CT',  20, '검사 6시간 전부터 금식',
     'CONTRAST_ALLERGY,NPO')
) AS e(code, name, dept_code, default_duration, prep_instruction, required_alerts)
JOIN department d ON d.code = e.dept_code;

-- =========================================================
-- 01-master.sql — 데모 초기화가 다시 심는 마스터 데이터
--
-- **Flyway 의 V3/V10 을 여기로 옮겨 적은 것이다.** 왜 복사했는지 남긴다.
--
-- 처음에는 초기화가 Flyway 시드 파일을 그대로 다시 실행했다.
-- 그런데 시드 파일은 "그때의 스키마" 를 향해 쓰여 있다.
-- V8 에서 exam_type 을 service_item 으로 바꾸자 V3 이 없는 테이블에 INSERT 하게 됐다.
-- 이미 적용된 마이그레이션이라 고칠 수 없다(체크섬이 깨진다).
--
-- 그래서 초기화가 심는 데이터는 여기에 둔다. 지금 스키마를 향해 쓴다.
-- Flyway 의 V3/V5/V10 은 새 DB 를 세울 때만 쓰이는 지나간 기록으로 남긴다.
--
-- 두 곳이 어긋나는 것은 CI 가 잡는다. 배포 이미지 잡이 이 초기화를 돌린 뒤
-- 브라우저 검사가 여기 심은 계정으로 로그인하고 여기 심은 업무 항목을 고른다.
-- 어긋나면 그 검사가 먼저 깨진다.
--
-- 등장하는 사람은 전부 가상 인물이다. 비밀번호는 모두 nurse1234! 다.
-- =========================================================

-- ── 부서 ────────────────────────────────────────────────
INSERT INTO department (code, name, dept_type, location, phone) VALUES
    ('W03', '3병동',  'WARD',  '본관 3층',      '1303'),
    ('W05', '5병동',  'WARD',  '본관 5층',      '1505'),
    ('MRI', 'MRI실',  'EXAM',  '별관 지하 1층', '1707'),
    ('CT',  'CT실',   'EXAM',  '별관 지하 1층', '1708'),
    ('ADM', '전산팀', 'ADMIN', '본관 1층',      '1100');

INSERT INTO department (code, name, dept_type, location, phone) VALUES
    ('LAB', '진단검사의학과', 'LAB',      '본관 지하 1층', '1901'),
    ('PHM', '약제부',        'PHARMACY', '본관 1층',      '1401'),
    ('BME', '의공학팀',      'BIOMED',   '별관 2층',      '1601');

-- ── 직원 ────────────────────────────────────────────────
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

INSERT INTO staff (login_id, password_hash, employee_no, name, role, department_id, phone)
SELECT s.login_id, s.password_hash, s.employee_no, s.name, s.role, d.id, s.phone
FROM (VALUES
    ('lab01',   '$2a$10$gG/KshAdisrlIqgXMieCt.xSLjqGW61sBhTOGEdvKxYwpTqzA0n6G', 'E30001', '한검사', 'NURSE', 'LAB', '1902'),
    ('pharm01', '$2a$10$KsVwSSvpLGNegnOG/dEI8.p4wae7tfx7mQiCvmEKWW0JYb8fRrh5K', 'E40001', '오약사', 'NURSE', 'PHM', '1402'),
    ('bme01',   '$2a$10$onnp0Ztvg.YdNYKM1id2ge96GjSABWhHTfLXcVXKSNugqa8nu.2ju', 'E50001', '서기사', 'NURSE', 'BME', '1602')
) AS s(login_id, password_hash, employee_no, name, role, dept_code, phone)
JOIN department d ON d.code = s.dept_code;

-- ── 업무 항목 : 이송 ────────────────────────────────────
INSERT INTO service_item
    (code, name, order_type, department_id, default_duration, prep_instruction, required_alerts)
SELECT e.code, e.name, 'TRANSFER', d.id, e.default_duration, e.prep_instruction, e.required_alerts
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

-- ── 업무 항목 : 검체 · 약제 · 의공 ──────────────────────
INSERT INTO service_item
    (code, name, order_type, department_id, default_duration, prep_instruction, required_alerts)
SELECT i.code, i.name, i.order_type, d.id, i.default_duration, i.prep_instruction, i.required_alerts
FROM (VALUES
    -- 검체 : 병동이 채취하고 진단검사의학과가 분석한다
    ('LAB_CBC',      '일반혈액검사',   'SPECIMEN', 'LAB', 60,
     'EDTA 튜브 1개. 채취 후 흔들어 섞는다', NULL),
    ('LAB_CHEM',     '생화학검사',     'SPECIMEN', 'LAB', 90,
     'SST 튜브 1개. 8시간 금식 후 채취', 'NPO'),
    ('LAB_CULTURE',  '혈액배양',       'SPECIMEN', 'LAB', 240,
     '항생제 투여 전에 서로 다른 부위 두 곳에서 채취', 'DRUG_ALLERGY'),

    -- 약제 : 약제부가 조제해서 병동으로 올려보낸다
    ('PHM_IV',       '수액 조제',      'PHARMACY', 'PHM', 30,
     '처방 확인 후 조제', 'DRUG_ALLERGY'),
    ('PHM_ABX',      '항생제 조제',    'PHARMACY', 'PHM', 45,
     '알레르기 이력을 반드시 확인한다', 'DRUG_ALLERGY'),

    -- 의공 : 환자가 없다
    ('BME_PUMP',     '수액펌프 수리',  'EQUIPMENT', 'BME', 120,
     '장비 번호와 증상을 메모에 적는다', NULL),
    ('BME_MONITOR',  '환자감시장치 수리', 'EQUIPMENT', 'BME', 180,
     '대체 장비가 필요하면 메모에 적는다', NULL)
) AS i(code, name, order_type, dept_code, default_duration, prep_instruction, required_alerts)
JOIN department d ON d.code = i.dept_code;

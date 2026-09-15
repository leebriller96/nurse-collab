-- =========================================================
-- V10__seed_other_order_types.sql
-- 이송 말고 다른 업무를 받는 파트와 그 업무 항목.
--
-- 등장하는 사람은 전부 가상 인물이다. 실명, 실제 사번, 실제 병원명을 넣지 않는다.
-- 비밀번호는 기존 데모 계정과 같은 nurse1234! 이며 BCrypt 해시로 저장한다.
-- =========================================================

INSERT INTO department (code, name, dept_type, location, phone) VALUES
    ('LAB', '진단검사의학과', 'LAB',      '본관 지하 1층', '1901'),
    ('PHM', '약제부',        'PHARMACY', '본관 1층',      '1401'),
    ('BME', '의공학팀',      'BIOMED',   '별관 2층',      '1601');

INSERT INTO staff (login_id, password_hash, employee_no, name, role, department_id, phone)
SELECT s.login_id, s.password_hash, s.employee_no, s.name, s.role, d.id, s.phone
FROM (VALUES
    ('lab01',   '$2a$10$gG/KshAdisrlIqgXMieCt.xSLjqGW61sBhTOGEdvKxYwpTqzA0n6G', 'E30001', '한검사', 'NURSE', 'LAB', '1902'),
    ('pharm01', '$2a$10$KsVwSSvpLGNegnOG/dEI8.p4wae7tfx7mQiCvmEKWW0JYb8fRrh5K', 'E40001', '오약사', 'NURSE', 'PHM', '1402'),
    ('bme01',   '$2a$10$onnp0Ztvg.YdNYKM1id2ge96GjSABWhHTfLXcVXKSNugqa8nu.2ju', 'E50001', '서기사', 'NURSE', 'BME', '1602')
) AS s(login_id, password_hash, employee_no, name, role, dept_code, phone)
JOIN department d ON d.code = s.dept_code;

-- ---------------------------------------------------------
-- 업무 항목
--
-- required_alerts 는 환자가 있는 업무에서만 뜻이 있다.
-- 장비 수리에는 환자가 없으므로 비워 둔다.
-- ---------------------------------------------------------
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

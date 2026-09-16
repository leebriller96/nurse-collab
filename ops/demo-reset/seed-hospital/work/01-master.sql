-- =========================================================
-- 01-master.sql — 부서·직원·업무 항목 (업무 DB)
--
-- 번호를 직접 준다. 원내 DB 의 기록자(간호사) 번호가 이 번호와 같아야 해서다.
-- 원내에는 직원 테이블이 없고, 00-plan.sql 의 같은 계산으로 번호와 이름을 안다.
-- =========================================================

INSERT INTO department (id, code, name, dept_type, location, phone)
SELECT id, code, name, dept_type, location, phone FROM dept_plan ORDER BY id;

-- 비밀번호는 전부 nurse1234! 다. 해시를 하나로 쓴다 — 테스트 데이터라 괜찮다.
INSERT INTO staff (id, login_id, password_hash, employee_no, name, role, department_id, phone)
SELECT s.id, s.login_id,
       '$2a$10$XaCZJ4SH0nJk80U7/npa8.U8gpjSbyEy2MzzFKSIkb9.VvpU5/O9K',
       s.employee_no, s.name, s.role, s.dept_id, d.phone
FROM staff_plan s JOIN dept_plan d ON d.id = s.dept_id
ORDER BY s.id;

-- 업무 항목. 확인 항목은 환자 주의사항과 겹칠 때 경고가 된다.
INSERT INTO service_item (id, code, name, order_type, department_id, default_duration, prep_instruction, required_alerts)
VALUES
    -- 이송 : MRI · CT
    ( 1, 'MRI_BRAIN',   '뇌 MRI',            'TRANSFER',  3, 40, '검사 4시간 전부터 금식',            'METAL_IMPLANT,CLAUSTROPHOBIA,CONTRAST_ALLERGY'),
    ( 2, 'MRI_LSPINE',  '요추 MRI',          'TRANSFER',  3, 35, '검사 4시간 전부터 금식',            'METAL_IMPLANT,CLAUSTROPHOBIA'),
    ( 3, 'MRI_KNEE',    '슬관절 MRI',        'TRANSFER',  3, 30, null,                              'METAL_IMPLANT,CLAUSTROPHOBIA'),
    ( 4, 'MRI_CSPINE',  '경추 MRI',          'TRANSFER',  3, 35, null,                              'METAL_IMPLANT,CLAUSTROPHOBIA'),
    ( 5, 'CT_CHEST',    '흉부 CT',           'TRANSFER',  4, 15, '조영제 사용 시 6시간 금식',         'CONTRAST_ALLERGY,DRUG_ALLERGY'),
    ( 6, 'CT_ABDOMEN',  '복부 CT',           'TRANSFER',  4, 20, '검사 6시간 전부터 금식',            'CONTRAST_ALLERGY,NPO'),
    ( 7, 'CT_BRAIN',    '두부 CT',           'TRANSFER',  4, 10, null,                              null),
    -- 이송 : 일반촬영 · 초음파 · 내시경
    ( 8, 'XR_CHEST',    '흉부 X-ray',        'TRANSFER', 14, 10, null,                              null),
    ( 9, 'XR_KNEE',     '슬관절 X-ray',      'TRANSFER', 14, 10, null,                              null),
    (10, 'XR_ABD',      '복부 X-ray',        'TRANSFER', 14, 10, null,                              null),
    (11, 'US_ABD',      '복부 초음파',        'TRANSFER', 15, 20, '검사 8시간 전부터 금식',            'NPO'),
    (12, 'US_DVT',      '하지 정맥 초음파',   'TRANSFER', 15, 25, null,                              null),
    (13, 'US_ECHO',     '심장 초음파',        'TRANSFER', 15, 30, null,                              'OXYGEN'),
    (14, 'ENDO_EGD',    '위내시경',          'TRANSFER', 16, 20, '검사 8시간 전부터 금식',            'NPO,DRUG_ALLERGY'),
    (15, 'ENDO_COLON',  '대장내시경',        'TRANSFER', 16, 40, '전날 저녁 장정결제 복용',           'NPO,DRUG_ALLERGY'),
    -- 검체 : 병동이 채취하고 진단검사의학과가 분석한다
    (20, 'LAB_CBC',     '일반혈액검사',       'SPECIMEN',  6, 60, 'EDTA 튜브 1개. 채취 후 흔들어 섞는다', null),
    (21, 'LAB_CHEM',    '생화학검사',         'SPECIMEN',  6, 90, 'SST 튜브 1개. 8시간 금식 후 채취',   'NPO'),
    (22, 'LAB_CRP',     '염증수치(CRP)',      'SPECIMEN',  6, 60, 'SST 튜브 1개',                       null),
    (23, 'LAB_UA',      '소변검사',           'SPECIMEN',  6, 45, '중간뇨 채취',                        null),
    (24, 'LAB_COAG',    '혈액응고검사',       'SPECIMEN',  6, 60, '구연산 튜브. 정확히 표시선까지',      'DRUG_ALLERGY'),
    (25, 'LAB_ABGA',    '동맥혈가스분석',     'SPECIMEN',  6, 15, '채취 즉시 얼음에 담아 내려보낸다',    'OXYGEN'),
    (26, 'LAB_CULTURE', '혈액배양',           'SPECIMEN',  6, 240, '항생제 투여 전에 서로 다른 부위 두 곳에서 채취', 'DRUG_ALLERGY'),
    (27, 'LAB_SPUTUM',  '객담배양',           'SPECIMEN',  6, 240, '아침 첫 객담',                      'ISOLATION'),
    -- 약제 : 약제부가 조제해서 올려보낸다
    (30, 'PHM_IV',      '수액 조제',          'PHARMACY',  7, 30, '처방 확인 후 조제',                  'DRUG_ALLERGY'),
    (31, 'PHM_ABX',     '항생제 조제',        'PHARMACY',  7, 45, '알레르기 이력을 반드시 확인한다',     'DRUG_ALLERGY'),
    (32, 'PHM_TPN',     '영양수액(TPN) 조제', 'PHARMACY',  7, 90, '무균 조제실에서 조제',                'DRUG_ALLERGY'),
    (33, 'PHM_NARC',    '마약류 불출',        'PHARMACY',  7, 20, '마약류 관리대장 서명 필요',           'DRUG_ALLERGY'),
    (34, 'PHM_ANTICO',  '항응고제 조제',      'PHARMACY',  7, 30, '혈액응고검사 결과 확인 후 조제',      'DRUG_ALLERGY'),
    (35, 'PHM_DISCH',   '퇴원약 조제',        'PHARMACY',  7, 60, '복약 안내문 동봉',                    'DRUG_ALLERGY'),
    -- 의공 : 환자가 없다
    (40, 'BME_PUMP',    '수액펌프 수리',      'EQUIPMENT', 8, 120, '장비 번호와 증상을 메모에 적는다',   null),
    (41, 'BME_MONITOR', '환자감시장치 수리',  'EQUIPMENT', 8, 180, '대체 장비가 필요하면 메모에 적는다', null),
    (42, 'BME_VENT',    '인공호흡기 점검',    'EQUIPMENT', 8, 240, '사용 중이면 예비기로 교체 후 의뢰',   null),
    (43, 'BME_BED',     '전동침대 수리',      'EQUIPMENT', 8, 120, '환자를 다른 침대로 옮긴 뒤 의뢰',     null),
    (44, 'BME_SUCTION', '흡인기 수리',        'EQUIPMENT', 8,  90, null,                                null),
    (45, 'BME_ECG',     '심전도기 점검',      'EQUIPMENT', 8, 120, null,                                null);

-- 번호를 직접 넣었으므로 다음 번호를 맞춰 둔다. 안 맞추면 화면에서 새로 만들 때 번호가 겹친다.
SELECT setval('department_id_seq', (SELECT max(id) FROM department));
SELECT setval('staff_id_seq',      (SELECT max(id) FROM staff));
SELECT setval('exam_type_id_seq',  (SELECT max(id) FROM service_item));

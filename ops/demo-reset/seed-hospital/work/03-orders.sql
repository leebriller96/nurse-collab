-- =========================================================
-- 03-orders.sql — 6개월치 업무 요청과 진행 기록 (업무 DB)
--
-- 실제 병동의 하루를 흉내 낸다.
--   · 입원하면 기본 채혈·흉부 사진·항생제가 나간다 (진료과마다 다르다)
--   · 채혈은 새벽 5~6시, 영상은 낮, 약은 투약 시간 전에 나간다
--   · 신경과는 입원 다음 날 뇌 MRI, 정형외과는 수술 뒤 X-ray, 퇴원하는 날은 퇴원약
--   · 장비 고장은 병동마다 하루 0~3건, 중환자실은 인공호흡기가 많다
--   · 몇 %는 보류되거나 취소되고, 의공은 부품을 기다리기도 한다
--   · 기준 시각에 가까운 요청은 아직 진행 중이다
--
-- 요청을 API 로 만들지 않는다. 기본 데모 데이터는 20건이라 API 로 만들지만 여기는 수만 건이라
-- 수십만 번 호출해야 한다. 대신 서버가 남기는 것과 같은 모양으로 진행 기록·버전·요청번호·발번 표를
-- 함께 만든다. 전이 순서는 OrderType 의 규칙표를 그대로 따른다.
-- =========================================================

-- ── 요청 규칙 ────────────────────────────────────────────
-- kind: ADMIT(입원 후 h_from~h_to 시간) · DAILY(입원 둘째 날부터 매일 h_from~h_to 시)
--       ONCE(day_no 째 날 하루) · DISCHARGE(퇴원 h_from~h_to 시간 전)
-- specialty '*' 는 같은 업무·같은 종류의 규칙이 따로 없는 진료과에만 적용된다.
CREATE TEMP TABLE order_rule (
    rule_no int, item_code text, specialty text, kind text, day_no int,
    prob float8, h_from float8, h_to float8, p_urgent float8, p_emergency float8
);
INSERT INTO order_rule VALUES
    -- 입원 직후
    ( 1, 'LAB_CBC',     '*',      'ADMIT', 0, 1.00, 0.5, 2.0, 0.10, 0),
    ( 2, 'LAB_CHEM',    '*',      'ADMIT', 0, 0.95, 0.5, 2.0, 0.08, 0),
    ( 3, 'LAB_CRP',     '*',      'ADMIT', 0, 0.35, 0.5, 2.0, 0.05, 0),
    ( 4, 'LAB_CRP',     'INFECT', 'ADMIT', 0, 0.90, 0.5, 2.0, 0.20, 0),
    ( 5, 'LAB_CRP',     'PULM',   'ADMIT', 0, 0.70, 0.5, 2.0, 0.15, 0),
    ( 6, 'LAB_UA',      '*',      'ADMIT', 0, 0.45, 1.0, 4.0, 0.03, 0),
    ( 7, 'LAB_COAG',    '*',      'ADMIT', 0, 0.15, 0.5, 2.0, 0.05, 0),
    ( 8, 'LAB_COAG',    'ORTHO',  'ADMIT', 0, 0.70, 0.5, 2.0, 0.05, 0),
    ( 9, 'LAB_COAG',    'SURG',   'ADMIT', 0, 0.70, 0.5, 2.0, 0.10, 0),
    (10, 'LAB_COAG',    'ICU',    'ADMIT', 0, 0.80, 0.3, 1.0, 0.40, 0),
    (11, 'LAB_COAG',    'NEURO',  'ADMIT', 0, 0.40, 0.5, 2.0, 0.20, 0),
    (12, 'LAB_ABGA',    'ICU',    'ADMIT', 0, 0.80, 0.2, 1.0, 0.60, 0.10),
    (13, 'LAB_ABGA',    'PULM',   'ADMIT', 0, 0.30, 0.5, 2.0, 0.40, 0),
    (14, 'LAB_CULTURE', '*',      'ADMIT', 0, 0.12, 0.5, 3.0, 0.40, 0),
    (15, 'LAB_CULTURE', 'INFECT', 'ADMIT', 0, 0.60, 0.5, 2.0, 0.50, 0),
    (16, 'LAB_CULTURE', 'ICU',    'ADMIT', 0, 0.60, 0.3, 1.5, 0.60, 0),
    (17, 'LAB_CULTURE', 'PULM',   'ADMIT', 0, 0.40, 0.5, 2.0, 0.40, 0),
    (18, 'LAB_SPUTUM',  'PULM',   'ADMIT', 0, 0.35, 2.0, 8.0, 0.05, 0),
    (19, 'LAB_SPUTUM',  'INFECT', 'ADMIT', 0, 0.50, 2.0, 8.0, 0.10, 0),
    (20, 'XR_CHEST',    '*',      'ADMIT', 0, 0.45, 1.0, 4.0, 0.05, 0),
    (21, 'XR_CHEST',    'PULM',   'ADMIT', 0, 0.95, 0.5, 2.5, 0.20, 0),
    (22, 'XR_CHEST',    'ICU',    'ADMIT', 0, 0.90, 0.3, 1.5, 0.50, 0),
    (23, 'XR_CHEST',    'INFECT', 'ADMIT', 0, 0.80, 0.5, 3.0, 0.15, 0),
    (24, 'XR_KNEE',     'ORTHO',  'ADMIT', 0, 0.35, 1.0, 4.0, 0.05, 0),
    (25, 'XR_ABD',      'GI',     'ADMIT', 0, 0.35, 1.0, 4.0, 0.10, 0),
    (26, 'XR_ABD',      'SURG',   'ADMIT', 0, 0.30, 1.0, 4.0, 0.15, 0),
    (27, 'CT_CHEST',    'PULM',   'ADMIT', 0, 0.30, 2.0, 8.0, 0.30, 0.02),
    (28, 'CT_CHEST',    'INFECT', 'ADMIT', 0, 0.25, 2.0, 8.0, 0.30, 0),
    (29, 'CT_ABDOMEN',  'GI',     'ADMIT', 0, 0.35, 2.0, 8.0, 0.30, 0.03),
    (30, 'CT_ABDOMEN',  'SURG',   'ADMIT', 0, 0.30, 1.0, 6.0, 0.30, 0.05),
    (31, 'CT_BRAIN',    'NEURO',  'ADMIT', 0, 0.70, 0.3, 2.0, 0.40, 0.15),
    (32, 'CT_BRAIN',    'ICU',    'ADMIT', 0, 0.35, 0.3, 2.0, 0.40, 0.20),
    (33, 'US_ECHO',     'PULM',   'ADMIT', 0, 0.30, 4.0, 24.0, 0.10, 0),
    (34, 'US_ECHO',     'ICU',    'ADMIT', 0, 0.30, 2.0, 12.0, 0.30, 0),
    (35, 'US_ABD',      'GI',     'ADMIT', 0, 0.30, 4.0, 24.0, 0.10, 0),
    (36, 'PHM_ABX',     'INFECT', 'ADMIT', 0, 0.90, 1.0, 3.0, 0.30, 0),
    (37, 'PHM_ABX',     'PULM',   'ADMIT', 0, 0.75, 1.0, 3.0, 0.25, 0),
    (38, 'PHM_ABX',     'ICU',    'ADMIT', 0, 0.80, 0.5, 2.0, 0.50, 0),
    (39, 'PHM_ABX',     'SURG',   'ADMIT', 0, 0.55, 1.0, 3.0, 0.10, 0),
    (40, 'PHM_ABX',     'GI',     'ADMIT', 0, 0.50, 1.0, 3.0, 0.10, 0),
    (41, 'PHM_ABX',     'ORTHO',  'ADMIT', 0, 0.40, 1.0, 3.0, 0.05, 0),
    (42, 'PHM_ABX',     'NEURO',  'ADMIT', 0, 0.20, 1.0, 3.0, 0.05, 0),
    (43, 'PHM_IV',      '*',      'ADMIT', 0, 0.50, 0.5, 2.0, 0.10, 0),
    (44, 'PHM_IV',      'ICU',    'ADMIT', 0, 0.90, 0.3, 1.0, 0.40, 0),
    (45, 'PHM_NARC',    'SURG',   'ADMIT', 0, 0.45, 2.0, 8.0, 0.20, 0),
    (46, 'PHM_NARC',    'ORTHO',  'ADMIT', 0, 0.45, 2.0, 8.0, 0.20, 0),
    (47, 'PHM_NARC',    'ICU',    'ADMIT', 0, 0.35, 0.5, 3.0, 0.40, 0),
    -- 입원 며칠째 하루
    (50, 'MRI_BRAIN',   'NEURO',  'ONCE',  1, 0.50, 9.0, 16.0, 0.20, 0.03),
    (51, 'MRI_LSPINE',  'ORTHO',  'ONCE',  1, 0.30, 9.0, 16.0, 0.05, 0),
    (52, 'MRI_KNEE',    'ORTHO',  'ONCE',  2, 0.12, 9.0, 16.0, 0.02, 0),
    (53, 'MRI_CSPINE',  'NEURO',  'ONCE',  2, 0.08, 9.0, 16.0, 0.10, 0),
    (54, 'MRI_CSPINE',  'ORTHO',  'ONCE',  2, 0.06, 9.0, 16.0, 0.05, 0),
    (55, 'ENDO_EGD',    'GI',     'ONCE',  1, 0.30, 9.0, 12.0, 0.10, 0),
    (56, 'ENDO_COLON',  'GI',     'ONCE',  2, 0.12, 9.0, 12.0, 0.02, 0),
    (57, 'US_DVT',      'ORTHO',  'ONCE',  3, 0.12, 9.0, 16.0, 0.20, 0),
    (58, 'US_DVT',      'NEURO',  'ONCE',  3, 0.08, 9.0, 16.0, 0.20, 0),
    (59, 'XR_KNEE',     'ORTHO',  'ONCE',  2, 0.40, 9.0, 15.0, 0.02, 0),
    -- 매일 (입원 둘째 날부터)
    (60, 'LAB_CBC',     '*',      'DAILY', 0, 0.45, 5.0, 6.5, 0.03, 0),
    (61, 'LAB_CBC',     'ICU',    'DAILY', 0, 1.00, 4.5, 6.0, 0.20, 0),
    (62, 'LAB_CHEM',    '*',      'DAILY', 0, 0.35, 5.0, 6.5, 0.03, 0),
    (63, 'LAB_CHEM',    'ICU',    'DAILY', 0, 0.95, 4.5, 6.0, 0.20, 0),
    (64, 'LAB_CRP',     'INFECT', 'DAILY', 0, 0.35, 5.0, 6.5, 0.05, 0),
    (65, 'LAB_CRP',     'PULM',   'DAILY', 0, 0.25, 5.0, 6.5, 0.05, 0),
    (66, 'LAB_CRP',     'ICU',    'DAILY', 0, 0.50, 4.5, 6.0, 0.10, 0),
    (67, 'LAB_ABGA',    'ICU',    'DAILY', 0, 0.60, 6.0, 8.0, 0.40, 0),
    (68, 'LAB_ABGA',    'ICU',    'DAILY', 0, 0.30, 14.0, 18.0, 0.40, 0.05),
    (69, 'LAB_COAG',    'ICU',    'DAILY', 0, 0.30, 4.5, 6.0, 0.10, 0),
    (70, 'LAB_COAG',    'NEURO',  'DAILY', 0, 0.08, 5.0, 6.5, 0.05, 0),
    (71, 'XR_CHEST',    'ICU',    'DAILY', 0, 0.60, 6.0, 9.0, 0.20, 0),
    (72, 'XR_CHEST',    'PULM',   'DAILY', 0, 0.12, 9.0, 16.0, 0.05, 0),
    (73, 'XR_CHEST',    'INFECT', 'DAILY', 0, 0.08, 9.0, 16.0, 0.05, 0),
    (74, 'PHM_ABX',     'INFECT', 'DAILY', 0, 0.45, 8.0, 11.0, 0.05, 0),
    (75, 'PHM_ABX',     'PULM',   'DAILY', 0, 0.35, 8.0, 11.0, 0.05, 0),
    (76, 'PHM_ABX',     'ICU',    'DAILY', 0, 0.50, 7.0, 10.0, 0.20, 0),
    (77, 'PHM_ABX',     'SURG',   'DAILY', 0, 0.25, 8.0, 11.0, 0.02, 0),
    (78, 'PHM_ABX',     'GI',     'DAILY', 0, 0.20, 8.0, 11.0, 0.02, 0),
    (79, 'PHM_IV',      '*',      'DAILY', 0, 0.20, 8.0, 17.0, 0.02, 0),
    (80, 'PHM_IV',      'ICU',    'DAILY', 0, 0.70, 6.0, 20.0, 0.20, 0),
    (81, 'PHM_TPN',     'SURG',   'DAILY', 0, 0.12, 13.0, 16.0, 0.02, 0),
    (82, 'PHM_TPN',     'ICU',    'DAILY', 0, 0.20, 13.0, 16.0, 0.05, 0),
    (83, 'PHM_NARC',    'SURG',   'DAILY', 0, 0.20, 9.0, 20.0, 0.10, 0),
    (84, 'PHM_NARC',    'ORTHO',  'DAILY', 0, 0.18, 9.0, 20.0, 0.10, 0),
    (85, 'PHM_NARC',    'ICU',    'DAILY', 0, 0.25, 6.0, 22.0, 0.30, 0),
    (86, 'PHM_ANTICO',  'PULM',   'DAILY', 0, 0.08, 16.0, 18.0, 0.05, 0),
    (87, 'PHM_ANTICO',  'NEURO',  'DAILY', 0, 0.08, 16.0, 18.0, 0.05, 0),
    (88, 'PHM_ANTICO',  'ORTHO',  'DAILY', 0, 0.06, 16.0, 18.0, 0.02, 0),
    -- 저녁·밤. 병원은 밤에도 돈다 — 열이 나면 배양을 내고, 아프다면 진통제를 받는다.
    (91, 'LAB_CULTURE', '*',      'DAILY', 0, 0.04, 18.0, 23.5, 0.50, 0),
    (92, 'PHM_NARC',    '*',      'DAILY', 0, 0.08, 19.0, 23.5, 0.30, 0),
    (93, 'PHM_IV',      '*',      'DAILY', 0, 0.15, 18.0, 22.0, 0.05, 0),
    (94, 'XR_CHEST',    'ICU',    'DAILY', 0, 0.15, 20.0, 23.5, 0.40, 0),
    (95, 'LAB_CBC',     'ICU',    'DAILY', 0, 0.25, 17.0, 20.0, 0.20, 0),
    -- 퇴원하는 날
    (90, 'PHM_DISCH',   '*',      'DISCHARGE', 0, 0.85, 2.0, 4.0, 0, 0);

-- '*' 규칙을 진료과별로 펼친다. 같은 업무·같은 종류의 진료과 규칙이 있으면 그쪽만 쓴다.
CREATE TEMP TABLE rule_x AS
SELECT r.rule_no, r.item_code, sp.specialty, r.kind, r.day_no, r.prob, r.h_from, r.h_to, r.p_urgent, r.p_emergency
FROM order_rule r
CROSS JOIN (SELECT DISTINCT specialty FROM bed_plan) sp
WHERE r.specialty = sp.specialty
   OR (r.specialty = '*' AND NOT EXISTS (
        SELECT 1 FROM order_rule r2
         WHERE r2.item_code = r.item_code AND r2.kind = r.kind AND r2.specialty = sp.specialty));

-- ── 요청 후보 ────────────────────────────────────────────
CREATE TEMP TABLE order_seed AS
SELECT 'r' || x.rule_no || ':' || sd.subject_ref || ':' || sd.day_no AS order_key,
       si.id AS item_id, si.code AS item_code, si.name AS item_name, si.order_type,
       si.department_id AS to_dept, si.default_duration AS dur,
       sd.ward_id AS from_dept, sd.subject_ref, sd.room_no,
       CASE x.kind
           WHEN 'ADMIT' THEN sd.admitted_at + make_interval(secs => 3600 * (x.h_from + (x.h_to - x.h_from)
                                 * pg_temp.u('t:' || x.rule_no || ':' || sd.subject_ref)))
           WHEN 'DISCHARGE' THEN sd.discharged_at - make_interval(secs => 3600 * (x.h_from + (x.h_to - x.h_from)
                                 * pg_temp.u('t:' || x.rule_no || ':' || sd.subject_ref)))
           ELSE sd.day_start + make_interval(secs => 3600 * (x.h_from + (x.h_to - x.h_from)
                                 * pg_temp.u('t:' || x.rule_no || ':' || sd.subject_ref || ':' || sd.day_no)))
       END AS requested_at,
       x.p_urgent, x.p_emergency, sd.admitted_at, sd.stay_end
FROM rule_x x
JOIN stay_day sd ON sd.specialty = x.specialty
JOIN service_item si ON si.code = x.item_code
WHERE ((x.kind = 'ADMIT' AND sd.day_no = 0)
    OR (x.kind = 'DAILY' AND sd.day_no >= 1)
    OR (x.kind = 'ONCE' AND sd.day_no = x.day_no)
    OR (x.kind = 'DISCHARGE' AND sd.discharged_at IS NOT NULL
        AND date_trunc('day', sd.discharged_at) = sd.day_start))
  AND pg_temp.u('p:' || x.rule_no || ':' || sd.subject_ref || ':' || sd.day_no) < x.prob;

-- 입원 전이나 퇴원 뒤에 걸린 요청은 없다
DELETE FROM order_seed
 WHERE requested_at < admitted_at + interval '15 minutes'
    OR requested_at > stay_end - interval '15 minutes';

-- 장비 고장. 병동마다 하루 0~3건, 대부분 낮에 발견한다.
INSERT INTO order_seed (order_key, item_id, item_code, item_name, order_type, to_dept, dur,
                        from_dept, subject_ref, room_no, requested_at, p_urgent, p_emergency)
SELECT e.order_key, si.id, si.code, si.name, si.order_type, si.department_id, si.default_duration,
       e.ward_id, null, null, e.requested_at,
       CASE WHEN si.code = 'BME_VENT' THEN 0.50 ELSE 0.15 END, 0
FROM (
    SELECT 'eq:' || d.id || ':' || g.day || ':' || s.slot AS order_key,
           d.id AS ward_id, d.specialty,
           p.sim_start + make_interval(days => g.day)
             + make_interval(secs => 3600 * CASE
                   WHEN pg_temp.u('eqd:' || d.id || ':' || g.day || ':' || s.slot) < 0.8
                   THEN 7 + 14 * pg_temp.u('eqh:' || d.id || ':' || g.day || ':' || s.slot)
                   ELSE 24 * pg_temp.u('eqh:' || d.id || ':' || g.day || ':' || s.slot) END) AS requested_at,
           pg_temp.u('eqi:' || d.id || ':' || g.day || ':' || s.slot) AS ui
    FROM dept_plan d
    CROSS JOIN plan_anchor p
    CROSS JOIN generate_series(0, 180) AS g(day)
    CROSS JOIN generate_series(1, 3) AS s(slot)
    WHERE d.is_ward
      AND pg_temp.u('eqp:' || d.id || ':' || g.day || ':' || s.slot)
          < CASE s.slot WHEN 1 THEN 0.45 WHEN 2 THEN 0.20 ELSE 0.07 END
) e
JOIN service_item si ON si.code = CASE
    WHEN e.specialty = 'ICU' THEN CASE
        WHEN e.ui < 0.35 THEN 'BME_VENT' WHEN e.ui < 0.60 THEN 'BME_MONITOR'
        WHEN e.ui < 0.80 THEN 'BME_PUMP' WHEN e.ui < 0.90 THEN 'BME_SUCTION'
        WHEN e.ui < 0.95 THEN 'BME_BED'  ELSE 'BME_ECG' END
    ELSE CASE
        WHEN e.ui < 0.35 THEN 'BME_PUMP' WHEN e.ui < 0.55 THEN 'BME_BED'
        WHEN e.ui < 0.75 THEN 'BME_MONITOR' WHEN e.ui < 0.90 THEN 'BME_SUCTION'
        ELSE 'BME_ECG' END
    END
WHERE e.requested_at <= (SELECT anchor FROM plan_anchor);

-- ── 요청 ─────────────────────────────────────────────────
CREATE TEMP TABLE ord AS
SELECT o.*,
       row_number() OVER (ORDER BY o.requested_at, o.order_key) AS id,
       CASE WHEN pg_temp.u('pri:' || o.order_key) < o.p_emergency THEN 'EMERGENCY'
            WHEN pg_temp.u('pri:' || o.order_key) < o.p_emergency + o.p_urgent THEN 'URGENT'
            ELSE 'ROUTINE' END AS priority,
       rq.ids[1 + floor(pg_temp.u('req:' || o.order_key) * rq.n)::int] AS requester_id,
       pf.ids[1 + floor(pg_temp.u('per:' || o.order_key) * pf.n)::int] AS performer_id,
       CASE
           WHEN o.order_type = 'EQUIPMENT' AND pg_temp.u('note:' || o.order_key) < 0.8 THEN
               pg_temp.pick(ARRAY['알람이 계속 울림', '전원이 켜지지 않음', '화면이 깜빡임',
                                  '바퀴 잠금이 풀리지 않음', '흡인 압력이 오르지 않음',
                                  '주입 속도가 설정과 다름', '배터리가 금방 닳음'], 'nt:' || o.order_key)
           WHEN o.order_type = 'TRANSFER' AND pg_temp.u('note:' || o.order_key) < 0.20 THEN
               pg_temp.pick(ARRAY['휠체어 이송', '침대 이송 필요', '산소 달고 이송',
                                  '보호자 동반', '격리 환자라 마지막 순서로 부탁드립니다'], 'nt:' || o.order_key)
           WHEN o.order_type = 'SPECIMEN' AND pg_temp.u('note:' || o.order_key) < 0.08 THEN
               pg_temp.pick(ARRAY['재채혈 요청 건', '어제 결과와 비교 필요', '수액 반대편 팔에서 채혈'],
                            'nt:' || o.order_key)
           WHEN o.order_type = 'PHARMACY' AND pg_temp.u('note:' || o.order_key) < 0.10 THEN
               pg_temp.pick(ARRAY['오전 투약분', '주치의 구두 확인 완료', '용량 변경분'], 'nt:' || o.order_key)
       END AS note
FROM order_seed o
JOIN staff_pool rq ON rq.dept_id = o.from_dept
JOIN staff_pool pf ON pf.dept_id = o.to_dept
WHERE o.requested_at <= (SELECT anchor FROM plan_anchor);

ALTER TABLE ord ADD COLUMN speed float8;
UPDATE ord SET speed = CASE priority WHEN 'EMERGENCY' THEN 0.35 WHEN 'URGENT' THEN 0.6 ELSE 1 END;
CREATE INDEX ON ord (id);

-- ── 진행 기록 ────────────────────────────────────────────
-- minutes 는 요청 시각에서 몇 분 뒤인가. side 는 누가 눌렀나(R 요청한 병동, P 수행 파트).
CREATE TEMP TABLE ev (order_id bigint, seq int, to_status text, minutes float8, side char(1), reason text);

-- 이송: 요청 → 접수(예정 시각) → [보류 → 접수] → 준비완료 → 이송중 → 검사중 → 복귀중 → 완료
CREATE TEMP TABLE tl_transfer AS
SELECT o.id AS order_id,
       a.t_acc, a.cancel_at, a.c_min, a.c_side, a.c_reason,
       b.t_sched, b.hold_len, b.t_hold, b.hold_reason
FROM ord o
CROSS JOIN LATERAL (
    SELECT (5 + 35 * pg_temp.u(o.order_key || ':1')) * o.speed AS t_acc,
           CASE WHEN pg_temp.u(o.order_key || ':cancel') < 0.02 THEN 1
                WHEN pg_temp.u(o.order_key || ':cancel') < 0.035 THEN 2 ELSE 0 END AS cancel_at,
           10 + 110 * pg_temp.u(o.order_key || ':c') AS c_min,
           CASE WHEN pg_temp.u(o.order_key || ':cs') < 0.7 THEN 'R' ELSE 'P' END AS c_side,
           pg_temp.pick(ARRAY['주치의 오더 취소', '환자 상태 악화로 연기', '중복 요청',
                              '퇴원 결정', '검사 일정 변경'], o.order_key || ':cr') AS c_reason
) a
CROSS JOIN LATERAL (
    SELECT extract(epoch FROM to_timestamp(ceil(extract(epoch FROM o.requested_at
               + make_interval(secs => 60 * (a.t_acc + (20 + 100 * pg_temp.u(o.order_key || ':2')) * o.speed)))
               / 600) * 600) - o.requested_at) / 60 AS t_sched,
           CASE WHEN a.cancel_at = 0 AND pg_temp.u(o.order_key || ':hold') < 0.05
                THEN 20 + 70 * pg_temp.u(o.order_key || ':3') ELSE 0 END AS hold_len,
           a.t_acc + 3 + 10 * pg_temp.u(o.order_key || ':4') AS t_hold,
           pg_temp.pick(ARRAY['응급 환자 우선 진행', '장비 점검 중', '환자 식사 중',
                              '보호자 동의 대기', '앞 검사 지연'], o.order_key || ':hr') AS hold_reason
) b
WHERE o.order_type = 'TRANSFER';

INSERT INTO ev
SELECT t.order_id, s.seq, s.st, s.m, s.side, s.reason
FROM tl_transfer t
JOIN ord o ON o.id = t.order_id
CROSS JOIN LATERAL (
    SELECT greatest(t.t_sched - (5 + 10 * pg_temp.u(o.order_key || ':5')) + t.hold_len,
                    t.t_hold + t.hold_len + 2, t.t_acc + 2) AS t_ready
) r
CROSS JOIN LATERAL (
    SELECT greatest(t.t_sched + (-3 + 8 * pg_temp.u(o.order_key || ':6')) + t.hold_len, r.t_ready + 2) AS t_transit
) tr
CROSS JOIN LATERAL (
    SELECT tr.t_transit + 5 + 10 * pg_temp.u(o.order_key || ':7') AS t_prog
) pg
CROSS JOIN LATERAL (
    SELECT pg.t_prog + o.dur * (0.8 + 0.5 * pg_temp.u(o.order_key || ':8')) AS t_ret
) rt
CROSS JOIN LATERAL (VALUES
    (1,  'REQUESTED',   0::float8,                                          'R', null::text, true),
    (2,  'ACCEPTED',    t.t_acc,                                            'P', null,       t.cancel_at <> 1),
    (3,  'ON_HOLD',     t.t_hold,                                           'P', t.hold_reason, t.hold_len > 0),
    (4,  'ACCEPTED',    t.t_hold + t.hold_len,                              'P', null,       t.hold_len > 0),
    (5,  'READY',       r.t_ready,                                          'P', null,       t.cancel_at = 0),
    (6,  'IN_TRANSIT',  tr.t_transit,                                       'R', null,       t.cancel_at = 0),
    (7,  'IN_PROGRESS', pg.t_prog,                                          'P', null,       t.cancel_at = 0),
    (8,  'RETURNED',    rt.t_ret,                                           'P', null,       t.cancel_at = 0),
    (9,  'COMPLETED',   rt.t_ret + 8 + 20 * pg_temp.u(o.order_key || ':9'), 'R', null,       t.cancel_at = 0),
    (10, 'CANCELLED',   CASE t.cancel_at WHEN 1 THEN t.c_min ELSE t.t_acc + t.c_min END,
                                                                            t.c_side, t.c_reason, t.cancel_at > 0)
) AS s(seq, st, m, side, reason, inc)
WHERE s.inc;

-- 검체: 요청 → 접수 → 채취완료(병동) → 검사중 → 결과등록 → 완료(병동)
INSERT INTO ev
SELECT o.id, s.seq, s.st, s.m, s.side, s.reason
FROM ord o
CROSS JOIN LATERAL (
    SELECT (3 + 17 * pg_temp.u(o.order_key || ':1')) * o.speed AS t_acc,
           pg_temp.u(o.order_key || ':cancel') < 0.02 AS cancelled
) a
CROSS JOIN LATERAL (SELECT a.t_acc + (10 + 30 * pg_temp.u(o.order_key || ':2')) * o.speed AS t_col) c
CROSS JOIN LATERAL (SELECT c.t_col + 10 + 25 * pg_temp.u(o.order_key || ':3') AS t_prog) p
CROSS JOIN LATERAL (SELECT p.t_prog + o.dur * (0.7 + 0.6 * pg_temp.u(o.order_key || ':4')) AS t_res) r
CROSS JOIN LATERAL (VALUES
    (1, 'REQUESTED',   0::float8,  'R', null::text, true),
    (2, 'ACCEPTED',    a.t_acc,    'P', null, NOT a.cancelled),
    (3, 'COLLECTED',   c.t_col,    'R', null, NOT a.cancelled),
    (4, 'IN_PROGRESS', p.t_prog,   'P', null, NOT a.cancelled),
    (5, 'RESULTED',    r.t_res,    'P', null, NOT a.cancelled),
    (6, 'COMPLETED',   r.t_res + 15 + 105 * pg_temp.u(o.order_key || ':5'), 'R', null, NOT a.cancelled),
    (7, 'CANCELLED',   5 + 60 * pg_temp.u(o.order_key || ':c'), 'R',
        pg_temp.pick(ARRAY['주치의 오더 취소', '중복 요청', '퇴원 결정'], o.order_key || ':cr'), a.cancelled)
) AS s(seq, st, m, side, reason, inc)
WHERE o.order_type = 'SPECIMEN' AND s.inc;

-- 약제: 요청 → 접수 → 조제중 → 조제완료 → 불출완료 → 완료(병동 수령 확인)
INSERT INTO ev
SELECT o.id, s.seq, s.st, s.m, s.side, s.reason
FROM ord o
CROSS JOIN LATERAL (
    SELECT (5 + 20 * pg_temp.u(o.order_key || ':1')) * o.speed AS t_acc,
           pg_temp.u(o.order_key || ':cancel') < 0.02 AS cancelled
) a
CROSS JOIN LATERAL (SELECT a.t_acc + 2 + 13 * pg_temp.u(o.order_key || ':2') AS t_prog) p
CROSS JOIN LATERAL (SELECT p.t_prog + o.dur * (0.7 + 0.6 * pg_temp.u(o.order_key || ':3')) AS t_disp) d
CROSS JOIN LATERAL (SELECT d.t_disp + 10 + 30 * pg_temp.u(o.order_key || ':4') AS t_deliv) v
CROSS JOIN LATERAL (VALUES
    (1, 'REQUESTED',   0::float8, 'R', null::text, true),
    (2, 'ACCEPTED',    a.t_acc,   'P', null, NOT a.cancelled),
    (3, 'IN_PROGRESS', p.t_prog,  'P', null, NOT a.cancelled),
    (4, 'DISPENSED',   d.t_disp,  'P', null, NOT a.cancelled),
    (5, 'DELIVERED',   v.t_deliv, 'P', null, NOT a.cancelled),
    (6, 'COMPLETED',   v.t_deliv + 5 + 55 * pg_temp.u(o.order_key || ':5'), 'R', null, NOT a.cancelled),
    (7, 'CANCELLED',   5 + 40 * pg_temp.u(o.order_key || ':c'), 'R',
        pg_temp.pick(ARRAY['주치의 오더 취소', '처방 변경', '퇴원 결정'], o.order_key || ':cr'), a.cancelled)
) AS s(seq, st, m, side, reason, inc)
WHERE o.order_type = 'PHARMACY' AND s.inc;

-- 의공: 요청 → 접수 → 수리중 → [부품대기 → 수리중] → 완료(수행 파트가 끝낸다)
INSERT INTO ev
SELECT o.id, s.seq, s.st, s.m, s.side, s.reason
FROM ord o
CROSS JOIN LATERAL (
    SELECT (10 + 80 * pg_temp.u(o.order_key || ':1')) * o.speed AS t_acc,
           pg_temp.u(o.order_key || ':cancel') < 0.03 AS cancelled,
           pg_temp.u(o.order_key || ':parts') < 0.15 AS parts
) a
CROSS JOIN LATERAL (SELECT a.t_acc + 10 + 110 * pg_temp.u(o.order_key || ':2') AS t_prog) p
CROSS JOIN LATERAL (SELECT p.t_prog + 30 + 210 * pg_temp.u(o.order_key || ':3') AS t_parts) w
CROSS JOIN LATERAL (SELECT w.t_parts + 1440 * (1 + 2 * pg_temp.u(o.order_key || ':4')) AS t_prog2) q
CROSS JOIN LATERAL (VALUES
    (1, 'REQUESTED',      0::float8, 'R', null::text, true),
    (2, 'ACCEPTED',       a.t_acc,   'P', null, NOT a.cancelled),
    (3, 'IN_PROGRESS',    p.t_prog,  'P', null, NOT a.cancelled),
    (4, 'AWAITING_PARTS', w.t_parts, 'P',
        pg_temp.pick(ARRAY['배터리 팩 주문', '센서 케이블 입고 대기', '제조사 부품 수급 대기',
                           '메인보드 교체 필요'], o.order_key || ':pr'), (NOT a.cancelled) AND a.parts),
    (5, 'IN_PROGRESS',    q.t_prog2, 'P', null, (NOT a.cancelled) AND a.parts),
    (6, 'COMPLETED',      CASE WHEN a.parts THEN q.t_prog2 ELSE p.t_prog END
                            + o.dur * (0.6 + 0.8 * pg_temp.u(o.order_key || ':5')), 'P', null, NOT a.cancelled),
    (7, 'CANCELLED',      20 + 100 * pg_temp.u(o.order_key || ':c'), 'R',
        pg_temp.pick(ARRAY['자체 해결됨', '예비 장비로 교체', '중복 요청'], o.order_key || ':cr'), a.cancelled)
) AS s(seq, st, m, side, reason, inc)
WHERE o.order_type = 'EQUIPMENT' AND s.inc;

-- 기준 시각 뒤의 일은 아직 일어나지 않았다. 그래서 최근 요청은 진행 중으로 남는다.
DELETE FROM ev
 USING ord o, plan_anchor p
 WHERE ev.order_id = o.id
   AND o.requested_at + make_interval(secs => 60 * ev.minutes) > p.anchor;

-- 최근 하루 요청 중 일부는 중간에 멈춰 있다. 시간표대로라면 끝났어야 하지만 실제 병동에는
-- 수령 확인을 안 누른 약, 밀린 검사, 아직 아무도 접수하지 않은 요청이 늘 남아 있다.
-- 이게 없으면 초기화 시각이 새벽일 때 큐와 보드가 전부 비어 보인다.
-- 퇴원한 환자의 요청은 멈추지 않는다 — 퇴원하면 걸린 일은 끝나거나 취소된다.
DELETE FROM ev
 USING ord o, plan_anchor p,
       (SELECT order_id, max(seq) AS last_seq, bool_or(to_status = 'CANCELLED') AS cancelled
          FROM ev GROUP BY order_id) m
 WHERE ev.order_id = o.id AND m.order_id = o.id
   AND NOT m.cancelled AND m.last_seq >= 2
   AND o.requested_at > p.anchor - interval '24 hours'
   AND (o.subject_ref IS NULL OR o.stay_end = p.anchor)
   AND pg_temp.u('stall:' || o.order_key)
       < 0.6 * (1 - extract(epoch FROM p.anchor - o.requested_at) / 86400)
   AND ev.seq >= 2 + floor(pg_temp.u('cut:' || o.order_key) * (m.last_seq - 1));

CREATE INDEX ON ev (order_id);

CREATE TEMP TABLE ev_summary AS
SELECT order_id,
       (array_agg(to_status ORDER BY minutes DESC, seq DESC))[1] AS status,
       (array_agg(reason    ORDER BY minutes DESC, seq DESC))[1] AS last_reason,
       max(minutes) AS last_min,
       count(*) AS n,
       max(minutes) FILTER (WHERE to_status = 'IN_PROGRESS') AS t_prog,
       max(minutes) FILTER (WHERE to_status = 'COMPLETED') AS t_done,
       bool_or(to_status = 'ACCEPTED') AS accepted
FROM ev
GROUP BY order_id;

INSERT INTO work_order (id, request_no, service_item_id, from_department_id, to_department_id, status, priority,
                        requested_by, requested_at, scheduled_at, started_at, completed_at, note, hold_reason,
                        version, created_at, updated_at, hold_from_status, order_type, subject_ref)
SELECT o.id,
       CASE o.order_type WHEN 'TRANSFER' THEN 'TR' WHEN 'SPECIMEN' THEN 'SP'
                         WHEN 'PHARMACY' THEN 'PH' ELSE 'EQ' END
         || to_char(o.requested_at, 'YYYYMMDD') || '-'
         || lpad(row_number() OVER (PARTITION BY o.order_type, date_trunc('day', o.requested_at)
                                    ORDER BY o.requested_at, o.id)::text, 4, '0'),
       o.item_id, o.from_dept, o.to_dept, e.status, o.priority, o.requester_id, o.requested_at,
       CASE WHEN o.order_type = 'TRANSFER' AND e.accepted
            THEN o.requested_at + make_interval(secs => 60 * t.t_sched) END,
       o.requested_at + make_interval(secs => 60 * e.t_prog),
       o.requested_at + make_interval(secs => 60 * e.t_done),
       o.note,
       CASE WHEN e.status IN ('ON_HOLD', 'CANCELLED') THEN e.last_reason END,
       e.n - 1,
       o.requested_at,
       o.requested_at + make_interval(secs => 60 * e.last_min),
       CASE WHEN e.status = 'ON_HOLD' THEN 'ACCEPTED' END,
       o.order_type, o.subject_ref
FROM ord o
JOIN ev_summary e ON e.order_id = o.id
LEFT JOIN tl_transfer t ON t.order_id = o.id;

INSERT INTO work_order_event (id, order_id, from_status, to_status, actor_id, actor_dept_id, reason, occurred_at)
SELECT row_number() OVER (ORDER BY x.occurred_at, x.order_id, x.seq), x.order_id,
       lag(x.to_status) OVER (PARTITION BY x.order_id ORDER BY x.occurred_at, x.seq),
       x.to_status, x.actor_id, x.actor_dept_id, x.reason, x.occurred_at
FROM (
    SELECT e.order_id, e.seq, e.to_status, e.reason,
           CASE e.side WHEN 'R' THEN o.requester_id ELSE o.performer_id END AS actor_id,
           CASE e.side WHEN 'R' THEN o.from_dept ELSE o.to_dept END AS actor_dept_id,
           o.requested_at + make_interval(secs => 60 * e.minutes) AS occurred_at
    FROM ev e JOIN ord o ON o.id = e.order_id
) x;

-- 접수 안 된 채 기준 시간(응급 10분·긴급 30분)을 넘긴 요청은 그때 이미 알린 것으로 찍는다.
-- 안 찍으면 초기화 1분 뒤 감시가 이것들을 한꺼번에 수간호사에게 올린다.
UPDATE work_order
   SET delay_notified_at = requested_at + CASE priority WHEN 'EMERGENCY' THEN interval '10 minutes'
                                                        ELSE interval '30 minutes' END
 WHERE status = 'REQUESTED'
   AND priority IN ('EMERGENCY', 'URGENT')
   AND requested_at + CASE priority WHEN 'EMERGENCY' THEN interval '10 minutes'
                                    ELSE interval '30 minutes' END <= (SELECT anchor FROM plan_anchor);

-- 요청번호 발번 표. 오늘 새로 만드는 요청이 기존 번호 뒤를 잇게 한다.
INSERT INTO request_no_sequence (date_key, order_type, last_no)
SELECT date_trunc('day', requested_at)::date, order_type, count(*)
FROM work_order
GROUP BY 1, 2;

-- ── 대화 ─────────────────────────────────────────────────
-- 요청 열에 하나 정도에 한두 마디가 오간다. 내용은 원내 DB 에 들어가고(초기화 스크립트가 옮긴다)
-- 여기에는 누가·언제와 열쇠만 남는다.
INSERT INTO request_message (id, order_id, sender_id, created_at, message_ref)
SELECT row_number() OVER (ORDER BY m.created_at, m.order_id, m.k), m.order_id, m.sender_id, m.created_at, m.message_ref
FROM (
    SELECT o.id AS order_id, k.k,
           CASE WHEN (pg_temp.u('ms:' || o.order_key) < 0.55) = (k.k = 1) THEN o.requester_id ELSE o.performer_id END AS sender_id,
           o.requested_at + make_interval(secs => 60 * (5 + 60 * pg_temp.u('mt:' || o.order_key)
                                                        + (k.k - 1) * (3 + 25 * pg_temp.u('mt2:' || o.order_key)))) AS created_at,
           md5('msg:' || o.order_key || ':' || k.k)::uuid AS message_ref
    FROM ord o
    CROSS JOIN generate_series(1, 2) AS k(k)
    WHERE o.order_type <> 'EQUIPMENT'
      AND pg_temp.u('msg:' || o.order_key) < 0.09
      AND (k.k = 1 OR pg_temp.u('msg2:' || o.order_key) < 0.35)
) m
WHERE m.created_at <= (SELECT anchor FROM plan_anchor);

-- ── 알림함 ───────────────────────────────────────────────
-- 서버와 같은 규칙으로 받는 사람을 고른다(api-spec 7장). 그 요청에 그때까지 손댄 사람 —
-- 요청한 사람, 진행 기록의 행위자, 메시지를 남긴 사람 — 이고, 한쪽 파트에 아직 손댄 사람이 없으면
-- 그 파트 전원이다. 누른 사람 본인은 뺀다. 최근 하루 치만 만든다 — 알림함은 어차피 최근 것만 본다.
CREATE TEMP TABLE noti_src AS
SELECT 'e' || e.id AS src_key, e.order_id, e.occurred_at AS at, e.actor_id, e.actor_dept_id,
       CASE WHEN e.to_status = 'REQUESTED' THEN 'ORDER_CREATED' ELSE 'STATUS_CHANGED' END AS noti_type,
       e.to_status
FROM work_order_event e, plan_anchor p
WHERE e.occurred_at > p.anchor - interval '24 hours'
UNION ALL
SELECT 'm' || m.id, m.order_id, m.created_at, m.sender_id, s.department_id, 'MESSAGE', NULL
FROM request_message m
JOIN staff s ON s.id = m.sender_id, plan_anchor p
WHERE m.created_at > p.anchor - interval '24 hours';

CREATE TEMP TABLE noti_involved AS
SELECT DISTINCT n.src_key, x.staff_id, st.department_id
FROM noti_src n
JOIN work_order w ON w.id = n.order_id
CROSS JOIN LATERAL (
    SELECT w.requested_by AS staff_id
    UNION SELECT e.actor_id FROM work_order_event e WHERE e.order_id = n.order_id AND e.occurred_at <= n.at
    UNION SELECT m.sender_id FROM request_message m WHERE m.order_id = n.order_id AND m.created_at <= n.at
) x
JOIN staff st ON st.id = x.staff_id AND st.is_active;
CREATE INDEX ON noti_involved (src_key, department_id);

INSERT INTO notification (recipient_id, noti_type, ref_type, ref_id, title, body, read_at, created_at)
SELECT sp.id, n.noti_type, 'WORK_ORDER', w.id,
       CASE n.noti_type
           WHEN 'ORDER_CREATED' THEN fd.name || '에서 새 요청을 보냈습니다'
           WHEN 'MESSAGE' THEN ad.name || ' ' || actor.name || '님이 메시지를 남겼습니다'
           ELSE ad.name || '에서 ' || CASE n.to_status
                WHEN 'ACCEPTED' THEN '접수됨' WHEN 'READY' THEN '준비완료' WHEN 'IN_TRANSIT' THEN '이송중'
                WHEN 'RETURNED' THEN '복귀중' WHEN 'IN_PROGRESS' THEN '진행중' WHEN 'COLLECTED' THEN '채취완료'
                WHEN 'RESULTED' THEN '결과등록' WHEN 'DISPENSED' THEN '조제완료' WHEN 'DELIVERED' THEN '불출완료'
                WHEN 'AWAITING_PARTS' THEN '부품대기' WHEN 'COMPLETED' THEN '완료' WHEN 'ON_HOLD' THEN '보류'
                WHEN 'CANCELLED' THEN '취소' ELSE n.to_status END || ' 처리했습니다' END,
       coalesce(ce.room_no || '호 / ', '') || si.name
         || coalesce(' / ' || to_char(w.scheduled_at, 'HH24:MI') || ' 예정', ''),
       CASE WHEN n.at < p.anchor - interval '6 hours'
                 AND pg_temp.u('rd:' || n.src_key || ':' || sp.id) < 0.85
            THEN n.at + make_interval(secs => 60 * (5 + 115 * pg_temp.u('rt:' || n.src_key || ':' || sp.id))) END,
       n.at
FROM noti_src n
CROSS JOIN plan_anchor p
JOIN work_order w ON w.id = n.order_id
JOIN service_item si ON si.id = w.service_item_id
JOIN department fd ON fd.id = w.from_department_id
JOIN department ad ON ad.id = n.actor_dept_id
JOIN staff actor ON actor.id = n.actor_id
LEFT JOIN care_episode ce ON ce.subject_ref = w.subject_ref
JOIN staff sp ON sp.department_id IN (w.from_department_id, w.to_department_id)
             AND sp.is_active AND sp.id <> n.actor_id
WHERE EXISTS (SELECT 1 FROM noti_involved i WHERE i.src_key = n.src_key AND i.staff_id = sp.id)
   OR NOT EXISTS (SELECT 1 FROM noti_involved i
                   WHERE i.src_key = n.src_key AND i.department_id = sp.department_id);

SELECT setval('transfer_request_id_seq', (SELECT max(id) FROM work_order));
SELECT setval('transfer_event_id_seq',   (SELECT max(id) FROM work_order_event));
SELECT setval('request_message_id_seq',  greatest((SELECT max(id) FROM request_message), 1));

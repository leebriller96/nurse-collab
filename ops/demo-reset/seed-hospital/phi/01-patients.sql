-- =========================================================
-- 01-patients.sql — 환자·재원·주의사항 (원내 DB)
--
-- 입원 한 건마다 환자 한 명이다. 재입원은 만들지 않는다 — 가명이 재원 건에 붙으므로
-- 재입원이어도 업무 쪽에서는 다른 사람처럼 보이고, 시연에 차이가 없다.
-- 이름은 성 + OO, 등록번호와 보호자 연락처는 지어낸 번호다.
-- =========================================================

CREATE TEMP TABLE patient_plan AS
SELECT s.seq, s.subject_ref, s.ward_id, s.specialty, s.room_no, s.bed_no,
       s.admitted_at, s.discharged_at,
       CASE s.specialty
           WHEN 'ORTHO' THEN 25 + floor(66 * power(pg_temp.u('age:' || s.subject_ref), 0.7))
           WHEN 'SURG'  THEN 30 + floor(58 * power(pg_temp.u('age:' || s.subject_ref), 0.8))
           WHEN 'INFECT' THEN 22 + floor(70 * power(pg_temp.u('age:' || s.subject_ref), 0.8))
           ELSE              45 + floor(48 * power(pg_temp.u('age:' || s.subject_ref), 0.6))
       END::int AS age,
       CASE WHEN pg_temp.u('sex:' || s.subject_ref) < 0.5 THEN 'M' ELSE 'F' END AS sex,
       CASE s.specialty
           WHEN 'GI' THEN pg_temp.pick(ARRAY['급성 담낭염', '급성 담관염', '급성 췌장염', '상부위장관 출혈',
               '간경변 악화', '감염성 장염', '급성 신우신염', '저나트륨혈증', '당뇨병성 케톤산증',
               '위궤양 출혈'], 'dx:' || s.subject_ref)
           WHEN 'PULM' THEN pg_temp.pick(ARRAY['지역사회획득 폐렴', '흡인성 폐렴', 'COPD 급성 악화',
               '천식 악화', '울혈성 심부전', '심방세동', '불안정 협심증', '폐색전증', '흉수',
               '기관지확장증 감염'], 'dx:' || s.subject_ref)
           WHEN 'ORTHO' THEN pg_temp.pick(ARRAY['대퇴골 경부 골절', '전슬관절 치환술 후', '전고관절 치환술 후',
               '요추 추간판탈출증', '척추관 협착증', '척추 압박골절', '발목 골절', '회전근개 파열 수술 후',
               '원위 요골 골절', '하지 봉와직염'], 'dx:' || s.subject_ref)
           WHEN 'SURG' THEN pg_temp.pick(ARRAY['급성 충수염 수술 후', '담낭 절제술 후', '위암 수술 후',
               '대장암 수술 후', '서혜부 탈장 수술 후', '장폐색', '갑상선 절제술 후', '치핵 수술 후',
               '유방암 수술 후', '복부 창상 감염'], 'dx:' || s.subject_ref)
           WHEN 'NEURO' THEN pg_temp.pick(ARRAY['뇌경색', '뇌출혈 재활', '일과성 허혈발작', '파킨슨병 악화',
               '뇌전증 발작', '길랭-바레 증후군', '척수 손상 재활', '어지럼증', '말초 신경병증',
               '섬망 동반 치매'], 'dx:' || s.subject_ref)
           WHEN 'INFECT' THEN pg_temp.pick(ARRAY['코로나19', '인플루엔자', '결핵 의심', '대상포진',
               'CRE 보균', 'VRE 보균', 'MRSA 폐렴', '클로스트리디오이데스 디피실 장염', '수두',
               '옴'], 'dx:' || s.subject_ref)
           ELSE pg_temp.pick(ARRAY['패혈성 쇼크', '급성 호흡부전', '심정지 후 치료', '중증 폐렴',
               '다발성 외상', '급성 심근경색', '뇌출혈', '당뇨병성 케톤산증', '급성 신손상',
               '수술 후 집중 관찰'], 'dx:' || s.subject_ref)
       END AS diagnosis
FROM stay_plan s;

ALTER TABLE patient_plan ADD COLUMN mobile boolean;
UPDATE patient_plan SET mobile = CASE specialty
    WHEN 'ICU'   THEN false
    WHEN 'ORTHO' THEN pg_temp.u('mob:' || subject_ref) < 0.4
    WHEN 'NEURO' THEN pg_temp.u('mob:' || subject_ref) < 0.5
    ELSE CASE WHEN age >= 80 THEN pg_temp.u('mob:' || subject_ref) < 0.55
              ELSE pg_temp.u('mob:' || subject_ref) < 0.85 END
END;

-- 성은 실제 분포에 가깝게 김·이·박이 많다
INSERT INTO patient (id, patient_no, name, birth_date, sex, guardian_phone, created_at, updated_at)
SELECT seq,
       'P' || lpad((1000000 + seq)::text, 7, '0'),
       pg_temp.pick(ARRAY['김','김','김','김','김','이','이','이','이','박','박','박','최','최','정','정',
                          '강','조','윤','장','임','한','오','서','신','권','황','안','송','전','홍','유',
                          '고','문','양','손','배','백','허','남'], 'pn:' || subject_ref) || 'OO',
       (admitted_at::date - (age * 365 + floor(365 * pg_temp.u('bd:' || subject_ref)))::int),
       sex,
       '010-0000-' || lpad((seq % 10000)::text, 4, '0'),
       admitted_at, admitted_at
FROM patient_plan
ORDER BY seq;

INSERT INTO encounter (id, patient_id, department_id, room_no, bed_no, admitted_at, discharged_at, status,
                       diagnosis, is_mobile, subject_ref, created_at, updated_at)
SELECT seq, seq, ward_id, room_no, bed_no, admitted_at, discharged_at,
       CASE WHEN discharged_at IS NULL THEN 'ADMITTED' ELSE 'DISCHARGED' END,
       diagnosis, mobile, subject_ref, admitted_at, coalesce(discharged_at, admitted_at)
FROM patient_plan
ORDER BY seq;

-- ── 주의사항 ─────────────────────────────────────────────
-- 곁에서 본 것을 간호사가 남긴다. 입원 후 몇 시간 안에 그 병동 간호사 중 한 명이.
CREATE TEMP TABLE alert_plan (seq bigint, alert_type text, severity text, content text);

-- 낙상: 고령, 정형외과, 신경과
INSERT INTO alert_plan
SELECT seq, 'FALL_RISK', 'WARN',
       pg_temp.pick(ARRAY['낙상 위험 등급 상. 이동 시 반드시 동반', '낙상 위험 등급 중. 야간 침상난간 올림',
                          '보행 시 어지러움 호소, 보조기 사용'], 'fa:' || subject_ref)
FROM patient_plan
WHERE (age >= 75 AND pg_temp.u('fall:' || subject_ref) < 0.7)
   OR (specialty IN ('ORTHO', 'NEURO') AND pg_temp.u('fall:' || subject_ref) < 0.5);

-- 체내 금속물: 치환술·골절 수술, 드물게 심박동기
INSERT INTO alert_plan
SELECT seq, 'METAL_IMPLANT', 'CRITICAL',
       CASE WHEN diagnosis LIKE '%고관절%' THEN '좌측 고관절 인공관절'
            WHEN diagnosis LIKE '%슬관절%' THEN '우측 슬관절 인공관절'
            WHEN diagnosis LIKE '%골절%' THEN '골절 부위 금속판 고정'
            WHEN specialty = 'PULM' THEN '심박동기 삽입(2021년)'
            ELSE '요추 척추 고정술 금속 나사' END
FROM patient_plan
WHERE (specialty = 'ORTHO' AND (diagnosis LIKE '%치환술%' OR diagnosis LIKE '%골절%')
       AND pg_temp.u('metal:' || subject_ref) < 0.85)
   OR (pg_temp.u('metal:' || subject_ref) < 0.03);

-- 격리: 감염병동은 전원, 그 밖은 드물게
INSERT INTO alert_plan
SELECT seq, 'ISOLATION', 'WARN',
       CASE WHEN diagnosis IN ('코로나19', '인플루엔자') THEN '비말주의 격리'
            WHEN diagnosis IN ('결핵 의심', '수두') THEN '공기주의 격리(음압병실)'
            WHEN specialty = 'INFECT' THEN '접촉주의 격리'
            ELSE '접촉주의 격리 — CRE 보균' END
FROM patient_plan
WHERE specialty = 'INFECT' OR pg_temp.u('iso:' || subject_ref) < 0.03;

INSERT INTO alert_plan
SELECT seq, 'DRUG_ALLERGY', 'WARN',
       pg_temp.pick(ARRAY['페니실린 알레르기', '세팔로스포린 알레르기', 'NSAIDs 과민반응',
                          '아스피린 알레르기', '설파제 알레르기'], 'da:' || subject_ref)
FROM patient_plan WHERE pg_temp.u('drug:' || subject_ref) < 0.12;

INSERT INTO alert_plan
SELECT seq, 'CONTRAST_ALLERGY', 'CRITICAL',
       pg_temp.pick(ARRAY['요오드 조영제 아나필락시스 이력', '조영제 투여 후 전신 두드러기 이력'], 'ca:' || subject_ref)
FROM patient_plan WHERE pg_temp.u('contrast:' || subject_ref) < 0.04;

INSERT INTO alert_plan
SELECT seq, 'CLAUSTROPHOBIA', 'WARN', '폐소공포 이력. 이전 MRI 중단 경험 있음'
FROM patient_plan WHERE pg_temp.u('claus:' || subject_ref) < 0.04;

INSERT INTO alert_plan
SELECT seq, 'NPO', 'INFO',
       CASE WHEN specialty = 'SURG' THEN '수술 전 금식 중' ELSE '내시경 전 금식' END
FROM patient_plan
WHERE (specialty = 'SURG' AND pg_temp.u('npo:' || subject_ref) < 0.30)
   OR (specialty = 'GI' AND pg_temp.u('npo:' || subject_ref) < 0.15);

INSERT INTO alert_plan
SELECT seq, 'OXYGEN', 'WARN',
       CASE WHEN specialty = 'ICU' THEN '고유량 산소요법 중' ELSE '비강 캐뉼라 2L/min' END
FROM patient_plan
WHERE (specialty = 'PULM' AND pg_temp.u('o2:' || subject_ref) < 0.35)
   OR (specialty = 'ICU' AND pg_temp.u('o2:' || subject_ref) < 0.80);

INSERT INTO patient_alert (patient_id, alert_type, severity, content, is_active, created_by, created_at)
SELECT a.seq, a.alert_type, a.severity, a.content,
       -- 금식은 검사·수술이 끝나면 내린다. 퇴원한 환자의 금식은 내려져 있다.
       NOT (a.alert_type = 'NPO' AND p.discharged_at IS NOT NULL),
       sp.ids[1 + floor(pg_temp.u('ab:' || p.subject_ref || ':' || a.alert_type) * sp.n)::int],
       p.admitted_at + make_interval(secs => 3600 * (0.5 + 5 * pg_temp.u('at:' || p.subject_ref || ':' || a.alert_type)))
FROM alert_plan a
JOIN patient_plan p ON p.seq = a.seq
JOIN staff_pool sp ON sp.dept_id = p.ward_id
ORDER BY a.seq, a.alert_type;

SELECT setval('patient_id_seq',   (SELECT max(id) FROM patient));
SELECT setval('encounter_id_seq', (SELECT max(id) FROM encounter));

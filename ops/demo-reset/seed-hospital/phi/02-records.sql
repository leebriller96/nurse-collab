-- =========================================================
-- 02-records.sql — 활력징후와 간호기록 (원내 DB)
--
-- 병동은 하루 세 번(06·14·22시 무렵), 중환자실은 네 시간마다 활력징후를 잰다.
-- 간호기록은 교대가 끝날 때(07·15·23시) 인수인계를 SBAR 로 남기고, 낮에 한 번쯤 일반 기록을 남긴다.
-- 기록자는 그 병동 간호사 중 한 명이고, 이름은 쓸 때 굳힌다(업무 DB 의 직원 이름과 같다).
-- =========================================================

-- ── 활력징후 ─────────────────────────────────────────────
INSERT INTO vital_sign (encounter_id, measured_at, temperature, pulse, respiration, sbp, dbp, spo2, pain_score,
                        recorded_by, recorded_by_name, created_at)
SELECT sd.seq, v.at,
       round((36.3 + 0.6 * pg_temp.u('tb:' || sd.subject_ref)
              + CASE WHEN f.fever THEN 1.2 + 1.3 * pg_temp.u('tf:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr)
                     ELSE 0.3 * (pg_temp.u('tv:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr) - 0.5) END)::numeric, 1),
       (62 + 22 * pg_temp.u('pb:' || sd.subject_ref)
        + 8 * pg_temp.u('pv:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr)
        + CASE WHEN f.fever THEN 14 ELSE 0 END + CASE WHEN sd.specialty = 'ICU' THEN 10 ELSE 0 END)::int,
       (14 + 5 * pg_temp.u('rv:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr)
        + CASE WHEN sd.specialty IN ('PULM', 'ICU') THEN 4 ELSE 0 END)::int,
       (104 + 42 * pg_temp.u('sb:' || sd.subject_ref)
        + 14 * (pg_temp.u('sv:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr) - 0.5))::int,
       (58 + 24 * pg_temp.u('db:' || sd.subject_ref)
        + 8 * (pg_temp.u('dv:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr) - 0.5))::int,
       CASE sd.specialty
           WHEN 'PULM' THEN 89 + 8 * pg_temp.u('ov:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr)
           WHEN 'ICU'  THEN 91 + 8 * pg_temp.u('ov:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr)
           ELSE             95 + 4 * pg_temp.u('ov:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr)
       END::int,
       CASE WHEN sd.specialty IN ('SURG', 'ORTHO') AND sd.day_no <= 2
            THEN floor(7 * power(pg_temp.u('pn:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr), 0.8))
            ELSE floor(4 * power(pg_temp.u('pn:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr), 2)) END::int,
       sp.ids[1 + floor(pg_temp.u('vr:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr) * sp.n)::int],
       sp.names[1 + floor(pg_temp.u('vr:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr) * sp.n)::int],
       v.at
FROM stay_day sd
JOIN staff_pool sp ON sp.dept_id = sd.ward_id
CROSS JOIN LATERAL unnest(CASE WHEN sd.specialty = 'ICU' THEN ARRAY[2, 6, 10, 14, 18, 22]
                               ELSE ARRAY[6, 14, 22] END) AS h(hr)
CROSS JOIN LATERAL (
    SELECT sd.day_start + make_interval(secs => 3600 * h.hr
               + 60 * floor(40 * pg_temp.u('vm:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr))) AS at
) v
CROSS JOIN LATERAL (
    SELECT pg_temp.u('fv:' || sd.subject_ref || ':' || sd.day_no) < CASE
               WHEN sd.specialty = 'INFECT' THEN 0.35 WHEN sd.specialty = 'ICU' THEN 0.30
               WHEN sd.specialty = 'PULM' THEN 0.25
               WHEN sd.specialty = 'SURG' AND sd.day_no <= 2 THEN 0.25 ELSE 0.08 END AS fever
) f
WHERE v.at >= sd.admitted_at + interval '30 minutes'
  AND v.at <= sd.stay_end;

-- ── 간호기록 : 교대 인수인계 (SBAR) ──────────────────────
INSERT INTO nursing_note (encounter_id, note_type, situation, background, assessment, recommendation,
                          recorded_at, recorded_by, recorded_by_name, created_at)
SELECT sd.seq, 'HANDOVER',
       replace(replace(pg_temp.pick(ARRAY[
           '{dx}로 입원 {d}일차. 특이 호소 없음',
           '야간 수면 양호, 통증 호소 없음',
           '통증 호소 NRS 4점, 처방된 진통제 투여함',
           '식사 절반 정도 섭취, 오심 없음',
           '보행 시 어지러움 호소',
           '근무 중 38도 이상 발열 1회 있음',
           '기침과 가래 지속, 흡인 1회 시행',
           '수술 부위 삼출물 소량, 드레싱 교환함'], 'ns:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr),
           '{dx}', e.diagnosis), '{d}', (sd.day_no + 1)::text),
       replace(pg_temp.pick(ARRAY[
           '{dx}', '{dx}. 기저질환 고혈압, 당뇨', '{dx}. 낙상 고위험군', '{dx}. 항생제 치료 중',
           '{dx}. 보호자 상주'], 'nb:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr), '{dx}', e.diagnosis),
       pg_temp.pick(ARRAY[
           '활력징후 안정적', '해열제 투여 후 37.4도로 하강', '산소포화도 94% 이상 유지',
           '수술 부위 발적·부종 없음', '의식 명료, 지남력 있음', '섭취량 대비 배설량 적음, 관찰 필요',
           '통증 조절 양호'], 'na:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr),
       pg_temp.pick(ARRAY[
           '4시간 간격 활력징후 확인', '내일 오전 검사 예정, 자정부터 금식 유지', '낙상 예방 교육 재실시',
           '보호자 교육 필요', '통증 지속 시 당직의 보고', '배액관 배액량 관찰', '다음 근무조 혈당 재확인'],
           'nr:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr),
       n.at,
       sp.ids[1 + floor(pg_temp.u('nw:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr) * sp.n)::int],
       sp.names[1 + floor(pg_temp.u('nw:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr) * sp.n)::int],
       n.at
FROM stay_day sd
JOIN encounter e ON e.id = sd.seq
JOIN staff_pool sp ON sp.dept_id = sd.ward_id
CROSS JOIN LATERAL unnest(ARRAY[7, 15, 23]) AS h(hr)
CROSS JOIN LATERAL (
    SELECT sd.day_start + make_interval(secs => 3600 * h.hr
               + 60 * floor(25 * pg_temp.u('nm:' || sd.subject_ref || ':' || sd.day_no || ':' || h.hr))) AS at
) n
WHERE n.at >= sd.admitted_at + interval '1 hour'
  AND n.at <= sd.stay_end;

-- ── 간호기록 : 일반 ──────────────────────────────────────
INSERT INTO nursing_note (encounter_id, note_type, content, recorded_at, recorded_by, recorded_by_name, created_at)
SELECT sd.seq, 'GENERAL',
       pg_temp.pick(ARRAY[
           '체위 변경 시행함', '보호자 면회, 현재 상태 설명함', '수액 주입 속도 재조정함',
           '낙상 예방 위해 침상난간 올림 확인', '처방된 경구약 복용 확인', '식후 혈당 182mg/dL, 주치의 보고함',
           '드레싱 교환 시행, 삼출물 없음', '보조하여 복도 보행 10분', '소변량 감소, 수분 섭취 권장',
           '말초정맥관 재삽입함'], 'gc:' || sd.subject_ref || ':' || sd.day_no),
       g.at,
       sp.ids[1 + floor(pg_temp.u('gw:' || sd.subject_ref || ':' || sd.day_no) * sp.n)::int],
       sp.names[1 + floor(pg_temp.u('gw:' || sd.subject_ref || ':' || sd.day_no) * sp.n)::int],
       g.at
FROM stay_day sd
JOIN staff_pool sp ON sp.dept_id = sd.ward_id
CROSS JOIN LATERAL (
    SELECT sd.day_start + make_interval(secs => 3600 * (9 + 11 * pg_temp.u('gt:' || sd.subject_ref || ':' || sd.day_no))) AS at
) g
WHERE pg_temp.u('gp:' || sd.subject_ref || ':' || sd.day_no) < 0.6
  AND g.at >= sd.admitted_at + interval '1 hour'
  AND g.at <= sd.stay_end;

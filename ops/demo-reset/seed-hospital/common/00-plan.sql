-- =========================================================
-- 00-plan.sql — 작은 병원 데이터의 설계도 (업무 DB · 원내 DB 공통)
--
-- 초기화 스크립트가 seed-hospital/work, seed-hospital/phi 의 **모든 파일 앞에** 이 내용을 붙여 돌린다.
-- 두 DB 가 따로 돌아도 같은 병상·같은 입원·같은 가명·같은 직원을 계산하게 하려는 것이다.
--
-- 무작위(random)를 쓰지 않는다. 모든 값은 번호의 해시에서 나온다. random() 은 두 DB 에서
-- 호출 순서가 조금만 달라도 다른 값을 내고, 그러면 원내의 환자와 업무의 침대가 어긋난다.
-- 시각은 초기화 스크립트가 한 번 정해 넘기는 :anchor 하나에 맞춘다. now() 를 각자 부르면
-- 두 DB 를 채우는 사이에 시간이 흘러 한쪽에만 있는 입원이 생긴다.
--
-- 전부 가상이다. 환자 이름은 성 + OO, 등록번호와 연락처도 지어낸 번호다.
-- =========================================================

SET TIME ZONE 'Asia/Seoul';

-- 해시 → 0 이상 1 미만. 같은 글자면 어느 DB 에서든 같은 값이다.
CREATE OR REPLACE FUNCTION pg_temp.u(t text) RETURNS double precision
    LANGUAGE sql IMMUTABLE AS
$$ SELECT ('x' || substr(md5(t), 1, 12))::bit(48)::bigint / 281474976710656.0 $$;

-- 배열에서 하나 고르기
CREATE OR REPLACE FUNCTION pg_temp.pick(arr text[], t text) RETURNS text
    LANGUAGE sql IMMUTABLE AS
$$ SELECT arr[1 + floor(pg_temp.u(t) * array_length(arr, 1))::int] $$;

CREATE TEMP TABLE plan_anchor AS
SELECT :'anchor'::timestamptz                              AS anchor,
       date_trunc('day', :'anchor'::timestamptz) - interval '180 days' AS sim_start;

-- ── 부서 ─────────────────────────────────────────────────
-- 1~8 은 기본 데모 데이터와 같은 번호·코드다. 데모 계정과 시나리오 문서가 그대로 통한다.
-- 중환자실은 병동 화면(환자 보드·활력징후)을 써야 해서 유형을 WARD 로 둔다.
CREATE TEMP TABLE dept_plan (
    id bigint, code text, name text, dept_type text, location text, phone text,
    specialty text, floor int, is_ward boolean
);
INSERT INTO dept_plan VALUES
    ( 1, 'W03',  '3병동',          'WARD',     '본관 3층',      '1303', 'GI',      3, true),
    ( 2, 'W05',  '5병동',          'WARD',     '본관 5층',      '1505', 'ORTHO',   5, true),
    ( 3, 'MRI',  'MRI실',          'EXAM',     '별관 지하 1층', '1707', null,   null, false),
    ( 4, 'CT',   'CT실',           'EXAM',     '별관 지하 1층', '1708', null,   null, false),
    ( 5, 'ADM',  '전산팀',         'ADMIN',    '본관 1층',      '1100', null,   null, false),
    ( 6, 'LAB',  '진단검사의학과', 'LAB',      '본관 지하 1층', '1901', null,   null, false),
    ( 7, 'PHM',  '약제부',         'PHARMACY', '본관 1층',      '1401', null,   null, false),
    ( 8, 'BME',  '의공학팀',       'BIOMED',   '별관 2층',      '1601', null,   null, false),
    ( 9, 'W04',  '4병동',          'WARD',     '본관 4층',      '1404', 'PULM',    4, true),
    (10, 'W06',  '6병동',          'WARD',     '본관 6층',      '1606', 'SURG',    6, true),
    (11, 'W07',  '7병동',          'WARD',     '본관 7층',      '1707', 'NEURO',   7, true),
    (12, 'W08',  '8병동',          'WARD',     '본관 8층',      '1808', 'INFECT',  8, true),
    (13, 'ICU',  '중환자실',       'WARD',     '본관 2층',      '1200', 'ICU',  null, true),
    (14, 'XR',   '일반촬영실',     'EXAM',     '본관 1층',      '1170', null,   null, false),
    (15, 'US',   '초음파실',       'EXAM',     '본관 1층',      '1180', null,   null, false),
    (16, 'ENDO', '내시경실',       'EXAM',     '본관 2층',      '1250', null,   null, false);

-- ── 직원 ─────────────────────────────────────────────────
-- 부서마다 인원을 정하고 번호로 만든다. 데모 계정은 같은 아이디·이름·소속으로 넣는다.
-- 병동 간호사는 3교대라 병동당 16명(중환자실 24명)에 수간호사 1명.
CREATE TEMP TABLE staff_plan (
    id bigint, dept_id bigint, idx int, login_id text, employee_no text,
    name text, role text
);
WITH headcount(dept_id, nurses, prefix) AS (
    VALUES (1, 17, 'w03'), (9, 17, 'w04'), (2, 17, 'w05'), (10, 17, 'w06'),
           (11, 17, 'w07'), (12, 17, 'w08'), (13, 25, 'icu'),
           (3, 6, 'mri'), (4, 6, 'ct'), (14, 5, 'xr'), (15, 4, 'us'), (16, 5, 'endo'),
           (6, 10, 'lab'), (7, 8, 'pharm'), (8, 4, 'bme'), (5, 3, 'admin')
), members AS (
    SELECT h.dept_id, h.prefix, g AS idx
    FROM headcount h, generate_series(1, h.nurses) g
), surnames AS (
    SELECT ARRAY['김','김','김','김','이','이','이','박','박','최','정','강','조','윤','장',
                 '임','한','오','서','신','권','황','안','송','전','홍','유','고','문','양'] AS arr
), givens AS (
    SELECT ARRAY['지현','수진','은정','민지','서연','하은','지은','혜진','유진','소영','미경','현주',
                 '다은','예린','수빈','지혜','은비','가영','보람','세희','윤정','나래','혜원','주연',
                 '민수','성훈','재현','동욱','상민','태호'] AS arr
)
INSERT INTO staff_plan
SELECT row_number() OVER (ORDER BY m.dept_id, m.idx) + 100 AS id,
       m.dept_id, m.idx,
       m.prefix || lpad(m.idx::text, 2, '0'),
       'E' || lpad((m.dept_id * 1000 + m.idx)::text, 6, '0'),
       pg_temp.pick(s.arr, 'staff-sn:' || m.dept_id || ':' || m.idx)
         || pg_temp.pick(g.arr, 'staff-gn:' || m.dept_id || ':' || m.idx),
       CASE WHEN m.dept_id = 5 THEN 'ADMIN' WHEN m.idx = 1 THEN 'HEAD_NURSE' ELSE 'NURSE' END
FROM members m, surnames s, givens g;

-- 데모 계정 — 기본 데이터와 같은 아이디·이름·역할·소속. 로그인 화면의 버튼이 그대로 통한다.
UPDATE staff_plan SET login_id = 'head01',  name = '정수간호', role = 'HEAD_NURSE' WHERE dept_id = 1  AND idx = 1;
UPDATE staff_plan SET login_id = 'ward01',  name = '김간호',   role = 'NURSE'      WHERE dept_id = 1  AND idx = 2;
UPDATE staff_plan SET login_id = 'ward02',  name = '이간호',   role = 'NURSE'      WHERE dept_id = 2  AND idx = 2;
UPDATE staff_plan SET login_id = 'mri01',   name = '박간호',   role = 'NURSE'      WHERE dept_id = 3  AND idx = 2;
UPDATE staff_plan SET login_id = 'ct01',    name = '최간호',   role = 'NURSE'      WHERE dept_id = 4  AND idx = 2;
UPDATE staff_plan SET login_id = 'lab01',   name = '한검사',   role = 'NURSE'      WHERE dept_id = 6  AND idx = 2;
UPDATE staff_plan SET login_id = 'pharm01', name = '오약사',   role = 'NURSE'      WHERE dept_id = 7  AND idx = 2;
UPDATE staff_plan SET login_id = 'bme01',   name = '서기사',   role = 'NURSE'      WHERE dept_id = 8  AND idx = 2;
UPDATE staff_plan SET login_id = 'admin01', name = '관리자',   role = 'ADMIN'      WHERE dept_id = 5  AND idx = 1;
-- 수행 파트의 "01" 아이디는 데모 계정이 쓰므로 나머지는 아이디가 겹치지 않게 한 칸 민다
UPDATE staff_plan SET login_id = regexp_replace(login_id, '01$', '00')
 WHERE idx = 1 AND dept_id IN (3, 4, 6, 7, 8) ;

-- 한 부서의 직원을 번호로 고르기 위한 표
CREATE TEMP TABLE staff_pool AS
SELECT dept_id, array_agg(id ORDER BY idx) AS ids, array_agg(name ORDER BY idx) AS names,
       count(*)::int AS n
FROM staff_plan
GROUP BY dept_id;

-- ── 병상 ─────────────────────────────────────────────────
-- 일반 병동은 한 층에 10개 병실, 36병상 — 1인실 둘, 2인실 둘, 4인실 셋, 6인실 셋.
-- 중환자실은 한 칸에 12병상.
CREATE TEMP TABLE bed_plan AS
WITH rooms(r, beds) AS (
    VALUES (1, 1), (2, 1), (3, 2), (4, 2), (5, 4), (6, 4), (7, 4), (8, 6), (9, 6), (10, 6)
)
SELECT d.id AS ward_id, d.code AS ward_code, d.specialty,
       (d.floor * 100 + r.r)::text AS room_no, b::text AS bed_no,
       d.code || '-' || (d.floor * 100 + r.r) || '-' || b AS bed_key
FROM dept_plan d
JOIN rooms r ON true
JOIN generate_series(1, 6) b ON b <= r.beds
WHERE d.is_ward AND d.specialty <> 'ICU'
UNION ALL
SELECT d.id, d.code, d.specialty, 'ICU', b::text, 'ICU-' || b
FROM dept_plan d, generate_series(1, 12) b
WHERE d.specialty = 'ICU';

-- ── 입원 ─────────────────────────────────────────────────
-- 침대마다 입원을 이어 붙인다. 재원 일수는 진료과마다 다르고, 퇴원 뒤 다음 입원까지 비는 시간이 있다.
-- 가동률이 85~90% 가 되도록 맞췄다. 180일 전부터 기준 시각까지 입원한 것만 남긴다.
CREATE TEMP TABLE stay_plan AS
WITH raw AS (
    SELECT b.*, k,
           -- 재원 일수: 진료과별 최소 + 폭 (한쪽으로 치우친 분포)
           CASE b.specialty
               WHEN 'ICU'    THEN 1.5 + 7  * power(pg_temp.u('los:' || b.bed_key || ':' || k), 1.6)
               WHEN 'ORTHO'  THEN 4   + 12 * power(pg_temp.u('los:' || b.bed_key || ':' || k), 1.3)
               WHEN 'NEURO'  THEN 4   + 14 * power(pg_temp.u('los:' || b.bed_key || ':' || k), 1.4)
               WHEN 'SURG'   THEN 2.5 + 9  * power(pg_temp.u('los:' || b.bed_key || ':' || k), 1.4)
               WHEN 'INFECT' THEN 4   + 10 * power(pg_temp.u('los:' || b.bed_key || ':' || k), 1.3)
               ELSE               3   + 9  * power(pg_temp.u('los:' || b.bed_key || ':' || k), 1.5)
           END AS los_days,
           0.2 + 1.2 * pg_temp.u('gap:' || b.bed_key || ':' || k) AS gap_days
    FROM bed_plan b, generate_series(0, 80) k
), timed AS (
    SELECT r.*,
           -- 첫 입원은 시작일 전부터 이미 누워 있던 환자처럼 앞당긴다
           coalesce(sum(r.los_days + r.gap_days) OVER (
               PARTITION BY r.bed_key ORDER BY r.k
               ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0)
             - r.los_days * pg_temp.u('first:' || r.bed_key) * (CASE WHEN r.k = 0 THEN 1 ELSE 0 END)
             AS start_offset
    FROM raw r
)
SELECT t.ward_id, t.ward_code, t.specialty, t.room_no, t.bed_no, t.bed_key, t.k AS stay_k,
       md5('stay:' || t.bed_key || ':' || t.k)::uuid AS subject_ref,
       -- 입원은 오전 9시~오후 5시, 퇴원은 오전 10시~오후 1시
       date_trunc('day', p.sim_start + (t.start_offset || ' days')::interval)
         + ((9 + 8 * pg_temp.u('adm-h:' || t.bed_key || ':' || t.k)) || ' hours')::interval AS admitted_at,
       date_trunc('day', p.sim_start + ((t.start_offset + t.los_days) || ' days')::interval)
         + ((10 + 3 * pg_temp.u('dis-h:' || t.bed_key || ':' || t.k)) || ' hours')::interval AS planned_discharge
FROM timed t, plan_anchor p
WHERE p.sim_start + (t.start_offset || ' days')::interval < p.anchor - interval '2 hours';

-- 기준 시각 이후에 퇴원 예정이면 아직 입원 중이다. 입원 시각보다 이른 퇴원은 없게 맞춘다.
ALTER TABLE stay_plan ADD COLUMN discharged_at timestamptz;
UPDATE stay_plan s
   SET discharged_at = CASE WHEN greatest(s.planned_discharge, s.admitted_at + interval '20 hours') <= p.anchor
                            THEN greatest(s.planned_discharge, s.admitted_at + interval '20 hours') END
  FROM plan_anchor p;

-- 입원 순서대로 번호를 붙인다. 등록번호와 환자가 이 번호에서 나온다.
ALTER TABLE stay_plan ADD COLUMN seq bigint;
UPDATE stay_plan s SET seq = o.rn
  FROM (SELECT subject_ref, row_number() OVER (ORDER BY admitted_at, bed_key) AS rn FROM stay_plan) o
 WHERE o.subject_ref = s.subject_ref;

-- 한 침대에 두 입원이 겹치면 안 된다. 앞선 어느 입원이든 아직 이어지고 있거나 뒤 입원 시각을
-- 넘기면 뒤 입원은 버린다. 바로 앞 입원만 보면, 그 입원이 지워진 뒤의 입원이 다시 겹친다.
DELETE FROM stay_plan s
 WHERE EXISTS (
     SELECT 1 FROM stay_plan prev
      WHERE prev.bed_key = s.bed_key AND prev.stay_k < s.stay_k
        AND (prev.discharged_at IS NULL OR prev.discharged_at > s.admitted_at));

CREATE INDEX ON stay_plan (subject_ref);

-- ── 입원 × 날짜 ──────────────────────────────────────────
-- 업무 요청(업무 DB)과 활력징후·간호기록(원내 DB)이 같은 "몇 번째 입원 며칠째" 를 본다.
CREATE TEMP TABLE stay_day AS
SELECT s.subject_ref, s.seq, s.ward_id, s.ward_code, s.specialty, s.room_no, s.bed_no,
       s.admitted_at, s.discharged_at,
       least(coalesce(s.discharged_at, p.anchor), p.anchor) AS stay_end,
       d AS day_no,
       date_trunc('day', s.admitted_at) + make_interval(days => d) AS day_start
FROM stay_plan s
CROSS JOIN plan_anchor p
CROSS JOIN LATERAL generate_series(
    0,
    floor(extract(epoch FROM (least(coalesce(s.discharged_at, p.anchor), p.anchor)
                              - date_trunc('day', s.admitted_at))) / 86400)::int) d;

CREATE INDEX ON stay_day (seq, day_no);

/**
 * 작은 병원 반년치 데이터(SEED_PROFILE=hospital)를 올린 스택에서 양이 많아야 드러나는 것을 본다.
 *
 * 응급 정렬(문자열 역순이라 응급이 맨 아래)과 자정 결함(안 끝난 요청이 날짜가 바뀌면 큐에서 사라짐)은
 * 둘 다 시연용 20건으로는 보이지 않았다. 반년치를 심고서야 드러났다. 그런 부류를 계속 잡으려고 둔다.
 *
 * 브라우저를 띄우지 않는다. 화면이 아니라 응답의 순서·개수·시간을 본다. 중계 서버(Caddy)를 거쳐 부르므로
 * 원내 경로는 실제로 원내 서버가 답한다 — 두 DB 를 따로 채운 데이터가 같은 사람에 닿는지도 여기서 확인된다.
 *
 *   APP=http://localhost:8081 node check-hospital-data.mjs
 */
const APP = process.env.APP ?? 'http://localhost:5173';
const API = `${APP}/api/v1`;

/** CI 러너는 느리다. 사람이 "멈췄다" 고 느끼는 선에서 넉넉히 잡는다. */
const LIST_BUDGET_MS = 2000;
const STATS_BUDGET_MS = 5000;

const checks = [];
const record = (ok, label, detail = '') => {
  checks.push({ ok, label });
  console.log(`  ${ok ? 'OK  ' : '실패'} ${label}${detail ? ` — ${detail}` : ''}`);
};

const tokens = {};

async function login(loginId) {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ loginId, password: 'nurse1234!' }),
  });
  if (!res.ok) throw new Error(`${loginId} 로그인 실패: ${res.status}`);
  tokens[loginId] = (await res.json()).accessToken;
}

/** 응답과 걸린 시간을 함께 준다 */
async function call(who, method, path, body) {
  const started = performance.now();
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { Authorization: `Bearer ${tokens[who]}`, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const ms = Math.round(performance.now() - started);
  if (!res.ok) throw new Error(`${who} ${method} ${path} → ${res.status} ${text.slice(0, 200)}`);
  return { data: text ? JSON.parse(text) : null, ms };
}

const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date());
const daysAgo = (n) =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date(Date.now() - n * 86400000));

const RANK = { EMERGENCY: 0, URGENT: 1, ROUTINE: 2 };

try {
  for (const id of ['ward01', 'head01', 'mri01', 'lab01', 'pharm01', 'admin01']) await login(id);

  // ── 큐: 순서·시간·차례 ───────────────────────────────────
  for (const who of ['lab01', 'pharm01', 'mri01']) {
    const { data, ms } = await call(who, 'GET', '/work-orders?direction=INBOUND&size=200');
    const ranks = data.content.map((r) => RANK[r.priority]);
    const sorted = ranks.every((r, i) => i === 0 || ranks[i - 1] <= r);
    record(sorted, `${who} 큐가 응급·긴급·일반 순이다`, `${data.totalElements}건`);
    record(ms <= LIST_BUDGET_MS, `${who} 큐 응답 시간`, `${ms}ms`);
    record(data.content.every((r) => typeof r.myTurn === 'boolean'), `${who} 큐의 행마다 누구 차례인지 있다`);

    // 기간을 주지 않은 조회는 "지금 할 일" 이다. 반년을 열어 센 안 끝난 요청과 같아야 한다.
    // 적으면 날짜가 바뀐 요청이 빠진 것이다(자정 결함).
    const wide = await call(who, 'GET', `/work-orders?direction=INBOUND&size=1&from=${daysAgo(200)}&to=${today}`);
    record(
      wide.data.totalElements === data.totalElements,
      `${who} 큐가 날짜 지난 안 끝난 요청까지 싣는다`,
      `기본 ${data.totalElements}건 / 반년 ${wide.data.totalElements}건`,
    );
  }

  // ── 병동: 두 DB 가 같은 사람에 닿는가 ────────────────────
  const board = await call('ward01', 'GET', '/care-episodes');
  record(board.data.length >= 20 && board.data.length <= 36, '3병동 재원 수가 병상 안에 있다', `${board.data.length}명`);

  const refs = board.data.map((e) => e.subjectRef);
  const briefs = await call('ward01', 'POST', '/phi/subjects/brief', refs);
  // 업무 DB 의 침대와 원내 DB 의 환자는 따로 채워졌다. 같은 계산에서 나오지 않았다면 이름이 빈다.
  record(briefs.data.length === refs.length, '업무 DB 의 재원이 원내 DB 에서 전부 사람으로 풀린다',
    `${briefs.data.length}/${refs.length}`);

  const vitals = await call('ward01', 'GET', `/phi/subjects/${refs[0]}/vital-signs?size=5`);
  record(vitals.data.content.length > 0, '재원 환자에게 활력징후가 있다');

  const mine = await call('ward01', 'GET', '/work-orders?direction=OUTBOUND&size=200');
  record(mine.ms <= LIST_BUDGET_MS, '병동 현황 응답 시간', `${mine.ms}ms`);
  // 어느 쪽이 몇 건일지는 초기화 시각에 달려 있어 개수를 걸지 않는다. 판정이 실려 있는지만 본다.
  record(mine.data.content.every((r) => typeof r.myTurn === 'boolean'),
    '병동 현황의 행마다 누구 차례인지 있다',
    `우리 차례 ${mine.data.content.filter((r) => r.myTurn).length} / 기다림 ${mine.data.content.filter((r) => !r.myTurn).length}`);

  // ── 지난 요청·통계 ───────────────────────────────────────
  const history = await call('lab01', 'GET',
    `/work-orders?direction=INBOUND&status=COMPLETED&from=${daysAgo(30)}&to=${today}&size=50`);
  record(history.data.totalElements > 1000, '지난 한 달 완료 요청이 검색된다', `${history.data.totalElements}건`);
  record(history.ms <= LIST_BUDGET_MS, '지난 요청 검색 응답 시간', `${history.ms}ms`);

  const stats = await call('admin01', 'GET', `/stats/waiting-time?from=${daysAgo(90)}&to=${today}`);
  record(stats.data.overall.totalRequests > 10000, '석 달 통계가 집계된다', `${stats.data.overall.totalRequests}건`);
  record(stats.ms <= STATS_BUDGET_MS, '석 달 통계 응답 시간', `${stats.ms}ms`);

  // ── 알림함: 남의 환자 알림이 쌓이지 않는가 ────────────────
  // 파트 전원에게 보내던 때는 병동 간호사 한 명에게 하루 수백~수천 건이 쌓였다.
  // 요청에 손댄 사람에게만 보내면 병동 간호사는 자기 요청 몇 건이다.
  for (const who of ['ward01', 'head01']) {
    const noti = await call(who, 'GET', '/notifications?size=1');
    record(noti.data.page.totalElements <= 150, `${who} 알림함이 넘치지 않는다`, `${noti.data.page.totalElements}건`);
  }
} catch (e) {
  record(false, '검사 도중 오류', e.message);
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);

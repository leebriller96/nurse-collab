import { chromium } from 'playwright';

/**
 * CSP 를 켠 뒤 앱이 실제로 멀쩡히 도는지 확인한다.
 *
 * 깨지는 CSP 는 없느니만 못하다. 헤더만 넣고 끝내면
 * 실시간 연결이나 그래프가 조용히 죽어도 화면은 그럴듯하게 보인다.
 * 그래서 로그인부터 실시간 연결까지 지나가며 위반 로그를 본다.
 *
 *   docker compose -f docker-compose.prod.yml up -d
 *   cd e2e && APP=http://localhost:8081 node check-csp.mjs
 */
const APP = process.env.APP ?? 'http://localhost:8081';

const violations = [];
/** 앱이 스스로 낸 위반만 모아 둔다. 아래쪽 탐침이 만든 것과 섞이면 안 된다. */
const appViolations = [];
const checks = [];
const record = (ok, label, detail = '') => {
  checks.push({ ok, label });
  console.log(`  ${ok ? 'OK  ' : '실패'} ${label}${detail ? ` — ${detail}` : ''}`);
};

const browser = await chromium.launch();
// 시간대를 맞춘다. 화면이 "오늘" 을 브라우저 기준으로 계산하는 곳이 있어
// 이것이 없으면 UTC 로 도는 CI 에서 새벽 시간대에만 결과가 달라진다.
const context = await browser.newContext({
  serviceWorkers: 'allow',
  locale: 'ko-KR',
  timezoneId: 'Asia/Seoul',
});
const page = await context.newPage();

page.on('console', (m) => {
  const t = m.text();
  if (/Content Security Policy|Refused to/i.test(t)) violations.push(t);
});

try {
  // ── 헤더가 실제로 오는지
  const res = await page.goto(`${APP}/login`, { waitUntil: 'networkidle' });
  const headers = res.headers();
  record(!!headers['content-security-policy'], 'CSP 헤더 있음');
  record(headers['x-content-type-options'] === 'nosniff', 'nosniff');
  record(!!headers['referrer-policy'], 'Referrer-Policy');
  record(!headers['server'], '서버 종류 감춤', headers['server'] ?? '');

  // ── 실제로 쓰는 기능이 CSP 에 막히지 않는지
  await page.getByRole('button', { name: /mri01/ }).click();
  await page.waitForURL(/\/service\/queue/, { timeout: 15000 });
  await page.waitForLoadState('networkidle');
  record(true, '로그인과 목록 조회');

  // 실시간 연결. connect-src 가 ws 를 막으면 여기서 회색으로 남는다.
  await page.waitForFunction(
    () => document.querySelector('[title="실시간으로 받는 중"]') !== null,
    null,
    { timeout: 15000 },
  ).then(() => record(true, '실시간 연결(WebSocket)'))
    .catch(() => record(false, '실시간 연결(WebSocket)', 'connect-src 가 막았을 수 있다'));

  // 서비스 워커. worker-src 가 막으면 등록이 실패한다.
  const swState = await page.evaluate(async () => {
    const reg = await navigator.serviceWorker.getRegistration();
    return reg?.active?.state ?? null;
  });
  record(swState === 'activated', '서비스 워커', swState ?? '등록 안 됨');

  record(violations.length === 0, 'CSP 위반 없음', violations.slice(0, 3).join(' | '));
  // 아래 탐침은 일부러 위반을 만든다. 그것까지 "앱이 낸 위반" 으로 세면 안 된다.
  appViolations.push(...violations);

  // ── 인라인 style 이 CSP 에 막히지 않는지 ─────────────────
  //
  // 일정 보드의 블록 수로 확인하려 했는데, 그 화면은 "오늘 잡힌 것" 만 보여줘서
  // 시연 데이터가 어느 시각에 심겼는지에 따라 0건이 된다. 검사가 시각에 따라
  // 흔들리면 진짜 고장과 구별할 수 없다. 데이터와 무관하게 직접 본다.
  //
  // 일부러 위반을 만드는 검사라 위반 집계(위)를 끝낸 뒤에 한다.
  const probe = await page.evaluate(() => {
    const el = document.createElement('div');
    document.body.appendChild(el);

    // React 는 style 속성이 아니라 CSSOM 으로 값을 넣는다. 이쪽이 실제로 쓰는 길이다.
    el.style.height = '42px';
    const viaCssom = getComputedStyle(el).height;

    // style 속성은 CSP 가 막는 쪽이다. 막혀야 정책이 실제로 걸려 있다는 뜻이다.
    el.setAttribute('style', 'height: 24px');
    const viaAttribute = getComputedStyle(el).height;

    el.remove();
    return { viaCssom, viaAttribute };
  });

  record(probe.viaCssom === '42px', '인라인 style 이 먹는다 (React 가 쓰는 길)', probe.viaCssom);
  record(
    probe.viaAttribute !== '24px',
    'style 속성은 막힌다 (정책이 선언만 된 것이 아니다)',
    probe.viaAttribute,
  );
} finally {
  await browser.close();
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
if (appViolations.length) {
  console.log('\n위반 내역:');
  for (const v of appViolations.slice(0, 10)) console.log(`  ${v}`);
}
process.exit(failed.length ? 1 : 0);

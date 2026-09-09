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
const checks = [];
const record = (ok, label, detail = '') => {
  checks.push({ ok, label });
  console.log(`  ${ok ? 'OK  ' : '실패'} ${label}${detail ? ` — ${detail}` : ''}`);
};

const browser = await chromium.launch();
const context = await browser.newContext({ serviceWorkers: 'allow', locale: 'ko-KR' });
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
  await page.waitForURL(/\/exam\/queue/, { timeout: 15000 });
  await page.waitForLoadState('networkidle');
  record(true, '로그인과 목록 조회');

  // 실시간 연결. connect-src 가 ws 를 막으면 여기서 회색으로 남는다.
  await page.waitForFunction(
    () => document.querySelector('[title="실시간으로 받는 중"]') !== null,
    null,
    { timeout: 15000 },
  ).then(() => record(true, '실시간 연결(WebSocket)'))
    .catch(() => record(false, '실시간 연결(WebSocket)', 'connect-src 가 막았을 수 있다'));

  // 인라인 style 로 위치를 잡는 화면.
  // style-src 가 막으면 블록이 전부 같은 자리에 겹치거나 높이가 0 이 된다.
  // 헤더만 넣고 넘어가면 이런 것이 조용히 깨진다.
  await page.getByRole('link', { name: '일정' }).click();
  await page.waitForURL(/\/exam\/schedule/);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(800);

  const laidOut = await page.evaluate(() =>
    [...document.querySelectorAll('button[style*="top"]')]
      .map((el) => el.getBoundingClientRect().height)
      .filter((h) => h > 10).length);
  record(laidOut > 0, '인라인 style 로 배치되는 블록', `${laidOut}개`);

  // 서비스 워커. worker-src 가 막으면 등록이 실패한다.
  const swState = await page.evaluate(async () => {
    const reg = await navigator.serviceWorker.getRegistration();
    return reg?.active?.state ?? null;
  });
  record(swState === 'activated', '서비스 워커', swState ?? '등록 안 됨');

  record(violations.length === 0, 'CSP 위반 없음', violations.slice(0, 3).join(' | '));
} finally {
  await browser.close();
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
if (violations.length) {
  console.log('\n위반 내역:');
  for (const v of violations.slice(0, 10)) console.log(`  ${v}`);
}
process.exit(failed.length ? 1 : 0);

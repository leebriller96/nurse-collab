import { chromium } from 'playwright';

/**
 * 조회 조건이 화면을 떠났다 돌아와도 살아 있는지 확인한다.
 *
 * 이건 눈으로 보기 어렵다. 조건을 맞추고 한 건을 열어본 뒤 뒤로 가야 드러나는데,
 * 개발 중에는 그렇게 쓸 일이 없어서 초기화되는 것을 모르고 지나간다.
 * 스무 건을 훑어보는 사람만 매번 다시 맞추게 된다.
 *
 *   APP=http://localhost:5173 node check-filters.mjs
 */
const APP = process.env.APP ?? 'http://localhost:5173';

const checks = [];
const record = (ok, label, detail = '') => {
  checks.push({ ok, label });
  console.log(`  ${ok ? 'OK  ' : '실패'} ${label}${detail ? ` — ${detail}` : ''}`);
};

const browser = await chromium.launch();
const context = await browser.newContext({ locale: 'ko-KR', timezoneId: 'Asia/Seoul' });
const page = await context.newPage();

async function login(loginId) {
  await page.goto(`${APP}/login`);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.getByRole('button', { name: new RegExp(loginId) }).click();
  await page.waitForURL(/\/(ward|service|admin)\//, { timeout: 15000 });
}

try {
  // ── 지난 요청: 조건을 맞추고 한 건을 열어본 뒤 돌아온다
  await login('mri01');
  await page.goto(`${APP}/service/history`);
  await page.waitForLoadState('networkidle');

  const dates = page.locator('input[type="date"]');
  await dates.first().fill('2026-09-01');
  await page.getByPlaceholder('환자명 또는 요청번호').fill('김');
  await page.getByRole('button', { name: '찾기' }).click();
  await page.waitForLoadState('networkidle');

  record(page.url().includes('from=2026-09-01'), '조건이 주소에 담긴다', new URL(page.url()).search);

  const rows = page.locator('tbody tr');
  if ((await rows.count()) === 0) {
    record(false, '되돌아왔을 때 조건이 살아 있다', '검색 결과가 없어 확인 불가');
  } else {
    await rows.first().click();
    await page.waitForURL(/\/service\/requests\/\d+/, { timeout: 10000 });
    await page.goBack();
    await page.waitForLoadState('networkidle');

    const keptFrom = await dates.first().inputValue();
    const keptKeyword = await page.getByPlaceholder('환자명 또는 요청번호').inputValue();
    record(
      keptFrom === '2026-09-01' && keptKeyword === '김',
      '되돌아왔을 때 조건이 살아 있다',
      `기간 ${keptFrom} / 검색어 "${keptKeyword}"`,
    );
  }

  // ── 조건을 여러 번 고쳐도 뒤로가기 한 번에 목록을 빠져나간다
  await page.goto(`${APP}/service/queue`);
  await page.waitForLoadState('networkidle');
  await page.getByRole('link', { name: '지난 요청' }).click();
  await page.waitForURL(/\/service\/history/);
  await page.waitForLoadState('networkidle');

  await dates.first().fill('2026-09-02');
  await page.waitForTimeout(300);
  await dates.first().fill('2026-09-03');
  await page.waitForTimeout(300);
  await dates.nth(1).fill('2026-09-09');
  await page.waitForTimeout(300);

  await page.goBack();
  await page.waitForLoadState('networkidle');
  record(
    /\/service\/queue/.test(page.url()),
    '조건을 세 번 고쳐도 뒤로가기 한 번이면 나간다',
    new URL(page.url()).pathname,
  );

  // ── 주소를 그대로 열면 같은 조건으로 뜬다 (링크로 넘길 수 있다)
  await page.goto(`${APP}/service/history?from=2026-09-05&to=2026-09-09&q=%EA%B9%80`);
  await page.waitForLoadState('networkidle');
  const shared = await dates.first().inputValue();
  const sharedKeyword = await page.getByPlaceholder('환자명 또는 요청번호').inputValue();
  record(
    shared === '2026-09-05' && sharedKeyword === '김',
    '주소를 열면 같은 조건으로 뜬다',
    `기간 ${shared} / 검색어 "${sharedKeyword}"`,
  );

  // ── 접근 기록도 같은지
  await login('admin01');
  await page.goto(`${APP}/admin/audit-logs?from=2026-09-04&to=2026-09-09`);
  await page.waitForLoadState('networkidle');
  const auditFrom = await page.locator('input[type="date"]').first().inputValue();
  record(auditFrom === '2026-09-04', '접근 기록도 주소를 따른다', auditFrom);
} finally {
  await browser.close();
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);

import { chromium } from 'playwright';

/**
 * 환자 정보가 업무 흐름과 실제로 갈려 있는지 확인한다.
 *
 * 이 단계에서 지켜야 할 것은 두 가지다.
 *
 *   1. 업무 응답 본문에 이름도 진단명도 들어 있지 않다.
 *   2. 원내에 닿지 못하면 업무는 그대로 돌고 <b>사람만 사라진다.</b>
 *      그때 빈칸으로 두지 않고 "원내망에서만 조회됩니다" 라고 말한다.
 *
 * 2번을 눈으로 확인하기는 어렵다. 원내가 살아 있으면 화면이 멀쩡해 보이고,
 * 끊어 봐야 비로소 무엇이 어디서 오는지 드러난다. 그래서 일부러 끊어 본다.
 *
 *   APP=http://localhost:5173 node check-phi-split.mjs
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

/** 업무 API 가 돌려준 본문을 그대로 들여다본다 */
const workOrderBodies = [];
page.on('response', async (res) => {
  const url = res.url();
  if (!url.includes('/api/v1/work-orders')) return;
  if (url.includes('/api/v1/phi/')) return;
  try {
    workOrderBodies.push(await res.text());
  } catch {
    /* 본문을 못 읽는 응답은 건너뛴다 */
  }
});

async function login(loginId) {
  await page.goto(`${APP}/login`);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.getByRole('button', { name: new RegExp(loginId) }).click();
  await page.waitForURL(/\/(ward|service|admin)\//, { timeout: 15000 });
  await page.waitForLoadState('networkidle');
}

try {
  // ── 원내가 살아 있을 때 ─────────────────────────────────
  await login('mri01');
  await page.waitForTimeout(800);

  const namedRow = await page.getByText(/[김이박정최]OO/).count();
  record(namedRow > 0, '평소에는 큐에 환자 이름이 보인다');

  // 업무 응답을 직접 뒤진다. 화면에 이름이 보이더라도 그것은 원내에서 온 것이어야 한다.
  const leaked = workOrderBodies.filter((b) => /[김이박정최]OO/.test(b));
  record(leaked.length === 0, '업무 응답 본문에 이름이 없다',
    leaked.length ? `${leaked.length}건에서 이름이 발견됨` : `본문 ${workOrderBodies.length}건 확인`);

  const diagnosisLeak = workOrderBodies.filter((b) => /뇌경색|폐렴|담낭염|추간판/.test(b));
  record(diagnosisLeak.length === 0, '업무 응답 본문에 진단명이 없다');

  // 상세로 들어가 경고까지 보이는지
  await page.locator('tbody tr').first().click();
  await page.waitForURL(/\/service\/requests\/\d+/, { timeout: 15000 });
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(500);
  const beforeCut = await page.getByText(/[김이박정최]OO/).count();
  record(beforeCut > 0, '상세에도 이름이 채워진다');

  const orderUrl = page.url();
  // 끊기 전 버튼 수를 세어 둔다. 어떤 요청이 열렸는지에 따라 0개일 수도 있어서
  // "버튼이 있다" 가 아니라 "끊기 전과 같다" 로 본다.
  const actionsBeforeCut = await page.locator('button').count();

  // ── 원내망을 끊는다 ─────────────────────────────────────
  await page.route('**/api/v1/phi/**', (route) => route.abort('failed'));
  await page.reload();
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1500);

  record(
    (await page.getByText(/원내망에서만 조회됩니다/).count()) > 0,
    '끊기면 빈칸이 아니라 "원내망에서만 조회됩니다" 가 뜬다',
  );

  record(
    (await page.getByText(/[김이박정최]OO/).count()) === 0,
    '끊기면 이름이 실제로 사라진다',
  );

  // 여기가 핵심이다. 경고를 "없음" 으로 보여주면 금기 환자를 그냥 보내게 된다.
  record(
    (await page.getByText(/주의사항을 확인하지 못했습니다/).count()) > 0,
    '경고를 못 받았다는 것을 감추지 않는다',
  );

  // ── 업무는 그대로 돌아야 한다 ───────────────────────────
  const requestNoShown = await page.getByText(/^(TR|SP|PH|EQ)\d{8}-\d{4}$/).count();
  record(requestNoShown > 0, '끊겨도 요청번호와 상태는 그대로 보인다');

  const actionsAfterCut = await page.locator('button').count();
  record(
    actionsAfterCut === actionsBeforeCut,
    '끊겨도 누를 수 있는 것이 줄지 않는다',
    `끊기 전 ${actionsBeforeCut}개 → 끊은 뒤 ${actionsAfterCut}개`,
  );

  // 큐도 마찬가지다. 한 사람을 못 받아왔다고 목록이 통째로 죽으면 안 된다.
  await page.goto(`${APP}/service/queue`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1000);
  record(
    (await page.locator('tbody tr').count()) > 0,
    '끊겨도 큐 목록은 그대로 뜬다',
  );

  // ── 병동 보드도 같은 방식으로 줄어들어야 한다 ───────────
  //
  // 이 화면만 예전에는 이름이 그대로 보였다. 재원 목록을 업무 쪽에서 받았기 때문이다.
  // 다른 화면은 사라지는데 이 화면만 아니면, 어디까지가 원내인지 아무도 모르게 된다.
  await login('ward01');
  await page.route('**/api/v1/phi/**', (route) => route.abort('failed'));
  await page.goto(`${APP}/ward/board`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1500);

  record(
    (await page.getByText(/[김이박정최]OO/).count()) === 0,
    '병동 보드에서도 이름이 사라진다',
  );
  record(
    (await page.getByText(/원내망에서만 조회됩니다/).count()) > 0,
    '병동 보드도 빈칸이 아니라 이유를 말한다',
  );
  // 침대와 요청 건수는 업무 쪽 사실이라 남아야 한다
  record(
    (await page.getByText(/^\d{3}-\d$/).count()) > 0,
    '끊겨도 어느 침대가 찼는지는 보인다',
  );

  await page.unroute('**/api/v1/phi/**');
  await login('mri01');
  await page.goto(orderUrl);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(800);
  record(
    (await page.getByText(/[김이박정최]OO/).count()) > 0,
    '원내망이 돌아오면 이름도 돌아온다',
  );

  // ── 활력징후·간호기록은 원내 경로로만 나간다 ─────────────
  //
  // 두 서버로 가르면 중계 서버는 경로 앞머리(/api/v1/phi)만 보고 보낼 곳을 정한다.
  // 이 기록 요청이 한 건이라도 그 밖으로 나가면 그 요청은 클라우드로 가서 조용히 사라진다.
  await login('ward01');
  await page.goto(`${APP}/ward/board`);
  const subjectLink = page.locator('a[href*="/ward/subjects/"]').first();
  await subjectLink.waitFor({ timeout: 15000 });
  const subjectHref = new URL(await subjectLink.getAttribute('href'), APP).pathname;

  const nursingCalls = [];
  const onNursingResponse = (res) => {
    const url = res.url();
    if (/vital-signs|nursing-notes/.test(url)) nursingCalls.push({ url, status: res.status() });
  };
  page.on('response', onNursingResponse);

  await page.goto(`${APP}${subjectHref}/vitals`);
  await page.locator('#pulse').waitFor({ timeout: 15000 });
  await page.locator('#pulse').fill('87');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await page.getByText('활력징후를 기록했습니다').first().waitFor({ timeout: 15000 }).catch(() => {});
  record(
    (await page.getByText('활력징후를 기록했습니다').count()) > 0,
    '활력징후를 원내 경로로 남긴다',
  );

  const NOTE_MARK = `경로 확인 ${Date.now()}`;
  await page.goto(`${APP}${subjectHref}/notes`);
  await page.locator('#situation').waitFor({ timeout: 15000 });
  await page.locator('#situation').fill(NOTE_MARK);
  await page.getByRole('button', { name: '기록 남기기' }).click();
  await page.getByText(NOTE_MARK).first().waitFor({ timeout: 15000 }).catch(() => {});
  record(
    (await page.getByText(NOTE_MARK).count()) > 0,
    '간호기록을 원내 경로로 남기고 다시 읽는다',
  );
  page.off('response', onNursingResponse);

  const outsidePhi = nursingCalls.filter((c) => !new URL(c.url).pathname.startsWith('/api/v1/phi/'));
  const failedCalls = nursingCalls.filter((c) => c.status >= 400);
  record(
    nursingCalls.length >= 4 && outsidePhi.length === 0 && failedCalls.length === 0,
    '기록 요청은 전부 /api/v1/phi 로만 나가고 성공한다',
    outsidePhi[0]?.url ?? failedCalls[0]?.url ?? `${nursingCalls.length}건`,
  );

  // ── 열람 기록도 원내에 있다 ────────────────────────────
  //
  // 끊겼을 때 빈 표를 보여주면 "아무도 열지 않았다" 로 읽힌다.
  // 감사 화면에서 그렇게 읽히는 것이 제일 나쁘다.
  await login('admin01');
  await page.route('**/api/v1/phi/**', (route) => route.abort('failed'));
  await page.goto(`${APP}/admin/audit-logs`);
  await page.getByText(/원내망에서만 조회됩니다|해당 기간에 기록이 없습니다/)
    .first().waitFor({ timeout: 15000 }).catch(() => {});
  record(
    (await page.getByText(/원내망에서만 조회됩니다/).count()) > 0,
    '열람 기록 화면도 끊기면 빈 표가 아니라 이유를 말한다',
  );

  await page.unroute('**/api/v1/phi/**');
  await page.reload();
  await page.getByText('환자 열람').first().waitFor({ timeout: 15000 }).catch(() => {});
  // 방금 mri01 이 이 환자를 열었다. 그 줄이 원내 기록에서 읽혀야 한다.
  record(
    (await page.locator('tr', { hasText: 'mri01' }).filter({ hasText: '환자 열람' }).count()) > 0,
    '원내망이 돌아오면 누가 열었는지 보인다',
  );
} finally {
  await browser.close();
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);

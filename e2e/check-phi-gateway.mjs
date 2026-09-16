import { chromium } from 'playwright';

/**
 * 원내망 밖에서 열었을 때의 모습 — 중계 서버가 원내 경로를 **실제로** 끊은 스택에서.
 *
 * check-phi-split 은 브라우저 안에서 원내 요청을 끊어 흉내 낸다.
 * 이 검사는 허용 대역 밖에서 띄운 스택을 연다. 둘이 같은 화면이어야 한다 —
 * 흉내와 실제가 다르면 흉내로 한 확인은 의미가 없다.
 *
 * 중계 서버가 403 을 주면 화면은 "원내망에서만 조회됩니다" 대신 오류를 띄운다.
 * 연결을 끊는 이유가 그것이고, 이 검사가 그 차이를 잡는다.
 *
 *   PHI_ALLOWED_NETS=203.0.113.0/24 docker compose -f docker-compose.split.yml up -d --no-deps web
 *   APP=http://localhost:8081 node check-phi-gateway.mjs
 */
const APP = process.env.APP ?? 'http://localhost:8081';

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
  // ── 병동 보드: 침대와 요청은 보이고 사람만 사라진다 ─────
  await login('ward01');
  await page.goto(`${APP}/ward/board`);
  await page.getByText(/원내망에서만 조회됩니다/).first().waitFor({ timeout: 20000 }).catch(() => {});

  record(
    (await page.getByText(/원내망에서만 조회됩니다/).count()) > 0,
    '원내망 밖의 병동 보드는 이유를 말한다',
  );
  record(
    (await page.getByText(/[김이박정최]OO/).count()) === 0,
    '원내망 밖에서는 이름이 한 명도 보이지 않는다',
  );
  record(
    (await page.getByText(/^\d{3}-\d$/).count()) > 0,
    '원내망 밖에서도 어느 침대가 찼는지는 보인다',
  );

  // ── 검사실 상세: 경고를 못 받았다는 것을 감추지 않는다 ─
  await login('mri01');
  await page.locator('tbody tr').first().waitFor({ timeout: 15000 });
  record((await page.locator('tbody tr').count()) > 0, '원내망 밖에서도 검사실 큐는 뜬다');

  await page.locator('tbody tr').first().click();
  await page.waitForURL(/\/service\/requests\/\d+/, { timeout: 15000 });
  await page.getByText(/주의사항을 확인하지 못했습니다/).first()
    .waitFor({ timeout: 20000 }).catch(() => {});
  record(
    (await page.getByText(/주의사항을 확인하지 못했습니다/).count()) > 0,
    '원내망 밖의 요청 상세는 경고를 못 받았다고 말한다',
  );
} finally {
  await browser.close();
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);

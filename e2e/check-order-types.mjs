import { chromium } from 'playwright';

/**
 * 이송 말고 다른 종류의 업무가 화면에서 실제로 도는지 확인한다.
 *
 * 규칙표는 단위 테스트가, 상태 코드는 API 테스트가 본다.
 * 여기서 보는 것은 그 규칙이 화면까지 닿는가다.
 *
 * 특히 <b>환자가 없는 업무</b>가 중요하다. 이송만 있을 때는 모든 목록이
 * 환자 이름을 그냥 꺼내 썼다. 장비 수리 한 건이 그 자리를 전부 밟는다.
 * 여기서 빠뜨린 화면은 첫 장비 요청이 들어온 날 백지가 된다.
 *
 *   APP=http://localhost:5173 node check-order-types.mjs
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
  await page.waitForLoadState('networkidle');
}

try {
  // ── 병동에서 장비 수리를 건다 (환자를 고르지 않는다) ────
  await login('ward01');
  await page.goto(`${APP}/ward/requests/new`);
  await page.waitForLoadState('networkidle');

  await page.getByRole('button', { name: '의공', exact: true }).click();
  await page.waitForTimeout(300);
  record(
    (await page.getByRole('button', { name: /수액펌프 수리/ }).count()) > 0,
    '종류 탭으로 의공 업무를 고를 수 있다',
  );

  await page.getByRole('button', { name: /수액펌프 수리/ }).click();
  await page.waitForTimeout(300);

  // 환자를 고르지 않았는데도 등록이 열려 있어야 한다
  const submit = page.getByRole('button', { name: '요청 등록' });
  record(await submit.isEnabled(), '환자를 고르지 않아도 등록할 수 있다');

  await submit.click();
  await page.waitForURL(/\/ward\/requests\/\d+/, { timeout: 15000 });
  const orderId = page.url().split('/').pop();

  // 화면 안에서 이동한 뒤에는 networkidle 이 상세 조회를 기다려 주지 않는다.
  // 값이 오기 전에 세면 "번호가 없다" 는 결과가 나오고, 진짜 고장과 구별할 수 없다.
  const requestNo = page.getByText(/^EQ\d{8}-\d{4}$/);
  const shown = await requestNo
    .first()
    .waitFor({ timeout: 10000 })
    .then(() => true)
    .catch(() => false);

  record(shown, '요청번호만 보고 종류를 알 수 있다 (EQ)');

  // ── 환자가 필요한 업무는 환자 없이 막힌다 ───────────────
  await page.goto(`${APP}/ward/requests/new`);
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: '검체', exact: true }).click();
  await page.getByRole('button', { name: /일반혈액검사/ }).click();
  await page.waitForTimeout(300);
  record(
    (await page.getByText(/대상 환자가 필요합니다/).count()) > 0,
    '환자가 필요한 업무는 환자 없이 막고 이유를 말한다',
  );

  // ── 의공학팀 화면 ────────────────────────────────────────
  await login('bme01');
  record(/\/service\/queue/.test(page.url()), '병동이 아닌 파트는 수행 화면으로 간다', page.url());

  await page.waitForTimeout(500);
  record(
    (await page.getByText('대상 환자 없음').count()) > 0,
    '환자 없는 요청이 큐에서 자리를 지킨다',
  );

  await page.goto(`${APP}/service/requests/${orderId}`);
  await page.waitForLoadState('networkidle');

  // 이송이면 예정시각을 받는 자리다. 의공은 받지 않는다.
  await page.getByRole('button', { name: '접수', exact: true }).click();
  await page.waitForTimeout(800);
  const repairButton = page.getByRole('button', { name: '수리 시작', exact: true });
  record(
    (await repairButton.count()) > 0,
    '같은 IN_PROGRESS 인데 버튼이 "검사 시작" 이 아니라 "수리 시작" 이다',
  );

  await repairButton.click();
  await page.waitForTimeout(800);
  record(
    (await page.getByText('수리중').count()) > 0,
    '상태 이름도 종류를 따라간다 (수리중)',
  );

  // 부품대기는 사유가 필수다. 그 판단을 화면이 스스로 하지 않는다.
  await page.getByRole('button', { name: '부품 대기', exact: true }).click();
  await page.waitForTimeout(500);
  record(
    (await page.getByPlaceholder('사유 (필수)').count()) > 0,
    '서버가 필수라고 한 입력칸이 눌리기 전에 뜬다',
  );

  // 의공은 수행 파트가 스스로 끝낸다. 이송과 다른 지점이다.
  await page.getByRole('button', { name: '그만두기' }).click();
  await page.waitForTimeout(300);
  record(
    (await page.getByRole('button', { name: '수리 완료', exact: true }).count()) > 0,
    '수행 파트가 스스로 완료할 수 있다',
  );
} finally {
  await browser.close();
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);

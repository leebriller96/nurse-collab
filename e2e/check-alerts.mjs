import { chromium } from 'playwright';

/**
 * 병동이 남긴 주의사항이 검사실 화면의 경고로 이어지는지 확인한다.
 *
 * 이 연결이 이 프로젝트에서 가장 특징적인 부분이다.
 * 그동안 주의사항이 시드로만 들어가 있어서, 만드는 쪽이 없으니 연결도 반쪽이었다.
 *
 *   APP=http://localhost:5173 node check-alerts.mjs
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
  await page.waitForURL(/\/(ward|exam|admin)\//, { timeout: 15000 });
  await page.waitForLoadState('networkidle');
}

try {
  // ── 병동에서 주의사항을 남긴다
  await login('ward01');
  // 주의사항이 없는 환자를 고른다. 폐소공포를 새로 남겨야 하기 때문이다.
  await page.getByRole('link', { name: /최OO/ }).first().click();
  await page.waitForURL(/\/ward\/encounters\/\d+$/);
  await page.waitForLoadState('networkidle');
  const encounterUrl = page.url();

  await page.getByRole('button', { name: '+ 추가' }).click();
  await page.getByRole('button', { name: '폐소공포' }).click();
  await page.getByRole('button', { name: /주의/ }).first().click();
  await page.getByPlaceholder(/자세히/).fill('이전 MRI 중단 경험 있음');
  await page.getByRole('button', { name: '남기기' }).click();

  await page.getByText('주의사항을 남겼습니다').waitFor({ timeout: 10000 });
  record(true, '병동에서 주의사항을 남긴다');

  await page.waitForTimeout(800);
  record(
    (await page.getByText('이전 MRI 중단 경험 있음').count()) > 0,
    '남긴 것이 환자 화면에 보인다',
  );

  // ── 뇌 MRI 를 요청하면 그 자리에서 안내가 뜬다
  await page.getByRole('link', { name: /이송 요청/ }).click();
  await page.waitForURL(/\/ward\/requests\/new/);
  await page.getByRole('button', { name: /뇌 MRI/ }).click();
  await page.waitForTimeout(500);
  record(
    (await page.getByText(/확인이 필요합니다/).count()) > 0,
    '검사를 고르면 그 자리에서 안내가 뜬다',
  );

  await page.getByRole('button', { name: '요청 등록' }).click();
  await page.waitForURL(/\/ward\/requests\/\d+/, { timeout: 15000 });
  const requestId = page.url().split('/').pop();

  // ── 검사실 화면에도 경고로 뜬다
  await login('mri01');
  await page.goto(`${APP}/exam/requests/${requestId}`);
  await page.waitForLoadState('networkidle');
  // 종류마다 문구가 다르다. 폐소공포는 "검사 전 진정 여부를 확인하세요" 다.
  record(
    (await page.getByText(/폐소공포 이력이 있습니다/).count()) > 0,
    '검사실 화면에 경고로 뜬다',
  );

  // ── 내리면 목록에서 빠진다 (지워지지는 않는다)
  await login('ward01');
  await page.goto(encounterUrl);
  await page.waitForLoadState('networkidle');
  await page.getByRole('button', { name: '내리기' }).first().click();
  await page.getByText('주의사항을 내렸습니다').waitFor({ timeout: 10000 });
  await page.waitForTimeout(800);
  record(
    (await page.getByText('이전 MRI 중단 경험 있음').count()) === 0,
    '내리면 목록에서 빠진다',
  );
} finally {
  await browser.close();
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);

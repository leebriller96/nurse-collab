import { chromium } from 'playwright';

/**
 * 스켈레톤이 제 일을 하는지 확인한다.
 *
 * 스켈레톤의 목적은 "예쁘게 기다리기" 가 아니라 **자리를 잡아 두는 것**이다.
 * 모양이 실제와 어긋나면 값이 들어오는 순간 화면이 밀리고,
 * 그러면 글자 한 줄("불러오는 중…")보다 나을 것이 없다.
 *
 * 그래서 눈으로 보는 대신 로딩 중과 로딩 후의 세로 위치를 재서 비교한다.
 *
 *   APP=http://localhost:5173 node check-skeleton.mjs
 */
const APP = process.env.APP ?? 'http://localhost:5173';

/** 이만큼까지는 밀려도 괜찮다고 본다. 목록 길이는 데이터에 따라 달라지므로. */
const TOLERANCE_PX = 24;

const checks = [];
const record = (ok, label, detail = '') => {
  checks.push({ ok, label });
  console.log(`  ${ok ? 'OK  ' : '실패'} ${label}${detail ? ` — ${detail}` : ''}`);
};

const browser = await chromium.launch();

/**
 * 로딩 중과 로딩 후에 **같은 것**을 재야 한다.
 * 스켈레톤의 제목 자리와 로딩 후의 첫 카드를 비교하면
 * 제목 높이만큼 늘 차이가 나서 없는 밀림을 본다.
 */
async function measure({ loginId, viewport, apiPattern, path, label, duringSelector, afterSelector }) {
  const ctx = await browser.newContext({ viewport, locale: 'ko-KR', timezoneId: 'Asia/Seoul' });
  const page = await ctx.newPage();

  await page.goto(`${APP}/login`);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.getByRole('button', { name: new RegExp(loginId) }).click();
  await page.waitForURL(/\/(ward|exam|admin)\//, { timeout: 15000 });
  if (path) await page.goto(`${APP}${path}`);
  await page.waitForLoadState('networkidle');

  // 응답을 늦춰 스켈레톤을 붙잡는다
  await page.route(apiPattern, async (route) => {
    await new Promise((r) => setTimeout(r, 4000));
    await route.continue();
  });
  await page.reload();

  // 로딩 중, 내용이 들어설 자리의 위치
  await page.locator(duringSelector).first().waitFor({ timeout: 10000 });
  const during = await page.evaluate((sel) => {
    const el = document.querySelector(sel);
    return el ? Math.round(el.getBoundingClientRect().top) : null;
  }, duringSelector);

  // 값이 들어온 뒤 그 자리에 실제로 오는 것의 위치
  await page.unroute(apiPattern);
  await page.locator(afterSelector).first().waitFor({ timeout: 15000 });
  await page.waitForTimeout(400);
  const after = await page.evaluate((sel) => {
    const el = document.querySelector(sel);
    return el ? Math.round(el.getBoundingClientRect().top) : null;
  }, afterSelector);

  await ctx.close();

  if (during === null || after === null) {
    record(false, label, '위치를 재지 못했다');
    return;
  }
  const shift = Math.abs(after - during);
  record(shift <= TOLERANCE_PX, label, `${during}px → ${after}px (${shift}px 밀림)`);
}

try {
  // 카드 목록: 스켈레톤의 첫 카드 ↔ 실제 첫 카드
  await measure({
    label: '병동 환자 보드',
    loginId: 'ward01',
    viewport: { width: 390, height: 844 },
    apiPattern: '**/api/v1/encounters**',
    duringSelector: '[role="status"] .space-y-2 > div',
    afterSelector: 'ul > li:first-child',
  });

  // 표: 스켈레톤의 표 상자 ↔ 실제 표 상자
  await measure({
    label: '검사실 들어온 요청',
    loginId: 'mri01',
    viewport: { width: 1280, height: 720 },
    apiPattern: '**/api/v1/transfer-requests**',
    duringSelector: '[role="status"] .overflow-hidden',
    afterSelector: '.overflow-x-auto',
  });

  // 통계: 숫자 카드 세 장이 놓이는 줄
  await measure({
    label: '관리자 통계',
    loginId: 'admin01',
    viewport: { width: 1280, height: 800 },
    apiPattern: '**/api/v1/stats/**',
    duringSelector: '[role="status"] .grid',
    afterSelector: '.grid',
  });
} finally {
  await browser.close();
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);

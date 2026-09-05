import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const APP = 'http://localhost:5173';
const OUT = '../docs/screenshots';

/**
 * README 에 넣을 화면 스틸을 뽑는다.
 *
 * 녹화(rehearsal.mjs)와 따로 둔 이유: 영상은 자막을 화면 위에 덮어쓰는데
 * 그 자막이 그대로 찍힌 스틸은 README 에 쓸 수 없다.
 *
 * 병동은 폰 크기로, 검사실·관리자는 PC 크기로 찍는다.
 * 같은 화면을 늘렸다 줄이는 게 아니라 처음부터 레이아웃이 다르기 때문이다.
 */
const DESKTOP = { width: 1280, height: 800 };
const PHONE = { width: 390, height: 844 };

async function login(page, loginId) {
  await page.goto(`${APP}/login`);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.getByRole('button', { name: new RegExp(loginId) }).click();
  await page.waitForURL(/\/(ward|exam|admin)\//, { timeout: 15000 });
}

/** 값이 다 들어온 뒤에 찍는다. 로딩 중 화면이 찍히면 README 가 비어 보인다. */
async function shot(page, name) {
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(700);
  await page.screenshot({ path: `${OUT}/${name}.png` });
  console.log(`  ${name}.png`);
}

async function newPage(browser, viewport) {
  const context = await browser.newContext({
    viewport,
    // README 에서 축소돼 보이므로 2배로 찍어야 글자가 선명하다
    deviceScaleFactor: 2,
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
  });
  return { context, page: await context.newPage() };
}

async function main() {
  mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();

  // ── 검사실 · 관리자 (PC)
  const desk = await newPage(browser, DESKTOP);
  try {
    await login(desk.page, 'mri01');
    await shot(desk.page, 'exam-queue');

    await desk.page.getByRole('link', { name: '일정' }).click();
    await desk.page.waitForURL(/\/exam\/schedule/);
    await shot(desk.page, 'exam-schedule');

    await login(desk.page, 'admin01');
    await shot(desk.page, 'stats');
  } finally {
    await desk.context.close();
  }

  // ── 병동 (폰)
  const phone = await newPage(browser, PHONE);
  try {
    await login(phone.page, 'ward01');
    await shot(phone.page, 'ward-board');

    // 요청 등록. 검사를 고르면 그 환자에게 확인이 필요한 항목이 바로 뜬다.
    await phone.page.getByRole('link', { name: /김OO/ }).first().click();
    await phone.page.waitForURL(/\/ward\/encounters\/\d+$/);
    await phone.page.getByRole('link', { name: /이송 요청/ }).click();
    await phone.page.waitForURL(/\/ward\/requests\/new/);
    await phone.page.getByRole('button', { name: /뇌 MRI/ }).click();
    await shot(phone.page, 'ward-create');

    // 진행 중인 요청 하나를 열어 이력을 보여준다
    await phone.page.getByRole('link', { name: '요청' }).click();
    await phone.page.waitForURL(/\/ward\/requests$/);
    await phone.page.locator('a[href^="/ward/requests/"]').first().click();
    await phone.page.waitForURL(/\/ward\/requests\/\d+$/);
    await shot(phone.page, 'ward-request');
  } finally {
    await phone.context.close();
  }

  await browser.close();
  console.log('\n스크린샷 완료');
}

main().catch((e) => {
  console.error('스크린샷 실패:', e.message);
  process.exit(1);
});

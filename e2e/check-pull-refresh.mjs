import { chromium } from 'playwright';

/**
 * 당겨서 새로고침이 실제 손가락 동작으로 되는지 확인한다.
 *
 * 코드만 봐서는 알 수 없다. 터치 이벤트가 붙었는지, 맨 위에서만 걸리는지,
 * 목록 중간에서 쓸어내릴 때 잘못 걸리지는 않는지는 눌러 봐야 안다.
 *
 *   APP=http://localhost:5173 node check-pull-refresh.mjs
 */
const APP = process.env.APP ?? 'http://localhost:5173';

const checks = [];
const record = (ok, label, detail = '') => {
  checks.push({ ok, label });
  console.log(`  ${ok ? 'OK  ' : '실패'} ${label}${detail ? ` — ${detail}` : ''}`);
};

const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 390, height: 844 },
  hasTouch: true,
  isMobile: true,
  locale: 'ko-KR',
  timezoneId: 'Asia/Seoul',
});
const page = await context.newPage();

/**
 * 손가락으로 아래로 끄는 동작.
 *
 * 한 번의 evaluate 안에서 전부 보내면 안 된다. 그 사이에는 React 가 그리지 못해서
 * 실제로는 여러 번에 걸쳐 일어나는 일이 한 순간에 몰린다.
 * 진짜 손가락처럼 틱을 나눠 보낸다.
 */
async function dispatchTouch(type, y) {
  await page.evaluate(
    ([type, x, clientY]) => {
      const target = document.elementFromPoint(x, Math.max(0, Math.min(clientY, innerHeight - 1)))
        ?? document.body;
      const list = clientY === null
        ? []
        : [new Touch({ identifier: 1, target, clientX: x, clientY })];
      target.dispatchEvent(new TouchEvent(type, {
        bubbles: true,
        cancelable: true,
        touches: type === 'touchend' ? [] : list,
        changedTouches: list.length ? list : [new Touch({ identifier: 1, target, clientX: x, clientY: 0 })],
      }));
    },
    [type, 195, y],
  );
}

async function drag(fromY, distance, { release = true } = {}) {
  await dispatchTouch('touchstart', fromY);
  for (let i = 1; i <= 6; i++) {
    await dispatchTouch('touchmove', fromY + (distance * i) / 6);
    await page.waitForTimeout(30);
  }
  if (release) {
    await dispatchTouch('touchend', fromY + distance);
    await page.waitForTimeout(100);
  }
}

try {
  await page.goto(`${APP}/login`);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.getByRole('button', { name: /ward01/ }).click();
  await page.waitForURL(/\/ward\//, { timeout: 15000 });
  await page.waitForLoadState('networkidle');

  // ── 맨 위에서 조금 당기면 안내가 뜬다
  await drag(200, 60, { release: false });
  const hint = await page.getByText(/당겨서 새로고침|놓으면 새로고침/).count();
  record(hint > 0, '맨 위에서 당기면 안내가 뜬다');

  // ── 충분히 당기면 문구가 바뀐다
  await drag(200, 220, { release: false });
  const ready = await page.getByText('놓으면 새로고침').count();
  record(ready > 0, '충분히 당기면 놓으라고 알린다');

  // ── 놓으면 실제로 다시 받아온다
  let refetched = false;
  page.on('request', (r) => {
    if (r.url().includes('/api/v1/care-episodes')) refetched = true;
  });
  await drag(200, 220);
  await page.waitForTimeout(1200);
  record(refetched, '놓으면 다시 받아온다');

  // ── 목록 중간에서는 걸리지 않는다
  await page.evaluate(() => window.scrollTo(0, 300));
  await page.waitForTimeout(200);
  await drag(400, 200, { release: false });
  const falsePositive = await page.getByText(/당겨서 새로고침|놓으면 새로고침/).count();
  record(falsePositive === 0, '중간에서 쓸어내려도 걸리지 않는다');
} finally {
  await browser.close();
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);

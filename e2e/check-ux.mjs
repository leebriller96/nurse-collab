import { chromium } from 'playwright';

/**
 * 사용 중 막히는 자리가 없는지 확인한다.
 *
 *   1. 조회가 실패했을 때 화면에서 빠져나올 수 있는가
 *   2. 되돌릴 수 없는 동작에 한 번 더 묻는가
 *
 * 둘 다 화면을 열어 보는 것만으로는 알 수 없다.
 * 오류 화면은 서버가 멀쩡하면 나오지 않고, 완료 확인은 요청을 끝까지 밀어야 나온다.
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
}

try {
  // ── 1. 조회 실패에서 빠져나올 수 있는가
  await login('mri01');
  await page.waitForLoadState('networkidle');

  // 서버가 죽은 상황을 만든다
  await page.route('**/api/v1/work-orders**', (route) => route.abort('failed'));
  await page.reload();

  const retry = page.getByRole('button', { name: '다시 시도' });
  await retry.waitFor({ timeout: 10000 });
  record(true, '조회 실패 시 다시 시도 버튼이 보인다');

  const body = await page.textContent('body');
  record(
    !/status code|Network Error|AxiosError|undefined/i.test(body ?? ''),
    '화면에 개발자 문구가 없다',
  );

  // 서버를 되살리고 눌러 본다
  await page.unroute('**/api/v1/work-orders**');
  await retry.click();
  await page.getByRole('heading', { name: /들어온 요청/ })
    .waitFor({ timeout: 10000 })
    .then(() => record(true, '다시 시도로 화면이 돌아온다'))
    .catch(() => record(false, '다시 시도로 화면이 돌아온다', '눌러도 복구되지 않는다'));

  // ── 2. 되돌릴 수 없는 동작에 한 번 더 묻는가
  // 요청 하나를 복귀중까지 밀어 두고, 병동이 완료를 누르는 상황을 만든다
  const requestId = await pushToReturned();

  await login('ward01');
  // 목록은 요청번호를 보여주지 않으므로 상세로 바로 간다
  await page.goto(`${APP}/ward/requests/${requestId}`);
  await page.waitForLoadState('networkidle');

  await page.getByRole('button', { name: '병동 도착', exact: true }).click();

  const warned = await page.getByText(/되돌릴 수 없습니다/).isVisible();
  record(warned, '완료 전에 되돌릴 수 없다고 알린다');

  const confirmButton = page.getByRole('button', { name: /병동 도착 확정/ });
  record(await confirmButton.isVisible(), '확정을 한 번 더 눌러야 한다');

  // 실제로 확정하면 끝나는지
  await confirmButton.click();
  await page.waitForTimeout(2000);
  record(
    (await page.textContent('body'))?.includes('완료') ?? false,
    '확정하면 완료된다',
  );
} finally {
  await browser.close();
}

/** 요청을 하나 만들어 복귀중까지 민다. 그래야 병동에 완료 버튼이 생긴다. 요청 id 를 준다. */
async function pushToReturned() {
  const api = `${APP.replace('5173', '8080')}/api/v1`;
  const token = async (id) =>
    (await (await fetch(`${api}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ loginId: id, password: 'nurse1234!' }),
    })).json()).accessToken;

  const ward = await token('ward01');
  const mri = await token('mri01');
  const h = (t) => ({ Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' });

  const encounters = await (await fetch(`${api}/encounters`, { headers: h(ward) })).json();
  const exams = await (await fetch(`${api}/service-items`, { headers: h(ward) })).json();
  const mriExam = exams.find((e) => e.code.startsWith('MRI'));

  const created = await (await fetch(`${api}/work-orders`, {
    method: 'POST',
    headers: h(ward),
    body: JSON.stringify({
      encounterId: (encounters.content ?? encounters)[0].encounterId,
      serviceItemId: mriExam.id,
      priority: 'ROUTINE',
    }),
  })).json();

  const steps = [
    [mri, 'ACCEPTED'], [mri, 'READY'], [ward, 'IN_TRANSIT'],
    [mri, 'IN_PROGRESS'], [mri, 'RETURNED'],
  ];
  for (const [t, toStatus] of steps) {
    const cur = await (await fetch(`${api}/work-orders/${created.id}`, { headers: h(t) })).json();
    const payload = { toStatus, version: cur.version };
    if (toStatus === 'ACCEPTED') payload.scheduledAt = new Date(Date.now() + 3600000).toISOString();
    await fetch(`${api}/work-orders/${created.id}/transitions`, {
      method: 'POST', headers: h(t), body: JSON.stringify(payload),
    });
  }
  return created.id;
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);

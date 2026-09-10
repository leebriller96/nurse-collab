import { chromium } from 'playwright';

/**
 * 내가 한 동작에 확인이 뜨는지, 그리고 실시간 알림이 여전히 뜨는지 확인한다.
 *
 * 둘은 같은 자리에 뜬다. 확인을 붙이면서 실시간 쪽을 건드렸으므로
 * 한쪽만 보면 다른 쪽이 죽은 것을 모른다.
 *
 *   APP=http://localhost:5173 node check-feedback.mjs
 */
const APP = process.env.APP ?? 'http://localhost:5173';
const API = `${APP.replace('5173', '8080').replace('8081', '8081')}/api/v1`;

const checks = [];
const record = (ok, label, detail = '') => {
  checks.push({ ok, label });
  console.log(`  ${ok ? 'OK  ' : '실패'} ${label}${detail ? ` — ${detail}` : ''}`);
};

const token = async (id) =>
  (await (await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ loginId: id, password: 'nurse1234!' }),
  })).json()).accessToken;

const h = (t) => ({ Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' });

/** 요청 하나를 만들어 검사실이 접수할 수 있는 상태로 둔다 */
async function createRequest() {
  const ward = await token('ward01');
  const encounters = await (await fetch(`${API}/encounters`, { headers: h(ward) })).json();
  const exams = await (await fetch(`${API}/exam-types`, { headers: h(ward) })).json();
  const mriExam = exams.find((e) => e.code.startsWith('MRI'));

  return (await (await fetch(`${API}/transfer-requests`, {
    method: 'POST',
    headers: h(ward),
    body: JSON.stringify({
      encounterId: (encounters.content ?? encounters)[0].encounterId,
      examTypeId: mriExam.id,
      priority: 'ROUTINE',
    }),
  })).json()).id;
}

const browser = await chromium.launch();
const context = await browser.newContext({ locale: 'ko-KR', timezoneId: 'Asia/Seoul' });
const page = await context.newPage();

try {
  const requestId = await createRequest();

  await page.goto(`${APP}/login`);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.getByRole('button', { name: /mri01/ }).click();
  await page.waitForURL(/\/exam\//, { timeout: 15000 });

  // ── 1. 내가 한 동작에 확인이 뜨는가
  await page.goto(`${APP}/exam/requests/${requestId}`);
  await page.waitForLoadState('networkidle');

  await page.getByRole('button', { name: '접수', exact: true }).click();
  const when = new Date(Date.now() + 3600000);
  when.setSeconds(0, 0);
  await page.locator('input[type="datetime-local"]').fill(
    new Date(when.getTime() - when.getTimezoneOffset() * 60000).toISOString().slice(0, 16),
  );
  await page.getByRole('button', { name: /접수 확정/ }).click();

  const confirm = page.getByText('접수 처리했습니다');
  await confirm.waitFor({ timeout: 10000 });
  record(true, '동작하면 확인이 뜬다');

  // 방송은 파트 채널로 나가므로 누른 사람에게도 되돌아온다.
  // 거르지 않으면 확인과 함께 "접수됨 — MRI실 박간호" 가 같이 떠서 두 개가 겹친다.
  await page.waitForTimeout(1500);
  const echoed = await page.getByText('MRI실 박간호').count();
  record(echoed === 0, '내가 한 일은 나에게 되돌아오지 않는다', echoed > 0 ? `${echoed}개 겹침` : '');

  // 잠깐 떠 있다 사라져야 한다. 남아 있으면 화면을 가린다.
  await confirm.waitFor({ state: 'hidden', timeout: 10000 });
  record(true, '확인은 잠깐 뒤 사라진다');

  // ── 2. 실시간 알림이 여전히 뜨는가
  // 다른 사람이 새 요청을 만들면 검사실 화면에 떠야 한다
  await page.goto(`${APP}/exam/queue`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1500); // STOMP 구독이 붙을 시간

  await createRequest();

  await page.getByText('새 요청').first().waitFor({ timeout: 15000 })
    .then(() => record(true, '실시간 알림이 여전히 뜬다'))
    .catch(() => record(false, '실시간 알림이 여전히 뜬다', '토스트를 옮기면서 끊겼다'));

  // ── 3. 토스트가 상단 메뉴를 막지 않는가
  const clickable = await page.getByRole('link', { name: '일정' }).isEnabled();
  record(clickable, '토스트가 떠 있어도 메뉴를 누를 수 있다');
} finally {
  await browser.close();
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);

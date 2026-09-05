import { chromium } from 'playwright';
import { mkdirSync, rmSync } from 'node:fs';

const APP = 'http://localhost:5173';
const API = 'http://localhost:8080/api/v1';
const OUT = 'recordings';

/** 화면이 바뀐 뒤 사람이 읽을 시간을 준다. 녹화가 목적이라 일부러 느리게 간다. */
const beat = (page, ms = 1600) => page.waitForTimeout(ms);

async function login(page, loginId, label) {
  await page.goto(`${APP}/login`);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.getByRole('button', { name: new RegExp(loginId) }).click();
  await page.waitForURL(/\/(ward|exam|admin)\//, { timeout: 15000 });
  console.log(`  ${label} 로그인 → ${new URL(page.url()).pathname}`);
  await beat(page);
}

/** 화면 위에 자막을 띄운다. 무엇을 보고 있는지 영상만으로 알 수 있게 한다. */
async function caption(page, step, text) {
  await page.evaluate(
    ([step, text]) => {
      document.getElementById('rehearsal-caption')?.remove();
      const el = document.createElement('div');
      el.id = 'rehearsal-caption';
      el.style.cssText = [
        'position:fixed', 'left:0', 'right:0', 'top:0', 'z-index:99999',
        // 자막이 고정 버튼을 가려 클릭을 막으면 안 된다
        'pointer-events:none',
        'background:rgba(15,23,42,.94)', 'color:#fff', 'padding:14px 20px',
        'font:600 17px/1.4 system-ui,-apple-system,sans-serif',
        'display:flex', 'gap:12px', 'align-items:baseline',
      ].join(';');
      el.innerHTML =
        `<span style="background:#0ea5e9;border-radius:6px;padding:2px 10px;font-size:14px">${step}</span>` +
        `<span>${text}</span>`;
      document.body.appendChild(el);
    },
    [step, text],
  );
  console.log(`[${step}] ${text}`);
}

/** 다른 간호사가 먼저 처리한다. 브라우저 밖에서 부르므로 화면 상태와 무관하다. */
async function otherNurseCompletesFirst(requestId) {
  const login = await (
    await fetch(`${API}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ loginId: 'mri01', password: 'nurse1234!' }),
    })
  ).json();

  const headers = {
    Authorization: `Bearer ${login.accessToken}`,
    'Content-Type': 'application/json',
  };
  const current = await (
    await fetch(`${API}/transfer-requests/${requestId}`, { headers })
  ).json();

  const res = await fetch(`${API}/transfer-requests/${requestId}/transitions`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ toStatus: 'READY', version: current.version }),
  });
  console.log(`  다른 간호사가 먼저 처리: HTTP ${res.status}`);
}

async function main() {
  rmSync(OUT, { recursive: true, force: true });
  mkdirSync(OUT, { recursive: true });

  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    recordVideo: { dir: OUT, size: { width: 1280, height: 800 } },
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
  });
  const page = await context.newPage();

  try {
    // ── 1. 병동 로그인
    await page.goto(`${APP}/login`);
    await beat(page, 1200);
    await caption(page, '1', '병동 간호사가 로그인한다. 소속 파트에 따라 홈 화면이 갈린다.');
    await beat(page);
    await login(page, 'ward01', '병동');
    await caption(page, '2', '담당 환자가 병실 순서대로 보인다. 주의사항은 카드에서 바로 확인한다.');
    await beat(page, 2400);

    // ── 2. 환자 상세
    await page.getByRole('link', { name: /김OO/ }).first().click();
    await page.waitForURL(/\/ward\/encounters\//);
    await caption(page, '3', '이 환자는 좌측 고관절에 인공관절이 있다.');
    await beat(page, 2600);

    // ── 3. 이송 요청 등록
    await page.getByRole('link', { name: /이송 요청/ }).click();
    await page.waitForURL(/\/ward\/requests\/new/);
    await caption(page, '4', '검사실로 보낼 검사를 고른다.');
    await beat(page);

    await page.getByRole('button', { name: /뇌 MRI/ }).click();
    await caption(page, '5', '검사를 고르자마자 안내가 뜬다. 이 환자에게 확인이 필요한 항목이다.');
    await beat(page, 3000);

    await page.getByRole('button', { name: '응급', exact: true }).click();
    await caption(page, '6', '응급으로 지정하고 등록한다.');
    await beat(page, 1400);
    await page.getByRole('button', { name: '요청 등록' }).click();
    await page.waitForURL(/\/ward\/requests\/\d+/, { timeout: 15000 });
    const requestNo = await page.locator('header span.font-mono').first().innerText();
    await caption(page, '7', `${requestNo} 로 접수됐다. 병동에서는 "취소" 만 가능하다. 접수는 검사실이 한다.`);
    await beat(page, 3000);

    // ── 4. 검사실 전환
    await login(page, 'mri01', '검사실');
    await caption(page, '8', `검사실 화면에 방금 보낸 ${requestNo} 가 응급으로 올라와 있다.`);
    await beat(page, 3000);

    // 큐는 우선순위·오래된 순이라 첫 행이 방금 만든 요청이 아니다. 번호로 집는다.
    await page.locator(`tbody tr:has-text("${requestNo}")`).click();
    await page.waitForURL(/\/exam\/requests\/\d+/);
    await caption(page, '9', '열어보면 "MRI 금기 가능성" 안내가 이미 떠 있다. 따로 확인하러 갈 필요가 없다.');
    await beat(page, 3200);

    // ── 5. 접수
    await page.getByRole('button', { name: '접수', exact: true }).click();
    await caption(page, '10', '접수하려면 예정 시각이 반드시 있어야 한다. 없으면 확정 버튼이 눌리지 않는다.');
    await beat(page, 2400);

    const when = new Date(Date.now() + 3600000);
    when.setSeconds(0, 0);
    const local = new Date(when.getTime() - when.getTimezoneOffset() * 60000)
      .toISOString()
      .slice(0, 16);
    await page.locator('input[type="datetime-local"]').fill(local);
    await beat(page, 1000);
    await page.getByRole('button', { name: /접수 확정/ }).click();
    await page.waitForTimeout(2000);
    await caption(page, '11', '접수하면 진행 기록에 한 줄이 남고, 다음에 할 일로 버튼이 바뀐다.');
    await beat(page, 3200);

    // ── 6. 동시 처리 충돌
    // 실시간 알림이 붙은 뒤로는 남이 먼저 처리하면 화면이 곧바로 따라잡는다.
    // 그래서 "낡은 화면" 을 만들어서는 충돌을 재현할 수 없다.
    // 두 사람이 거의 동시에 누른 상황을 만들어야 한다.
    await caption(page, '12', '두 사람이 같은 요청을 거의 동시에 누르는 상황을 만든다.');
    await beat(page, 2200);

    const requestId = page.url().split('/').pop();

    // 화면이 보낸 요청을 잠깐 붙잡아 두고, 그 사이 다른 사람이 먼저 커밋하게 한다
    await page.route('**/transfer-requests/*/transitions', async (route) => {
      await new Promise((r) => setTimeout(r, 2500));
      await route.continue();
    });

    const clicked = page.getByRole('button', { name: '준비 완료', exact: true }).click();
    await new Promise((r) => setTimeout(r, 400));
    await otherNurseCompletesFirst(requestId);
    await clicked;

    await page.waitForSelector('[role="alert"]', { timeout: 15000 });
    await caption(page, '13', '나중에 누른 쪽은 막힌다. 같은 환자를 두 번 보내는 일이 생기지 않는다.');
    await beat(page, 4000);
    await page.unroute('**/transfer-requests/*/transitions');

    // ── 7. 관리자 통계
    await login(page, 'admin01', '관리자');
    await caption(page, '14', '수간호사와 관리자는 검사실별 평균 대기시간을 본다.');
    await beat(page, 4000);

    console.log('\n리허설 완료');
  } finally {
    await context.close();
    await browser.close();
  }
}

main().catch((e) => {
  console.error('리허설 실패:', e.message);
  process.exit(1);
});

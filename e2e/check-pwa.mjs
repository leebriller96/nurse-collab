import { chromium } from 'playwright';

/**
 * 프로덕션 빌드가 실제로 설치 가능한 앱인지 확인한다.
 *
 *   cd frontend && npm run build && npm run preview
 *   cd e2e && node check-pwa.mjs
 *
 * 이 검사를 따로 두는 이유: PWA 는 조용히 깨진다.
 * 매니페스트 항목 하나가 빠지거나 아이콘 경로가 틀려도 화면은 멀쩡히 뜬다.
 * 폰에서 "홈 화면에 추가" 가 안 나오고 나서야 알게 된다.
 *
 * 특히 마지막 검사가 중요하다. **API 응답이 캐시에 들어가면 안 된다.**
 * 오프라인에서 지난 활력징후나 낡은 이송 상태를 지금 값처럼 보여주면
 * 간호사가 이미 끝난 검사를 다시 보내거나 옛날 수치로 판단할 수 있다.
 */
const APP = process.env.APP ?? 'http://localhost:4173';

const checks = [];
const record = (ok, label, detail = '') => {
  checks.push({ ok, label, detail });
  console.log(`  ${ok ? 'OK  ' : '실패'} ${label}${detail ? ` — ${detail}` : ''}`);
};

const browser = await chromium.launch();
const context = await browser.newContext({ serviceWorkers: 'allow' });
const page = await context.newPage();

try {
  await page.goto(`${APP}/login`, { waitUntil: 'networkidle' });
  // 서비스 워커 등록은 load 이벤트 뒤에 일어난다
  await page.waitForTimeout(3000);

  const state = await page.evaluate(async () => {
    const reg = await navigator.serviceWorker.getRegistration();
    const link = document.querySelector('link[rel="manifest"]');
    const manifest = link ? await (await fetch(link.href)).json() : null;

    const keys = await caches.keys();
    const cachedUrls = [];
    for (const k of keys) {
      for (const req of await (await caches.open(k)).keys()) cachedUrls.push(req.url);
    }

    return {
      swState: reg?.active?.state ?? null,
      manifest,
      appleIcon: document.querySelector('link[rel="apple-touch-icon"]')?.href ?? null,
      themeColor: document.querySelector('meta[name="theme-color"]')?.content ?? null,
      cachedUrls,
    };
  });

  record(state.swState === 'activated', '서비스 워커 활성', state.swState ?? '등록 안 됨');
  record(!!state.manifest, '매니페스트 제공');
  record(state.manifest?.display === 'standalone', '앱처럼 실행(standalone)');
  record(!!state.manifest?.short_name, '홈 화면 이름', state.manifest?.short_name);
  record(state.themeColor === '#0284c7', '상단 막대 색');
  record(!!state.appleIcon, 'iOS 홈 화면 아이콘');

  const sizes = (state.manifest?.icons ?? []).map((i) => i.sizes);
  record(sizes.includes('192x192') && sizes.includes('512x512'), '설치 아이콘 192·512');
  record(
    (state.manifest?.icons ?? []).some((i) => i.purpose === 'maskable'),
    '잘려도 되는 아이콘(maskable)',
  );

  // 아이콘이 실제로 받아지는지. 경로만 맞고 파일이 없는 경우가 흔하다.
  for (const icon of state.manifest?.icons ?? []) {
    const res = await page.request.get(new URL(icon.src, APP).href);
    record(res.ok(), `아이콘 파일 ${icon.src}`, `HTTP ${res.status()}`);
  }

  record(state.cachedUrls.length > 0, '앱 껍데기 캐시됨', `${state.cachedUrls.length}개`);

  const cachedApi = state.cachedUrls.filter((u) => u.includes('/api/'));
  record(cachedApi.length === 0, 'API 응답은 캐시하지 않음', cachedApi.join(', '));

  // ── 연결이 끊겼을 때 ────────────────────────────────────
  // 병동 간호사는 엘리베이터와 지하 검사실을 오간다. 끊기는 것이 일상이라
  // 끊긴 줄 모르고 버튼을 누르는 상황을 막아야 한다.
  const banner = page.getByRole('alert').filter({ hasText: '연결이 끊겼습니다' });

  record(!(await banner.isVisible()), '연결됐을 때는 배너 없음');

  await context.setOffline(true);
  await page.evaluate(() => window.dispatchEvent(new Event('offline')));
  await page.waitForTimeout(500);
  record(await banner.isVisible(), '끊기면 배너가 뜬다');
  // 실패했을 때 눈으로 볼 것을 남긴다. recordings/ 는 커밋하지 않는다.
  await page.screenshot({ path: 'recordings/offline.png' });

  await context.setOffline(false);
  await page.evaluate(() => window.dispatchEvent(new Event('online')));
  await page.waitForTimeout(500);
  record(!(await banner.isVisible()), '다시 붙으면 배너가 사라진다');
} finally {
  await browser.close();
}

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);

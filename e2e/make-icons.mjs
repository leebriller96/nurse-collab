import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';

/**
 * public/icon.svg 를 PWA 가 요구하는 PNG 크기들로 굽는다.
 *
 * 브라우저로 굽는 이유는 sharp 같은 이미지 라이브러리를 하나 더 들이지 않기 위해서다.
 * Playwright 는 리허설 녹화 때문에 이미 있다.
 *
 *   node make-icons.mjs
 */
const SVG = '../frontend/public/icon.svg';
const OUT = '../frontend/public';

// 192·512 는 안드로이드 설치 아이콘, 180 은 iOS 홈 화면 아이콘 규격이다
const SIZES = [
  { size: 192, name: 'icon-192.png' },
  { size: 512, name: 'icon-512.png' },
  { size: 180, name: 'apple-touch-icon.png' },
];

async function main() {
  const svg = readFileSync(SVG, 'utf8');
  const browser = await chromium.launch();

  try {
    for (const { size, name } of SIZES) {
      const page = await browser.newPage({ viewport: { width: size, height: size } });
      // 배경을 투명하게 두면 iOS 가 검은색을 깔아 버린다. SVG 자체가 배경을 채운다.
      await page.setContent(
        `<style>html,body{margin:0;padding:0}svg{display:block;width:${size}px;height:${size}px}</style>${svg}`,
      );
      await page.screenshot({ path: `${OUT}/${name}`, omitBackground: true });
      await page.close();
      console.log(`  ${name} (${size}x${size})`);
    }
    console.log('\n아이콘 생성 완료');
  } finally {
    await browser.close();
  }
}

main().catch((e) => {
  console.error('실패:', e.message);
  process.exit(1);
});

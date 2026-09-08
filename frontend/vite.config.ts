import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'node:path';

/** 개발 서버와 미리보기 서버가 같은 경로를 백엔드로 넘긴다 */
const PROXY = {
  '/api': { target: 'http://localhost:8080', changeOrigin: true },
  // STOMP 는 WebSocket 이라 ws: true 가 없으면 프록시가 중계하지 않는다
  '/ws': { target: 'ws://localhost:8080', ws: true, changeOrigin: true },
};

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      // 새로 배포하면 다음 방문 때 조용히 갱신된다.
      // 간호사에게 "새 버전이 있습니다" 를 눌러 달라고 할 수는 없다.
      registerType: 'autoUpdate',
      // 개발 서버에서는 서비스 워커를 끈다.
      // 캐시가 HMR 과 얽히면 고친 코드가 화면에 안 나오는 이유를 한참 찾게 된다.
      // 확인은 `npm run build && npm run preview` 로 한다. localhost 도 보안 컨텍스트라
      // 거기서 설치까지 그대로 재현된다.
      devOptions: { enabled: false },
      includeAssets: ['icon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: '간호 협업 시스템',
        // 홈 화면 아이콘 아래 들어가는 이름. 길면 잘린다.
        short_name: '간호 협업',
        description: '병동과 검사실 사이의 환자 이송 요청',
        lang: 'ko',
        start_url: '/',
        // 주소창 없이 앱처럼 뜬다. 한 손으로 쓰는 화면이라 세로 공간이 아깝다.
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#f1f5f9',
        theme_color: '#0284c7',
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
          // 안드로이드가 아이콘을 원형 등으로 잘라낼 때 쓴다.
          // 글리프를 가운데 60% 안에 넣어 두어 잘려도 살아남는다.
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // 앱 껍데기(JS/CSS/HTML)만 캐시한다.
        //
        // API 응답은 **절대 캐시하지 않는다.** 오프라인에서 지난 활력징후나
        // 낡은 이송 상태를 보여주면, 간호사가 이미 끝난 검사를 다시 보내거나
        // 옛날 수치를 보고 판단할 수 있다. 화면이 안 뜨는 것보다 그쪽이 위험하다.
        // 그래서 연결이 끊기면 캐시된 값을 내놓는 대신 끊겼다고 알린다.
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        navigateFallback: '/index.html',
        // SPA 폴백이 API 와 실시간 연결까지 가로채면 안 된다
        navigateFallbackDenylist: [/^\/api\//, /^\/ws/],
      },
    }),
  ],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    // 개발 중에는 프록시로 붙인다. 백엔드에 CORS 설정을 넣지 않아도 되고,
    // 배포 때 같은 오리진에서 서비스하는 구성과도 모양이 같아진다.
    proxy: PROXY,
  },
  // 빌드 결과를 확인할 때도 같은 프록시가 필요하다.
  // 서비스 워커와 설치 동작은 여기서만 진짜 모습으로 확인된다.
  preview: {
    port: 4173,
    proxy: PROXY,
  },
});

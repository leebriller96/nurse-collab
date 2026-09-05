import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    // 개발 중에는 프록시로 붙인다. 백엔드에 CORS 설정을 넣지 않아도 되고,
    // 배포 때 같은 오리진에서 서비스하는 구성과도 모양이 같아진다.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      // STOMP 는 WebSocket 이라 ws: true 가 없으면 프록시가 중계하지 않는다
      '/ws': { target: 'ws://localhost:8080', ws: true, changeOrigin: true },
    },
  },
});

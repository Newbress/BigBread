import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // 레포 루트의 .env를 백엔드/Docker Compose와 공유한다.
  envDir: '..',
  // 브라우저에 노출할 변수 접두사. KAKAO_JS_KEY(JS 키는 도메인 등록으로 보호되는 공개용 키)만 해당되고
  // KAKAO_REST_API_KEY, KAKAO_CLIENT_SECRET 같은 비밀 값은 노출되지 않는다.
  envPrefix: ['VITE_', 'KAKAO_JS_'],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})

import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
// Cổng gateway mặc định 8081 (dùng chung của nhóm). Máy cá nhân đổi qua .env.local (gitignored):
//   VITE_GATEWAY_URL=http://127.0.0.1:9991
//   VITE_KEYCLOAK_URL=http://localhost:9998
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const gateway = env.VITE_GATEWAY_URL || 'http://127.0.0.1:8081'
  return {
    plugins: [react()],
    server: {
      port: 5173,
      // Proxy /identity/**, /work/** và /workspace/** tới API Gateway khi dev
      // (tránh CORS, giống reverse-proxy production).
      proxy: {
        '/identity': { target: gateway, changeOrigin: true },
        '/work': { target: gateway, changeOrigin: true },
        '/workspace': { target: gateway, changeOrigin: true },
      },
    },
  }
})

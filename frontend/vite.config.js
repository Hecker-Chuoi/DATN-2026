import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Proxy /identity/** to API Gateway during development.
    // This avoids CORS issues and mirrors the production reverse-proxy setup.
    proxy: {
      '/identity': {
        target: 'http://127.0.0.1:9991',
        changeOrigin: true,
      },
      '/work': {
        target: 'http://127.0.0.1:9991',
        changeOrigin: true,
      },
    },
  },
})


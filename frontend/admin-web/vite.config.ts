import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 7514,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.VITE_GATEWAY_URL ?? 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})

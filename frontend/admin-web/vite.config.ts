import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

const configDir = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, configDir, '')
  return {
    plugins: [react()],
    server: {
      port: 7514,
      strictPort: true,
      proxy: {
        '/api': {
          target: env.VITE_GATEWAY_URL || process.env.VITE_GATEWAY_URL || 'http://127.0.0.1:8088',
          changeOrigin: true,
        },
      },
    },
  }
})

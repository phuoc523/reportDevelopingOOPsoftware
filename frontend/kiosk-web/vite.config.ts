import os from 'node:os'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

const VIRTUAL_ADAPTER = /vethernet|vmware|vmnet|virtualbox|vbox|wsl|hyper-v|docker|loopback|tailscale|zerotier|hamachi|bluetooth/i
const WIFI_ADAPTER = /wi-fi|wifi|wlan|wireless/i
const ETHERNET_ADAPTER = /ethernet|lan|^en\d+/i

function detectLanIp(): string | null {
  const candidates: Array<{ name: string; address: string; priority: number }> = []
  for (const [name, list] of Object.entries(os.networkInterfaces())) {
    if (!list || VIRTUAL_ADAPTER.test(name)) continue
    for (const info of list) {
      if (info.family !== 'IPv4' || info.internal || info.address.startsWith('169.254.')) continue
      const priority = WIFI_ADAPTER.test(name) ? 0 : ETHERNET_ADAPTER.test(name) ? 1 : 2
      candidates.push({ name, address: info.address, priority })
    }
  }
  candidates.sort((a, b) => a.priority - b.priority)
  return candidates[0]?.address ?? null
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [
      react(),
      {
        name: 'kiosk-dynamic-lan-ip',
        configureServer(server) {
          server.middlewares.use('/__kiosk-network', (_req, res) => {
            res.setHeader('Content-Type', 'application/json')
            res.setHeader('Cache-Control', 'no-store')
            res.end(JSON.stringify({ ip: detectLanIp() }))
          })
        },
      },
    ],
    server: {
      host: '0.0.0.0',
      port: 7515,
      proxy: {
        '/api': {
          target: env.VITE_GATEWAY_URL || env.VITE_API_BASE_URL || 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },
  }
})

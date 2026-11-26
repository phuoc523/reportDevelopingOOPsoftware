import { execFile } from 'node:child_process'
import type { IncomingMessage, ServerResponse } from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { promisify } from 'node:util'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

const execFileAsync = promisify(execFile)
const REPO_ROOT = path.resolve(__dirname, '..', '..')
const ESP32_BRIDGE_SCRIPT = path.join(REPO_ROOT, 'scripts', 'esp32-serial-bridge.ps1')
const SYNC_LAN_SCRIPT = path.join(REPO_ROOT, 'scripts', 'sync-lan-ip.ps1')

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

async function detectHostWifiSsid(): Promise<string | null> {
  if (process.platform !== 'win32') return null
  try {
    const { stdout } = await execFileAsync('netsh', ['wlan', 'show', 'interfaces'], { timeout: 3000 })
    const match = stdout.match(/^\s*SSID\s*:\s*(.+)$/m)
    const ssid = match?.[1]?.trim()
    return ssid && ssid !== '' ? ssid : null
  } catch {
    return null
  }
}

async function readJsonBody(req: IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = []
  for await (const chunk of req) {
    chunks.push(typeof chunk === 'string' ? Buffer.from(chunk) : chunk)
  }
  const raw = Buffer.concat(chunks).toString('utf8').trim()
  if (!raw) return {}
  return JSON.parse(raw) as Record<string, unknown>
}

function sendJson(res: ServerResponse, status: number, body: unknown) {
  res.statusCode = status
  res.setHeader('Content-Type', 'application/json; charset=utf-8')
  res.setHeader('Cache-Control', 'no-store')
  res.end(JSON.stringify(body))
}

async function runEsp32Bridge(
  action: 'STATUS' | 'SCAN_WIFI' | 'WIFI',
  options: { ssid?: string; password?: string; broker?: string } = {},
) {
  const args = ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ESP32_BRIDGE_SCRIPT, '-Action', action]

  const env = { ...process.env }
  if (options.ssid !== undefined) {
    env.ESP32_WIFI_SSID = options.ssid
  }
  if (options.password !== undefined) {
    env.ESP32_WIFI_PASSWORD = options.password
  }
  if (options.broker !== undefined) {
    env.ESP32_MQTT_BROKER = options.broker
  }

  const { stdout } = await execFileAsync('powershell.exe', args, {
    cwd: REPO_ROOT,
    env,
    timeout: action === 'WIFI' ? 45000 : 18000,
    maxBuffer: 1024 * 1024,
  })
  const lines = stdout.trim().split(/\r?\n/).filter(Boolean)
  const lastLine = lines[lines.length - 1] || '{}'
  return JSON.parse(lastLine) as Record<string, unknown>
}

async function runLanTlsSync(): Promise<{ ok: boolean; error?: string }> {
  try {
    await execFileAsync(
      'powershell.exe',
      ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', SYNC_LAN_SCRIPT],
      { cwd: REPO_ROOT, timeout: 90000, maxBuffer: 2 * 1024 * 1024 },
    )
    return { ok: true }
  } catch {
    return { ok: false, error: 'Không thể đồng bộ chứng chỉ TLS theo IP mới.' }
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [
      react(),
      {
        name: 'kiosk-local-device-bridge',
        configureServer(server) {
          server.middlewares.use('/__kiosk-network/sync', async (req, res) => {
            if (req.method !== 'POST') {
              sendJson(res, 405, { ok: false, error: 'Method Not Allowed' })
              return
            }
            const sync = await runLanTlsSync()
            const ip = detectLanIp()
            const hostWifiSsid = await detectHostWifiSsid()
            sendJson(res, sync.ok ? 200 : 500, { ...sync, ip, hostWifiSsid })
          })

          server.middlewares.use('/__kiosk-network', async (_req, res) => {
            const [ip, hostWifiSsid] = [detectLanIp(), await detectHostWifiSsid()]
            sendJson(res, 200, { ip, hostWifiSsid })
          })

          server.middlewares.use('/__kiosk-esp32/status', async (_req, res) => {
            try {
              const [bridge, hostWifiSsid] = await Promise.all([
                runEsp32Bridge('STATUS'),
                detectHostWifiSsid(),
              ])
              sendJson(res, 200, { ...bridge, hostIp: detectLanIp(), hostWifiSsid })
            } catch (err) {
              sendJson(res, 500, {
                ok: false,
                connected: false,
                hostIp: detectLanIp(),
                error: err instanceof Error ? err.message : 'Không thể đọc trạng thái ESP32',
              })
            }
          })

          server.middlewares.use('/__kiosk-esp32/scan', async (_req, res) => {
            try {
              const bridge = await runEsp32Bridge('SCAN_WIFI')
              sendJson(res, 200, bridge)
            } catch (err) {
              sendJson(res, 500, {
                ok: false,
                error: err instanceof Error ? err.message : 'Không thể quét Wi-Fi từ ESP32',
              })
            }
          })

          server.middlewares.use('/__kiosk-esp32/wifi', async (req, res) => {
            if (req.method !== 'POST') {
              sendJson(res, 405, { ok: false, error: 'Method Not Allowed' })
              return
            }
            try {
              const body = await readJsonBody(req)
              const ssid = typeof body.ssid === 'string' ? body.ssid.trim() : ''
              const password = typeof body.password === 'string' ? body.password : ''
              const syncTls = body.syncTls !== false
              if (!ssid) {
                sendJson(res, 400, { ok: false, error: 'Vui lòng nhập tên Wi-Fi (SSID).' })
                return
              }
              const hostIp = detectLanIp()
              const esp32 = await runEsp32Bridge('WIFI', {
                ssid,
                password,
                broker: hostIp ?? undefined,
              })
              let tlsSync: { ok: boolean; output?: string; error?: string } | null = null
              if (syncTls) {
                tlsSync = await runLanTlsSync()
              }
              sendJson(res, 200, {
                ok: Boolean(esp32.ok),
                port: esp32.port ?? null,
                esp32: esp32.data ?? null,
                error: esp32.error ?? null,
                hostIp,
                tlsSync,
              })
            } catch (err) {
              sendJson(res, 500, {
                ok: false,
                error: err instanceof Error ? err.message : 'Không thể cấu hình Wi-Fi cho ESP32',
              })
            }
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

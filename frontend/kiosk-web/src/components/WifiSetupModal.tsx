import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'

interface Esp32StatusData {
  machineId?: string
  firmwareVersion?: string
  wifiConnected?: boolean
  ssid?: string
  ip?: string
  rssi?: number
  mqttConnected?: boolean | number
  mqttHost?: string
}

interface Esp32StatusResponse {
  ok?: boolean
  connected?: boolean
  port?: string | null
  hostIp?: string | null
  hostWifiSsid?: string | null
  data?: Esp32StatusData
  error?: string
}

interface WifiNetwork {
  ssid: string
  rssi: number
  secured: boolean
}

interface WifiSetupModalProps {
  open: boolean
  onClose: () => void
  onSynced?: () => void
}

function signalLabel(rssi: number): string {
  if (rssi >= -55) return 'Rất mạnh'
  if (rssi >= -68) return 'Tốt'
  if (rssi >= -78) return 'Trung bình'
  return 'Yếu'
}

export function WifiSetupModal({ open, onClose, onSynced }: WifiSetupModalProps) {
  const [statusLoading, setStatusLoading] = useState(false)
  const [scanLoading, setScanLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  const [status, setStatus] = useState<Esp32StatusResponse | null>(null)
  const [hasScanned, setHasScanned] = useState(false)
  const [networks, setNetworks] = useState<WifiNetwork[]>([])
  const [ssid, setSsid] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [syncTls, setSyncTls] = useState(true)
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error'; text: string } | null>(null)

  const loadStatus = useCallback(async () => {
    setStatusLoading(true)
    try {
      const res = await fetch('/__kiosk-esp32/status', { cache: 'no-store' })
      const data = await res.json() as Esp32StatusResponse
      setStatus(data)
    } catch {
      setStatus({
        ok: false,
        connected: false,
        error: 'Không thể đọc trạng thái thiết bị.',
      })
    } finally {
      setStatusLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!open) return
    setFeedback(null)
    setHasScanned(false)
    setNetworks([])
    setSsid('')
    setPassword('')
    setShowPassword(false)
    void loadStatus()
  }, [open, loadStatus])

  if (!open) return null

  async function scanWifi() {
    setScanLoading(true)
    setFeedback(null)
    try {
      const res = await fetch('/__kiosk-esp32/scan', { cache: 'no-store' })
      const data = await res.json() as { ok?: boolean; error?: string; data?: { networks?: WifiNetwork[] } }
      if (!data.ok) {
        setFeedback({ kind: 'error', text: data.error || 'Không thể quét danh sách Wi-Fi từ ESP32.' })
        return
      }
      const list = data.data?.networks ?? []
      setNetworks(list)
      setHasScanned(true)
      if (list.length === 0) {
        setFeedback({ kind: 'error', text: 'Không tìm thấy mạng Wi-Fi 2.4GHz nào xung quanh.' })
      }
    } catch {
      setFeedback({ kind: 'error', text: 'Không thể quét Wi-Fi. Vui lòng thử lại.' })
    } finally {
      setScanLoading(false)
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!ssid.trim()) {
      setFeedback({ kind: 'error', text: 'Vui lòng chọn hoặc nhập tên mạng Wi-Fi.' })
      return
    }
    setSaving(true)
    setFeedback(null)
    try {
      const res = await fetch('/__kiosk-esp32/wifi', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ssid: ssid.trim(), password, syncTls }),
      })
      const result = await res.json() as {
        ok?: boolean
        error?: string
        esp32?: { ssid?: string; ip?: string } | null
        tlsSync?: { ok?: boolean } | null
      }
      if (!result.ok) {
        setFeedback({ kind: 'error', text: result.error || 'Không thể kết nối Wi-Fi mới. Kiểm tra lại tên mạng hoặc mật khẩu.' })
      } else {
        const syncedOk = !syncTls || Boolean(result.tlsSync?.ok)
        setFeedback({
          kind: 'success',
          text: syncedOk
            ? `Đã kết nối Wi-Fi "${ssid.trim()}" và đồng bộ mạng thành công.`
            : `Đã kết nối Wi-Fi "${ssid.trim()}". Hệ thống chưa đồng bộ xong chứng chỉ TLS.`,
        })
        setPassword('')
        await loadStatus()
        onSynced?.()
      }
    } catch {
      setFeedback({ kind: 'error', text: 'Có lỗi xảy ra khi kết nối Wi-Fi. Vui lòng thử lại.' })
    } finally {
      setSaving(false)
    }
  }

  const espData = status?.data

  return (
    <div className="wifi-modal-backdrop" role="presentation" onClick={(e) => { if (e.target === e.currentTarget && !saving) onClose() }}>
      <section className="wifi-modal" role="dialog" aria-modal="true" aria-labelledby="wifi-modal-title">
        <header className="wifi-modal-header">
          <div>
            <p className="eyebrow">CẤU HÌNH MẠNG & MÁY PHUN</p>
            <h2 id="wifi-modal-title">Kết nối Wi-Fi cho ESP32</h2>
          </div>
          <button type="button" className="wifi-close-btn" onClick={onClose} disabled={saving} aria-label="Đóng">✕</button>
        </header>

        <div className="wifi-status-grid">
          <div>
            <span>IP LAN Kiosk</span>
            <strong>{status?.hostIp || 'Đang dò…'}</strong>
            <small>{status?.hostWifiSsid ? `Wi-Fi máy tính: ${status.hostWifiSsid}` : 'Chưa rõ SSID máy tính'}</small>
          </div>
          <div>
            <span>Bo mạch ESP32 (USB)</span>
            <strong>{statusLoading ? 'Đang kiểm tra…' : status?.port ? `Đã cắm (${status.port})` : 'Chưa cắm USB'}</strong>
            <small>
              {espData
                ? `${espData.wifiConnected ? `Wi-Fi: ${espData.ssid} (${espData.ip})` : 'Chưa nối Wi-Fi'} · ${espData.mqttConnected ? 'MQTT OK' : 'MQTT offline'}`
                : (status?.error || 'Cắm USB ESP32 vào máy Kiosk để cấu hình Wi-Fi.')}
            </small>
          </div>
        </div>

        <div className="wifi-toolbar">
          <button type="button" className="secondary compact" onClick={() => void loadStatus()} disabled={statusLoading || saving}>
            {statusLoading ? 'Đang kiểm tra…' : 'Làm mới trạng thái'}
          </button>
          <button type="button" className="secondary compact" onClick={() => void scanWifi()} disabled={scanLoading || saving || !status?.port}>
            {scanLoading ? 'Đang quét Wi-Fi…' : 'Quét Wi-Fi xung quanh'}
          </button>
        </div>

        {networks.length > 0 && (
          <div className="wifi-network-list" role="listbox" aria-label="Danh sách mạng Wi-Fi">
            {networks.map((net) => (
              <button
                key={net.ssid}
                type="button"
                className={`wifi-network-item ${ssid === net.ssid ? 'active' : ''}`}
                onClick={() => setSsid(net.ssid)}
              >
                <span>{net.ssid}</span>
                <small>{net.secured ? '🔒 ' : ''}{signalLabel(net.rssi)} ({net.rssi} dBm)</small>
              </button>
            ))}
          </div>
        )}

        {hasScanned ? (
          <form className="wifi-form" onSubmit={(e) => void handleSubmit(e)}>
            <label>
              Tên mạng Wi-Fi (2.4GHz)
              <input
                type="text"
                value={ssid}
                onChange={(e) => setSsid(e.target.value)}
                placeholder="Chọn Wi-Fi ở trên hoặc nhập tên mạng"
                disabled={saving}
                required
              />
            </label>
            <label>
              Mật khẩu Wi-Fi
              <div className="wifi-password-row">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Nhập mật khẩu Wi-Fi"
                  disabled={saving}
                />
                <button type="button" className="secondary compact" onClick={() => setShowPassword((v) => !v)} disabled={saving}>
                  {showPassword ? 'Ẩn' : 'Hiện'}
                </button>
              </div>
            </label>
            <label className="wifi-checkbox">
              <input type="checkbox" checked={syncTls} onChange={(e) => setSyncTls(e.target.checked)} disabled={saving} />
              <span>Tự động đồng bộ IP LAN và chứng chỉ TLS cho QR thanh toán</span>
            </label>

            {feedback && (
              <div className={feedback.kind === 'success' ? 'wifi-feedback success' : 'error-box'} role="status">
                {feedback.text}
              </div>
            )}

            <div className="wifi-modal-actions">
              <button type="button" className="secondary" onClick={onClose} disabled={saving}>Đóng</button>
              <button type="submit" className="primary" disabled={saving || !status?.port}>
                {saving ? 'Đang kết nối & đồng bộ…' : 'Kết nối Wi-Fi & Đồng bộ'}
              </button>
            </div>
          </form>
        ) : (
          <>
            {feedback && (
              <div className={feedback.kind === 'success' ? 'wifi-feedback success' : 'error-box'} role="status">
                {feedback.text}
              </div>
            )}
            <div className="wifi-modal-actions">
              <button type="button" className="secondary" onClick={onClose} disabled={saving}>Đóng</button>
            </div>
          </>
        )}
      </section>
    </div>
  )
}

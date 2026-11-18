import { useCallback, useEffect, useMemo, useState } from 'react'
import { ApiError } from '../api/client'
import { backend } from '../api/scentStation'
import type { NotificationItem, Principal } from '../api/types'
import {
  deleteFirebaseToken,
  firebaseMessagingConfigured,
  notificationDeviceId,
  requestFirebaseToken,
  subscribeForegroundMessages,
} from '../services/firebaseMessaging'

const priorityLabels: Record<string, string> = { CRITICAL: 'Khẩn cấp', HIGH: 'Cao', NORMAL: 'Bình thường', LOW: 'Thấp' }
const typeLabels: Record<string, string> = {
  MACHINE_ALERT: 'Cảnh báo kiosk',
  SYSTEM: 'Hệ thống',
  SYSTEM_ALERT: 'Cảnh báo hệ thống',
  INVENTORY_ALERT: 'Tồn kho',
}
const errorText = (error: unknown) => error instanceof ApiError || error instanceof Error ? error.message : 'Không thể tải thông báo.'

const legacyTitles: Record<string, string> = {
  'High priority: Low stock': 'Ưu tiên cao: Nước hoa sắp hết',
  'Integration notification test': 'Kiểm thử tích hợp thông báo',
  'Machine alert': 'Cảnh báo kiosk',
}

function notificationTitle(title: string) {
  return legacyTitles[title] ?? title
}

function notificationContent(content: string) {
  if (content === 'Notification API live test') return 'Kiểm thử trực tiếp API thông báo'
  if (content === 'A machine alert requires attention.') return 'Có cảnh báo kiosk cần được xử lý.'
  const lowStock = content.match(/^Low stock on machine (\d+) at location ([^ ]+) is ([A-Z_]+)\. Severity: ([A-Z_]+)\. Alert ID: (\d+)\. Occurred at: (.+)\.$/)
  if (lowStock) {
    const [, machineId, locationId, status, severity, alertId, occurredAt] = lowStock
    const statuses: Record<string, string> = { OPEN: 'Mới', ACKNOWLEDGED: 'Đã xác nhận', ASSIGNED: 'Đã phân công', ESCALATED: 'Đã chuyển cấp', RESOLVED: 'Đã xử lý' }
    const severities: Record<string, string> = { LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao', CRITICAL: 'Nghiêm trọng' }
    return `Nước hoa sắp hết tại kiosk ${machineId}, vị trí ${locationId}. Trạng thái: ${statuses[status] ?? status}. Mức độ: ${severities[severity] ?? severity}. Mã cảnh báo: ${alertId}. Thời điểm: ${occurredAt}.`
  }
  return content
}

export function NotificationCenter({ principal, tenantId, tenantIds = [] }: { principal: Principal; tenantId?: number; tenantIds?: number[] }) {
  const platform = principal.roles.includes('PLATFORM_SUPER_ADMIN')
  const activeTenantId = tenantId ?? principal.tenantId ?? undefined
  const [availableTenantIds, setAvailableTenantIds] = useState(tenantIds)
  const tenantScopeKey = availableTenantIds.join(',')
  const scopeTenantIds = useMemo(
    () => activeTenantId ? [activeTenantId] : platform ? availableTenantIds : [],
    [activeTenantId, platform, tenantScopeKey],
  )
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<NotificationItem[]>([])
  const [unread, setUnread] = useState(0)
  const [loading, setLoading] = useState(false)
  const [pushEnabled, setPushEnabled] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (tenantIds.length) { setAvailableTenantIds(tenantIds); return }
    if (!platform || activeTenantId) return
    let current = true
    backend.tenants()
      .then((tenants) => { if (current) setAvailableTenantIds(tenants.map((tenant) => tenant.id)) })
      .catch((reason) => { if (current) setError(errorText(reason)) })
    return () => { current = false }
  }, [activeTenantId, platform, tenantIds.join(',')])

  const refresh = useCallback(async (quiet = false) => {
    if (!scopeTenantIds.length) { setItems([]); setUnread(0); return }
    if (!quiet) setLoading(true)
    try {
      const groups = await Promise.all(scopeTenantIds.map(async (scopeTenantId) => {
        const [page, count] = await Promise.all([
          backend.notifications(scopeTenantId),
          backend.unreadNotifications(scopeTenantId),
        ])
        return { page, count }
      }))
      setItems(groups.flatMap(({ page }) => page.items).sort((left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt)))
      setUnread(groups.reduce((total, { count }) => total + count.unreadCount, 0))
      setError('')
    } catch (reason) { setError(errorText(reason)) } finally { if (!quiet) setLoading(false) }
  }, [scopeTenantIds])

  useEffect(() => {
    void refresh()
    const timer = window.setInterval(() => void refresh(true), 30_000)
    return () => window.clearInterval(timer)
  }, [refresh])

  useEffect(() => {
    if (!activeTenantId || !firebaseMessagingConfigured) { setPushEnabled(false); return }
    let current = true
    setPushEnabled(false)
    backend.notificationDevice(notificationDeviceId(), activeTenantId)
      .then((registration) => { if (current) setPushEnabled(registration.active) })
      .catch((reason: unknown) => {
        if (!current) return
        if (reason instanceof ApiError && reason.status === 404) setPushEnabled(false)
        else setError(errorText(reason))
      })
    return () => { current = false }
  }, [activeTenantId])

  useEffect(() => {
    if (!activeTenantId || !firebaseMessagingConfigured) return
    let disposed = false
    let unsubscribe: (() => void) | undefined
    void subscribeForegroundMessages(() => void refresh(true))
      .then((listener) => { if (disposed) listener(); else unsubscribe = listener })
      .catch((reason: unknown) => { if (!disposed) setError(errorText(reason)) })
    return () => { disposed = true; unsubscribe?.() }
  }, [activeTenantId, refresh])

  async function markRead(item: NotificationItem) {
    const itemTenantId = Number(item.tenantId)
    if (item.readAt || !Number.isInteger(itemTenantId) || itemTenantId <= 0) return
    try { await backend.markNotificationRead(item.id, itemTenantId); await refresh(true) }
    catch (reason) { setError(errorText(reason)) }
  }

  async function markAllRead() {
    const grouped = new Map<number, number[]>()
    for (const item of items.filter((candidate) => !candidate.readAt)) {
      const itemTenantId = Number(item.tenantId)
      if (!Number.isInteger(itemTenantId) || itemTenantId <= 0) continue
      grouped.set(itemTenantId, [...(grouped.get(itemTenantId) ?? []), item.id])
    }
    if (!grouped.size) return
    try { await Promise.all([...grouped].map(([scopeTenantId, notificationIds]) => backend.markNotificationsRead(notificationIds, scopeTenantId))); await refresh(true) }
    catch (reason) { setError(errorText(reason)) }
  }

  async function enablePush() {
    if (!activeTenantId) return
    setLoading(true)
    try {
      const { deviceId, token } = await requestFirebaseToken()
      await backend.registerNotificationDevice({ tenantId: String(activeTenantId), deviceId, firebaseToken: token, platform: 'WEB' })
      setPushEnabled(true); setError('')
    } catch (reason) { setError(errorText(reason)) } finally { setLoading(false) }
  }

  async function disablePush() {
    if (!activeTenantId) return
    setLoading(true)
    try {
      await backend.revokeNotificationDevice(notificationDeviceId(), activeTenantId)
      setPushEnabled(false)
      await deleteFirebaseToken().catch(() => undefined)
      setError('')
    } catch (reason) { setError(errorText(reason)) } finally { setLoading(false) }
  }

  const allTenants = platform && !activeTenantId
  const title = allTenants ? 'Thông báo trên toàn nền tảng' : activeTenantId ? 'Thông báo vận hành' : 'Chưa có phạm vi thương hiệu'
  return <div className="notification-center">
    <button className="notification-trigger" title={title} disabled={!scopeTenantIds.length} onClick={() => setOpen((value) => !value)} aria-label={title} aria-expanded={open}>
      <span aria-hidden="true">◈</span>{unread > 0 && <strong>{unread > 99 ? '99+' : unread}</strong>}
    </button>
    {open && <div className="notification-popover">
      <div className="notification-heading"><div><h3>Thông báo</h3><small>{unread} chưa đọc · {allTenants ? `${scopeTenantIds.length} thương hiệu` : 'thương hiệu đang chọn'}</small></div><button onClick={() => setOpen(false)} aria-label="Đóng">×</button></div>
      <div className="notification-tools">
        <button onClick={() => void refresh()} disabled={loading}>↻ Làm mới</button>
        <button onClick={() => void markAllRead()} disabled={!unread}>Đánh dấu đã đọc</button>
        {firebaseMessagingConfigured && activeTenantId && <button onClick={() => void (pushEnabled ? disablePush() : enablePush())} disabled={loading}>{pushEnabled ? 'Tắt thông báo đẩy' : 'Bật thông báo đẩy'}</button>}
      </div>
      {error && <p className="notification-error">{error}</p>}
      <div className="notification-list">
        {loading && !items.length ? <div className="notification-empty"><span className="spinner" /> Đang tải…</div> : !items.length ? <div className="notification-empty">Chưa có thông báo trong phạm vi đang xem.</div> : items.map((item) => <button key={`${item.tenantId}-${item.id}`} className={`notification-item ${item.readAt ? '' : 'unread'} ${item.priority.toLowerCase()}`} onClick={() => void markRead(item)}>
          <span className="notification-status" />
          <span><span className="notification-meta">{typeLabels[item.notificationType] ?? item.notificationType} · {priorityLabels[item.priority] ?? item.priority}</span><strong>{notificationTitle(item.title)}</strong><span>{notificationContent(item.content)}</span><time>{new Date(item.createdAt).toLocaleString('vi-VN')}</time></span>
        </button>)}
      </div>
      {!firebaseMessagingConfigured && <p className="notification-hint">Thông báo trong ứng dụng đang hoạt động. Cấu hình Firebase Web để bật thông báo đẩy trên trình duyệt.</p>}
      {firebaseMessagingConfigured && allTenants && <p className="notification-hint">Chọn một thương hiệu cụ thể để bật hoặc tắt thông báo đẩy trên trình duyệt.</p>}
    </div>}
  </div>
}

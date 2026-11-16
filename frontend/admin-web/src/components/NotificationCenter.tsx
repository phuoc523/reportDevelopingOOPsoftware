import { useCallback, useEffect, useState } from 'react'
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
const typeLabels: Record<string, string> = { MACHINE_ALERT: 'Cảnh báo kiosk', SYSTEM: 'Hệ thống', INVENTORY_ALERT: 'Tồn kho' }
const errorText = (error: unknown) => error instanceof ApiError || error instanceof Error ? error.message : 'Không thể tải thông báo.'

export function NotificationCenter({ principal, tenantId }: { principal: Principal; tenantId?: number }) {
  const activeTenantId = tenantId ?? principal.tenantId ?? undefined
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<NotificationItem[]>([])
  const [unread, setUnread] = useState(0)
  const [loading, setLoading] = useState(false)
  const [pushEnabled, setPushEnabled] = useState(false)
  const [error, setError] = useState('')

  const refresh = useCallback(async (quiet = false) => {
    if (!activeTenantId) { setItems([]); setUnread(0); return }
    if (!quiet) setLoading(true)
    try {
      const [page, count] = await Promise.all([
        backend.notifications(activeTenantId),
        backend.unreadNotifications(activeTenantId),
      ])
      setItems(page.items)
      setUnread(count.unreadCount)
      setError('')
    } catch (reason) { setError(errorText(reason)) } finally { if (!quiet) setLoading(false) }
  }, [activeTenantId])

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
    if (item.readAt || !activeTenantId) return
    try { await backend.markNotificationRead(item.id, activeTenantId); await refresh(true) }
    catch (reason) { setError(errorText(reason)) }
  }

  async function markAllRead() {
    const ids = items.filter((item) => !item.readAt).map((item) => item.id)
    if (!ids.length || !activeTenantId) return
    try { await backend.markNotificationsRead(ids, activeTenantId); await refresh(true) }
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

  const title = activeTenantId ? 'Thông báo vận hành' : 'Chọn một thương hiệu để xem thông báo'
  return <div className="notification-center">
    <button className="notification-trigger" title={title} disabled={!activeTenantId} onClick={() => setOpen((value) => !value)} aria-label={title} aria-expanded={open}>
      <span aria-hidden="true">♢</span>{unread > 0 && <strong>{unread > 99 ? '99+' : unread}</strong>}
    </button>
    {open && <div className="notification-popover">
      <div className="notification-heading"><div><h3>Thông báo</h3><small>{unread} thông báo chưa đọc</small></div><button onClick={() => setOpen(false)} aria-label="Đóng">×</button></div>
      <div className="notification-tools">
        <button onClick={() => void refresh()} disabled={loading}>↻ Làm mới</button>
        <button onClick={() => void markAllRead()} disabled={!unread}>Đánh dấu đã đọc</button>
        {firebaseMessagingConfigured && <button onClick={() => void (pushEnabled ? disablePush() : enablePush())} disabled={loading}>{pushEnabled ? 'Tắt thông báo đẩy' : 'Bật thông báo đẩy'}</button>}
      </div>
      {error && <p className="notification-error">{error}</p>}
      <div className="notification-list">
        {loading && !items.length ? <div className="notification-empty"><span className="spinner" /> Đang tải…</div> : !items.length ? <div className="notification-empty">Chưa có thông báo cho thương hiệu này.</div> : items.map((item) => <button key={item.id} className={`notification-item ${item.readAt ? '' : 'unread'} ${item.priority.toLowerCase()}`} onClick={() => void markRead(item)}>
          <span className="notification-status" />
          <span><span className="notification-meta">{typeLabels[item.notificationType] ?? item.notificationType} · {priorityLabels[item.priority] ?? item.priority}</span><strong>{item.title}</strong><span>{item.content}</span><time>{new Date(item.createdAt).toLocaleString('vi-VN')}</time></span>
        </button>)}
      </div>
      {!firebaseMessagingConfigured && <p className="notification-hint">Thông báo trong ứng dụng đang hoạt động. Cấu hình Firebase Web để bật thông báo đẩy trên trình duyệt.</p>}
    </div>}
  </div>
}

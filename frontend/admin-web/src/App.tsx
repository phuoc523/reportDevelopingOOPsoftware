import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import './Admin.css'
import { ApiError, authToken, clearApiCache } from './api/client'
import { backend } from './api/scentStation'
import type { Account, Alert, Bottle, BottleInstallation, Campaign, InventoryBatch, Location, Machine, MaintenanceTicket, Order, Payment, PaymentReview, Permission, Principal, Product, PublicTenantSummary, Role, Slot, Tenant } from './api/types'
import { CampaignForm, ProductPriceForm } from './components/CatalogForms'
import { AccountSettingsModal, TenantForm, UserForm } from './components/IdentityForms'
import { LocationForm, MachineForm, SlotForm } from './components/MachineForms'
import { InventoryDisposeForm, InventoryForm, InventoryWorkflowForm, MaintenanceForm, MaintenanceWorkflowForm } from './components/OperationForms'
import type { InventoryDisposeTarget, InventoryWorkflowPrefill } from './components/OperationForms'
import { NotificationCenter } from './components/NotificationCenter'
import { ReportingTools } from './components/ReportingTools'
import type { TransactionFilters } from './components/ReportingTools'

type Page = 'dashboard' | 'tenants' | 'users' | 'products' | 'campaigns' | 'machines' | 'inventory' | 'alerts' | 'maintenance' | 'orders' | 'transactions' | 'audit'
type Row = Record<string, unknown>

type DashboardPeriod = 'day' | 'week' | 'month'

function periodRange(period: DashboardPeriod) {
  const now = new Date()
  const y = now.getFullYear(); const m = now.getMonth(); const d = now.getDate()
  const pad = (n: number) => String(n).padStart(2, '0')
  const fmt = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  if (period === 'day') {
    const start = new Date(y, m, d, 0, 0, 0, 0); const end = new Date(y, m, d, 23, 59, 59, 999)
    return { start, end, startDate: fmt(start), endDate: fmt(end) }
  }
  if (period === 'week') {
    const offset = (now.getDay() + 6) % 7
    const start = new Date(y, m, d - offset, 0, 0, 0, 0)
    const end = new Date(y, m, d - offset + 6, 23, 59, 59, 999)
    return { start, end, startDate: fmt(start), endDate: fmt(end) }
  }
  const start = new Date(y, m, 1, 0, 0, 0, 0); const end = new Date(y, m + 1, 0, 23, 59, 59, 999)
  return { start, end, startDate: fmt(start), endDate: fmt(end) }
}

function transactionSummaryForTenant(payments: Payment[], orders: Order[], tenantId: number | undefined, period: DashboardPeriod) {
  const { start, end } = periodRange(period)
  const tenantOrderIds = new Set(orders.filter((order) => tenantId === undefined || String(order.tenantId) === String(tenantId)).map((order) => order.id))
  const scoped = payments.filter((payment) => {
    const createdAt = new Date(payment.createdAt)
    return tenantOrderIds.has(payment.orderId) && createdAt >= start && createdAt <= end
  })
  const hasStatus = (payment: Payment, status: string) => payment.status.toUpperCase() === status
  return {
    totalTransactions: scoped.length,
    successfulTransactions: scoped.filter((payment) => hasStatus(payment, 'PAID')).length,
    pendingTransactions: scoped.filter((payment) => hasStatus(payment, 'PENDING')).length,
    failedTransactions: scoped.filter((payment) => hasStatus(payment, 'FAILED')).length,
    expiredTransactions: scoped.filter((payment) => hasStatus(payment, 'EXPIRED')).length,
    refundedTransactions: scoped.filter((payment) => hasStatus(payment, 'REFUNDED')).length,
  }
}

function aggregateOperationalReports(reports: Row[], startDate: string, endDate: string): Row | null {
  if (!reports.length) return null
  if (reports.length === 1) return reports[0]
  const num = (obj: Row | null | undefined, key: string) => Number(obj?.[key] ?? 0)
  const arr = (obj: Row | null | undefined, key: string) => (Array.isArray(obj?.[key]) ? (obj![key] as unknown[]) : [])
  let totalSprays = 0; let customerOk = 0; let diagOk = 0; let failedSprays = 0; let unconfirmed = 0; let paidNoSpray = 0
  const unpaidIds: string[] = []
  let totalRemaining = 0; let refills = 0; let replacements = 0; let adjustments = 0
  const refillCandidates: unknown[] = []; const abnormalConsumptions: unknown[] = []
  let overdueTickets = 0; let ackSum = 0; let ackCount = 0; let resSum = 0; let resCount = 0; let downtime = 0; let alertDowntime = 0
  for (const report of reports) {
    const s = (report.sprays ?? {}) as Row
    totalSprays += num(s, 'total'); customerOk += num(s, 'customerSuccessful'); diagOk += num(s, 'diagnosticSuccessful')
    failedSprays += num(s, 'failed'); unconfirmed += num(s, 'unconfirmed'); paidNoSpray += num(s, 'paidWithoutSprayConfirmation')
    unpaidIds.push(...(arr(s, 'paidOrderIdsWithoutSprayConfirmation') as string[]))
    const inv = (report.inventory ?? {}) as Row
    totalRemaining += num(inv, 'totalRemaining'); refills += num(inv, 'refills'); replacements += num(inv, 'replacements'); adjustments += num(inv, 'adjustments')
    refillCandidates.push(...arr(inv, 'refillCandidates')); abnormalConsumptions.push(...arr(inv, 'abnormalConsumptions'))
    const inc = (report.incidents ?? {}) as Row
    overdueTickets += num(inc, 'overdueMaintenanceTickets'); downtime += num(inc, 'downtimeHours'); alertDowntime += num(inc, 'alertDowntimeHours')
    const ack = num(inc, 'averageAcknowledgeHours'); if (ack > 0) { ackSum += ack; ackCount += 1 }
    const res = num(inc, 'averageAlertResolutionHours'); if (res > 0) { resSum += res; resCount += 1 }
  }
  const okTotal = customerOk + diagOk
  return {
    startDate, endDate,
    sprays: {
      total: totalSprays, customerSuccessful: customerOk, diagnosticSuccessful: diagOk,
      failed: failedSprays, unconfirmed, paidWithoutSprayConfirmation: paidNoSpray,
      paidOrderIdsWithoutSprayConfirmation: unpaidIds,
      successRate: totalSprays > 0 ? Math.round((okTotal * 1000) / totalSprays) / 10 : 0,
    },
    inventory: {
      totalRemaining: Math.round(totalRemaining * 100) / 100, refills, replacements, adjustments,
      refillCandidates, abnormalConsumptions,
    },
    incidents: {
      overdueMaintenanceTickets: overdueTickets,
      averageAcknowledgeHours: ackCount ? Math.round((ackSum / ackCount) * 10) / 10 : 0,
      averageAlertResolutionHours: resCount ? Math.round((resSum / resCount) * 10) / 10 : 0,
      downtimeHours: Math.round(downtime * 10) / 10,
      alertDowntimeHours: Math.round(alertDowntime * 10) / 10,
    },
  }
}

const pages: Array<{ id: Page; label: string; icon: string; permissions?: Permission[] }> = [
  { id: 'dashboard', label: 'Tổng quan', icon: '▦', permissions: ['REPORT_READ', 'PLATFORM_REPORT_READ'] },
  { id: 'tenants', label: 'Thương hiệu', icon: '◇', permissions: ['TENANT_MANAGE', 'BRAND_PROFILE_READ'] },
  { id: 'users', label: 'Tài khoản & phân quyền', icon: '♙', permissions: ['BRAND_USER_MANAGE'] },
  { id: 'products', label: 'Sản phẩm', icon: '◈', permissions: ['CATALOG_READ'] },
  { id: 'campaigns', label: 'Chiến dịch', icon: '◷', permissions: ['CATALOG_READ', 'CAMPAIGN_MANAGE'] },
  { id: 'machines', label: 'Kiosk', icon: '▣', permissions: ['MACHINE_READ'] },
  { id: 'inventory', label: 'Kho', icon: '▤', permissions: ['INVENTORY_READ'] },
  { id: 'alerts', label: 'Cảnh báo', icon: '⚑', permissions: ['ALERT_READ'] },
  { id: 'maintenance', label: 'Bảo trì', icon: '⚙', permissions: ['MAINTENANCE_READ'] },
  { id: 'orders', label: 'Đơn hàng & thanh toán', icon: '▧', permissions: ['ORDER_READ'] },
  { id: 'transactions', label: 'Giao dịch & báo cáo', icon: '▥', permissions: ['REPORT_READ', 'PLATFORM_REPORT_READ'] },
  { id: 'audit', label: 'Nhật ký hệ thống', icon: '≡', permissions: ['AUDIT_READ', 'PLATFORM_AUDIT_READ'] },
]

const labels: Record<string, string> = {
  id: 'ID', kind: 'Loại dữ liệu', name: 'Tên', code: 'Mã', status: 'Trạng thái', tenantId: 'Mã thương hiệu', tenantName: 'Thương hiệu', productId: 'Mã sản phẩm', productName: 'Sản phẩm', machineId: 'Máy',
  username: 'Tài khoản', fullName: 'Họ tên', email: 'Email', roles: 'Vai trò', active: 'Hoạt động', locked: 'Đã khóa', pendingApproval: 'Chờ duyệt vai trò',
  defaultPrice: 'Phí trải nghiệm', bottlePrice: 'Giá bán chai', bottleVolumeMl: 'Dung tích chai (ml)', bottleStock: 'Tồn chai bán lẻ', orderType: 'Loại đơn', available: 'Khả dụng', fragranceNotes: 'Nhóm hương', serialNumber: 'Serial', locationId: 'Địa điểm', machineName: 'Kiosk',
  operationalStatus: 'Vận hành', deviceRegistrationStatus: 'Đăng ký thiết bị', slotNumber: 'Đầu phun', slotCode: 'Mã đầu phun', cartridgeId: 'Mã hộp hương', doseMl: 'Lưu lượng (ml/lượt)', installedBottleCode: 'Chai đang lắp', remainingMl: 'Còn lại (ml)', estimatedSprays: 'Lượt xịt ước tính', stockThreshold: 'Ngưỡng tồn', price: 'Giá xịt thử', locationName: 'Địa điểm', imageUrl: 'Hình ảnh',
  summary: 'Nội dung', severity: 'Mức độ', type: 'Loại', title: 'Tiêu đề', priority: 'Ưu tiên', critical: 'Khẩn cấp',
  totalAmount: 'Tổng tiền', currency: 'Tiền tệ', reference: 'Tham chiếu', orderId: 'Đơn hàng', amount: 'Số tiền', items: 'Sản phẩm',
  createdAt: 'Tạo lúc', updatedAt: 'Cập nhật', lastSeenAt: 'Gần nhất', occurredAt: 'Thời gian', action: 'Hành động', outcome: 'Kết quả',
  totalTransactions: 'Tổng giao dịch', successfulTransactions: 'Thành công', pendingTransactions: 'Đang chờ', failedTransactions: 'Thất bại', expiredTransactions: 'Hết hạn', refundedTransactions: 'Đã hoàn tiền',
  total: 'Tổng số', online: 'Trực tuyến', offline: 'Ngoại tuyến', unstable: 'Không ổn định', disabled: 'Đã tắt', maintenance: 'Đang bảo trì', error: 'Lỗi', unknown: 'Không xác định',
  open: 'Đang mở', acknowledged: 'Đã xác nhận', assigned: 'Đã phân công', escalated: 'Đã chuyển cấp', resolved: 'Đã xử lý',
  bottles: 'Tổng số chai', installed: 'Đã lắp', nearlyEmpty: 'Sắp hết', empty: 'Đã hết', damaged: 'Hư hỏng',
  batchCode: 'Mã lô', bottleCode: 'Mã chai',
  initialQuantity: 'Dung tích ban đầu (ml)', currentQuantity: 'Tổng dung tích trong kho (ml)', calibratedConsumptionPerSpray: 'Mỗi lượt xịt (ml)', quantityBefore: 'Dung tích trước (ml)', quantityAfter: 'Dung tích sau (ml)',
  contactPhone: 'Điện thoại', machineCount: 'Số máy', onlineMachineCount: 'Máy online', adminCount: 'Quản trị viên', assignedTo: 'Người xử lý', assignedToActorId: 'Kỹ thuật viên', dispensingStatus: 'Kết quả phun',
  dueAt: 'Hạn xử lý', receivedAt: 'Ngày nhập', expiryDate: 'Hạn sử dụng', slotId: 'Slot', movementType: 'Loại biến động', reason: 'Lý do', reconciliationFlag: 'Đối soát phun', manualReviewRequired: 'Cần đối soát thủ công', manualReviewReason: 'Lý do đối soát', resolvedAt: 'Xử lý lúc', paidAt: 'Thanh toán lúc', contactEmail: 'Email liên hệ', approved: 'Duyệt phiếu',
}

const titles: Record<Page, [string, string]> = {
  dashboard: ['Tổng quan vận hành', 'Số liệu tổng hợp theo thời gian thực từ các dịch vụ'], tenants: ['Thương hiệu', 'Hồ sơ và trạng thái hoạt động của từng thương hiệu'],
  users: ['Tài khoản & phân quyền', 'Quản lý vai trò, quyền bổ sung và phạm vi vận hành'], products: ['Danh mục sản phẩm', 'Sản phẩm nước hoa theo từng thương hiệu'],
  campaigns: ['Chiến dịch khuyến mãi', 'Mức giảm giá và thời gian áp dụng theo sản phẩm'], machines: ['Kiosk', 'Trạng thái kết nối, địa điểm lắp đặt và quản lý đầu phun'],
  inventory: ['Kho', 'Lô nhập, chai nước hoa và lịch sử biến động kho'], alerts: ['Cảnh báo', 'Theo dõi và xử lý sự cố thiết bị'],
  maintenance: ['Bảo trì', 'Phiếu bảo trì theo phạm vi vận hành'], orders: ['Đơn hàng & thanh toán', 'Theo dõi đơn hàng, giao dịch thanh toán và đối soát'],
  transactions: ['Giao dịch & báo cáo', 'Dữ liệu giao dịch được đồng bộ từ dịch vụ đơn hàng'], audit: ['Nhật ký hệ thống', 'Lịch sử thao tác được backend ghi nhận'],
}

const valueLabels: Record<string, string> = {
  TRIAL: 'Trải nghiệm xịt thử', BOTTLE: 'Mua chai',
  ACTIVE: 'Đang hoạt động', SUSPENDED: 'Tạm ngưng', ONLINE: 'Trực tuyến', OFFLINE: 'Ngoại tuyến', UNSTABLE: 'Không ổn định', DISABLED: 'Đã tắt', ERROR: 'Lỗi', MAINTENANCE: 'Đang bảo trì',
  AVAILABLE: 'Sẵn sàng', INSTALLED: 'Đã lắp', NEARLY_EMPTY: 'Sắp hết', EMPTY: 'Đã hết', DAMAGED: 'Hư hỏng', EXPIRED: 'Hết hạn',
  INSTALL: 'Lắp vào máy', REMOVE: 'Tháo khỏi máy', REPLACE: 'Thay chai', REFILL: 'Nạp thêm dung dịch', BATCH_RECEIVE: 'Nhập lô hàng', BATCH_CANCEL: 'Hủy lô hàng', CUSTOMER_CONSUMPTION: 'Khách phun', DIAGNOSTIC_CONSUMPTION: 'Phun kiểm tra', DISPOSE: 'Hủy / thanh lý', ADJUSTMENT: 'Điều chỉnh',
  OPEN: 'Đang mở', ACKNOWLEDGED: 'Đã xác nhận', ASSIGNED: 'Đã phân công', ESCALATED: 'Đã chuyển cấp', RESOLVED: 'Đã xử lý',
  PENDING: 'Đang chờ', PAID: 'Đã thanh toán', FAILED: 'Thất bại', CONFIRMED: 'Đã xác nhận', DISPENSED: 'Đã phun', PROCESSING: 'Đang xử lý', SHIPPED: 'Đang giao', DELIVERED: 'Hoàn tất', CANCELLED: 'Đã hủy', REFUNDED: 'Đã hoàn tiền',
  LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao', CRITICAL: 'Nghiêm trọng', URGENT: 'Khẩn cấp',
  PLATFORM_SUPER_ADMIN: 'Quản trị nền tảng', BRAND_ADMIN: 'Quản trị thương hiệu', OPERATIONS_MANAGER: 'Quản lý vận hành', TECHNICIAN: 'Kỹ thuật viên', INVENTORY_STAFF: 'Nhân viên kho', CUSTOMER: 'Khách hàng',
  BATCH: 'Lô nhập', MOVEMENT: 'Biến động kho', ORDER: 'Đơn hàng', PAYMENT: 'Thanh toán', REVIEW: 'Đối soát', MACHINE: 'Kiosk', SLOT: 'Đầu phun',
  PERCENTAGE: 'Theo phần trăm', FIXED_AMOUNT: 'Số tiền cố định', SUCCESS: 'Thành công',
  'local-copy': 'Dữ liệu đồng bộ nội bộ', 'live-tenant-payments': 'Thanh toán trực tiếp theo phạm vi', ok: 'Hoạt động', unavailable: 'Không khả dụng',
  'skipped-missing-tenant': 'Bỏ qua: chưa chọn thương hiệu', 'skipped-invalid-tenant': 'Bỏ qua: mã thương hiệu không hợp lệ',
  'skipped-non-numeric-tenant': 'Bỏ qua: mã thương hiệu không phải số',
  'unsupported-missing-list-api': 'Chưa hỗ trợ: thiếu API danh sách',
  'unsupported-tenant-transaction-schema': 'Chưa hỗ trợ giao dịch theo thương hiệu',
}

const can = (principal: Principal, required?: Permission[]) => !required || principal.roles.includes('PLATFORM_SUPER_ADMIN') || required.some((permission) => principal.grants.includes(permission))
const errorText = (error: unknown) => error instanceof ApiError || error instanceof Error ? error.message : 'Không thể hoàn thành yêu cầu.'
function display(value: unknown, key = ''): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return key === 'approved' ? (value ? 'Đã chấp nhận' : 'Chờ chấp nhận') : (value ? 'Có' : 'Không')
  if (Array.isArray(value)) return value.map((item) => typeof item === 'object' ? JSON.stringify(item) : (valueLabels[String(item)] ?? String(item))).join(', ') || '—'
  if (typeof value === 'object') return JSON.stringify(value)
  if (/price|amount|revenue|quantity/i.test(key) && !Number.isNaN(Number(value))) return new Intl.NumberFormat('vi-VN').format(Number(value))
  if (/At$|time|date/i.test(key) && typeof value === 'string') { const date = new Date(value); if (!Number.isNaN(date.getTime())) return date.toLocaleString('vi-VN') }
  if ((key === 'kind' || key === 'recordType') && value === 'BOTTLE') return 'Chai nước hoa'
  return valueLabels[String(value)] ?? String(value)
}

function Login({ done, sessionNotice }: { done: (principal: Principal) => void; sessionNotice?: string }) {
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login')
  const [registerStep, setRegisterStep] = useState<1 | 2>(1)
  const [fullName, setFullName] = useState(''); const [email, setEmail] = useState('')
  const [username, setUsername] = useState(''); const [password, setPassword] = useState(''); const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [loading, setLoading] = useState(false)

  function handleNextStep(event: FormEvent) {
    event.preventDefault(); setError('')
    if (!fullName.trim() || !email.trim()) { setError('Vui lòng nhập đầy đủ họ tên và email.'); return }
    setRegisterStep(2)
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setNotice(''); setLoading(true)
    try {
      if (authMode === 'login') {
        await backend.login(username.trim(), password)
        done(await backend.me())
      } else {
        if (password.length < 4) { setError('Mật khẩu phải có ít nhất 4 ký tự.'); setLoading(false); return }
        if (password !== confirmPassword) { setError('Mật khẩu xác nhận không khớp.'); setLoading(false); return }
        await backend.registerStaffAccount({ username: username.trim(), email: email.trim(), password, fullName: fullName.trim() })
        setNotice('Đăng ký tài khoản nhân viên thành công! Hãy đăng nhập để gửi phiếu yêu cầu thương hiệu và chức vụ cho Quản trị thương hiệu.')
        setPassword(''); setConfirmPassword(''); setRegisterStep(1); setAuthMode('login')
      }
    } catch (reason) {
      if (authMode === 'login') authToken.clear()
      setError(errorText(reason))
    } finally { setLoading(false) }
  }

  return <main className="login-shell"><section className="login-art"><div className="brand-mark">S.</div><p>SCENTSTATION</p><h1>Vận hành mọi điểm chạm hương thơm.</h1><span>Trung tâm quản trị dành cho nhân sự &amp; thương hiệu</span></section><section className="login-panel"><form onSubmit={authMode === 'register' && registerStep === 1 ? handleNextStep : submit} className="login-card"><p className="eyebrow">CỔNG QUẢN TRỊ &amp; NHÂN VIÊN</p><h2>{authMode === 'login' ? 'Đăng nhập' : registerStep === 1 ? 'Đăng ký nhân viên (Bước 1/2)' : 'Tạo tài khoản (Bước 2/2)'}</h2><nav className="order-tabs" style={{ justifyContent: 'flex-start' }}><button type="button" className={authMode === 'login' ? 'active' : ''} onClick={() => { setAuthMode('login'); setError('') }}>Đăng nhập</button><button type="button" className={authMode === 'register' ? 'active' : ''} onClick={() => { setAuthMode('register'); setRegisterStep(1); setError(''); setNotice('') }}>Đăng ký nhân viên</button></nav>{sessionNotice && !error && !notice && <div className="error-box">{sessionNotice}</div>}{notice && <div className="notice">✓ {notice}</div>}{authMode === 'login' ? (<><label>Tên đăng nhập<input autoFocus required value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" /></label><label>Mật khẩu<input required type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" /></label></>) : registerStep === 1 ? (<><label>Họ và tên<input autoFocus required maxLength={120} placeholder="Nhập họ tên của bạn" value={fullName} onChange={(event) => setFullName(event.target.value)} /></label><label>Email<input required type="email" maxLength={160} placeholder="email@example.com" value={email} onChange={(event) => setEmail(event.target.value)} /></label></>) : (<><small style={{ color: '#6e7872' }}>Thông tin: <strong>{fullName}</strong> ({email})</small><label>Tên đăng nhập<input autoFocus required minLength={3} maxLength={80} value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" /></label><label>Mật khẩu (tối thiểu 4 ký tự)<input required type="password" minLength={4} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" /></label><label>Xác nhận mật khẩu<input required type="password" minLength={4} maxLength={72} value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} autoComplete="new-password" /></label></>)}{error && <div className="error-box">{error}</div>}<div style={{ display: 'flex', gap: 10 }}>{authMode === 'register' && registerStep === 2 && <button type="button" style={{ flex: '0 0 auto' }} onClick={() => { setRegisterStep(1); setError('') }}>← Quay lại</button>}<button className="primary" style={{ flex: 1 }} disabled={loading}>{loading ? 'Đang xử lý…' : authMode === 'login' ? 'Đăng nhập' : registerStep === 1 ? 'Tiếp tục →' : 'Hoàn tất đăng ký'}</button></div></form></section></main>
}

function StaffOnboarding({ principal, refreshPrincipal, exit }: { principal: Principal; refreshPrincipal: (next: Principal) => void; exit: () => void }) {
  const [tenants, setTenants] = useState<PublicTenantSummary[]>([])
  const [tenantId, setTenantId] = useState(principal.tenantId ? String(principal.tenantId) : '')
  const [requestedRole, setRequestedRole] = useState<Role>('OPERATIONS_MANAGER')
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('')
  const [showAccountSettings, setShowAccountSettings] = useState(false)
  const isPending = Boolean(principal.tenantId && principal.grants.length === 0)

  useEffect(() => {
    void backend.publicTenants().then((list) => {
      setTenants(list)
      if (!tenantId && list[0]) setTenantId(String(list[0].id))
    }).catch(() => undefined)
  }, [])

  async function submitRequest(event: FormEvent) {
    event.preventDefault(); setError(''); setNotice(''); setBusy(true)
    try {
      const res = await backend.submitStaffRoleRequest({ tenantId: Number(tenantId), requestedRole })
      setNotice(res.message)
      refreshPrincipal(await backend.me())
    } catch (reason) {
      setError(errorText(reason))
    } finally { setBusy(false) }
  }

  async function recheck() {
    setBusy(true); setError('')
    try {
      const latest = await backend.me()
      refreshPrincipal(latest)
      if (!latest.grants.length) setNotice('Phiếu yêu cầu vẫn đang chờ Quản trị thương hiệu phê duyệt.')
    } catch (reason) { setError(errorText(reason)) } finally { setBusy(false) }
  }

  async function logout() { try { await backend.logout() } catch { /* ignore */ } clearApiCache(); authToken.clear(); exit() }

  const brandName = tenants.find((t) => String(t.id) === String(principal.tenantId))?.name ?? (principal.tenantId ? `Thương hiệu #${principal.tenantId}` : '—')
  return (
    <main className="login-shell">
      <section className="login-art">
        <div className="brand-mark">S.</div>
        <p>SCENTSTATION</p>
        <h1>Phiếu yêu cầu gia nhập thương hiệu.</h1>
        <span>Xin chào <strong>{principal.username}</strong> — hoàn tất thông tin làm việc để Quản trị thương hiệu phê duyệt vai trò.</span>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <p className="eyebrow">YÊU CẦU CẤP VAI TRÒ NHÂN VIÊN</p>
          <h2>{isPending ? 'Đang chờ phê duyệt' : 'Gửi phiếu yêu cầu'}</h2>
          {notice && <div className="notice">✓ {notice}</div>}
          {error && <div className="error-box">{error}</div>}
          {isPending ? (
            <>
              <div className="slot-sync-box">
                <p style={{ margin: '0 0 6px' }}><strong>Tài khoản:</strong> {principal.username}</p>
                <p style={{ margin: '0 0 6px' }}><strong>Thương hiệu:</strong> {brandName}</p>
                <p style={{ margin: 0 }}><strong>Chức vụ đề nghị:</strong> {principal.roles.map((r) => valueLabels[r] ?? r).join(', ')}</p>
              </div>
              <p style={{ margin: 0, color: '#6e7872', fontSize: 13 }}>Phiếu yêu cầu đã được gửi tới Quản trị thương hiệu. Sau khi được phê duyệt, bạn có thể vào hệ thống làm việc ngay.</p>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                <button type="button" className="primary" style={{ flex: 1 }} disabled={busy} onClick={() => void recheck()}>{busy ? 'Đang kiểm tra…' : '↻ Kiểm tra trạng thái duyệt'}</button>
                <button type="button" onClick={() => setShowAccountSettings(true)}>⚙ Tài khoản</button>
                <button type="button" onClick={() => void logout()}>Đăng xuất</button>
              </div>
            </>
          ) : (
            <form onSubmit={submitRequest} style={{ display: 'grid', gap: 14 }}>
              <label>
                Tên thương hiệu
                <select required value={tenantId} onChange={(e) => setTenantId(e.target.value)}>
                  <option value="">— Chọn thương hiệu —</option>
                  {tenants.map((t) => <option key={t.id} value={String(t.id)}>{t.name} ({t.code})</option>)}
                </select>
              </label>
              <label>
                Chức vụ / vai trò đề nghị
                <select required value={requestedRole} onChange={(e) => setRequestedRole(e.target.value as Role)}>
                  <option value="OPERATIONS_MANAGER">Quản lý vận hành</option>
                  <option value="TECHNICIAN">Kỹ thuật viên</option>
                  <option value="INVENTORY_STAFF">Nhân viên kho</option>
                </select>
              </label>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                <button className="primary" style={{ flex: 1 }} disabled={busy || !tenantId}>{busy ? 'Đang gửi…' : 'Gửi phiếu yêu cầu'}</button>
                <button type="button" onClick={() => setShowAccountSettings(true)}>⚙ Tài khoản</button>
                <button type="button" onClick={() => void logout()}>Đăng xuất</button>
              </div>
            </form>
          )}
        </div>
      </section>
      {showAccountSettings && <AccountSettingsModal principal={principal} brandCode={tenants.find((t) => String(t.id) === String(principal.tenantId || tenantId))?.code} close={() => setShowAccountSettings(false)} savedUsername={(next, message) => { refreshPrincipal(next); setNotice(message) }} savedPassword={(message) => setNotice(message)} />}
    </main>
  )
}

function Table({ rows, columns, action }: { rows: Row[]; columns: string[]; action: (row: Row) => React.ReactNode }) {
  if (!rows.length) return <div className="empty">Không có dữ liệu phù hợp từ backend.</div>
  const renderedActions = rows.map(action); const hasActions = renderedActions.some(Boolean)
  return <div className="table-wrap"><table><thead><tr>{columns.map((column) => <th key={column}>{labels[column] ?? column}</th>)}{hasActions && <th>Thao tác</th>}</tr></thead><tbody>{rows.map((row, index) => <tr key={String(row.id ?? row.reference ?? row.code ?? index)}>{columns.map((column) => <td key={column} title={column === 'imageUrl' ? undefined : display(row[column], column)}>{column === 'imageUrl' && typeof row[column] === 'string' ? <img className="product-thumb" src={String(row[column])} alt="Ảnh sản phẩm" /> : column === 'status' || column.endsWith('Status') ? <span className={`badge ${String(row[column]).toLowerCase()}`}>{display(row[column], column)}</span> : display(row[column], column)}</td>)}{hasActions && <td className="actions">{renderedActions[index]}</td>}</tr>)}</tbody></table></div>
}

function Dashboard({ data, operational, period, setPeriod, principal }: { data: Row | null; operational: Row | null; period: DashboardPeriod; setPeriod: (p: DashboardPeriod) => void; principal: Principal }) {
  const roleHint = principal.roles.includes('PLATFORM_SUPER_ADMIN')
    ? 'Góc nhìn Quản trị nền tảng: tổng hợp doanh thu, máy, sự cố và vận hành toàn hệ thống.'
    : principal.roles.includes('BRAND_ADMIN')
      ? 'Góc nhìn Quản trị thương hiệu: doanh thu, máy, tồn kho và hiệu suất vận hành của thương hiệu.'
      : principal.roles.includes('OPERATIONS_MANAGER')
        ? 'Góc nhìn Quản lý vận hành: trạng thái máy Kiosk, tỷ lệ phun, cảnh báo và hiệu quả xử lý sự cố.'
        : principal.roles.includes('INVENTORY_STAFF')
          ? 'Góc nhìn Nhân viên kho: tồn kho chai/lõi, sản phẩm sắp hết, refill và phát hiện tiêu hao bất thường.'
          : 'Góc nhìn Kỹ thuật viên: phiếu bảo trì, máy được phân công và tình trạng sự cố.'
  if (!data) return <div className="empty">Không có dữ liệu tổng quan.</div>
  const groupNames: Record<string, string> = { transactions: 'Giao dịch', machines: 'Kiosk', alerts: 'Cảnh báo', inventory: 'Kho' }
  const primaryFields: Record<string, string> = { transactions: 'totalTransactions', machines: 'total', alerts: 'total', inventory: 'bottles' }
  const groups = ['transactions', 'machines', 'alerts', 'inventory'].map((key) => [key, (data[key] ?? {}) as Row] as const)
  const sprays = (operational?.sprays ?? null) as Row | null
  const invPerf = (operational?.inventory ?? null) as Row | null
  const incPerf = (operational?.incidents ?? null) as Row | null
  const unpaidOrders = Array.isArray(sprays?.paidOrderIdsWithoutSprayConfirmation) ? sprays.paidOrderIdsWithoutSprayConfirmation as string[] : []
  const refillCandidates = Array.isArray(invPerf?.refillCandidates) ? invPerf.refillCandidates as Row[] : []
  const abnormalConsumptions = Array.isArray(invPerf?.abnormalConsumptions) ? invPerf.abnormalConsumptions as Row[] : []
  return <>
    <section className="panel toolbar">
      <span>{roleHint}</span>
      <nav className="order-tabs" aria-label="Kỳ báo cáo">
        {([['day', 'Hôm nay'], ['week', 'Tuần này'], ['month', 'Tháng này']] as const).map(([key, label]) => (
          <button type="button" key={key} className={period === key ? 'active' : ''} onClick={() => setPeriod(key)}>{label}</button>
        ))}
      </nav>
    </section>
    <div className="metric-grid">{groups.map(([key, group]) => <article className="metric-card" key={key}><span>{groupNames[key]}</span><strong>{display(group[primaryFields[key]] ?? 0)}</strong>{Object.entries(group).filter(([name]) => name !== primaryFields[key]).slice(0, 5).map(([name, value]) => <small key={name}>{labels[name] ?? name}: {display(value, name)}</small>)}</article>)}</div>
    {operational && <section className="panel">
      <h3>Phân tích vận hành chi tiết ({String(operational.startDate ?? '')} → {String(operational.endDate ?? '')})</h3>
      <div className="metric-grid">
        {sprays && <article className="metric-card">
          <span>Hiệu suất phun</span>
          <strong>{display(sprays.successRate ?? 0)}%</strong>
          <small>Tổng lượt phun: {display(sprays.total ?? 0)} (Khách: {display(sprays.customerSuccessful ?? 0)} · Chẩn đoán: {display(sprays.diagnosticSuccessful ?? 0)})</small>
          <small>Thất bại: {display(sprays.failed ?? 0)} · Chưa xác nhận: {display(sprays.unconfirmed ?? 0)}</small>
          <small>Đơn PAID chưa xác nhận phun: {display(sprays.paidWithoutSprayConfirmation ?? 0)}{unpaidOrders.length ? ` (${unpaidOrders.slice(0, 5).join(', ')})` : ''}</small>
        </article>}
        {invPerf && <article className="metric-card">
          <span>Kho & Nạp thêm</span>
          <strong>{refillCandidates.length} chai</strong>
          <small>Chai cần nạp thêm / sắp hết: {refillCandidates.length} · Tổng còn lại: {display(invPerf.totalRemaining ?? 0, 'quantity')} ml</small>
          <small>Nạp thêm: {display(invPerf.refills ?? 0)} · Thay chai: {display(invPerf.replacements ?? 0)} · Điều chỉnh: {display(invPerf.adjustments ?? 0)}</small>
          <small>Tiêu hao bất thường: {abnormalConsumptions.length}</small>
        </article>}
        {incPerf && <article className="metric-card">
          <span>Sự cố & Bảo trì</span>
          <strong>{display(incPerf.overdueMaintenanceTickets ?? 0)} phiếu</strong>
          <small>Phiếu bảo trì quá hạn: {display(incPerf.overdueMaintenanceTickets ?? 0)}</small>
          <small>Tiếp nhận TB: {display(incPerf.averageAcknowledgeHours ?? 0)} giờ · Xử lý CB TB: {display(incPerf.averageAlertResolutionHours ?? 0)} giờ</small>
          <small>Downtime bảo trì: {display(incPerf.downtimeHours ?? 0)} giờ · Downtime sự cố: {display(incPerf.alertDowntimeHours ?? 0)} giờ</small>
        </article>}
      </div>
    </section>}
  </>
}

function OrderOverview({ counts, selected, select }: { counts: { orders: number; payments: number; reviews: number }; selected: 'ALL' | 'ORDER' | 'PAYMENT' | 'REVIEW'; select: (kind: 'ALL' | 'ORDER' | 'PAYMENT' | 'REVIEW') => void }) {
  const options = [
    ['ALL', 'Tất cả', counts.orders + counts.payments + counts.reviews],
    ['ORDER', 'Đơn hàng', counts.orders],
    ['PAYMENT', 'Thanh toán', counts.payments],
    ['REVIEW', 'Cần đối soát', counts.reviews],
  ] as const
  return <section className="order-overview" aria-label="Tổng quan đơn hàng và thanh toán"><div><span>Đơn hàng</span><strong>{counts.orders}</strong></div><div><span>Thanh toán</span><strong>{counts.payments}</strong></div><div><span>Đối soát</span><strong>{counts.reviews}</strong></div><nav className="order-tabs" aria-label="Lọc loại dữ liệu">{options.map(([kind, label, count]) => <button type="button" className={selected === kind ? 'active' : ''} key={kind} onClick={() => select(kind)}>{label}<small>{count}</small></button>)}</nav></section>
}

function ProductForm({ tenantId, product, close, saved }: { tenantId: number; product?: Product; close: () => void; saved: () => void }) {
  const [form, setForm] = useState({ name: product?.name ?? '', description: product?.description ?? '', fragranceNotes: product?.fragranceNotes ?? '', imageUrl: product?.imageUrl ?? '', defaultPrice: String(product?.defaultPrice ?? ''), bottlePrice: String(product?.bottlePrice ?? ''), bottleVolumeMl: String(product?.bottleVolumeMl ?? 100), bottleStock: String(product?.bottleStock ?? 0), available: product?.available ?? true })
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('')
    if (Number(form.defaultPrice) < 0 || Number(form.bottlePrice) <= 0) { setError('Phí trải nghiệm không được âm và giá bán chai phải lớn hơn 0.'); return }
    if (Number(form.bottleVolumeMl) <= 0 || Number(form.bottleStock) < 0) { setError('Dung tích phải lớn hơn 0 và tồn kho không được âm.'); return }
    setSaving(true)
    const payload = { name: form.name.trim(), description: form.description.trim() || null, fragranceNotes: form.fragranceNotes.trim() || null, imageUrl: form.imageUrl || null, defaultPrice: Number(form.defaultPrice), bottlePrice: Number(form.bottlePrice), bottleVolumeMl: Number(form.bottleVolumeMl), bottleStock: Number(form.bottleStock), available: form.available }
    try { if (product) await backend.updateProduct(product.id, payload); else await backend.createProduct({ ...payload, tenantId }); saved(); close() }
    catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  return <div className="modal-backdrop"><form className="modal" onSubmit={submit}><h3>{product ? `Cập nhật: ${product.name}` : 'Thêm sản phẩm'}</h3><label>Tên<input required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label><label>Nhóm hương<input value={form.fragranceNotes} onChange={(event) => setForm({ ...form, fragranceNotes: event.target.value })} /></label><label>Mô tả<textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label><label>Đường dẫn hình ảnh<input type="url" value={form.imageUrl} onChange={(event) => setForm({ ...form, imageUrl: event.target.value })} /></label><label>Phí trải nghiệm (VND)<input required min="0" step="1" type="number" placeholder="VD: 10000" value={form.defaultPrice} onChange={(event) => setForm({ ...form, defaultPrice: event.target.value })} /></label><label>Giá bán chai (VND)<input required min="1" step="1" type="number" placeholder="VD: 100000" value={form.bottlePrice} onChange={(event) => setForm({ ...form, bottlePrice: event.target.value })} /></label><label>Dung tích chai (ml)<input required min="1" step="1" type="number" value={form.bottleVolumeMl} onChange={(event) => setForm({ ...form, bottleVolumeMl: event.target.value })} /></label><label>Tồn chai bán lẻ<input required min="0" step="1" type="number" value={form.bottleStock} onChange={(event) => setForm({ ...form, bottleStock: event.target.value })} /></label><label className="switch"><input type="checkbox" checked={form.available} onChange={(event) => setForm({ ...form, available: event.target.checked })} />Đang kinh doanh</label>{error && <div className="error-box">{error}</div>}<div className="modal-actions"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu sản phẩm'}</button></div></form></div>
}

function Admin({ principal, refreshPrincipal, exit }: { principal: Principal; refreshPrincipal: (next: Principal) => void; exit: () => void }) {
  const platform = principal.roles.includes('PLATFORM_SUPER_ADMIN')
  const isAdminRole = platform || principal.roles.includes('BRAND_ADMIN')
  const hasBrandProfile = !platform && can(principal, ['BRAND_PROFILE_READ'])
  const isOpsManager = principal.roles.includes('OPERATIONS_MANAGER')
  const isTechnician = principal.roles.includes('TECHNICIAN')
  const menu = useMemo(
    () => pages.filter((item) => can(principal, item.permissions) && (platform || item.id !== 'tenants')),
    [principal, platform],
  )
  const canAccessPage = useCallback(
    (target: Page) => menu.some((item) => item.id === target) || (target === 'tenants' && hasBrandProfile),
    [menu, hasBrandProfile],
  )
  const hashPage = window.location.hash.slice(1) as Page
  const [page, setPage] = useState<Page>(canAccessPage(hashPage) ? hashPage : (menu[0]?.id ?? 'products'))
  const [tenants, setTenants] = useState<Tenant[]>([]); const [tenantId, setTenantId] = useState<number | undefined>(principal.tenantId ?? undefined)
  const [rows, setRows] = useState<Row[]>([]); const [dashboard, setDashboard] = useState<Row | null>(null); const [operationalReport, setOperationalReport] = useState<Row | null>(null); const [dashboardPeriod, setDashboardPeriod] = useState<DashboardPeriod>('month'); const [columns, setColumns] = useState<string[]>([])
  const [loading, setLoading] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [search, setSearch] = useState('')
  const [orderKind, setOrderKind] = useState<'ALL' | 'ORDER' | 'PAYMENT' | 'REVIEW'>('ALL'); const [orderStatusFilter, setOrderStatusFilter] = useState('')
  const [inventoryKind, setInventoryKind] = useState<'ALL' | 'BATCH' | 'BOTTLE' | 'MOVEMENT'>('ALL')
  const [seenInventory, setSeenInventory] = useState<Record<string, string[]>>({})
  const [alertSeverityFilter, setAlertSeverityFilter] = useState(''); const [alertStatusFilter, setAlertStatusFilter] = useState('')
  const [onlyMyTickets, setOnlyMyTickets] = useState(false)
  const [maintenancePrefill, setMaintenancePrefill] = useState<{ machineId?: number; title?: string; description?: string; category?: string; severity?: string } | undefined>(undefined)
  const [productEditor, setProductEditor] = useState<Product | 'new' | null>(null); const [selectedMachine, setSelectedMachine] = useState<Machine | null>(null); const [machineSlots, setMachineSlots] = useState<Row[]>([]); const [machineInstallations, setMachineInstallations] = useState<BottleInstallation[]>([]); const [slotsLoading, setSlotsLoading] = useState(false)
  const showProduct = productEditor === 'new'; const setShowProduct = (show: boolean) => setProductEditor(show ? 'new' : null)
  const [tenantEditor, setTenantEditor] = useState<Tenant | 'new' | null>(null); const [userEditor, setUserEditor] = useState<Account | 'new' | null>(null)
  const [productPriceEditor, setProductPriceEditor] = useState<Product | null>(null); const [campaignEditor, setCampaignEditor] = useState<Campaign | 'new' | null>(null)
  const [catalogProducts, setCatalogProducts] = useState<Product[]>([])
  const [locations, setLocations] = useState<Location[]>([]); const [machineEditor, setMachineEditor] = useState<Machine | 'new' | null>(null); const [slotEditor, setSlotEditor] = useState<Slot | 'new' | null>(null); const [showLocation, setShowLocation] = useState(false)
  const [inventoryBatches, setInventoryBatches] = useState<InventoryBatch[]>([]); const [inventoryBottles, setInventoryBottles] = useState<Bottle[]>([]); const [showInventoryForm, setShowInventoryForm] = useState(false); const [inventoryInitialProductId, setInventoryInitialProductId] = useState<number | undefined>(undefined); const [showInventoryWorkflow, setShowInventoryWorkflow] = useState(false); const [inventoryWorkflowPrefill, setInventoryWorkflowPrefill] = useState<InventoryWorkflowPrefill | undefined>(undefined); const [inventoryDisposeTarget, setInventoryDisposeTarget] = useState<InventoryDisposeTarget | null>(null); const [maintenanceEditor, setMaintenanceEditor] = useState<MaintenanceTicket | 'new' | null>(null); const [maintenanceWorkflow, setMaintenanceWorkflow] = useState<MaintenanceTicket | null>(null)
  const [showAccountSettings, setShowAccountSettings] = useState(false)
  const [pendingStaffCount, setPendingStaffCount] = useState(0)
  const [transactionFilters, setTransactionFilters] = useState<TransactionFilters>({})

  const refreshTenants = useCallback(async () => {
    const list = platform ? await backend.tenants() : [await backend.brandTenant()]
    setTenants(list); if (list.length && !platform) setTenantId((current) => current ?? list[0].id)
    return list
  }, [platform])
  useEffect(() => { void refreshTenants().catch((reason) => { if ((reason as ApiError).status !== 403) setError(errorText(reason)) }) }, [refreshTenants])
  const refreshPendingStaff = useCallback(async () => {
    if (!can(principal, ['BRAND_USER_MANAGE'])) { setPendingStaffCount(0); return }
    try {
      const list = await backend.users(platform, tenantId)
      setPendingStaffCount(list.filter((acc) => acc.pendingApproval).length)
    } catch { /* ignore */ }
  }, [principal, platform, tenantId])
  useEffect(() => { void refreshPendingStaff() }, [refreshPendingStaff])
  useEffect(() => {
    setNotice('')
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}#${page}`)
  }, [page])
  useEffect(() => {
    if (menu.length && !canAccessPage(page)) {
      setPage(menu[0].id)
      setError('Quyền truy cập của bạn đã thay đổi. Đã chuyển về trang được phép.')
    }
  }, [menu, page, canAccessPage])
  useEffect(() => {
    const onHashChange = () => {
      const requested = window.location.hash.slice(1) as Page
      if (!requested || requested === page) return
      if (canAccessPage(requested)) {
        setSelectedMachine(null)
        setPage(requested)
      } else {
        window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}#${page}`)
        setError('Bạn không có quyền truy cập trang được yêu cầu.')
      }
    }
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [page, canAccessPage])

  const load = useCallback(async () => {
    setLoading(true); setError(''); setDashboard(null); setOperationalReport(null)
    try {
      let data: Row[] = []
      const targetTenantIds = tenantId ? [tenantId] : platform ? tenants.map((tenant) => tenant.id) : principal.tenantId ? [principal.tenantId] : []
      const tenantNameMap = new Map(tenants.map((tenant) => [String(tenant.id), tenant.name]))
      const tenantNameOf = (id: string | number | undefined) => (id !== undefined ? tenantNameMap.get(String(id)) ?? `#${id}` : '—')
      switch (page) {
        case 'dashboard': {
          const scopedTenantId = tenantId ?? principal.tenantId ?? undefined
          const range = periodRange(dashboardPeriod)
          const liveTenantIds = scopedTenantId ? [scopedTenantId] : platform ? tenants.map((t) => t.id) : []
          const [summary, opReport, orderPair, liveData] = await Promise.all([
            backend.dashboard(tenantId, dashboardPeriod) as Promise<unknown>,
            scopedTenantId
              ? backend.operationalReport({ tenantId: scopedTenantId, period: dashboardPeriod }).catch(() => null)
              : platform && tenants.length
                ? Promise.all(tenants.map((t) => backend.operationalReport({ tenantId: t.id, period: dashboardPeriod }).catch(() => null)))
                    .then((items) => aggregateOperationalReports(items.filter(Boolean) as unknown as Row[], range.startDate, range.endDate))
                : Promise.resolve(null),
            can(principal, ['ORDER_READ'])
              ? Promise.all([backend.orders(), backend.payments()]).catch(() => null)
              : Promise.resolve(null),
            liveTenantIds.length
              ? Promise.all([
                  can(principal, ['MACHINE_READ']) ? Promise.all(liveTenantIds.map((id) => backend.machines(id).catch(() => []))).then((g) => g.flat()) : Promise.resolve(null),
                  can(principal, ['ALERT_READ']) ? Promise.all(liveTenantIds.map((id) => backend.alerts(id).catch(() => []))).then((g) => g.flat()) : Promise.resolve(null),
                  can(principal, ['INVENTORY_READ']) ? Promise.all(liveTenantIds.map((id) => backend.bottles(id).catch(() => []))).then((g) => g.flat()) : Promise.resolve(null),
                ]).catch(() => null)
              : Promise.resolve(null),
          ])
          const dashboardData = summary as Row
          setOperationalReport(opReport as unknown as Row | null)
          if (orderPair) {
            const [orders, payments] = orderPair
            dashboardData.transactions = transactionSummaryForTenant(payments, orders, scopedTenantId, dashboardPeriod)
          }
          if (liveData) {
            const [liveMachines, liveAlerts, liveBottles] = liveData
            if (liveMachines) {
              const machineCounts: Row = { total: liveMachines.length, online: 0, offline: 0, unstable: 0, disabled: 0, maintenance: 0, error: 0, unknown: 0 }
              for (const machine of liveMachines) {
                const status = machine.operationalStatus.toLowerCase()
                machineCounts[status in machineCounts ? status : 'unknown'] = Number(machineCounts[status in machineCounts ? status : 'unknown']) + 1
              }
              dashboardData.machines = machineCounts
            }
            if (liveAlerts) {
              const alertCounts: Row = { total: liveAlerts.length, open: 0, acknowledged: 0, assigned: 0, escalated: 0, resolved: 0 }
              for (const alert of liveAlerts) {
                const status = alert.status.toLowerCase()
                if (status in alertCounts) alertCounts[status] = Number(alertCounts[status]) + 1
              }
              dashboardData.alerts = alertCounts
            }
            if (liveBottles) {
              const bottleCounts: Row = { bottles: liveBottles.length, available: 0, installed: 0, nearlyEmpty: 0, empty: 0, damaged: 0 }
              for (const bottle of liveBottles) {
                const s = bottle.status.toUpperCase()
                if (s === 'AVAILABLE') bottleCounts.available = Number(bottleCounts.available) + 1
                else if (s === 'INSTALLED') bottleCounts.installed = Number(bottleCounts.installed) + 1
                else if (s === 'NEARLY_EMPTY') bottleCounts.nearlyEmpty = Number(bottleCounts.nearlyEmpty) + 1
                else if (s === 'EMPTY') bottleCounts.empty = Number(bottleCounts.empty) + 1
                else if (s === 'DAMAGED' || s === 'EXPIRED') bottleCounts.damaged = Number(bottleCounts.damaged) + 1
              }
              dashboardData.inventory = bottleCounts
            }
          }
          setDashboard(dashboardData)
          break
        }
        case 'tenants': {
          const tenantRows = platform ? await backend.tenants() : [await backend.brandTenant()]
          data = await Promise.all(tenantRows.map(async (tenant) => {
            if (!platform) return tenant as unknown as Row
            const [machines, accounts] = await Promise.all([backend.machines(tenant.id), backend.users(true, tenant.id)])
            return { ...tenant, machineCount: machines.length, onlineMachineCount: machines.filter((machine) => machine.operationalStatus === 'ONLINE').length, adminCount: accounts.filter((account) => account.roles.includes('BRAND_ADMIN')).length }
          }))
          break
        }
        case 'users': {
          const userList = await backend.users(platform, tenantId)
          setPendingStaffCount(userList.filter((acc) => acc.pendingApproval).length)
          data = userList as unknown as Row[]
          break
        }
        case 'products': {
          const products = (await Promise.all(targetTenantIds.map((id) => backend.products(id)))).flat()
          setCatalogProducts(products)
          data = products.map((product) => ({ ...product, tenantName: tenantNameOf(product.tenantId) })) as unknown as Row[]
          break
        }
        case 'campaigns': { const groups = await Promise.all(targetTenantIds.map(async (id) => { const [campaigns, products] = await Promise.all([backend.campaigns(id), backend.products(id)]); return { campaigns, products } })); const products = groups.flatMap((group) => group.products); setCatalogProducts(products); const productMap = new Map(products.map((p) => [p.id, p.name])); data = groups.flatMap((group) => group.campaigns).map((campaign) => ({ ...campaign, tenantName: tenantNameOf(campaign.tenantId), productName: productMap.get(campaign.productId) ?? `#${campaign.productId}` })) as unknown as Row[]; break }
        case 'machines': { const groups = await Promise.all(targetTenantIds.map(async (id) => { const [machines, machineLocations] = await Promise.all([backend.machines(id), backend.locations(id)]); return { machines, machineLocations } })); const machineLocations = groups.flatMap((group) => group.machineLocations); setLocations(machineLocations); const locMap = new Map(machineLocations.map((l) => [l.locationId, l.name])); data = groups.flatMap((group) => group.machines).map((machine) => ({ ...machine, kind: 'MACHINE', tenantName: tenantNameOf(machine.tenantId), locationName: locMap.get(machine.locationId) ?? machine.locationId })) as unknown as Row[]; break }
        case 'inventory': {
          const groups = await Promise.all(targetTenantIds.map(async (id) => {
            const [batches, bottles, movements, products] = await Promise.all([
              backend.batches(id), backend.bottles(id), backend.movements(id),
              can(principal, ['CATALOG_READ']) ? backend.products(id) : Promise.resolve([]),
            ])
            return { id, batches, bottles, movements, products }
          }))
          setInventoryBatches(groups.flatMap((group) => group.batches))
          setInventoryBottles(groups.flatMap((group) => group.bottles))
          const rowTime = (item: Row) => {
            const raw = item.updatedAt ?? item.createdAt ?? item.cancelledAt ?? item.receivedAt
            const parsed = typeof raw === 'string' ? Date.parse(raw) : NaN
            return Number.isNaN(parsed) ? 0 : parsed
          }
          data = groups.flatMap((group) => {
            const tenantName = tenantNameOf(group.id)
            const productObjMap = new Map(group.products.map((p) => [p.id, p]))
            const bottleMap = new Map(group.bottles.map((b) => [b.id, b.productId]))
            const productName = (productId?: number) => (productId ? (productObjMap.get(productId)?.name ?? `Sản phẩm #${productId}`) : '—')
            const productVolume = (productId?: number) => (productId ? (productObjMap.get(productId)?.bottleVolumeMl ?? null) : null)
            const batchMovements = group.batches.map((item) => {
              const opTime = item.cancelledAt ?? item.updatedAt ?? item.createdAt ?? item.receivedAt
              return {
                kind: 'MOVEMENT',
                id: `batch-${item.id}`,
                tenantName,
                productName: productName(item.productId),
                batchCode: item.batchCode,
                movementType: item.status === 'CANCELLED' ? 'BATCH_CANCEL' : 'BATCH_RECEIVE',
                reason: item.status === 'CANCELLED'
                  ? `Hủy lô ${item.batchCode}: ${item.cancelReason ?? ''}`
                  : `Nhập lô ${item.batchCode} (SL nhận: ${item.receivedQuantity})`,
                updatedAt: opTime,
              }
            })
            const activeProductIds = new Set(
              group.bottles
                .filter((b) => !['DAMAGED', 'EXPIRED'].includes(String(b.status).toUpperCase()))
                .map((b) => b.productId),
            )
            const visibleBottles = group.bottles.filter((b) => {
              const disposed = ['DAMAGED', 'EXPIRED'].includes(String(b.status).toUpperCase())
              return !disposed || !activeProductIds.has(b.productId)
            })
            return [
              ...group.batches.map((item) => ({ kind: 'BATCH', tenantName, ...item, status: item.status ?? 'ACTIVE', reason: item.cancelReason ?? undefined, productName: productName(item.productId), bottleVolumeMl: productVolume(item.productId), updatedAt: item.cancelledAt ?? item.updatedAt ?? item.createdAt ?? item.receivedAt })),
              ...visibleBottles.map((item) => ({ kind: 'BOTTLE', tenantName, ...item, productName: productName(item.productId), bottleVolumeMl: productVolume(item.productId) })),
              ...batchMovements,
              ...group.movements.map((item) => ({ kind: 'MOVEMENT', tenantName, ...item, updatedAt: item.createdAt ?? item.updatedAt, productName: productName(bottleMap.get(Number(item.bottleId))) })),
            ]
          }) as unknown as Row[]
          data.sort((a, b) => {
            const diff = rowTime(b) - rowTime(a)
            if (diff !== 0) return diff
            return Number(b.id ?? 0) - Number(a.id ?? 0)
          })
          break
        }
        case 'alerts': { const alertGroups = await Promise.all(targetTenantIds.map((id) => backend.alerts(id))); data = alertGroups.flat().map((alert) => ({ ...alert, tenantName: tenantNameOf(alert.tenantId) })) as unknown as Row[]; break }
        case 'maintenance': { const groups = await Promise.all(targetTenantIds.map(async (id) => ({ id, tickets: await backend.maintenance(id) }))); data = groups.flatMap((group) => group.tickets.map((ticket) => ({ ...ticket, tenantName: tenantNameOf(group.id) }))) as unknown as Row[]; break }
        case 'orders': { const reviews = can(principal, ['ORDER_REVIEW']) ? backend.reviews() : Promise.resolve([]); const [orders, payments, paymentReviews] = await Promise.all([backend.orders(), backend.payments(), reviews]); const orderTenantMap = new Map(orders.map((order) => [order.id, order.tenantId])); const visibleOrders = tenantId ? orders.filter((order) => String(order.tenantId) === String(tenantId)) : orders; const visibleOrderIds = new Set(visibleOrders.map((order) => order.id)); const visiblePayments = payments.filter((payment) => visibleOrderIds.has(payment.orderId)); const visibleReviews = paymentReviews.filter((review) => visibleOrderIds.has(review.orderId)); data = [...visibleOrders.map((item) => { const needsCheck = item.manualReviewRequired || item.dispensingStatus === 'FAILED' || (item.status === 'PAID' && item.dispensingStatus !== 'DISPENSED'); return { kind: 'ORDER', ...item, reconciliationFlag: needsCheck ? '⚠ Cần đối soát / chưa xác nhận phun' : 'Bình thường', tenantName: tenantNameOf(item.tenantId), items: item.items.map((orderItem) => `${orderItem.productName} ×${orderItem.quantity}`).join(', ') } }), ...visiblePayments.map((item) => ({ kind: 'PAYMENT', ...item, tenantName: tenantNameOf(orderTenantMap.get(item.orderId)) })), ...visibleReviews.map((item) => ({ kind: 'REVIEW', ...item, reconciliationFlag: '⚠ Cần đối soát', tenantName: tenantNameOf(orderTenantMap.get(item.orderId)) }))] as unknown as Row[]; break }
        case 'transactions': data = (await backend.transactions({ ...transactionFilters, tenantId })).content as unknown as Row[]; break
        case 'audit': data = await backend.audit(tenantId); break
      }
      setRows(data)
      const map: Record<Page, string[]> = { dashboard: [], tenants: ['id', 'code', 'name', 'status', 'contactEmail', 'contactPhone', 'machineCount', 'onlineMachineCount', 'adminCount'], users: ['id', 'username', 'fullName', 'email', 'roles', 'tenantId', 'pendingApproval', 'active', 'locked'], products: ['imageUrl', 'tenantName', 'name', 'fragranceNotes', 'defaultPrice', 'bottlePrice', 'bottleVolumeMl', 'bottleStock', 'available', 'updatedAt'], campaigns: ['tenantName', 'id', 'name', 'productName', 'discountType', 'discountValue', 'active', 'startAt', 'endAt'], machines: ['tenantName', 'id', 'name', 'serialNumber', 'locationName', 'deviceRegistrationStatus', 'operationalStatus', 'lastSeenAt'], inventory: ['tenantName', 'kind', 'id', 'productName', 'batchCode', 'bottleCode', 'bottleVolumeMl', 'movementType', 'machineId', 'slotId', 'currentQuantity', 'calibratedConsumptionPerSpray', 'quantityBefore', 'quantityAfter', 'receivedAt', 'expiryDate', 'reason', 'status', 'updatedAt'], alerts: ['tenantName', 'id', 'severity', 'status', 'type', 'summary', 'machineId', 'locationId', 'assignedTo', 'lastSeenAt'], maintenance: ['tenantName', 'id', 'severity', 'status', 'approved', 'title', 'machineId', 'priority', 'assignedToActorId', 'dueAt', 'updatedAt'], orders: ['kind', 'tenantName', 'id', 'reference', 'orderId', 'orderType', 'items', 'totalAmount', 'amount', 'currency', 'status', 'dispensingStatus', 'reconciliationFlag', 'manualReviewRequired', 'createdAt'], transactions: ['id', 'reference', 'orderId', 'tenantId', 'machineId', 'productId', 'amount', 'currency', 'status', 'createdAt', 'paidAt'], audit: ['id', 'occurredAt', 'actorId', 'tenantId', 'action', 'targetType', 'targetId', 'outcome'] }
      setColumns(map[page].filter((key) => data.some((item) => key in item)))
    } catch (reason) { setRows([]); setError(errorText(reason)) } finally { setLoading(false) }
  }, [page, tenantId, platform, principal, transactionFilters, tenants, dashboardPeriod])
  useEffect(() => { void load() }, [load])

  const loadMachineSlots = useCallback(async (machine: Machine) => {
    setSlotsLoading(true); setError('')
    try {
      const machineTenantId = Number(machine.tenantId)
      const [slots, products, bottles, installations] = await Promise.all([
        backend.slots(machine.id),
        can(principal, ['CATALOG_READ']) ? backend.products(machineTenantId).catch(() => []) : Promise.resolve([]),
        can(principal, ['INVENTORY_READ']) ? backend.bottles(machineTenantId).catch(() => []) : Promise.resolve([]),
        can(principal, ['INVENTORY_READ']) ? backend.installations(machineTenantId, machine.id).catch(() => []) : Promise.resolve([]),
      ])
      setCatalogProducts(products)
      setInventoryBottles(bottles)
      setMachineInstallations(installations)
      const productMap = new Map(products.map((p) => [String(p.id), p.name]))
      const bottleMap = new Map(bottles.map((b) => [b.id, b]))
      const installationBySlot = new Map(installations.map((inst) => [inst.slotId, inst]))
      setMachineSlots(slots.map((s) => {
        const inst = installationBySlot.get(s.id)
        const bottle = inst ? bottleMap.get(inst.bottleId) : undefined
        const effectiveDose = s.doseMl ?? bottle?.calibratedConsumptionPerSpray ?? null
        const estimatedSprays = bottle && effectiveDose && effectiveDose > 0
          ? Math.floor(bottle.currentQuantity / effectiveDose)
          : null
        return {
          ...s,
          productName: s.productId ? (productMap.get(String(s.productId)) ?? `Sản phẩm #${s.productId}`) : '—',
          installedBottleCode: bottle ? bottle.bottleCode : 'Chưa lắp chai',
          remainingMl: bottle ? `${bottle.currentQuantity} / ${bottle.initialQuantity} ml` : '—',
          estimatedSprays: estimatedSprays !== null ? `${estimatedSprays} lượt` : '—',
        }
      }) as unknown as Row[])
    } catch (reason) {
      setMachineSlots([])
      setMachineInstallations([])
      setError(errorText(reason))
    } finally {
      setSlotsLoading(false)
    }
  }, [principal])

  const openMachineSlots = useCallback((machine: Machine) => {
    setNotice(''); setError(''); setSearch(''); setMachineSlots([]); setMachineInstallations([])
    setSelectedMachine(machine)
    void loadMachineSlots(machine)
  }, [loadMachineSlots])

  const closeMachineSlots = useCallback(() => {
    setSelectedMachine(null); setMachineSlots([]); setMachineInstallations([]); setNotice(''); setError(''); setSearch('')
  }, [])

  const isViewingMachineSlots = page === 'machines' && selectedMachine !== null
  const activeRows = isViewingMachineSlots ? machineSlots : rows
  const activeLoading = isViewingMachineSlots ? slotsLoading : loading
  const filtered = activeRows.filter((item) => {
    if (page === 'orders' && orderKind !== 'ALL' && item.kind !== orderKind) return false
    if (page === 'orders' && orderStatusFilter && String(item.status ?? '').toUpperCase() !== orderStatusFilter) return false
    if (page === 'inventory' && inventoryKind !== 'ALL' && item.kind !== inventoryKind) return false
    if (page === 'alerts' && alertSeverityFilter && String(item.severity ?? '').toUpperCase() !== alertSeverityFilter) return false
    if (page === 'alerts' && alertStatusFilter && String(item.status ?? '').toUpperCase() !== alertStatusFilter) return false
    if (page === 'maintenance' && onlyMyTickets && Number(item.assignedToActorId) !== principal.id) return false
    return JSON.stringify(item).toLowerCase().includes(search.trim().toLowerCase())
  })
  const lowStockBottleCount = inventoryBottles.filter((b) => ['NEARLY_EMPTY', 'EMPTY', 'EXPIRED'].includes(String(b.status).toUpperCase())).length
  const inventorySeenKey = `scentstation.inventory.seen.${principal.id}.${tenantId ?? 'all'}`
  const inventoryItemSig = (item: Row) => `${String(item.kind)}:${String(item.id)}:${String(item.updatedAt ?? item.createdAt ?? item.status ?? '')}`

  useEffect(() => {
    try {
      const raw = window.localStorage.getItem(inventorySeenKey)
      setSeenInventory(raw ? (JSON.parse(raw) as Record<string, string[]>) : {})
    } catch {
      setSeenInventory({})
    }
  }, [inventorySeenKey])

  const markInventoryKindsSeen = useCallback((kinds: Array<'BATCH' | 'BOTTLE' | 'MOVEMENT'>, sourceRows: Row[]) => {
    if (!sourceRows.length) return
    setSeenInventory((prev) => {
      const next: Record<string, string[]> = { ...prev }
      let changed = false
      for (const kind of kinds) {
        const sigs = sourceRows.filter((item) => item.kind === kind).map(inventoryItemSig)
        const existing = new Set(prev[kind] ?? [])
        const merged = Array.from(new Set([...existing, ...sigs]))
        if (merged.length !== existing.size) {
          next[kind] = merged
          changed = true
        }
      }
      if (!changed) return prev
      try { window.localStorage.setItem(inventorySeenKey, JSON.stringify(next)) } catch { /* ignore storage quota */ }
      return next
    })
  }, [inventorySeenKey])

  useEffect(() => {
    if (page !== 'inventory' || loading || !rows.length) return
    const hasBaseline = Boolean(seenInventory.BATCH || seenInventory.BOTTLE || seenInventory.MOVEMENT)
    if (!hasBaseline) {
      markInventoryKindsSeen(['BATCH', 'BOTTLE', 'MOVEMENT'], rows)
    } else if (inventoryKind === 'BATCH' || inventoryKind === 'BOTTLE' || inventoryKind === 'MOVEMENT') {
      markInventoryKindsSeen([inventoryKind], rows)
    }
  }, [page, loading, rows, inventoryKind, seenInventory, markInventoryKindsSeen])

  const unseenInventoryCount = (kind: 'BATCH' | 'BOTTLE' | 'MOVEMENT') => {
    if (page !== 'inventory') return 0
    const seenList = seenInventory[kind]
    if (!seenList) return 0
    const seenSet = new Set(seenList)
    return rows.filter((item) => item.kind === kind && !seenSet.has(inventoryItemSig(item))).length
  }
  const inventoryUnseen = {
    batches: unseenInventoryCount('BATCH'),
    bottles: unseenInventoryCount('BOTTLE'),
    movements: unseenInventoryCount('MOVEMENT'),
  }
  const orderCounts = { orders: rows.filter((item) => item.kind === 'ORDER').length, payments: rows.filter((item) => item.kind === 'PAYMENT').length, reviews: rows.filter((item) => item.kind === 'REVIEW').length }
  const visibleColumns = useMemo(() => {
    if (isViewingMachineSlots) return ['slotNumber', 'slotCode', 'productName', 'cartridgeId', 'doseMl', 'installedBottleCode', 'remainingMl', 'estimatedSprays', 'price', 'status', 'available']
    if (page === 'orders' && orderKind === 'ORDER') return columns.filter((col) => !['reference', 'orderId', 'amount'].includes(col))
    if (page === 'orders' && orderKind === 'PAYMENT') return columns.filter((col) => !['id', 'orderType', 'items', 'totalAmount', 'dispensingStatus', 'reconciliationFlag', 'manualReviewRequired'].includes(col))
    if (page === 'orders' && orderKind === 'REVIEW') return columns.filter((col) => !['id', 'orderType', 'items', 'totalAmount', 'amount', 'currency', 'dispensingStatus'].includes(col))
    if (page === 'inventory' && inventoryKind === 'BATCH') return columns.filter((col) => !['bottleCode', 'movementType', 'machineId', 'slotId', 'currentQuantity', 'calibratedConsumptionPerSpray', 'quantityBefore', 'quantityAfter'].includes(col))
    if (page === 'inventory' && inventoryKind === 'BOTTLE') return columns.filter((col) => !['batchCode', 'movementType', 'machineId', 'slotId', 'quantityBefore', 'quantityAfter', 'receivedAt', 'expiryDate', 'reason'].includes(col))
    if (page === 'inventory' && inventoryKind === 'MOVEMENT') return columns.filter((col) => !['bottleCode', 'bottleVolumeMl', 'currentQuantity', 'calibratedConsumptionPerSpray', 'receivedAt', 'expiryDate', 'status'].includes(col))
    return columns
  }, [isViewingMachineSlots, page, orderKind, inventoryKind, columns])
  async function mutate(job: () => Promise<unknown>, message: string) { try { setError(''); await job(); setNotice(message); await load() } catch (reason) { setError(errorText(reason)) } }
  function action(row: Row) {
    if (page === 'tenants') { const tenant = row as unknown as Tenant; const editable = platform || can(principal, ['BRAND_PROFILE_EDIT']); if (!editable) return null; return <><button onClick={() => setTenantEditor(tenant)}>Chỉnh sửa</button>{platform && <button className={tenant.status === 'ACTIVE' ? 'danger' : ''} onClick={() => { if (tenant.status === 'ACTIVE' && !window.confirm(`Thương hiệu ${tenant.name} đang hoạt động. Tạm ngưng sẽ chặn các nghiệp vụ thuộc thương hiệu này. Bạn chắc chắn muốn tiếp tục?`)) return; void mutate(() => backend.updateTenantStatus(tenant.id, tenant.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'), tenant.status === 'ACTIVE' ? 'Đã tạm ngưng thương hiệu.' : 'Đã kích hoạt thương hiệu.') }}>{tenant.status === 'ACTIVE' ? 'Tạm ngưng' : 'Kích hoạt'}</button>}</> }
    if (page === 'users') {
      const account = row as unknown as Account
      if (account.pendingApproval) {
        return <>
          <button className="primary" onClick={() => void mutate(() => backend.approveUser(platform, account.id), `Đã phê duyệt vai trò nhân viên cho ${account.username}.`)}>Phê duyệt vai trò</button>
          <button className="danger" onClick={() => { if (!window.confirm(`Từ chối yêu cầu đăng ký nhân viên của ${account.username}?`)) return; void mutate(() => backend.rejectUser(platform, account.id), `Đã từ chối yêu cầu của ${account.username}.`) }}>Từ chối</button>
        </>
      }
      return <><button onClick={() => setUserEditor(account)}>Quản lý</button><button className={account.locked ? '' : 'danger'} onClick={() => { if (!account.locked && !window.confirm(`Bạn có chắc chắn muốn khóa tài khoản ${account.username}? Phiên đăng nhập hiện tại của tài khoản này sẽ bị vô hiệu hóa.`)) return; void mutate(() => backend.updateUserStatus(platform, account.id, account.active, !account.locked), account.locked ? 'Đã mở khóa tài khoản.' : 'Đã khóa tài khoản.') }}>{account.locked ? 'Mở khóa' : 'Khóa'}</button></>
    }
    if (page === 'products' && tenantId && !platform && can(principal, ['CATALOG_MANAGE'])) { const product = row as unknown as Product; return <><button onClick={() => setProductEditor(product)}>Chỉnh sửa</button><button onClick={() => setProductPriceEditor(product)}>Cập nhật nhanh giá</button><button onClick={() => void mutate(() => backend.setProductAvailability(product.id, !product.available), 'Đã cập nhật sản phẩm.')}>{product.available ? 'Tạm ngưng' : 'Mở bán'}</button></> }
    if (page === 'campaigns' && tenantId && can(principal, ['CAMPAIGN_MANAGE'])) { const campaign = row as unknown as Campaign; return <><button onClick={() => setCampaignEditor(campaign)}>Chỉnh sửa</button><button className="danger" onClick={() => { if (!window.confirm(`Bạn có chắc chắn muốn xóa chiến dịch "${campaign.name}"?`)) return; void mutate(() => backend.deleteCampaign(campaign.id, tenantId), 'Đã xóa chiến dịch.') }}>Xóa</button></> }
    if (page === 'inventory' && !platform && can(principal, ['INVENTORY_MANAGE'])) {
      if (row.kind === 'BATCH') {
        const batch = row as unknown as InventoryBatch
        const isCancelled = String(batch.status ?? 'ACTIVE').toUpperCase() === 'CANCELLED'
        if (isCancelled) {
          return <button onClick={() => { setInventoryInitialProductId(batch.productId); setShowInventoryForm(true) }}>Nhập lô mới</button>
        }
        return <>
          <button onClick={() => {
            const defaultVol = Number((row as Row).bottleVolumeMl ?? 100) || 100
            const volInput = window.prompt(`Nhập dung tích (ml) cho chai mới xuất từ lô ${batch.batchCode}:`, String(defaultVol))
            if (!volInput) return
            const qty = Number(volInput)
            if (!Number.isFinite(qty) || qty <= 0) { setError('Dung tích phải lớn hơn 0.'); return }
            const code = `BOT-${batch.batchCode}-${Date.now().toString().slice(-4)}`
            void mutate(() => backend.createBottle({ tenantId: batch.tenantId, productId: batch.productId, batchId: batch.id, bottleCode: code, initialQuantity: qty }), `Đã cấp chai mới "${code}" (${qty} ml) từ lô ${batch.batchCode}.`)
          }}>Cấp chai từ lô</button>
          <button className="danger" onClick={() => setInventoryDisposeTarget({ kind: 'BATCH', item: batch })}>Hủy lô</button>
        </>
      }
      if (row.kind === 'BOTTLE') {
        const bottle = row as unknown as Bottle
        const isDisposed = ['DAMAGED', 'EXPIRED'].includes(String(bottle.status ?? '').toUpperCase())
        if (!isDisposed) {
          return <>{tenantId && <button onClick={() => { setInventoryWorkflowPrefill(undefined); setShowInventoryWorkflow(true) }}>Nạp thêm / lắp / thay chai</button>}<button className="danger" onClick={() => setInventoryDisposeTarget({ kind: 'BOTTLE', item: bottle })}>Hủy / báo hư</button></>
        }
        const parentBatch = inventoryBatches.find((b) => b.id === bottle.batchId)
        if (parentBatch && String(parentBatch.status ?? 'ACTIVE').toUpperCase() !== 'CANCELLED') {
          return <button onClick={() => {
            const defaultVol = Number((row as Row).bottleVolumeMl ?? bottle.initialQuantity ?? 100) || 100
            const volInput = window.prompt(`Lô ${parentBatch.batchCode} vẫn đang hoạt động. Nhập dung tích (ml) cho chai thay thế:`, String(defaultVol))
            if (!volInput) return
            const qty = Number(volInput)
            if (!Number.isFinite(qty) || qty <= 0) { setError('Dung tích phải lớn hơn 0.'); return }
            const code = `BOT-${parentBatch.batchCode}-${Date.now().toString().slice(-4)}`
            void mutate(() => backend.createBottle({ tenantId: parentBatch.tenantId, productId: parentBatch.productId, batchId: parentBatch.id, bottleCode: code, initialQuantity: qty }), `Đã cấp chai thay thế "${code}" (${qty} ml) từ lô ${parentBatch.batchCode}.`)
          }}>Cấp chai thay thế</button>
        }
        return <button onClick={() => { setInventoryInitialProductId(bottle.productId); setShowInventoryForm(true) }}>Nhập lô mới</button>
      }
      return null
    }
    if (isViewingMachineSlots && selectedMachine) {
      if (platform) return null
      const canConfigureSlot = can(principal, ['MACHINE_CONFIGURE'])
      const canSwapBottle = can(principal, ['INVENTORY_MANAGE'])
      if (!canConfigureSlot && !canSwapBottle) return null
      const slot = row as unknown as Slot
      const activeInst = machineInstallations.find((inst) => inst.slotId === slot.id)
      return <>
        {canConfigureSlot && <button onClick={() => setSlotEditor(slot)}>Chỉnh sửa</button>}
        {canSwapBottle && <button onClick={() => { setInventoryWorkflowPrefill({ mode: activeInst ? 'replace' : 'install', machineId: selectedMachine.id, slotId: slot.id, oldBottleId: activeInst?.bottleId }); setShowInventoryWorkflow(true) }}>{activeInst ? 'Thay chai' : 'Lắp chai'}</button>}
        {canConfigureSlot && <button onClick={() => void (async () => { try { await backend.setSlotEnabled(selectedMachine.id, slot.id, !slot.enabled); await loadMachineSlots(selectedMachine); setNotice(slot.enabled ? 'Đã tắt đầu phun.' : 'Đã bật đầu phun.') } catch (reason) { setError(errorText(reason)) } })()}>{slot.enabled ? 'Tắt' : 'Bật'}</button>}
      </>
    }
    if (page === 'machines') {
      const machine = row as unknown as Machine
      if (platform) return <button onClick={() => openMachineSlots(machine)}>Xem đầu phun</button>
      return <><button onClick={() => openMachineSlots(machine)}>Xem đầu phun</button>{tenantId && can(principal, ['MACHINE_CONFIGURE']) && <button onClick={() => setMachineEditor(machine)}>Chỉnh sửa</button>}{can(principal, ['MACHINE_CONTROL']) && <>{(machine.operationalStatus === 'OFFLINE' || machine.operationalStatus === 'UNSTABLE') && <button className="primary" onClick={() => void mutate(() => backend.setMachineOperationalStatus(machine.id, 'ONLINE'), 'Đã đưa kiosk về trạng thái trực tuyến.')}>Đưa về trực tuyến</button>}<button onClick={() => void mutate(() => backend.controlMachine(machine.id, machine.operationalStatus === 'DISABLED' ? 'ENABLE' : 'DISABLE'), machine.operationalStatus === 'DISABLED' ? 'Đã bật máy.' : 'Đã tắt máy.')}>{machine.operationalStatus === 'DISABLED' ? 'Bật máy' : 'Tắt máy'}</button>{machine.operationalStatus !== 'MAINTENANCE' && <button onClick={() => void mutate(() => backend.controlMachine(machine.id, 'ENTER_MAINTENANCE'), 'Đã chuyển máy sang trạng thái bảo trì.')}>Bảo trì</button>}</>}</>
    }
    if (page === 'alerts') {
      if (isAdminRole) return null
      const alert = row as unknown as Alert
      const alertTenantId = Number(alert.tenantId)
      const isMachineIssue = String(alert.type ?? '').toUpperCase() !== 'LOW_STOCK'
      const assignedToSelf = String(alert.assignedTo ?? '') === String(principal.id)
      if (isOpsManager && can(principal, ['ALERT_MANAGE'])) {
        return <>
          {alert.status === 'OPEN' && <button onClick={() => void mutate(() => backend.alertAction(alertTenantId, alert.id, 'acknowledge'), 'Đã xác nhận cảnh báo.')}>Xác nhận</button>}
          {alert.status !== 'RESOLVED' && <button onClick={() => { const assignee = window.prompt('Nhập ID nhân viên (Kỹ thuật / Kho / Vận hành — không phân công cho Admin):', alert.assignedTo ?? ''); if (assignee?.trim()) { if (['4', '5', '9201', '9202'].includes(assignee.trim())) { setError('Không được phân công cảnh báo cho tài khoản Quản trị viên (Admin).'); return } void mutate(() => backend.assignAlert(alertTenantId, alert.id, assignee.trim(), 'Phân công từ quản lý vận hành'), 'Đã phân công cảnh báo cho nhân viên.') } }}>Phân công</button>}
          {alert.status !== 'RESOLVED' && can(principal, ['MAINTENANCE_MANAGE']) && <button onClick={() => { setMaintenancePrefill({ machineId: alert.machineId, title: `[Cảnh báo #${alert.id}] ${alert.type}`, description: alert.summary || `Tạo từ cảnh báo #${alert.id}`, category: alert.type, severity: alert.severity === 'CRITICAL' ? 'CRITICAL' : alert.severity === 'HIGH' ? 'MAJOR' : 'MODERATE' }); setMaintenanceEditor('new') }}>Tạo phiếu bảo trì</button>}
          {alert.status !== 'RESOLVED' && <button onClick={() => void mutate(() => backend.alertAction(alertTenantId, alert.id, 'resolve', 'Đã xử lý bởi quản lý vận hành'), 'Đã xử lý cảnh báo.')}>Xử lý</button>}
          {alert.status === 'RESOLVED' && <button onClick={() => void mutate(() => backend.alertAction(alertTenantId, alert.id, 'reopen', 'Mở lại từ quản lý vận hành'), 'Đã mở lại cảnh báo.')}>Mở lại</button>}
        </>
      }
      if (isTechnician && (isMachineIssue || assignedToSelf)) {
        return <>
          {alert.status === 'OPEN' && <button onClick={() => void mutate(() => backend.alertAction(alertTenantId, alert.id, 'acknowledge'), 'Đã xác nhận tiếp nhận sự cố máy.')}>Xác nhận</button>}
          {alert.status !== 'RESOLVED' && <button className="primary" onClick={() => void mutate(() => backend.alertAction(alertTenantId, alert.id, 'resolve', 'Đã xử lý sự cố máy bởi kỹ thuật viên'), 'Đã xử lý cảnh báo sự cố máy.')}>Xử lý</button>}
          {alert.status !== 'RESOLVED' && can(principal, ['MAINTENANCE_MANAGE']) && <button onClick={() => { setMaintenancePrefill({ machineId: alert.machineId, title: `[Sự cố máy #${alert.id}] ${alert.type}`, description: alert.summary || `Tạo từ cảnh báo sự cố #${alert.id}`, category: alert.type, severity: alert.severity === 'CRITICAL' ? 'CRITICAL' : alert.severity === 'HIGH' ? 'MAJOR' : 'MODERATE' }); setMaintenanceEditor('new') }}>Tạo phiếu bảo trì</button>}
        </>
      }
      return null
    }
    if (page === 'maintenance' && can(principal, ['MAINTENANCE_READ'])) {
      const ticket = row as unknown as MaintenanceTicket
      const ticketTenantId = ticket.tenantId || tenantId || principal.tenantId || 1
      if (isAdminRole) {
        return <>
          <button onClick={() => setMaintenanceWorkflow(ticket)}>Theo dõi</button>
          {!ticket.approved && ticket.status !== 'COMPLETED' && ticket.status !== 'CANCELLED' && (
            <button className="primary" onClick={() => void mutate(() => backend.approveMaintenance(ticket.id, ticketTenantId, principal.id, 'Đã chấp nhận phiếu bảo trì'), 'Đã chấp nhận phiếu bảo trì.')}>Chấp nhận phiếu</button>
          )}
        </>
      }
      if (isOpsManager) {
        return <>
          {!ticket.approved && ticket.status !== 'COMPLETED' && ticket.status !== 'CANCELLED' && (
            <button className="primary" onClick={() => void mutate(() => backend.approveMaintenance(ticket.id, ticketTenantId, principal.id, 'Đã chấp nhận phiếu bảo trì'), 'Đã chấp nhận phiếu bảo trì.')}>Chấp nhận phiếu</button>
          )}
          <button onClick={() => setMaintenanceWorkflow(ticket)}>Phân công & quy trình</button>
          {can(principal, ['MAINTENANCE_MANAGE']) && <button onClick={() => setMaintenanceEditor(ticket)}>Chỉnh sửa</button>}
          {can(principal, ['MAINTENANCE_MANAGE']) && (ticket.status === 'COMPLETED' ? <button onClick={() => void mutate(() => backend.reopenMaintenance(ticket.id, ticketTenantId, principal.id, 'Mở lại từ quản lý vận hành'), 'Đã mở lại phiếu.')}>Mở lại</button> : <button onClick={() => { const note = window.prompt('Ghi chú đóng phiếu:'); if (note?.trim()) void mutate(() => backend.closeMaintenance(ticket.id, ticketTenantId, principal.id, note.trim()), 'Đã đóng phiếu.') }}>Đóng phiếu</button>)}
        </>
      }
      if (isTechnician) {
        return <>
          <button className="primary" onClick={() => setMaintenanceWorkflow(ticket)}>Thực hiện bảo trì</button>
          {ticket.status !== 'COMPLETED' && ticket.status !== 'CANCELLED' && <button onClick={() => { const note = window.prompt('Ghi chú đóng phiếu:'); if (note?.trim()) void mutate(() => backend.closeMaintenance(ticket.id, ticketTenantId, principal.id, note.trim()), 'Đã đóng phiếu.') }}>Đóng phiếu</button>}
        </>
      }
      return <button onClick={() => setMaintenanceWorkflow(ticket)}>Theo dõi</button>
    }
    if (page === 'orders' && row.kind === 'REVIEW' && row.status !== 'RESOLVED' && can(principal, ['ORDER_REVIEW'])) { const review = row as unknown as PaymentReview; return <button onClick={() => void mutate(() => backend.resolveReview(review.reference), 'Đã hoàn tất đối soát.')}>Hoàn tất đối soát</button> }
    return null
  }
  async function logout() { try { await backend.logout() } catch { /* kết thúc phiên cục bộ */ } clearApiCache(); authToken.clear(); exit() }

  const activeBrand = !platform ? (tenants[0] ?? (rows[0] as unknown as Tenant | undefined) ?? null) : null

  return <div className="app-shell"><aside><div className="sidebar-brand"><strong>S.</strong><span>SCENTSTATION<small>TRUNG TÂM QUẢN TRỊ</small></span></div><nav>{menu.map((item) => <button key={item.id} className={page === item.id ? 'active' : ''} onClick={() => { clearApiCache(); closeMachineSlots(); setPage(item.id) }}><i>{item.icon}</i><span style={{ flex: 1 }}>{item.label}</span>{item.id === 'users' && pendingStaffCount > 0 && <span className="badge pending" style={{ padding: '2px 7px', fontSize: 10 }}>+{pendingStaffCount}</span>}</button>)}</nav><div className="sidebar-user" role="button" tabIndex={0} title="Nhấn để đổi tên đăng nhập hoặc mật khẩu" style={{ cursor: 'pointer' }} onClick={() => setShowAccountSettings(true)} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setShowAccountSettings(true) }}><span>{principal.username.slice(0, 2).toUpperCase()}</span><div><strong>{principal.username}</strong><small>{principal.roles.map((role) => valueLabels[role] ?? role).join(', ')} · ⚙ Tài khoản</small></div></div></aside><main className="workspace"><header><div><span className="live-dot" /> Hệ thống đang trực tuyến</div><div className="header-actions">{(platform || tenants.length > 1) && !!tenants.length && <label>Phạm vi thương hiệu<select value={tenantId ?? ''} onChange={(event) => { clearApiCache(); closeMachineSlots(); setTenantId(event.target.value ? Number(event.target.value) : undefined) }}>{platform && <option value="">Toàn nền tảng</option>}{tenants.map((tenant) => <option key={tenant.id} value={tenant.id}>{tenant.name} ({tenant.code})</option>)}</select></label>}{can(principal, ['NOTIFICATION_READ']) && <NotificationCenter principal={principal} tenantId={tenantId} />}{hasBrandProfile && <button type="button" className={`profile-trigger ${page === 'tenants' ? 'active' : ''}`} title="Hồ sơ thương hiệu" onClick={() => { clearApiCache(); closeMachineSlots(); setPage('tenants') }}><span className="profile-avatar">{(activeBrand?.name || principal.username).slice(0, 2).toUpperCase()}</span><span>{activeBrand?.name || 'Hồ sơ thương hiệu'}<small>Hồ sơ thương hiệu</small></span></button>}<button type="button" onClick={() => setShowAccountSettings(true)}>Tài khoản</button><button onClick={() => void logout()}>Đăng xuất</button></div></header><div className="content"><div className="page-heading"><div><p className="eyebrow">SCENTSTATION · QUẢN TRỊ VẬN HÀNH</p><h1>{isViewingMachineSlots ? `Đầu phun: ${selectedMachine.name}` : (!platform && page === 'tenants' ? 'Hồ sơ thương hiệu' : titles[page][0])}</h1><p>{isViewingMachineSlots ? `Máy #${selectedMachine.id} (${selectedMachine.serialNumber})` : (page === 'alerts' && isAdminRole ? 'Theo dõi sự cố thiết bị (Nhân viên vận hành và kỹ thuật viên phụ trách xử lý)' : page === 'maintenance' && isAdminRole ? 'Theo dõi tiến độ và chấp nhận phiếu bảo trì' : page === 'inventory' && platform ? 'Theo dõi lô nhập, chai nước hoa và lịch sử biến động tồn kho toàn nền tảng' : titles[page][1])}</p></div><div className="heading-actions">{isViewingMachineSlots && <button onClick={closeMachineSlots}>← Danh sách máy</button>}{page === 'tenants' && platform && <button className="primary" onClick={() => setTenantEditor('new')}>＋ Tạo thương hiệu</button>}{page === 'tenants' && !platform && activeBrand && can(principal, ['BRAND_PROFILE_EDIT']) && <button className="primary" onClick={() => setTenantEditor(activeBrand)}>✎ Chỉnh sửa hồ sơ</button>}{page === 'users' && <button className="primary" onClick={() => setUserEditor('new')}>＋ Tạo tài khoản</button>}{page === 'products' && !platform && tenantId && can(principal, ['CATALOG_MANAGE']) && <button className="primary" onClick={() => setShowProduct(true)}>＋ Thêm sản phẩm</button>}{page === 'campaigns' && !platform && tenantId && can(principal, ['CAMPAIGN_MANAGE']) && <button className="primary" onClick={() => setCampaignEditor('new')}>＋ Tạo chiến dịch</button>}{page === 'machines' && !platform && tenantId && principal.tenantId && can(principal, ['MACHINE_CONFIGURE']) && <>{!isViewingMachineSlots && <button onClick={() => setShowLocation(true)}>＋ Địa điểm</button>}<button className="primary" onClick={() => isViewingMachineSlots ? setSlotEditor('new') : setMachineEditor('new')}>＋ {isViewingMachineSlots ? 'Tạo đầu phun' : 'Đăng ký máy'}</button></>}{page === 'inventory' && !platform && tenantId && can(principal, ['INVENTORY_MANAGE', 'INVENTORY_ADJUST']) && <button className="primary" onClick={() => setShowInventoryForm(true)}>＋ Nghiệp vụ kho</button>}{page === 'maintenance' && (isOpsManager || isTechnician) && tenantId && can(principal, ['MAINTENANCE_MANAGE']) && <button className="primary" onClick={() => setMaintenanceEditor('new')}>＋ Tạo phiếu</button>}<button onClick={() => { clearApiCache(); if (isViewingMachineSlots && selectedMachine) void loadMachineSlots(selectedMachine); else void load() }} disabled={activeLoading}>↻ {activeLoading ? 'Đang tải' : 'Làm mới'}</button></div></div>{error && <div className="error-box"><span>{error}</span><button onClick={() => { if (isViewingMachineSlots && selectedMachine) void loadMachineSlots(selectedMachine); else void load() }}>Thử lại</button></div>}{notice && <div className="notice">✓ {notice}</div>}{page === 'users' && pendingStaffCount > 0 && <div className="notice" style={{ background: '#fff8e7', color: '#7a5c18', borderColor: '#e4cd8f' }}><span>🔔 Có <strong>{pendingStaffCount}</strong> yêu cầu đăng ký tài khoản nhân viên đang chờ Quản trị thương hiệu phê duyệt vai trò bên dưới.</span></div>}{page === 'dashboard' ? <Dashboard data={dashboard} operational={operationalReport} period={dashboardPeriod} setPeriod={setDashboardPeriod} principal={principal} /> : page === 'tenants' && !platform ? <section className="panel">{activeLoading ? <div className="empty"><span className="spinner" /> Đang tải hồ sơ thương hiệu…</div> : activeBrand ? <div className="brand-profile-card"><div className="brand-profile-head"><div className="brand-profile-identity">{activeBrand.logoUrl ? <img className="brand-profile-logo" src={activeBrand.logoUrl} alt={activeBrand.name} /> : <div className="brand-profile-logo">{activeBrand.name.slice(0, 1).toUpperCase()}</div>}<div><h2>{activeBrand.name}</h2><span className={`badge ${activeBrand.status.toLowerCase()}`}>{display(activeBrand.status, 'status')}</span></div></div>{can(principal, ['BRAND_PROFILE_EDIT']) && <button className="primary" onClick={() => setTenantEditor(activeBrand)}>Chỉnh sửa thông tin thương hiệu</button>}</div><div className="brand-profile-grid"><div className="brand-profile-item"><span>Mã thương hiệu</span><strong>{activeBrand.code}</strong></div><div className="brand-profile-item"><span>ID hệ thống</span><strong>#{activeBrand.id}</strong></div><div className="brand-profile-item"><span>Email liên hệ</span><strong>{activeBrand.contactEmail || '—'}</strong></div><div className="brand-profile-item"><span>Số điện thoại</span><strong>{activeBrand.contactPhone || '—'}</strong></div><div className="brand-profile-item full"><span>Mô tả thương hiệu</span><strong>{activeBrand.description || 'Chưa cập nhật mô tả.'}</strong></div><div className="brand-profile-item full"><span>Nội dung hiển thị trên Kiosk</span><strong>{activeBrand.kioskContent || 'Chưa cập nhật nội dung Kiosk.'}</strong></div></div></div> : <div className="empty">Không tìm thấy hồ sơ thương hiệu.</div>}</section> : <>{page === 'transactions' && <ReportingTools filters={transactionFilters} apply={setTransactionFilters} canExport={can(principal, ['REPORT_EXPORT'])} tenantId={tenantId} />}{page === 'orders' && <OrderOverview counts={orderCounts} selected={orderKind} select={(kind) => { setOrderKind(kind); setOrderStatusFilter('') }} />}{page === 'inventory' && <section className="panel toolbar"><nav className="order-tabs" aria-label="Phân loại tồn kho">{([['ALL', 'Tất cả', inventoryUnseen.batches + inventoryUnseen.bottles + inventoryUnseen.movements], ['BATCH', 'Lô hàng', inventoryUnseen.batches], ['BOTTLE', 'Chai nước hoa', inventoryUnseen.bottles], ['MOVEMENT', 'Biến động kho', inventoryUnseen.movements]] as const).map(([key, label, unseen]) => <button type="button" key={key} className={inventoryKind === key ? 'active' : ''} onClick={() => { setInventoryKind(key); if (key === 'ALL') markInventoryKindsSeen(['BATCH', 'BOTTLE', 'MOVEMENT'], rows); else markInventoryKindsSeen([key], rows) }}>{label}{unseen > 0 && <small>+{unseen}</small>}</button>)}</nav>{lowStockBottleCount > 0 && <span className="badge error">⚠ {lowStockBottleCount} chai gần hết / hết / hết hạn</span>}</section>}<section className="panel"><div className="toolbar"><label className="search">⌕<input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm trong dữ liệu đã tải…" /></label><div className="toolbar-filters">{page === 'orders' && <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}><span>Trạng thái:</span><select aria-label="Lọc trạng thái" value={orderStatusFilter} onChange={(event) => setOrderStatusFilter(event.target.value)}>{orderKind === 'REVIEW' ? <><option value="">Mọi trạng thái đối soát</option><option value="OPEN">Đang mở</option><option value="RESOLVED">Đã xử lý</option></> : orderKind === 'PAYMENT' ? <><option value="">Mọi trạng thái thanh toán</option><option value="PENDING">Đang chờ</option><option value="PAID">Đã thanh toán</option><option value="FAILED">Thất bại</option><option value="EXPIRED">Hết hạn</option><option value="REFUNDED">Đã hoàn tiền</option></> : <><option value="">{orderKind === 'ORDER' ? 'Mọi trạng thái đơn' : 'Mọi trạng thái'}</option><option value="PENDING">Đang chờ</option><option value="PAID">Đã thanh toán</option><option value="FAILED">Thất bại</option><option value="EXPIRED">Hết hạn</option><option value="CANCELLED">Đã hủy</option><option value="REFUNDED">Đã hoàn tiền</option></>}</select></label>}{page === 'alerts' && <><select aria-label="Lọc mức độ cảnh báo" value={alertSeverityFilter} onChange={(event) => setAlertSeverityFilter(event.target.value)}><option value="">Mọi mức độ</option><option value="LOW">Thấp</option><option value="MEDIUM">Trung bình</option><option value="HIGH">Cao</option><option value="CRITICAL">Nghiêm trọng</option></select><select aria-label="Lọc trạng thái cảnh báo" value={alertStatusFilter} onChange={(event) => setAlertStatusFilter(event.target.value)}><option value="">Mọi trạng thái</option><option value="OPEN">Đang mở</option><option value="ACKNOWLEDGED">Đã xác nhận</option><option value="ASSIGNED">Đã phân công</option><option value="ESCALATED">Đã chuyển cấp</option><option value="RESOLVED">Đã xử lý</option></select></>}{page === 'maintenance' && <label className="switch"><input type="checkbox" checked={onlyMyTickets} onChange={(event) => setOnlyMyTickets(event.target.checked)} />Chỉ phiếu được giao cho tôi</label>}<span>{filtered.length} bản ghi</span></div></div>{activeLoading ? <div className="empty"><span className="spinner" /> Đang tải dữ liệu thực…</div> : <Table rows={filtered} columns={visibleColumns} action={action} />}</section></>}</div></main>
    {showAccountSettings && <AccountSettingsModal principal={principal} brandCode={activeBrand?.code ?? tenants.find((t) => t.id === principal.tenantId)?.code} close={() => setShowAccountSettings(false)} savedUsername={(next, message) => { refreshPrincipal(next); setNotice(message) }} savedPassword={(message) => setNotice(message)} />}
    {showProduct && tenantId && <ProductForm tenantId={tenantId} close={() => setShowProduct(false)} saved={() => void load()} />}
    {productEditor && productEditor !== 'new' && tenantId && <ProductForm tenantId={tenantId} product={productEditor} close={() => setProductEditor(null)} saved={() => { setNotice('Đã cập nhật đầy đủ thông tin sản phẩm.'); void load() }} />}
    {tenantEditor && <TenantForm tenant={tenantEditor === 'new' ? undefined : tenantEditor} platform={platform} close={() => setTenantEditor(null)} saved={() => { setNotice('Đã lưu thông tin thương hiệu.'); void refreshTenants(); void load() }} />}
    {userEditor && <UserForm account={userEditor === 'new' ? undefined : userEditor} principal={principal} platform={platform} tenants={tenants} selectedTenantId={tenantId} close={() => setUserEditor(null)} saved={() => { setNotice('Đã lưu tài khoản và phân quyền.'); void load() }} />}
    {productPriceEditor && <ProductPriceForm product={productPriceEditor} close={() => setProductPriceEditor(null)} saved={() => { setNotice('Đã cập nhật giá sản phẩm.'); void load() }} />}
    {campaignEditor && tenantId && <CampaignForm campaign={campaignEditor === 'new' ? undefined : campaignEditor} tenantId={tenantId} products={catalogProducts} close={() => setCampaignEditor(null)} saved={(_saved, message) => { setNotice(message || 'Đã lưu chiến dịch.'); void load() }} />}
    {showLocation && tenantId && <LocationForm tenantId={tenantId} close={() => setShowLocation(false)} saved={(location) => { setLocations((current) => [...current, location]); setNotice('Đã tạo địa điểm.') }} />}
    {machineEditor && tenantId && <MachineForm machine={machineEditor === 'new' ? undefined : machineEditor} tenantId={tenantId} locations={locations} close={() => setMachineEditor(null)} saved={() => { setNotice('Đã lưu máy.'); void load() }} />}
    {slotEditor && selectedMachine && <SlotForm slot={slotEditor === 'new' ? undefined : slotEditor} machineId={selectedMachine.id} tenantId={Number(selectedMachine.tenantId)} principal={principal} products={catalogProducts} bottles={inventoryBottles} activeInstallation={slotEditor === 'new' ? null : (machineInstallations.find((inst) => inst.slotId === slotEditor.id) ?? null)} close={() => setSlotEditor(null)} saved={(_savedSlot, message) => { setNotice(message || 'Đã lưu đầu phun.'); void loadMachineSlots(selectedMachine) }} />}
    {showInventoryForm && tenantId && <InventoryForm tenantId={tenantId} principal={principal} batches={inventoryBatches} bottles={inventoryBottles} initialProductId={inventoryInitialProductId} close={() => { setShowInventoryForm(false); setInventoryInitialProductId(undefined) }} saved={(message) => { clearApiCache(); setNotice(message || 'Đã hoàn thành nghiệp vụ kho.'); void load() }} />}
    {showInventoryWorkflow && (selectedMachine ? Number(selectedMachine.tenantId) : tenantId) && <InventoryWorkflowForm tenantId={selectedMachine ? Number(selectedMachine.tenantId) : (tenantId as number)} principal={principal} bottles={inventoryBottles} prefill={inventoryWorkflowPrefill} close={() => { setShowInventoryWorkflow(false); setInventoryWorkflowPrefill(undefined) }} saved={() => { clearApiCache(); setNotice('Đã ghi nhận nạp thêm / lắp / thay chai.'); if (isViewingMachineSlots && selectedMachine) void loadMachineSlots(selectedMachine); else void load() }} />}
    {inventoryDisposeTarget && <InventoryDisposeForm target={inventoryDisposeTarget} principal={principal} close={() => setInventoryDisposeTarget(null)} saved={(message) => { clearApiCache(); setNotice(message); void load() }} />}
    {maintenanceEditor && tenantId && <MaintenanceForm ticket={maintenanceEditor === 'new' ? undefined : maintenanceEditor} prefill={maintenanceEditor === 'new' ? maintenancePrefill : undefined} tenantId={tenantId} principal={principal} close={() => { setMaintenanceEditor(null); setMaintenancePrefill(undefined) }} saved={() => { setNotice('Đã lưu phiếu bảo trì.'); setMaintenancePrefill(undefined); void load() }} />}
    {maintenanceWorkflow && <MaintenanceWorkflowForm ticket={maintenanceWorkflow} tenantId={maintenanceWorkflow.tenantId || tenantId || principal.tenantId || 1} principal={principal} close={() => setMaintenanceWorkflow(null)} saved={() => { setNotice('Đã cập nhật quy trình bảo trì.'); void load() }} />}
  </div>
}

export default function App() {
  const [principal, setPrincipal] = useState<Principal | null>(null)
  const [restoring, setRestoring] = useState(Boolean(authToken.get()))
  const [sessionNotice, setSessionNotice] = useState('')
  useEffect(() => {
    const unauthorized = (event: Event) => {
      const detail = (event as CustomEvent<string>).detail
      setSessionNotice(detail || 'Phiên đăng nhập đã hết hạn hoặc tài khoản đã bị khóa. Vui lòng đăng nhập lại.')
      setPrincipal(null)
    }
    const forbidden = () => {
      if (authToken.get()) {
        backend.me().then(setPrincipal).catch(() => { /* 401 đã xử lý bởi sự kiện unauthorized */ })
      }
    }
    window.addEventListener('scentstation:unauthorized', unauthorized)
    window.addEventListener('scentstation:forbidden', forbidden)
    if (authToken.get()) {
      backend.me().then(setPrincipal).catch(() => authToken.clear()).finally(() => setRestoring(false))
    } else {
      setRestoring(false)
    }
    return () => {
      window.removeEventListener('scentstation:unauthorized', unauthorized)
      window.removeEventListener('scentstation:forbidden', forbidden)
    }
  }, [])
  if (restoring) return <div className="boot"><span className="spinner" /> Đang khôi phục phiên bảo mật…</div>
  if (!principal) return <Login done={(next) => { setSessionNotice(''); setPrincipal(next) }} sessionNotice={sessionNotice} />
  const needsStaffOnboarding = !principal.roles.includes('PLATFORM_SUPER_ADMIN') && principal.grants.length === 0
  if (needsStaffOnboarding) {
    return <StaffOnboarding principal={principal} refreshPrincipal={(next) => setPrincipal(next)} exit={() => { setSessionNotice(''); setPrincipal(null) }} />
  }
  return <Admin principal={principal} refreshPrincipal={(next) => setPrincipal(next)} exit={() => { setSessionNotice(''); setPrincipal(null) }} />
}

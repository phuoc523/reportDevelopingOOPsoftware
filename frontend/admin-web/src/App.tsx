import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import './Admin.css'
import { ApiError, authToken, clearApiCache } from './api/client'
import { backend } from './api/scentStation'
import type { Account, Alert, Bottle, Campaign, InventoryBatch, Location, Machine, MaintenanceTicket, Order, Payment, PaymentReview, Permission, Principal, Product, Slot, Tenant } from './api/types'
import { CampaignForm, ProductPriceForm } from './components/CatalogForms'
import { TenantForm, UserForm } from './components/IdentityForms'
import { LocationForm, MachineForm, SlotForm } from './components/MachineForms'
import { InventoryDisposeForm, InventoryForm, InventoryWorkflowForm, MaintenanceForm, MaintenanceWorkflowForm } from './components/OperationForms'
import type { InventoryDisposeTarget } from './components/OperationForms'
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
  { id: 'inventory', label: 'Tồn kho', icon: '▤', permissions: ['INVENTORY_READ'] },
  { id: 'alerts', label: 'Cảnh báo', icon: '⚑', permissions: ['ALERT_READ'] },
  { id: 'maintenance', label: 'Bảo trì', icon: '⚙', permissions: ['MAINTENANCE_READ'] },
  { id: 'orders', label: 'Đơn hàng & thanh toán', icon: '▧', permissions: ['ORDER_READ'] },
  { id: 'transactions', label: 'Giao dịch & báo cáo', icon: '▥', permissions: ['REPORT_READ', 'PLATFORM_REPORT_READ'] },
  { id: 'audit', label: 'Nhật ký hệ thống', icon: '≡', permissions: ['AUDIT_READ', 'PLATFORM_AUDIT_READ'] },
]

const labels: Record<string, string> = {
  id: 'ID', kind: 'Loại dữ liệu', name: 'Tên', code: 'Mã', status: 'Trạng thái', tenantId: 'Mã thương hiệu', tenantName: 'Thương hiệu', productId: 'Mã sản phẩm', productName: 'Sản phẩm', machineId: 'Máy',
  username: 'Tài khoản', fullName: 'Họ tên', email: 'Email', roles: 'Vai trò', active: 'Hoạt động', locked: 'Đã khóa',
  defaultPrice: 'Phí trải nghiệm', bottlePrice: 'Giá bán chai', bottleVolumeMl: 'Dung tích chai (ml)', bottleStock: 'Tồn chai bán lẻ', orderType: 'Loại đơn', available: 'Khả dụng', fragranceNotes: 'Nhóm hương', serialNumber: 'Serial', locationId: 'Địa điểm', machineName: 'Kiosk',
  operationalStatus: 'Vận hành', deviceRegistrationStatus: 'Đăng ký thiết bị', slotNumber: 'Đầu phun', slotCode: 'Mã đầu phun', stockThreshold: 'Ngưỡng tồn', price: 'Giá xịt thử', locationName: 'Địa điểm', imageUrl: 'Hình ảnh',
  summary: 'Nội dung', severity: 'Mức độ', type: 'Loại', title: 'Tiêu đề', priority: 'Ưu tiên', critical: 'Khẩn cấp',
  totalAmount: 'Tổng tiền', currency: 'Tiền tệ', reference: 'Tham chiếu', orderId: 'Đơn hàng', amount: 'Số tiền', items: 'Sản phẩm',
  createdAt: 'Tạo lúc', updatedAt: 'Cập nhật', lastSeenAt: 'Gần nhất', occurredAt: 'Thời gian', action: 'Hành động', outcome: 'Kết quả',
  totalTransactions: 'Tổng giao dịch', successfulTransactions: 'Thành công', pendingTransactions: 'Đang chờ', failedTransactions: 'Thất bại', expiredTransactions: 'Hết hạn', refundedTransactions: 'Đã hoàn tiền',
  total: 'Tổng số', online: 'Trực tuyến', offline: 'Ngoại tuyến', unstable: 'Không ổn định', disabled: 'Đã tắt', maintenance: 'Đang bảo trì', error: 'Lỗi', unknown: 'Không xác định',
  open: 'Đang mở', acknowledged: 'Đã xác nhận', assigned: 'Đã phân công', escalated: 'Đã chuyển cấp', resolved: 'Đã xử lý',
  bottles: 'Tổng số chai', installed: 'Đã lắp', nearlyEmpty: 'Sắp hết', empty: 'Đã hết', damaged: 'Hư hỏng',
  batchCode: 'Mã lô', bottleCode: 'Mã chai',
  initialQuantity: 'Dung tích ban đầu (ml)', currentQuantity: 'Còn lại (ml)', calibratedConsumptionPerSpray: 'Mỗi lượt xịt (ml)',
  contactPhone: 'Điện thoại', machineCount: 'Số máy', onlineMachineCount: 'Máy online', adminCount: 'Quản trị viên', assignedTo: 'Người xử lý', assignedToActorId: 'Kỹ thuật viên', dispensingStatus: 'Kết quả phun',
  dueAt: 'Hạn xử lý', receivedAt: 'Ngày nhập', expiryDate: 'Hạn sử dụng', slotId: 'Slot', movementType: 'Loại biến động', reason: 'Lý do', reconciliationFlag: 'Đối soát phun', approved: 'Duyệt phiếu',
}

const titles: Record<Page, [string, string]> = {
  dashboard: ['Tổng quan vận hành', 'Số liệu tổng hợp theo thời gian thực từ các dịch vụ'], tenants: ['Thương hiệu', 'Hồ sơ và trạng thái hoạt động của từng thương hiệu'],
  users: ['Tài khoản & phân quyền', 'Quản lý vai trò, quyền bổ sung và phạm vi vận hành'], products: ['Danh mục sản phẩm', 'Sản phẩm nước hoa theo từng thương hiệu'],
  campaigns: ['Chiến dịch khuyến mãi', 'Mức giảm giá và thời gian áp dụng theo sản phẩm'], machines: ['Kiosk', 'Trạng thái kết nối, địa điểm lắp đặt và quản lý đầu phun'],
  inventory: ['Tồn kho', 'Lô nhập, chai nước hoa và lịch sử thay đổi tồn kho'], alerts: ['Cảnh báo', 'Theo dõi và xử lý sự cố thiết bị'],
  maintenance: ['Bảo trì', 'Phiếu bảo trì theo phạm vi vận hành'], orders: ['Đơn hàng & thanh toán', 'Theo dõi đơn hàng, giao dịch thanh toán và đối soát'],
  transactions: ['Giao dịch & báo cáo', 'Dữ liệu giao dịch được đồng bộ từ dịch vụ đơn hàng'], audit: ['Nhật ký hệ thống', 'Lịch sử thao tác được backend ghi nhận'],
}

const valueLabels: Record<string, string> = {
  TRIAL: 'Trải nghiệm xịt thử', BOTTLE: 'Mua chai',
  ACTIVE: 'Đang hoạt động', SUSPENDED: 'Tạm ngưng', ONLINE: 'Trực tuyến', OFFLINE: 'Ngoại tuyến', UNSTABLE: 'Không ổn định', DISABLED: 'Đã tắt', ERROR: 'Lỗi', MAINTENANCE: 'Đang bảo trì',
  AVAILABLE: 'Sẵn sàng', INSTALLED: 'Đã lắp', NEARLY_EMPTY: 'Sắp hết', EMPTY: 'Đã hết', DAMAGED: 'Hư hỏng', EXPIRED: 'Hết hạn',
  INSTALL: 'Lắp vào máy', REMOVE: 'Tháo khỏi máy', REPLACE: 'Thay chai', REFILL: 'Refill', CUSTOMER_CONSUMPTION: 'Khách phun', DIAGNOSTIC_CONSUMPTION: 'Phun kiểm tra', DISPOSE: 'Hủy / thanh lý', ADJUSTMENT: 'Điều chỉnh',
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
  const [username, setUsername] = useState(''); const [password, setPassword] = useState('')
  const [error, setError] = useState(''); const [loading, setLoading] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setLoading(true)
    try { await backend.login(username.trim(), password); done(await backend.me()) }
    catch (reason) { authToken.clear(); setError(errorText(reason)) } finally { setLoading(false) }
  }
  return <main className="login-shell"><section className="login-art"><div className="brand-mark">S.</div><p>SCENTSTATION</p><h1>Vận hành mọi điểm chạm hương thơm.</h1><span>Trung tâm quản trị vận hành</span></section><section className="login-panel"><form onSubmit={submit} className="login-card"><p className="eyebrow">TRUY CẬP QUẢN TRỊ AN TOÀN</p><h2>Đăng nhập</h2><p>Sử dụng tài khoản quản trị được cấp. Phiên đăng nhập được bảo vệ bằng JWT.</p>{sessionNotice && !error && <div className="error-box">{sessionNotice}</div>}<label>Tên đăng nhập<input autoFocus required value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" /></label><label>Mật khẩu<input required type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" /></label>{error && <div className="error-box">{error}</div>}<button className="primary" disabled={loading}>{loading ? 'Đang xác thực…' : 'Đăng nhập'}</button></form></section></main>
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
  const groupNames: Record<string, string> = { transactions: 'Giao dịch', machines: 'Kiosk', alerts: 'Cảnh báo', inventory: 'Tồn kho' }
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
          <span>Tồn kho & Refill</span>
          <strong>{refillCandidates.length} chai</strong>
          <small>Chai cần refill / sắp hết: {refillCandidates.length} · Tổng còn lại: {display(invPerf.totalRemaining ?? 0, 'quantity')} ml</small>
          <small>Refill: {display(invPerf.refills ?? 0)} · Thay chai: {display(invPerf.replacements ?? 0)} · Điều chỉnh: {display(invPerf.adjustments ?? 0)}</small>
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
  return <div className="modal-backdrop"><form className="modal" onSubmit={submit}><h3>{product ? `Cập nhật: ${product.name}` : 'Thêm sản phẩm'}</h3><label>Tên<input required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label><label>Nhóm hương<input value={form.fragranceNotes} onChange={(event) => setForm({ ...form, fragranceNotes: event.target.value })} /></label><label>Mô tả<textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label><label>Đường dẫn hình ảnh<input type="url" value={form.imageUrl} onChange={(event) => setForm({ ...form, imageUrl: event.target.value })} /></label><label>Phí trải nghiệm<input required min="0" step="1000" type="number" value={form.defaultPrice} onChange={(event) => setForm({ ...form, defaultPrice: event.target.value })} /></label><label>Giá bán chai<input required min="1" step="1000" type="number" value={form.bottlePrice} onChange={(event) => setForm({ ...form, bottlePrice: event.target.value })} /></label><label>Dung tích chai (ml)<input required min="1" type="number" value={form.bottleVolumeMl} onChange={(event) => setForm({ ...form, bottleVolumeMl: event.target.value })} /></label><label>Tồn chai bán lẻ<input required min="0" type="number" value={form.bottleStock} onChange={(event) => setForm({ ...form, bottleStock: event.target.value })} /></label><label className="switch"><input type="checkbox" checked={form.available} onChange={(event) => setForm({ ...form, available: event.target.checked })} />Đang kinh doanh</label>{error && <div className="error-box">{error}</div>}<div className="modal-actions"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu sản phẩm'}</button></div></form></div>
}

function Admin({ principal, exit }: { principal: Principal; exit: () => void }) {
  const platform = principal.roles.includes('PLATFORM_SUPER_ADMIN')
  const isAdminRole = platform || principal.roles.includes('BRAND_ADMIN')
  const isOpsManager = principal.roles.includes('OPERATIONS_MANAGER')
  const isTechnician = principal.roles.includes('TECHNICIAN')
  const menu = useMemo(() => pages.filter((item) => can(principal, item.permissions)), [principal])
  const hashPage = window.location.hash.slice(1) as Page
  const [page, setPage] = useState<Page>(menu.some((item) => item.id === hashPage) ? hashPage : (menu[0]?.id ?? 'products'))
  const [tenants, setTenants] = useState<Tenant[]>([]); const [tenantId, setTenantId] = useState<number | undefined>(principal.tenantId ?? undefined)
  const [rows, setRows] = useState<Row[]>([]); const [dashboard, setDashboard] = useState<Row | null>(null); const [operationalReport, setOperationalReport] = useState<Row | null>(null); const [dashboardPeriod, setDashboardPeriod] = useState<DashboardPeriod>('month'); const [columns, setColumns] = useState<string[]>([])
  const [loading, setLoading] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [search, setSearch] = useState('')
  const [orderKind, setOrderKind] = useState<'ALL' | 'ORDER' | 'PAYMENT' | 'REVIEW'>('ALL'); const [orderStatusFilter, setOrderStatusFilter] = useState('')
  const [inventoryKind, setInventoryKind] = useState<'ALL' | 'BATCH' | 'BOTTLE' | 'MOVEMENT'>('ALL')
  const [alertSeverityFilter, setAlertSeverityFilter] = useState(''); const [alertStatusFilter, setAlertStatusFilter] = useState('')
  const [onlyMyTickets, setOnlyMyTickets] = useState(false)
  const [maintenancePrefill, setMaintenancePrefill] = useState<{ machineId?: number; title?: string; description?: string; category?: string; severity?: string } | undefined>(undefined)
  const [productEditor, setProductEditor] = useState<Product | 'new' | null>(null); const [selectedMachine, setSelectedMachine] = useState<Machine | null>(null); const [machineSlots, setMachineSlots] = useState<Row[]>([]); const [slotsLoading, setSlotsLoading] = useState(false)
  const showProduct = productEditor === 'new'; const setShowProduct = (show: boolean) => setProductEditor(show ? 'new' : null)
  const [tenantEditor, setTenantEditor] = useState<Tenant | 'new' | null>(null); const [userEditor, setUserEditor] = useState<Account | 'new' | null>(null)
  const [productPriceEditor, setProductPriceEditor] = useState<Product | null>(null); const [campaignEditor, setCampaignEditor] = useState<Campaign | 'new' | null>(null)
  const [catalogProducts, setCatalogProducts] = useState<Product[]>([])
  const [locations, setLocations] = useState<Location[]>([]); const [machineEditor, setMachineEditor] = useState<Machine | 'new' | null>(null); const [slotEditor, setSlotEditor] = useState<Slot | 'new' | null>(null); const [showLocation, setShowLocation] = useState(false)
  const [inventoryBatches, setInventoryBatches] = useState<InventoryBatch[]>([]); const [inventoryBottles, setInventoryBottles] = useState<Bottle[]>([]); const [showInventoryForm, setShowInventoryForm] = useState(false); const [showInventoryWorkflow, setShowInventoryWorkflow] = useState(false); const [inventoryDisposeTarget, setInventoryDisposeTarget] = useState<InventoryDisposeTarget | null>(null); const [maintenanceEditor, setMaintenanceEditor] = useState<MaintenanceTicket | 'new' | null>(null); const [maintenanceWorkflow, setMaintenanceWorkflow] = useState<MaintenanceTicket | null>(null)
  const [transactionFilters, setTransactionFilters] = useState<TransactionFilters>({})

  const refreshTenants = useCallback(async () => {
    const list = platform ? await backend.tenants() : [await backend.brandTenant()]
    setTenants(list); if (list.length && !platform) setTenantId((current) => current ?? list[0].id)
    return list
  }, [platform])
  useEffect(() => { void refreshTenants().catch((reason) => { if ((reason as ApiError).status !== 403) setError(errorText(reason)) }) }, [refreshTenants])
  useEffect(() => {
    setNotice('')
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}#${page}`)
  }, [page])
  useEffect(() => {
    if (menu.length && !menu.some((item) => item.id === page)) {
      setPage(menu[0].id)
      setError('Quyền truy cập của bạn đã thay đổi. Đã chuyển về trang được phép.')
    }
  }, [menu, page])
  useEffect(() => {
    const onHashChange = () => {
      const requested = window.location.hash.slice(1) as Page
      if (!requested || requested === page) return
      if (menu.some((item) => item.id === requested)) {
        setSelectedMachine(null)
        setPage(requested)
      } else {
        window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}#${page}`)
        setError('Bạn không có quyền truy cập trang được yêu cầu.')
      }
    }
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [menu, page])

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
        case 'users': data = await backend.users(platform, tenantId) as unknown as Row[]; break
        case 'products': { const products = (await Promise.all(targetTenantIds.map((id) => backend.products(id)))).flat(); setCatalogProducts(products); data = products.map((product) => ({ ...product, tenantName: tenantNameOf(product.tenantId) })) as unknown as Row[]; break }
        case 'campaigns': { const groups = await Promise.all(targetTenantIds.map(async (id) => { const [campaigns, products] = await Promise.all([backend.campaigns(id), backend.products(id)]); return { campaigns, products } })); const products = groups.flatMap((group) => group.products); setCatalogProducts(products); const productMap = new Map(products.map((p) => [p.id, p.name])); data = groups.flatMap((group) => group.campaigns).map((campaign) => ({ ...campaign, tenantName: tenantNameOf(campaign.tenantId), productName: productMap.get(campaign.productId) ?? `#${campaign.productId}` })) as unknown as Row[]; break }
        case 'machines': { const groups = await Promise.all(targetTenantIds.map(async (id) => { const [machines, machineLocations] = await Promise.all([backend.machines(id), backend.locations(id)]); return { machines, machineLocations } })); const machineLocations = groups.flatMap((group) => group.machineLocations); setLocations(machineLocations); const locMap = new Map(machineLocations.map((l) => [l.locationId, l.name])); data = groups.flatMap((group) => group.machines).map((machine) => ({ ...machine, kind: 'MACHINE', tenantName: tenantNameOf(machine.tenantId), locationName: locMap.get(machine.locationId) ?? machine.locationId })) as unknown as Row[]; break }
        case 'inventory': { const groups = await Promise.all(targetTenantIds.map(async (id) => { const [batches, bottles, movements, products] = await Promise.all([backend.batches(id), backend.bottles(id), backend.movements(id), can(principal, ['CATALOG_READ']) ? backend.products(id) : Promise.resolve([])]); return { id, batches, bottles, movements, products } })); setInventoryBatches(groups.flatMap((group) => group.batches)); setInventoryBottles(groups.flatMap((group) => group.bottles)); data = groups.flatMap((group) => { const tenantName = tenantNameOf(group.id); const productMap = new Map(group.products.map((p) => [p.id, p.name])); const bottleMap = new Map(group.bottles.map((b) => [b.id, b.productId])); const productName = (productId?: number) => (productId ? (productMap.get(productId) ?? `Sản phẩm #${productId}`) : '—'); return [...group.batches.map((item) => ({ kind: 'BATCH', tenantName, ...item, status: item.status ?? 'ACTIVE', reason: item.cancelReason ?? undefined, productName: productName(item.productId) })), ...group.bottles.map((item) => ({ kind: 'BOTTLE', tenantName, ...item, productName: productName(item.productId) })), ...group.movements.map((item) => ({ kind: 'MOVEMENT', tenantName, ...item, productName: productName(bottleMap.get(Number(item.bottleId))) }))] }) as unknown as Row[]; break }
        case 'alerts': { const alertGroups = await Promise.all(targetTenantIds.map((id) => backend.alerts(id))); data = alertGroups.flat().map((alert) => ({ ...alert, tenantName: tenantNameOf(alert.tenantId) })) as unknown as Row[]; break }
        case 'maintenance': { const groups = await Promise.all(targetTenantIds.map(async (id) => ({ id, tickets: await backend.maintenance(id) }))); data = groups.flatMap((group) => group.tickets.map((ticket) => ({ ...ticket, tenantName: tenantNameOf(group.id) }))) as unknown as Row[]; break }
        case 'orders': { const reviews = can(principal, ['ORDER_REVIEW']) ? backend.reviews() : Promise.resolve([]); const [orders, payments, paymentReviews] = await Promise.all([backend.orders(), backend.payments(), reviews]); const orderTenantMap = new Map(orders.map((order) => [order.id, order.tenantId])); const visibleOrders = tenantId ? orders.filter((order) => String(order.tenantId) === String(tenantId)) : orders; const visibleOrderIds = new Set(visibleOrders.map((order) => order.id)); const visiblePayments = payments.filter((payment) => visibleOrderIds.has(payment.orderId)); const visibleReviews = paymentReviews.filter((review) => visibleOrderIds.has(review.orderId)); data = [...visibleOrders.map((item) => { const needsCheck = item.manualReviewRequired || item.dispensingStatus === 'FAILED' || (item.status === 'PAID' && item.dispensingStatus !== 'DISPENSED'); return { kind: 'ORDER', ...item, reconciliationFlag: needsCheck ? '⚠ Cần đối soát / chưa xác nhận phun' : 'Bình thường', tenantName: tenantNameOf(item.tenantId), items: item.items.map((orderItem) => `${orderItem.productName} ×${orderItem.quantity}`).join(', ') } }), ...visiblePayments.map((item) => ({ kind: 'PAYMENT', ...item, tenantName: tenantNameOf(orderTenantMap.get(item.orderId)) })), ...visibleReviews.map((item) => ({ kind: 'REVIEW', ...item, reconciliationFlag: '⚠ Cần đối soát', tenantName: tenantNameOf(orderTenantMap.get(item.orderId)) }))] as unknown as Row[]; break }
        case 'transactions': data = (await backend.transactions({ ...transactionFilters, tenantId })).content as unknown as Row[]; break
        case 'audit': data = await backend.audit(tenantId); break
      }
      setRows(data)
      const map: Record<Page, string[]> = { dashboard: [], tenants: ['id', 'code', 'name', 'status', 'contactEmail', 'contactPhone', 'machineCount', 'onlineMachineCount', 'adminCount'], users: ['id', 'username', 'fullName', 'email', 'roles', 'tenantId', 'active', 'locked'], products: ['imageUrl', 'tenantName', 'name', 'fragranceNotes', 'defaultPrice', 'bottlePrice', 'bottleVolumeMl', 'bottleStock', 'available', 'updatedAt'], campaigns: ['tenantName', 'id', 'name', 'productName', 'discountType', 'discountValue', 'active', 'startAt', 'endAt'], machines: ['tenantName', 'id', 'name', 'serialNumber', 'locationName', 'deviceRegistrationStatus', 'operationalStatus', 'lastSeenAt'], inventory: ['tenantName', 'kind', 'id', 'productName', 'batchCode', 'bottleCode', 'movementType', 'machineId', 'slotId', 'initialQuantity', 'currentQuantity', 'calibratedConsumptionPerSpray', 'receivedAt', 'expiryDate', 'reason', 'status', 'updatedAt'], alerts: ['tenantName', 'id', 'severity', 'status', 'type', 'summary', 'machineId', 'locationId', 'assignedTo', 'lastSeenAt'], maintenance: ['tenantName', 'id', 'severity', 'status', 'approved', 'title', 'machineId', 'priority', 'assignedToActorId', 'dueAt', 'updatedAt'], orders: ['kind', 'tenantName', 'id', 'reference', 'orderId', 'orderType', 'items', 'totalAmount', 'amount', 'currency', 'status', 'dispensingStatus', 'reconciliationFlag', 'manualReviewRequired', 'createdAt'], transactions: ['id', 'reference', 'orderId', 'tenantId', 'machineId', 'productId', 'amount', 'currency', 'status', 'createdAt', 'paidAt'], audit: ['id', 'occurredAt', 'actorId', 'tenantId', 'action', 'targetType', 'targetId', 'outcome'] }
      setColumns(map[page].filter((key) => data.some((item) => key in item)))
    } catch (reason) { setRows([]); setError(errorText(reason)) } finally { setLoading(false) }
  }, [page, tenantId, platform, principal, transactionFilters, tenants, dashboardPeriod])
  useEffect(() => { void load() }, [load])

  const loadMachineSlots = useCallback(async (machine: Machine) => {
    setSlotsLoading(true); setError('')
    try {
      const [slots, products] = await Promise.all([
        backend.slots(machine.id),
        can(principal, ['CATALOG_READ']) ? backend.products(Number(machine.tenantId)).catch(() => []) : Promise.resolve([]),
      ])
      const productMap = new Map(products.map((p) => [String(p.id), p.name]))
      setMachineSlots(slots.map((s) => ({ ...s, productName: s.productId ? (productMap.get(String(s.productId)) ?? `Sản phẩm #${s.productId}`) : '—' })) as unknown as Row[])
    } catch (reason) {
      setMachineSlots([])
      setError(errorText(reason))
    } finally {
      setSlotsLoading(false)
    }
  }, [principal])

  const openMachineSlots = useCallback((machine: Machine) => {
    setNotice(''); setError(''); setSearch(''); setMachineSlots([])
    setSelectedMachine(machine)
    void loadMachineSlots(machine)
  }, [loadMachineSlots])

  const closeMachineSlots = useCallback(() => {
    setSelectedMachine(null); setMachineSlots([]); setNotice(''); setError(''); setSearch('')
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
  const inventoryCounts = { batches: rows.filter((item) => item.kind === 'BATCH').length, bottles: rows.filter((item) => item.kind === 'BOTTLE').length, movements: rows.filter((item) => item.kind === 'MOVEMENT').length }
  const orderCounts = { orders: rows.filter((item) => item.kind === 'ORDER').length, payments: rows.filter((item) => item.kind === 'PAYMENT').length, reviews: rows.filter((item) => item.kind === 'REVIEW').length }
  const visibleColumns = useMemo(() => {
    if (isViewingMachineSlots) return ['id', 'slotNumber', 'slotCode', 'productName', 'status', 'available', 'stockThreshold', 'price']
    if (page === 'orders' && orderKind === 'ORDER') return columns.filter((col) => !['reference', 'orderId', 'amount'].includes(col))
    if (page === 'orders' && orderKind === 'PAYMENT') return columns.filter((col) => !['id', 'orderType', 'items', 'totalAmount', 'dispensingStatus', 'reconciliationFlag', 'manualReviewRequired'].includes(col))
    if (page === 'orders' && orderKind === 'REVIEW') return columns.filter((col) => !['id', 'orderType', 'items', 'totalAmount', 'amount', 'currency', 'dispensingStatus'].includes(col))
    if (page === 'inventory' && inventoryKind === 'BATCH') return columns.filter((col) => !['bottleCode', 'movementType', 'machineId', 'slotId', 'initialQuantity', 'currentQuantity', 'calibratedConsumptionPerSpray'].includes(col))
    if (page === 'inventory' && inventoryKind === 'BOTTLE') return columns.filter((col) => !['batchCode', 'movementType', 'machineId', 'slotId', 'receivedAt', 'expiryDate', 'reason'].includes(col))
    if (page === 'inventory' && inventoryKind === 'MOVEMENT') return columns.filter((col) => !['batchCode', 'bottleCode', 'initialQuantity', 'currentQuantity', 'calibratedConsumptionPerSpray', 'receivedAt', 'expiryDate', 'status'].includes(col))
    return columns
  }, [isViewingMachineSlots, page, orderKind, inventoryKind, columns])
  async function mutate(job: () => Promise<unknown>, message: string) { try { setError(''); await job(); setNotice(message); await load() } catch (reason) { setError(errorText(reason)) } }
  function action(row: Row) {
    if (page === 'tenants') { const tenant = row as unknown as Tenant; const editable = platform || can(principal, ['BRAND_PROFILE_EDIT']); if (!editable) return null; return <><button onClick={() => setTenantEditor(tenant)}>Chỉnh sửa</button>{platform && <button className={tenant.status === 'ACTIVE' ? 'danger' : ''} onClick={() => { if (tenant.status === 'ACTIVE' && !window.confirm(`Thương hiệu ${tenant.name} đang hoạt động. Tạm ngưng sẽ chặn các nghiệp vụ thuộc thương hiệu này. Bạn chắc chắn muốn tiếp tục?`)) return; void mutate(() => backend.updateTenantStatus(tenant.id, tenant.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'), tenant.status === 'ACTIVE' ? 'Đã tạm ngưng thương hiệu.' : 'Đã kích hoạt thương hiệu.') }}>{tenant.status === 'ACTIVE' ? 'Tạm ngưng' : 'Kích hoạt'}</button>}</> }
    if (page === 'users') { const account = row as unknown as Account; return <><button onClick={() => setUserEditor(account)}>Quản lý</button><button className={account.locked ? '' : 'danger'} onClick={() => { if (!account.locked && !window.confirm(`Bạn có chắc chắn muốn khóa tài khoản ${account.username}? Phiên đăng nhập hiện tại của tài khoản này sẽ bị vô hiệu hóa.`)) return; void mutate(() => backend.updateUserStatus(platform, account.id, account.active, !account.locked), account.locked ? 'Đã mở khóa tài khoản.' : 'Đã khóa tài khoản.') }}>{account.locked ? 'Mở khóa' : 'Khóa'}</button></> }
    if (page === 'products' && tenantId && !platform && can(principal, ['CATALOG_MANAGE'])) { const product = row as unknown as Product; return <><button onClick={() => setProductEditor(product)}>Chỉnh sửa</button><button onClick={() => setProductPriceEditor(product)}>Cập nhật nhanh giá</button><button onClick={() => void mutate(() => backend.setProductAvailability(product.id, !product.available), 'Đã cập nhật sản phẩm.')}>{product.available ? 'Tạm ngưng' : 'Mở bán'}</button></> }
    if (page === 'campaigns' && tenantId && can(principal, ['CAMPAIGN_MANAGE'])) return <button onClick={() => setCampaignEditor(row as unknown as Campaign)}>Chỉnh sửa</button>
    if (page === 'inventory' && !platform && can(principal, ['INVENTORY_MANAGE'])) {
      if (row.kind === 'BATCH' && String(row.status ?? 'ACTIVE').toUpperCase() !== 'CANCELLED') {
        return <button className="danger" onClick={() => setInventoryDisposeTarget({ kind: 'BATCH', item: row as unknown as InventoryBatch })}>Hủy lô</button>
      }
      if (row.kind === 'BOTTLE' && !['DAMAGED', 'EXPIRED'].includes(String(row.status ?? '').toUpperCase())) {
        return <>{tenantId && <button onClick={() => setShowInventoryWorkflow(true)}>Refill / lắp / thay chai</button>}<button className="danger" onClick={() => setInventoryDisposeTarget({ kind: 'BOTTLE', item: row as unknown as Bottle })}>Hủy / báo hư</button></>
      }
      return null
    }
    if (isViewingMachineSlots && selectedMachine) { if (!can(principal, ['MACHINE_CONFIGURE'])) return null; const slot = row as unknown as Slot; return <><button onClick={() => setSlotEditor(slot)}>Chỉnh sửa</button><button onClick={() => void (async () => { try { await backend.setSlotEnabled(selectedMachine.id, slot.id, !slot.enabled); await loadMachineSlots(selectedMachine); setNotice(slot.enabled ? 'Đã tắt đầu phun.' : 'Đã bật đầu phun.') } catch (reason) { setError(errorText(reason)) } })()}>{slot.enabled ? 'Tắt' : 'Bật'}</button></> }
    if (page === 'machines') { const machine = row as unknown as Machine; return <><button onClick={() => openMachineSlots(machine)}>Xem đầu phun</button>{tenantId && can(principal, ['MACHINE_CONFIGURE']) && <button onClick={() => setMachineEditor(machine)}>Chỉnh sửa</button>}{can(principal, ['MACHINE_CONTROL']) && <>{(machine.operationalStatus === 'OFFLINE' || machine.operationalStatus === 'UNSTABLE') && <button className="primary" onClick={() => void mutate(() => backend.setMachineOperationalStatus(machine.id, 'ONLINE'), 'Đã đưa kiosk về trạng thái trực tuyến.')}>Đưa về trực tuyến</button>}<button onClick={() => void mutate(() => backend.controlMachine(machine.id, machine.operationalStatus === 'DISABLED' ? 'ENABLE' : 'DISABLE'), machine.operationalStatus === 'DISABLED' ? 'Đã bật máy.' : 'Đã tắt máy.')}>{machine.operationalStatus === 'DISABLED' ? 'Bật máy' : 'Tắt máy'}</button>{machine.operationalStatus !== 'MAINTENANCE' && <button onClick={() => void mutate(() => backend.controlMachine(machine.id, 'ENTER_MAINTENANCE'), 'Đã chuyển máy sang trạng thái bảo trì.')}>Bảo trì</button>}</>}</> }
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

  return <div className="app-shell"><aside><div className="sidebar-brand"><strong>S.</strong><span>SCENTSTATION<small>TRUNG TÂM QUẢN TRỊ</small></span></div><nav>{menu.map((item) => <button key={item.id} className={page === item.id ? 'active' : ''} onClick={() => { clearApiCache(); closeMachineSlots(); setPage(item.id) }}><i>{item.icon}</i>{item.label}</button>)}</nav><div className="sidebar-user"><span>{principal.username.slice(0, 2).toUpperCase()}</span><div><strong>{principal.username}</strong><small>{principal.roles.map((role) => valueLabels[role] ?? role).join(', ')}</small></div></div></aside><main className="workspace"><header><div><span className="live-dot" /> Hệ thống đang trực tuyến</div><div className="header-actions">{!!tenants.length && <label>Phạm vi thương hiệu<select value={tenantId ?? ''} onChange={(event) => { clearApiCache(); closeMachineSlots(); setTenantId(event.target.value ? Number(event.target.value) : undefined) }}>{platform && <option value="">Toàn nền tảng</option>}{tenants.map((tenant) => <option key={tenant.id} value={tenant.id}>{tenant.name} ({tenant.code})</option>)}</select></label>}{can(principal, ['NOTIFICATION_READ']) && <NotificationCenter principal={principal} tenantId={tenantId} />}<button onClick={() => void logout()}>Đăng xuất</button></div></header><div className="content"><div className="page-heading"><div><p className="eyebrow">SCENTSTATION · QUẢN TRỊ VẬN HÀNH</p><h1>{isViewingMachineSlots ? `Đầu phun: ${selectedMachine.name}` : titles[page][0]}</h1><p>{isViewingMachineSlots ? `Máy #${selectedMachine.id} (${selectedMachine.serialNumber})` : (page === 'alerts' && isAdminRole ? 'Theo dõi sự cố thiết bị (Nhân viên vận hành và kỹ thuật viên phụ trách xử lý)' : page === 'maintenance' && isAdminRole ? 'Theo dõi tiến độ và chấp nhận phiếu bảo trì' : page === 'inventory' && platform ? 'Theo dõi lô nhập, chai nước hoa và lịch sử biến động tồn kho toàn nền tảng' : titles[page][1])}</p></div><div className="heading-actions">{isViewingMachineSlots && <button onClick={closeMachineSlots}>← Danh sách máy</button>}{page === 'tenants' && platform && <button className="primary" onClick={() => setTenantEditor('new')}>＋ Tạo thương hiệu</button>}{page === 'users' && <button className="primary" onClick={() => setUserEditor('new')}>＋ Tạo tài khoản</button>}{page === 'products' && tenantId && can(principal, ['CATALOG_MANAGE']) && <button className="primary" onClick={() => setShowProduct(true)}>＋ Thêm sản phẩm</button>}{page === 'campaigns' && tenantId && can(principal, ['CAMPAIGN_MANAGE']) && <button className="primary" onClick={() => setCampaignEditor('new')}>＋ Tạo chiến dịch</button>}{page === 'machines' && tenantId && principal.tenantId && can(principal, ['MACHINE_CONFIGURE']) && <>{!isViewingMachineSlots && <button onClick={() => setShowLocation(true)}>＋ Địa điểm</button>}<button className="primary" onClick={() => isViewingMachineSlots ? setSlotEditor('new') : setMachineEditor('new')}>＋ {isViewingMachineSlots ? 'Tạo đầu phun' : 'Đăng ký máy'}</button></>}{page === 'inventory' && !platform && tenantId && can(principal, ['INVENTORY_MANAGE', 'INVENTORY_ADJUST']) && <button className="primary" onClick={() => setShowInventoryForm(true)}>＋ Nghiệp vụ tồn kho</button>}{page === 'maintenance' && (isOpsManager || isTechnician) && tenantId && can(principal, ['MAINTENANCE_MANAGE']) && <button className="primary" onClick={() => setMaintenanceEditor('new')}>＋ Tạo phiếu</button>}<button onClick={() => { clearApiCache(); if (isViewingMachineSlots && selectedMachine) void loadMachineSlots(selectedMachine); else void load() }} disabled={activeLoading}>↻ {activeLoading ? 'Đang tải' : 'Làm mới'}</button></div></div>{error && <div className="error-box"><span>{error}</span><button onClick={() => { if (isViewingMachineSlots && selectedMachine) void loadMachineSlots(selectedMachine); else void load() }}>Thử lại</button></div>}{notice && <div className="notice">✓ {notice}</div>}{page === 'dashboard' ? <Dashboard data={dashboard} operational={operationalReport} period={dashboardPeriod} setPeriod={setDashboardPeriod} principal={principal} /> : <>{page === 'transactions' && <ReportingTools filters={transactionFilters} apply={setTransactionFilters} canExport={can(principal, ['REPORT_EXPORT'])} tenantId={tenantId} />}{page === 'orders' && <OrderOverview counts={orderCounts} selected={orderKind} select={(kind) => { setOrderKind(kind); setOrderStatusFilter('') }} />}{page === 'inventory' && <section className="panel toolbar"><nav className="order-tabs" aria-label="Phân loại tồn kho">{([['ALL', 'Tất cả', inventoryCounts.batches + inventoryCounts.bottles + inventoryCounts.movements], ['BATCH', 'Lô hàng', inventoryCounts.batches], ['BOTTLE', 'Chai nước hoa', inventoryCounts.bottles], ['MOVEMENT', 'Biến động kho', inventoryCounts.movements]] as const).map(([key, label, count]) => <button type="button" key={key} className={inventoryKind === key ? 'active' : ''} onClick={() => setInventoryKind(key)}>{label}<small>{count}</small></button>)}</nav>{lowStockBottleCount > 0 && <span className="badge error">⚠ {lowStockBottleCount} chai gần hết / hết / hết hạn</span>}</section>}<section className="panel"><div className="toolbar"><label className="search">⌕<input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm trong dữ liệu đã tải…" /></label><div className="toolbar-filters">{page === 'orders' && <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}><span>Trạng thái:</span><select aria-label="Lọc trạng thái" value={orderStatusFilter} onChange={(event) => setOrderStatusFilter(event.target.value)}>{orderKind === 'REVIEW' ? <><option value="">Mọi trạng thái đối soát</option><option value="OPEN">Đang mở</option><option value="RESOLVED">Đã xử lý</option></> : orderKind === 'PAYMENT' ? <><option value="">Mọi trạng thái thanh toán</option><option value="PENDING">Đang chờ</option><option value="PAID">Đã thanh toán</option><option value="FAILED">Thất bại</option><option value="EXPIRED">Hết hạn</option><option value="REFUNDED">Đã hoàn tiền</option></> : <><option value="">{orderKind === 'ORDER' ? 'Mọi trạng thái đơn' : 'Mọi trạng thái'}</option><option value="PENDING">Đang chờ</option><option value="PAID">Đã thanh toán</option><option value="FAILED">Thất bại</option><option value="EXPIRED">Hết hạn</option><option value="CANCELLED">Đã hủy</option><option value="REFUNDED">Đã hoàn tiền</option></>}</select></label>}{page === 'alerts' && <><select aria-label="Lọc mức độ cảnh báo" value={alertSeverityFilter} onChange={(event) => setAlertSeverityFilter(event.target.value)}><option value="">Mọi mức độ</option><option value="LOW">Thấp</option><option value="MEDIUM">Trung bình</option><option value="HIGH">Cao</option><option value="CRITICAL">Nghiêm trọng</option></select><select aria-label="Lọc trạng thái cảnh báo" value={alertStatusFilter} onChange={(event) => setAlertStatusFilter(event.target.value)}><option value="">Mọi trạng thái</option><option value="OPEN">Đang mở</option><option value="ACKNOWLEDGED">Đã xác nhận</option><option value="ASSIGNED">Đã phân công</option><option value="ESCALATED">Đã chuyển cấp</option><option value="RESOLVED">Đã xử lý</option></select></>}{page === 'maintenance' && <label className="switch"><input type="checkbox" checked={onlyMyTickets} onChange={(event) => setOnlyMyTickets(event.target.checked)} />Chỉ phiếu được giao cho tôi</label>}<span>{filtered.length} bản ghi</span></div></div>{activeLoading ? <div className="empty"><span className="spinner" /> Đang tải dữ liệu thực…</div> : <Table rows={filtered} columns={visibleColumns} action={action} />}</section></>}</div></main>
    {showProduct && tenantId && <ProductForm tenantId={tenantId} close={() => setShowProduct(false)} saved={() => void load()} />}
    {productEditor && productEditor !== 'new' && tenantId && <ProductForm tenantId={tenantId} product={productEditor} close={() => setProductEditor(null)} saved={() => { setNotice('Đã cập nhật đầy đủ thông tin sản phẩm.'); void load() }} />}
    {tenantEditor && <TenantForm tenant={tenantEditor === 'new' ? undefined : tenantEditor} platform={platform} close={() => setTenantEditor(null)} saved={() => { setNotice('Đã lưu thông tin thương hiệu.'); void refreshTenants(); void load() }} />}
    {userEditor && <UserForm account={userEditor === 'new' ? undefined : userEditor} principal={principal} platform={platform} tenants={tenants} selectedTenantId={tenantId} close={() => setUserEditor(null)} saved={() => { setNotice('Đã lưu tài khoản và phân quyền.'); void load() }} />}
    {productPriceEditor && <ProductPriceForm product={productPriceEditor} close={() => setProductPriceEditor(null)} saved={() => { setNotice('Đã cập nhật giá sản phẩm.'); void load() }} />}
    {campaignEditor && tenantId && <CampaignForm campaign={campaignEditor === 'new' ? undefined : campaignEditor} tenantId={tenantId} products={catalogProducts} close={() => setCampaignEditor(null)} saved={() => { setNotice('Đã lưu chiến dịch.'); void load() }} />}
    {showLocation && tenantId && <LocationForm tenantId={tenantId} close={() => setShowLocation(false)} saved={(location) => { setLocations((current) => [...current, location]); setNotice('Đã tạo địa điểm.') }} />}
    {machineEditor && tenantId && <MachineForm machine={machineEditor === 'new' ? undefined : machineEditor} tenantId={tenantId} locations={locations} close={() => setMachineEditor(null)} saved={() => { setNotice('Đã lưu máy.'); void load() }} />}
    {slotEditor && selectedMachine && <SlotForm slot={slotEditor === 'new' ? undefined : slotEditor} machineId={selectedMachine.id} close={() => setSlotEditor(null)} saved={() => { setNotice('Đã lưu đầu phun.'); void loadMachineSlots(selectedMachine) }} />}
    {showInventoryForm && tenantId && <InventoryForm tenantId={tenantId} principal={principal} batches={inventoryBatches} bottles={inventoryBottles} close={() => setShowInventoryForm(false)} saved={() => { setNotice('Đã hoàn thành nghiệp vụ tồn kho.'); void load() }} />}
    {showInventoryWorkflow && tenantId && <InventoryWorkflowForm tenantId={tenantId} principal={principal} bottles={inventoryBottles} close={() => setShowInventoryWorkflow(false)} saved={() => { setNotice('Đã ghi nhận refill/lắp/thay chai.'); void load() }} />}
    {inventoryDisposeTarget && <InventoryDisposeForm target={inventoryDisposeTarget} principal={principal} close={() => setInventoryDisposeTarget(null)} saved={(message) => { setNotice(message); void load() }} />}
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
  return principal
    ? <Admin principal={principal} exit={() => { setSessionNotice(''); setPrincipal(null) }} />
    : <Login done={(next) => { setSessionNotice(''); setPrincipal(next) }} sessionNotice={sessionNotice} />
}

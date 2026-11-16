import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import './Admin.css'
import { ApiError, authToken } from './api/client'
import { backend } from './api/scentStation'
import type { Account, Alert, Bottle, Campaign, InventoryBatch, Location, Machine, MaintenanceTicket, PaymentReview, Permission, Principal, Product, Slot, Tenant } from './api/types'
import { CampaignForm, ProductPriceForm } from './components/CatalogForms'
import { TenantForm, UserForm } from './components/IdentityForms'
import { LocationForm, MachineForm, SlotForm } from './components/MachineForms'
import { InventoryForm, MaintenanceForm } from './components/OperationForms'
import { NotificationCenter } from './components/NotificationCenter'
import { ReportingTools } from './components/ReportingTools'
import type { TransactionFilters } from './components/ReportingTools'

type Page = 'dashboard' | 'tenants' | 'users' | 'products' | 'campaigns' | 'machines' | 'slots' | 'inventory' | 'alerts' | 'maintenance' | 'orders' | 'transactions' | 'audit'
type Row = Record<string, unknown>

const pages: Array<{ id: Page; label: string; icon: string; permissions?: Permission[] }> = [
  { id: 'dashboard', label: 'Tổng quan', icon: '▦', permissions: ['REPORT_READ', 'PLATFORM_REPORT_READ'] },
  { id: 'tenants', label: 'Thương hiệu', icon: '◇', permissions: ['TENANT_MANAGE', 'BRAND_PROFILE_READ'] },
  { id: 'users', label: 'Tài khoản & phân quyền', icon: '♙', permissions: ['BRAND_USER_MANAGE'] },
  { id: 'products', label: 'Sản phẩm', icon: '◈', permissions: ['CATALOG_READ'] },
  { id: 'campaigns', label: 'Chiến dịch', icon: '◷', permissions: ['CATALOG_READ', 'CAMPAIGN_MANAGE'] },
  { id: 'machines', label: 'Kiosk', icon: '▣', permissions: ['MACHINE_READ'] },
  { id: 'slots', label: 'Đầu phun', icon: '◉', permissions: ['MACHINE_READ'] },
  { id: 'inventory', label: 'Tồn kho', icon: '▤', permissions: ['INVENTORY_READ'] },
  { id: 'alerts', label: 'Cảnh báo', icon: '⚑', permissions: ['ALERT_READ'] },
  { id: 'maintenance', label: 'Bảo trì', icon: '⚙', permissions: ['MAINTENANCE_READ'] },
  { id: 'orders', label: 'Đơn hàng & thanh toán', icon: '▧', permissions: ['ORDER_READ'] },
  { id: 'transactions', label: 'Giao dịch & báo cáo', icon: '▥', permissions: ['PLATFORM_REPORT_READ'] },
  { id: 'audit', label: 'Nhật ký hệ thống', icon: '≡', permissions: ['AUDIT_READ', 'PLATFORM_AUDIT_READ'] },
]

const labels: Record<string, string> = {
  id: 'ID', kind: 'Loại dữ liệu', name: 'Tên', code: 'Mã', status: 'Trạng thái', tenantId: 'Mã thương hiệu', tenantName: 'Thương hiệu', productId: 'Mã sản phẩm', productName: 'Sản phẩm', machineId: 'Máy',
  username: 'Tài khoản', fullName: 'Họ tên', email: 'Email', roles: 'Vai trò', active: 'Hoạt động', locked: 'Đã khóa',
  defaultPrice: 'Giá mặc định', available: 'Khả dụng', fragranceNotes: 'Nhóm hương', serialNumber: 'Serial', locationId: 'Địa điểm', machineName: 'Kiosk',
  operationalStatus: 'Vận hành', deviceRegistrationStatus: 'Đăng ký thiết bị', slotNumber: 'Đầu phun', slotCode: 'Mã đầu phun', stockThreshold: 'Ngưỡng tồn', locationName: 'Địa điểm', imageUrl: 'Hình ảnh',
  summary: 'Nội dung', severity: 'Mức độ', type: 'Loại', title: 'Tiêu đề', priority: 'Ưu tiên', critical: 'Khẩn cấp',
  totalAmount: 'Tổng tiền', currency: 'Tiền tệ', reference: 'Tham chiếu', orderId: 'Đơn hàng', amount: 'Số tiền', items: 'Sản phẩm',
  createdAt: 'Tạo lúc', updatedAt: 'Cập nhật', lastSeenAt: 'Gần nhất', occurredAt: 'Thời gian', action: 'Hành động', outcome: 'Kết quả',
  totalTransactions: 'Tổng giao dịch', successfulTransactions: 'Thành công', pendingTransactions: 'Đang chờ', failedTransactions: 'Thất bại', expiredTransactions: 'Hết hạn',
  total: 'Tổng số', online: 'Trực tuyến', offline: 'Ngoại tuyến', unstable: 'Không ổn định', disabled: 'Đã tắt', maintenance: 'Đang bảo trì', error: 'Lỗi', unknown: 'Không xác định',
  open: 'Đang mở', acknowledged: 'Đã xác nhận', assigned: 'Đã phân công', escalated: 'Đã chuyển cấp', resolved: 'Đã xử lý',
  bottles: 'Tổng số chai', installed: 'Đã lắp', nearlyEmpty: 'Sắp hết', empty: 'Đã hết',
}

const titles: Record<Page, [string, string]> = {
  dashboard: ['Tổng quan vận hành', 'Số liệu tổng hợp theo thời gian thực từ các dịch vụ'], tenants: ['Thương hiệu', 'Hồ sơ và trạng thái hoạt động của từng thương hiệu'],
  users: ['Tài khoản & phân quyền', 'Quản lý vai trò, quyền bổ sung và phạm vi vận hành'], products: ['Danh mục sản phẩm', 'Sản phẩm nước hoa theo từng thương hiệu'],
  campaigns: ['Chiến dịch khuyến mãi', 'Mức giảm giá và thời gian áp dụng theo sản phẩm'], machines: ['Kiosk', 'Trạng thái kết nối và địa điểm lắp đặt'], slots: ['Đầu phun', 'Tổng thể đầu phun và sản phẩm đang được lắp trên các kiosk'],
  inventory: ['Tồn kho', 'Lô nhập, chai nước hoa và lịch sử thay đổi tồn kho'], alerts: ['Cảnh báo', 'Theo dõi và xử lý sự cố thiết bị'],
  maintenance: ['Bảo trì', 'Phiếu bảo trì theo phạm vi vận hành'], orders: ['Đơn hàng & thanh toán', 'Trạng thái đơn hàng, phun hương và đối soát'],
  transactions: ['Giao dịch & báo cáo', 'Dữ liệu giao dịch được đồng bộ từ dịch vụ đơn hàng'], audit: ['Nhật ký hệ thống', 'Lịch sử thao tác được backend ghi nhận'],
}

const valueLabels: Record<string, string> = {
  ACTIVE: 'Đang hoạt động', SUSPENDED: 'Tạm ngưng', ONLINE: 'Trực tuyến', OFFLINE: 'Ngoại tuyến', UNSTABLE: 'Không ổn định', DISABLED: 'Đã tắt', ERROR: 'Lỗi', MAINTENANCE: 'Đang bảo trì',
  AVAILABLE: 'Sẵn sàng', INSTALLED: 'Đã lắp', NEARLY_EMPTY: 'Sắp hết', EMPTY: 'Đã hết', DAMAGED: 'Hư hỏng', EXPIRED: 'Hết hạn',
  OPEN: 'Đang mở', ACKNOWLEDGED: 'Đã xác nhận', ASSIGNED: 'Đã phân công', ESCALATED: 'Đã chuyển cấp', RESOLVED: 'Đã xử lý',
  PENDING: 'Đang chờ', PAID: 'Đã thanh toán', FAILED: 'Thất bại', CONFIRMED: 'Đã xác nhận', DISPENSED: 'Đã phun', PROCESSING: 'Đang xử lý', SHIPPED: 'Đang giao', DELIVERED: 'Hoàn tất', CANCELLED: 'Đã hủy',
  LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao', CRITICAL: 'Nghiêm trọng', URGENT: 'Khẩn cấp',
  PLATFORM_SUPER_ADMIN: 'Quản trị nền tảng', BRAND_ADMIN: 'Quản trị thương hiệu', OPERATIONS_MANAGER: 'Quản lý vận hành', TECHNICIAN: 'Kỹ thuật viên', INVENTORY_STAFF: 'Nhân viên kho', CUSTOMER: 'Khách hàng',
  BATCH: 'Lô nhập', BOTTLE: 'Chai nước hoa', MOVEMENT: 'Biến động kho', ORDER: 'Đơn hàng', REVIEW: 'Đối soát', MACHINE: 'Kiosk', SLOT: 'Đầu phun',
  PERCENTAGE: 'Theo phần trăm', FIXED_AMOUNT: 'Số tiền cố định', SUCCESS: 'Thành công',
  'local-copy': 'Dữ liệu đồng bộ nội bộ', ok: 'Hoạt động', unavailable: 'Không khả dụng',
  'skipped-missing-tenant': 'Bỏ qua: chưa chọn thương hiệu', 'skipped-invalid-tenant': 'Bỏ qua: mã thương hiệu không hợp lệ',
  'skipped-non-numeric-tenant': 'Bỏ qua: mã thương hiệu không phải số',
  'unsupported-missing-list-api': 'Chưa hỗ trợ: thiếu API danh sách',
  'unsupported-tenant-transaction-schema': 'Chưa hỗ trợ giao dịch theo thương hiệu',
}

const serviceLabels: Record<string, string> = {
  'order-service': 'Dịch vụ đơn hàng', 'machine-service': 'Dịch vụ kiosk',
  'machine-locations': 'Địa điểm kiosk', 'machine-alerts': 'Cảnh báo kiosk',
  'operation-service': 'Dịch vụ vận hành', 'operation-batches': 'Lô hàng',
  'operation-consumption': 'Tiêu thụ tồn kho', 'operation-maintenance': 'Bảo trì',
  'operation-failures': 'Sự cố vận hành',
}

const can = (principal: Principal, required?: Permission[]) => !required || principal.roles.includes('PLATFORM_SUPER_ADMIN') || required.some((permission) => principal.grants.includes(permission))
const errorText = (error: unknown) => error instanceof ApiError || error instanceof Error ? error.message : 'Không thể hoàn thành yêu cầu.'
function display(value: unknown, key = ''): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? 'Có' : 'Không'
  if (Array.isArray(value)) return value.map((item) => typeof item === 'object' ? JSON.stringify(item) : (valueLabels[String(item)] ?? String(item))).join(', ') || '—'
  if (typeof value === 'object') return JSON.stringify(value)
  if (/price|amount|revenue|quantity/i.test(key) && !Number.isNaN(Number(value))) return new Intl.NumberFormat('vi-VN').format(Number(value))
  if (/At$|time|date/i.test(key) && typeof value === 'string') { const date = new Date(value); if (!Number.isNaN(date.getTime())) return date.toLocaleString('vi-VN') }
  return valueLabels[String(value)] ?? String(value)
}

function Login({ done }: { done: (principal: Principal) => void }) {
  const [username, setUsername] = useState(''); const [password, setPassword] = useState('')
  const [error, setError] = useState(''); const [loading, setLoading] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setLoading(true)
    try { await backend.login(username.trim(), password); done(await backend.me()) }
    catch (reason) { authToken.clear(); setError(errorText(reason)) } finally { setLoading(false) }
  }
  return <main className="login-shell"><section className="login-art"><div className="brand-mark">S.</div><p>SCENTSTATION</p><h1>Vận hành mọi điểm chạm hương thơm.</h1><span>Trung tâm quản trị vận hành</span></section><section className="login-panel"><form onSubmit={submit} className="login-card"><p className="eyebrow">TRUY CẬP QUẢN TRỊ AN TOÀN</p><h2>Đăng nhập</h2><p>Sử dụng tài khoản quản trị được cấp. Phiên đăng nhập được bảo vệ bằng JWT.</p><label>Tên đăng nhập<input autoFocus required value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" /></label><label>Mật khẩu<input required type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" /></label>{error && <div className="error-box">{error}</div>}<button className="primary" disabled={loading}>{loading ? 'Đang xác thực…' : 'Đăng nhập'}</button></form></section></main>
}

function Table({ rows, columns, action }: { rows: Row[]; columns: string[]; action: (row: Row) => React.ReactNode }) {
  if (!rows.length) return <div className="empty">Không có dữ liệu phù hợp từ backend.</div>
  const renderedActions = rows.map(action); const hasActions = renderedActions.some(Boolean)
  return <div className="table-wrap"><table><thead><tr>{columns.map((column) => <th key={column}>{labels[column] ?? column}</th>)}{hasActions && <th>Thao tác</th>}</tr></thead><tbody>{rows.map((row, index) => <tr key={String(row.id ?? row.reference ?? row.code ?? index)}>{columns.map((column) => <td key={column} title={column === 'imageUrl' ? undefined : display(row[column], column)}>{column === 'imageUrl' && typeof row[column] === 'string' ? <img className="product-thumb" src={String(row[column])} alt="Ảnh sản phẩm" /> : column === 'status' || column.endsWith('Status') ? <span className={`badge ${String(row[column]).toLowerCase()}`}>{display(row[column], column)}</span> : display(row[column], column)}</td>)}{hasActions && <td className="actions">{renderedActions[index]}</td>}</tr>)}</tbody></table></div>
}

function Dashboard({ data }: { data: Row | null }) {
  if (!data) return <div className="empty">Không có dữ liệu tổng quan.</div>
  const groupNames: Record<string, string> = { transactions: 'Giao dịch', machines: 'Kiosk', alerts: 'Cảnh báo', inventory: 'Tồn kho' }
  const primaryFields: Record<string, string> = { transactions: 'totalTransactions', machines: 'total', alerts: 'total', inventory: 'bottles' }
  const groups = ['transactions', 'machines', 'alerts', 'inventory'].map((key) => [key, (data[key] ?? {}) as Row] as const)
  return <><div className="metric-grid">{groups.map(([key, group]) => <article className="metric-card" key={key}><span>{groupNames[key]}</span><strong>{display(group[primaryFields[key]] ?? 0)}</strong>{Object.entries(group).filter(([name]) => name !== primaryFields[key]).slice(0, 5).map(([name, value]) => <small key={name}>{labels[name] ?? name}: {display(value, name)}</small>)}</article>)}</div><section className="panel"><h3>Trạng thái kết nối dịch vụ</h3><div className="upstream">{Object.entries((data.upstream ?? {}) as Row).map(([service, status]) => <span key={service} className={`badge ${String(status).toLowerCase()}`}>{serviceLabels[service] ?? service}: {display(status)}</span>)}</div></section></>
}

function ProductForm({ tenantId, close, saved }: { tenantId: number; close: () => void; saved: () => void }) {
  const [form, setForm] = useState({ name: '', description: '', fragranceNotes: '', imageUrl: '', defaultPrice: '', available: true })
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('')
    if (Number(form.defaultPrice) < 0) { setError('Giá không được nhỏ hơn 0.'); return }
    setSaving(true)
    try { await backend.createProduct({ ...form, tenantId, defaultPrice: Number(form.defaultPrice), imageUrl: form.imageUrl || null }); saved(); close() }
    catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  return <div className="modal-backdrop"><form className="modal" onSubmit={submit}><h3>Thêm sản phẩm</h3><label>Tên<input required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label><label>Nhóm hương<input value={form.fragranceNotes} onChange={(event) => setForm({ ...form, fragranceNotes: event.target.value })} /></label><label>Mô tả<textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label><label>Đường dẫn hình ảnh<input type="url" value={form.imageUrl} onChange={(event) => setForm({ ...form, imageUrl: event.target.value })} /></label><label>Giá mặc định<input required min="0" type="number" value={form.defaultPrice} onChange={(event) => setForm({ ...form, defaultPrice: event.target.value })} /></label>{error && <div className="error-box">{error}</div>}<div className="modal-actions"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang tạo…' : 'Tạo sản phẩm'}</button></div></form></div>
}

function Admin({ principal, exit }: { principal: Principal; exit: () => void }) {
  const platform = principal.roles.includes('PLATFORM_SUPER_ADMIN')
  const menu = useMemo(() => pages.filter((item) => can(principal, item.permissions)), [principal])
  const hashPage = window.location.hash.slice(1) as Page
  const [page, setPage] = useState<Page>(menu.some((item) => item.id === hashPage) ? hashPage : (menu[0]?.id ?? 'products'))
  const [tenants, setTenants] = useState<Tenant[]>([]); const [tenantId, setTenantId] = useState<number | undefined>(principal.tenantId ?? undefined)
  const [rows, setRows] = useState<Row[]>([]); const [dashboard, setDashboard] = useState<Row | null>(null); const [columns, setColumns] = useState<string[]>([])
  const [loading, setLoading] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [search, setSearch] = useState('')
  const [showProduct, setShowProduct] = useState(false); const [selectedMachine, setSelectedMachine] = useState<Machine | null>(null)
  const [tenantEditor, setTenantEditor] = useState<Tenant | 'new' | null>(null); const [userEditor, setUserEditor] = useState<Account | 'new' | null>(null)
  const [productPriceEditor, setProductPriceEditor] = useState<Product | null>(null); const [campaignEditor, setCampaignEditor] = useState<Campaign | 'new' | null>(null)
  const [catalogProducts, setCatalogProducts] = useState<Product[]>([])
  const [locations, setLocations] = useState<Location[]>([]); const [machineEditor, setMachineEditor] = useState<Machine | 'new' | null>(null); const [slotEditor, setSlotEditor] = useState<Slot | 'new' | null>(null); const [showLocation, setShowLocation] = useState(false)
  const [inventoryBatches, setInventoryBatches] = useState<InventoryBatch[]>([]); const [inventoryBottles, setInventoryBottles] = useState<Bottle[]>([]); const [showInventoryForm, setShowInventoryForm] = useState(false); const [maintenanceEditor, setMaintenanceEditor] = useState<MaintenanceTicket | 'new' | null>(null)
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

  const load = useCallback(async () => {
    setLoading(true); setError(''); setDashboard(null)
    try {
      let data: Row[] = []
      const targetTenantIds = tenantId ? [tenantId] : platform ? tenants.map((tenant) => tenant.id) : principal.tenantId ? [principal.tenantId] : []
      switch (page) {
        case 'dashboard': {
          const summary = await backend.dashboard(tenantId) as unknown as Row
          if (platform && !tenantId) {
            const machines = (await Promise.all(tenants.map((tenant) => backend.machines(tenant.id)))).flat()
            const counts: Row = { total: machines.length, online: 0, offline: 0, unstable: 0, disabled: 0, maintenance: 0, error: 0, unknown: 0 }
            for (const machine of machines) {
              const status = machine.operationalStatus.toLowerCase()
              counts[status in counts ? status : 'unknown'] = Number(counts[status in counts ? status : 'unknown']) + 1
            }
            summary.machines = counts
            summary.upstream = { ...((summary.upstream ?? {}) as Row), 'machine-service': 'live' }
          }
          setDashboard(summary)
          break
        }
        case 'tenants': data = (platform ? await backend.tenants() : [await backend.brandTenant()]) as unknown as Row[]; break
        case 'users': data = await backend.users(platform, tenantId) as unknown as Row[]; break
        case 'products': { const products = (await Promise.all(targetTenantIds.map((id) => backend.products(id)))).flat(); setCatalogProducts(products); data = products.map((product) => ({ ...product, tenantName: tenants.find((tenant) => tenant.id === product.tenantId)?.name ?? `#${product.tenantId}` })) as unknown as Row[]; break }
        case 'campaigns': { const groups = await Promise.all(targetTenantIds.map(async (id) => { const [campaigns, products] = await Promise.all([backend.campaigns(id), backend.products(id)]); return { campaigns, products } })); const products = groups.flatMap((group) => group.products); setCatalogProducts(products); data = groups.flatMap((group) => group.campaigns).map((campaign) => ({ ...campaign, tenantName: tenants.find((tenant) => tenant.id === campaign.tenantId)?.name ?? `#${campaign.tenantId}`, productName: products.find((product) => product.id === campaign.productId)?.name ?? `#${campaign.productId}` })) as unknown as Row[]; break }
        case 'machines': { const groups = await Promise.all(targetTenantIds.map(async (id) => { const [machines, machineLocations] = await Promise.all([backend.machines(id), backend.locations(id)]); return { machines, machineLocations } })); const machineLocations = groups.flatMap((group) => group.machineLocations); setLocations(machineLocations); data = groups.flatMap((group) => group.machines).map((machine) => ({ ...machine, kind: 'MACHINE', tenantName: tenants.find((tenant) => String(tenant.id) === String(machine.tenantId))?.name ?? `#${machine.tenantId}`, locationName: machineLocations.find((location) => location.locationId === machine.locationId)?.name ?? machine.locationId })) as unknown as Row[]; break }
        case 'slots': { const machineGroups = await Promise.all(targetTenantIds.map((id) => backend.machines(id))); const machines = machineGroups.flat(); const productGroups = can(principal, ['CATALOG_READ']) ? await Promise.all(targetTenantIds.map((id) => backend.products(id))) : []; const products = productGroups.flat(); const slotGroups = await Promise.all(machines.map(async (machine) => ({ machine, slots: await backend.slots(machine.id) }))); data = slotGroups.flatMap(({ machine, slots }) => slots.map((slot) => ({ ...slot, kind: 'SLOT', tenantName: tenants.find((tenant) => String(tenant.id) === String(machine.tenantId))?.name ?? `#${machine.tenantId}`, machineName: machine.name, productName: products.find((product) => String(product.id) === String(slot.productId))?.name ?? (slot.productId ? `Sản phẩm #${slot.productId}` : '—') }))) as unknown as Row[]; break }
        case 'inventory': if (tenantId) { const [batches, bottles, movements, products] = await Promise.all([backend.batches(tenantId), backend.bottles(tenantId), backend.movements(tenantId), can(principal, ['CATALOG_READ']) ? backend.products(tenantId) : Promise.resolve([])]); setInventoryBatches(batches); setInventoryBottles(bottles); const productName = (productId?: number) => products.find((product) => product.id === productId)?.name ?? (productId ? `Sản phẩm #${productId}` : '—'); data = [...batches.map((item) => ({ kind: 'BATCH', ...item, productName: productName(item.productId) })), ...bottles.map((item) => ({ kind: 'BOTTLE', ...item, productName: productName(item.productId) })), ...movements.map((item) => { const bottle = bottles.find((candidate) => candidate.id === Number(item.bottleId)); return { kind: 'MOVEMENT', ...item, productName: productName(bottle?.productId) } })] as unknown as Row[] } break
        case 'alerts': { const alertGroups = await Promise.all(targetTenantIds.map((id) => backend.alerts(id))); data = alertGroups.flat().map((alert) => ({ ...alert, tenantName: tenants.find((tenant) => String(tenant.id) === String(alert.tenantId))?.name ?? `#${alert.tenantId}` })) as unknown as Row[]; break }
        case 'maintenance': if (tenantId) data = await backend.maintenance(tenantId) as unknown as Row[]; break
        case 'orders': { const reviews = can(principal, ['ORDER_REVIEW']) ? backend.reviews() : Promise.resolve([]); const [orders, paymentReviews] = await Promise.all([backend.orders(), reviews]); data = [...orders.map((item) => ({ kind: 'ORDER', ...item, items: item.items.map((orderItem) => `${orderItem.productName} ×${orderItem.quantity}`).join(', ') })), ...paymentReviews.map((item) => ({ kind: 'REVIEW', ...item }))] as unknown as Row[]; break }
        case 'transactions': data = (await backend.transactions(transactionFilters)).content as unknown as Row[]; break
        case 'audit': data = await backend.audit(tenantId); break
      }
      setRows(data)
      const map: Record<Page, string[]> = { dashboard: [], tenants: ['id', 'code', 'name', 'status', 'contactEmail'], users: ['id', 'username', 'fullName', 'email', 'roles', 'tenantId', 'active', 'locked'], products: ['imageUrl', 'tenantName', 'name', 'fragranceNotes', 'defaultPrice', 'available', 'updatedAt'], campaigns: ['tenantName', 'id', 'name', 'productName', 'discountType', 'discountValue', 'active', 'startAt', 'endAt'], machines: ['tenantName', 'id', 'name', 'serialNumber', 'locationName', 'deviceRegistrationStatus', 'operationalStatus', 'lastSeenAt'], slots: ['tenantName', 'machineName', 'slotNumber', 'slotCode', 'productName', 'status', 'available', 'stockThreshold', 'price'], inventory: ['kind', 'id', 'productName', 'batchCode', 'bottleCode', 'currentQuantity', 'status', 'updatedAt'], alerts: ['tenantName', 'id', 'severity', 'status', 'type', 'summary', 'machineId', 'lastSeenAt'], maintenance: ['id', 'severity', 'status', 'title', 'machineId', 'priority', 'assignedToActorId', 'updatedAt'], orders: ['kind', 'id', 'reference', 'items', 'totalAmount', 'currency', 'status', 'manualReviewRequired', 'createdAt'], transactions: ['id', 'reference', 'orderId', 'amount', 'currency', 'status', 'createdAt', 'paidAt'], audit: ['id', 'occurredAt', 'actorId', 'tenantId', 'action', 'targetType', 'targetId', 'outcome'] }
      setColumns(map[page].filter((key) => data.some((item) => key in item)))
    } catch (reason) { setRows([]); setError(errorText(reason)) } finally { setLoading(false) }
  }, [page, tenantId, platform, principal, transactionFilters, tenants])
  useEffect(() => { void load() }, [load])

  const filtered = rows.filter((item) => JSON.stringify(item).toLowerCase().includes(search.trim().toLowerCase()))
  async function mutate(job: () => Promise<unknown>, message: string) { try { setError(''); await job(); setNotice(message); await load() } catch (reason) { setError(errorText(reason)) } }
  function action(row: Row) {
    if (page === 'tenants') { const tenant = row as unknown as Tenant; const editable = platform || can(principal, ['BRAND_PROFILE_EDIT']); if (!editable) return null; return <><button onClick={() => setTenantEditor(tenant)}>Chỉnh sửa</button>{platform && <button className={tenant.status === 'ACTIVE' ? 'danger' : ''} onClick={() => void mutate(() => backend.updateTenantStatus(tenant.id, tenant.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'), tenant.status === 'ACTIVE' ? 'Đã tạm ngưng thương hiệu.' : 'Đã kích hoạt thương hiệu.')}>{tenant.status === 'ACTIVE' ? 'Tạm ngưng' : 'Kích hoạt'}</button>}</> }
    if (page === 'users') { const account = row as unknown as Account; return <><button onClick={() => setUserEditor(account)}>Quản lý</button><button onClick={() => void mutate(() => backend.updateUserStatus(platform, account.id, account.active, !account.locked), account.locked ? 'Đã mở khóa tài khoản.' : 'Đã khóa tài khoản.')}>{account.locked ? 'Mở khóa' : 'Khóa'}</button></> }
    if (page === 'products' && tenantId && !platform && can(principal, ['CATALOG_MANAGE'])) { const product = row as unknown as Product; return <><button onClick={() => setProductPriceEditor(product)}>Cập nhật giá</button><button onClick={() => void mutate(() => backend.setProductAvailability(product.id, !product.available), 'Đã cập nhật sản phẩm.')}>{product.available ? 'Tạm ngưng' : 'Mở bán'}</button></> }
    if (page === 'campaigns' && tenantId && can(principal, ['CAMPAIGN_MANAGE'])) return <button onClick={() => setCampaignEditor(row as unknown as Campaign)}>Chỉnh sửa</button>
    if (page === 'machines' && selectedMachine) { if (!can(principal, ['MACHINE_CONFIGURE'])) return null; const slot = row as unknown as Slot; return <><button onClick={() => setSlotEditor(slot)}>Chỉnh sửa</button><button onClick={() => void (async () => { try { await backend.setSlotEnabled(selectedMachine.id, slot.id, !slot.enabled); setRows(await backend.slots(selectedMachine.id) as unknown as Row[]); setNotice(slot.enabled ? 'Đã tắt đầu phun.' : 'Đã bật đầu phun.') } catch (reason) { setError(errorText(reason)) } })()}>{slot.enabled ? 'Tắt' : 'Bật'}</button></> }
    if (page === 'machines') { const machine = row as unknown as Machine; return <><button onClick={() => void (async () => { setSelectedMachine(machine); try { const slots = await backend.slots(machine.id); setRows(slots as unknown as Row[]); setColumns(['id', 'slotNumber', 'slotCode', 'productId', 'status', 'available', 'stockThreshold', 'price']) } catch (reason) { setError(errorText(reason)) } })()}>Xem đầu phun</button>{tenantId && can(principal, ['MACHINE_CONFIGURE']) && <button onClick={() => setMachineEditor(machine)}>Chỉnh sửa</button>}{tenantId && can(principal, ['MACHINE_CONTROL']) && <button onClick={() => void mutate(() => backend.controlMachine(machine.id, machine.operationalStatus === 'DISABLED' ? 'ENABLE' : 'DISABLE'), machine.operationalStatus === 'DISABLED' ? 'Đã bật máy.' : 'Đã tắt máy.')}>{machine.operationalStatus === 'DISABLED' ? 'Bật máy' : 'Tắt máy'}</button>}</> }
    if (page === 'alerts' && tenantId && can(principal, ['ALERT_MANAGE'])) { const alert = row as unknown as Alert; return <>{alert.status === 'OPEN' && <button onClick={() => void mutate(() => backend.alertAction(tenantId, alert.id, 'acknowledge'), 'Đã xác nhận cảnh báo.')}>Xác nhận</button>}{alert.status !== 'RESOLVED' && <button onClick={() => void mutate(() => backend.alertAction(tenantId, alert.id, 'resolve', 'Đã xử lý từ trang quản trị'), 'Đã xử lý cảnh báo.')}>Xử lý</button>}{alert.status === 'RESOLVED' && <button onClick={() => void mutate(() => backend.alertAction(tenantId, alert.id, 'reopen', 'Mở lại từ trang quản trị'), 'Đã mở lại cảnh báo.')}>Mở lại</button>}</> }
    if (page === 'maintenance' && tenantId && can(principal, ['MAINTENANCE_MANAGE'])) { const ticket = row as unknown as MaintenanceTicket; return <><button onClick={() => setMaintenanceEditor(ticket)}>Chỉnh sửa</button>{ticket.status === 'COMPLETED' ? <button onClick={() => void mutate(() => backend.reopenMaintenance(ticket.id, tenantId, principal.id, 'Mở lại từ trang quản trị'), 'Đã mở lại phiếu.')}>Mở lại</button> : <button onClick={() => void mutate(() => backend.closeMaintenance(ticket.id, tenantId, principal.id, 'Đóng từ trang quản trị'), 'Đã đóng phiếu.')}>Đóng phiếu</button>}</> }
    if (page === 'orders' && row.kind === 'REVIEW' && row.status !== 'RESOLVED' && can(principal, ['ORDER_REVIEW'])) { const review = row as unknown as PaymentReview; return <button onClick={() => void mutate(() => backend.resolveReview(review.reference), 'Đã hoàn tất đối soát.')}>Hoàn tất đối soát</button> }
    return null
  }
  async function logout() { try { await backend.logout() } catch { /* kết thúc phiên cục bộ */ } authToken.clear(); exit() }

  return <div className="app-shell"><aside><div className="sidebar-brand"><strong>S.</strong><span>SCENTSTATION<small>TRUNG TÂM QUẢN TRỊ</small></span></div><nav>{menu.map((item) => <button key={item.id} className={page === item.id ? 'active' : ''} onClick={() => { setSelectedMachine(null); setPage(item.id) }}><i>{item.icon}</i>{item.label}</button>)}</nav><div className="sidebar-user"><span>{principal.username.slice(0, 2).toUpperCase()}</span><div><strong>{principal.username}</strong><small>{principal.roles.map((role) => valueLabels[role] ?? role).join(', ')}</small></div></div></aside><main className="workspace"><header><div><span className="live-dot" /> Hệ thống đang trực tuyến</div><div className="header-actions">{!!tenants.length && <label>Phạm vi thương hiệu<select value={tenantId ?? ''} onChange={(event) => setTenantId(event.target.value ? Number(event.target.value) : undefined)}>{platform && <option value="">Toàn nền tảng</option>}{tenants.map((tenant) => <option key={tenant.id} value={tenant.id}>{tenant.name} ({tenant.code})</option>)}</select></label>}{can(principal, ['NOTIFICATION_READ']) && <NotificationCenter principal={principal} tenantId={tenantId} />}<button onClick={() => void logout()}>Đăng xuất</button></div></header><div className="content"><div className="page-heading"><div><p className="eyebrow">SCENTSTATION · QUẢN TRỊ VẬN HÀNH</p><h1>{selectedMachine ? `Đầu phun: ${selectedMachine.name}` : titles[page][0]}</h1><p>{selectedMachine ? `Máy #${selectedMachine.id} (${selectedMachine.serialNumber})` : titles[page][1]}</p></div><div className="heading-actions">{selectedMachine && <button onClick={() => { setSelectedMachine(null); void load() }}>← Danh sách máy</button>}{page === 'tenants' && platform && <button className="primary" onClick={() => setTenantEditor('new')}>＋ Tạo thương hiệu</button>}{page === 'users' && <button className="primary" onClick={() => setUserEditor('new')}>＋ Tạo tài khoản</button>}{page === 'products' && tenantId && can(principal, ['CATALOG_MANAGE']) && <button className="primary" onClick={() => setShowProduct(true)}>＋ Thêm sản phẩm</button>}{page === 'campaigns' && tenantId && can(principal, ['CAMPAIGN_MANAGE']) && <button className="primary" onClick={() => setCampaignEditor('new')}>＋ Tạo chiến dịch</button>}{page === 'machines' && tenantId && principal.tenantId && can(principal, ['MACHINE_CONFIGURE']) && <><button onClick={() => setShowLocation(true)}>＋ Địa điểm</button><button className="primary" onClick={() => selectedMachine ? setSlotEditor('new') : setMachineEditor('new')}>＋ {selectedMachine ? 'Tạo đầu phun' : 'Đăng ký máy'}</button></>}{page === 'inventory' && tenantId && can(principal, ['INVENTORY_MANAGE', 'INVENTORY_ADJUST']) && <button className="primary" onClick={() => setShowInventoryForm(true)}>＋ Nghiệp vụ tồn kho</button>}{page === 'maintenance' && tenantId && can(principal, ['MAINTENANCE_MANAGE']) && <button className="primary" onClick={() => setMaintenanceEditor('new')}>＋ Tạo phiếu</button>}<button onClick={() => void load()} disabled={loading}>↻ {loading ? 'Đang tải' : 'Làm mới'}</button></div></div>{error && <div className="error-box"><span>{error}</span><button onClick={() => void load()}>Thử lại</button></div>}{notice && <div className="notice">✓ {notice}</div>}{page === 'dashboard' ? <Dashboard data={dashboard} /> : <>{page === 'transactions' && <ReportingTools filters={transactionFilters} apply={setTransactionFilters} canExport={platform && can(principal, ['REPORT_EXPORT'])} />}<section className="panel"><div className="toolbar"><label className="search">⌕<input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm trong dữ liệu đã tải…" /></label><span>{filtered.length} bản ghi</span></div>{loading ? <div className="empty"><span className="spinner" /> Đang tải dữ liệu thực…</div> : <Table rows={filtered} columns={columns} action={action} />}</section></>}</div></main>
    {showProduct && tenantId && <ProductForm tenantId={tenantId} close={() => setShowProduct(false)} saved={() => void load()} />}
    {tenantEditor && <TenantForm tenant={tenantEditor === 'new' ? undefined : tenantEditor} platform={platform} close={() => setTenantEditor(null)} saved={() => { setNotice('Đã lưu thông tin thương hiệu.'); void refreshTenants(); void load() }} />}
    {userEditor && <UserForm account={userEditor === 'new' ? undefined : userEditor} principal={principal} platform={platform} tenants={tenants} selectedTenantId={tenantId} close={() => setUserEditor(null)} saved={() => { setNotice('Đã lưu tài khoản và phân quyền.'); void load() }} />}
    {productPriceEditor && <ProductPriceForm product={productPriceEditor} close={() => setProductPriceEditor(null)} saved={() => { setNotice('Đã cập nhật giá sản phẩm.'); void load() }} />}
    {campaignEditor && tenantId && <CampaignForm campaign={campaignEditor === 'new' ? undefined : campaignEditor} tenantId={tenantId} products={catalogProducts} close={() => setCampaignEditor(null)} saved={() => { setNotice('Đã lưu chiến dịch.'); void load() }} />}
    {showLocation && tenantId && <LocationForm tenantId={tenantId} close={() => setShowLocation(false)} saved={(location) => { setLocations((current) => [...current, location]); setNotice('Đã tạo địa điểm.') }} />}
    {machineEditor && tenantId && <MachineForm machine={machineEditor === 'new' ? undefined : machineEditor} tenantId={tenantId} locations={locations} close={() => setMachineEditor(null)} saved={() => { setNotice('Đã lưu máy.'); void load() }} />}
    {slotEditor && selectedMachine && <SlotForm slot={slotEditor === 'new' ? undefined : slotEditor} machineId={selectedMachine.id} close={() => setSlotEditor(null)} saved={() => { setNotice('Đã lưu đầu phun.'); void (async () => { const slots = await backend.slots(selectedMachine.id); setRows(slots as unknown as Row[]) })() }} />}
    {showInventoryForm && tenantId && <InventoryForm tenantId={tenantId} principal={principal} batches={inventoryBatches} bottles={inventoryBottles} close={() => setShowInventoryForm(false)} saved={() => { setNotice('Đã hoàn thành nghiệp vụ tồn kho.'); void load() }} />}
    {maintenanceEditor && tenantId && <MaintenanceForm ticket={maintenanceEditor === 'new' ? undefined : maintenanceEditor} tenantId={tenantId} principal={principal} close={() => setMaintenanceEditor(null)} saved={() => { setNotice('Đã lưu phiếu bảo trì.'); void load() }} />}
  </div>
}

export default function App() {
  const [principal, setPrincipal] = useState<Principal | null>(null); const [restoring, setRestoring] = useState(Boolean(authToken.get()))
  useEffect(() => { const unauthorized = () => setPrincipal(null); window.addEventListener('scentstation:unauthorized', unauthorized); if (authToken.get()) backend.me().then(setPrincipal).catch(() => authToken.clear()).finally(() => setRestoring(false)); else setRestoring(false); return () => window.removeEventListener('scentstation:unauthorized', unauthorized) }, [])
  if (restoring) return <div className="boot"><span className="spinner" /> Đang khôi phục phiên bảo mật…</div>
  return principal ? <Admin principal={principal} exit={() => setPrincipal(null)} /> : <Login done={setPrincipal} />
}

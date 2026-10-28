
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'
// import { mockDevices } from './data/mockDevices';
import type { Device } from './types/device';
import { deviceService } from './services/deviceService';

type Role =
  | 'Admin'
  | 'Operator'
  | 'Technician'
  | 'Inventory Manager'
  | 'Finance'

type Page =
  | 'dashboard'
  | 'products'
  | 'campaigns'
  | 'inventory'
  | 'devices'
  | 'alerts'
  | 'maintenance'
  | 'orders'
  | 'audit'

const roles: Role[] = [
  'Admin',
  'Operator',
  'Technician',
  'Inventory Manager',
  'Finance',
]

const navigation: {
  id: Page
  label: string
  icon: string
  permission: Role[]
}[] = [
  {
    id: 'dashboard',
    label: 'Tổng quan',
    icon: '▦',
    permission: roles,
  },
  {
    id: 'products',
    label: 'Sản phẩm',
    icon: '◈',
    permission: ['Admin', 'Operator', 'Inventory Manager'],
  },
  {
    id: 'campaigns',
    label: 'Chiến dịch',
    icon: '◷',
    permission: ['Admin', 'Operator'],
  },
  {
    id: 'inventory',
    label: 'Quản lý tồn kho',
    icon: '▤',
    permission: ['Admin', 'Inventory Manager', 'Technician'],
  },
  {
    id: 'devices',
    label: 'Máy phun',
    icon: '▣',
    permission: ['Admin', 'Operator', 'Technician'],
  },
  {
    id: 'alerts',
    label: 'Cảnh báo',
    icon: '⚑',
    permission: ['Admin', 'Operator', 'Technician'],
  },
  {
    id: 'maintenance',
    label: 'Bảo trì',
    icon: '⚙',
    permission: ['Admin', 'Technician'],
  },
  {
    id: 'orders',
    label: 'Đơn hàng & thanh toán',
    icon: '▧',
    permission: ['Admin', 'Operator', 'Finance'],
  },
  {
    id: 'audit',
    label: 'Nhật ký hệ thống',
    icon: '≡',
    permission: ['Admin', 'Finance',],
  },
]


type Product = {
  name: string
  category: string
  price: string
  stock: number
  status: string
}

type ProductForm = {
  name: string
  category: string
  price: string
  stock: string
}

type InventoryHistory = {
  id: number
  productName: string
  type: 'import' | 'export'
  quantity: number
  previousStock: number
  newStock: number
  reason: string
  timestamp: string
}

const initialProducts: Product[] = [
  {
    name: 'Citrus No. 08',
    category: 'Citrus',
    price: '25.000 ₫',
    stock: 82,
    status: 'Đang kinh doanh',
  },
  {
    name: 'Ocean Breeze',
    category: 'Fresh',
    price: '30.000 ₫',
    stock: 16,
    status: 'Đang kinh doanh',
  },
  {
    name: 'Rose Velvet',
    category: 'Floral',
    price: '35.000 ₫',
    stock: 7,
    status: 'Sắp hết hàng',
  },
  {
    name: 'Woody Night',
    category: 'Woody',
    price: '40.000 ₫',
    stock: 0,
    status: 'Hết hàng',
  },
]

type Campaign = {
  id: number
  name: string
  startDate: string
  endDate: string
}

const initialCampaigns: Campaign[] = [
  {
    id: 1,
    name: 'Ưu đãi khai trương',
    startDate: '2026-10-20',
    endDate: '2026-10-30',
  },
  {
    id: 2,
    name: 'Tuần lễ hương thơm',
    startDate: '2026-10-25',
    endDate: '2026-11-05',
  },
  {
    id: 3,
    name: 'Khuyến mãi mùa hè',
    startDate: '2026-06-01',
    endDate: '2026-06-30',
  },
]

// const devices: Device[] = mockDevices;

// const devices = [
//   {
//     name: 'Đầu phun SP-001',
//     product: 'Citrus No. 08',
//     location: 'Tầng 1 · Sảnh chính',
//     status: 'Online',
//     stock: '82%',
//   },
//   {
//     name: 'Đầu phun SP-002',
//     product: 'Ocean Breeze',
//     location: 'Tầng 2 · Khu mua sắm',
//     status: 'Online',
//     stock: '45%',
//   },
//   {
//     name: 'Đầu phun SP-003',
//     product: 'Rose Velvet',
//     location: 'Tầng 3 · Khu dịch vụ',
//     status: 'Offline',
//     stock: '12%',
//   },
//   {
//     name: 'Đầu phun SP-004',
//     product: 'Woody Night',
//     location: 'Tầng 3 · Khu dịch vụ',
//     status: 'Online',
//     stock: '0%',
//   },
// ]



type Order = {
  id: string
  product: string
  time: string
  amount: number
  paymentStatus: 'Thành công' | 'Thất bại' | 'Đang xử lý'
  fulfillmentStatus: 'Hoàn tất' | 'Cần kiểm tra' | 'Chưa xử lý'
  reviewNote: string
}


type AlertSeverity = 'Cao' | 'Trung bình' | 'Thấp'
type AlertStatus = 'Chưa xử lý' | 'Đang xử lý' | 'Đã xử lý'

type SystemAlert = {
  id: string
  title: string
  description: string
  source: string
  severity: AlertSeverity
  status: AlertStatus
  time: string
  resolutionNote: string
}


type MaintenanceStatus =
  | 'Chờ xử lý'
  | 'Đang xử lý'
  | 'Hoàn thành'

type MaintenancePriority = 'Thấp' | 'Trung bình' | 'Cao'

type MaintenanceTask = {
  id: string
  deviceName: string
  title: string
  description: string
  priority: MaintenancePriority
  status: MaintenanceStatus
  assignee: string
  createdAt: string
  updatedAt: string
  resolutionNote: string
}

type AuditLog = {
  id: string
  time: string
  actor: string
  action: string
  target: string
  detail: string
  severity: 'info' | 'warning' | 'success'
}

const initialMaintenanceTasks: MaintenanceTask[] = [
  {
    id: 'MNT-001',
    deviceName: 'Máy phun SP-003',
    title: 'Kiểm tra mất kết nối',
    description: 'Thiết bị không gửi trạng thái kết nối về hệ thống.',
    priority: 'Cao',
    status: 'Chờ xử lý',
    assignee: 'Technician',
    createdAt: '2026-10-27 08:30',
    updatedAt: '2026-10-27 08:30',
    resolutionNote: '',
  },
  {
    id: 'MNT-002',
    deviceName: 'Máy phun SP-002',
    title: 'Kiểm tra đầu phun',
    description: 'Kiểm tra khả năng phun và vệ sinh đầu phun.',
    priority: 'Trung bình',
    status: 'Đang xử lý',
    assignee: 'Technician',
    createdAt: '2026-10-27 09:00',
    updatedAt: '2026-10-27 09:15',
    resolutionNote: '',
  },
]

const initialAuditLogs: AuditLog[] = [
  {
    id: 'LOG-001',
    time: '2026-10-27 08:30:00',
    actor: 'Admin',
    action: 'CREATE',
    target: 'MNT-001',
    detail: 'Tạo phiếu kiểm tra máy phun SP-003.',
    severity: 'warning',
  },
  {
    id: 'LOG-002',
    time: '2026-10-27 09:15:00',
    actor: 'Technician',
    action: 'UPDATE',
    target: 'MNT-002',
    detail: 'Chuyển phiếu bảo trì sang trạng thái đang xử lý.',
    severity: 'info',
  },
]


const initialAlerts: SystemAlert[] = [
  {
    id: 'ALT-001',
    title: 'Máy phun mất kết nối',
    description: 'Không nhận được trạng thái kết nối từ thiết bị.',
    source: 'SP-003',
    severity: 'Cao',
    status: 'Chưa xử lý',
    time: '09:15',
    resolutionNote: '',
  },
  {
    id: 'ALT-002',
    title: 'Sản phẩm sắp hết hàng',
    description: 'Lượng tồn kho đã chạm ngưỡng cảnh báo.',
    source: 'Rose Velvet',
    severity: 'Trung bình',
    status: 'Chưa xử lý',
    time: '09:21',
    resolutionNote: '',
  },
  {
    id: 'ALT-003',
    title: 'Cần kiểm tra cấp phát',
    description: 'Cần xác minh kết quả cấp phát sau thanh toán.',
    source: 'ORD-1026',
    severity: 'Trung bình',
    status: 'Đang xử lý',
    time: '09:30',
    resolutionNote: '',
  },
  {
    id: 'ALT-004',
    title: 'Tồn kho đã hết',
    description: 'Sản phẩm hiện không còn hàng.',
    source: 'Woody Night',
    severity: 'Cao',
    status: 'Chưa xử lý',
    time: '09:35',
    resolutionNote: '',
  },
]


const initialOrders: Order[] = [
  {
    id: 'ORD-1028',
    product: 'Citrus No. 08',
    time: '09:42',
    amount: 25000,
    paymentStatus: 'Thành công',
    fulfillmentStatus: 'Hoàn tất',
    reviewNote: '',
  },
  {
    id: 'ORD-1027',
    product: 'Ocean Breeze',
    time: '09:36',
    amount: 30000,
    paymentStatus: 'Thành công',
    fulfillmentStatus: 'Hoàn tất',
    reviewNote: '',
  },
  {
    id: 'ORD-1026',
    product: 'Rose Velvet',
    time: '09:21',
    amount: 35000,
    paymentStatus: 'Thành công',
    fulfillmentStatus: 'Cần kiểm tra',
    reviewNote: '',
  },
  {
    id: 'ORD-1025',
    product: 'Woody Night',
    time: '09:10',
    amount: 40000,
    paymentStatus: 'Thất bại',
    fulfillmentStatus: 'Chưa xử lý',
    reviewNote: '',
  },
]

const pageTitles: Record<Page, string> = {
  dashboard: 'Tổng quan hệ thống',
  products: 'Quản lý sản phẩm',
  campaigns: 'Quản lý chiến dịch',
  inventory: 'Quản lý tồn kho',
  devices: 'Quản lý máy phun',
  alerts: 'Cảnh báo hệ thống',
  maintenance: 'Bảo trì thiết bị',
  orders: 'Đơn hàng & thanh toán',
  audit: 'Nhật ký hệ thống',
}

function App() {
  const [role, setRole] = useState<Role>('Admin')
  const [products, setProducts] = useState<Product[]>(initialProducts)
  const [orders, setOrders] = useState<Order[]>(initialOrders)
  const [orderSearch, setOrderSearch] = useState('')
  const [orderStatusFilter, setOrderStatusFilter] = useState('all')
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null)
  const [reviewNote, setReviewNote] = useState('')
  const [reviewingOrderId, setReviewingOrderId] = useState<string | null>(null) 
  const [alerts] = useState<SystemAlert[]>(initialAlerts)
  const [alertSearch, setAlertSearch] = useState('')
  const [alertSeverityFilter, setAlertSeverityFilter] = useState('all')
  const [alertStatusFilter, setAlertStatusFilter] = useState('all')

  const filteredOrders = orders.filter((order) => {
  const search = orderSearch.trim().toLowerCase()

  const matchesSearch =
    order.id.toLowerCase().includes(search) ||
    order.product.toLowerCase().includes(search)

  const matchesStatus =
    orderStatusFilter === 'all' ||
    order.paymentStatus === orderStatusFilter ||
    order.fulfillmentStatus === orderStatusFilter

  return matchesSearch && matchesStatus
  })

  
  const filteredAlerts = alerts.filter((alert) => {
  const keyword = alertSearch.trim().toLowerCase()

  const matchesSearch =
    alert.id.toLowerCase().includes(keyword) ||
    alert.title.toLowerCase().includes(keyword) ||
    alert.description.toLowerCase().includes(keyword) ||
    alert.source.toLowerCase().includes(keyword)

  const matchesSeverity =
    alertSeverityFilter === 'all' ||
    alert.severity === alertSeverityFilter

  const matchesStatus =
    alertStatusFilter === 'all' ||
    alert.status === alertStatusFilter

  return matchesSearch && matchesSeverity && matchesStatus
  })

  const [campaigns] = useState<Campaign[]>(initialCampaigns)
  const [inventoryHistory, setInventoryHistory] =
    useState<InventoryHistory[]>([])
  const [showProductForm, setShowProductForm] = useState(false)
  const [editingProduct, setEditingProduct] = useState<string | null>(null)
  const [devices, setDevices] = useState<Device[]>([])
  const [devicesLoading, setDevicesLoading] = useState(true)
  const [devicesError, setDevicesError] = useState('')
  useEffect(() => {
  let cancelled = false

  const loadDevices = async () => {
    try {
      setDevicesLoading(true)
      setDevicesError('')

      const data = await deviceService.getDevices()

      if (!cancelled) {
        setDevices(data)
      }
    } catch {
      if (!cancelled) {
        setDevicesError('Không thể tải danh sách máy phun.')
      }
    } finally {
      if (!cancelled) {
        setDevicesLoading(false)
      }
    }
  }

  void loadDevices()

  return () => {
    cancelled = true
  }
  }, [])
  const [productForm, setProductForm] = useState<ProductForm>({
    name: '',
    category: '',
    price: '',
    stock: '0',})
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null)
  const openDeviceDetail = (device: Device) => {
  setSelectedDevice(device)
  }

  const closeDeviceDetail = () => {
    setSelectedDevice(null)
  }
  const [page, setPage] = useState<Page>('dashboard')
  const [loggedIn, setLoggedIn] = useState(false)
  const [email, setEmail] = useState('admin@example.com')
  const [password, setPassword] = useState('')
  const [search, setSearch] = useState('')
  const [notice, setNotice] = useState('')
  const [showLoginPassword, setShowLoginPassword] = useState(false) 
  const [adjustingProduct, setAdjustingProduct] = useState<string | null>(null)
  const [adjustmentType, setAdjustmentType] = useState<'import' | 'export'>('import')
  const [adjustmentQuantity, setAdjustmentQuantity] = useState('')
  const [adjustmentReason, setAdjustmentReason] = useState('')
  
  const [maintenanceTasks, setMaintenanceTasks] =
    useState<MaintenanceTask[]>(initialMaintenanceTasks)

  const [auditLogs, setAuditLogs] =
    useState<AuditLog[]>(initialAuditLogs)

  const [maintenanceSearch, setMaintenanceSearch] = useState('')
  const [maintenanceStatusFilter, setMaintenanceStatusFilter] =
    useState('all')

  const [showMaintenanceForm, setShowMaintenanceForm] = useState(false)
  const [selectedMaintenance, setSelectedMaintenance] =
    useState<MaintenanceTask | null>(null)

  const [maintenanceForm, setMaintenanceForm] = useState({
    deviceName: '',
    title: '',
    description: '',
    priority: 'Trung bình' as MaintenancePriority,
    assignee: 'Technician',
  })

  const [resolutionNote, setResolutionNote] = useState('')

  const can = (permission: string) => {
    const permissions: Record<Role, string[]> = {
      Admin: [
        'product.manage',
        'inventory.adjust',
        'device.manage',
        'device.diagnostic_spray',
        'maintenance.manage',
        'payment.refund',
        'audit.view',
      ],
      Operator: ['product.manage', 'device.manage'],
      Technician: [
        'inventory.adjust',
        'device.manage',
        'device.diagnostic_spray',
        'maintenance.manage',
      ],
      'Inventory Manager': ['product.manage', 'inventory.adjust'],
      Finance: ['payment.refund', 'audit.view'],
    }

    return permissions[role].includes(permission)
  }

  const visibleNavigation = navigation.filter((item) =>
    item.permission.includes(role),
  )

  const filteredProducts = products.filter((product) =>
    `${product.name} ${product.category}`
      .toLowerCase()
      .includes(search.toLowerCase()),
  )

  
  const lowStockThreshold = 10

  const lowStockCount = products.filter(
    (product) =>
      product.stock > 0 &&
      product.stock <= lowStockThreshold,
  ).length

  const outOfStockCount = products.filter(
    (product) => product.stock === 0,
  ).length

  const login = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    if (!email.trim() || !password.trim()) {
      setNotice('Vui lòng nhập email và mật khẩu.')
      return
    }

    setLoggedIn(true)
    setPage('dashboard')
    setNotice('')
  }

  const logout = () => {
    setLoggedIn(false)
    setPassword('')
    setNotice('')
  }

  
  const openInventoryAdjustment = (productName: string) => {
    setAdjustingProduct(productName)
    setAdjustmentType('import')
    setAdjustmentQuantity('')
    setAdjustmentReason('')
    setNotice('')
  }

  const closeInventoryAdjustment = () => {
    setAdjustingProduct(null)
    setAdjustmentQuantity('')
    setAdjustmentReason('')
  }

  const submitInventoryAdjustment = () => {
    if (!adjustingProduct) return

  const quantity = Number(adjustmentQuantity)

  if (
    !Number.isSafeInteger(quantity) ||
    quantity <= 0
  ) {
    setNotice('Số lượng phải là số nguyên lớn hơn 0.')
    return
  }

  if (!adjustmentReason.trim()) {
    setNotice('Vui lòng nhập lý do điều chỉnh tồn kho.')
    return
  }

  const product = products.find(
    (item) => item.name === adjustingProduct,
  )

  if (!product) {
    setNotice('Không tìm thấy sản phẩm cần điều chỉnh.')
    return
  }

  const newStock =
    adjustmentType === 'import'
      ? product.stock + quantity
      : product.stock - quantity

  if (!Number.isSafeInteger(newStock) || newStock < 0) {
    setNotice('Số lượng xuất vượt quá tồn kho hiện tại.')
    return
  }

  setProducts((currentProducts) =>
    currentProducts.map((item) =>
      item.name === adjustingProduct
        ? {
            ...item,
            stock: newStock,
            status:
              newStock === 0
                ? 'Hết hàng'
                : newStock <= lowStockThreshold
                  ? 'Sắp hết hàng'
                  : 'Đang kinh doanh',
          }
        : item,
    ),
  )

  setInventoryHistory((currentHistory) => [
  {
    id: Date.now(),
    productName: adjustingProduct,
    type: adjustmentType,
    quantity,
    previousStock: product.stock,
    newStock,
    reason: adjustmentReason.trim(),
    timestamp: new Date().toLocaleString('vi-VN'),
  },
  ...currentHistory,
  ])

  setNotice(
    `Đã ${
      adjustmentType === 'import' ? 'nhập thêm' : 'xuất'
    } ${quantity} đơn vị ${adjustingProduct}. Tồn kho mới: ${newStock}. Lý do: ${adjustmentReason.trim()}`,
  )

  closeInventoryAdjustment()
  }

  const openOrderReview = (order: Order) => {
    setReviewingOrderId(order.id)
    setReviewNote(order.reviewNote)
    setNotice('')
  }

  const saveOrderReview = () => {
    if (!reviewingOrderId) return

    if (!reviewNote.trim()) {
      setNotice('Vui lòng nhập lý do hoặc ghi chú xử lý.')
      return
  }

  setOrders((currentOrders) =>
    currentOrders.map((order) =>
      order.id === reviewingOrderId
        ? { ...order, reviewNote: reviewNote.trim() }
        : order,
    ),
  )

  setNotice(`Đã lưu ghi chú xử lý cho đơn ${reviewingOrderId}.`)
  setReviewingOrderId(null)
  setReviewNote('')
  }

  const handleAddProduct = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

  const name = productForm.name.trim()
  const category = productForm.category.trim()
  const priceValue = Number(
    productForm.price.replace(/[^\d]/g, ''),
  )
  const stockValue = Number(productForm.stock)

  if (!name || !category || !productForm.price.trim()) {
    setNotice('Vui lòng nhập đầy đủ thông tin sản phẩm.')
    return
  }

  if (!Number.isFinite(priceValue) || priceValue <= 0) {
    setNotice('Giá sản phẩm phải lớn hơn 0.')
    return
  }

  if (
    productForm.stock.trim() === '' ||
    !Number.isInteger(stockValue) ||
    stockValue < 0
  ) {
    setNotice('Số lượng tồn kho phải là số nguyên không âm.')
    return
  }

  const duplicated = products.some(
    (product) =>
    product.name.toLowerCase() === name.toLowerCase() &&
    product.name !== editingProduct,
  )

  if (duplicated) {
    setNotice('Tên sản phẩm đã tồn tại.')
    return
  }

  const newProduct: Product = {
    name,
    category,
    price: `${priceValue.toLocaleString('vi-VN')} ₫`,
    stock: stockValue,
    status:
      stockValue === 0
        ? 'Hết hàng'
        : stockValue <= lowStockThreshold
          ? 'Sắp hết'
          : 'Đang kinh doanh',
  }

  if (editingProduct !== null) {
    setProducts((currentProducts) =>
      currentProducts.map((product) =>
        product.name === editingProduct ? newProduct : product,
      ),
    )
    setNotice('Đã cập nhật sản phẩm thành công.')
  } else {
    setProducts((currentProducts) => [...currentProducts, newProduct])
    setNotice('Đã thêm sản phẩm thành công.')
  }

  setProductForm({
    name: '',
    category: '',
    price: '',
    stock: '0',
  })

  setEditingProduct(null)
  setShowProductForm(false)
  }

  if (!loggedIn) {
    return (
      <main className="login-screen">
        <section className="login-brand">
          <div className="brand-logo">S</div>
          <span className="brand-name">SCENT<span>OS</span></span>
          <div className="login-brand-copy">
            <span className="eyebrow">SMART FRAGRANCE PLATFORM</span>
            <h1>Quản lý thông minh.<br />Vận hành tinh gọn.</h1>
            <p>Một nền tảng quản trị sản phẩm, thiết bị phun và hoạt động kinh doanh tập trung.</p>
            <div className="login-feature">
              <span>✓</span> Theo dõi thiết bị theo thời gian thực
            </div>
            <div className="login-feature">
              <span>✓</span> Quản lý tồn kho và giao dịch
            </div>
            <div className="login-feature">
              <span>✓</span> Kiểm soát truy cập theo vai trò
            </div>
          </div>
          <div className="login-footer">SCENTOS ADMIN · DEMO ENVIRONMENT</div>
        </section>

        <section className="login-panel">
          <form className="login-form" onSubmit={login}>
            <div className="mobile-brand">
              <div className="brand-logo">S</div>
              <span className="brand-name">SCENT<span>OS</span></span>
            </div>
            <span className="eyebrow">WELCOME BACK</span>
            <h2>Đăng nhập</h2>
            <p className="muted">Đăng nhập để truy cập trang quản trị.</p>

            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="admin@example.com"
              required
            />

            <label htmlFor="password">Mật khẩu</label>
            <div className="password-field">
              <input
                id="password"
                type={showLoginPassword ? 'text' : 'password'}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Nhập mật khẩu bất kỳ cho bản demo"
                required
              />
              <button
                className="password-toggle"
                type="button"
                onClick={() => setShowLoginPassword(!showLoginPassword)}
              >
                {showLoginPassword ? 'Ẩn' : 'Hiện'}
              </button>
            </div>

            {notice && <p className="form-error">{notice}</p>}

            <button className="primary-button login-button" type="submit">
              Đăng nhập <span>→</span>
            </button>

            <div className="demo-note">
              <strong>Chế độ trình diễn</strong>
              <p>Đây là đăng nhập mô phỏng. Chưa kết nối xác thực backend.</p>
            </div>
          </form>
        </section>
      </main>
    )
  }

  const handleEditProduct = (product: Product) => {
  setProductForm({
    name: product.name,
    category: product.category,
    price: product.price.replace(/[^\d]/g, ''),
    stock: String(product.stock),
  })

  setEditingProduct(product.name)
  setShowProductForm(true)
  setNotice('')
  }

  
const handleDeleteProduct = (product: Product) => {
  const confirmed = window.confirm(
    `Bạn có chắc chắn muốn xóa sản phẩm "${product.name}" không?`,
  )

  if (!confirmed) {
    return
  }

  setProducts((currentProducts) =>
    currentProducts.filter(
      (item) => item.name !== product.name,
    ),
  )

  if (editingProduct === product.name) {
    setEditingProduct(null)
    setShowProductForm(false)
    setProductForm({
      name: '',
      category: '',
      price: '',
      stock: '0',
    })
  }

  setNotice(`Đã xóa sản phẩm "${product.name}" thành công.`)
  }

  const renderDashboard = () => (
    <>
      <div className="metric-grid">
        <article className="metric-card">
          <div className="metric-top">
            <span className="metric-icon purple">◈</span>
            <span className="metric-change positive">+12,5%</span>
          </div>
          <p>Tổng sản phẩm</p>
          <h3>{products.length}</h3>
          <span className="metric-caption">4 đầu phun tương ứng</span>
        </article>

        <article className="metric-card">
          <div className="metric-top">
            <span className="metric-icon blue">▣</span>
            <span className="metric-change positive">+2 thiết bị</span>
          </div>
          <p>Thiết bị trực tuyến</p>
          <h3>{devices.filter((device) => device.status === 'Online').length}
            <small> / {devices.length}</small>
          </h3>
          <span className="metric-caption">
            {devices.filter((device) => device.status === 'Offline').length} đầu phun ngoại tuyến
          </span>
        </article>

        <article className="metric-card">
          <div className="metric-top">
            <span className="metric-icon green">↗</span>
            <span className="metric-change positive">+8,2%</span>
          </div>
          <p>Doanh thu hôm nay</p>
          <h3>4,85M <small>₫</small></h3>
          <span className="metric-caption">So với ngày hôm trước</span>
        </article>

        <article className="metric-card">
          <div className="metric-top">
            <span className="metric-icon orange">⚑</span>
            <span className="metric-change warning">Cần xử lý</span>
          </div>
          <p>Cảnh báo đang mở</p>
          <h3>5</h3>
          <span className="metric-caption">3 mức cao · 2 mức thấp</span>
        </article>
      </div>

      <div className="dashboard-grid">
        <section className="panel inventory-panel">
          <div className="panel-heading">
            <div>
              <h3>Tình trạng sản phẩm</h3>
              <p>Theo dõi lượng hàng còn lại</p>
            </div>
            <button className="text-button" onClick={() => setPage('inventory')}>
              Xem tồn kho →
            </button>
          </div>
          {devicesLoading && (
            <p className="panel-note">Đang tải danh sách máy phun...</p>
          )}

          {devicesError && (
            <p className="panel-note">{devicesError}</p>
        )}
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Sản phẩm</th>
                  <th>Giá</th>
                  <th>Tồn kho</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {products.map((product) => (
                  <tr key={product.name}>
                    <td>
                      <div className="product-cell">
                        <span className="product-avatar">
                          {product.name.charAt(0)}
                        </span>
                        <div>
                          <strong>{product.name}</strong>
                          <small>{product.category}</small>
                        </div>
                      </div>
                    </td>
                    <td>{product.price}</td>
                    <td>
                      <strong>{product.stock}</strong>
                      <span className="unit"> đơn vị</span>
                    </td>
                    <td>
                      <span className={`status-pill ${
                        product.stock === 0
                          ? 'danger'
                          : product.stock < 10
                            ? 'warning'
                            : 'success'
                      }`}>
                        {product.stock === 0
                          ? 'Hết hàng'
                          : product.stock < 10
                            ? 'Sắp hết'
                            : 'Ổn định'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="panel device-panel">
          <div className="panel-heading">
            <div>
              <h3>Trạng thái thiết bị</h3>
              <p>Tình trạng máy phun gần đây</p>
            </div>
            <button className="icon-button" onClick={() => setPage('devices')}>↗</button>
          </div>
          <div className="device-list">
            {!devicesLoading && !devicesError && devices.map((device) => (
              <div className="device-row" key={device.name}>
                <div className="device-symbol">▣</div>
                <div className="device-info">
                  <strong>{device.name}</strong>
                  <small>{device.product} · {device.location}</small>
                </div>
                <span className={`status-pill ${device.status === 'Online' ? 'success' : 'danger'}`}>
                  {device.status}
                </span>
              </div>
            ))}
          </div>
          <div className="device-summary">
            <span>
              <i className="dot online-dot" />
              {devices.filter((device) => device.status === 'Online').length} trực tuyến
            </span>
            <span>
              <i className="dot offline-dot" />
              {devices.filter((device) => device.status === 'Offline').length} ngoại tuyến
            </span>
          </div>
        </section>
      </div>

      <section className="panel transactions-panel">
        <div className="panel-heading">
          <div>
            <h3>Giao dịch gần đây</h3>
            <p>Các giao dịch mới nhất trong hệ thống</p>
          </div>
          <button className="text-button" onClick={() => setPage('orders')}>
            Xem tất cả →
          </button>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Mã giao dịch</th>
                <th>Sản phẩm</th>
                <th>Thời gian</th>
                <th>Số tiền</th>
                <th>Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {orders.slice(0, 4).map((order) => (
                <tr key={order.id}>
                  <td><strong>{order.id}</strong></td>
                  <td>{order.product}</td>
                  <td>{order.time}</td>
                  <td>{order.amount.toLocaleString('vi-VN')} ₫</td>
                  <td>
                    <span className={`status-pill ${
                      order.paymentStatus === 'Thành công'
                        ? 'success'
                        : order.paymentStatus === 'Thất bại'
                          ? 'danger'
                          : 'warning'
                    }`}>
                      {order.paymentStatus}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )

  const renderProducts = () => (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h3>Danh sách sản phẩm</h3>
          <p>Dữ liệu mẫu phục vụ phát triển giao diện</p>
        </div>
        {can('product.manage') && (
          <button className="primary-button" onClick={() => {
            setNotice('')
            setShowProductForm(true)
        }}>
            + Thêm sản phẩm
          </button>
        )}
      </div>
      
{showProductForm && (
  <form className="product-form" onSubmit={handleAddProduct}>
    <h3>{editingProduct !== null ? 'Chỉnh sửa sản phẩm' : 'Thêm sản phẩm mới'}</h3>

    <div className="product-form-grid">
      <label>
        Tên sản phẩm
        <input
          value={productForm.name}
          onChange={(event) =>
            setProductForm((current) => ({
              ...current,
              name: event.target.value,
            }))
          }
          required
          placeholder="Ví dụ: Ocean Breeze"
        />
      </label>

      <label>
        Nhóm hương
        <input
          value={productForm.category}
          onChange={(event) =>
            setProductForm((current) => ({
              ...current,
              category: event.target.value,
            }))
          }
          required
          placeholder="Ví dụ: Fresh"
        />
      </label>

      <label>
        Giá sản phẩm (VNĐ)
        <input
          type="number"
          min="1"
          step="1"
          value={productForm.price}
          onChange={(event) =>
            setProductForm((current) => ({
              ...current,
              price: event.target.value,
            }))
          }
          required
          placeholder="25000"
        />
      </label>

      <label>
        Số lượng tồn kho
        <input
          type="number"
          min="0"
          step="1"
          value={productForm.stock}
          onChange={(event) =>
            setProductForm((current) => ({
              ...current,
              stock: event.target.value,
            }))
          }
          required
        />
      </label>
    </div>

    <div className="form-actions">
      <button
        type="button"
        className="secondary-button"
        onClick={() => {
          setShowProductForm(false)
          setEditingProduct(null)
          setProductForm({
            name: '',
            category: '',
            price: '',
            stock: '0',
          })
          setNotice('')
        }}
      >
        Hủy
      </button>

      <button type="submit" className="primary-button">
        {editingProduct !== null ? 'Cập nhật' : 'Lưu sản phẩm'}
      </button>
    </div>
  </form>
)}

      <div className="filter-bar">
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Tìm tên sản phẩm hoặc nhóm hương..."
        />
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Sản phẩm</th><th>Nhóm hương</th><th>Giá</th><th>Tồn kho</th><th>Trạng thái</th><th>Thao tác</th></tr>
          </thead>
          <tbody>
            {filteredProducts.map((product) => (
              <tr key={product.name}>
                <td><strong>{product.name}</strong></td>
                <td>{product.category}</td>
                <td>{product.price}</td>
                <td>{product.stock}</td>
                <td><span className={`status-pill ${product.stock === 0 ? 'danger' : product.stock < 10 ? 'warning' : 'success'}`}>{product.status}</span></td>
                <td>
                  {can('product.manage') && (
                    <div className="product-actions">
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() => handleEditProduct(product)}
                      >
                      Sửa
                      </button>

                      <button
                        type="button"
                        className="delete-button"
                        onClick={() => handleDeleteProduct(product)}
                      >
                        Xóa
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
            {filteredProducts.length === 0 && (
              <tr><td colSpan={6}>Không tìm thấy sản phẩm phù hợp.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  )

  const renderCampaigns = () => {
  // Lấy ngày hiện tại theo múi giờ máy người dùng.
  const now = new Date()
  const today = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('-')

  const getCampaignStatus = (campaign: Campaign) => {
    if (today < campaign.startDate) {
      return {
        label: 'Sắp diễn ra',
        className: 'warning',
      }
    }

    if (today > campaign.endDate) {
      return {
        label: 'Đã kết thúc',
        className: 'neutral',
      }
    }

    return {
      label: 'Đang diễn ra',
      className: 'success',
    }
  }

  const formatDate = (date: string) => {
    const [year, month, day] = date.split('-')
    return `${day}/${month}/${year}`
  }

  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h3>Danh sách chiến dịch</h3>
          <p>Thời gian áp dụng và trạng thái các chiến dịch</p>
        </div>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>STT</th>
              <th>Tên chiến dịch</th>
              <th>Ngày bắt đầu</th>
              <th>Ngày kết thúc</th>
              <th>Trạng thái</th>
            </tr>
          </thead>

          <tbody>
            {campaigns.map((campaign, index) => {
              const status = getCampaignStatus(campaign)

              return (
                <tr key={campaign.id}>
                  <td>{index + 1}</td>

                  <td>
                    <strong>{campaign.name}</strong>
                  </td>

                  <td>{formatDate(campaign.startDate)}</td>

                  <td>{formatDate(campaign.endDate)}</td>

                  <td>
                    <span className={`status-pill ${status.className}`}>
                      {status.label}
                    </span>
                  </td>
                </tr>
              )
            })}

            {campaigns.length === 0 && (
              <tr>
                <td colSpan={5}>Chưa có chiến dịch nào.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="panel-note">
        Trạng thái được xác định tự động theo ngày hiện tại.
        Dữ liệu chiến dịch hiện là dữ liệu mẫu trên frontend.
      </p>
    </section>
  )
  }

  const renderDevices = () => (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h3>Danh sách máy phun</h3>
          <p>Theo dõi trạng thái kết nối và lượng hàng</p>
        </div>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Đầu phun</th>
              <th>Sản phẩm</th>
              <th>Vị trí</th>
              <th>Kết nối</th>
              <th>Tồn ước tính</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {devices.map((device) => (
              <tr key={device.name}>
                <td>
                  <button
                    type="button"
                    className="text-button"
                    onClick={() => openDeviceDetail(device)}
                  >
                    {device.name}
                  </button>
                </td>
                <td>{device.product}</td>
                <td>{device.location}</td>
                <td><span className={`status-pill ${device.status === 'Online' ? 'success' : 'danger'}`}>{device.status}</span></td>
                <td>{device.stock}%</td>
                <td>
                  <div className="product-actions">
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => openDeviceDetail(device)}
                    >
                    Chi tiết
                    </button>

                    {can('device.diagnostic_spray') &&
                    device.status === 'Online' ? (
                    <button
                      type="button"
                      className="small-button"
                      onClick={() =>
                        setNotice(`Đã yêu cầu mô phỏng phun thử trên ${device.name}. Chưa gửi lệnh tới thiết bị thật.`)
                      }
                    >
                    Phun thử
                    </button>
                    ) : null}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="panel-note">Phun thử hiện chỉ là mô phỏng giao diện, không điều khiển thiết bị thật.</p>
    </section>
  )


  const renderInventory = () => (
  <>
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h3>Tổng quan tồn kho</h3>
          <p>Số liệu mẫu, chưa đồng bộ với kho thực tế</p>
        </div>
      </div>

      <div className="inventory-summary">
        <div>
          <span>Tổng mặt hàng</span>
          <strong>{products.length}</strong>
        </div>
        <div>
          <span>Sắp hết hàng</span>
          <strong className="text-orange">{lowStockCount}</strong>
        </div>
        <div>
          <span>Hết hàng</span>
          <strong className="text-red">{outOfStockCount}</strong>
        </div>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Sản phẩm</th>
              <th>Số lượng</th>
              <th>Ngưỡng cảnh báo</th>
              <th>Trạng thái</th>
              <th>Điều chỉnh</th>
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <tr key={product.name}>
                <td><strong>{product.name}</strong></td>
                <td>{product.stock}</td>
                <td>{lowStockThreshold} đơn vị</td>
                <td>
                  <span
                    className={`status-pill ${
                      product.stock === 0
                        ? 'danger'
                        : product.stock <= lowStockThreshold
                          ? 'warning'
                          : 'success'
                    }`}
                  >
                    {product.stock === 0
                      ? 'Hết hàng'
                      : product.stock <= lowStockThreshold
                        ? 'Sắp hết'
                        : 'Ổn định'}
                  </span>
                </td>
                <td>
                  {can('inventory.adjust') ? (
                    <button
                      className="small-button"
                      onClick={() => openInventoryAdjustment(product.name)}
                    >
                      Điều chỉnh
                    </button>
                  ) : (
                    <span className="muted">Không có quyền</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>

    {adjustingProduct !== null && (
      <section className="panel adjustment-panel">
        <div className="panel-heading">
          <div>
            <h3>Điều chỉnh tồn kho</h3>
            <p>Sản phẩm: {adjustingProduct}</p>
          </div>
          <button
            className="secondary-button"
            onClick={closeInventoryAdjustment}
            type="button"
          >
            Đóng
          </button>
        </div>

        <form
          onSubmit={(event) => {
            event.preventDefault()
            submitInventoryAdjustment()
          }}
        >
          <label htmlFor="adjustmentType">Loại điều chỉnh</label>
          <select
            id="adjustmentType"
            value={adjustmentType}
            onChange={(event) =>
              setAdjustmentType(event.target.value as 'import' | 'export')
            }
          >
            <option value="import">Nhập thêm hàng</option>
            <option value="export">Xuất bớt hàng</option>
          </select>

          <label htmlFor="adjustmentQuantity">Số lượng</label>
          <input
            id="adjustmentQuantity"
            type="number"
            min="1"
            step="1"
            value={adjustmentQuantity}
            onChange={(event) => setAdjustmentQuantity(event.target.value)}
            placeholder="Nhập số lượng"
            required
          />

          <label htmlFor="adjustmentReason">Lý do điều chỉnh</label>
          <textarea
            id="adjustmentReason"
            value={adjustmentReason}
            onChange={(event) => setAdjustmentReason(event.target.value)}
            placeholder="Ví dụ: Nhập hàng mới từ nhà cung cấp"
            required
          />

          <div className="heading-actions">
            <button
              type="button"
              className="secondary-button"
              onClick={closeInventoryAdjustment}
            >
              Hủy
            </button>
            <button type="submit" className="primary-button">
              Xác nhận điều chỉnh
            </button>
          </div>
        </form>
      </section>
    )}
    <section className="panel">
  <div className="panel-heading">
    <div>
      <h3>Lịch sử điều chỉnh kho</h3>
      <p>Theo dõi các lần nhập và xuất hàng</p>
    </div>
    <span className="status-pill neutral">
      {inventoryHistory.length} giao dịch
    </span>
  </div>

  <div className="table-wrap">
    <table>
      <thead>
        <tr>
          <th>Thời gian</th>
          <th>Sản phẩm</th>
          <th>Loại điều chỉnh</th>
          <th>Tồn trước</th>
          <th>Thay đổi</th>
          <th>Tồn sau</th>
          <th>Lý do</th>
        </tr>
      </thead>

      <tbody>
        {inventoryHistory.map((item) => (
          <tr key={item.id}>
            <td>{item.timestamp}</td>
            <td>
              <strong>{item.productName}</strong>
            </td>
            <td>
              <span
                className={`status-pill ${
                  item.type === 'import'
                    ? 'success'
                    : 'warning'
                }`}
              >
                {item.type === 'import'
                  ? 'Nhập kho'
                  : 'Xuất kho'}
              </span>
            </td>
            <td>{item.previousStock}</td>
            <td>
              <strong>
                {item.type === 'import' ? '+' : '-'}
                {item.quantity}
              </strong>
            </td>
            <td>{item.newStock}</td>
            <td>{item.reason}</td>
          </tr>
        ))}

        {inventoryHistory.length === 0 && (
          <tr>
            <td colSpan={7}>
              Chưa có lịch sử điều chỉnh kho.
            </td>
          </tr>
        )}
      </tbody>
    </table>
  </div>

  <p className="panel-note">
      Lịch sử hiện được lưu trong bộ nhớ frontend,
      chưa được lưu trữ lâu dài trên máy chủ.
    </p>
  </section>
  </>
  )
  
  const renderOrders = () => (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h3>Đơn hàng & thanh toán</h3>
          <p>Theo dõi giao dịch và kết quả cấp phát</p>
        </div>
      </div>

    {/* Tìm kiếm và lọc đơn hàng */}
    <div
      style={{
        display: 'flex',
        gap: '12px',
        marginBottom: '16px',
        flexWrap: 'wrap',
      }}
    >
      <input
        type="text"
        placeholder="Tìm theo mã đơn hoặc tên sản phẩm..."
        value={orderSearch}
        onChange={(e) => setOrderSearch(e.target.value)}
        style={{
          padding: '10px 12px',
          border: '1px solid #ddd',
          borderRadius: '8px',
          flex: '1',
          minWidth: '220px',
        }}
      />

      <select
        value={orderStatusFilter}
        onChange={(e) => setOrderStatusFilter(e.target.value)}
        style={{
          padding: '10px 12px',
          border: '1px solid #ddd',
          borderRadius: '8px',
          minWidth: '180px',
        }}
      >
        <option value="all">Tất cả trạng thái</option>
        <option value="Thành công">Thanh toán thành công</option>
        <option value="Thất bại">Thanh toán thất bại</option>
        <option value="Đang xử lý">Đang xử lý thanh toán</option>
        <option value="Hoàn tất">Đã cấp phát</option>
        <option value="Cần kiểm tra">Cần kiểm tra cấp phát</option>
        <option value="Chưa xử lý">Chưa xử lý cấp phát</option>
      </select>
    </div>

    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Mã đơn</th>
            <th>Sản phẩm</th>
            <th>Thời gian</th>
            <th>Số tiền</th>
            <th>Thanh toán</th>
            <th>Trạng thái xử lý</th>
            <th>Thao tác</th>
          </tr>
        </thead>

        <tbody>
          {filteredOrders.map((order) => (
            <tr key={order.id}>
              <td>
                <strong>{order.id}</strong>
              </td>

              <td>{order.product}</td>

              <td>{order.time}</td>

              <td>{order.amount.toLocaleString('vi-VN')} ₫</td>

              <td>
                <span
                  className={`status-pill ${
                    order.paymentStatus === 'Thành công'
                      ? 'success'
                      : order.paymentStatus === 'Thất bại'
                        ? 'danger'
                        : 'warning'
                  }`}
                >
                  {order.paymentStatus}
                </span>
              </td>

              <td>
                <span
                  className={`status-pill ${
                    order.fulfillmentStatus === 'Hoàn tất'
                      ? 'success'
                      : order.fulfillmentStatus === 'Chưa xử lý'
                        ? 'warning'
                        : 'danger'
                  }`}
                >
                  {order.fulfillmentStatus}
                </span>
              </td>         
              <td>
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                <button
                  className="small-button"
                  onClick={() => setSelectedOrder(order)}
                >
                Chi tiết
                </button>

                {(
                order.paymentStatus !== 'Thành công' ||
                order.fulfillmentStatus !== 'Hoàn tất'
                ) && can('payment.refund') && (
                <button
                  type="button"
                  className="small-button"
                  onClick={() => openOrderReview(order)}
                >
                  Xem xét
                </button>
              )}
              </div>
              </td>
            </tr>
          ))}

          {filteredOrders.length === 0 && (
            <tr>
              <td colSpan={7} style={{ textAlign: 'center', padding: '24px' }}>
                Không tìm thấy đơn hàng phù hợp.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>

    <p className="panel-note">
      Hoàn tiền và kiểm tra kết quả phun cần tích hợp backend trước khi sử dụng thật.
    </p>

    
    {reviewingOrderId && (
      <section className="panel" style={{ marginTop: '16px' }}>
        <div className="panel-heading">
          <div>
            <h3>Xử lý đơn hàng {reviewingOrderId}</h3>
            <p>Ghi nhận kết quả kiểm tra đơn hàng</p>
          </div>

        <button
          className="secondary-button"
          type="button"
          onClick={() => {
            setReviewingOrderId(null)
            setReviewNote('')
          }}
        >
          Hủy
        </button>
      </div>

      <label htmlFor="orderReviewNote">
        Lý do hoặc ghi chú xử lý
      </label>

      <textarea
        id="orderReviewNote"
        value={reviewNote}
        onChange={(event) => setReviewNote(event.target.value)}
        placeholder="Ví dụ: Thanh toán thành công nhưng chưa xác nhận cấp phát..."
        rows={4}
        style={{ width: '100%', marginTop: '8px' }}
      />

      <div style={{ marginTop: '12px' }}>
        <button
          type="button"
          className="primary-button"
          onClick={saveOrderReview}
        >
          Lưu xử lý
        </button>
      </div>
    </section>
    )}


    {/* Cửa sổ chi tiết đơn hàng */}
    {selectedOrder && (
      <div
        className="device-modal-overlay"
        onClick={() => setSelectedOrder(null)}
      >
        <div
          className="device-modal"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="panel-heading">
            <div>
              <h3>Chi tiết đơn hàng</h3>
              <p>{selectedOrder.id}</p>
            </div>

            <button
              className="small-button"
              onClick={() => setSelectedOrder(null)}
            >
              Đóng
            </button>
          </div>

          <div className="device-detail-grid">
            <div className="device-detail-item">
              <span>Mã đơn hàng</span>
              <strong>{selectedOrder.id}</strong>
            </div>

            <div className="device-detail-item">
              <span>Sản phẩm</span>
              <strong>{selectedOrder.product}</strong>
            </div>

            <div className="device-detail-item">
              <span>Thời gian</span>
              <strong>{selectedOrder.time}</strong>
            </div>

            <div className="device-detail-item">
              <span>Số tiền</span>
              <strong>
                {selectedOrder.amount.toLocaleString('vi-VN')} ₫
              </strong>
            </div>

            <div className="device-detail-item">
              <span>Trạng thái thanh toán</span>
              <strong>{selectedOrder.paymentStatus}</strong>
            </div>

            <div className="device-detail-item">
              <span>Trạng thái cấp phát</span>
              <strong>{selectedOrder.fulfillmentStatus}</strong>
            </div>

            <div className="device-detail-item">
              <span>Ghi chú xử lý</span>
              <strong>{selectedOrder.reviewNote || 'Chưa có ghi chú'}</strong>
            </div>
          </div>
        </div>
      </div>
    )}
  </section>
  )

  
  // const renderPlaceholder = () => {
  //   const isMaintenance = page === 'maintenance'

  //   const heading = isMaintenance
  //     ? 'Công việc bảo trì'
  //     : 'Nhật ký hoạt động'

  // const description = isMaintenance
  //   ? 'Theo dõi phiếu bảo trì và lịch sử sửa chữa thiết bị.'
  //   : 'Theo dõi các hoạt động quan trọng của hệ thống.'

  // return (
  //   <section className="panel">
  //     <div className="panel-heading">
  //       <div>
  //         <h3>{heading}</h3>
  //         <p>{description}</p>
  //       </div>
  //     </div>

  //     {isMaintenance ? (
  //       <div className="placeholder-list">
  //         {[
  //           {
  //             id: 'MNT-001',
  //             title: 'Kiểm tra kết nối SP-003',
  //             detail: 'Thiết bị không gửi trạng thái kết nối về hệ thống.',
  //             status: 'Chờ xử lý',
  //             technician: 'Technician',
  //           },
  //           {
  //             id: 'MNT-002',
  //             title: 'Kiểm tra đầu phun SP-002',
  //             detail: 'Kiểm tra hoạt động và tình trạng đầu phun.',
  //             status: 'Đang xử lý',
  //             technician: 'Technician',
  //           },
  //           {
  //             id: 'MNT-003',
  //             title: 'Bảo trì định kỳ SP-001',
  //             detail: 'Kiểm tra thiết bị theo lịch bảo trì định kỳ.',
  //             status: 'Hoàn thành',
  //             technician: 'Admin',
  //           },
  //         ].map((item) => (
  //           <div className="placeholder-row" key={item.id}>
  //             <span className="placeholder-number">
  //               {item.id}
  //             </span>

  //             <div>
  //               <strong>{item.title}</strong>
  //               <p>{item.detail}</p>
  //               <small>
  //                 Người phụ trách: {item.technician}
  //               </small>
  //             </div>

  //             <span
  //               className={`status-pill ${
  //                 item.status === 'Hoàn thành'
  //                   ? 'success'
  //                   : item.status === 'Đang xử lý'
  //                     ? 'warning'
  //                     : 'neutral'
  //               }`}
  //             >
  //               {item.status}
  //             </span>
  //           </div>
  //         ))}
  //       </div>
  //     ) : (
  //       <div className="placeholder-list">
  //         {[
  //           {
  //             time: '2026-10-27 08:30',
  //             actor: 'Admin',
  //             action: 'Đăng nhập quản trị',
  //             detail: 'Người dùng đăng nhập vào hệ thống.',
  //           },
  //           {
  //             time: '2026-10-27 08:35',
  //             actor: 'Technician',
  //             action: 'Kiểm tra thiết bị SP-003',
  //             detail: 'Ghi nhận thiết bị mất kết nối.',
  //           },
  //           {
  //             time: '2026-10-27 08:40',
  //             actor: 'Admin',
  //             action: 'Tạo phiếu bảo trì MNT-001',
  //             detail: 'Tạo yêu cầu kiểm tra kết nối thiết bị.',
  //           },
  //         ].map((item, index) => (
  //           <div
  //             className="placeholder-row"
  //             key={`${item.time}-${index}`}
  //           >
  //             <span className="placeholder-number">
  //               {String(index + 1).padStart(2, '0')}
  //             </span>

  //             <div>
  //               <strong>{item.action}</strong>
  //               <p>{item.detail}</p>
  //               <small>
  //                 {item.time} · Người thực hiện: {item.actor}
  //               </small>
  //             </div>

  //             <span className="status-pill neutral">
  //               Nhật ký
  //             </span>
  //           </div>
  //         ))}
  //       </div>
  //     )}

  //     <p className="panel-note">
  //       {isMaintenance
  //         ? 'Dữ liệu hiện đang là dữ liệu mẫu. Chức năng cập nhật và lưu phiếu sẽ được triển khai ở bước tiếp theo.'
  //         : 'Nhật ký hiện đang sử dụng dữ liệu mẫu, chưa được ghi tự động vào cơ sở dữ liệu.'}
  //     </p>
  //   </section>
  // )
  // }


  const renderAlerts = () => (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h3>Danh sách cảnh báo</h3>
          <p>Theo dõi sự cố thiết bị, tồn kho và giao dịch bất thường</p>
        </div>

        <span className="status-pill warning">
          {alerts.filter((alert) => alert.status !== 'Đã xử lý').length} chưa hoàn tất
        </span>
      </div>

      <div className="filter-bar">
        <input
          type="text"
          value={alertSearch}
          onChange={(event) => setAlertSearch(event.target.value)}
          placeholder="Tìm mã, tiêu đề, nguồn hoặc mô tả cảnh báo..."
        />

        <select
          value={alertSeverityFilter}
          onChange={(event) => setAlertSeverityFilter(event.target.value)}
        >
          <option value="all">Tất cả mức độ</option>
          <option value="Cao">Cao</option>
          <option value="Trung bình">Trung bình</option>
          <option value="Thấp">Thấp</option>
        </select>

        <select
          value={alertStatusFilter}
          onChange={(event) => setAlertStatusFilter(event.target.value)}
        >
          <option value="all">Tất cả trạng thái</option>
          <option value="Chưa xử lý">Chưa xử lý</option>
          <option value="Đang xử lý">Đang xử lý</option>
          <option value="Đã xử lý">Đã xử lý</option>
        </select>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Mã cảnh báo</th>
              <th>Nội dung</th>
              <th>Nguồn</th>
              <th>Mức độ</th>
              <th>Thời gian</th>
              <th>Trạng thái</th>
            </tr>
          </thead>

          <tbody>
            {filteredAlerts.map((alert) => (
              <tr key={alert.id}>
                <td><strong>{alert.id}</strong></td>
                <td>
                  <strong>{alert.title}</strong>
                  <div className="muted">{alert.description}</div>
                </td>
                <td>{alert.source}</td>
                <td>
                  <span
                    className={`status-pill ${
                      alert.severity === 'Cao'
                        ? 'danger'
                        : alert.severity === 'Trung bình'
                          ? 'warning'
                          : 'neutral'
                    }`}
                  >
                    {alert.severity}
                  </span>
                </td>
                <td>{alert.time}</td>
                <td>
                  <span
                    className={`status-pill ${
                      alert.status === 'Đã xử lý'
                        ? 'success'
                        : alert.status === 'Đang xử lý'
                          ? 'warning'
                          : 'danger'
                    }`}
                  >
                    {alert.status}
                  </span>
                </td>
              </tr>
            ))}

            {filteredAlerts.length === 0 && (
              <tr>
                <td colSpan={6} style={{ textAlign: 'center', padding: '24px' }}>
                  Không tìm thấy cảnh báo phù hợp.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="panel-note">
        Dữ liệu cảnh báo hiện là dữ liệu mẫu trong bộ nhớ frontend.
        Chưa kết nối hệ thống phát hiện hoặc lưu trữ cảnh báo trên backend.
      </p>
    </section>
    )

  
  const renderMaintenance = () => {
    const filteredTasks = maintenanceTasks.filter((task) => {
    const keyword = maintenanceSearch.trim().toLowerCase()

    const matchesSearch = [
      task.id,
      task.deviceName,
      task.title,
      task.assignee,
    ].some((value) => value.toLowerCase().includes(keyword))

    const matchesStatus =
      maintenanceStatusFilter === 'all' ||
      task.status === maintenanceStatusFilter

    return matchesSearch && matchesStatus
  })

  const statusClass = (status: MaintenanceStatus) => {
    if (status === 'Hoàn thành') return 'success'
    if (status === 'Đang xử lý') return 'warning'
    return 'neutral'
  }

  return (
    <>
      <div className="inventory-summary">
        <div>
          <span>Tổng phiếu</span>
          <strong>{maintenanceTasks.length}</strong>
        </div>
        <div>
          <span>Chờ xử lý</span>
          <strong>
            {maintenanceTasks.filter(
              (task) => task.status === 'Chờ xử lý',
            ).length}
          </strong>
        </div>
        <div>
          <span>Đang xử lý</span>
          <strong>
            {maintenanceTasks.filter(
              (task) => task.status === 'Đang xử lý',
            ).length}
          </strong>
        </div>
        <div>
          <span>Hoàn thành</span>
          <strong>
            {maintenanceTasks.filter(
              (task) => task.status === 'Hoàn thành',
            ).length}
          </strong>
        </div>
      </div>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <h3>Danh sách phiếu bảo trì</h3>
            <p>Theo dõi sự cố và tiến độ sửa chữa thiết bị</p>
          </div>

          {can('maintenance.manage') && (
            <button
              className="primary-button"
              onClick={() => {
                setNotice('')
                setShowMaintenanceForm(true)
              }}
            >
              + Tạo phiếu
            </button>
          )}
        </div>

        <div className="filter-bar">
          <input
            value={maintenanceSearch}
            onChange={(event) =>
              setMaintenanceSearch(event.target.value)
            }
            placeholder="Tìm mã phiếu, thiết bị hoặc công việc..."
          />

          <select
            value={maintenanceStatusFilter}
            onChange={(event) =>
              setMaintenanceStatusFilter(event.target.value)
            }
          >
            <option value="all">Tất cả trạng thái</option>
            <option value="Chờ xử lý">Chờ xử lý</option>
            <option value="Đang xử lý">Đang xử lý</option>
            <option value="Hoàn thành">Hoàn thành</option>
          </select>
        </div>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Mã phiếu</th>
                <th>Thiết bị / Công việc</th>
                <th>Ưu tiên</th>
                <th>Phụ trách</th>
                <th>Trạng thái</th>
                <th>Cập nhật</th>
                <th>Thao tác</th>
              </tr>
            </thead>

            <tbody>
              {filteredTasks.map((task) => (
                <tr key={task.id}>
                  <td><strong>{task.id}</strong></td>

                  <td>
                    <strong>{task.deviceName}</strong>
                    <div className="muted">{task.title}</div>
                  </td>

                  <td>
                    <span className={`status-pill ${
                      task.priority === 'Cao'
                        ? 'danger'
                        : task.priority === 'Trung bình'
                          ? 'warning'
                          : 'neutral'
                    }`}>
                      {task.priority}
                    </span>
                  </td>

                  <td>{task.assignee}</td>

                  <td>
                    <span className={`status-pill ${statusClass(task.status)}`}>
                      {task.status}
                    </span>
                  </td>

                  <td>{task.updatedAt}</td>

                  <td>
                    <button
                      className="secondary-button"
                      onClick={() => {
                        setSelectedMaintenance(task)
                        setResolutionNote(task.resolutionNote)
                      }}
                    >
                      Chi tiết
                    </button>
                  </td>
                </tr>
              ))}

              {filteredTasks.length === 0 && (
                <tr>
                  <td colSpan={7}>
                    Không tìm thấy phiếu bảo trì phù hợp.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {showMaintenanceForm && (
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h3>Tạo phiếu bảo trì</h3>
              <p>Ghi nhận công việc cần thực hiện</p>
            </div>
          </div>

          <form
            className="product-form"
            onSubmit={createMaintenanceTask}
          >
            <div className="product-form-grid">
              <label>
                Thiết bị
                <select
                  value={maintenanceForm.deviceName}
                  onChange={(event) =>
                    setMaintenanceForm((current) => ({
                      ...current,
                      deviceName: event.target.value,
                    }))
                  }
                  required
                >
                  <option value="">Chọn máy phun</option>
                  {devices.map((device) => (
                    <option key={device.name} value={device.name}>
                      {device.name}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Người phụ trách
                <select
                  value={maintenanceForm.assignee}
                  onChange={(event) =>
                    setMaintenanceForm((current) => ({
                      ...current,
                      assignee: event.target.value,
                    }))
                  }
                >
                  <option value="Technician">Technician</option>
                  <option value="Admin">Admin</option>
                </select>
              </label>

              <label>
                Mức độ ưu tiên
                <select
                  value={maintenanceForm.priority}
                  onChange={(event) =>
                    setMaintenanceForm((current) => ({
                      ...current,
                      priority: event.target.value as MaintenancePriority,
                    }))
                  }
                >
                  <option value="Thấp">Thấp</option>
                  <option value="Trung bình">Trung bình</option>
                  <option value="Cao">Cao</option>
                </select>
              </label>

              <label>
                Tên công việc
                <input
                  value={maintenanceForm.title}
                  onChange={(event) =>
                    setMaintenanceForm((current) => ({
                      ...current,
                      title: event.target.value,
                    }))
                  }
                  required
                  placeholder="Ví dụ: Kiểm tra đầu phun"
                />
              </label>
            </div>

            <label>
              Mô tả sự cố
              <textarea
                value={maintenanceForm.description}
                onChange={(event) =>
                  setMaintenanceForm((current) => ({
                    ...current,
                    description: event.target.value,
                  }))
                }
                required
                rows={3}
              />
            </label>

            <div className="form-actions">
              <button
                type="button"
                className="secondary-button"
                onClick={() => setShowMaintenanceForm(false)}
              >
                Hủy
              </button>
              <button className="primary-button" type="submit">
                Tạo phiếu
              </button>
            </div>
          </form>
        </section>
      )}

      {selectedMaintenance && (
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h3>{selectedMaintenance.id} — Chi tiết bảo trì</h3>
              <p>{selectedMaintenance.title}</p>
            </div>
            <button
              className="secondary-button"
              onClick={() => {
                setSelectedMaintenance(null)
                setResolutionNote('')
              }}
            >
              Đóng
            </button>
          </div>

          <p><strong>Thiết bị:</strong> {selectedMaintenance.deviceName}</p>
          <p><strong>Mô tả:</strong> {selectedMaintenance.description}</p>
          <p><strong>Người phụ trách:</strong> {selectedMaintenance.assignee}</p>
          <p><strong>Trạng thái:</strong> {selectedMaintenance.status}</p>
          <p><strong>Tạo lúc:</strong> {selectedMaintenance.createdAt}</p>

          {selectedMaintenance.resolutionNote && (
            <p>
              <strong>Kết quả xử lý:</strong>{' '}
              {selectedMaintenance.resolutionNote}
            </p>
          )}

          {can('maintenance.manage') &&
            selectedMaintenance.status !== 'Hoàn thành' && (
              <>
                <label htmlFor="resolutionNote">
                  Ghi chú xử lý
                </label>
                <textarea
                  id="resolutionNote"
                  value={resolutionNote}
                  onChange={(event) => setResolutionNote(event.target.value)}
                  rows={3}
                  placeholder="Ghi kết quả kiểm tra hoặc sửa chữa..."
                />

                <div className="form-actions">
                  {selectedMaintenance.status === 'Chờ xử lý' && (
                    <button
                      className="small-button"
                      onClick={() =>
                        updateMaintenanceTask(
                          selectedMaintenance,
                          'Đang xử lý',
                        )
                      }
                    >
                      Bắt đầu xử lý
                    </button>
                  )}

                  <button
                    className="primary-button"
                    onClick={() =>
                      updateMaintenanceTask(
                        selectedMaintenance,
                        'Hoàn thành',
                      )
                    }
                  >
                    Hoàn thành bảo trì
                  </button>
                </div>
              </>
            )}
        </section>
      )}
    </>
  )
  }

  
  const renderAudit = () => {
    const keyword = search.trim().toLowerCase()

  const filteredLogs = auditLogs.filter((log) =>
    [
      log.id,
      log.actor,
      log.action,
      log.target,
      log.detail,
    ].some((value) => value.toLowerCase().includes(keyword)),
  )

  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h3>Lịch sử hoạt động</h3>
          <p>Theo dõi các thao tác được ghi nhận trong phiên demo</p>
        </div>

        <span className="status-pill neutral">
          {auditLogs.length} bản ghi
        </span>
      </div>

      <div className="filter-bar">
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Tìm theo người thực hiện, hành động hoặc nội dung..."
        />
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Thời gian</th>
              <th>Người thực hiện</th>
              <th>Hành động</th>
              <th>Đối tượng</th>
              <th>Nội dung</th>
              <th>Loại</th>
            </tr>
          </thead>

          <tbody>
            {filteredLogs.map((log) => (
              <tr key={log.id}>
                <td>{log.time}</td>
                <td><strong>{log.actor}</strong></td>
                <td>
                  <span className="status-pill neutral">
                    {log.action}
                  </span>
                </td>
                <td>{log.target}</td>
                <td>{log.detail}</td>
                <td>
                  <span className={`status-pill ${
                    log.severity === 'success'
                      ? 'success'
                      : log.severity === 'warning'
                        ? 'warning'
                        : 'neutral'
                  }`}>
                    {log.severity === 'success'
                      ? 'Thành công'
                      : log.severity === 'warning'
                        ? 'Cảnh báo'
                        : 'Thông tin'}
                  </span>
                </td>
              </tr>
            ))}

            {filteredLogs.length === 0 && (
              <tr>
                <td colSpan={6}>
                  Không tìm thấy nhật ký phù hợp.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="panel-note">
        Nhật ký demo có thể bị mất khi tải lại trang. Nhật ký kiểm toán
        chính thức cần được backend ghi nhận và bảo vệ khỏi chỉnh sửa trái phép.
      </p>
    </section>
  )
  }


  const addAuditLog = (
    action: string,
    target: string,
    detail: string,
    severity: AuditLog['severity'] = 'info',
  ) => {
  const now = new Date()

  const time = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('-') + ' ' + now.toLocaleTimeString('vi-VN', {
    hour12: false,
  })

  const log: AuditLog = {
    id: `LOG-${Date.now()}`,
    time,
    actor: role,
    action,
    target,
    detail,
    severity,
  }

  setAuditLogs((current) => [log, ...current])
  }

  
  const createMaintenanceTask = (
    event: FormEvent<HTMLFormElement>,
    ) => {
    event.preventDefault()

  if (
    !maintenanceForm.deviceName ||
    !maintenanceForm.title.trim() ||
    !maintenanceForm.description.trim()
  ) {
    setNotice('Vui lòng nhập đầy đủ thông tin bảo trì.')
    return
  }

  const now = new Date().toLocaleString('sv-SE', {
    hour12: false,
  }).replace(',', '')

  const task: MaintenanceTask = {
    id: `MNT-${Date.now()}`,
    deviceName: maintenanceForm.deviceName,
    title: maintenanceForm.title.trim(),
    description: maintenanceForm.description.trim(),
    priority: maintenanceForm.priority,
    status: 'Chờ xử lý',
    assignee: maintenanceForm.assignee,
    createdAt: now,
    updatedAt: now,
    resolutionNote: '',
  }

  setMaintenanceTasks((current) => [task, ...current])

  addAuditLog(
    'CREATE',
    task.id,
    `Tạo phiếu bảo trì ${task.title} cho ${task.deviceName}.`,
    task.priority === 'Cao' ? 'warning' : 'info',
  )

  setShowMaintenanceForm(false)
  setMaintenanceForm({
    deviceName: '',
    title: '',
    description: '',
    priority: 'Trung bình',
    assignee: 'Technician',
  })

  setNotice(`Đã tạo phiếu bảo trì ${task.id}.`)
  }

  const updateMaintenanceTask = (
    task: MaintenanceTask,
    status: MaintenanceStatus,
  ) => {
  if (
    status === 'Hoàn thành' &&
    !resolutionNote.trim()
  ) {
    setSelectedMaintenance(task)
    setNotice('Vui lòng nhập ghi chú kết quả trước khi hoàn thành.')
    return
  }

  const now = new Date().toLocaleString('sv-SE', {
    hour12: false,
  }).replace(',', '')

  const note =
    status === 'Hoàn thành'
      ? resolutionNote.trim()
      : task.resolutionNote

  setMaintenanceTasks((current) =>
    current.map((item) =>
      item.id === task.id
        ? {
            ...item,
            status,
            updatedAt: now,
            resolutionNote: note,
          }
        : item,
    ),
  )

  addAuditLog(
    'UPDATE',
    task.id,
    `Cập nhật phiếu ${task.id}: ${task.status} → ${status}.` +
      (note ? ` Kết quả: ${note}` : ''),
    status === 'Hoàn thành' ? 'success' : 'info',
  )

  setSelectedMaintenance(null)
  setResolutionNote('')
  setNotice(`Đã cập nhật phiếu ${task.id}: ${status}.`)
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="brand-logo">S</div>
          <span className="brand-name">
            SCENT<span>OS</span>
          </span>
        </div>

        <nav className="sidebar-nav">
          {visibleNavigation.map((item) => (
            <button
              key={item.id}
              type="button"
              className={
                page === item.id
                  ? 'nav-item active'
                  : 'nav-item'
              }
              onClick={() => {
                setPage(item.id)
                setNotice('')
              }}
            >
              <span className="nav-icon">{item.icon}</span>
              <span>{item.label}</span>
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <label htmlFor="demo-role">Vai trò demo</label>
          <select
            id="demo-role"
            value={role}
            onChange={(event) => {
              const nextRole = event.target.value as Role
              setRole(nextRole)

              const allowedPages = navigation.filter(
                (item) => item.permission.includes(nextRole),
              )

              if (!allowedPages.some((item) => item.id === page)) {
                setPage('dashboard')
              }

              setNotice('')
            }}
          >
            {roles.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>

          <button
            type="button"
            className="secondary-button"
            onClick={logout}
          >
            Đăng xuất
          </button>
        </div>
      </aside>

      <div className="main-content">
        <header className="page-header">
          <div>
            <span className="eyebrow">SCENTOS ADMIN</span>
            <h1>{pageTitles[page]}</h1>
          </div>

          <div className="header-user">
            <span className="user-avatar">
              {email.charAt(0).toUpperCase()}
            </span>
            <div>
              <strong>{email}</strong>
              <small>{role}</small>
            </div>
          </div>
        </header>

        {notice && (
          <div className="notice-message" role="status">
            <span>{notice}</span>
            <button
              type="button"
              onClick={() => setNotice('')}
              aria-label="Đóng thông báo"
            >
              ×
            </button>
          </div>
        )}

        <main className="page-content">
          {page === 'dashboard' && renderDashboard()}
          {page === 'products' && renderProducts()}
          {page === 'campaigns' && renderCampaigns()}
          {page === 'inventory' && renderInventory()}
          {page === 'devices' && renderDevices()}
          {page === 'alerts' && renderAlerts()}
          {page === 'orders' && renderOrders()}
          {page === 'maintenance' && renderMaintenance()}
          {page === 'audit' && renderAudit()}
        </main>
      </div>

      {selectedDevice && (
        <div
          className="device-modal-overlay"
          onClick={closeDeviceDetail}
        >
          <section
            className="device-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="device-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="panel-heading">
              <div>
                <h3 id="device-modal-title">Chi tiết máy phun</h3>
                <p>{selectedDevice.name}</p>
              </div>

              <button
                type="button"
                className="secondary-button"
                onClick={closeDeviceDetail}
              >
                Đóng
              </button>
            </div>

            <div className="device-detail-grid">
              <div className="device-detail-item">
                <span>Tên thiết bị</span>
                <strong>{selectedDevice.name}</strong>
              </div>

              <div className="device-detail-item">
                <span>Sản phẩm</span>
                <strong>{selectedDevice.product}</strong>
              </div>

              <div className="device-detail-item">
                <span>Vị trí</span>
                <strong>{selectedDevice.location}</strong>
              </div>

              <div className="device-detail-item">
                <span>Trạng thái kết nối</span>
                <strong>{selectedDevice.status}</strong>
              </div>

              <div className="device-detail-item">
                <span>Lượng hàng ước tính</span>
                <strong>{selectedDevice.stock}%</strong>
              </div>
            </div>
          </section>
        </div>
      )}
    </div>
  )
}

export default App


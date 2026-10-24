
import { useState } from 'react'
import './App.css'

type Role =
  | 'Admin'
  | 'Operator'
  | 'Technician'
  | 'Inventory Manager'
  | 'Finance'

type Page =
  | 'dashboard'
  | 'products'
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

const devices = [
  {
    name: 'Đầu phun SP-001',
    product: 'Citrus No. 08',
    location: 'Tầng 1 · Sảnh chính',
    status: 'Online',
    stock: '82%',
  },
  {
    name: 'Đầu phun SP-002',
    product: 'Ocean Breeze',
    location: 'Tầng 2 · Khu mua sắm',
    status: 'Online',
    stock: '45%',
  },
  {
    name: 'Đầu phun SP-003',
    product: 'Rose Velvet',
    location: 'Tầng 3 · Khu dịch vụ',
    status: 'Offline',
    stock: '12%',
  },
  {
    name: 'Đầu phun SP-004',
    product: 'Woody Night',
    location: 'Tầng 3 · Khu dịch vụ',
    status: 'Online',
    stock: '0%',
  },
]


const transactions = [
  { id: 'ORD-1028', product: 'Citrus No. 08', time: '09:42', amount: '25.000 ₫', status: 'Thành công' },
  { id: 'ORD-1027', product: 'Ocean Breeze', time: '09:36', amount: '30.000 ₫', status: 'Thành công' },
  { id: 'ORD-1026', product: 'Rose Velvet', time: '09:21', amount: '35.000 ₫', status: 'Cần kiểm tra' },
  { id: 'ORD-1025', product: 'Woody Night', time: '09:10', amount: '40.000 ₫', status: 'Thất bại' },
]

const pageTitles: Record<Page, string> = {
  dashboard: 'Tổng quan hệ thống',
  products: 'Quản lý sản phẩm',
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
  const [showProductForm, setShowProductForm] = useState(false)
  const [editingProduct, setEditingProduct] = useState<string | null>(null)
  const [productForm, setProductForm] = useState<ProductForm>({
    name: '',
    category: '',
    price: '',
    stock: '0',})
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

  const login = (event: React.FormEvent<HTMLFormElement>) => {
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

  setNotice(
    `Đã ${
      adjustmentType === 'import' ? 'nhập thêm' : 'xuất'
    } ${quantity} đơn vị ${adjustingProduct}. Tồn kho mới: ${newStock}. Lý do: ${adjustmentReason.trim()}`,
  )

  closeInventoryAdjustment()
}

const handleAddProduct = (event: React.FormEvent<HTMLFormElement>) => {
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
        : stockValue < 10
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

  setShowProductForm(false)
  setNotice('Đã thêm sản phẩm thành công.')
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
            {devices.map((device) => (
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
              {transactions.map((transaction) => (
                <tr key={transaction.id}>
                  <td><strong>{transaction.id}</strong></td>
                  <td>{transaction.product}</td>
                  <td>{transaction.time}</td>
                  <td>{transaction.amount}</td>
                  <td>
                    <span className={`status-pill ${
                      transaction.status === 'Thành công'
                        ? 'success'
                        : transaction.status === 'Thất bại'
                          ? 'danger'
                          : 'warning'
                    }`}>
                      {transaction.status}
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
                <td><strong>{device.name}</strong></td>
                <td>{device.product}</td>
                <td>{device.location}</td>
                <td><span className={`status-pill ${device.status === 'Online' ? 'success' : 'danger'}`}>{device.status}</span></td>
                <td>{device.stock}</td>
                <td>
                  {can('device.diagnostic_spray') && device.status === 'Online' ? (
                    <button className="small-button" onClick={() => setNotice(`Đã yêu cầu mô phỏng phun thử trên ${device.name}. Chưa gửi lệnh tới thiết bị thật.`)}>Phun thử</button>
                  ) : <span className="muted">Chỉ xem</span>}
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
  </>
)


  const renderOrders = () => (
    <section className="panel">
      <div className="panel-heading">
        <div><h3>Đơn hàng & thanh toán</h3><p>Theo dõi giao dịch và kết quả cấp phát</p></div>
      </div>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Mã đơn</th><th>Sản phẩm</th><th>Thời gian</th><th>Thanh toán</th><th>Trạng thái xử lý</th><th>Thao tác</th></tr></thead>
          <tbody>
            {transactions.map((transaction) => (
              <tr key={transaction.id}>
                <td><strong>{transaction.id}</strong></td>
                <td>{transaction.product}</td>
                <td>{transaction.time}</td>
                <td>{transaction.amount}</td>
                <td><span className={`status-pill ${transaction.status === 'Thành công' ? 'success' : transaction.status === 'Thất bại' ? 'danger' : 'warning'}`}>{transaction.status}</span></td>
                <td>{transaction.status !== 'Thành công' && can('payment.refund') ? <button className="small-button" onClick={() => setNotice(`Đơn ${transaction.id} được đánh dấu cần xem xét hoàn tiền. Chưa có hoàn tiền thật.`)}>Xem xét</button> : <span className="muted">Chi tiết</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="panel-note">Hoàn tiền và kiểm tra kết quả phun cần tích hợp backend trước khi sử dụng thật.</p>
    </section>
  )

  const renderPlaceholder = () => {
    const content: Record<'alerts' | 'maintenance' | 'audit', { heading: string; description: string; rows: string[] }> = {
      alerts: {
        heading: 'Danh sách cảnh báo',
        description: 'Theo dõi cảnh báo tồn kho, mất kết nối và giao dịch bất thường.',
        rows: ['Máy phun SP-003 mất kết nối', 'Rose Velvet sắp hết hàng', 'ORD-1026 cần kiểm tra kết quả cấp phát'],
      },
      maintenance: {
        heading: 'Công việc bảo trì',
        description: 'Theo dõi phiếu bảo trì và lịch sử sửa chữa thiết bị.',
        rows: ['Kiểm tra kết nối SP-003', 'Kiểm tra đầu phun SP-002', 'Bảo trì định kỳ SP-001'],
      },
      audit: {
        heading: 'Nhật ký hoạt động',
        description: 'Theo dõi các hoạt động quan trọng của hệ thống.',
        rows: ['Đăng nhập quản trị', 'Cập nhật cấu hình thiết bị', 'Kiểm tra giao dịch cần xem xét'],
      },
    }

    const data = content[page as 'alerts' | 'maintenance' | 'audit']

    return (
      <section className="panel">
        <div className="panel-heading">
          <div><h3>{data.heading}</h3><p>{data.description}</p></div>
        </div>
        <div className="placeholder-list">
          {data.rows.map((item, index) => (
            <div className="placeholder-row" key={item}>
              <span className="placeholder-number">0{index + 1}</span>
              <span>{item}</span>
              <span className={`status-pill ${index === 0 ? 'warning' : 'neutral'}`}>{index === 0 ? 'Cần xử lý' : 'Đang theo dõi'}</span>
            </div>
          ))}
        </div>
        <p className="panel-note">Đây là nội dung minh họa. Chưa có API lưu trữ, phân công hoặc xác nhận xử lý.</p>
      </section>
    )
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="brand-logo">S</div>
          <div><span className="brand-name">SCENT<span>OS</span></span><small>ADMIN CONSOLE</small></div>
        </div>

        <div className="workspace-label">WORKSPACE</div>
        <div className="workspace-card">
          <div className="workspace-icon">S</div>
          <div><strong>SCENTOS Platform</strong><small>Hệ thống quản trị</small></div>
          <span>⌄</span>
        </div>

        <div className="nav-label">QUẢN LÝ</div>
        <nav className="sidebar-nav">
          {visibleNavigation.map((item) => (
            <button
              key={item.id}
              className={`nav-item ${page === item.id ? 'active' : ''}`}
              onClick={() => {
                setPage(item.id)
                setSearch('')
                setNotice('')
              }}
            >
              <span className="nav-icon">{item.icon}</span>
              <span>{item.label}</span>
              {item.id === 'alerts' && <span className="nav-count">5</span>}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="help-card">
            <span className="help-icon">?</span>
            <strong>Cần hỗ trợ?</strong>
            <p>Kiểm tra tài liệu hướng dẫn vận hành hệ thống.</p>
            <button onClick={() => setNotice('Tài liệu hướng dẫn sẽ được bổ sung sau.')}>Xem hướng dẫn →</button>
          </div>
          <div className="sidebar-user">
            <div className="user-avatar">AD</div>
            <div className="user-meta"><strong>Quản trị viên</strong><small>{role}</small></div>
            <button className="logout-icon" title="Đăng xuất" onClick={logout}>↪</button>
          </div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="breadcrumb">SCENTOS <span>/</span> <strong>{pageTitles[page]}</strong></div>
          <div className="topbar-actions">
            <span className="environment"><i className="dot online-dot" /> Demo</span>
            <button className="notification-button" title="Cảnh báo" onClick={() => setPage('alerts')}>
              ♧<span className="notification-dot" />
            </button>
            <div className="topbar-divider" />
            <div className="topbar-user">
              <div className="user-avatar">AD</div>
              <div><strong>Quản trị viên</strong><small>{role}</small></div>
            </div>
          </div>
        </header>

        <div className="page-content">
          <div className="page-heading">
            <div>
              <span className="eyebrow">OVERVIEW / MANAGEMENT</span>
              <h1>{pageTitles[page]}</h1>
              <p>Xin chào! Đây là tình hình hoạt động của hệ thống hôm nay.</p>
            </div>
            <div className="heading-actions">
              <label className="role-switcher">
                <span>Vai trò demo</span>
                <select
                  value={role}
                  onChange={(event) => {
                    const nextRole = event.target.value as Role
                    setRole(nextRole)
                    const firstAllowed = navigation.find((item) => item.permission.includes(nextRole))
                    if (firstAllowed) setPage(firstAllowed.id)
                    setNotice('')
                  }}
                >
                  {roles.map((item) => <option key={item} value={item}>{item}</option>)}
                </select>
              </label>
              <button className="secondary-button" onClick={() => setNotice('Dữ liệu minh họa không cần đồng bộ.')}>↻ Làm mới</button>
            </div>
          </div>

          {notice && (
            <div className="notice-banner">
              <span>ⓘ</span>
              <p>{notice}</p>
              <button onClick={() => setNotice('')} aria-label="Đóng thông báo">×</button>
            </div>
          )}

          {page === 'dashboard' && renderDashboard()}
          {page === 'products' && renderProducts()}
          {page === 'inventory' && renderInventory()}
          {page === 'devices' && renderDevices()}
          {page === 'orders' && renderOrders()}
          {(page === 'alerts' || page === 'maintenance' || page === 'audit') && renderPlaceholder()}

          <footer className="page-footer">
            <span>© 2026 SCENTOS · Admin Console</span>
            <span>Frontend prototype · Mock data</span>
          </footer>
        </div>
      </main>
    </div>
  )
}

export default App

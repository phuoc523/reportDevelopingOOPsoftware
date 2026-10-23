
import { useState } from 'react'
import './App.css'
import ProductPage from './components/ProductPage'

type NavItem = {
  label: string
  icon: string
}

const navGroups: { title: string; items: NavItem[] }[] = [
  {
    title: 'TỔNG QUAN',
    items: [
      { label: 'Dashboard', icon: '▦' },
    ],
  },
  {
    title: 'QUẢN LÝ',
    items: [
      { label: 'Sản phẩm', icon: '◇' },
      { label: 'Chiến dịch', icon: '✳' },
      { label: 'Tồn kho', icon: '▤' },
      { label: 'Máy bán hàng', icon: '▣' },
    ],
  },
  {
    title: 'GIAO DỊCH',
    items: [
      { label: 'Đơn hàng', icon: '▧' },
      { label: 'Thanh toán', icon: '↗' },
      { label: 'Báo cáo', icon: '▥' },
    ],
  },
]

const stats = [
  {
    label: 'Doanh thu hôm nay',
    value: '8.450.000 ₫',
    change: '+12,8%',
    note: 'so với hôm qua',
    icon: '↗',
    color: 'purple',
  },
  {
    label: 'Đơn hàng hôm nay',
    value: '124',
    change: '+8,2%',
    note: 'so với hôm qua',
    icon: '▧',
    color: 'blue',
  },
  {
    label: 'Máy đang hoạt động',
    value: '18/20',
    change: '90%',
    note: 'tổng số máy',
    icon: '▣',
    color: 'green',
  },
  {
    label: 'Cảnh báo cần xử lý',
    value: '3',
    change: 'Cần chú ý',
    note: '2 máy · 1 tồn kho',
    icon: '!',
    color: 'orange',
  },
]

const recentOrders = [
  { id: '#SS-10248', product: 'Rose Elegance', machine: 'HCM-001', time: '10:42', amount: '220.000 ₫', status: 'Hoàn thành' },
  { id: '#SS-10247', product: 'Ocean Breeze', machine: 'HCM-003', time: '10:38', amount: '190.000 ₫', status: 'Hoàn thành' },
  { id: '#SS-10246', product: 'Floral Bloom', machine: 'HCM-002', time: '10:31', amount: '150.000 ₫', status: 'Đang xử lý' },
  { id: '#SS-10245', product: 'Rose Elegance', machine: 'HCM-001', time: '10:24', amount: '220.000 ₫', status: 'Hoàn thành' },
]

const alerts = [
  { title: 'Máy HCM-007 mất kết nối', detail: 'Không nhận tín hiệu trong 12 phút', level: 'danger', time: '5 phút trước' },
  { title: 'Tồn kho thấp', detail: 'Floral Bloom · Máy HCM-003', level: 'warning', time: '18 phút trước' },
  { title: 'Cần bảo trì định kỳ', detail: 'Máy HCM-012', level: 'warning', time: '32 phút trước' },
]

const revenue = [34, 46, 39, 62, 48, 72, 58, 83, 67, 91, 76, 100]

function App() {
  const [activePage, setActivePage] = useState('Dashboard')
  const [menuOpen, setMenuOpen] = useState(false)
  const [search, setSearch] = useState('')

  function selectPage(page: string) {
    setActivePage(page)
    setMenuOpen(false)
  }

  return (
    <div className="admin-app">
      {menuOpen && (
        <button
          className="sidebar-overlay"
          aria-label="Đóng menu"
          onClick={() => setMenuOpen(false)}
        />
      )}

      <aside className={`sidebar ${menuOpen ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <div className="brand-mark">S</div>
          <div className="brand-copy">
            <strong>ScentStation</strong>
            <span>ADMIN CONSOLE</span>
          </div>
          <button
            className="icon-button sidebar-close"
            onClick={() => setMenuOpen(false)}
            aria-label="Đóng menu"
          >
            ×
          </button>
        </div>

        <div className="tenant-switch">
          <div className="tenant-avatar">S</div>
          <div className="tenant-copy">
            <strong>ScentStation Vietnam</strong>
            <span>Quản trị viên</span>
          </div>
          <span className="tenant-chevron">⌄</span>
        </div>

        <nav className="side-navigation" aria-label="Điều hướng chính">
          {navGroups.map((group) => (
            <div className="nav-group" key={group.title}>
              <p className="nav-heading">{group.title}</p>
              {group.items.map((item) => (
                <button
                  key={item.label}
                  className={`nav-item ${activePage === item.label ? 'nav-active' : ''}`}
                  onClick={() => selectPage(item.label)}
                >
                  <span className="nav-icon">{item.icon}</span>
                  <span>{item.label}</span>
                  {item.label === 'Máy bán hàng' && (
                    <span className="nav-count">20</span>
                  )}
                  {item.label === 'Đơn hàng' && (
                    <span className="nav-count">12</span>
                  )}
                </button>
              ))}
            </div>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="help-card">
            <div className="help-icon">?</div>
            <strong>Cần hỗ trợ?</strong>
            <p>Liên hệ đội ngũ vận hành khi bạn cần trợ giúp.</p>
            <button onClick={() => selectPage('Trung tâm hỗ trợ')}>
              Trung tâm hỗ trợ ↗
            </button>
          </div>
          <div className="sidebar-version">ScentStation Admin · v1.0.0</div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <button
            className="icon-button menu-toggle"
            onClick={() => setMenuOpen(true)}
            aria-label="Mở menu"
          >
            ☰
          </button>

          <div className="breadcrumbs">
            <span>Quản trị</span>
            <span className="breadcrumb-separator">/</span>
            <strong>{activePage}</strong>
          </div>

          <div className="topbar-actions">
            <label className="search-box">
              <span>⌕</span>
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Tìm kiếm..."
                aria-label="Tìm kiếm"
              />
              <kbd>⌘ K</kbd>
            </label>
            <button className="icon-button notification-button" aria-label="Thông báo">
              ♧
              <span className="notification-dot" />
            </button>
            <div className="topbar-divider" />
            <button className="profile-button" onClick={() => selectPage('Tài khoản')}>
              <div className="profile-avatar">AD</div>
              <div className="profile-copy">
                <strong>Admin User</strong>
                <span>Super Admin</span>
              </div>
              <span className="profile-chevron">⌄</span>
            </button>
          </div>
        </header>

        <div className="page-content">
          {activePage === 'Dashboard' ? (
            <>
              <section className="page-heading">
                <div>
                  <div className="eyebrow">THỨ BA, 20 THÁNG 10, 2026</div>
                  <h1>Tổng quan hoạt động</h1>
                  <p>Xin chào, Admin! Đây là tình hình hoạt động của hệ thống.</p>
                </div>
                <button
                  className="primary-button"
                  onClick={() => selectPage('Báo cáo')}
                >
                  <span>↗</span> Xem báo cáo
                </button>
              </section>

              <div className="demo-notice">
                <span>ⓘ</span>
                Đây là dữ liệu minh họa cho giao diện. Chưa kết nối API backend.
              </div>

              <section className="stats-grid" aria-label="Chỉ số tổng quan">
                {stats.map((stat) => (
                  <article className="stat-card" key={stat.label}>
                    <div className="stat-top">
                      <span>{stat.label}</span>
                      <div className={`stat-icon ${stat.color}`}>{stat.icon}</div>
                    </div>
                    <strong className="stat-value">{stat.value}</strong>
                    <div className="stat-footer">
                      <span className={`stat-change ${stat.color}`}>
                        {stat.change}
                      </span>
                      <span>{stat.note}</span>
                    </div>
                  </article>
                ))}
              </section>

              <section className="dashboard-grid">
                <article className="panel revenue-panel">
                  <div className="panel-heading">
                    <div>
                      <h2>Doanh thu</h2>
                      <p>Xu hướng doanh thu trong ngày</p>
                    </div>
                    <button className="select-button">Hôm nay⌄</button>
                  </div>
                  <div className="revenue-summary">
                    <strong>8.450.000 ₫</strong>
                    <span className="positive-change">↗ 12,8%</span>
                  </div>
                  <div className="chart-area">
                    <div className="chart-y-labels">
                      <span>10 triệu</span>
                      <span>7,5 triệu</span>
                      <span>5 triệu</span>
                      <span>2,5 triệu</span>
                      <span>0</span>
                    </div>
                    <div className="chart">
                      <div className="chart-grid-lines">
                        {[0, 1, 2, 3, 4].map((line) => (
                          <span key={line} />
                        ))}
                      </div>
                      <div className="bar-chart">
                        {revenue.map((height, index) => (
                          <div className="bar-column" key={index}>
                            <div
                              className={`bar ${index === 9 ? 'bar-highlight' : ''}`}
                              style={{ height: `${height}%` }}
                              title={`${height}%`}
                            />
                          </div>
                        ))}
                      </div>
                      <div className="chart-x-labels">
                        <span>08:00</span>
                        <span>10:00</span>
                        <span>12:00</span>
                        <span>14:00</span>
                        <span>16:00</span>
                        <span>18:00</span>
                        <span>20:00</span>
                      </div>
                    </div>
                  </div>
                </article>

                <article className="panel machine-panel">
                  <div className="panel-heading">
                    <div>
                      <h2>Trạng thái máy</h2>
                      <p>Tình trạng thiết bị trên hệ thống</p>
                    </div>
                    <button
                      className="text-button"
                      onClick={() => selectPage('Máy bán hàng')}
                    >
                      Xem tất cả →
                    </button>
                  </div>
                  <div className="machine-total">
                    <div className="machine-ring">
                      <div className="ring-inner">
                        <strong>90%</strong>
                        <span>Hoạt động</span>
                      </div>
                    </div>
                    <div className="machine-legend">
                      <div><span className="legend-dot online" /> <span>Đang hoạt động</span><strong>18</strong></div>
                      <div><span className="legend-dot offline" /> <span>Mất kết nối</span><strong>1</strong></div>
                      <div><span className="legend-dot maintenance" /> <span>Bảo trì</span><strong>1</strong></div>
                    </div>
                  </div>
                  <div className="machine-footer">
                    <span>20 máy trong hệ thống</span>
                    <span className="status-pill success">● 18 trực tuyến</span>
                  </div>
                </article>
              </section>

              <section className="bottom-grid">
                <article className="panel orders-panel">
                  <div className="panel-heading">
                    <div>
                      <h2>Đơn hàng gần đây</h2>
                      <p>Theo dõi các giao dịch mới nhất</p>
                    </div>
                    <button
                      className="text-button"
                      onClick={() => selectPage('Đơn hàng')}
                    >
                      Tất cả đơn hàng →
                    </button>
                  </div>
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>MÃ ĐƠN</th>
                          <th>SẢN PHẨM</th>
                          <th>MÁY</th>
                          <th>THỜI GIAN</th>
                          <th>GIÁ TRỊ</th>
                          <th>TRẠNG THÁI</th>
                        </tr>
                      </thead>
                      <tbody>
                        {recentOrders
                          .filter((order) =>
                            `${order.id} ${order.product} ${order.machine}`
                              .toLowerCase()
                              .includes(search.toLowerCase()),
                          )
                          .map((order) => (
                            <tr key={order.id}>
                              <td className="order-id">{order.id}</td>
                              <td>{order.product}</td>
                              <td>{order.machine}</td>
                              <td>{order.time}</td>
                              <td className="amount">{order.amount}</td>
                              <td>
                                <span className={`status-pill ${order.status === 'Hoàn thành' ? 'success' : 'processing'}`}>
                                  {order.status}
                                </span>
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                    {recentOrders.filter((order) =>
                      `${order.id} ${order.product} ${order.machine}`
                        .toLowerCase()
                        .includes(search.toLowerCase()),
                    ).length === 0 && (
                      <p className="empty-state">Không tìm thấy đơn hàng phù hợp.</p>
                    )}
                  </div>
                </article>

                <article className="panel alerts-panel">
                  <div className="panel-heading">
                    <div>
                      <h2>Cảnh báo hệ thống</h2>
                      <p>Các vấn đề cần chú ý</p>
                    </div>
                    <span className="alert-count">3</span>
                  </div>
                  <div className="alert-list">
                    {alerts.map((alert) => (
                      <div className="alert-item" key={alert.title}>
                        <div className={`alert-symbol ${alert.level}`}>
                          {alert.level === 'danger' ? '!' : '△'}
                        </div>
                        <div className="alert-copy">
                          <strong>{alert.title}</strong>
                          <p>{alert.detail}</p>
                          <span>{alert.time}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                  <button
                    className="all-alerts-button"
                    onClick={() => selectPage('Máy bán hàng')}
                  >
                    Xem tất cả cảnh báo →
                  </button>
                </article>
              </section>
            </>
          ) : activePage === 'Sản phẩm' ? (
          <ProductPage />
          ) : (
          <section className="placeholder-page panel">
              <div className="placeholder-icon">▦</div>
              <h1>{activePage}</h1>
              <p>
                Giao diện chức năng này sẽ được triển khai ở bước tiếp theo.
              </p>
              <button className="primary-button" onClick={() => selectPage('Dashboard')}>
                ← Quay lại Dashboard
              </button>
            </section>
          )}

          <footer className="page-footer">
            <span>© 2026 ScentStation. All rights reserved.</span>
            <span>Admin Console <i /> Hỗ trợ vận hành</span>
          </footer>
        </div>
      </main>
    </div>
  )
}

export default App


import { useEffect, useMemo, useState } from 'react'
import './ProductPage.css'

type Product = {
  id: number
  tenantId: number
  name: string
  description: string | null
  fragranceNotes: string | null
  imageUrl: string | null
  defaultPrice: number | string
  available: boolean
}

type AvailabilityFilter = 'all' | 'available' | 'unavailable'

const formatPrice = (price: number | string) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(Number(price))

function ProductPage() {
  const [products, setProducts] = useState<Product[]>([])
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState<AvailabilityFilter>('all')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function loadProducts() {
    try {
      setLoading(true)
      setError('')

      const response = await fetch('/api/products', {
        headers: { Accept: 'application/json' },
      })

      if (!response.ok) {
        throw new Error(`Không thể tải sản phẩm (HTTP ${response.status}).`)
      }

      const data: Product[] = await response.json()
      setProducts(data)
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Đã xảy ra lỗi khi tải danh sách sản phẩm.',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadProducts()
  }, [])

  const filteredProducts = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase('vi')

    return products.filter((product) => {
      const matchesSearch = [
        product.name,
        product.fragranceNotes ?? '',
        product.description ?? '',
      ]
        .join(' ')
        .toLocaleLowerCase('vi')
        .includes(keyword)

      const matchesAvailability =
        filter === 'all' ||
        (filter === 'available' && product.available) ||
        (filter === 'unavailable' && !product.available)

      return matchesSearch && matchesAvailability
    })
  }, [products, search, filter])

  const availableCount = products.filter((product) => product.available).length
  const unavailableCount = products.length - availableCount

  return (
    <section className="product-page">
      <div className="product-page-heading">
        <div>
          <span className="product-eyebrow">QUẢN LÝ DANH MỤC</span>
          <h1>Sản phẩm</h1>
          <p>Quản lý danh mục nước hoa và tình trạng kinh doanh.</p>
        </div>

        <button
          type="button"
          className="product-refresh-button"
          onClick={() => void loadProducts()}
          disabled={loading}
        >
          ↻ {loading ? 'Đang tải...' : 'Làm mới'}
        </button>
      </div>

      <div className="product-summary-grid">
        <article className="product-summary-card">
          <span>Tổng sản phẩm</span>
          <strong>{products.length}</strong>
          <small>Tất cả sản phẩm trong danh mục</small>
        </article>

        <article className="product-summary-card">
          <span>Đang kinh doanh</span>
          <strong className="product-number-available">{availableCount}</strong>
          <small>Sản phẩm có thể bán</small>
        </article>

        <article className="product-summary-card">
          <span>Hết hàng / không khả dụng</span>
          <strong className="product-number-unavailable">{unavailableCount}</strong>
          <small>Sản phẩm đang bị tắt khả dụng</small>
        </article>
      </div>

      <div className="product-list-panel">
        <div className="product-list-heading">
          <div>
            <h2>Danh sách sản phẩm</h2>
            <p>{filteredProducts.length} sản phẩm phù hợp</p>
          </div>
        </div>

        <div className="product-toolbar">
          <label className="product-search">
            <span>⌕</span>
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Tìm theo tên, mô tả hoặc nhóm hương..."
              aria-label="Tìm kiếm sản phẩm"
            />
          </label>

          <label className="product-filter">
            <span>Trạng thái</span>
            <select
              value={filter}
              onChange={(event) =>
                setFilter(event.target.value as AvailabilityFilter)
              }
              aria-label="Lọc theo trạng thái"
            >
              <option value="all">Tất cả</option>
              <option value="available">Còn hàng</option>
              <option value="unavailable">Hết hàng / không khả dụng</option>
            </select>
          </label>
        </div>

        {error && (
          <div className="product-error" role="alert">
            <span>{error}</span>
            <button type="button" onClick={() => void loadProducts()}>
              Thử lại
            </button>
          </div>
        )}

        {loading ? (
          <div className="product-empty-state">Đang tải danh sách sản phẩm...</div>
        ) : !error && filteredProducts.length === 0 ? (
          <div className="product-empty-state">
            Không tìm thấy sản phẩm phù hợp với bộ lọc hiện tại.
          </div>
        ) : !error ? (
          <div className="product-table-scroll">
            <table className="product-table">
              <thead>
                <tr>
                  <th>SẢN PHẨM</th>
                  <th>NHÓM HƯƠNG</th>
                  <th>GIÁ BÁN</th>
                  <th>TRẠNG THÁI</th>
                  <th>MÃ SẢN PHẨM</th>
                </tr>
              </thead>
              <tbody>
                {filteredProducts.map((product) => (
                  <tr key={product.id}>
                    <td>
                      <div className="product-name-cell">
                        {product.imageUrl ? (
                          <img
                            src={product.imageUrl}
                            alt=""
                            className="product-thumbnail"
                            onError={(event) => {
                              event.currentTarget.style.visibility = 'hidden'
                            }}
                          />
                        ) : (
                          <div className="product-thumbnail product-thumbnail-placeholder">
                            ◇
                          </div>
                        )}
                        <div>
                          <strong>{product.name}</strong>
                          <span>{product.description || 'Chưa có mô tả'}</span>
                        </div>
                      </div>
                    </td>
                    <td>{product.fragranceNotes || 'Chưa phân loại'}</td>
                    <td className="product-price">
                      {formatPrice(product.defaultPrice)}
                    </td>
                    <td>
                      <span
                        className={`product-status ${
                          product.available ? 'is-available' : 'is-unavailable'
                        }`}
                      >
                        <span />
                        {product.available ? 'Còn hàng' : 'Không khả dụng'}
                      </span>
                    </td>
                    <td className="product-id">#{product.id}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        <div className="product-list-footer">
          <span>Dữ liệu lấy trực tiếp từ Catalog Service</span>
          <span>{filteredProducts.length} / {products.length} sản phẩm</span>
        </div>
      </div>
    </section>
  )
}

export default ProductPage


import { useEffect, useState } from 'react'
import Header from './components/Header'
import FragranceCard from './components/FragranceCard'
import CheckoutBar from './components/CheckoutBar'
import type { Fragrance } from './types/fragrance'
import { apiGet } from './services/api'
import './App.css'

type ProductResponse = {
  id: number
  tenantId: number
  name: string
  description: string | null
  fragranceNotes: string | null
  imageUrl: string | null
  defaultPrice: number | string
  available: boolean
}

const colors = ['rose', 'amber', 'blue', 'plum']


function mapProduct(product: ProductResponse, index: number,): Fragrance | null {
  const price = Number(product.defaultPrice)

  if (
    !Number.isInteger(product.id) ||
    product.id <= 0 ||
    typeof product.name !== 'string' ||
    !product.name.trim() ||
    !Number.isFinite(price) ||
    price < 0 ||
    typeof product.available !== 'boolean'
  ) {
    console.warn('Bỏ qua sản phẩm có dữ liệu không hợp lệ:', product)
    return null
  }

  return {
    id: String(product.id),
    name: product.name.trim(),
    family: product.fragranceNotes || 'Hương thơm đặc trưng',
    description:
      product.description || 'Khám phá mùi hương dành riêng cho bạn.',
    price,
    color: colors[index % colors.length],
    available: product.available,
  }
}


function App() {
  const [fragrances, setFragrances] = useState<Fragrance[]>([])
  const [selected, setSelected] = useState<Fragrance | null>(null)
  const [showConfirmation, setShowConfirmation] = useState(false)
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false

    async function loadProducts() {
      try {
        setLoading(true)
        setError('')

        const products = await apiGet<ProductResponse[]>('/products')

        if (!cancelled) {
          if (!Array.isArray(products)) {
          throw new Error('API không trả về danh sách sản phẩm hợp lệ')
          }

        const mappedProducts = products
        .map(mapProduct)
        .filter((product): product is Fragrance => product !== null)

        setFragrances(mappedProducts)
        setSelected(null)
      }
      } catch {
        if (!cancelled) {
          setError('Không thể tải danh sách sản phẩm. Vui lòng kiểm tra Catalog Service.')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void loadProducts()

    return () => {
      cancelled = true
    }
  }, [])

  function handleSelect(fragrance: Fragrance) {
  setSelected(fragrance)
  setShowConfirmation(false)
  setNotice('')
}

async function handleContinue() {
  if (!selected) return

  try {
    setNotice('')
    setError('')

    // Kiểm tra lại trạng thái sản phẩm từ Catalog Service
    const products = await apiGet<ProductResponse[]>('/products')

    // Cập nhật danh sách sản phẩm mới nhất trên giao diện
    setFragrances(products.map(mapProduct).filter((product): product is Fragrance => product !== null))

    const latestProduct = products.find(
      (product) => String(product.id) === selected.id,
    )

    // Sản phẩm không còn tồn tại hoặc đã hết hàng
    if (!latestProduct || !latestProduct.available) {
      setSelected(null)
      setShowConfirmation(false)
      setNotice(
        'Sản phẩm đã hết hàng hoặc không còn khả dụng. Vui lòng chọn sản phẩm khác.',
      )
      return
    }

    // Cập nhật thông tin mới nhất của sản phẩm được chọn
    const latestFragrance = mapProduct(
      latestProduct,
      products.indexOf(latestProduct),
    )

    setSelected(latestFragrance)
    setShowConfirmation(true)
  } catch {
    setError(
      'Không thể kiểm tra tình trạng sản phẩm. Vui lòng thử lại.',
    )
  }
}

function handleConfirmSelection() {
  if (!selected || !selected.available) return

  setShowConfirmation(false)
  setNotice(
    `Bạn đã xác nhận lựa chọn "${selected.name}" với giá ${new Intl.NumberFormat(
      'vi-VN',
      {
        style: 'currency',
        currency: 'VND',
        maximumFractionDigits: 0,
      },
    ).format(selected.price)}. Đơn hàng chưa được tạo vì API Order Service chưa được tích hợp.`,
  )
}

  return (
    <main className="kiosk">
      <Header />

      <section className="hero">
        <div className="hero-copy">
          <span className="eyebrow">A LITTLE LUXURY, EVERY DAY</span>
          <h1>
            Tìm thấy
            <br />
            <em>mùi hương</em> của bạn.
          </h1>
          <p>
            Khám phá những nốt hương tinh tế và chọn mùi hương
            phù hợp với khoảnh khắc của riêng bạn.
          </p>
          <a className="hero-link" href="#fragrances">
            KHÁM PHÁ BỘ SƯU TẬP <span aria-hidden="true">↓</span>
          </a>
        </div>

        <div className="hero-art" aria-label="Minh họa chai nước hoa">
          <div className="art-orbit orbit-one" />
          <div className="art-orbit orbit-two" />
          <div className="art-glow" />
          <div className="perfume">
            <div className="perfume-cap" />
            <div className="perfume-neck" />
            <div className="perfume-bottle">
              <span className="bottle-label">
                <small>SCENT</small>
                <strong>No. 05</strong>
                <small>PARFUM</small>
              </span>
            </div>
          </div>
          <span className="art-caption">THE ART OF FRAGRANCE</span>
        </div>
      </section>

      <section className="collection" id="fragrances">
        <div className="section-heading">
          <div>
            <span className="eyebrow">CURATED FOR YOU</span>
            <h2>Khám phá mùi hương</h2>
          </div>
          <span className="collection-count">
            {String(fragrances.length).padStart(2, '0')} MÙI HƯƠNG
          </span>
        </div>

        {loading && <p role="status">Đang tải danh sách sản phẩm...</p>}

        {error && (
          <p className="notice" role="alert">
            {error}
          </p>
        )}

        {!loading && !error && fragrances.length === 0 && (
          <p>Hiện chưa có sản phẩm nào.</p>
        )}

        {!loading && !error && fragrances.length > 0 && (
          <div className="fragrance-grid">
            {fragrances.map((fragrance, index) => (
              <FragranceCard
                key={fragrance.id}
                fragrance={fragrance}
                index={index}
                selected={selected?.id === fragrance.id}
                onSelect={handleSelect}
              />
            ))}
          </div>
        )}

        <CheckoutBar
        selected={selected}
        onContinue={handleContinue}
        />

        {showConfirmation && selected && (
          <section
          className="confirmation-panel"
          aria-labelledby="confirmation-title"
          aria-live="polite"
        >
        <h3 id="confirmation-title">Xác nhận lựa chọn</h3>

        <p>
          Bạn đã chọn <strong>{selected.name}</strong>.
        </p>

        <p>
          Giá sản phẩm:{' '}
          <strong>
            {new Intl.NumberFormat('vi-VN', {
              style: 'currency',
              currency: 'VND',
              maximumFractionDigits: 0,
            }).format(selected.price)}
          </strong>
        </p>

        <p>Vui lòng kiểm tra thông tin trước khi tiếp tục.</p>

      <div className="confirmation-actions">
          <button
          type="button"
          onClick={() => setShowConfirmation(false)}
        >
          Quay lại
          </button>

        <button
          type="button"
          disabled={!selected.available}
          onClick={handleConfirmSelection}
        >
          Xác nhận lựa chọn
        </button>
      </div>
    </section>
  )}

        {notice && (
          <p className="notice" role="status">
            {notice}
          </p>
        )}
      </section>

      <footer className="footer">
        <span>SCENTSTATION © 2026</span>
        <span>FIND YOUR SIGNATURE</span>
        <span>MADE FOR YOUR MOMENT</span>
      </footer>
    </main>
  )
}

export default App

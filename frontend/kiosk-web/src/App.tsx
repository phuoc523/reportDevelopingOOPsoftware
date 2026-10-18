
import { useState } from 'react'
import Header from './components/Header'
import FragranceCard from './components/FragranceCard'
import CheckoutBar from './components/CheckoutBar'
import type { Fragrance } from './types/fragrance'
import './App.css'

const fragrances: Fragrance[] = [
  {
    id: 'floral-bloom',
    name: 'Floral Bloom',
    family: 'Hương hoa',
    description: 'Thanh lịch, nhẹ nhàng với cảm hứng từ những cánh hoa tươi.',
    price: 15000,
    color: 'rose',
    available: true,
  },
  {
    id: 'woody-amber',
    name: 'Woody Amber',
    family: 'Hương gỗ',
    description: 'Ấm áp, sâu lắng với sắc thái gỗ và hổ phách.',
    price: 18000,
    color: 'amber',
    available: true,
  },
  {
    id: 'ocean-breeze',
    name: 'Ocean Breeze',
    family: 'Hương tươi mát',
    description: 'Trong trẻo, sảng khoái như làn gió biển buổi sớm.',
    price: 15000,
    color: 'blue',
    available: true,
  },
  {
    id: 'velvet-rose',
    name: 'Velvet Rose',
    family: 'Hương hoa',
    description: 'Nét hoa hồng mềm mại dành cho những khoảnh khắc đặc biệt.',
    price: 20000,
    color: 'plum',
    available: false,
  },
]

function App() {
  const [selected, setSelected] = useState<Fragrance | null>(null)
  const [notice, setNotice] = useState('')

  function handleSelect(fragrance: Fragrance) {
    setSelected(fragrance)
    setNotice('')
  }

  function handleContinue() {
    if (!selected) return

    setNotice(
      `Bạn đã chọn ${selected.name}. Chức năng tạo đơn sẽ được tích hợp với backend sau.`,
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

        <CheckoutBar
          selected={selected}
          onContinue={handleContinue}
        />

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

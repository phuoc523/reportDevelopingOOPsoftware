
import type { Fragrance } from '../types/fragrance'
import '../App.css'

type Props = {
  fragrance: Fragrance
  index: number
  selected: boolean
  onSelect: (fragrance: Fragrance) => void
}

const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value)

function FragranceCard({
  fragrance,
  index,
  selected,
  onSelect,
}: Props) {
  return (
    <article
      className={`fragrance-card ${selected ? 'is-selected' : ''} ${
        !fragrance.available ? 'is-unavailable' : ''
      }`}
    >
      <button
        className="product-select"
        type="button"
        disabled={!fragrance.available}
        onClick={() => onSelect(fragrance)}
        aria-pressed={selected}
        aria-label={`Chọn ${fragrance.name}`}
      >
        <div className={`product-art ${fragrance.color}`}>
          <span className="product-number">
            {String(index + 1).padStart(2, '0')}
          </span>

          <div className="mini-bottle">
            <span className="mini-cap" />
            <span className="mini-glass">
              <span className="mini-label">S.</span>
            </span>
          </div>

          {!fragrance.available && (
            <span className="sold-out">TẠM HẾT HÀNG</span>
          )}

          {selected && (
            <span className="selected-check" aria-label="Đã chọn">
              ✓
            </span>
          )}
        </div>

        <div className="product-info">
          <span className="product-family">{fragrance.family}</span>
          <h3>{fragrance.name}</h3>
          <p>{fragrance.description}</p>

          <div className="product-bottom">
            <strong>{money(fragrance.price)}</strong>
            <span className="choose-label">
              {selected ? 'ĐÃ CHỌN' : 'CHỌN +'}
            </span>
          </div>
        </div>
      </button>
    </article>
  )
}

export default FragranceCard

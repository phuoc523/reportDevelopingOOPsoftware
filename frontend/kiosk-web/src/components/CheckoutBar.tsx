
import type { Fragrance } from '../types/fragrance'
import '../App.css'

type Props = {
  selected: Fragrance | null
  onContinue: () => void
}

const money = (value: number) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value)

function CheckoutBar({ selected, onContinue }: Props) {
  return (
    <div className="checkout-bar">
      <div className="selection-summary">
        <span className="summary-icon" aria-hidden="true">
          ✳
        </span>

        <div>
          <span className="summary-label">MÙI HƯƠNG CỦA BẠN</span>
          <strong>
            {selected ? selected.name : 'Chưa chọn sản phẩm'}
          </strong>
        </div>
      </div>

      <div className="checkout-action">
        {selected && (
          <strong className="checkout-price">
            {money(selected.price)}
          </strong>
        )}

        <button
          className="continue-button"
          type="button"
          disabled={!selected}
          onClick={onContinue}
        >
          TIẾP TỤC <span aria-hidden="true">→</span>
        </button>
      </div>
    </div>
  )
}

export default CheckoutBar

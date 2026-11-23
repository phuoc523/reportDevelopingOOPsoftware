import { useEffect, useMemo, useState } from 'react'

type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'EXPIRED'

type PaymentRecord = {
  reference: string
  orderId: number
  amount: number
  currency: string
  status: PaymentStatus
  qrPayload: string
  createdAt: string
  expiresAt: string
  paidAt: string | null
}

const liteProxy = import.meta.env.VITE_LITE_PROXY === '1'
const orderServiceUrl = liteProxy ? '' : (import.meta.env.VITE_ORDER_SERVICE_URL ?? 'http://localhost:8083').replace(/\/$/, '')
const callbackToken = import.meta.env.VITE_PAYMENT_CALLBACK_TOKEN ?? 'local-mock-callback-token'

function getReferenceFromUrl() {
  const pathname = window.location.pathname.replace(/\/$/, '')
  const parts = pathname.split('/').filter(Boolean)
  return decodeURIComponent(parts.at(-1) ?? '')
}

function formatMoney(amount: number, currency: string) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(amount)
}

export default function App() {
  const reference = useMemo(() => getReferenceFromUrl(), [])
  const [payment, setPayment] = useState<PaymentRecord | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState('')

  useEffect(() => {
    if (!reference) {
      setError('Thiếu mã thanh toán trong URL. Vui lòng quét lại mã QR.')
      setLoading(false)
      return
    }

    const fetchPayment = async () => {
      try {
        const response = await fetch(`${orderServiceUrl}/api/v1/payments/public/${encodeURIComponent(reference)}`)
        if (!response.ok) {
          throw new Error(`Không thể tải thông tin giao dịch (${response.status}).`)
        }
        const data: PaymentRecord = await response.json()
        setPayment(data)
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Không thể tải thông tin thanh toán.')
      } finally {
        setLoading(false)
      }
    }

    void fetchPayment()
  }, [reference])

  async function confirmPayment() {
    if (!payment) return
    setSubmitting(true)
    setError('')
    setResult('')

    try {
      const response = await fetch(`${orderServiceUrl}/api/v1/payments/public/${encodeURIComponent(reference)}/confirm`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(!liteProxy && { 'X-Payment-Callback-Token': callbackToken }),
        },
        body: JSON.stringify({
          eventId: `mock-${Date.now()}`,
          reference: payment.reference,
          orderId: payment.orderId,
          amount: payment.amount,
          currency: payment.currency,
          status: 'PAID',
        }),
      })

      const data = await response.json().catch(() => null)
      if (!response.ok) {
        const message = data && typeof data === 'object' && 'message' in data && typeof (data as { message?: string }).message === 'string'
          ? (data as { message: string }).message
          : 'Không thể xác nhận thanh toán.'
        throw new Error(message)
      }

      const updated = data as PaymentRecord
      setPayment(updated)
      setResult('Thanh toán thành công. Hệ thống đã xác nhận giao dịch và cập nhật đơn hàng.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể xác nhận thanh toán ngay lúc này.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <main className="shell"><section className="panel"><p className="eyebrow">SCENTSTATION</p><h1>Đang tải giao dịch…</h1><div className="spinner" /></section></main>

  if (!reference || !payment) {
    return <main className="shell"><section className="panel error-panel"><p className="eyebrow">SCENTSTATION</p><h1>Không tìm thấy giao dịch</h1>{error && <p>{error}</p>}</section></main>
  }

  return (
    <main className="shell">
      <section className="panel">
        <p className="eyebrow">SCENTSTATION · THANH TOÁN MÔ PHỎNG</p>
        <h1>Thanh toán đơn hàng</h1>

        <div className="summary-grid">
          <div className="box">
            <span>Mã tham chiếu</span>
            <strong>{payment.reference}</strong>
          </div>
          <div className="box">
            <span>Mã đơn hàng</span>
            <strong>#{payment.orderId}</strong>
          </div>
          <div className="box">
            <span>Tổng tiền</span>
            <strong>{formatMoney(payment.amount, payment.currency)}</strong>
          </div>
          <div className="box">
            <span>Trạng thái</span>
            <strong>{payment.status}</strong>
          </div>
        </div>

        <div className="details">
          <p><strong>Thời gian hết hạn:</strong> {new Date(payment.expiresAt).toLocaleString('vi-VN')}</p>
          <p><strong>Đơn vị tiền tệ:</strong> {payment.currency}</p>
        </div>

        {error && <div className="alert error">{error}</div>}
        {result && <div className="alert success">{result}</div>}

        {payment.status === 'PAID' && <div className="alert success">Giao dịch đã được xử lý.</div>}
        {payment.status === 'FAILED' && <div className="alert error">Thanh toán thất bại. Giao dịch không được chấp nhận.</div>}
        {payment.status === 'EXPIRED' && <div className="alert error">Giao dịch đã hết hạn. Vui lòng tạo đơn hàng mới.</div>}

        <button className="primary" disabled={submitting || payment.status !== 'PENDING'} onClick={() => void confirmPayment()}>
          {submitting ? 'Đang xác nhận...' : payment.status === 'PENDING' ? 'Xác nhận thanh toán' : 'Đã xử lý'}
        </button>
      </section>
    </main>
  )
}

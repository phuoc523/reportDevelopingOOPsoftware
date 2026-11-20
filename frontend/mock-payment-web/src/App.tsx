import { useEffect, useRef, useState } from 'react'
import type { Html5Qrcode as Html5QrcodeScanner } from 'html5-qrcode'
import { parsePaymentQrData } from './paymentQr'

type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'EXPIRED'

type PaymentRecord = {
  reference: string
  orderId: number
  amount: number
  currency: string
  status: PaymentStatus
  qrUrl: string
  qrPayload: string
  createdAt: string
  expiresAt: string
  paidAt: string | null
}

type OrderItem = {
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

type OrderRecord = {
  id: number
  machineId: string
  slotId: string
  items: OrderItem[]
  totalAmount: number
  status: string
  currency: string
  expiresAt: string
}

type PublicPaymentDetails = {
  payment: PaymentRecord
  order: OrderRecord
}

const scannerElementId = 'mock-payment-qr-reader'
const lanHost = window.location.hostname
const orderServiceUrl = (
  import.meta.env.VITE_ORDER_SERVICE_URL ||
  `${window.location.protocol}//${lanHost}:8080`
).replace(/\/$/, '')

function formatMoney(amount: number, currency: string) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(amount)
}

function createEventId(reference: string) {
  const bytes = new Uint8Array(16)
  window.crypto.getRandomValues(bytes)
  const nonce = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `mock-${reference}-${nonce}`
}

export default function App() {
  const [payment, setPayment] = useState<PaymentRecord | null>(null)
  const [order, setOrder] = useState<OrderRecord | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState('')
  const [scanning, setScanning] = useState(false)
  const scannerRef = useRef<Html5QrcodeScanner | null>(null)
  const scanHandledRef = useRef(false)
  const scannerStartRequestedRef = useRef(false)
  const paymentFlowVersionRef = useRef(0)

  async function stopScanner() {
    scannerStartRequestedRef.current = false
    const scanner = scannerRef.current
    if (!scanner) {
      setScanning(false)
      return
    }

    if (scanner.isScanning) await scanner.stop()
    scanner.clear()
    scannerRef.current = null
    setScanning(false)
  }

  useEffect(() => () => {
    const scanner = scannerRef.current
    if (!scanner) return
    if (scanner.isScanning) {
      void scanner.stop()
        .then(() => scanner.clear())
        .catch((cause: unknown) => console.error('Unable to stop QR camera on page exit.', cause))
    } else {
      scanner.clear()
    }
  }, [])

  async function loadPayment(requestedReference: string) {
    setLoading(true)
    setError('')
    setPayment(null)
    setOrder(null)

    try {
      const response = await fetch(`${orderServiceUrl}/api/v1/payments/public/lookup`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ paymentReference: requestedReference }),
      })
      if (!response.ok) {
        throw new Error(response.status === 404
          ? 'Không tìm thấy giao dịch tương ứng với mã QR.'
          : `Không thể tải thông tin giao dịch (${response.status}).`)
      }

      const data: PublicPaymentDetails = await response.json()
      if (data.payment.reference !== requestedReference) {
        throw new Error('Thông tin giao dịch trả về không khớp với mã QR.')
      }
      setPayment(data.payment)
      setOrder(data.order)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể tải thông tin thanh toán.')
    } finally {
      setLoading(false)
    }
  }

  async function handleQrPayload(raw: string) {
    try {
      const requestedReference = parsePaymentQrData(raw)
      await stopScanner()
      await loadPayment(requestedReference)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể đọc mã QR.')
    } finally {
      scanHandledRef.current = false
    }
  }

  async function startScanner() {
    setError('')

    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      setError('Trình duyệt chặn camera trên HTTP bằng địa chỉ IP LAN vì đây không phải Secure Context. Không thể quét QR tại địa chỉ hiện tại.')
      return
    }

    try {
      scannerStartRequestedRef.current = true
      setScanning(true)
      await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()))
      if (!scannerStartRequestedRef.current) return
      const { Html5Qrcode, Html5QrcodeSupportedFormats } = await import('html5-qrcode')
      const scanner = new Html5Qrcode(scannerElementId, {
        formatsToSupport: [Html5QrcodeSupportedFormats.QR_CODE],
        verbose: false,
      })
      scannerRef.current = scanner
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 240, height: 240 } },
        (decodedText) => {
          if (scanHandledRef.current) return
          scanHandledRef.current = true
          void handleQrPayload(decodedText)
        },
        () => undefined,
      )
      setScanning(true)
    } catch (cause) {
      const scanner = scannerRef.current
      if (scanner && !scanner.isScanning) scanner.clear()
      scannerRef.current = null
      scannerStartRequestedRef.current = false
      setScanning(false)
      setError(cause instanceof Error
        ? `Không thể mở camera trực tiếp: ${cause.message}`
        : 'Không thể mở camera trực tiếp.')
    }
  }

  async function confirmPayment() {
    if (!payment || payment.status !== 'PENDING') return
    const flowVersion = paymentFlowVersionRef.current
    setSubmitting(true)
    setError('')
    setResult('')

    try {
      const response = await fetch(`${orderServiceUrl}/api/v1/payments/public/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          eventId: createEventId(payment.reference),
          reference: payment.reference,
          orderId: payment.orderId,
          amount: payment.amount,
          currency: payment.currency,
          status: 'PAID',
        }),
      })

      const data: unknown = await response.json()
      if (flowVersion !== paymentFlowVersionRef.current) return
      if (!response.ok) {
        const message = data && typeof data === 'object' && 'message' in data && typeof data.message === 'string'
          ? data.message
          : 'Không thể xác nhận thanh toán.'
        throw new Error(message)
      }

      const updated = data as PaymentRecord
      setPayment(updated)
      setResult('Thanh toán thành công. Giao dịch đã được backend xác nhận.')
    } catch (cause) {
      if (flowVersion === paymentFlowVersionRef.current) {
        setError(cause instanceof Error ? cause.message : 'Không thể xác nhận thanh toán ngay lúc này.')
      }
    } finally {
      if (flowVersion === paymentFlowVersionRef.current) setSubmitting(false)
    }
  }

  function backToHome() {
    paymentFlowVersionRef.current += 1
    void stopScanner().catch((cause: unknown) => {
      setError(cause instanceof Error ? cause.message : 'Không thể dừng camera.')
    })
    setPayment(null)
    setOrder(null)
    setError('')
    setResult('')
    setSubmitting(false)
    scanHandledRef.current = false
  }

  if (loading) {
    return <main className="shell"><section className="panel"><p className="eyebrow">SCENTSTATION</p><h1>Đang tải giao dịch…</h1><div className="spinner" /></section></main>
  }

  if (!payment) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="eyebrow">SCENTSTATION · MOCK PAYMENT</p>
          <h1>Quét mã QR</h1>
          <p>Dùng camera để quét mã giao dịch đang hiển thị trên kiosk. Không cần nhập mã thanh toán.</p>
          {error && <div className="alert error" role="alert">{error}</div>}
          <div className="actions">
            <button type="button" onClick={() => void startScanner()} disabled={scanning}>QUÉT MÃ QR</button>
            {scanning && <button className="secondary" type="button" onClick={() => {
              void stopScanner().catch((cause: unknown) => setError(cause instanceof Error ? cause.message : 'Không thể dừng camera.'))
            }}>DỪNG QUÉT</button>}
          </div>
          <div id={scannerElementId} className={scanning ? 'qr-scanner' : 'qr-scanner qr-scanner-idle'} />
          <p className="camera-note">
            Camera trực tiếp cần Secure Context. HTTP IPv4 LAN hiện tại không đáp ứng điều kiện đó trên các trình duyệt hiện đại; khả năng camera thực tế chưa được kiểm thử trên thiết bị.
          </p>
        </section>
      </main>
    )
  }

  return (
    <main className="shell">
      <section className="panel">
        <p className="eyebrow">SCENTSTATION · MOCK PAYMENT</p>
        <h1>{payment.status === 'PAID' ? 'Thanh toán thành công' : 'Thanh toán đơn hàng'}</h1>

        <div className="summary-grid">
          <div className="box">
            <span>Tổng tiền</span>
            <strong>{formatMoney(payment.amount, payment.currency)}</strong>
          </div>
          <div className="box">
            <span>Trạng thái</span>
            <strong>{payment.status}</strong>
          </div>
        </div>

        {order && (
          <div className="order-details">
            <h2>Chi tiết đơn hàng</h2>
            <p><strong>Mã đơn:</strong> #{order.id}</p>
            <p><strong>Máy:</strong> {order.machineId}</p>
            <p><strong>Vị trí khay:</strong> {order.slotId}</p>
            <p><strong>Trạng thái đơn:</strong> {order.status}</p>
            {order.items.map((item) => (
              <div className="order-item" key={`${item.productId}-${item.productName}`}>
                <div>
                  <strong>{item.productName}</strong>
                  <span>Số lượng: {item.quantity} · Đơn giá: {formatMoney(item.unitPrice, payment.currency)}</span>
                </div>
                <strong>{formatMoney(item.lineTotal, payment.currency)}</strong>
              </div>
            ))}
            <div className="order-total">
              <span>Tổng thanh toán</span>
              <strong>{formatMoney(order.totalAmount, order.currency || payment.currency)}</strong>
            </div>
          </div>
        )}

        <div className="details">
          <p><strong>Thời gian hết hạn:</strong> {new Date(payment.expiresAt).toLocaleString('vi-VN')}</p>
          <p><strong>Đơn vị tiền tệ:</strong> {payment.currency}</p>
        </div>

        {error && <div className="alert error" role="alert">{error}</div>}
        {result && <div className="alert success" role="status">{result}</div>}
        {payment.status === 'PAID' && !result && <div className="alert success">Giao dịch đã được xử lý.</div>}
        {payment.status === 'FAILED' && <div className="alert error">Thanh toán thất bại. Giao dịch không được chấp nhận.</div>}
        {payment.status === 'EXPIRED' && <div className="alert error">Giao dịch đã hết hạn. Vui lòng liên hệ kiosk để được hỗ trợ.</div>}

        {payment.status === 'PENDING' ? (
          <div className="actions">
            <button type="button" disabled={submitting} onClick={() => void confirmPayment()}>
              {submitting ? 'Đang xác nhận...' : 'THANH TOÁN'}
            </button>
            <button className="secondary" type="button" onClick={backToHome}>QUAY LẠI</button>
          </div>
        ) : (
          <button type="button" onClick={backToHome}>QUAY LẠI TRANG CHÍNH</button>
        )}
      </section>
    </main>
  )
}

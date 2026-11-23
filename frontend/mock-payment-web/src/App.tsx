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
const orderServiceUrl = (
  import.meta.env.VITE_ORDER_SERVICE_URL ||
  ''
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
  const [scannerStatus, setScannerStatus] = useState<'idle' | 'starting' | 'scanning' | 'identified' | 'lookup'>('idle')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState('')
  const [scanning, setScanning] = useState(false)
  const scannerRef = useRef<Html5QrcodeScanner | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const scanHandledRef = useRef(false)
  const scannerStartRequestedRef = useRef(false)
  const lastScannedPayloadRef = useRef('')
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
    setScannerStatus((status) => status === 'scanning' ? 'idle' : status)
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
    setScannerStatus('lookup')
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
      setScannerStatus('idle')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể tải thông tin thanh toán.')
      setScannerStatus('idle')
    }
  }

  async function handleQrPayload(raw: string) {
    if (scanHandledRef.current || lastScannedPayloadRef.current === raw) return
    scanHandledRef.current = true
    lastScannedPayloadRef.current = raw
    setScannerStatus('identified')

    try {
      const requestedReference = parsePaymentQrData(raw)
      await stopScanner()
      setScannerStatus('lookup')
      await loadPayment(requestedReference)
    } catch (cause) {
      scanHandledRef.current = false
      setScannerStatus('idle')
      setError(cause instanceof Error ? cause.message : 'Không thể đọc mã QR.')
    }
  }

  async function startScanner() {
    if (scannerStartRequestedRef.current || scannerRef.current) return
    scanHandledRef.current = false
    lastScannedPayloadRef.current = ''
    setError('')

    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      setScannerStatus('idle')
      setError('Camera trực tiếp cần HTTPS hoặc localhost. Trang đang chạy trên HTTP LAN nên một số điện thoại chặn getUserMedia. Dùng nút chụp QR bằng camera hệ thống hoặc mở trang trên HTTPS.')
      return
    }

    try {
      scannerStartRequestedRef.current = true
      setScanning(true)
      setScannerStatus('starting')
      await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()))
      if (!scannerStartRequestedRef.current) return
      const { Html5Qrcode, Html5QrcodeSupportedFormats } = await import('html5-qrcode')
      const cameras = await Html5Qrcode.getCameras()
      if (!cameras.length) throw new DOMException('No camera found', 'NotFoundError')
      const cameraId = cameras.find((camera) =>
        /back|rear|environment|sau|ngoài/i.test(`${camera.label} ${camera.id}`)
      )?.id || cameras[0].id
      if (!scannerStartRequestedRef.current) return
      const scanner = new Html5Qrcode(scannerElementId, {
        formatsToSupport: [Html5QrcodeSupportedFormats.QR_CODE],
        verbose: false,
      })
      scannerRef.current = scanner
      await scanner.start(
        cameraId ? { deviceId: { exact: cameraId } } : { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 240, height: 240 } },
        (decodedText) => {
          void handleQrPayload(decodedText)
        },
        () => undefined,
      )
      setScanning(true)
      setScannerStatus('scanning')
    } catch (cause) {
      const scanner = scannerRef.current
      if (scanner && !scanner.isScanning) scanner.clear()
      scannerRef.current = null
      scannerStartRequestedRef.current = false
      setScanning(false)
      setScannerStatus('idle')
      const name = cause instanceof DOMException ? cause.name : cause instanceof Error ? cause.name : ''
      const message = name === 'NotAllowedError' || name === 'PermissionDeniedError'
        ? 'Bạn đã từ chối hoặc chưa cấp quyền camera. Hãy cho phép Camera trong trình duyệt rồi nhấn thử lại.'
        : name === 'NotFoundError'
          ? 'Không tìm thấy camera phù hợp trên thiết bị.'
          : name === 'NotReadableError'
            ? 'Camera đang được ứng dụng khác sử dụng. Hãy đóng ứng dụng khác rồi thử lại.'
            : name === 'SecurityError'
              ? 'Trình duyệt chặn camera trên môi trường HTTP LAN. Hãy dùng HTTPS hoặc localhost.'
              : cause instanceof Error
                ? `Không thể mở camera: ${cause.message}`
                : 'Không thể mở camera trực tiếp.'
      setError(`${message} Có thể thử chụp QR bằng camera hệ thống.`)
    }
  }

  async function scanCapturedImage(file: File | undefined) {
    if (!file) return
    setError('')
    setScannerStatus('identified')

    try {
      const { Html5Qrcode, Html5QrcodeSupportedFormats } = await import('html5-qrcode')
      const imageScanner = new Html5Qrcode(scannerElementId, {
        formatsToSupport: [Html5QrcodeSupportedFormats.QR_CODE],
        verbose: false,
      })
      let decodedText: string
      try {
        decodedText = await imageScanner.scanFile(file, false)
      } finally {
        await imageScanner.clear()
      }
      await handleQrPayload(decodedText)
    } catch (cause) {
      setScannerStatus('idle')
      scanHandledRef.current = false
      lastScannedPayloadRef.current = ''
      const message = cause instanceof Error
        ? cause.message
        : 'Không xác định được mã QR.'
      if (/not found|no qrcode|qr code not found|notfoundexception|no multi.?format readers/i.test(message)) {
        setError('Ảnh không chứa mã QR rõ ràng hoặc ảnh bị mờ. Hãy chụp lại gần và đủ sáng.')
      } else {
        setError(`Không đọc được mã QR trong ảnh: ${message}`)
      }
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
    lastScannedPayloadRef.current = ''
    setScannerStatus('idle')
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
          {scannerStatus === 'starting' && <div className="alert" role="status">Đang yêu cầu quyền camera…</div>}
          {scannerStatus === 'scanning' && <div className="alert" role="status">Đang quét mã QR đơn hàng…</div>}
          {scannerStatus === 'identified' && <div className="alert" role="status">Đã nhận diện QR. Đang kiểm tra dữ liệu…</div>}
          {scannerStatus === 'lookup' && <div className="alert" role="status">Đã nhận diện QR. Đang tra cứu đơn hàng…</div>}
          <div className="actions">
            <button type="button" onClick={() => void startScanner()} disabled={scanning}>QUÉT MÃ QR</button>
            <button className="secondary" type="button" onClick={() => fileInputRef.current?.click()} disabled={scanning}>
              CHỤP QR BẰNG CAMERA HỆ THỐNG
            </button>
            {scanning && <button className="secondary" type="button" onClick={() => {
              void stopScanner().catch((cause: unknown) => setError(cause instanceof Error ? cause.message : 'Không thể dừng camera.'))
            }}>DỪNG QUÉT</button>}
          </div>
          <input
            ref={fileInputRef}
            className="camera-input"
            type="file"
            accept="image/*"
            capture="environment"
            aria-label="Chụp ảnh mã QR thanh toán"
            onChange={(event) => {
              const file = event.currentTarget.files?.[0]
              event.currentTarget.value = ''
              void scanCapturedImage(file)
            }}
          />
          <div id={scannerElementId} className={scanning ? 'qr-scanner' : 'qr-scanner qr-scanner-idle'} />
          <p className="camera-note">
            {window.isSecureContext
              ? 'Trang đang chạy trong secure context. Nhấn QUÉT MÃ QR và cấp quyền camera khi trình duyệt hỏi; camera sau sẽ được ưu tiên.'
              : 'Trên HTTP bằng địa chỉ IP LAN, trình duyệt thường chặn camera trực tiếp (getUserMedia). Hãy mở trang bằng HTTPS; nút chụp ảnh chỉ là phương án dự phòng.'}
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

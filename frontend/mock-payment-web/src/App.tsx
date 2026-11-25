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

type TrialConfirmationSession = {
  sessionId: string
  status: 'PENDING' | 'CONFIRMED' | 'DECLINED' | 'EXPIRED'
  confirmUrl: string
  machineId: string
  slotId: string
  productId: number
  productName: string
  unitPrice: number
  currency: string
  expiresAt: string
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

function getInitialConfirmSessionId() {
  return new URLSearchParams(window.location.search).get('confirmSession')?.trim() || ''
}

export default function App() {
  const [confirmSessionId, setConfirmSessionId] = useState<string>(() => getInitialConfirmSessionId())
  const [confirmSession, setConfirmSession] = useState<TrialConfirmationSession | null>(null)
  const [confirmLoading, setConfirmLoading] = useState<boolean>(() => Boolean(getInitialConfirmSessionId()))
  const [confirmResponding, setConfirmResponding] = useState(false)
  const [confirmDeclined, setConfirmDeclined] = useState(false)

  const [payment, setPayment] = useState<PaymentRecord | null>(null)
  const [order, setOrder] = useState<OrderRecord | null>(null)
  const [scannerStatus, setScannerStatus] = useState<'idle' | 'starting' | 'scanning' | 'identified' | 'lookup'>('idle')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState('')
  const [scanning, setScanning] = useState(false)
  const scannerRef = useRef<Html5QrcodeScanner | null>(null)
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

  useEffect(() => {
    if (!confirmSessionId) return
    let active = true
    setConfirmLoading(true)
    setError('')
    void fetch(`${orderServiceUrl}/api/v1/payments/public/trial-confirmations/${encodeURIComponent(confirmSessionId)}`)
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(response.status === 404
            ? 'Phiên xác nhận dùng thử không tồn tại hoặc đã hết hạn.'
            : `Không thể tải phiên xác nhận (${response.status}).`)
        }
        return response.json() as Promise<TrialConfirmationSession>
      })
      .then((data) => {
        if (!active) return
        setConfirmSession(data)
        if (data.status === 'DECLINED') setConfirmDeclined(true)
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : 'Không thể tải thông tin xác nhận.')
      })
      .finally(() => {
        if (active) setConfirmLoading(false)
      })
    return () => { active = false }
  }, [confirmSessionId])

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
      setError('Camera quét trực tiếp trong trình duyệt cần HTTPS (hoặc localhost). Hãy mở trang bằng HTTPS (cổng 3443) để trình duyệt cho phép mở camera trực tiếp.')
      return
    }

    try {
      scannerStartRequestedRef.current = true
      setScanning(true)
      setScannerStatus('starting')
      await new Promise<void>((resolve) => window.requestAnimationFrame(() => window.requestAnimationFrame(() => resolve())))
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
              ? 'Trình duyệt chặn camera trên HTTP LAN. Hãy mở trang bằng HTTPS.'
              : cause instanceof Error
                ? `Không thể mở camera: ${cause.message}`
                : 'Không thể mở camera trực tiếp.'
      setError(message)
    }
  }

  async function respondTrialConfirmation(accepted: boolean) {
    if (!confirmSessionId || confirmResponding) return
    setConfirmResponding(true)
    setError('')
    try {
      const response = await fetch(
        `${orderServiceUrl}/api/v1/payments/public/trial-confirmations/${encodeURIComponent(confirmSessionId)}/respond`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ accepted }),
        },
      )
      if (!response.ok) {
        throw new Error(response.status === 409
          ? 'Phiên xác nhận đã hết hạn. Vui lòng quét lại mã trên kiosk.'
          : `Không thể gửi xác nhận (${response.status}).`)
      }
      const updated: TrialConfirmationSession = await response.json()
      setConfirmSession(updated)
      if (!accepted) {
        setConfirmDeclined(true)
        return
      }
      window.history.replaceState({}, '', window.location.pathname)
      setConfirmSessionId('')
      setConfirmSession(null)
      void startScanner()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Không thể gửi phản hồi xác nhận.')
    } finally {
      setConfirmResponding(false)
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
      setResult('Thanh toán thành công! Hệ thống đang gửi tín hiệu phun tới máy Kiosk.')
    } catch (cause) {
      if (flowVersion === paymentFlowVersionRef.current) {
        setError(cause instanceof Error ? cause.message : 'Không thể xác nhận thanh toán ngay lúc này.')
      }
    } finally {
      if (flowVersion === paymentFlowVersionRef.current) setSubmitting(false)
    }
  }

  function backToScanner() {
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

  if (confirmSessionId) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="eyebrow">SCENTSTATION · MOCK PAYMENT</p>
          <h1>Bạn có xác nhận dùng thử nước hoa?</h1>
          {confirmLoading && <div className="spinner" />}
          {confirmSession && (
            <div className="order-details">
              <div className="order-item">
                <div>
                  <strong>{confirmSession.productName}</strong>
                  <span>Kiosk #{confirmSession.machineId} · Đầu phun #{confirmSession.slotId}</span>
                </div>
                <strong>{formatMoney(confirmSession.unitPrice, confirmSession.currency || 'VND')}</strong>
              </div>
            </div>
          )}
          {error && <div className="alert error" role="alert">{error}</div>}
          {confirmDeclined ? (
            <div className="alert" role="status">
              Bạn đã chọn <strong>Không</strong>. Lượt dùng thử nước hoa đã được hủy trên màn hình Kiosk.
            </div>
          ) : confirmSession?.status === 'EXPIRED' ? (
            <div className="alert error" role="alert">
              Phiên xác nhận đã hết hạn. Vui lòng chọn lại mùi hương trên màn hình Kiosk.
            </div>
          ) : (
            <div className="actions">
              <button type="button" disabled={confirmLoading || confirmResponding} onClick={() => void respondTrialConfirmation(true)}>
                {confirmResponding ? 'Đang xử lý…' : 'CÓ'}
              </button>
              <button className="secondary" type="button" disabled={confirmLoading || confirmResponding} onClick={() => void respondTrialConfirmation(false)}>
                KHÔNG
              </button>
            </div>
          )}
          <p className="camera-note">
            Khi bạn chọn <strong>Có</strong>, màn hình Kiosk sẽ tự chuyển sang mã QR đơn hàng và điện thoại sẽ mở trực tiếp camera quét QR.
          </p>
        </section>
      </main>
    )
  }

  if (!payment) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="eyebrow">SCENTSTATION · MOCK PAYMENT</p>
          <h1>Quét mã QR đơn hàng</h1>
          <p>Đưa camera hướng vào mã QR đơn hàng đang hiển thị trên màn hình Kiosk.</p>
          {error && <div className="alert error" role="alert">{error}</div>}
          {scannerStatus === 'starting' && <div className="alert" role="status">Đang mở camera trực tiếp…</div>}
          {scannerStatus === 'scanning' && <div className="alert" role="status">Camera đang hoạt động — hãy đưa vào mã QR đơn hàng…</div>}
          {scannerStatus === 'identified' && <div className="alert" role="status">Đã nhận diện QR. Đang kiểm tra dữ liệu…</div>}
          {scannerStatus === 'lookup' && <div className="alert" role="status">Đã nhận diện QR. Đang tra cứu thông tin đơn hàng…</div>}
          <div id={scannerElementId} className={scanning ? 'qr-scanner' : 'qr-scanner qr-scanner-idle'} />
          <div className="actions">
            {!scanning ? (
              <button type="button" onClick={() => void startScanner()}>MỞ CAMERA QUÉT QR</button>
            ) : (
              <button className="secondary" type="button" onClick={() => {
                void stopScanner().catch((cause: unknown) => setError(cause instanceof Error ? cause.message : 'Không thể dừng camera.'))
              }}>DỪNG QUÉT</button>
            )}
          </div>
          <p className="camera-note">
            {window.isSecureContext
              ? 'Camera web đang dùng trực tiếp trong trình duyệt (ưu tiên camera sau).'
              : 'Lưu ý: trình duyệt điện thoại yêu cầu HTTPS (cổng 3443) để mở camera trực tiếp trong trang web.'}
          </p>
        </section>
      </main>
    )
  }

  return (
    <main className="shell">
      <section className="panel">
        <p className="eyebrow">SCENTSTATION · MOCK PAYMENT</p>
        <h1>{payment.status === 'PAID' ? 'Thanh toán thành công' : 'Xác nhận thanh toán đơn hàng'}</h1>

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
            <h2>Chi tiết đơn hàng #{order.id}</h2>
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
          <p><strong>Mã giao dịch:</strong> {payment.reference}</p>
          <p><strong>Thời gian hết hạn:</strong> {new Date(payment.expiresAt).toLocaleString('vi-VN')}</p>
        </div>

        {error && <div className="alert error" role="alert">{error}</div>}
        {result && <div className="alert success" role="status">{result}</div>}
        {payment.status === 'PAID' && !result && <div className="alert success">Giao dịch đã được xử lý và gửi tín hiệu phun.</div>}
        {payment.status === 'FAILED' && <div className="alert error">Thanh toán thất bại. Giao dịch không được chấp nhận.</div>}
        {payment.status === 'EXPIRED' && <div className="alert error">Giao dịch đã hết hạn. Vui lòng thực hiện lại trên kiosk.</div>}

        {payment.status === 'PENDING' ? (
          <div className="actions">
            <button type="button" disabled={submitting} onClick={() => void confirmPayment()}>
              {submitting ? 'Đang gửi xác nhận...' : 'XÁC NHẬN THANH TOÁN & PHUN'}
            </button>
            <button className="secondary" type="button" onClick={backToScanner}>QUÉT LẠI QR</button>
          </div>
        ) : (
          <button type="button" onClick={backToScanner}>QUÉT MÃ QR MỚI</button>
        )}
      </section>
    </main>
  )
}

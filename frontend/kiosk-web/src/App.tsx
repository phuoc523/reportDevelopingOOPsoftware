import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import './App.css'
import { ApiError, authToken, clearKioskCache, kioskApi } from './services/api'
import type { DispensingResponse, KioskItem, Machine, OrderResponse, PaymentResponse, Principal } from './types/api'
import { availabilityText, dispensingStatusText, formatCountdown } from './utils/kioskHelpers.ts'

type Screen = 'catalog' | 'confirm' | 'payment' | 'dispensing' | 'result' | 'purchase'
const configuredMachineId = Number(import.meta.env.VITE_KIOSK_MACHINE_ID || '9101')
const money = (value: number, currency = 'VND') => new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(value)
const readableError = (error: unknown) => error instanceof Error ? error.message : 'Không thể hoàn thành yêu cầu. Vui lòng thử lại.'

function isPaymentQrData(qrPayload: string, reference: string) {
  try {
    const data: unknown = JSON.parse(qrPayload)
    if (!data || typeof data !== 'object' || Array.isArray(data)) return false
    const payload = data as Record<string, unknown>
    return Object.keys(payload).length === 2
      && payload.type === 'SCENTSTATION_PAYMENT'
      && payload.paymentReference === reference
  } catch {
    return false
  }
}

function getPaymentQrUrl(value: string) {
  try {
    const url = new URL(value)
    const octets = url.hostname.split('.').map(Number)
    const isIpv4 = octets.length === 4 && octets.every((octet) => Number.isInteger(octet) && octet >= 0 && octet <= 255)
    const isHttpLan = url.protocol === 'http:' && (url.port === '3000' || url.port === '') && isIpv4
      && octets[0] !== 0 && octets[0] !== 127 && !(octets[0] === 169 && octets[1] === 254)
    const isHttpsOrigin = url.protocol === 'https:' && (url.port === '' || url.port === '3443')
      && (isIpv4 || url.hostname.includes('.'))
    if (!isHttpLan && !isHttpsOrigin) return null
    if (url.pathname !== '/' || url.search || url.hash || url.username || url.password) return null
    return url.href
  } catch {
    return null
  }
}

function App() {
  const [principal, setPrincipal] = useState<Principal | null>(null)
  const [machine, setMachine] = useState<Machine | null>(null)
  const [items, setItems] = useState<KioskItem[]>([])
  const [selected, setSelected] = useState<KioskItem | null>(null)
  const [screen, setScreen] = useState<Screen>('catalog')
  const [order, setOrder] = useState<OrderResponse | null>(null)
  const [payment, setPayment] = useState<PaymentResponse | null>(null)
  const [dispensing, setDispensing] = useState<DispensingResponse | null>(null)
  const [transactionType, setTransactionType] = useState<'TRIAL' | 'BOTTLE'>('TRIAL')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [backendOffline, setBackendOffline] = useState(false)
  const [nowMs, setNowMs] = useState(() => Date.now())
  const [resultCountdown, setResultCountdown] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const creatingRef = useRef(false)
  const paymentReference = payment?.reference
  const paymentStatus = payment?.status
  const paymentQrUrl = payment ? getPaymentQrUrl(payment.qrUrl) : null
  const paymentQrDataValid = payment ? isPaymentQrData(payment.qrPayload, payment.reference) : false

  const machineReady = Boolean(!backendOffline
    && machine?.deviceRegistrationStatus === 'ACTIVE'
    && machine.operationalStatus === 'ONLINE'
    && machine.maintenanceChecklistComplete
    && machine.postMaintenanceTestPassed)

  const loadCatalog = useCallback(async (actor: Principal) => {
    if (!Number.isInteger(configuredMachineId) || configuredMachineId <= 0) throw new Error('VITE_KIOSK_MACHINE_ID phải là mã máy hợp lệ.')
    if (!actor.tenantId) throw new Error('Tài khoản khách chưa được gán thương hiệu (tenant).')
    if (!actor.grants.includes('KIOSK_ORDER')) throw new Error('Tài khoản chưa có quyền tạo đơn tại Kiosk.')
    const [machineData, slotData, productData] = await Promise.all([
      kioskApi.machine(configuredMachineId), kioskApi.slots(configuredMachineId), kioskApi.products(actor.tenantId),
    ])
    if (Number(machineData.tenantId) !== actor.tenantId) throw new Error('Máy Kiosk không thuộc thương hiệu của tài khoản đang đăng nhập.')
    const products = new Map(productData.map((product) => [String(product.id), product]))
    const linked = slotData.flatMap((slot) => {
      const product = slot.productId ? products.get(slot.productId) : undefined
      return product ? [{ slot, product }] : []
    })
    const kioskItems = await Promise.all(linked.map(async ({ slot, product }) => ({
      slot, product, pricing: await kioskApi.pricing(actor.tenantId as number, product.id),
    })))
    setMachine(machineData); setItems(kioskItems); setBackendOffline(false)
    setSelected((current) => kioskItems.find((item) => item.slot.id === current?.slot.id) || null)
  }, [])

  useEffect(() => {
    const onUnauthorized = (event: Event) => {
      const detail = (event as CustomEvent<string>).detail
      setPrincipal(null); setMachine(null); setItems([]); setSelected(null)
      setOrder(null); setPayment(null); setDispensing(null); setScreen('catalog')
      setError(detail || 'Phiên đăng nhập Kiosk đã hết hạn hoặc tài khoản bị khóa. Vui lòng đăng nhập lại.')
    }
    window.addEventListener('scentstation:kiosk-unauthorized', onUnauthorized)
    return () => window.removeEventListener('scentstation:kiosk-unauthorized', onUnauthorized)
  }, [])

  useEffect(() => {
    let active = true
    async function restore() {
      if (!authToken.get()) { setLoading(false); return }
      try {
        const actor = await kioskApi.me()
        if (!active) return
        setPrincipal(actor); await loadCatalog(actor)
      } catch (reason) {
        if (reason instanceof ApiError && (reason.status === 401 || reason.status === 403)) authToken.clear()
        if (active) setError(readableError(reason))
      } finally { if (active) setLoading(false) }
    }
    void restore()
    return () => { active = false }
  }, [loadCatalog])

  useEffect(() => {
    if (screen !== 'payment' || !paymentReference || paymentStatus !== 'PENDING') return
    let active = true
    let polling = false
    const poll = async () => {
      if (polling) return
      polling = true
      try {
        const current = await kioskApi.payment(paymentReference)
        if (!active) return
        setPayment(current)
        if (current.status === 'PAID') {
          setError('')
          setScreen(order?.orderType === 'BOTTLE' ? 'result' : 'dispensing')
        }
        else if (current.status === 'FAILED') { setError('Thanh toán thất bại. Hệ thống không gửi lệnh xịt.'); setScreen('result') }
        else if (current.status === 'EXPIRED') { setError('Giao dịch đã hết hạn. Hệ thống không gửi lệnh xịt.'); setScreen('result') }
      } catch (reason) { if (active) setError(`Mất kết nối khi kiểm tra thanh toán: ${readableError(reason)}`) }
      finally { polling = false }
    }
    void poll()
    const timer = window.setInterval(() => void poll(), 3000)
    return () => { active = false; window.clearInterval(timer) }
  }, [order?.orderType, paymentReference, paymentStatus, screen])

  useEffect(() => {
    if (screen !== 'dispensing' || !order) return
    let active = true
    let attempts = 0
    const poll = async () => {
      attempts += 1
      try {
        const current = await kioskApi.dispensing(order.id)
        if (!active) return
        setDispensing(current); setError('')
        if (['DISPENSED', 'FAILED', 'UNKNOWN'].includes(current.status)) setScreen('result')
      } catch (reason) {
        if (!active) return
        if (reason instanceof ApiError && reason.status === 404 && attempts < 5) return
        setError(`Không thể nhận kết quả xịt: ${readableError(reason)}`)
        if (attempts >= 10) setScreen('result')
      }
    }
    void poll()
    const timer = window.setInterval(() => void poll(), 2500)
    return () => { active = false; window.clearInterval(timer) }
  }, [order, screen])

  const availableCount = useMemo(() => items.filter(({ slot, product }) => machineReady && product.available && slot.available && slot.enabled && slot.status === 'AVAILABLE').length, [items, machineReady])
  const resumablePayment = Boolean(payment?.status === 'PENDING' && order && selected && String(order.slotId) === String(selected.slot.id))

  async function submitLogin(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      await kioskApi.login(username.trim(), password)
      const actor = await kioskApi.me()
      setPrincipal(actor); await loadCatalog(actor); setPassword('')
    } catch (reason) {
      authToken.clear(); setPrincipal(null); setError(readableError(reason))
    } finally { setBusy(false); setLoading(false) }
  }

  async function refresh() {
    if (!principal) return
    clearKioskCache()
    setBusy(true); setError('')
    try { await loadCatalog(principal) }
    catch (reason) {
      if (reason instanceof ApiError && (reason.status === 0 || reason.status === 408)) setBackendOffline(true)
      setError(readableError(reason))
    } finally { setBusy(false) }
  }

  async function recheckOrderStatus() {
    if (!order || busy) return
    setBusy(true); setError('')
    try {
      const latestOrder = await kioskApi.order(order.id)
      setOrder(latestOrder)
      if (latestOrder.orderType !== 'BOTTLE') {
        try {
          const latestDispensing = await kioskApi.dispensing(order.id)
          setDispensing(latestDispensing)
        } catch (dispenseReason) {
          if (!(dispenseReason instanceof ApiError && dispenseReason.status === 404)) {
            setError(`Không thể truy vấn kết quả xịt: ${readableError(dispenseReason)}`)
          }
        }
      }
      if (payment?.reference) {
        try { setPayment(await kioskApi.payment(payment.reference)) } catch { /* giữ payment hiện tại */ }
      }
    } catch (reason) {
      setError(`Không thể kiểm tra lại trạng thái đơn hàng: ${readableError(reason)}`)
    } finally { setBusy(false) }
  }

  async function startPayment(type: 'TRIAL' | 'BOTTLE' = transactionType) {
    if (!principal || !selected || creatingRef.current) return
    if (backendOffline) {
      setError('Không thể tạo đơn mới khi Kiosk đang mất kết nối với hệ thống. Vui lòng thử làm mới.')
      return
    }
    if (type === 'TRIAL' && !machineReady) {
      setError('Máy hiện không sẵn sàng để phục vụ lượt xịt. Không thể tạo đơn mới.')
      return
    }
    if (resumablePayment && payment) {
      setBusy(true)
      setError('')
      try {
        const currentPayment = await kioskApi.payment(payment.reference)
        setPayment(currentPayment)
        if (currentPayment.status === 'PAID') {
          setScreen(order?.orderType === 'BOTTLE' ? 'result' : 'dispensing')
        } else if (currentPayment.status === 'FAILED' || currentPayment.status === 'EXPIRED') {
          setError(currentPayment.status === 'EXPIRED'
            ? 'Giao dịch đã hết hạn. Vui lòng tạo đơn mới.'
            : 'Thanh toán thất bại. Vui lòng tạo đơn mới.')
          setScreen('result')
        } else {
          setScreen('payment')
        }
      } catch (reason) {
        setError(readableError(reason))
      } finally {
        setBusy(false)
      }
      return
    }
    creatingRef.current = true; setBusy(true); setError('')
    setTransactionType(type)
    try {
      const reusableOrder = order
        && ['PENDING', 'CONFIRMED'].includes(order.status)
        && String(order.machineId) === String(selected.slot.machineId)
        && String(order.slotId) === String(selected.slot.id)
        && order.items.length === 1
        && order.items[0].productId === selected.product.id
        && order.orderType === type
        ? order
        : null
      const currentOrder = reusableOrder ? await kioskApi.order(reusableOrder.id) : null
      const created = currentOrder && ['PENDING', 'CONFIRMED'].includes(currentOrder.status)
        ? currentOrder
        : await kioskApi.createOrder({
          customerId: principal.id, tenantId: String(principal.tenantId), machineId: String(selected.slot.machineId), slotId: String(selected.slot.id),
          orderType: type,
          items: [{ productId: selected.product.id, productName: selected.product.name, unitPrice: type === 'BOTTLE' ? Number(selected.product.bottlePrice) : selected.pricing.effectivePrice, quantity: 1 }],
        })
      setOrder(created)
      const qr = await kioskApi.createPayment(created.id)
      setPayment(qr)
      if (qr.status === 'PAID') { setError(''); setScreen(type === 'BOTTLE' ? 'result' : 'dispensing') }
      else if (qr.status === 'FAILED') { setError('Thanh toán thất bại. Hệ thống không gửi lệnh xịt.'); setScreen('result') }
      else if (qr.status === 'EXPIRED') { setError('Giao dịch đã hết hạn. Hệ thống không gửi lệnh xịt.'); setScreen('result') }
      else setScreen('payment')
    } catch (reason) {
      setError(readableError(reason)); await refresh()
    } finally { creatingRef.current = false; setBusy(false) }
  }

  const resetTransaction = useCallback(() => {
    clearKioskCache()
    setSelected(null); setOrder(null); setPayment(null); setDispensing(null); setTransactionType('TRIAL'); setError(''); setResultCountdown(null); setScreen('catalog')
    if (principal) {
      setBusy(true)
      void loadCatalog(principal).catch((reason) => {
        if (reason instanceof ApiError && (reason.status === 0 || reason.status === 408)) setBackendOffline(true)
        setError(readableError(reason))
      }).finally(() => setBusy(false))
    }
  }, [loadCatalog, principal])

  const qrCountdownActive = Boolean(screen === 'payment' && payment?.expiresAt && payment.status === 'PENDING')
  const qrRemainingSeconds = qrCountdownActive && payment?.expiresAt
    ? Math.max(0, Math.ceil((Date.parse(payment.expiresAt) - nowMs) / 1000))
    : null

  useEffect(() => {
    if (!qrCountdownActive) return
    const timer = window.setInterval(() => setNowMs(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [qrCountdownActive])

  const autoResetAllowed = screen === 'result' && (
    dispensing?.status === 'DISPENSED'
    || (order?.orderType === 'BOTTLE' && payment?.status === 'PAID')
    || payment?.status === 'FAILED'
    || payment?.status === 'EXPIRED'
  )

  useEffect(() => {
    if (!autoResetAllowed) return
    let seconds = 30
    const timer = window.setInterval(() => {
      seconds -= 1
      if (seconds <= 0) {
        window.clearInterval(timer)
        resetTransaction()
      } else {
        setResultCountdown(seconds)
      }
    }, 1000)
    return () => {
      window.clearInterval(timer)
      setResultCountdown(null)
    }
  }, [autoResetAllowed, resetTransaction])

  const displayResultCountdown = autoResetAllowed ? (resultCountdown ?? 30) : null

  async function logout() {
    setBusy(true)
    try { await kioskApi.logout() } catch { authToken.clear() }
    setPrincipal(null); setMachine(null); setItems([]); setSelected(null); setOrder(null); setPayment(null); setDispensing(null); setError(''); setScreen('catalog'); setBusy(false)
  }

  if (loading) return <main className="center-screen"><div className="spinner"/><p>Đang kết nối ScentStation…</p></main>

  if (!principal) return <main className="login-screen"><section className="login-card">
    <div className="brand-emblem">S</div><p className="eyebrow">SCENTSTATION · KIOSK</p><h1>Bắt đầu trải nghiệm hương thơm</h1><p>Đăng nhập tài khoản khách được cấp cho máy này để tiếp tục.</p>
    <form onSubmit={submitLogin}><label>Tên đăng nhập<input autoFocus autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} required /></label><label>Mật khẩu<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>{error && <div className="error-box" role="alert">{error}</div>}<button className="primary" disabled={busy}>{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</button></form><small>Máy cấu hình: #{configuredMachineId}</small>
  </section></main>

  return <main className="kiosk-shell">
    <header className="topbar"><div className="brand-lockup"><span>S</span><div><strong>SCENTSTATION</strong><small>HƯƠNG THƠM CỦA RIÊNG BẠN</small></div></div><div className={`machine-pill ${machineReady ? 'online' : 'offline'}`}><i />{machine?.name || `Máy #${configuredMachineId}`} · {machineReady ? 'Sẵn sàng' : 'Ngoại tuyến'}</div><button className="logout-button" type="button" onClick={() => void logout()} disabled={busy}><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M10 17l5-5-5-5m5 5H3m9-7h6a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-6" /></svg><span>Đăng xuất</span></button></header>

    {screen === 'catalog' && <><section className="hero"><div><p className="eyebrow">BỘ SƯU TẬP TẠI MÁY #{configuredMachineId}</p><h1>Chọn mùi hương bạn muốn trải nghiệm</h1><p>1. Chọn mùi hương → 2. Quét mã QR thanh toán → 3. Đưa cổ tay vào đầu phun nhận hương.</p></div><button className="secondary" onClick={() => void refresh()} disabled={busy}>{busy ? 'Đang tải…' : 'Làm mới'}</button></section>
      {error && <div className="error-banner" role="alert">{error}</div>}{!machineReady && <div className="warning-banner">Máy hiện không sẵn sàng. Không thể tạo đơn hoặc thanh toán cho đến khi máy trực tuyến và hoàn tất kiểm tra kỹ thuật.</div>}<div className="catalog-meta"><strong>{items.length} đầu phun</strong><span>{availableCount} mùi đang sẵn sàng</span></div>
      <section className="product-grid">{items.map((item, index) => {
        const ready = Boolean(machineReady && item.product.available && item.slot.available && item.slot.enabled && item.slot.status === 'AVAILABLE')
        return <article className={`product-card ${ready ? '' : 'unavailable'}`} key={item.slot.id}>
          <div className="product-image">
            {item.product.imageUrl ? <img src={item.product.imageUrl} alt={item.product.name} /> : <div className="bottle-art">S.</div>}
            <span className="slot-number">Đầu phun {index + 1}</span>
            <span className="dose-badge">1 lượt · {item.slot.doseMl ?? 0.12} ml</span>
          </div>
          <div className="product-copy">
            <p className="eyebrow product-code">{item.slot.slotCode}</p>
            <h2 className="product-name" title={item.product.name}>{item.product.name}</h2>
            <p className="product-description">{item.product.fragranceNotes || item.product.description || 'Thông tin hương thơm đang được cập nhật.'}</p>
            {item.pricing.promotionApplied && <div className="promotion">{item.pricing.campaignName || 'Ưu đãi đang áp dụng'}</div>}
            {!ready && <div className="availability-note" role="status">{availabilityText(item.slot, Boolean(machineReady), item.product, machine)}</div>}
            <div className="card-footer">
              <div className="card-price">
                <small>Phí trải nghiệm</small>
                <span className="card-price-values">
                  <strong>{money(item.pricing.effectivePrice)}</strong>
                  {item.pricing.promotionApplied && <del>{money(item.pricing.defaultPrice)}</del>}
                </span>
              </div>
              <div className="card-buttons">
                <button className="primary" disabled={!ready} title={!ready ? availabilityText(item.slot, Boolean(machineReady), item.product, machine) : 'Bắt đầu lượt thử hương'} onClick={() => { setSelected(item); setTransactionType('TRIAL'); setError(''); setScreen('confirm') }}>{ready ? 'Thử hương' : 'Chưa thể thử'}</button>
                <button className="secondary compact" disabled={!item.product.bottlePrice || item.product.bottleStock <= 0} onClick={() => { setSelected(item); setTransactionType('BOTTLE'); setError(''); setScreen('purchase') }}>{item.product.bottleStock > 0 ? 'Mua chai' : 'Hết chai'}</button>
              </div>
            </div>
          </div>
        </article>
      })}{!items.length && <div className="empty-state"><h2>Chưa có sản phẩm tại máy này</h2><p>Backend không trả về slot nào có sản phẩm hợp lệ. Kiosk không dùng dữ liệu thay thế.</p></div>}</section>
    </>}

    {screen === 'confirm' && selected && <section className="flow-card">
      <p className="step">BƯỚC 1 / 3 · XÁC NHẬN LƯỢNG THỬ</p>
      <h1 className="flow-title">Xác nhận lượt trải nghiệm</h1>
      <div className="flow-product-summary">
        {selected.product.imageUrl ? <img src={selected.product.imageUrl} alt={selected.product.name} /> : <div className="flow-product-placeholder">S.</div>}
        <div className="flow-product-copy">
          <p className="eyebrow">{selected.slot.slotCode}</p>
          <h2>{selected.product.name}</h2>
          {selected.product.fragranceNotes && <p><strong>Nhóm hương:</strong> {selected.product.fragranceNotes}</p>}
          <p>{selected.product.description || (selected.product.fragranceNotes ? '' : 'Thông tin hương thơm đang được cập nhật.')}</p>
        </div>
      </div>
      <div className="sample-dose"><span className="sample-icon">✦</span><div><small>Lượng thử đã hiệu chuẩn</small><strong>1 lượt xịt · {selected.slot.doseMl ?? 0.12} ml</strong><p>Đây là lượng cố định an toàn được cấu hình trực tiếp tại đầu phun.</p></div><span className="selected-mark">Đã chọn</span></div>
      <div className="summary-grid"><div><span>Máy</span><strong>{machine?.name}</strong></div><div><span>Đầu phun</span><strong>{selected.slot.slotCode}</strong></div><div><span>Thời lượng hiệu chuẩn</span><strong>{selected.slot.doseMl ? 'Theo cấu hình đầu phun' : 'Theo cấu hình máy'}</strong></div><div><span>Phí trải nghiệm</span><strong>{money(selected.pricing.effectivePrice)}</strong></div></div>
      <p className="flow-note">Giá, tenant, trạng thái máy, đầu phun và lượng xịt đều được backend kiểm tra lại. Kiosk không tự thay đổi liều lượng.</p>
      {resumablePayment && <div className="pending-payment-note">Mã QR đã được tạo và vẫn còn hiệu lực. Tiếp tục để mở lại đúng giao dịch, không tạo thêm đơn mới.</div>}
      {error && <div className="error-box" role="alert">{error}</div>}
      <div className="flow-actions"><button className="secondary" onClick={() => setScreen('catalog')} disabled={busy}>Quay lại</button><button className="primary large" onClick={() => void startPayment('TRIAL')} disabled={busy}>{busy ? 'Đang tạo đơn…' : resumablePayment ? 'Tiếp tục thanh toán' : 'Thanh toán lượt thử'}</button></div>
    </section>}

    {screen === 'payment' && payment && <section className="flow-card payment-card">
      <p className="step">BƯỚC 2 / 3 · THANH TOÁN</p>
      <h1 className="flow-title">Quét mã để thanh toán</h1>
      <div className="flow-product-summary payment-product-summary">
        {selected?.product.imageUrl ? <img src={selected.product.imageUrl} alt={selected.product.name} /> : <div className="flow-product-placeholder">S.</div>}
        <div className="flow-product-copy">
          <p className="eyebrow">{selected?.slot.slotCode || 'ĐƠN HÀNG'}</p>
          <h2>{order?.items[0]?.productName || selected?.product.name || `Đơn hàng #${payment.orderId}`}</h2>
          <p>Đơn hàng #{payment.orderId}</p>
        </div>
      </div>
      {paymentQrUrl && paymentQrDataValid ? <div className="qr-grid">
        <article className="qr-card">
          <h2>1. Mở Mock Payment trên điện thoại</h2>
          <div className="qr-wrap" aria-label="QR URL trang chủ Mock Payment">
            <QRCodeSVG value={paymentQrUrl} size={220} level="M" title="Mở trang chủ Mock Payment" />
          </div>
          <a className="payment-root-url" href={paymentQrUrl}>{paymentQrUrl}</a>
        </article>
        <article className="qr-card">
          <h2>2. Dùng nút Quét mã QR trên điện thoại</h2>
          <div className="qr-wrap" aria-label="QR dữ liệu thanh toán riêng của đơn hàng">
            <QRCodeSVG value={payment.qrPayload} size={220} level="M" title="QR dữ liệu thanh toán của đơn hàng" />
            <span className="pulse-dot" aria-hidden="true"/>
          </div>
          <p>Giữ trang thanh toán mở và đưa camera điện thoại vào mã này.</p>
        </article>
      </div> : <p className="error-box" role="alert">Dữ liệu URL hoặc QR thanh toán không hợp lệ. Vui lòng tải lại giao dịch.</p>}
      <strong className="payment-amount">{money(payment.amount, payment.currency)}</strong>
      <div className="payment-details">
        <p><span>Mã giao dịch</span><code>{payment.reference}</code></p>
        <p><span>Hết hạn lúc</span><strong>{new Date(payment.expiresAt).toLocaleTimeString('vi-VN')}{qrRemainingSeconds !== null ? ` (còn ${formatCountdown(qrRemainingSeconds)})` : ''}</strong></p>
      </div>
      <small className="payment-hint">QR thứ nhất mở trang Mock Payment. QR thứ hai chỉ chứa dữ liệu định danh riêng của giao dịch.</small>
      <div className={`payment-status status-${payment.status.toLowerCase()}`} role="status">
        {payment.status === 'PENDING' && <div className="spinner"/>}
        <span>{payment.status === 'PENDING' ? 'Đang chờ backend xác nhận thanh toán…' : payment.status === 'PAID' ? 'Thanh toán thành công' : payment.status === 'FAILED' ? 'Thanh toán thất bại' : 'Giao dịch đã hết hạn'}</span>
      </div>
      {error && <div className="error-box" role="alert">{error}</div>}
      <div className="flow-actions payment-actions">
        <button className="secondary large" onClick={() => { setError(''); setScreen(order?.orderType === 'BOTTLE' ? 'purchase' : 'confirm') }}>← Quay lại xác nhận</button>
        {payment.status === 'PENDING' && <button className="secondary large" onClick={resetTransaction}>Hủy & chọn mùi khác</button>}
      </div>
      <small className="payment-hint">Mã QR vẫn có hiệu lực đến thời gian hết hạn. Khi quay lại, Kiosk sẽ dừng kiểm tra tạm thời và không tạo thêm đơn mới.</small>
    </section>}

    {screen === 'dispensing' && <section className="flow-card dispensing-card"><p className="step">BƯỚC 3 / 3 · XỊT NƯỚC HOA</p><div className="spray-animation">✦</div><h1>Thanh toán đã được xác nhận</h1><p>Backend đang gửi lệnh hợp lệ đến {selected?.slot.slotCode}. Vui lòng đưa cổ tay đến vị trí đầu phun.</p><div className="waiting"><div className="spinner"/>{dispensingStatusText(dispensing?.status)}</div>{error && <div className="error-box">{error}</div>}</section>}

    {screen === 'result' && order?.orderType === 'BOTTLE' && payment?.status === 'PAID' && <section className="flow-card result-card success"><div className="result-icon">✓</div><p className="step">ĐẶT CHAI THÀNH CÔNG</p><h1>Đơn mua chai đã được xác nhận</h1><p>Thanh toán đã hoàn tất và tồn kho chai đã được backend cập nhật. Vui lòng nhận sản phẩm tại quầy hỗ trợ của kiosk.</p><div className="receipt"><span>Đơn hàng #{order.id}</span><span>{payment.reference}</span><strong>{money(order.totalAmount, order.currency)}</strong></div>{displayResultCountdown !== null && <small className="payment-hint">Tự động quay về màn hình chính sau {displayResultCountdown} giây.</small>}<div className="flow-actions"><button className="primary large" onClick={resetTransaction}>Tiếp tục khám phá</button></div></section>}

    {screen === 'result' && order?.orderType === 'BOTTLE' && payment?.status !== 'PAID' && <section className="flow-card result-card failure"><div className="result-icon">!</div><p className="step">KẾT QUẢ ĐƠN MUA CHAI</p><h1>{payment?.status === 'EXPIRED' ? 'Giao dịch đã hết hạn' : 'Thanh toán chưa thành công'}</h1><p>{error || 'Tồn kho chai chưa bị trừ. Bạn có thể quay lại danh mục và thực hiện một đơn mới.'}</p>{displayResultCountdown !== null && <small className="payment-hint">Tự động quay về màn hình chính sau {displayResultCountdown} giây.</small>}<div className="flow-actions"><button className="primary large" onClick={resetTransaction}>Quay lại danh mục</button></div></section>}

    {screen === 'result' && order?.orderType !== 'BOTTLE' && (() => {
      const dispensed = dispensing?.status === 'DISPENSED'
      const needsManualReview = Boolean(payment?.status === 'PAID' && !dispensed && (dispensing?.status === 'UNKNOWN' || dispensing?.manualReviewRequired || order?.manualReviewRequired || !dispensing))
      const canRecheck = Boolean(order && payment?.status === 'PAID' && !dispensed)
      return <section className={`flow-card result-card ${dispensed ? 'success' : 'failure'}`}>
        <div className="result-icon">{dispensed ? '✓' : '!'}</div>
        <p className="step">{needsManualReview ? 'CẦN KIỂM TRA · ĐANG ĐỐI SOÁT' : 'KẾT QUẢ GIAO DỊCH'}</p>
        <h1>{dispensed ? 'Bạn có thích mùi hương này?' : payment?.status === 'FAILED' ? 'Thanh toán thất bại' : payment?.status === 'EXPIRED' ? 'Giao dịch đã hết hạn' : needsManualReview ? 'Thanh toán thành công · Chưa xác nhận phun' : 'Chưa thể hoàn tất lượt xịt'}</h1>
        <p>{dispensing?.manualReviewReason || order?.manualReviewReason || error || (dispensed ? 'Lượt thử đã hoàn tất. Bạn có thể chọn mua chai hoặc tiếp tục khám phá mùi khác.' : dispensing?.status === 'FAILED' ? 'Máy báo lỗi khi thực hiện lệnh xịt. Vui lòng cung cấp mã giao dịch cho nhân viên hỗ trợ để được kiểm tra và hoàn tiền.' : 'Hệ thống chưa nhận được xác nhận phun từ máy. Không tự gửi lại lệnh phun để tránh phun trùng — bạn có thể kiểm tra lại trạng thái hoặc liên hệ quầy hỗ trợ kèm mã giao dịch bên dưới.')}</p>
        {order && <div className="receipt"><span>Đơn hàng #{order.id}</span><span>{payment?.reference}</span><strong>{money(order.totalAmount, order.currency)}</strong></div>}
        {displayResultCountdown !== null && <small className="payment-hint">Tự động quay về màn hình chính sau {displayResultCountdown} giây.</small>}
        <div className="flow-actions">
          {canRecheck && <button className="secondary large" disabled={busy} onClick={() => void recheckOrderStatus()}>{busy ? 'Đang kiểm tra…' : 'Kiểm tra lại trạng thái đơn'}</button>}
          {dispensed && <button className="secondary large" onClick={() => { setTransactionType('BOTTLE'); setOrder(null); setPayment(null); setDispensing(null); setScreen('purchase') }}>Tôi muốn mua chai</button>}
          <button className="primary large" disabled={busy} onClick={resetTransaction}>{dispensed ? 'Khám phá mùi khác' : 'Bắt đầu lượt mới'}</button>
        </div>
      </section>
    })()}

    {screen === 'purchase' && selected && <section className="flow-card purchase-card">
      <p className="step">MUA SẢN PHẨM</p>
      <h1 className="flow-title">Chi tiết mua chai</h1>
      <div className="flow-product-summary">
        {selected.product.imageUrl ? <img src={selected.product.imageUrl} alt={selected.product.name} /> : <div className="flow-product-placeholder">S.</div>}
        <div className="flow-product-copy">
          <p className="eyebrow">ID SẢN PHẨM #{selected.product.id}</p>
          <h2>{selected.product.name}</h2>
          <p>{selected.product.description || selected.product.fragranceNotes || 'Backend chưa cung cấp mô tả cho sản phẩm này.'}</p>
        </div>
      </div>
      <section className="purchase-capability available" aria-labelledby="purchase-capability-title">
        <h2 id="purchase-capability-title">Xác nhận mua chai nguyên hộp</h2>
        <p>Giá bán, dung tích và tồn kho dưới đây được lấy trực tiếp từ Catalog Service.</p>
        <dl>
          <div><dt>Dung tích</dt><dd>{selected.product.bottleVolumeMl ? `${selected.product.bottleVolumeMl} ml` : 'Chưa cấu hình'}</dd></div>
          <div><dt>Đơn giá chai</dt><dd>{selected.product.bottlePrice ? money(selected.product.bottlePrice) : 'Chưa cấu hình'}</dd></div>
          <div><dt>Tồn kho bán lẻ</dt><dd>{selected.product.bottleStock} chai</dd></div>
          <div><dt>Hình thức nhận</dt><dd>Nhận tại quầy hỗ trợ kiosk</dd></div>
        </dl>
        <p className="purchase-safety-note">Đây là đơn mua chai, backend sẽ không gửi lệnh xịt. Tồn kho bán lẻ chỉ được trừ một lần sau khi thanh toán thành công.</p>
      </section>
      {resumablePayment && order?.orderType === 'BOTTLE' && <div className="pending-payment-note">Đơn mua chai đang chờ thanh toán. Tiếp tục để mở lại mã QR hiện tại.</div>}
      {error && <div className="error-box" role="alert">{error}</div>}
      <div className="flow-actions"><button className="secondary" onClick={() => setScreen(dispensing?.status === 'DISPENSED' ? 'result' : 'catalog')} disabled={busy}>Quay lại</button><button className="primary large" onClick={() => void startPayment('BOTTLE')} disabled={busy || !selected.product.bottlePrice || selected.product.bottleStock <= 0}>{busy ? 'Đang tạo đơn…' : resumablePayment ? 'Tiếp tục thanh toán' : 'Thanh toán mua chai'}</button></div>
    </section>}
  </main>
}

export default App

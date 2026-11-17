import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import './App.css'
import { ApiError, authToken, kioskApi } from './services/api'
import type { DispensingResponse, KioskItem, Machine, OrderResponse, PaymentResponse, Principal, Product, Slot } from './types/api'

type Screen = 'catalog' | 'confirm' | 'payment' | 'dispensing' | 'result' | 'purchase'
const configuredMachineId = Number(import.meta.env.VITE_KIOSK_MACHINE_ID || '9101')
const money = (value: number, currency = 'VND') => new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(value)
const readableError = (error: unknown) => error instanceof Error ? error.message : 'Không thể hoàn thành yêu cầu. Vui lòng thử lại.'

const availabilityText = (slot: Slot, machineReady: boolean, product: Product) => {
  if (!machineReady) return 'Máy đang ngoại tuyến hoặc chưa sẵn sàng'
  if (!product.available) return 'Sản phẩm đang tạm ngừng phục vụ'
  if (!slot.enabled || slot.status === 'DISABLED') return 'Đầu phun đang bị khóa'
  if (slot.status === 'EMPTY') return 'Đã hết nước hoa'
  if (slot.status === 'ERROR') return 'Đầu phun đang gặp lỗi'
  return slot.availabilityReason || 'Đầu phun chưa sẵn sàng'
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
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const creatingRef = useRef(false)
  const paymentReference = payment?.reference
  const paymentStatus = payment?.status

  const machineReady = machine?.deviceRegistrationStatus === 'ACTIVE'
    && machine.operationalStatus === 'ONLINE'
    && machine.maintenanceChecklistComplete
    && machine.postMaintenanceTestPassed

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
    setMachine(machineData); setItems(kioskItems)
    setSelected((current) => kioskItems.find((item) => item.slot.id === current?.slot.id) || null)
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
        if (current.status === 'PAID') { setError(''); setScreen('dispensing') }
        else if (current.status === 'FAILED') { setError('Thanh toán thất bại. Hệ thống không gửi lệnh xịt.'); setScreen('result') }
        else if (current.status === 'EXPIRED') { setError('Giao dịch đã hết hạn. Hệ thống không gửi lệnh xịt.'); setScreen('result') }
      } catch (reason) { if (active) setError(`Mất kết nối khi kiểm tra thanh toán: ${readableError(reason)}`) }
      finally { polling = false }
    }
    void poll()
    const timer = window.setInterval(() => void poll(), 3000)
    return () => { active = false; window.clearInterval(timer) }
  }, [paymentReference, paymentStatus, screen])

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
    setBusy(true); setError('')
    try { await loadCatalog(principal) } catch (reason) { setError(readableError(reason)) } finally { setBusy(false) }
  }

  async function startPayment() {
    if (!principal || !selected || creatingRef.current) return
    if (resumablePayment) {
      setError('')
      setScreen('payment')
      return
    }
    creatingRef.current = true; setBusy(true); setError('')
    try {
      const reusableOrder = order
        && ['PENDING', 'CONFIRMED'].includes(order.status)
        && String(order.machineId) === String(selected.slot.machineId)
        && String(order.slotId) === String(selected.slot.id)
        && order.items.length === 1
        && order.items[0].productId === selected.product.id
        ? order
        : null
      const currentOrder = reusableOrder ? await kioskApi.order(reusableOrder.id) : null
      const created = currentOrder && ['PENDING', 'CONFIRMED'].includes(currentOrder.status)
        ? currentOrder
        : await kioskApi.createOrder({
          customerId: principal.id, tenantId: String(principal.tenantId), machineId: String(selected.slot.machineId), slotId: String(selected.slot.id),
          items: [{ productId: selected.product.id, productName: selected.product.name, unitPrice: selected.pricing.effectivePrice, quantity: 1 }],
        })
      setOrder(created)
      const qr = await kioskApi.createPayment(created.id)
      setPayment(qr)
      if (qr.status === 'PAID') { setError(''); setScreen('dispensing') }
      else if (qr.status === 'FAILED') { setError('Thanh toán thất bại. Hệ thống không gửi lệnh xịt.'); setScreen('result') }
      else if (qr.status === 'EXPIRED') { setError('Giao dịch đã hết hạn. Hệ thống không gửi lệnh xịt.'); setScreen('result') }
      else setScreen('payment')
    } catch (reason) {
      setError(readableError(reason)); await refresh()
    } finally { creatingRef.current = false; setBusy(false) }
  }

  function resetTransaction() {
    setSelected(null); setOrder(null); setPayment(null); setDispensing(null); setError(''); setScreen('catalog')
    if (principal) void refresh()
  }

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

    {screen === 'catalog' && <><section className="hero"><div><p className="eyebrow">BỘ SƯU TẬP TẠI MÁY #{configuredMachineId}</p><h1>Chọn mùi hương bạn muốn trải nghiệm</h1><p>Mỗi lựa chọn tương ứng với một đầu phun thật đang được hệ thống kiểm tra.</p></div><button className="secondary" onClick={() => void refresh()} disabled={busy}>{busy ? 'Đang tải…' : 'Làm mới'}</button></section>
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
            {!ready && <div className="availability-note" role="status">{availabilityText(item.slot, Boolean(machineReady), item.product)}</div>}
            <div className="card-footer">
              <div className="card-price">
                <small>Phí trải nghiệm</small>
                <span className="card-price-values">
                  <strong>{money(item.pricing.effectivePrice)}</strong>
                  {item.pricing.promotionApplied && <del>{money(item.pricing.defaultPrice)}</del>}
                </span>
              </div>
              <div className="card-buttons">
                <button className="primary" disabled={!ready} onClick={() => { setSelected(item); setError(''); setScreen('confirm') }}>{ready ? 'Xịt thử' : 'Không khả dụng'}</button>
                <button className="secondary compact" onClick={() => { setSelected(item); setError(''); setScreen('purchase') }}>Mua chai</button>
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
          <p>{selected.product.fragranceNotes || selected.product.description || 'Thông tin hương thơm đang được cập nhật.'}</p>
        </div>
      </div>
      <div className="sample-dose"><span className="sample-icon">✦</span><div><small>Lượng thử đã hiệu chuẩn</small><strong>1 lượt xịt · {selected.slot.doseMl ?? 0.12} ml</strong><p>Đây là lượng cố định an toàn được cấu hình trực tiếp tại đầu phun.</p></div><span className="selected-mark">Đã chọn</span></div>
      <div className="summary-grid"><div><span>Máy</span><strong>{machine?.name}</strong></div><div><span>Đầu phun</span><strong>{selected.slot.slotCode}</strong></div><div><span>Thời lượng hiệu chuẩn</span><strong>{selected.slot.doseMl ? 'Theo cấu hình đầu phun' : 'Theo cấu hình máy'}</strong></div><div><span>Phí trải nghiệm</span><strong>{money(selected.pricing.effectivePrice)}</strong></div></div>
      <p className="flow-note">Giá, tenant, trạng thái máy, đầu phun và lượng xịt đều được backend kiểm tra lại. Kiosk không tự thay đổi liều lượng.</p>
      {resumablePayment && <div className="pending-payment-note">Mã QR đã được tạo và vẫn còn hiệu lực. Tiếp tục để mở lại đúng giao dịch, không tạo thêm đơn mới.</div>}
      {error && <div className="error-box" role="alert">{error}</div>}
      <div className="flow-actions"><button className="secondary" onClick={() => setScreen('catalog')} disabled={busy}>Quay lại</button><button className="primary large" onClick={() => void startPayment()} disabled={busy}>{busy ? 'Đang tạo đơn…' : resumablePayment ? 'Tiếp tục thanh toán' : 'Thanh toán lượt thử'}</button></div>
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
      <div className="qr-wrap" aria-label="Mã QR thanh toán">
        <QRCodeSVG value={payment.qrPayload} size={260} level="M" title={`QR thanh toán cho đơn hàng ${payment.orderId}`} />
        <span className="pulse-dot" aria-hidden="true"/>
      </div>
      <strong className="payment-amount">{money(payment.amount, payment.currency)}</strong>
      <div className="payment-details">
        <p><span>Mã giao dịch</span><code>{payment.reference}</code></p>
        <p><span>Hết hạn lúc</span><strong>{new Date(payment.expiresAt).toLocaleTimeString('vi-VN')}</strong></p>
      </div>
      <div className={`payment-status status-${payment.status.toLowerCase()}`} role="status">
        {payment.status === 'PENDING' && <div className="spinner"/>}
        <span>{payment.status === 'PENDING' ? 'Đang chờ backend xác nhận thanh toán…' : payment.status === 'PAID' ? 'Thanh toán thành công' : payment.status === 'FAILED' ? 'Thanh toán thất bại' : 'Giao dịch đã hết hạn'}</span>
      </div>
      {error && <div className="error-box" role="alert">{error}</div>}
      <div className="flow-actions payment-actions"><button className="secondary large" onClick={() => { setError(''); setScreen('confirm') }}>← Quay lại xác nhận</button></div>
      <small className="payment-hint">Mã QR vẫn có hiệu lực đến thời gian hết hạn. Khi quay lại, Kiosk sẽ dừng kiểm tra tạm thời và không tạo thêm đơn mới.</small>
    </section>}

    {screen === 'dispensing' && <section className="flow-card dispensing-card"><p className="step">BƯỚC 3 / 3 · XỊT NƯỚC HOA</p><div className="spray-animation">✦</div><h1>Thanh toán đã được xác nhận</h1><p>Backend đang gửi lệnh hợp lệ đến {selected?.slot.slotCode}. Vui lòng đưa cổ tay đến vị trí đầu phun.</p><div className="waiting"><div className="spinner"/>{dispensing ? `Trạng thái: ${dispensing.status}` : 'Đang chờ máy nhận lệnh…'}</div>{error && <div className="error-box">{error}</div>}</section>}

    {screen === 'result' && <section className={`flow-card result-card ${dispensing?.status === 'DISPENSED' ? 'success' : 'failure'}`}><div className="result-icon">{dispensing?.status === 'DISPENSED' ? '✓' : '!'}</div><p className="step">KẾT QUẢ GIAO DỊCH</p><h1>{dispensing?.status === 'DISPENSED' ? 'Bạn có thích mùi hương này?' : payment?.status === 'FAILED' ? 'Thanh toán thất bại' : payment?.status === 'EXPIRED' ? 'Giao dịch đã hết hạn' : 'Chưa thể hoàn tất lượt xịt'}</h1><p>{dispensing?.manualReviewReason || error || (dispensing?.status === 'DISPENSED' ? 'Lượt thử đã hoàn tất. Bạn có thể chọn mua chai hoặc tiếp tục khám phá mùi khác.' : dispensing?.status === 'FAILED' ? 'Máy báo lỗi khi thực hiện lệnh xịt.' : 'Vui lòng liên hệ nhân viên hỗ trợ nếu tiền đã được trừ.')}</p>{order && <div className="receipt"><span>Đơn hàng #{order.id}</span><span>{payment?.reference}</span><strong>{money(order.totalAmount, order.currency)}</strong></div>}<div className="flow-actions">{dispensing?.status === 'DISPENSED' && <button className="secondary large" onClick={() => setScreen('purchase')}>Tôi muốn mua chai</button>}<button className="primary large" onClick={resetTransaction}>{dispensing?.status === 'DISPENSED' ? 'Khám phá mùi khác' : 'Bắt đầu lượt mới'}</button></div></section>}

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
      <section className="purchase-capability" aria-labelledby="purchase-capability-title">
        <h2 id="purchase-capability-title">Thông tin mua chai chưa có trong Backend</h2>
        <p>Để tránh hiển thị giá hoặc tùy chọn không có thật, Kiosk chưa thể tạo đơn mua chai.</p>
        <dl>
          <div><dt>Dung tích và đơn giá chai</dt><dd>Chưa có dữ liệu/API bán chai</dd></div>
          <div><dt>Tồn kho và số lượng</dt><dd>Backend chưa cung cấp tồn kho chai</dd></div>
          <div><dt>Nhận tại điểm bán / giao hàng</dt><dd>Backend chưa có fulfillment</dd></div>
          <div><dt>Thanh toán cho đơn mua chai</dt><dd>Chưa có loại Order bán chai</dd></div>
        </dl>
        <p className="purchase-safety-note">Phí trải nghiệm trên danh sách sản phẩm không phải giá bán chai. Order thanh toán hiện có gắn với máy/slot và sau khi PAID sẽ kích hoạt lệnh xịt, vì vậy Kiosk không dùng luồng đó cho đơn mua chai.</p>
      </section>
      <div className="flow-actions"><button className="secondary" onClick={() => setScreen(dispensing?.status === 'DISPENSED' ? 'result' : 'catalog')}>Quay lại</button><button className="primary" disabled title="Chờ API sản phẩm và Order bán chai">Chưa thể xác nhận mua chai</button></div>
    </section>}
  </main>
}

export default App

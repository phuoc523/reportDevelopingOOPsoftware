import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { ApiError, authToken, kioskApi } from '../src/services/api.ts'
import { availabilityText, dispensingStatusText, formatCountdown } from '../src/utils/kioskHelpers.ts'
import type { Machine, Product, Slot } from '../src/types/api.ts'

type FetchCall = { url: string; method: string; headers: Headers; body?: string }

function installKioskMocks(
  handler: (call: FetchCall, signal?: AbortSignal) => Promise<{ status: number; body?: unknown }> | { status: number; body?: unknown },
) {
  const storage = new Map<string, string>()
  const events: Array<{ type: string; detail?: unknown }> = []
  const calls: FetchCall[] = []

  Object.defineProperty(globalThis, 'sessionStorage', {
    configurable: true,
    value: {
      getItem: (k: string) => storage.get(k) ?? null,
      setItem: (k: string, v: string) => { storage.set(k, v) },
      removeItem: (k: string) => { storage.delete(k) },
    },
  })

  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: {
      setTimeout: globalThis.setTimeout.bind(globalThis),
      clearTimeout: globalThis.clearTimeout.bind(globalThis),
      dispatchEvent: (event: Event) => {
        events.push({ type: event.type, detail: (event as CustomEvent).detail })
        return true
      },
    },
  })

  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const headers = new Headers(init?.headers)
    const call: FetchCall = {
      url: String(input),
      method: init?.method ?? 'GET',
      headers,
      body: typeof init?.body === 'string' ? init.body : undefined,
    }
    calls.push(call)
    const res = await handler(call, init?.signal ?? undefined)
    return {
      ok: res.status >= 200 && res.status < 300,
      status: res.status,
      text: async () => (res.body === undefined ? '' : typeof res.body === 'string' ? res.body : JSON.stringify(res.body)),
    } as Response
  }) as typeof fetch

  return { calls, events, storage }
}

const baseMachine: Machine = {
  id: 9101, tenantId: '12', locationId: 'LOC-1', name: 'Kiosk Lobby',
  deviceRegistrationStatus: 'ACTIVE', operationalStatus: 'ONLINE',
  operatingHoursStart: '08:00:00', operatingHoursEnd: '22:00:00',
  lastSeenAt: null, maintenanceChecklistComplete: true, postMaintenanceTestPassed: true,
}

const baseProduct: Product = {
  id: 10, tenantId: 12, name: 'Santal Noir', description: 'Gỗ đàn hương ấm áp',
  fragranceNotes: 'Gỗ, Hổ phách', imageUrl: null, defaultPrice: 15000,
  bottlePrice: 1200000, bottleVolumeMl: 100, bottleStock: 5, available: true,
}

const baseSlot: Slot = {
  id: 1, machineId: 9101, slotNumber: 1, slotCode: 'S01', productId: '10',
  cartridgeId: 'C01', enabled: true, status: 'AVAILABLE', available: true,
  availabilityReason: null, doseMl: 0.12, price: 15000,
}

describe('P5.1 & P5.4 — Availability, dispensing labels, and QR countdown', () => {
  it('explains offline machine, empty bottle, disabled slot, and outside operating hours clearly in Vietnamese', () => {
    assert.equal(availabilityText(baseSlot, false, baseProduct, baseMachine), 'Máy đang ngoại tuyến hoặc chưa sẵn sàng')
    assert.equal(availabilityText({ ...baseSlot, status: 'EMPTY' }, true, baseProduct, baseMachine), 'Đã hết nước hoa')
    assert.equal(availabilityText({ ...baseSlot, enabled: false }, true, baseProduct, baseMachine), 'Đầu phun đang bị khóa')
    assert.equal(
      availabilityText({ ...baseSlot, available: false, availabilityReason: 'Machine is outside operating hours' }, true, baseProduct, baseMachine),
      'Máy hiện ngoài giờ phục vụ (08:00–22:00)',
    )
  })

  it('formats dispensing statuses in Vietnamese and formats mm:ss countdown safely', () => {
    assert.equal(dispensingStatusText('PROCESSING'), 'Đang thực hiện phun nước hoa…')
    assert.equal(dispensingStatusText('DISPENSED'), 'Phun nước hoa thành công')
    assert.equal(dispensingStatusText('UNKNOWN'), 'Chưa xác định được kết quả phun')
    assert.equal(formatCountdown(125), '02:05')
    assert.equal(formatCountdown(-5), '00:00')
  })
})

describe('P5.2, P5.3, P5.5, P5.6 — Kiosk API flow, timeout, 401 handling, and safe status re-query', () => {
  it('completes login -> order -> QR payment -> read-only dispensing status re-query without duplicate commands', async () => {
    const env = installKioskMocks((call) => {
      if (call.url === '/api/auth/login') return { status: 200, body: { token: 'kiosk-jwt', tokenType: 'Bearer', userId: 7, username: 'kiosk1', role: 'CUSTOMER' } }
      if (call.url === '/api/v1/orders') return { status: 201, body: { id: 55, orderType: 'TRIAL', status: 'PENDING', totalAmount: 15000, currency: 'VND' } }
      if (call.url === '/api/v1/orders/55/payments/qr') return { status: 200, body: { reference: 'PAY-55', orderId: 55, status: 'PENDING', amount: 15000, currency: 'VND', qrPayload: 'QR-DATA' } }
      if (call.url === '/api/v1/payments/PAY-55') return { status: 200, body: { reference: 'PAY-55', orderId: 55, status: 'PAID', amount: 15000, currency: 'VND', qrPayload: 'QR-DATA' } }
      if (call.url === '/api/v1/orders/55/dispensing') return { status: 200, body: { orderId: 55, status: 'UNKNOWN', manualReviewRequired: true } }
      if (call.url === '/api/v1/orders/55') return { status: 200, body: { id: 55, orderType: 'TRIAL', status: 'PAID', manualReviewRequired: true } }
      return { status: 200, body: {} }
    })

    await kioskApi.login('kiosk1', 'Pass123!')
    assert.equal(authToken.get(), 'kiosk-jwt')

    const created = await kioskApi.createOrder({
      customerId: 7, tenantId: '12', machineId: '9101', slotId: '1', orderType: 'TRIAL',
      items: [{ productId: 10, productName: 'Santal Noir', unitPrice: 15000, quantity: 1 }],
    })
    assert.equal(created.id, 55)

    const qr = await kioskApi.createPayment(55)
    assert.equal(qr.reference, 'PAY-55')

    const paid = await kioskApi.payment('PAY-55')
    assert.equal(paid.status, 'PAID')

    // Re-querying order & dispensing status uses GET only (never triggers duplicate spray)
    const orderStatus = await kioskApi.order(55)
    const dispenseStatus = await kioskApi.dispensing(55)
    assert.equal(orderStatus.manualReviewRequired, true)
    assert.equal(dispenseStatus.status, 'UNKNOWN')
    assert.equal(env.calls[4].method, 'GET')
    assert.equal(env.calls[5].method, 'GET')
  })

  it('handles network disconnect (ApiError 0), timeout (ApiError 408), and 401 session revocation safely', async () => {
    // 1. Network disconnect
    installKioskMocks(() => { throw new TypeError('Failed to fetch') })
    await assert.rejects(() => kioskApi.machine(9101), (err: unknown) => err instanceof ApiError && err.status === 0)

    // 2. AbortError timeout -> ApiError 408
    installKioskMocks(() => { throw new DOMException('Aborted', 'AbortError') })
    await assert.rejects(() => kioskApi.machine(9101), (err: unknown) => err instanceof ApiError && err.status === 408)

    // 3. 401 on authenticated call clears token and notifies Kiosk shell
    const env = installKioskMocks(() => ({ status: 401, body: { message: 'Session expired' } }))
    authToken.set('expired-kiosk-jwt')
    await assert.rejects(() => kioskApi.me(), (err: unknown) => err instanceof ApiError && err.status === 401)
    assert.equal(authToken.get(), null)
    assert.equal(env.events.length, 1)
    assert.equal(env.events[0].type, 'scentstation:kiosk-unauthorized')
  })
})

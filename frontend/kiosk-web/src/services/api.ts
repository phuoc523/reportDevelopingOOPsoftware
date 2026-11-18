import type {
  AuthResponse,
  DispensingResponse,
  EffectivePrice,
  Machine,
  OrderResponse,
  PaymentResponse,
  Principal,
  Product,
  Slot,
} from '../types/api'

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const TOKEN_KEY = 'scentstation.kiosk.access-token'

export class ApiError extends Error {
  readonly status: number
  readonly details?: unknown

  constructor(
    status: number,
    message: string,
    details?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

export const authToken = {
  get: () => sessionStorage.getItem(TOKEN_KEY),
  set: (token: string) => sessionStorage.setItem(TOKEN_KEY, token),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
}

function errorMessage(status: number, body: unknown) {
  if (body && typeof body === 'object') {
    const value = body as Record<string, unknown>
    const message = value.message ?? value.detail ?? value.error
    if (typeof message === 'string' && message.trim()) return message
  }
  if (status === 401) return 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.'
  if (status === 403) return 'Tài khoản không có quyền thực hiện thao tác này.'
  if (status === 404) return 'Không tìm thấy dữ liệu được yêu cầu.'
  if (status === 409) return 'Trạng thái máy, đầu phun hoặc giao dịch không còn hợp lệ.'
  if (status >= 500) return 'Dịch vụ đang gián đoạn. Vui lòng thử lại sau.'
  return `Yêu cầu thất bại (HTTP ${status}).`
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = authToken.get()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })
  } catch (error) {
    throw new ApiError(0, 'Không thể kết nối đến hệ thống ScentStation.', error)
  }

  const text = await response.text()
  let body: unknown
  if (text) {
    try { body = JSON.parse(text) } catch { body = text }
  }
  if (!response.ok) throw new ApiError(response.status, errorMessage(response.status, body), body)
  return body as T
}

export const kioskApi = {
  async login(username: string, password: string) {
    const response = await request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    authToken.set(response.token)
    return response
  },
  me: () => request<Principal>('/api/auth/me'),
  logout: async () => {
    try { await request<void>('/api/auth/logout', { method: 'POST' }) }
    finally { authToken.clear() }
  },
  machine: (machineId: number) => request<Machine>(`/api/machines/${machineId}`),
  slots: (machineId: number) => request<Slot[]>(`/api/machines/${machineId}/slots`),
  products: (tenantId: number) => request<Product[]>(`/api/products?tenantId=${tenantId}`),
  pricing: (tenantId: number, productId: number) => request<EffectivePrice>(`/api/pricing/products/${productId}?tenantId=${tenantId}`),
  createOrder: (payload: {
    customerId: number
    tenantId: string
    machineId: string
    slotId: string
    orderType: 'TRIAL' | 'BOTTLE'
    items: Array<{ productId: number; productName: string; unitPrice: number; quantity: number }>
  }) => request<OrderResponse>('/api/v1/orders', { method: 'POST', body: JSON.stringify(payload) }),
  createPayment: (orderId: number) => request<PaymentResponse>(`/api/v1/orders/${orderId}/payments/qr`, { method: 'POST' }),
  payment: (reference: string) => request<PaymentResponse>(`/api/v1/payments/${encodeURIComponent(reference)}`),
  order: (orderId: number) => request<OrderResponse>(`/api/v1/orders/${orderId}`),
  dispensing: (orderId: number) => request<DispensingResponse>(`/api/v1/orders/${orderId}/dispensing`),
}

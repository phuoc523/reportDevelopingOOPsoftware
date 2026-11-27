const TOKEN_KEY = 'scentstation.admin.jwt'
const GET_CACHE_TTL_MS = 10_000
const env = (import.meta as { env?: Record<string, string | undefined> }).env ?? {}
const API_BASE_URL = (env.VITE_API_BASE_URL || '').replace(/\/$/, '')

const getCache = new Map<string, { expiresAt: number; data: unknown }>()
const inFlightGets = new Map<string, Promise<unknown>>()

export function clearApiCache() {
  getCache.clear()
  inFlightGets.clear()
}

export class ApiError extends Error {
  readonly status: number
  readonly details?: unknown

  constructor(
    message: string,
    status: number,
    details?: unknown,
  ) {
    super(message)
    this.status = status
    this.details = details
  }
}

export const authToken = {
  get: () => sessionStorage.getItem(TOKEN_KEY),
  set: (token: string) => { clearApiCache(); sessionStorage.setItem(TOKEN_KEY, token) },
  clear: () => { clearApiCache(); sessionStorage.removeItem(TOKEN_KEY) },
}

function isCacheableGet(path: string, method: string): boolean {
  return method === 'GET' && !path.startsWith('/api/auth/me')
}

function errorMessage(payload: unknown, status: number): string {
  if (status === 401) return 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.'
  if (status === 403) return 'Bạn không có quyền truy cập chức năng này.'
  if (status === 404) return 'Không tìm thấy dữ liệu được yêu cầu.'
  if (status === 408) return 'Hết thời gian chờ phản hồi từ máy chủ. Vui lòng thử lại.'
  if (status >= 500) return 'Dịch vụ đang gặp lỗi nội bộ. Vui lòng thử lại sau.'
  if (payload && typeof payload === 'object') {
    const value = payload as Record<string, unknown>
    for (const key of ['detail', 'message', 'error']) {
      if (typeof value[key] === 'string' && value[key]) return value[key]
    }
  }
  return `Không thể hoàn thành yêu cầu (HTTP ${status}).`
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const token = authToken.get()
  const cacheable = isCacheableGet(path, method)
  const cacheKey = `${token ?? ''}:${path}`

  if (!cacheable && method !== 'GET') {
    getCache.clear()
  }

  if (cacheable) {
    const cached = getCache.get(cacheKey)
    if (cached && cached.expiresAt > Date.now()) {
      return cached.data as T
    }
    const existing = inFlightGets.get(cacheKey)
    if (existing) {
      return existing as Promise<T>
    }
  }

  const run = (async (): Promise<T> => {
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')

    let response: Response
    try {
      response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })
    } catch (error) {
      throw new ApiError('Không thể kết nối đến máy chủ backend.', 0, error)
    }

    const contentType = response.headers.get('content-type') ?? ''
    const payload = response.status === 204
      ? undefined
      : contentType.includes('json')
        ? await response.json().catch(() => undefined)
        : await response.text().catch(() => undefined)

    if (!response.ok) {
      const message = errorMessage(payload, response.status)
      if (response.status === 401) {
        authToken.clear()
        window.dispatchEvent(new CustomEvent('scentstation:unauthorized', { detail: message }))
      } else if (response.status === 403 && authToken.get()) {
        window.dispatchEvent(new Event('scentstation:forbidden'))
      }
      throw new ApiError(message, response.status, payload)
    }

    if (cacheable) {
      getCache.set(cacheKey, { expiresAt: Date.now() + GET_CACHE_TTL_MS, data: payload })
    }
    return payload as T
  })()

  if (cacheable) {
    inFlightGets.set(cacheKey, run)
    try {
      return await run
    } finally {
      inFlightGets.delete(cacheKey)
    }
  }
  return run
}

export async function download(path: string, filename: string, accept = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'): Promise<void> {
  const headers = new Headers({ Accept: accept })
  const token = authToken.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE_URL}${path}`, { headers })
  if (!response.ok) {
    const payload = await response.json().catch(() => undefined)
    throw new ApiError(errorMessage(payload, response.status), response.status, payload)
  }
  const url = URL.createObjectURL(await response.blob())
  const anchor = document.createElement('a')
  anchor.href = url; anchor.download = filename; anchor.click()
  URL.revokeObjectURL(url)
}

export const query = (values: Record<string, string | number | boolean | null | undefined>) => {
  const search = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const encoded = search.toString()
  return encoded ? `?${encoded}` : ''
}

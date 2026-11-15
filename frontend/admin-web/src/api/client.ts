const TOKEN_KEY = 'scentstation.admin.jwt'

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
  set: (token: string) => sessionStorage.setItem(TOKEN_KEY, token),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
}

function errorMessage(payload: unknown, status: number): string {
  if (status === 401) return 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.'
  if (status === 403) return 'Bạn không có quyền truy cập chức năng này.'
  if (status === 404) return 'Không tìm thấy dữ liệu được yêu cầu.'
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
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  const token = authToken.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')

  const response = await fetch(path, { ...init, headers })
  const contentType = response.headers.get('content-type') ?? ''
  const payload = response.status === 204
    ? undefined
    : contentType.includes('json')
      ? await response.json().catch(() => undefined)
      : await response.text().catch(() => undefined)

  if (!response.ok) {
    if (response.status === 401) {
      authToken.clear()
      window.dispatchEvent(new Event('scentstation:unauthorized'))
    }
    throw new ApiError(errorMessage(payload, response.status), response.status, payload)
  }
  return payload as T
}

export async function download(path: string, filename: string): Promise<void> {
  const headers = new Headers({ Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const token = authToken.get()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(path, { headers })
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

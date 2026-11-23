import assert from 'node:assert/strict'
import { beforeEach, describe, it } from 'node:test'
import { brandAssignableRoles, brandDelegablePermissions, permissions, roles } from '../src/config/rbac.ts'
import { ApiError, authToken, query } from '../src/api/client.ts'
import { backend } from '../src/api/scentStation.ts'

type FetchCall = { url: string; method: string; headers: Headers; body?: string }

function installBrowserMocks(handler: (call: FetchCall) => { status: number; body?: unknown; contentType?: string }) {
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
      dispatchEvent: (event: Event) => {
        events.push({ type: event.type, detail: (event as CustomEvent).detail })
        return true
      },
    },
  })

  Object.defineProperty(globalThis, 'document', {
    configurable: true,
    value: {
      createElement: () => ({ href: '', download: '', click: () => {} }),
    },
  })

  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: () => 'blob:mock' })
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: () => {} })

  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const headers = new Headers(init?.headers)
    const call: FetchCall = {
      url: String(input),
      method: init?.method ?? 'GET',
      headers,
      body: typeof init?.body === 'string' ? init.body : undefined,
    }
    calls.push(call)
    const res = handler(call)
    const contentType = res.contentType ?? 'application/json'
    return {
      ok: res.status >= 200 && res.status < 300,
      status: res.status,
      headers: new Headers({ 'content-type': contentType }),
      json: async () => res.body,
      text: async () => (typeof res.body === 'string' ? res.body : JSON.stringify(res.body ?? '')),
      blob: async () => new Blob([typeof res.body === 'string' ? res.body : 'mock-bytes']),
    } as Response
  }) as typeof fetch

  return { calls, events, storage }
}

describe('P5.8 & P5.10 — RBAC policy boundaries', () => {
  it('Brand Admin cannot assign PLATFORM_SUPER_ADMIN or BRAND_ADMIN roles', () => {
    assert.ok(!brandAssignableRoles.includes('PLATFORM_SUPER_ADMIN'))
    assert.ok(!brandAssignableRoles.includes('BRAND_ADMIN'))
    assert.ok(!brandAssignableRoles.includes('CUSTOMER'))
    assert.deepEqual(brandAssignableRoles, ['OPERATIONS_MANAGER', 'TECHNICIAN', 'INVENTORY_STAFF'])
  })

  it('Brand Admin delegable permissions exclude platform-only, high-risk, and user/role admin permissions', () => {
    const forbidden = [
      'TENANT_MANAGE', 'PLATFORM_CONFIG_MANAGE', 'PLATFORM_ROLE_MANAGE',
      'PLATFORM_AUDIT_READ', 'PLATFORM_REPORT_READ', 'DEVICE_ACCESS_REVOKE',
      'REFUND_APPROVE', 'INVENTORY_ADJUST', 'DIAGNOSTIC_SPRAY', 'MACHINE_CONFIGURE',
      'BRAND_ROLE_MANAGE', 'BRAND_USER_MANAGE',
    ]
    for (const perm of forbidden) {
      assert.ok(!brandDelegablePermissions.includes(perm as typeof permissions[number]), `Should exclude ${perm}`)
    }
    assert.ok(roles.includes('PLATFORM_SUPER_ADMIN'))
  })
})

describe('P5.8 — Authentication, 401 session expiry/lockout, and 403 permission refresh', () => {
  let env: ReturnType<typeof installBrowserMocks>

  beforeEach(() => {
    env = installBrowserMocks((call) => {
      if (call.url === '/api/auth/login') {
        return { status: 200, body: { token: 'jwt-abc', tokenType: 'Bearer', userId: 1, username: 'admin', role: 'BRAND_ADMIN' } }
      }
      if (call.url === '/api/auth/me') {
        return { status: 200, body: { id: 1, username: 'admin', tenantId: 12, roles: ['BRAND_ADMIN'], grants: ['REPORT_READ', 'REPORT_EXPORT'], locationIds: [], machineIds: [] } }
      }
      if (call.url === '/api/auth/logout') {
        return { status: 204 }
      }
      if (call.url.startsWith('/api/locked')) {
        return { status: 401, body: { message: 'Account is locked' } }
      }
      if (call.url.startsWith('/api/platform/tenants')) {
        return { status: 403, body: { message: 'Forbidden' } }
      }
      return { status: 200, body: {} }
    })
  })

  it('stores JWT on login, sends Bearer token on me(), and logs out cleanly', async () => {
    await backend.login('admin', 'StrongPassword123!')
    assert.equal(authToken.get(), 'jwt-abc')
    const profile = await backend.me()
    assert.equal(profile.tenantId, 12)
    assert.equal(env.calls[1].headers.get('Authorization'), 'Bearer jwt-abc')
    await backend.logout()
    assert.equal(env.calls[2].url, '/api/auth/logout')
  })

  it('clears token and dispatches scentstation:unauthorized on 401 (expired/locked account)', async () => {
    authToken.set('revoked-jwt')
    env = installBrowserMocks(() => ({ status: 401, body: { message: 'Account locked' } }))
    await assert.rejects(() => backend.me(), (err: unknown) => err instanceof ApiError && err.status === 401)
    assert.equal(authToken.get(), null)
    assert.equal(env.events.length, 1)
    assert.equal(env.events[0].type, 'scentstation:unauthorized')
  })

  it('dispatches scentstation:forbidden on 403 when session token exists so UI can resync permissions', async () => {
    authToken.set('valid-jwt')
    await assert.rejects(() => backend.tenants(), (err: unknown) => err instanceof ApiError && err.status === 403)
    assert.equal(authToken.get(), 'valid-jwt')
    assert.ok(env.events.some((e) => e.type === 'scentstation:forbidden'))
  })
})

describe('P5.9–P5.17 — Backend API routing, tenant scoping, and Reporting integration', () => {
  it('routes Super Admin vs Brand Admin user management and account locking to separate API tiers', async () => {
    const env = installBrowserMocks(() => ({ status: 200, body: [] }))
    authToken.set('jwt-1')

    await backend.users(true, 12)
    await backend.users(false, 99) // Brand Admin must NOT send tenantId override
    await backend.updateUserStatus(false, 5, true, true)

    assert.equal(env.calls[0].url, '/api/platform/users?tenantId=12')
    assert.equal(env.calls[1].url, '/api/brand/users')
    assert.equal(env.calls[2].url, '/api/brand/users/5/status')
    assert.equal(env.calls[2].method, 'PATCH')
    assert.deepEqual(JSON.parse(env.calls[2].body ?? '{}'), { active: true, locked: true })
  })

  it('calls Reporting dashboard, operational report, revenue breakdown, and Excel/CSV exports correctly', async () => {
    const env = installBrowserMocks(() => ({ status: 200, body: {}, contentType: 'text/csv' }))
    authToken.set('jwt-report')

    await backend.dashboard(12, 'week')
    await backend.operationalReport({ tenantId: 12, period: 'month', machineId: 4 })
    await backend.revenueBreakdown('2026-11-01', '2026-11-30', 12)
    await backend.exportTransactions({ tenantId: 12, status: 'PAID' }, 'csv')
    await backend.exportRevenue('2026-11-01', '2026-11-30', 12, 'xlsx')
    await backend.exportOperations({ tenantId: 12, period: 'month' }, 'csv')

    assert.equal(env.calls[0].url, '/api/reporting/dashboard?tenantId=12&period=week')
    assert.equal(env.calls[1].url, '/api/reporting/operations?tenantId=12&period=month&machineId=4')
    assert.equal(env.calls[2].url, '/api/reporting/revenue/breakdown?startDate=2026-11-01&endDate=2026-11-30&tenantId=12')
    assert.equal(env.calls[3].url, '/api/reporting/export/transactions?tenantId=12&status=PAID&format=csv')
    assert.equal(env.calls[3].headers.get('Accept'), 'text/csv')
    assert.equal(env.calls[4].url, '/api/reporting/export/revenue?startDate=2026-11-01&endDate=2026-11-30&tenantId=12&format=xlsx')
    assert.equal(env.calls[5].url, '/api/reporting/export/operations?tenantId=12&period=month&format=csv')
  })

  it('supports machine control (ENABLE/DISABLE/ENTER_MAINTENANCE), inventory workflows, and query encoding', async () => {
    const env = installBrowserMocks(() => ({ status: 200, body: {} }))
    authToken.set('jwt-ops')

    await backend.controlMachine(7, 'ENTER_MAINTENANCE')
    await backend.adjustInventory({ tenantId: 12, bottleId: 50, newQuantity: 25, actorId: 2, reason: 'Kiểm kê thực tế' })

    assert.equal(env.calls[0].url, '/api/machines/7/control')
    assert.deepEqual(JSON.parse(env.calls[0].body ?? '{}'), { action: 'ENTER_MAINTENANCE' })
    assert.equal(env.calls[1].url, '/api/inventory/adjustments')
    assert.equal(query({ tenantId: 12, empty: '', nil: null, undef: undefined }), '?tenantId=12')
  })
})

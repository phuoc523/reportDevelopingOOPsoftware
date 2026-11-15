import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { backend } from '../api/scentStation'
import type { Account, Permission, Principal, Role, Tenant, TenantInput } from '../api/types'
import { brandAssignableRoles, brandDelegablePermissions, permissions, roleLabel, roles } from '../config/rbac'

const errorText = (error: unknown) => error instanceof ApiError || error instanceof Error
  ? error.message : 'Không thể hoàn thành yêu cầu.'
const optional = (value: string) => value.trim() || null
const ids = (value: string) => value.trim() ? value.split(',').map((item) => Number(item.trim())).filter(Number.isInteger) : []

export function Modal({ title, close, children }: { title: string; close: () => void; children: React.ReactNode }) {
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && close()}>
    <section className="modal modal-wide" role="dialog" aria-modal="true" aria-label={title}>
      <div className="modal-title"><h3>{title}</h3><button type="button" onClick={close} aria-label="Đóng">×</button></div>
      {children}
    </section>
  </div>
}

export function TenantForm({ tenant, platform, close, saved }: {
  tenant?: Tenant; platform: boolean; close: () => void; saved: (tenant: Tenant) => void
}) {
  const [form, setForm] = useState({
    code: tenant?.code ?? '', name: tenant?.name ?? '', logoUrl: tenant?.logoUrl ?? '',
    description: tenant?.description ?? '', contactEmail: tenant?.contactEmail ?? '',
    contactPhone: tenant?.contactPhone ?? '', kioskContent: tenant?.kioskContent ?? '',
  })
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('')
    if (!tenant && !/^[a-zA-Z0-9][a-zA-Z0-9_-]{2,63}$/.test(form.code)) {
      setError('Mã tenant phải dài 3–64 ký tự và chỉ gồm chữ, số, _ hoặc -.'); return
    }
    setSaving(true)
    const payload: TenantInput = { code: form.code, name: form.name.trim(), profile: {
      logoUrl: optional(form.logoUrl), description: optional(form.description),
      contactEmail: optional(form.contactEmail), contactPhone: optional(form.contactPhone),
      kioskContent: optional(form.kioskContent),
    } }
    try { saved(tenant ? await backend.updateTenant(platform, tenant.id, payload) : await backend.createTenant(payload)); close() }
    catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  return <Modal title={tenant ? 'Cập nhật thương hiệu' : 'Tạo thương hiệu'} close={close}>
    <form onSubmit={submit} className="form-grid">
      {!tenant && <label>Mã tenant<input required minLength={3} maxLength={64} value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} /></label>}
      <label>Tên thương hiệu<input required maxLength={160} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
      <label>Email liên hệ<input type="email" maxLength={160} value={form.contactEmail} onChange={(e) => setForm({ ...form, contactEmail: e.target.value })} /></label>
      <label>Số điện thoại<input maxLength={40} value={form.contactPhone} onChange={(e) => setForm({ ...form, contactPhone: e.target.value })} /></label>
      <label className="full">Logo URL<input type="url" maxLength={1024} value={form.logoUrl} onChange={(e) => setForm({ ...form, logoUrl: e.target.value })} /></label>
      <label className="full">Mô tả<textarea maxLength={2000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
      <label className="full">Nội dung kiosk<textarea maxLength={3000} value={form.kioskContent} onChange={(e) => setForm({ ...form, kioskContent: e.target.value })} /></label>
      {error && <div className="error-box full">{error}</div>}
      <div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu'}</button></div>
    </form>
  </Modal>
}

export function UserForm({ account, principal, platform, tenants, selectedTenantId, close, saved }: {
  account?: Account; principal: Principal; platform: boolean; tenants: Tenant[]; selectedTenantId?: number;
  close: () => void; saved: (account: Account) => void
}) {
  const assignableRoles = platform ? roles : brandAssignableRoles
  const assignablePermissions = platform ? permissions : brandDelegablePermissions
  const editingSelf = account?.id === principal.id
  const [form, setForm] = useState({
    username: account?.username ?? '', email: account?.email ?? '', fullName: account?.fullName ?? '', password: '',
    tenantId: account?.tenantId ?? selectedTenantId ?? principal.tenantId ?? null,
    roles: account?.roles ?? ([platform && !selectedTenantId ? 'PLATFORM_SUPER_ADMIN' : assignableRoles[0]] as Role[]),
    grants: account?.explicitGrants ?? ([] as Permission[]),
    locationIds: account?.locationIds.join(', ') ?? '', machineIds: account?.machineIds.join(', ') ?? '',
    active: account?.active ?? true, locked: account?.locked ?? false,
  })
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)
  const platformAccount = form.roles.includes('PLATFORM_SUPER_ADMIN')
  const roleOptions = useMemo(() => account && !assignableRoles.some((role) => account.roles.includes(role))
    ? [...assignableRoles, ...account.roles] : assignableRoles, [account, assignableRoles])
  const toggleRole = (role: Role) => setForm({ ...form, roles: form.roles.includes(role) ? form.roles.filter((item) => item !== role) : [...form.roles, role] })
  const toggleGrant = (permission: Permission) => setForm({ ...form, grants: form.grants.includes(permission) ? form.grants.filter((item) => item !== permission) : [...form.grants, permission] })
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('')
    if (!form.roles.length) { setError('Phải chọn ít nhất một vai trò.'); return }
    if (!account && form.password.length < 12) { setError('Mật khẩu tạo bởi admin phải có ít nhất 12 ký tự.'); return }
    if (!platformAccount && !form.tenantId) { setError('Tài khoản thương hiệu phải thuộc một tenant.'); return }
    setSaving(true)
    try {
      let result: Account
      if (!account) {
        result = await backend.createUser(platform, { username: form.username.trim(), email: form.email.trim(),
          password: form.password, fullName: form.fullName.trim(), tenantId: platformAccount ? null : form.tenantId,
          roles: form.roles, locationIds: ids(form.locationIds), machineIds: ids(form.machineIds) })
      } else {
        result = await backend.updateUserProfile(platform, account.id, form.email.trim(), form.fullName.trim())
        if (!editingSelf) result = await backend.updateUserRoles(platform, account.id, form.roles)
        result = await backend.updateUserGrants(platform, account.id, form.grants)
        result = await backend.updateUserScopes(platform, account.id, ids(form.locationIds), ids(form.machineIds))
        result = await backend.updateUserStatus(platform, account.id, form.active, form.locked)
        if (form.password) { if (form.password.length < 12) throw new Error('Mật khẩu mới phải có ít nhất 12 ký tự.'); await backend.resetUserPassword(platform, account.id, form.password) }
      }
      saved(result); close()
    } catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  return <Modal title={account ? `Quản lý: ${account.username}` : 'Tạo tài khoản'} close={close}>
    <form onSubmit={submit} className="form-grid">
      <label>Tên đăng nhập<input required disabled={!!account} minLength={3} maxLength={80} value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></label>
      <label>Họ tên<input required maxLength={120} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></label>
      <label>Email<input required type="email" maxLength={160} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
      <label>{account ? 'Mật khẩu mới (tùy chọn)' : 'Mật khẩu'}<input required={!account} type="password" minLength={12} maxLength={72} autoComplete="new-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
      {platform && !platformAccount && <label>Tenant<select required value={form.tenantId ?? ''} onChange={(e) => setForm({ ...form, tenantId: Number(e.target.value) || null })}><option value="">Chọn tenant</option>{tenants.map((tenant) => <option key={tenant.id} value={tenant.id}>{tenant.name}</option>)}</select></label>}
      <fieldset className="full"><legend>Vai trò</legend><div className="check-grid">{roleOptions.map((role) => <label key={role}><input type="checkbox" disabled={editingSelf} checked={form.roles.includes(role)} onChange={() => toggleRole(role)} />{roleLabel[role]}</label>)}</div>{editingSelf && <small>Không thể tự thay đổi vai trò của chính mình.</small>}</fieldset>
      {account && <fieldset className="full"><legend>Explicit grants</legend><div className="check-grid permissions">{assignablePermissions.map((permission) => <label key={permission}><input type="checkbox" checked={form.grants.includes(permission)} onChange={() => toggleGrant(permission)} />{permission}</label>)}</div></fieldset>}
      <label>Location IDs<input placeholder="1, 2, 3" value={form.locationIds} onChange={(e) => setForm({ ...form, locationIds: e.target.value })} /></label>
      <label>Machine IDs<input placeholder="10, 11" value={form.machineIds} onChange={(e) => setForm({ ...form, machineIds: e.target.value })} /></label>
      {account && <><label className="switch"><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />Tài khoản hoạt động</label><label className="switch"><input type="checkbox" checked={form.locked} onChange={(e) => setForm({ ...form, locked: e.target.checked })} />Khóa tài khoản</label></>}
      {error && <div className="error-box full">{error}</div>}
      <div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu tài khoản'}</button></div>
    </form>
  </Modal>
}

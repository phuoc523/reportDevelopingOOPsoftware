import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { backend } from '../api/scentStation'
import type { Account, Principal, Role, Tenant, TenantInput } from '../api/types'
import { brandAssignableRoles, roleLabel, roles } from '../config/rbac'

const errorText = (error: unknown) => error instanceof ApiError || error instanceof Error
  ? error.message : 'Không thể hoàn thành yêu cầu.'
const optional = (value: string) => value.trim() || null

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
  const editingSelf = account?.id === principal.id
  const isCustomerAccount = Boolean(account?.roles.includes('CUSTOMER'))
  const [form, setForm] = useState({
    username: account?.username ?? '', email: account?.email ?? '', fullName: account?.fullName ?? '', password: '',
    tenantId: account?.tenantId ?? selectedTenantId ?? principal.tenantId ?? null,
    roles: account?.roles ?? ([platform && !selectedTenantId ? 'PLATFORM_SUPER_ADMIN' : assignableRoles[0]] as Role[]),
    active: account?.active ?? true, locked: account?.locked ?? false,
  })
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)
  const platformAccount = form.roles.includes('PLATFORM_SUPER_ADMIN')
  const roleOptions = useMemo(() => account && !assignableRoles.some((role) => account.roles.includes(role))
    ? [...assignableRoles, ...account.roles] : assignableRoles, [account, assignableRoles])
  const toggleRole = (role: Role) => setForm({ ...form, roles: form.roles.includes(role) ? form.roles.filter((item) => item !== role) : [...form.roles, role] })
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
          roles: form.roles, locationIds: [], machineIds: [] })
      } else {
        result = await backend.updateUserProfile(platform, account.id, form.email.trim(), form.fullName.trim(), form.username.trim())
        const rolesChanged = JSON.stringify([...account.roles].sort()) !== JSON.stringify([...form.roles].sort())
        if (!editingSelf && rolesChanged) result = await backend.updateUserRoles(platform, account.id, form.roles)
        if (form.active !== account.active || form.locked !== account.locked) {
          result = await backend.updateUserStatus(platform, account.id, form.active, form.locked)
        }
        if (!isCustomerAccount && form.password) { if (form.password.length < 12) throw new Error('Mật khẩu mới phải có ít nhất 12 ký tự.'); await backend.resetUserPassword(platform, account.id, form.password) }
      }
      saved(result); close()
    } catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  return <Modal title={account ? `Quản lý: ${account.username}` : 'Tạo tài khoản'} close={close}>
    <form onSubmit={submit} className="form-grid">
      <label>Tên đăng nhập<input required minLength={3} maxLength={80} placeholder="VD: ten@tenthuonghieu.vaitro" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></label>
      <label>Họ tên<input required maxLength={120} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></label>
      <label>Email<input required type="email" maxLength={160} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
      {isCustomerAccount ? (
        <div className="slot-sync-box full">
          <strong>Bảo mật mật khẩu khách hàng:</strong> Mật khẩu được băm một chiều (BCrypt). Quản trị viên không thể xem hoặc đặt lại mật khẩu của khách hàng.
        </div>
      ) : (
        <label>{account ? 'Đặt lại mật khẩu mới (tùy chọn · lưu hash BCrypt)' : 'Mật khẩu (tự động băm BCrypt)'}<input required={!account} type="password" minLength={12} maxLength={72} autoComplete="new-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
      )}
      {platform && !platformAccount && <label>Tenant<select required value={form.tenantId ?? ''} onChange={(e) => setForm({ ...form, tenantId: Number(e.target.value) || null })}><option value="">Chọn tenant</option>{tenants.map((tenant) => <option key={tenant.id} value={tenant.id}>{tenant.name}</option>)}</select></label>}
      <fieldset className="full"><legend>Vai trò</legend><div className="check-grid">{roleOptions.map((role) => <label key={role}><input type="checkbox" disabled={editingSelf} checked={form.roles.includes(role)} onChange={() => toggleRole(role)} />{roleLabel[role]}</label>)}</div>{editingSelf && <small>Không thể tự thay đổi vai trò của chính mình.</small>}</fieldset>
      {account && <><label className="switch"><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />Tài khoản hoạt động</label><label className="switch"><input type="checkbox" checked={form.locked} onChange={(e) => setForm({ ...form, locked: e.target.checked })} />Khóa tài khoản</label></>}
      {error && <div className="error-box full">{error}</div>}
      <div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu tài khoản'}</button></div>
    </form>
  </Modal>
}

const roleSlugMap: Record<string, string> = {
  PLATFORM_SUPER_ADMIN: 'platform',
  BRAND_ADMIN: 'brandadmin',
  OPERATIONS_MANAGER: 'ops',
  TECHNICIAN: 'technician',
  INVENTORY_STAFF: 'inventory',
  CUSTOMER: 'customer',
}

export function AccountSettingsModal({ principal, brandCode, initialTab = 'username', close, savedUsername, savedPassword }: {
  principal: Principal
  brandCode?: string
  initialTab?: 'username' | 'password'
  close: () => void
  savedUsername: (next: Principal, message: string) => void
  savedPassword: (message: string) => void
}) {
  const [tab, setTab] = useState<'username' | 'password'>(initialTab)
  const [newUsername, setNewUsername] = useState(principal.username)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const primaryRole = principal.roles[0] ?? 'STAFF'
  const roleSlug = roleSlugMap[primaryRole] ?? primaryRole.toLowerCase()
  const cleanBrand = (brandCode || 'scentstation').toLowerCase().replace(/[^a-z0-9]/g, '') || 'brand'
  const baseName = principal.username.split('@')[0].replace(/[^a-zA-Z0-9._-]/g, '').toLowerCase() || 'ten'
  const suggestion = `${baseName}@${cleanBrand}.${roleSlug}`

  async function submitUsername(event: FormEvent) {
    event.preventDefault()
    setError('')
    const trimmed = newUsername.trim()
    if (trimmed.length < 3 || trimmed.length > 80) { setError('Tên đăng nhập phải từ 3 đến 80 ký tự.'); return }
    if (/\s/.test(trimmed)) { setError('Tên đăng nhập không được chứa khoảng trắng.'); return }
    setSaving(true)
    try {
      await backend.changeOwnUsername(trimmed)
      const latest = await backend.me()
      savedUsername(latest, `Đã đổi tên đăng nhập thành "${latest.username}" (vai trò: ${latest.roles.map((r) => roleLabel[r as Role] ?? r).join(', ')}).`)
      close()
    } catch (reason) {
      setError(errorText(reason))
    } finally {
      setSaving(false)
    }
  }

  async function submitPassword(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (newPassword.length < 4) { setError('Mật khẩu mới phải có ít nhất 4 ký tự.'); return }
    if (newPassword !== confirmPassword) { setError('Mật khẩu xác nhận không khớp.'); return }
    setSaving(true)
    try {
      await backend.changeOwnPassword(currentPassword, newPassword)
      savedPassword('Đã đổi mật khẩu thành công.')
      close()
    } catch (reason) {
      setError(errorText(reason))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title="Cài đặt tài khoản cá nhân" close={close}>
      <nav className="order-tabs" style={{ justifyContent: 'flex-start', marginBottom: 14 }}>
        <button type="button" className={tab === 'username' ? 'active' : ''} onClick={() => { setTab('username'); setError('') }}>Đổi tên đăng nhập</button>
        <button type="button" className={tab === 'password' ? 'active' : ''} onClick={() => { setTab('password'); setError('') }}>Đổi mật khẩu</button>
      </nav>
      {tab === 'username' ? (
        <form onSubmit={submitUsername} className="form-grid">
          <div className="slot-sync-box full">
            <p style={{ margin: '0 0 6px' }}><strong>Tên hiện tại:</strong> {principal.username}</p>
            <p style={{ margin: '0 0 6px' }}><strong>Vai trò:</strong> {principal.roles.map((r) => roleLabel[r as Role] ?? r).join(', ')}</p>
            <p style={{ margin: 0 }}>
              <strong>Định dạng gợi ý:</strong> <code>{suggestion}</code>{' '}
              <button type="button" style={{ marginLeft: 8, padding: '2px 8px', fontSize: 12 }} onClick={() => setNewUsername(suggestion)}>Dùng tên gợi ý</button>
            </p>
          </div>
          <label className="full">
            Tên đăng nhập mới (ví dụ: tên@tên-thương-hiệu.vai-trò)
            <input required minLength={3} maxLength={80} placeholder={suggestion} value={newUsername} onChange={(e) => setNewUsername(e.target.value)} />
          </label>
          {error && <div className="error-box full">{error}</div>}
          <div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang cập nhật…' : 'Cập nhật tên đăng nhập'}</button></div>
        </form>
      ) : (
        <form onSubmit={submitPassword} className="form-grid">
          <label className="full">Mật khẩu hiện tại<input required type="password" autoComplete="current-password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} /></label>
          <label>Mật khẩu mới (tối thiểu 4 ký tự)<input required type="password" minLength={4} maxLength={72} autoComplete="new-password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} /></label>
          <label>Xác nhận mật khẩu mới<input required type="password" minLength={4} maxLength={72} autoComplete="new-password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} /></label>
          {error && <div className="error-box full">{error}</div>}
          <div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang cập nhật…' : 'Cập nhật mật khẩu'}</button></div>
        </form>
      )}
    </Modal>
  )
}

import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { backend } from '../api/scentStation'
import type { Campaign, CampaignInput, Product } from '../api/types'
import { Modal } from './IdentityForms'

const errorText = (error: unknown) => error instanceof ApiError || error instanceof Error
  ? error.message : 'Không thể hoàn thành yêu cầu.'
const localDateTime = (value?: string) => value ? value.slice(0, 16) : ''

export function ProductPriceForm({ product, close, saved }: {
  product: Product; close: () => void; saved: (product: Product) => void
}) {
  const [price, setPrice] = useState(String(product.defaultPrice)); const [error, setError] = useState(''); const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); const value = Number(price)
    if (!Number.isFinite(value) || value < 0) { setError('Giá phải là số không âm.'); return }
    setSaving(true); try { saved(await backend.setProductPrice(product.id, value)); close() }
    catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  return <Modal title={`Cập nhật giá: ${product.name}`} close={close}><form className="form-grid" onSubmit={submit}>
    <label className="full">Giá mặc định<input autoFocus required type="number" min="0" step="0.01" value={price} onChange={(event) => setPrice(event.target.value)} /></label>
    {error && <div className="error-box full">{error}</div>}<div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Cập nhật giá'}</button></div>
  </form></Modal>
}

export function CampaignForm({ campaign, tenantId, products, close, saved }: {
  campaign?: Campaign; tenantId: number; products: Product[]; close: () => void; saved: (campaign?: Campaign, message?: string) => void
}) {
  const [form, setForm] = useState<CampaignInput>({
    tenantId, productId: campaign?.productId ?? products[0]?.id ?? 0, name: campaign?.name ?? '',
    discountType: campaign?.discountType === 'FIXED_AMOUNT' ? 'FIXED_AMOUNT' : 'PERCENTAGE',
    discountValue: campaign?.discountValue ?? 0, startAt: localDateTime(campaign?.startAt),
    endAt: localDateTime(campaign?.endAt), active: campaign?.active ?? true,
  })
  const [selectedProductIds, setSelectedProductIds] = useState<number[]>(() =>
    campaign ? [campaign.productId] : products.map((product) => product.id),
  )
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)

  const allSelected = products.length > 0 && selectedProductIds.length === products.length
  const fragranceGroups = Array.from(new Set(
    products.map((product) => (product.fragranceNotes || '').split(/[·,–-]/)[0]?.trim()).filter(Boolean),
  ))

  function toggleAll(checked: boolean) {
    setSelectedProductIds(checked ? products.map((product) => product.id) : [])
  }

  function toggleProduct(productId: number) {
    setSelectedProductIds((current) =>
      current.includes(productId) ? current.filter((id) => id !== productId) : [...current, productId],
    )
  }

  function toggleGroup(groupLabel: string) {
    const groupIds = products
      .filter((product) => (product.fragranceNotes || '').toLowerCase().includes(groupLabel.toLowerCase()))
      .map((product) => product.id)
    if (!groupIds.length) return
    const everyInGroup = groupIds.every((id) => selectedProductIds.includes(id))
    setSelectedProductIds((current) =>
      everyInGroup
        ? current.filter((id) => !groupIds.includes(id))
        : Array.from(new Set([...current, ...groupIds])),
    )
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); setError('')
    if (campaign) {
      if (!form.productId) { setError('Vui lòng chọn sản phẩm áp dụng chiến dịch.'); return }
    } else if (!selectedProductIds.length) {
      setError('Vui lòng chọn ít nhất 1 sản phẩm (hoặc tick Tất cả sản phẩm).'); return
    }
    if (form.discountValue <= 0) { setError('Giá trị giảm phải lớn hơn 0.'); return }
    if (form.discountType === 'PERCENTAGE' && form.discountValue > 100) { setError('Phần trăm giảm không được vượt quá 100.'); return }
    if (new Date(form.endAt) <= new Date(form.startAt)) { setError('Thời gian kết thúc phải sau thời gian bắt đầu.'); return }
    setSaving(true)
    try {
      if (campaign) {
        const result = await backend.updateCampaign(campaign.id, tenantId, {
          productId: form.productId,
          name: form.name.trim(),
          discountType: form.discountType,
          discountValue: Number(form.discountValue),
          startAt: form.startAt,
          endAt: form.endAt,
          active: form.active,
        })
        saved(result, 'Đã cập nhật chiến dịch.')
      } else {
        const created = await Promise.all(
          selectedProductIds.map((productId) =>
            backend.createCampaign({
              ...form,
              productId,
              name: form.name.trim(),
              discountValue: Number(form.discountValue),
            }),
          ),
        )
        saved(created[0], `Đã tạo chiến dịch áp dụng cho ${created.length} sản phẩm.`)
      }
      close()
    } catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  return <Modal title={campaign ? `Cập nhật chiến dịch: ${campaign.name}` : 'Tạo chiến dịch'} close={close}><form className="form-grid" onSubmit={submit}>
    <label className="full">Tên chiến dịch<input required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
    {campaign ? (
      <label className="full">Sản phẩm áp dụng<select required value={form.productId} onChange={(event) => setForm({ ...form, productId: Number(event.target.value) })}><option value={0}>Chọn sản phẩm</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name} ({new Intl.NumberFormat('vi-VN').format(product.defaultPrice)} đ)</option>)}</select></label>
    ) : (
      <fieldset className="full">
        <legend>Sản phẩm áp dụng ({selectedProductIds.length}/{products.length} đã chọn)</legend>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center', marginBottom: 10 }}>
          <label className="switch" style={{ margin: 0 }}>
            <input type="checkbox" checked={allSelected} onChange={(event) => toggleAll(event.target.checked)} />
            <strong>Tất cả sản phẩm ({products.length})</strong>
          </label>
          {fragranceGroups.slice(0, 6).map((group) => (
            <button key={group} type="button" style={{ fontSize: 11, padding: '4px 8px', borderRadius: 999, border: '1px solid #d7d6cf', background: '#faf9f5' }} onClick={() => toggleGroup(group)}>
              Nhóm: {group}
            </button>
          ))}
        </div>
        <div className="check-grid permissions">
          {products.map((product) => (
            <label key={product.id}>
              <input type="checkbox" checked={selectedProductIds.includes(product.id)} onChange={() => toggleProduct(product.id)} />
              <span>{product.name} ({new Intl.NumberFormat('vi-VN').format(product.defaultPrice)} đ)</span>
            </label>
          ))}
        </div>
      </fieldset>
    )}
    <label>Loại giảm<select value={form.discountType} onChange={(event) => setForm({ ...form, discountType: event.target.value as CampaignInput['discountType'] })}><option value="PERCENTAGE">Phần trăm (%)</option><option value="FIXED_AMOUNT">Số tiền cố định (VND)</option></select></label>
    <label>Giá trị giảm<input required type="number" min="1" step="1" value={form.discountValue} onChange={(event) => setForm({ ...form, discountValue: Number(event.target.value) })} /></label>
    <label className="switch full"><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} />Đang hoạt động</label>
    <label>Bắt đầu<input required type="datetime-local" value={form.startAt} onChange={(event) => setForm({ ...form, startAt: event.target.value })} /></label>
    <label>Kết thúc<input required type="datetime-local" value={form.endAt} onChange={(event) => setForm({ ...form, endAt: event.target.value })} /></label>
    {error && <div className="error-box full">{error}</div>}<div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu chiến dịch'}</button></div>
  </form></Modal>
}

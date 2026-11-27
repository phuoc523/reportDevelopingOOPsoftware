import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { backend } from '../api/scentStation'
import type { Bottle, BottleInstallation, Location, Machine, Principal, Product, Slot, SlotInput } from '../api/types'
import { Modal } from './IdentityForms'

const errorText = (error: unknown) => error instanceof ApiError || error instanceof Error ? error.message : 'Không thể hoàn thành yêu cầu.'
const optional = (value: string) => value.trim() || null

export function LocationForm({ tenantId, close, saved }: { tenantId: number; close: () => void; saved: (location: Location) => void }) {
  const [form, setForm] = useState({ locationId: '', name: '', address: '' }); const [error, setError] = useState(''); const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); setError(''); try { saved(await backend.createLocation({ ...form, tenantId: String(tenantId), address: optional(form.address) })); close() } catch (reason) { setError(errorText(reason)) } finally { setSaving(false) } }
  return <Modal title="Tạo địa điểm" close={close}><form className="form-grid" onSubmit={submit}><label>Mã địa điểm<input required maxLength={100} value={form.locationId} onChange={(event) => setForm({ ...form, locationId: event.target.value })} /></label><label>Tên địa điểm<input required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label><label className="full">Địa chỉ<input value={form.address} onChange={(event) => setForm({ ...form, address: event.target.value })} /></label>{error && <div className="error-box full">{error}</div>}<div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>Tạo địa điểm</button></div></form></Modal>
}

export function MachineForm({ machine, tenantId, locations, close, saved }: { machine?: Machine; tenantId: number; locations: Location[]; close: () => void; saved: (machine: Machine) => void }) {
  const [form, setForm] = useState({ serialNumber: machine?.serialNumber ?? '', deviceIdentity: machine?.deviceIdentity ?? '', locationId: machine?.locationId ?? locations[0]?.locationId ?? '', name: machine?.name ?? '' })
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); setError(''); try { const base = { tenantId: String(tenantId), locationId: form.locationId, name: form.name.trim() }; saved(machine ? await backend.updateMachine(machine.id, base) : await backend.createMachine({ ...base, serialNumber: form.serialNumber.trim(), deviceIdentity: form.deviceIdentity.trim() })); close() } catch (reason) { setError(errorText(reason)) } finally { setSaving(false) } }
  return <Modal title={machine ? `Cập nhật máy: ${machine.name}` : 'Đăng ký máy'} close={close}><form className="form-grid" onSubmit={submit}>{!machine && <><label>Số sê-ri<input required maxLength={100} value={form.serialNumber} onChange={(event) => setForm({ ...form, serialNumber: event.target.value })} /></label><label>Mã định danh thiết bị<input required maxLength={150} value={form.deviceIdentity} onChange={(event) => setForm({ ...form, deviceIdentity: event.target.value })} /></label></>}<label>Tên máy<input required maxLength={150} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label><label>Địa điểm<select required value={form.locationId} onChange={(event) => setForm({ ...form, locationId: event.target.value })}><option value="">Chọn địa điểm</option>{locations.map((location) => <option key={location.locationId} value={location.locationId}>{location.name} ({location.locationId})</option>)}</select></label>{error && <div className="error-box full">{error}</div>}<div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving || !locations.length}>{saving ? 'Đang lưu…' : 'Lưu máy'}</button></div></form></Modal>
}

export function SlotForm({
  slot,
  machineId,
  tenantId,
  principal,
  products,
  bottles,
  activeInstallation,
  close,
  saved,
}: {
  slot?: Slot
  machineId: number
  tenantId: number
  principal: Principal
  products: Product[]
  bottles: Bottle[]
  activeInstallation?: BottleInstallation | null
  close: () => void
  saved: (slot: Slot, message?: string) => void
}) {
  const canSyncInventory = principal.roles.includes('PLATFORM_SUPER_ADMIN') || principal.grants.includes('INVENTORY_MANAGE')
  const installedBottle = useMemo(
    () => (activeInstallation ? bottles.find((bottle) => bottle.id === activeInstallation.bottleId) ?? null : null),
    [activeInstallation, bottles],
  )

  const [form, setForm] = useState({
    slotNumber: String(slot?.slotNumber ?? ''),
    slotCode: slot?.slotCode ?? '',
    productId: slot?.productId ?? '',
    cartridgeId: slot?.cartridgeId ?? '',
    bottleId: activeInstallation ? String(activeInstallation.bottleId) : '',
    enabled: slot?.enabled ?? true,
    stockThreshold: String(slot?.stockThreshold ?? 50),
    calibratedSprayDurationMs: String(slot?.calibratedSprayDurationMs ?? 500),
    doseMl: String(slot?.doseMl ?? installedBottle?.calibratedConsumptionPerSpray ?? 0.12),
    price: String(slot?.price ?? 0),
  })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const matchingBottles = useMemo(() => {
    if (!form.productId) return []
    return bottles.filter((bottle) => {
      if (String(bottle.productId) !== String(form.productId)) return false
      if (installedBottle && bottle.id === installedBottle.id) return true
      return bottle.status.toUpperCase() === 'AVAILABLE' && bottle.currentQuantity > 0
    })
  }, [bottles, form.productId, installedBottle])

  const selectedBottle = useMemo(
    () => (form.bottleId ? bottles.find((bottle) => String(bottle.id) === form.bottleId) ?? null : null),
    [bottles, form.bottleId],
  )

  const doseValue = Number(form.doseMl)
  const estimatedSprays = selectedBottle && Number.isFinite(doseValue) && doseValue > 0
    ? Math.floor(selectedBottle.currentQuantity / doseValue)
    : null

  function handleProductChange(nextProductId: string) {
    if (!nextProductId) {
      setForm((current) => ({ ...current, productId: '', cartridgeId: '', bottleId: '' }))
      return
    }
    const product = products.find((item) => String(item.id) === nextProductId)
    const candidateBottles = bottles.filter((bottle) => {
      if (String(bottle.productId) !== nextProductId) return false
      if (installedBottle && bottle.id === installedBottle.id) return true
      return bottle.status.toUpperCase() === 'AVAILABLE' && bottle.currentQuantity > 0
    })
    const preferredBottle = (installedBottle && String(installedBottle.productId) === nextProductId ? installedBottle : candidateBottles[0]) ?? null
    setForm((current) => ({
      ...current,
      productId: nextProductId,
      bottleId: preferredBottle ? String(preferredBottle.id) : '',
      cartridgeId: preferredBottle
        ? `CART-${preferredBottle.bottleCode}`
        : (current.productId === nextProductId && current.cartridgeId ? current.cartridgeId : `CART-${nextProductId}-${current.slotCode || slot?.slotCode || 'SLOT'}`),
      price: product ? String(product.defaultPrice) : current.price,
    }))
  }

  function handleBottleChange(nextBottleId: string) {
    const bottle = bottles.find((item) => String(item.id) === nextBottleId)
    setForm((current) => ({
      ...current,
      bottleId: nextBottleId,
      cartridgeId: bottle ? `CART-${bottle.bottleCode}` : current.cartridgeId,
    }))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (Boolean(form.productId.trim()) !== Boolean(form.cartridgeId.trim())) {
      setError('Sản phẩm và mã hộp hương phải cùng có giá trị hoặc cùng để trống.')
      return
    }
    const parsedDose = form.doseMl ? Number(form.doseMl) : null
    if (parsedDose !== null && (!Number.isFinite(parsedDose) || parsedDose <= 0)) {
      setError('Lưu lượng mỗi lượt xịt (ml) phải lớn hơn 0.')
      return
    }
    const data: SlotInput = {
      productId: optional(form.productId),
      cartridgeId: optional(form.cartridgeId),
      enabled: form.enabled,
      stockThreshold: Number(form.stockThreshold),
      calibratedSprayDurationMs: Number(form.calibratedSprayDurationMs),
      doseMl: parsedDose,
      price: Number(form.price),
    }

    setSaving(true)
    try {
      const savedSlot = slot
        ? await backend.updateSlot(machineId, slot.id, data)
        : await backend.createSlot(machineId, { ...data, slotNumber: Number(form.slotNumber), slotCode: form.slotCode.trim() })

      const syncNotes: string[] = []
      if (canSyncInventory) {
        const oldBottleId = activeInstallation?.bottleId ?? null
        const nextBottleId = form.bottleId ? Number(form.bottleId) : null
        const targetBottle = nextBottleId ? bottles.find((b) => b.id === nextBottleId) ?? null : null

        if (oldBottleId && !nextBottleId) {
          await backend.removeBottle({
            tenantId,
            bottleId: oldBottleId,
            actorId: principal.id,
            reason: `Tháo chai khi cập nhật đầu phun ${savedSlot.slotCode}`,
            measuredQuantityAfter: installedBottle?.currentQuantity ?? 0,
          })
          syncNotes.push('đã tháo chai cũ về kho')
        } else if (oldBottleId && nextBottleId && oldBottleId !== nextBottleId) {
          await backend.replaceBottle({
            tenantId,
            oldBottleId,
            newBottleId: nextBottleId,
            actorId: principal.id,
            reason: `Đổi nước hoa / chai trên đầu phun ${savedSlot.slotCode}`,
            oldBottleMeasuredQuantityAfter: installedBottle?.currentQuantity ?? 0,
            newBottleMeasuredQuantityBefore: targetBottle?.currentQuantity ?? 0,
          })
          syncNotes.push('đã thay chai trong tồn kho')
        } else if (!oldBottleId && nextBottleId) {
          await backend.installBottle({
            tenantId,
            bottleId: nextBottleId,
            machineId,
            slotId: savedSlot.id,
            actorId: principal.id,
            measuredQuantityBefore: targetBottle?.currentQuantity ?? 0,
          })
          syncNotes.push('đã lắp chai vào đầu phun')
        }

        if (nextBottleId && parsedDose && parsedDose > 0) {
          await backend.updateSprayCalibration({
            tenantId,
            bottleId: nextBottleId,
            consumptionPerSpray: parsedDose,
          })
          syncNotes.push(`đồng bộ lưu lượng ${parsedDose} ml/lượt`)
        }
      }

      const message = syncNotes.length
        ? `Đã lưu đầu phun ${savedSlot.slotCode} (${syncNotes.join(', ')}).`
        : `Đã lưu đầu phun ${savedSlot.slotCode}.`
      saved(savedSlot, message)
      close()
    } catch (reason) {
      setError(errorText(reason))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={slot ? `Cập nhật đầu phun: ${slot.slotCode}` : 'Tạo đầu phun'} close={close}>
      <form className="form-grid" onSubmit={submit}>
        {!slot && (
          <>
            <label>Số đầu phun<input required type="number" min="1" value={form.slotNumber} onChange={(event) => setForm({ ...form, slotNumber: event.target.value })} /></label>
            <label>Mã đầu phun<input required maxLength={50} value={form.slotCode} onChange={(event) => setForm({ ...form, slotCode: event.target.value })} /></label>
          </>
        )}

        <label className="full">
          Sản phẩm nước hoa trên đầu phun
          <select value={form.productId} onChange={(event) => handleProductChange(event.target.value)}>
            <option value="">— Để trống (chưa gắn nước hoa) —</option>
            {products.map((product) => (
              <option key={product.id} value={String(product.id)}>
                {product.name} (#{product.id} · {new Intl.NumberFormat('vi-VN').format(product.defaultPrice)} đ)
              </option>
            ))}
          </select>
        </label>

        {canSyncInventory && (
          <label className="full">
            Chai nước hoa đồng bộ tồn kho
            <select value={form.bottleId} disabled={!form.productId} onChange={(event) => handleBottleChange(event.target.value)}>
              <option value="">{form.productId ? (matchingBottles.length ? '— Không gắn chai tồn kho / tháo chai cũ —' : '— Chưa có chai khả dụng cho sản phẩm này trong kho —') : '— Chọn sản phẩm trước —'}</option>
              {matchingBottles.map((bottle) => (
                <option key={bottle.id} value={String(bottle.id)}>
                  {bottle.bottleCode} · Còn {bottle.currentQuantity} / {bottle.initialQuantity} ml{installedBottle?.id === bottle.id ? ' (Đang lắp trên đầu phun này)' : ' (Sẵn sàng trong kho)'}
                </option>
              ))}
            </select>
          </label>
        )}

        <label>Mã hộp hương (Cartridge)<input value={form.cartridgeId} onChange={(event) => setForm({ ...form, cartridgeId: event.target.value })} placeholder="VD: CART-SS-91001" /></label>
        <label>Giá xịt thử (VND)<input required type="number" min="0" step="1" value={form.price} onChange={(event) => setForm({ ...form, price: event.target.value })} /></label>
        <label>Lưu lượng mỗi lượt xịt (ml)<input required type="number" min="0.01" max="5" step="0.01" value={form.doseMl} onChange={(event) => setForm({ ...form, doseMl: event.target.value })} /></label>
        <label>Thời gian phun (ms)<input required type="number" min="50" max="5000" step="10" value={form.calibratedSprayDurationMs} onChange={(event) => setForm({ ...form, calibratedSprayDurationMs: event.target.value })} /></label>
        <label>Ngưỡng cảnh báo tồn (lượt)<input required type="number" min="0" step="1" value={form.stockThreshold} onChange={(event) => setForm({ ...form, stockThreshold: event.target.value })} /></label>
        <label className="switch"><input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} />Đầu phun đang bật</label>

        {selectedBottle && (
          <div className="slot-sync-box full">
            <strong>Đồng bộ tồn kho & lưu lượng:</strong> Chai <strong>{selectedBottle.bottleCode}</strong> hiện còn <strong>{selectedBottle.currentQuantity} ml</strong>.
            {estimatedSprays !== null && <> Với lưu lượng <strong>{form.doseMl} ml/lượt</strong>, ước tính còn khoảng <strong>{estimatedSprays} lượt xịt</strong>. Khi khách xịt thành công, hệ thống sẽ tự động trừ đúng <strong>{form.doseMl} ml</strong> vào chai này.</>}
          </div>
        )}

        {error && <div className="error-box full">{error}</div>}
        <div className="modal-actions full">
          <button type="button" onClick={close}>Hủy</button>
          <button className="primary" disabled={saving}>{saving ? 'Đang lưu & đồng bộ…' : 'Lưu đầu phun'}</button>
        </div>
      </form>
    </Modal>
  )
}

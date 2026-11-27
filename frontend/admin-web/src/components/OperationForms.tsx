import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { backend } from '../api/scentStation'
import type { Account, Bottle, BottleInstallation, InventoryBatch, Location, Machine, MaintenanceHistory, MaintenanceTicket, Principal, Product, Slot } from '../api/types'
import { Modal } from './IdentityForms'

const errorText = (error: unknown) => error instanceof ApiError || error instanceof Error ? error.message : 'Không thể hoàn thành yêu cầu.'
const numberOrNull = (value: string) => {
  if (!value.trim()) return null
  const num = Number(value)
  return Number.isInteger(num) && num > 0 ? num : null
}

const MAINTENANCE_CATEGORIES = [
  ['NOZZLE_CLOG', 'Nghẹt / tắc đầu phun'],
  ['PUMP_FAILURE', 'Lỗi bơm / van phun'],
  ['SENSOR_FAULT', 'Lỗi cảm biến'],
  ['LEAKAGE', 'Rò rỉ dung dịch'],
  ['LOW_STOCK', 'Tồn kho thấp / cần thay chai'],
  ['CONNECTIVITY', 'Mất kết nối mạng / MQTT'],
  ['DOOR_OPEN', 'Cửa máy mở / an ninh'],
  ['ROUTINE_MAINTENANCE', 'Bảo trì định kỳ'],
  ['OTHER', 'Khác (tự nhập)'],
] as const

export type InventoryWorkflowPrefill = {
  mode?: 'refill' | 'install' | 'replace'
  machineId?: number
  slotId?: number
  oldBottleId?: number
}

function localTodayIso(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

function generateBatchCode(productId?: string): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  return productId ? `BATCH-SP${productId}-${stamp}` : `BATCH-${stamp}`
}

async function syncProductInventory(
  tenantId: number,
  productId: number,
  stockDelta: number,
  bottleVolumeMl: number | null,
  canManageCatalog: boolean,
): Promise<boolean> {
  if (!canManageCatalog || !productId) return false
  try {
    const products = await backend.products(tenantId)
    const target = products.find((item) => item.id === productId)
    if (!target) return false
    const nextStock = Math.max(0, Number(target.bottleStock ?? 0) + stockDelta)
    const nextVolume = bottleVolumeMl && bottleVolumeMl > 0 ? bottleVolumeMl : target.bottleVolumeMl
    await backend.updateProduct(target.id, {
      name: target.name,
      description: target.description,
      fragranceNotes: target.fragranceNotes,
      imageUrl: target.imageUrl,
      defaultPrice: target.defaultPrice,
      bottlePrice: target.bottlePrice,
      bottleVolumeMl: nextVolume,
      bottleStock: nextStock,
      available: target.available,
    })
    return true
  } catch {
    return false
  }
}

export function InventoryForm({ tenantId, principal, bottles, initialProductId, close, saved }: { tenantId: number; principal: Principal; batches?: InventoryBatch[]; bottles: Bottle[]; initialProductId?: number; close: () => void; saved: (message?: string) => void }) {
  const canManage = principal.grants.includes('INVENTORY_MANAGE'); const canAdjust = principal.grants.includes('INVENTORY_ADJUST')
  const canNotify = principal.roles.includes('PLATFORM_SUPER_ADMIN') || principal.grants.includes('NOTIFICATION_CONFIGURE')
  const canManageCatalog = principal.roles.includes('PLATFORM_SUPER_ADMIN') || principal.grants.includes('CATALOG_MANAGE')
  const [mode, setMode] = useState<'batch' | 'adjust'>(canManage ? 'batch' : 'adjust')
  const [products, setProducts] = useState<Product[]>([])
  const [form, setForm] = useState(() => ({
    productId: initialProductId ? String(initialProductId) : '',
    batchCode: generateBatchCode(initialProductId ? String(initialProductId) : undefined),
    receivedQuantity: '1',
    bottleVolumeMl: '100',
    receivedAt: localTodayIso(),
    expiryDate: '',
    bottleId: '',
    quantity: '',
    reason: '',
  }))
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)

  useEffect(() => {
    let active = true
    void backend.products(tenantId).then((list) => {
      if (!active) return
      setProducts(list)
      if (initialProductId) {
        const matched = list.find((item) => item.id === initialProductId)
        if (matched?.bottleVolumeMl) {
          setForm((current) => ({ ...current, bottleVolumeMl: String(matched.bottleVolumeMl) }))
        }
      }
    }).catch(() => undefined)
    return () => { active = false }
  }, [tenantId, initialProductId])

  const productNameOf = (productId?: number | string) => {
    const found = products.find((item) => String(item.id) === String(productId))
    return found ? found.name : `Sản phẩm #${productId}`
  }

  const selectedProduct = products.find((item) => String(item.id) === form.productId) ?? null
  const currentProductTotalMl = form.productId
    ? Math.round(bottles
        .filter((b) => String(b.productId) === form.productId && !['DAMAGED', 'EXPIRED'].includes(b.status.toUpperCase()))
        .reduce((sum, b) => sum + Number(b.currentQuantity ?? 0), 0) * 1000) / 1000
    : 0
  const qtyNum = Number(form.receivedQuantity)
  const volNum = Number(form.bottleVolumeMl)
  const totalIncomingMl = Number.isFinite(qtyNum) && qtyNum > 0 && Number.isFinite(volNum) && volNum > 0
    ? Math.round(qtyNum * volNum * 1000) / 1000
    : 0

  function handleSelectProduct(nextProductId: string) {
    const prod = products.find((item) => String(item.id) === nextProductId)
    setForm((current) => ({
      ...current,
      productId: nextProductId,
      batchCode: generateBatchCode(nextProductId),
      bottleVolumeMl: prod?.bottleVolumeMl ? String(prod.bottleVolumeMl) : current.bottleVolumeMl,
      receivedAt: localTodayIso(),
    }))
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError('')
    try {
      let successMessage = 'Đã hoàn thành nghiệp vụ kho.'
      if (mode === 'batch') {
        const autoReceivedAt = localTodayIso()
        const autoBatchCode = form.batchCode.trim() || generateBatchCode(form.productId)
        const receivedQty = Number(form.receivedQuantity)
        const perBottleMl = Number(form.bottleVolumeMl)
        if (!Number.isFinite(perBottleMl) || perBottleMl <= 0) {
          throw new Error('Dung tích mỗi chai (ml) phải lớn hơn 0.')
        }
        const addedVolumeMl = Math.round(receivedQty * perBottleMl * 1000) / 1000

        const createdBatch = await backend.createBatch({
          tenantId,
          productId: Number(form.productId),
          batchCode: autoBatchCode,
          receivedQuantity: receivedQty,
          receivedAt: autoReceivedAt,
          expiryDate: form.expiryDate || null,
        })
        const pName = productNameOf(form.productId)

        const autoBottleCode = `BOT-${createdBatch.batchCode}`
        const createdBottle = await backend.createBottle({
          tenantId,
          productId: Number(form.productId),
          batchId: createdBatch.id,
          bottleCode: autoBottleCode,
          initialQuantity: addedVolumeMl,
        })

        const synced = await syncProductInventory(tenantId, Number(form.productId), receivedQty, perBottleMl, canManageCatalog)
        successMessage = `Đã nhập lô "${createdBatch.batchCode}" (${pName}), tự tạo chai "${createdBottle.bottleCode}" (${addedVolumeMl} ml)${synced ? ' và đồng bộ lên mục Sản phẩm.' : '.'}`

        if (canNotify) {
          void backend.createNotification({
            tenantId: String(tenantId),
            title: `Nhập lô hàng mới: ${createdBatch.batchCode}`,
            content: `Đã nhập lô ${createdBatch.batchCode} cho sản phẩm ${pName} (${receivedQty} chai × ${perBottleMl} ml = +${addedVolumeMl} ml).`,
            notificationType: 'INVENTORY_BATCH_RECEIVED',
            priority: 'NORMAL',
            source: 'INVENTORY',
            sourceEventId: `batch-receive-${createdBatch.id}`,
            recipientIds: [String(principal.id)],
          }).catch(() => undefined)
        }
      }
      if (mode === 'adjust') {
        await backend.adjustInventory({ tenantId, bottleId: Number(form.bottleId), newQuantity: Number(form.quantity), actorId: principal.id, reason: form.reason.trim() })
        successMessage = 'Đã điều chỉnh dung tích thực tế trong kho và ghi nhận biến động.'
      }
      saved(successMessage); close()
    } catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  return <Modal title="Nghiệp vụ kho" close={close}><form className="form-grid" onSubmit={submit}><label className="full">Nghiệp vụ<select value={mode} onChange={(event) => setMode(event.target.value as typeof mode)}>{canManage && <option value="batch">Nhập lô hàng</option>}{canAdjust && <option value="adjust">Điều chỉnh dung tích thực tế</option>}</select></label>
    {mode === 'batch' && <>
      <label className="full">
        Sản phẩm nước hoa
        {products.length ? (
          <select required value={form.productId} onChange={(event) => handleSelectProduct(event.target.value)}>
            <option value="">— Chọn sản phẩm —</option>
            {products.map((product) => <option key={product.id} value={String(product.id)}>{product.name} (#{product.id} · {product.bottleVolumeMl ?? 100} ml/chai)</option>)}
          </select>
        ) : (
          <input required type="number" min="1" placeholder="Nhập mã sản phẩm" value={form.productId} onChange={(event) => handleSelectProduct(event.target.value)} />
        )}
      </label>
      <label>Mã lô hàng (tự động cấp)<input readOnly disabled value={form.batchCode} /></label>
      <label>Ngày nhận (tự động)<input readOnly disabled type="date" value={form.receivedAt} /></label>
      <label>Số lượng chai nhập<input required type="number" min="1" step="1" value={form.receivedQuantity} onChange={(event) => setForm({ ...form, receivedQuantity: event.target.value })} /></label>
      <label>Dung tích mỗi chai (ml)<input required type="number" min="1" step="1" value={form.bottleVolumeMl} onChange={(event) => setForm({ ...form, bottleVolumeMl: event.target.value })} /></label>
      <label className="full">Hạn sử dụng (nhập thủ công)<input type="date" min={form.receivedAt} value={form.expiryDate} onChange={(event) => setForm({ ...form, expiryDate: event.target.value })} /></label>
      {selectedProduct && totalIncomingMl > 0 && (
        <div className="slot-sync-box full">
          <strong>Quy tắc 1 Lô ↔ 1 Chai theo hạn dùng:</strong> Tạo lô <strong>{form.batchCode}</strong> kèm chai riêng <strong>BOT-{form.batchCode}</strong> ({totalIncomingMl} ml). Tổng dung tích kho của sản phẩm sẽ tăng từ <strong>{currentProductTotalMl} ml → {Math.round((currentProductTotalMl + totalIncomingMl) * 1000) / 1000} ml</strong>. Nếu sau này hủy lô này, các lô khác của cùng sản phẩm vẫn an toàn.
        </div>
      )}
    </>}
    {mode === 'adjust' && <>
      <label className="full">Chai nước hoa<select required value={form.bottleId} onChange={(event) => setForm({ ...form, bottleId: event.target.value })}><option value="">— Chọn chai —</option>{bottles.map((bottle) => <option key={bottle.id} value={bottle.id}>{bottle.bottleCode} · {productNameOf(bottle.productId)} · Còn {bottle.currentQuantity} ml</option>)}</select></label>
      <label className="full">Dung tích thực tế (ml)<input required type="number" min="0" step="0.001" value={form.quantity} onChange={(event) => setForm({ ...form, quantity: event.target.value })} /></label>
      <label className="full">Lý do điều chỉnh<textarea required maxLength={500} value={form.reason} onChange={(event) => setForm({ ...form, reason: event.target.value })} /></label>
    </>}
    {error && <div className="error-box full">{error}</div>}<div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang xử lý…' : 'Xác nhận'}</button></div>
  </form></Modal>
}

export function MaintenanceForm({ ticket, prefill, tenantId, principal, close, saved }: { ticket?: MaintenanceTicket; prefill?: { machineId?: number; title?: string; description?: string; category?: string; severity?: string }; tenantId: number; principal: Principal; close: () => void; saved: () => void }) {
  const initialCategory = ticket?.category ?? prefill?.category ?? 'NOZZLE_CLOG'
  const isPresetCategory = MAINTENANCE_CATEGORIES.some(([code]) => code === initialCategory && code !== 'OTHER')
  const [form, setForm] = useState({
    machineId: String(ticket?.machineId ?? prefill?.machineId ?? ''),
    locationId: String(ticket?.locationId ?? ''),
    slotId: '',
    bottleId: '',
    title: ticket?.title ?? prefill?.title ?? '',
    description: ticket?.description ?? prefill?.description ?? '',
    categoryPreset: isPresetCategory ? initialCategory : 'OTHER',
    customCategory: isPresetCategory ? '' : initialCategory,
    severity: ticket?.severity ?? prefill?.severity ?? 'MODERATE',
    priority: ticket?.priority ?? 'MEDIUM',
    dueAt: ticket?.dueAt ? ticket.dueAt.slice(0, 16) : '',
    critical: ticket?.critical ?? false,
  })
  const [machines, setMachines] = useState<Machine[]>([])
  const [locations, setLocations] = useState<Location[]>([])
  const [slots, setSlots] = useState<Slot[]>([])
  const [bottles, setBottles] = useState<Bottle[]>([])
  const [installations, setInstallations] = useState<BottleInstallation[]>([])
  const [loadingOptions, setLoadingOptions] = useState(!ticket)
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (ticket) return
    let active = true
    setLoadingOptions(true)
    void Promise.all([
      backend.machines(tenantId).catch(() => []),
      backend.locations(tenantId).catch(() => []),
      backend.bottles(tenantId).catch(() => []),
    ]).then(([machineList, locationList, bottleList]) => {
      if (!active) return
      setMachines(machineList)
      setLocations(locationList)
      setBottles(bottleList)
      setForm((current) => {
        const pickedMachineId = current.machineId || (machineList[0] ? String(machineList[0].id) : '')
        const pickedMachine = machineList.find((item) => String(item.id) === pickedMachineId)
        const numericLoc = pickedMachine && /^\d+$/.test(pickedMachine.locationId) ? pickedMachine.locationId : current.locationId
        return { ...current, machineId: pickedMachineId, locationId: numericLoc }
      })
    }).finally(() => { if (active) setLoadingOptions(false) })
    return () => { active = false }
  }, [tenantId, ticket])

  useEffect(() => {
    if (ticket || !form.machineId) { setSlots([]); setInstallations([]); return }
    let active = true
    const mid = Number(form.machineId)
    void Promise.all([
      backend.slots(mid).catch(() => []),
      backend.installations(tenantId, mid).catch(() => []),
    ]).then(([slotList, instList]) => {
      if (!active) return
      setSlots(slotList)
      setInstallations(instList)
    })
    return () => { active = false }
  }, [form.machineId, tenantId, ticket])

  function handleMachineChange(nextMachineId: string) {
    const picked = machines.find((item) => String(item.id) === nextMachineId)
    const numericLoc = picked && /^\d+$/.test(picked.locationId) ? picked.locationId : ''
    setForm((current) => ({ ...current, machineId: nextMachineId, locationId: numericLoc, slotId: '', bottleId: '' }))
  }

  function handleSlotChange(nextSlotId: string) {
    const inst = installations.find((item) => String(item.slotId) === nextSlotId)
    setForm((current) => ({ ...current, slotId: nextSlotId, bottleId: inst ? String(inst.bottleId) : current.bottleId }))
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError('')
    const finalCategory = form.categoryPreset === 'OTHER' ? form.customCategory.trim() : form.categoryPreset
    if (!finalCategory) { setError('Vui lòng chọn hoặc nhập danh mục bảo trì.'); setSaving(false); return }
    const common = { title: form.title.trim(), description: form.description.trim(), category: finalCategory, severity: form.severity, priority: form.priority, dueAt: form.dueAt || null }
    try {
      if (ticket) await backend.updateMaintenance(ticket.id, tenantId, common)
      else await backend.createMaintenance({ tenantId, machineId: Number(form.machineId), locationId: numberOrNull(form.locationId), slotId: numberOrNull(form.slotId), bottleId: numberOrNull(form.bottleId), ...common, reportedByActorId: principal.id, critical: form.critical })
      saved(); close()
    } catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }

  const numericLocations = locations.filter((loc) => /^\d+$/.test(loc.locationId))
  return <Modal title={ticket ? `Cập nhật phiếu #${ticket.id}` : 'Tạo phiếu bảo trì'} close={close}><form className="form-grid" onSubmit={submit}>
    {!ticket && (
      <>
        <label>
          Máy Kiosk
          <select required value={form.machineId} disabled={loadingOptions} onChange={(event) => handleMachineChange(event.target.value)}>
            <option value="">{loadingOptions ? 'Đang tải danh sách máy…' : '— Chọn máy Kiosk —'}</option>
            {machines.map((machine) => (
              <option key={machine.id} value={String(machine.id)}>
                #{machine.id} · {machine.name} ({machine.serialNumber})
              </option>
            ))}
          </select>
        </label>
        <label>
          Địa điểm (tùy chọn)
          <select value={form.locationId} disabled={loadingOptions} onChange={(event) => setForm({ ...form, locationId: event.target.value })}>
            <option value="">— Không chọn / tự động theo máy —</option>
            {numericLocations.map((location) => (
              <option key={location.locationId} value={location.locationId}>
                #{location.locationId} · {location.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Đầu phun liên quan (tùy chọn)
          <select value={form.slotId} disabled={!form.machineId} onChange={(event) => handleSlotChange(event.target.value)}>
            <option value="">— Toàn máy / không chỉ định đầu phun —</option>
            {slots.map((slot) => (
              <option key={slot.id} value={String(slot.id)}>
                Đầu phun #{slot.slotNumber} · {slot.slotCode} {slot.productId ? `(SP #${slot.productId})` : '(Trống)'}
              </option>
            ))}
          </select>
        </label>
        <label>
          Chai nước hoa liên quan (tùy chọn)
          <select value={form.bottleId} disabled={loadingOptions} onChange={(event) => setForm({ ...form, bottleId: event.target.value })}>
            <option value="">— Không chỉ định chai —</option>
            {bottles.map((bottle) => (
              <option key={bottle.id} value={String(bottle.id)}>
                {bottle.bottleCode} · Còn {bottle.currentQuantity} ml ({bottle.status})
              </option>
            ))}
          </select>
        </label>
      </>
    )}
    <label className="full">Tiêu đề<input required placeholder="VD: Đầu phun #1 không ra hương / kiểm tra van phun" value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} /></label>
    <label className="full">Mô tả chi tiết<textarea required placeholder="Mô tả hiện tượng, mã cảnh báo liên quan và bước cần kiểm tra…" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
    <label>
      Danh mục sự cố
      <select value={form.categoryPreset} onChange={(event) => setForm({ ...form, categoryPreset: event.target.value })}>
        {MAINTENANCE_CATEGORIES.map(([code, label]) => <option key={code} value={code}>{label}</option>)}
      </select>
    </label>
    {form.categoryPreset === 'OTHER' ? (
      <label>Nhập tên danh mục<input required value={form.customCategory} onChange={(event) => setForm({ ...form, customCategory: event.target.value })} placeholder="VD: CALIBRATION" /></label>
    ) : (
      <label>Hạn xử lý<input type="datetime-local" value={form.dueAt} onChange={(event) => setForm({ ...form, dueAt: event.target.value })} /></label>
    )}
    {form.categoryPreset === 'OTHER' && <label>Hạn xử lý<input type="datetime-local" value={form.dueAt} onChange={(event) => setForm({ ...form, dueAt: event.target.value })} /></label>}
    <label>Mức độ<select value={form.severity} onChange={(event) => setForm({ ...form, severity: event.target.value })}><option value="MINOR">Nhẹ</option><option value="MODERATE">Trung bình</option><option value="MAJOR">Nghiêm trọng</option><option value="CRITICAL">Khẩn cấp</option></select></label>
    <label>Ưu tiên<select value={form.priority} onChange={(event) => setForm({ ...form, priority: event.target.value })}><option value="LOW">Thấp</option><option value="MEDIUM">Trung bình</option><option value="HIGH">Cao</option><option value="URGENT">Khẩn cấp</option></select></label>
    {!ticket && <label className="switch full"><input type="checkbox" checked={form.critical} onChange={(event) => setForm({ ...form, critical: event.target.checked })} />Đánh dấu sự cố khẩn cấp</label>}
    {error && <div className="error-box full">{error}</div>}
    <div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu phiếu'}</button></div>
  </form></Modal>
}

export function InventoryWorkflowForm({ tenantId, principal, bottles, prefill, close, saved }: { tenantId: number; principal: Principal; bottles: Bottle[]; prefill?: InventoryWorkflowPrefill; close: () => void; saved: () => void }) {
  const [mode, setMode] = useState<'refill' | 'install' | 'replace'>(prefill?.mode ?? 'refill')
  const [form, setForm] = useState({
    bottleId: '',
    oldBottleId: prefill?.oldBottleId ? String(prefill.oldBottleId) : '',
    newBottleId: '',
    machineId: prefill?.machineId ? String(prefill.machineId) : '',
    slotId: prefill?.slotId ? String(prefill.slotId) : '',
    quantity: '',
    oldQuantity: '',
    reason: prefill?.mode === 'replace' ? 'Thay chai mới trên đầu phun Kiosk' : '',
  })
  const [machines, setMachines] = useState<Machine[]>([])
  const [slots, setSlots] = useState<Slot[]>([])
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)

  useEffect(() => {
    let active = true
    void backend.machines(tenantId).then((list) => { if (active) setMachines(list) }).catch(() => undefined)
    return () => { active = false }
  }, [tenantId])

  useEffect(() => {
    if (!form.machineId) { setSlots([]); return }
    let active = true
    void backend.slots(Number(form.machineId)).then((list) => { if (active) setSlots(list) }).catch(() => undefined)
    return () => { active = false }
  }, [form.machineId])

  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setSaving(true)
    try {
      if (mode === 'refill') await backend.refillBottle({ tenantId, bottleId: Number(form.bottleId), refillQuantity: Number(form.quantity), actorId: principal.id, reason: form.reason.trim() })
      if (mode === 'install') await backend.installBottle({ tenantId, bottleId: Number(form.bottleId), machineId: Number(form.machineId), slotId: Number(form.slotId), actorId: principal.id, measuredQuantityBefore: Number(form.quantity) })
      if (mode === 'replace') await backend.replaceBottle({ tenantId, oldBottleId: Number(form.oldBottleId), newBottleId: Number(form.newBottleId), actorId: principal.id, reason: form.reason.trim(), oldBottleMeasuredQuantityAfter: Number(form.oldQuantity), newBottleMeasuredQuantityBefore: Number(form.quantity) })
      saved(); close()
    } catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  const bottleOptions = bottles.map((bottle) => <option key={bottle.id} value={bottle.id}>{bottle.bottleCode} · SP #{bottle.productId} · {bottle.currentQuantity} ml · {bottle.status}</option>)
  const availableBottleOptions = bottles.filter((bottle) => bottle.status.toUpperCase() === 'AVAILABLE').map((bottle) => <option key={bottle.id} value={bottle.id}>{bottle.bottleCode} · SP #{bottle.productId} · {bottle.currentQuantity} ml</option>)
  return <Modal title="Nạp thêm, lắp hoặc thay chai" close={close}><form className="form-grid" onSubmit={submit}>
    <label className="full">Nghiệp vụ<select value={mode} onChange={(event) => setMode(event.target.value as typeof mode)}><option value="refill">Nạp thêm dung dịch vào chai</option><option value="install">Lắp chai vào máy/đầu phun</option><option value="replace">Thay chai cũ bằng chai mới</option></select></label>
    {mode === 'refill' && <label className="full">Chai cần nạp thêm<select required value={form.bottleId} onChange={(event) => setForm({ ...form, bottleId: event.target.value })}><option value="">Chọn chai</option>{bottleOptions}</select></label>}
    {mode === 'install' && (
      <>
        <label className="full">Chai sẵn sàng trong kho<select required value={form.bottleId} onChange={(event) => { const b = bottles.find((item) => String(item.id) === event.target.value); setForm({ ...form, bottleId: event.target.value, quantity: b ? String(b.currentQuantity) : form.quantity }) }}><option value="">Chọn chai khả dụng</option>{availableBottleOptions}</select></label>
        <label>Máy Kiosk<select required value={form.machineId} onChange={(event) => setForm({ ...form, machineId: event.target.value, slotId: '' })}><option value="">Chọn máy Kiosk</option>{machines.map((m) => <option key={m.id} value={m.id}>#{m.id} · {m.name}</option>)}</select></label>
        <label>Đầu phun (Slot)<select required value={form.slotId} disabled={!form.machineId} onChange={(event) => setForm({ ...form, slotId: event.target.value })}><option value="">Chọn đầu phun</option>{slots.map((s) => <option key={s.id} value={s.id}>Đầu phun #{s.slotNumber} · {s.slotCode} {s.productId ? `(SP #${s.productId})` : '(Trống)'}</option>)}</select></label>
      </>
    )}
    {mode === 'replace' && (
      <>
        <label>Chai cũ đang lắp<select required value={form.oldBottleId} onChange={(event) => { const b = bottles.find((item) => String(item.id) === event.target.value); setForm({ ...form, oldBottleId: event.target.value, oldQuantity: b ? String(b.currentQuantity) : form.oldQuantity }) }}><option value="">Chọn chai cũ</option>{bottleOptions}</select></label>
        <label>Chai mới trong kho<select required value={form.newBottleId} onChange={(event) => { const b = bottles.find((item) => String(item.id) === event.target.value); setForm({ ...form, newBottleId: event.target.value, quantity: b ? String(b.currentQuantity) : form.quantity }) }}><option value="">Chọn chai mới</option>{availableBottleOptions}</select></label>
        <label>Lượng còn lại chai cũ (ml)<input required min="0" step="0.001" type="number" value={form.oldQuantity} onChange={(event) => setForm({ ...form, oldQuantity: event.target.value })} /></label>
      </>
    )}
    <label>{mode === 'refill' ? 'Lượng nạp thêm (ml)' : 'Lượng đo chai mới trước khi lắp (ml)'}<input required min="0.001" step="0.001" type="number" value={form.quantity} onChange={(event) => setForm({ ...form, quantity: event.target.value })} /></label>
    {mode !== 'install' && <label className="full">Lý do<textarea required maxLength={500} value={form.reason} onChange={(event) => setForm({ ...form, reason: event.target.value })} /></label>}
    {error && <div className="error-box full">{error}</div>}<div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang xử lý…' : 'Xác nhận nghiệp vụ'}</button></div>
  </form></Modal>
}

export type InventoryDisposeTarget =
  | { kind: 'BATCH'; item: InventoryBatch }
  | { kind: 'BOTTLE'; item: Bottle }

export function InventoryDisposeForm({ target, principal, close, saved }: { target: InventoryDisposeTarget; principal: Principal; close: () => void; saved: (message: string) => void }) {
  const isBatch = target.kind === 'BATCH'
  const [targetStatus, setTargetStatus] = useState<'DAMAGED' | 'EXPIRED'>('DAMAGED')
  const [preset, setPreset] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const presets = isBatch
    ? ['Lô hàng bị lỗi chất lượng từ nhà cung cấp', 'Lô hàng bị hư hỏng trong quá trình vận chuyển', 'Lô hàng hết hạn / không đạt tiêu chuẩn bảo quản']
    : ['Chai bị nứt vỡ / hư hỏng', 'Vỏ chai bị trầy xước, móp méo không đạt thẩm mỹ', 'Rò rỉ dung dịch / hỏng đầu vòi', 'Chai đã hết hạn sử dụng']

  async function submit(event: FormEvent) {
    event.preventDefault()
    const finalReason = reason.trim()
    if (!finalReason) { setError('Vui lòng nhập lý do hủy.'); return }
    setSaving(true); setError('')
    try {
      const canManageCatalog = principal.roles.includes('PLATFORM_SUPER_ADMIN') || principal.grants.includes('CATALOG_MANAGE')
      if (isBatch) {
        await backend.disposeBatch(target.item.id, { tenantId: target.item.tenantId, actorId: principal.id, reason: finalReason })
        const synced = await syncProductInventory(target.item.tenantId, target.item.productId, -Math.max(0, target.item.receivedQuantity), null, canManageCatalog)
        saved(`Đã hủy lô hàng ${target.item.batchCode} và các chai thuộc lô${synced ? ' (đã đồng bộ lên sản phẩm)' : ''}.`)
      } else {
        await backend.disposeBottle(target.item.id, { tenantId: target.item.tenantId, actorId: principal.id, reason: finalReason, targetStatus })
        const synced = await syncProductInventory(target.item.tenantId, target.item.productId, -1, null, canManageCatalog)
        saved(`Đã hủy chai nước hoa ${target.item.bottleCode}${synced ? ' (đã đồng bộ lên sản phẩm)' : ''}.`)
      }
      close()
    } catch (err) {
      setError(errorText(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={isBatch ? `Hủy lô hàng ${target.item.batchCode}` : `Hủy / báo hư chai ${target.item.bottleCode}`} close={close}>
      <form className="form-grid" onSubmit={submit}>
        <p className="full" style={{ margin: 0, color: '#6e7872', lineHeight: 1.5 }}>
          {isBatch
            ? 'Hủy lô hàng sẽ chuyển trạng thái lô sang Đã hủy và tự động hủy (đưa tồn về 0 ml) toàn bộ các chai nước hoa còn lại thuộc lô này.'
            : 'Hủy chai nước hoa sẽ đưa dung tích còn lại về 0 ml, tự động tháo khỏi đầu phun (nếu đang lắp) và ghi nhận biến động kho.'}
        </p>
        {!isBatch && (
          <label className="full">
            Phân loại trạng thái sau khi hủy
            <select value={targetStatus} onChange={(event) => setTargetStatus(event.target.value as 'DAMAGED' | 'EXPIRED')}>
              <option value="DAMAGED">Hư hỏng / lỗi vỏ / trầy xước</option>
              <option value="EXPIRED">Hết hạn sử dụng</option>
            </select>
          </label>
        )}
        <label className="full">
          Chọn nhanh lý do phổ biến
          <select value={preset} onChange={(event) => { setPreset(event.target.value); if (event.target.value) setReason(event.target.value) }}>
            <option value="">-- Tự nhập lý do bên dưới --</option>
            {presets.map((text) => <option key={text} value={text}>{text}</option>)}
          </select>
        </label>
        <label className="full">
          Lý do hủy chi tiết
          <textarea required maxLength={500} placeholder="Ví dụ: Vỏ chai trầy xước nặng khi kiểm kho, lô hàng bị lỗi mùi..." value={reason} onChange={(event) => setReason(event.target.value)} />
        </label>
        {error && <div className="error-box full">{error}</div>}
        <div className="modal-actions full">
          <button type="button" onClick={close}>Đóng</button>
          <button className="primary" disabled={saving}>{saving ? 'Đang xử lý…' : isBatch ? 'Xác nhận hủy lô' : 'Xác nhận hủy chai'}</button>
        </div>
      </form>
    </Modal>
  )
}

export function MaintenanceWorkflowForm({ ticket, tenantId, principal, close, saved }: { ticket: MaintenanceTicket; tenantId: number; principal: Principal; close: () => void; saved: () => void }) {
  const [history, setHistory] = useState<MaintenanceHistory | null>(null); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [busy, setBusy] = useState(false)
  const [technicianId, setTechnicianId] = useState(String(ticket.assignedToActorId ?? ''))
  const [technicians, setTechnicians] = useState<Account[]>([])
  const [check, setCheck] = useState({ itemName: '', description: '', required: true })
  const [repair, setRepair] = useState({ diagnosticResult: '', correctiveAction: '', replacedPart: '', cost: '', repairResult: 'SUCCESS', note: '', evidenceUrl: '' })
  const load = async () => { try { setHistory(await backend.maintenanceHistory(ticket.id, tenantId)) } catch (reason) { setError(errorText(reason)) } }
  useEffect(() => {
    void load()
    const isPlatform = principal.roles.includes('PLATFORM_SUPER_ADMIN')
    if (isPlatform || principal.grants.includes('BRAND_USER_MANAGE')) {
      void backend.users(isPlatform, tenantId)
        .then((list) => setTechnicians(list.filter((acc) => acc.active && !acc.locked && acc.roles.includes('TECHNICIAN'))))
        .catch(() => undefined)
    }
  }, [])
  async function run(job: () => Promise<unknown>, message: string) { setBusy(true); setError(''); setNotice(''); try { await job(); await load(); setNotice(message); saved() } catch (reason) { setError(errorText(reason)) } finally { setBusy(false) } }
  const isAdmin = principal.roles.includes('PLATFORM_SUPER_ADMIN') || principal.roles.includes('BRAND_ADMIN')
  const isOps = principal.roles.includes('OPERATIONS_MANAGER')
  const isTech = principal.roles.includes('TECHNICIAN')
  const canApprove = (isAdmin || isOps) && !(history?.ticket.approved ?? ticket.approved) && ticket.status !== 'COMPLETED' && ticket.status !== 'CANCELLED'
  const canAssign = isOps
  const canTechAct = isTech
  const canAddChecklist = isOps || isTech
  const canTest = isTech && principal.grants.includes('DIAGNOSTIC_SPRAY')
  const approved = history?.ticket.approved ?? ticket.approved ?? false
  return <Modal title={`${isAdmin ? 'Theo dõi' : 'Quy trình'} bảo trì #${ticket.id}`} close={close}><div className="workflow-grid">
    <section>
      <h4>Thông tin & Phê duyệt phiếu</h4>
      <div className="workflow-item">
        <span><strong>{ticket.title}</strong><small>Máy #{ticket.machineId} · Trạng thái duyệt: {approved ? 'Đã chấp nhận' : 'Chờ chấp nhận'} · Kỹ thuật viên: {history?.ticket.assignedToActorId ?? ticket.assignedToActorId ?? 'Chưa phân công'}</small></span>
        {approved ? <span className="badge active">Đã chấp nhận</span> : canApprove ? <button className="primary" disabled={busy} onClick={() => void run(() => backend.approveMaintenance(ticket.id, tenantId, principal.id, 'Đã chấp nhận phiếu bảo trì'), 'Đã chấp nhận phiếu bảo trì.')}>Chấp nhận phiếu</button> : <span className="badge pending">Chờ chấp nhận</span>}
      </div>
    </section>
    {(canAssign || canTechAct) && <section>
      <h4>Phân công kỹ thuật viên</h4>
      <div className="inline-form">
        {canAssign && <>{technicians.length ? <select value={technicianId} onChange={(event) => setTechnicianId(event.target.value)}><option value="">— Chọn kỹ thuật viên —</option>{technicians.map((tech) => <option key={tech.id} value={String(tech.id)}>#{tech.id} · {tech.fullName} ({tech.username})</option>)}</select> : <input type="number" min="1" placeholder="Nhập ID kỹ thuật viên (VD: 9204)" value={technicianId} onChange={(event) => setTechnicianId(event.target.value)} />}<button className="primary" disabled={busy || !technicianId} onClick={() => void run(() => backend.assignMaintenance(ticket.id, tenantId, Number(technicianId), principal.id), 'Đã phân công kỹ thuật viên.')}>Phân công</button></>}
        {canTechAct && <button className="primary" disabled={busy} onClick={() => void run(() => backend.acknowledgeMaintenance(ticket.id, tenantId, principal.id), 'Đã xác nhận nhận việc.')}>Xác nhận nhận việc</button>}
      </div>
    </section>}
    <section>
      <h4>Checklist bắt buộc</h4>
      {history?.checklist.length ? history.checklist.map((item) => <div className="workflow-item" key={item.id}><span><strong>{item.itemName}</strong><small>{item.description || 'Không có mô tả'}</small></span>{item.completed ? <span className="badge success">Đã hoàn thành</span> : canTechAct ? <button disabled={busy} onClick={() => { const note = window.prompt('Kết quả kiểm tra:') ?? ''; if (note.trim()) void run(() => backend.completeChecklistItem(ticket.id, item.id, tenantId, principal.id, note.trim()), 'Đã hoàn thành checklist.') }}>Hoàn thành</button> : <span className="badge pending">Chưa hoàn thành</span>}</div>) : <small style={{ color: '#6e7872' }}>Chưa có bước kiểm tra nào.</small>}
      {canAddChecklist && <div className="inline-form"><input placeholder="Tên bước kiểm tra" value={check.itemName} onChange={(event) => setCheck({ ...check, itemName: event.target.value })} /><input placeholder="Mô tả" value={check.description} onChange={(event) => setCheck({ ...check, description: event.target.value })} /><button disabled={busy || !check.itemName.trim()} onClick={() => void run(() => backend.createChecklistItem(ticket.id, tenantId, check.itemName.trim(), check.description.trim(), check.required), 'Đã thêm bước checklist.')}>Thêm bước</button></div>}
    </section>
    <section>
      <h4>Kết quả sửa chữa</h4>
      {canTechAct && <div className="compact-form"><textarea placeholder="Kết quả chẩn đoán" value={repair.diagnosticResult} onChange={(event) => setRepair({ ...repair, diagnosticResult: event.target.value })} /><textarea placeholder="Hành động sửa chữa" value={repair.correctiveAction} onChange={(event) => setRepair({ ...repair, correctiveAction: event.target.value })} /><input placeholder="Linh kiện thay thế" value={repair.replacedPart} onChange={(event) => setRepair({ ...repair, replacedPart: event.target.value })} /><input type="number" min="0" step="1" placeholder="Chi phí sửa chữa (tùy chọn)" value={repair.cost} onChange={(event) => setRepair({ ...repair, cost: event.target.value })} /><input placeholder="Ghi chú thêm (tùy chọn)" value={repair.note} onChange={(event) => setRepair({ ...repair, note: event.target.value })} /><input type="url" placeholder="URL ảnh minh chứng" value={repair.evidenceUrl} onChange={(event) => setRepair({ ...repair, evidenceUrl: event.target.value })} /><select value={repair.repairResult} onChange={(event) => setRepair({ ...repair, repairResult: event.target.value })}><option value="SUCCESS">Hoàn thành</option><option value="PARTIAL">Hoàn thành một phần</option><option value="FAILED">Không thành công</option></select><button className="primary" disabled={busy || !repair.diagnosticResult.trim() || !repair.correctiveAction.trim()} onClick={() => void run(() => backend.createRepairLog(ticket.id, { tenantId, diagnosedByActorId: principal.id, diagnosticResult: repair.diagnosticResult.trim(), correctiveAction: repair.correctiveAction.trim(), replacedPart: repair.replacedPart.trim() || null, cost: repair.cost ? Number(repair.cost) : null, repairResult: repair.repairResult, note: repair.note.trim() || null, evidenceUrl: repair.evidenceUrl.trim() || null }), 'Đã ghi kết quả sửa chữa.')}>Ghi kết quả</button></div>}
      {history?.repairLogs.length ? history.repairLogs.map((log) => <div className="workflow-item" key={log.id}><span><strong>{log.repairResult}</strong><small>{log.diagnosticResult} · {log.correctiveAction}</small></span></div>) : <small style={{ color: '#6e7872' }}>Chưa có nhật ký sửa chữa.</small>}
    </section>
    <section>
      <h4>Test spray sau bảo trì</h4>
      {canTest && <button disabled={busy} onClick={() => void run(() => backend.requestPostMaintenanceTest(ticket.id, tenantId, principal.id), 'Đã gửi yêu cầu test spray.')}>Yêu cầu test spray có kiểm soát</button>}
      {history?.postMaintenanceTests.length ? history.postMaintenanceTests.map((test) => <div className="workflow-item" key={test.id}><span><strong>{test.status}</strong><small>{test.externalTestId} · {test.resultMessage || 'Đang chờ kết quả'}</small></span></div>) : <small style={{ color: '#6e7872' }}>Chưa có lần test spray nào.</small>}
    </section>
    {history?.alertLink && <section><h4>Cảnh báo liên kết</h4><p style={{ margin: 0, fontSize: 13 }}>{history.alertLink.alertType} · #{history.alertLink.sourceAlertId} · Máy {history.alertLink.machineId}</p></section>}
    {notice && <div className="notice">✓ {notice}</div>}
    {error && <div className="error-box">{error}</div>}<div className="modal-actions"><button onClick={close}>Đóng</button></div>
  </div></Modal>
}

import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { backend } from '../api/scentStation'
import type { Bottle, InventoryBatch, MaintenanceHistory, MaintenanceTicket, Principal } from '../api/types'
import { Modal } from './IdentityForms'

const errorText = (error: unknown) => error instanceof ApiError || error instanceof Error ? error.message : 'Không thể hoàn thành yêu cầu.'
const numberOrNull = (value: string) => value ? Number(value) : null

export function InventoryForm({ tenantId, principal, batches, bottles, close, saved }: { tenantId: number; principal: Principal; batches: InventoryBatch[]; bottles: Bottle[]; close: () => void; saved: () => void }) {
  const canManage = principal.grants.includes('INVENTORY_MANAGE'); const canAdjust = principal.grants.includes('INVENTORY_ADJUST')
  const [mode, setMode] = useState<'batch' | 'bottle' | 'adjust'>(canManage ? 'batch' : 'adjust')
  const [form, setForm] = useState({ productId: '', batchId: '', batchCode: '', receivedQuantity: '', receivedAt: new Date().toISOString().slice(0, 10), expiryDate: '', bottleId: '', bottleCode: '', quantity: '', reason: '' })
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError('')
    try {
      if (mode === 'batch') await backend.createBatch({ tenantId, productId: Number(form.productId), batchCode: form.batchCode.trim(), receivedQuantity: Number(form.receivedQuantity), receivedAt: form.receivedAt, expiryDate: form.expiryDate || null })
      if (mode === 'bottle') await backend.createBottle({ tenantId, productId: Number(form.productId), batchId: Number(form.batchId), bottleCode: form.bottleCode.trim(), initialQuantity: Number(form.quantity) })
      if (mode === 'adjust') await backend.adjustInventory({ tenantId, bottleId: Number(form.bottleId), newQuantity: Number(form.quantity), actorId: principal.id, reason: form.reason.trim() })
      saved(); close()
    } catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  return <Modal title="Nghiệp vụ tồn kho" close={close}><form className="form-grid" onSubmit={submit}><label className="full">Nghiệp vụ<select value={mode} onChange={(event) => setMode(event.target.value as typeof mode)}>{canManage && <><option value="batch">Nhập lô hàng</option><option value="bottle">Tạo chai nước hoa</option></>}{canAdjust && <option value="adjust">Điều chỉnh số lượng</option>}</select></label>
    {mode === 'batch' && <><label>Mã sản phẩm<input required type="number" min="1" value={form.productId} onChange={(event) => setForm({ ...form, productId: event.target.value })} /></label><label>Mã lô hàng<input required value={form.batchCode} onChange={(event) => setForm({ ...form, batchCode: event.target.value })} /></label><label>Số lượng nhận<input required type="number" min="1" value={form.receivedQuantity} onChange={(event) => setForm({ ...form, receivedQuantity: event.target.value })} /></label><label>Ngày nhận<input required type="date" max={new Date().toISOString().slice(0, 10)} value={form.receivedAt} onChange={(event) => setForm({ ...form, receivedAt: event.target.value })} /></label><label>Hạn dùng<input type="date" value={form.expiryDate} onChange={(event) => setForm({ ...form, expiryDate: event.target.value })} /></label></>}
    {mode === 'bottle' && <><label>Lô hàng<select required value={form.batchId} onChange={(event) => { const batch = batches.find((item) => item.id === Number(event.target.value)); setForm({ ...form, batchId: event.target.value, productId: String(batch?.productId ?? '') }) }}><option value="">Chọn lô hàng</option>{batches.filter((batch) => batch.status !== 'CANCELLED').map((batch) => <option key={batch.id} value={batch.id}>{batch.batchCode} · Sản phẩm {batch.productId}</option>)}</select></label><label>Mã chai<input required value={form.bottleCode} onChange={(event) => setForm({ ...form, bottleCode: event.target.value })} /></label><label>Số lượng ban đầu<input required type="number" min="0.001" step="0.001" value={form.quantity} onChange={(event) => setForm({ ...form, quantity: event.target.value })} /></label></>}
    {mode === 'adjust' && <><label>Chai nước hoa<select required value={form.bottleId} onChange={(event) => setForm({ ...form, bottleId: event.target.value })}><option value="">Chọn chai</option>{bottles.map((bottle) => <option key={bottle.id} value={bottle.id}>{bottle.bottleCode} · {bottle.currentQuantity}</option>)}</select></label><label>Số lượng thực tế<input required type="number" min="0" step="0.001" value={form.quantity} onChange={(event) => setForm({ ...form, quantity: event.target.value })} /></label><label className="full">Lý do<textarea required maxLength={500} value={form.reason} onChange={(event) => setForm({ ...form, reason: event.target.value })} /></label></>}
    {error && <div className="error-box full">{error}</div>}<div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang xử lý…' : 'Xác nhận'}</button></div>
  </form></Modal>
}

export function MaintenanceForm({ ticket, prefill, tenantId, principal, close, saved }: { ticket?: MaintenanceTicket; prefill?: { machineId?: number; title?: string; description?: string; category?: string; severity?: string }; tenantId: number; principal: Principal; close: () => void; saved: () => void }) {
  const [form, setForm] = useState({ machineId: String(ticket?.machineId ?? prefill?.machineId ?? ''), locationId: String(ticket?.locationId ?? ''), slotId: '', bottleId: '', title: ticket?.title ?? prefill?.title ?? '', description: ticket?.description ?? prefill?.description ?? '', category: ticket?.category ?? prefill?.category ?? '', severity: ticket?.severity ?? prefill?.severity ?? 'MODERATE', priority: ticket?.priority ?? 'MEDIUM', dueAt: ticket?.dueAt ? ticket.dueAt.slice(0, 16) : '', critical: ticket?.critical ?? false })
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError('')
    const common = { title: form.title.trim(), description: form.description.trim(), category: form.category.trim(), severity: form.severity, priority: form.priority, dueAt: form.dueAt || null }
    try { if (ticket) await backend.updateMaintenance(ticket.id, tenantId, common); else await backend.createMaintenance({ tenantId, machineId: Number(form.machineId), locationId: numberOrNull(form.locationId), slotId: numberOrNull(form.slotId), bottleId: numberOrNull(form.bottleId), ...common, reportedByActorId: principal.id, critical: form.critical }); saved(); close() } catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  return <Modal title={ticket ? `Cập nhật phiếu #${ticket.id}` : 'Tạo phiếu bảo trì'} close={close}><form className="form-grid" onSubmit={submit}>{!ticket && <><label>Mã máy<input required type="number" min="1" value={form.machineId} onChange={(event) => setForm({ ...form, machineId: event.target.value })} /></label><label>Mã địa điểm<input type="number" min="1" value={form.locationId} onChange={(event) => setForm({ ...form, locationId: event.target.value })} /></label><label>Mã đầu phun<input type="number" min="1" value={form.slotId} onChange={(event) => setForm({ ...form, slotId: event.target.value })} /></label><label>Mã chai<input type="number" min="1" value={form.bottleId} onChange={(event) => setForm({ ...form, bottleId: event.target.value })} /></label></>}<label className="full">Tiêu đề<input required value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} /></label><label className="full">Mô tả<textarea required value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label><label>Danh mục<input required value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value })} /></label><label>Hạn xử lý<input type="datetime-local" value={form.dueAt} onChange={(event) => setForm({ ...form, dueAt: event.target.value })} /></label><label>Mức độ<select value={form.severity} onChange={(event) => setForm({ ...form, severity: event.target.value })}><option value="MINOR">Nhẹ</option><option value="MODERATE">Trung bình</option><option value="MAJOR">Nghiêm trọng</option><option value="CRITICAL">Khẩn cấp</option></select></label><label>Ưu tiên<select value={form.priority} onChange={(event) => setForm({ ...form, priority: event.target.value })}><option value="LOW">Thấp</option><option value="MEDIUM">Trung bình</option><option value="HIGH">Cao</option><option value="URGENT">Khẩn cấp</option></select></label>{!ticket && <label className="switch full"><input type="checkbox" checked={form.critical} onChange={(event) => setForm({ ...form, critical: event.target.checked })} />Đánh dấu sự cố khẩn cấp</label>}{error && <div className="error-box full">{error}</div>}<div className="modal-actions full"><button type="button" onClick={close}>Hủy</button><button className="primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu phiếu'}</button></div></form></Modal>
}

export function InventoryWorkflowForm({ tenantId, principal, bottles, close, saved }: { tenantId: number; principal: Principal; bottles: Bottle[]; close: () => void; saved: () => void }) {
  const [mode, setMode] = useState<'refill' | 'install' | 'replace'>('refill')
  const [form, setForm] = useState({ bottleId: '', oldBottleId: '', newBottleId: '', machineId: '', slotId: '', quantity: '', oldQuantity: '', reason: '' })
  const [error, setError] = useState(''); const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setSaving(true)
    try {
      if (mode === 'refill') await backend.refillBottle({ tenantId, bottleId: Number(form.bottleId), refillQuantity: Number(form.quantity), actorId: principal.id, reason: form.reason.trim() })
      if (mode === 'install') await backend.installBottle({ tenantId, bottleId: Number(form.bottleId), machineId: Number(form.machineId), slotId: Number(form.slotId), actorId: principal.id, measuredQuantityBefore: Number(form.quantity) })
      if (mode === 'replace') await backend.replaceBottle({ tenantId, oldBottleId: Number(form.oldBottleId), newBottleId: Number(form.newBottleId), actorId: principal.id, reason: form.reason.trim(), oldBottleMeasuredQuantityAfter: Number(form.oldQuantity), newBottleMeasuredQuantityBefore: Number(form.quantity) })
      saved(); close()
    } catch (reason) { setError(errorText(reason)) } finally { setSaving(false) }
  }
  const bottleOptions = bottles.map((bottle) => <option key={bottle.id} value={bottle.id}>{bottle.bottleCode} · {bottle.currentQuantity} ml · {bottle.status}</option>)
  return <Modal title="Refill, lắp hoặc thay chai" close={close}><form className="form-grid" onSubmit={submit}>
    <label className="full">Nghiệp vụ<select value={mode} onChange={(event) => setMode(event.target.value as typeof mode)}><option value="refill">Refill chai</option><option value="install">Lắp chai vào máy/slot</option><option value="replace">Thay chai cũ bằng chai mới</option></select></label>
    {mode !== 'replace' && <label>Chai<select required value={form.bottleId} onChange={(event) => setForm({ ...form, bottleId: event.target.value })}><option value="">Chọn chai</option>{bottleOptions}</select></label>}
    {mode === 'replace' && <><label>Chai cũ<select required value={form.oldBottleId} onChange={(event) => setForm({ ...form, oldBottleId: event.target.value })}><option value="">Chọn chai cũ</option>{bottleOptions}</select></label><label>Chai mới<select required value={form.newBottleId} onChange={(event) => setForm({ ...form, newBottleId: event.target.value })}><option value="">Chọn chai mới</option>{bottleOptions}</select></label><label>Lượng còn lại chai cũ<input required min="0" step="0.001" type="number" value={form.oldQuantity} onChange={(event) => setForm({ ...form, oldQuantity: event.target.value })} /></label></>}
    {mode === 'install' && <><label>Mã máy<input required min="1" type="number" value={form.machineId} onChange={(event) => setForm({ ...form, machineId: event.target.value })} /></label><label>Mã slot<input required min="1" type="number" value={form.slotId} onChange={(event) => setForm({ ...form, slotId: event.target.value })} /></label></>}
    <label>{mode === 'refill' ? 'Lượng refill' : 'Lượng đo trước khi lắp'}<input required min="0.001" step="0.001" type="number" value={form.quantity} onChange={(event) => setForm({ ...form, quantity: event.target.value })} /></label>
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
      if (isBatch) {
        await backend.disposeBatch(target.item.id, { tenantId: target.item.tenantId, actorId: principal.id, reason: finalReason })
        saved(`Đã hủy lô hàng ${target.item.batchCode} và các chai thuộc lô.`)
      } else {
        await backend.disposeBottle(target.item.id, { tenantId: target.item.tenantId, actorId: principal.id, reason: finalReason, targetStatus })
        saved(`Đã hủy chai nước hoa ${target.item.bottleCode}.`)
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
  const [check, setCheck] = useState({ itemName: '', description: '', required: true })
  const [repair, setRepair] = useState({ diagnosticResult: '', correctiveAction: '', replacedPart: '', cost: '', repairResult: 'SUCCESS', note: '', evidenceUrl: '' })
  const load = async () => { try { setHistory(await backend.maintenanceHistory(ticket.id, tenantId)) } catch (reason) { setError(errorText(reason)) } }
  useEffect(() => { void load() }, [])
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
        {canAssign && <><input type="number" min="1" placeholder="Nhập ID kỹ thuật viên (VD: 9204)" value={technicianId} onChange={(event) => setTechnicianId(event.target.value)} /><button className="primary" disabled={busy || !technicianId} onClick={() => void run(() => backend.assignMaintenance(ticket.id, tenantId, Number(technicianId), principal.id), 'Đã phân công kỹ thuật viên.')}>Phân công</button></>}
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
      {canTechAct && <div className="compact-form"><textarea placeholder="Kết quả chẩn đoán" value={repair.diagnosticResult} onChange={(event) => setRepair({ ...repair, diagnosticResult: event.target.value })} /><textarea placeholder="Hành động sửa chữa" value={repair.correctiveAction} onChange={(event) => setRepair({ ...repair, correctiveAction: event.target.value })} /><input placeholder="Linh kiện thay thế" value={repair.replacedPart} onChange={(event) => setRepair({ ...repair, replacedPart: event.target.value })} /><input type="number" min="0" step="1000" placeholder="Chi phí sửa chữa (tùy chọn)" value={repair.cost} onChange={(event) => setRepair({ ...repair, cost: event.target.value })} /><input placeholder="Ghi chú thêm (tùy chọn)" value={repair.note} onChange={(event) => setRepair({ ...repair, note: event.target.value })} /><input type="url" placeholder="URL ảnh minh chứng" value={repair.evidenceUrl} onChange={(event) => setRepair({ ...repair, evidenceUrl: event.target.value })} /><select value={repair.repairResult} onChange={(event) => setRepair({ ...repair, repairResult: event.target.value })}><option value="SUCCESS">Hoàn thành</option><option value="PARTIAL">Hoàn thành một phần</option><option value="FAILED">Không thành công</option></select><button className="primary" disabled={busy || !repair.diagnosticResult.trim() || !repair.correctiveAction.trim()} onClick={() => void run(() => backend.createRepairLog(ticket.id, { tenantId, diagnosedByActorId: principal.id, diagnosticResult: repair.diagnosticResult.trim(), correctiveAction: repair.correctiveAction.trim(), replacedPart: repair.replacedPart.trim() || null, cost: repair.cost ? Number(repair.cost) : null, repairResult: repair.repairResult, note: repair.note.trim() || null, evidenceUrl: repair.evidenceUrl.trim() || null }), 'Đã ghi kết quả sửa chữa.')}>Ghi kết quả</button></div>}
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

import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../api/client'
import { backend } from '../api/scentStation'
import type { RevenueBreakdown } from '../api/types'

export type TransactionFilters = { reference?: string; orderId?: string; status?: string; startDate?: string; endDate?: string }
const errorText = (error: unknown) => error instanceof ApiError || error instanceof Error ? error.message : 'Không thể hoàn thành yêu cầu.'
const money = (value: number) => new Intl.NumberFormat('vi-VN').format(value)

export function ReportingTools({ filters, apply, canExport, tenantId }: { filters: TransactionFilters; apply: (filters: TransactionFilters) => void; canExport: boolean; tenantId?: number }) {
  const [form, setForm] = useState({ reference: filters.reference ?? '', orderId: filters.orderId ?? '', status: filters.status ?? '', startDate: filters.startDate ?? '', endDate: filters.endDate ?? '' })
  const [format, setFormat] = useState<'xlsx' | 'csv'>('xlsx')
  const [breakdown, setBreakdown] = useState<RevenueBreakdown | null>(null)
  const [error, setError] = useState(''); const [exporting, setExporting] = useState(false)
  const values = (): TransactionFilters => ({ reference: form.reference.trim() || undefined, orderId: form.orderId.trim() || undefined, status: form.status || undefined, startDate: form.startDate || undefined, endDate: form.endDate || undefined })
  function submit(event: FormEvent) { event.preventDefault(); setError(''); if (form.startDate && form.endDate && form.startDate > form.endDate) { setError('Ngày bắt đầu không được sau ngày kết thúc.'); return } apply(values()) }
  async function loadBreakdown() {
    setExporting(true); setError('')
    try {
      if (!form.startDate || !form.endDate) throw new Error('Xem phân bổ doanh thu yêu cầu ngày bắt đầu và ngày kết thúc.')
      setBreakdown(await backend.revenueBreakdown(form.startDate, form.endDate, tenantId))
    } catch (reason) { setError(errorText(reason)) } finally { setExporting(false) }
  }
  async function exportFile(kind: 'transactions' | 'revenue') {
    setExporting(true); setError('')
    try {
      if (kind === 'transactions') await backend.exportTransactions({ ...values(), tenantId }, format)
      else {
        if (!form.startDate || !form.endDate) throw new Error('Xuất doanh thu yêu cầu ngày bắt đầu và ngày kết thúc.')
        await backend.exportRevenue(form.startDate, form.endDate, tenantId, format)
      }
    } catch (reason) { setError(errorText(reason)) } finally { setExporting(false) }
  }
  const statusLabels: Record<string, string> = { PENDING: 'Đang chờ', PAID: 'Đã thanh toán', FAILED: 'Thất bại', EXPIRED: 'Hết hạn', REFUNDED: 'Đã hoàn tiền' }
  function applyPreset(preset: 'week' | 'month') {
    const now = new Date()
    const y = now.getFullYear(); const m = now.getMonth(); const d = now.getDate()
    const pad = (n: number) => String(n).padStart(2, '0')
    const fmt = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    let startDate: string; let endDate: string
    if (preset === 'week') {
      const offset = (now.getDay() + 6) % 7
      startDate = fmt(new Date(y, m, d - offset))
      endDate = fmt(new Date(y, m, d - offset + 6))
    } else {
      startDate = fmt(new Date(y, m, 1))
      endDate = fmt(new Date(y, m + 1, 0))
    }
    const next = { ...form, startDate, endDate }
    setForm(next); setError('')
    apply({ reference: next.reference.trim() || undefined, orderId: next.orderId.trim() || undefined, status: next.status || undefined, startDate, endDate })
  }
  return <>
    <form className="report-filters panel" onSubmit={submit}>
      <div className="report-actions" style={{ alignItems: 'center' }}>
        <span style={{ fontSize: 12, fontWeight: 650, color: '#59635d' }}>Chọn nhanh kỳ báo cáo:</span>
        <button type="button" onClick={() => applyPreset('week')}>Tuần này</button>
        <button type="button" onClick={() => applyPreset('month')}>Tháng này</button>
      </div>
      <label>Mã tham chiếu<input value={form.reference} onChange={(event) => setForm({ ...form, reference: event.target.value })} /></label>
      <label>Mã đơn hàng<input value={form.orderId} onChange={(event) => setForm({ ...form, orderId: event.target.value })} /></label>
      <label>Trạng thái<select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })}><option value="">Tất cả</option>{Object.entries(statusLabels).map(([status, label]) => <option key={status} value={status}>{label}</option>)}</select></label>
      <label>Từ ngày<input type="date" value={form.startDate} onChange={(event) => setForm({ ...form, startDate: event.target.value })} /></label>
      <label>Đến ngày<input type="date" value={form.endDate} onChange={(event) => setForm({ ...form, endDate: event.target.value })} /></label>
      {canExport && <label>Định dạng xuất<select value={format} onChange={(event) => setFormat(event.target.value as 'xlsx' | 'csv')}><option value="xlsx">Excel (.xlsx)</option><option value="csv">CSV (.csv)</option></select></label>}
      <div className="report-actions">
        <button className="primary">Áp dụng</button>
        <button type="button" onClick={() => { setForm({ reference: '', orderId: '', status: '', startDate: '', endDate: '' }); setBreakdown(null); apply({}) }}>Xóa lọc</button>
        <button type="button" disabled={exporting} onClick={() => void loadBreakdown()}>Phân bổ doanh thu</button>
        {canExport && <>
          <button type="button" disabled={exporting} onClick={() => void exportFile('transactions')}>Xuất giao dịch</button>
          <button type="button" disabled={exporting} onClick={() => void exportFile('revenue')}>Xuất doanh thu</button>
        </>}
      </div>
      {error && <div className="error-box full">{error}</div>}
    </form>
    {breakdown && <section className="panel">
      <h3>Phân bổ doanh thu ({breakdown.startDate} → {breakdown.endDate}) — Tổng: {money(breakdown.totalRevenue)} ({breakdown.totalPaidTransactions} giao dịch PAID)</h3>
      <div className="metric-grid">
        <article className="metric-card"><span>Theo thương hiệu</span>{breakdown.byTenant.length ? breakdown.byTenant.map((item) => <small key={item.key}>Tenant #{item.key}: {money(item.revenue)} ({item.transactionCount} GD)</small>) : <small>Chưa có dữ liệu</small>}</article>
        <article className="metric-card"><span>Theo máy Kiosk</span>{breakdown.byMachine.length ? breakdown.byMachine.map((item) => <small key={item.key}>Máy #{item.key}: {money(item.revenue)} ({item.transactionCount} GD)</small>) : <small>Chưa có dữ liệu</small>}</article>
        <article className="metric-card"><span>Theo sản phẩm</span>{breakdown.byProduct.length ? breakdown.byProduct.map((item) => <small key={item.key}>SP #{item.key}: {money(item.revenue)} ({item.transactionCount} GD)</small>) : <small>Chưa có dữ liệu</small>}</article>
      </div>
    </section>}
  </>
}

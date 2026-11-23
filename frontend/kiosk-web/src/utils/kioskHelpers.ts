import type { DispensingResponse, Machine, Product, Slot } from '../types/api.ts'

export const formatCountdown = (seconds: number) => {
  const safe = Math.max(0, seconds)
  const mins = Math.floor(safe / 60)
  const secs = safe % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

export const dispensingStatusText = (status?: DispensingResponse['status']) => {
  switch (status) {
    case 'NOT_REQUESTED': return 'Đang chuẩn bị gửi lệnh phun…'
    case 'REQUESTED': return 'Đã gửi lệnh đến máy Kiosk…'
    case 'ACCEPTED': return 'Máy đã tiếp nhận lệnh, chuẩn bị phun…'
    case 'PROCESSING': return 'Đang thực hiện phun nước hoa…'
    case 'DISPENSED': return 'Phun nước hoa thành công'
    case 'FAILED': return 'Máy báo lỗi khi thực hiện phun'
    case 'UNKNOWN': return 'Chưa xác định được kết quả phun'
    default: return 'Đang chờ máy nhận lệnh…'
  }
}

export const availabilityText = (slot: Slot, machineReady: boolean, product: Product, machine: Machine | null) => {
  if (!machineReady) return 'Máy đang ngoại tuyến hoặc chưa sẵn sàng'
  if (!product.available) return 'Sản phẩm đang tạm ngừng phục vụ'
  if (!slot.enabled || slot.status === 'DISABLED') return 'Đầu phun đang bị khóa'
  if (slot.status === 'EMPTY') return 'Đã hết nước hoa'
  if (slot.status === 'ERROR') return 'Đầu phun đang gặp lỗi'
  const reason = slot.availabilityReason
  if (!reason) return 'Đầu phun chưa sẵn sàng'
  if (reason === 'Machine is outside operating hours') {
    const hours = machine?.operatingHoursStart && machine?.operatingHoursEnd
      ? ` (${machine.operatingHoursStart.slice(0, 5)}–${machine.operatingHoursEnd.slice(0, 5)})`
      : ''
    return `Máy hiện ngoài giờ phục vụ${hours}`
  }
  if (reason === 'Device registration has been revoked') return 'Thiết bị đã bị thu hồi đăng ký'
  if (reason === 'Slot is disabled') return 'Đầu phun đang bị khóa'
  if (reason === 'No product is assigned to the slot') return 'Đầu phun chưa được gán sản phẩm'
  if (reason === 'No cartridge is assigned to the slot') return 'Đầu phun chưa được lắp bình nước hoa'
  if (reason.startsWith('Machine operational status is ')) return 'Máy chưa ở trạng thái trực tuyến'
  if (reason.startsWith('Slot status is ')) return 'Đầu phun chưa ở trạng thái sẵn sàng'
  if (reason.startsWith('Active ')) return 'Đầu phun đang có cảnh báo cần xử lý'
  return reason
}

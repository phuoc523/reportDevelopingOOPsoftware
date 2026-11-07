// RBAC giao dien DEMO. Backend phai xac minh role + tenant + pham vi.
export const roles = [
  'Platform Super Administrator',
  'Brand Administrator',
  'Operations Manager',
  'Technician',
  'Inventory Staff',
  'Finance',
] as const
export type Role = (typeof roles)[number]
export type Permission =
  | 'tenant.manage' | 'user.platform.manage' | 'user.brand.manage'
  | 'product.view' | 'product.manage' | 'campaign.view' | 'campaign.manage'
  | 'inventory.view' | 'inventory.manage' | 'inventory.adjust'
  | 'device.view' | 'device.manage' | 'device.configure' | 'device.maintenance' | 'device.diagnostic_spray'
  | 'alert.view' | 'alert.manage' | 'maintenance.view' | 'maintenance.manage'
  | 'order.view' | 'order.review' | 'payment.refund'
  | 'report.platform.read' | 'report.brand.read' | 'report.operations.read' | 'report.inventory.read' | 'report.finance.read'
  | 'audit.view'
export const rolePermissions: Record<Role, Permission[]> = {
  'Platform Super Administrator': ['tenant.manage','user.platform.manage','device.view','alert.view','order.view','report.platform.read','audit.view'],
  'Brand Administrator': ['user.brand.manage','product.view','product.manage','campaign.view','campaign.manage','inventory.view','device.view','device.configure','alert.view','maintenance.view','order.view','report.brand.read'],
  'Operations Manager': ['product.view','inventory.view','device.view','device.manage','alert.view','alert.manage','maintenance.view','maintenance.manage','order.view','order.review','report.operations.read'],
  'Technician': ['device.view','device.maintenance','device.diagnostic_spray','alert.view','maintenance.view','maintenance.manage'],
  'Inventory Staff': ['product.view','inventory.view','inventory.manage','inventory.adjust','device.view','alert.view','report.inventory.read'],
  // Finance la role mo rong theo yeu cau nhom, khong co trong 6 nhom goc cua ScriptOOP.
  // Hoan tien can duoc phe duyet va xac thuc backend; UI demo chi cho phep ghi nhan xem xet.
  Finance: ['order.view','order.review','report.finance.read'],
}
export const hasPermission = (role: Role, permission: Permission): boolean => rolePermissions[role].includes(permission)

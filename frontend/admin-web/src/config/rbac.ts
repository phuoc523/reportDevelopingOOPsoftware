import type { Permission, Role } from '../api/types'

export const roles: Role[] = [
  'PLATFORM_SUPER_ADMIN', 'BRAND_ADMIN', 'OPERATIONS_MANAGER',
  'TECHNICIAN', 'INVENTORY_STAFF', 'CUSTOMER',
]

export const brandAssignableRoles: Role[] = [
  'OPERATIONS_MANAGER', 'TECHNICIAN', 'INVENTORY_STAFF',
]

export const permissions: Permission[] = [
  'TENANT_MANAGE', 'PLATFORM_CONFIG_MANAGE', 'PLATFORM_ROLE_MANAGE',
  'PLATFORM_AUDIT_READ', 'PLATFORM_REPORT_READ', 'DEVICE_ACCESS_REVOKE',
  'BRAND_PROFILE_READ', 'BRAND_PROFILE_EDIT', 'BRAND_USER_MANAGE', 'BRAND_ROLE_MANAGE',
  'CATALOG_READ', 'CATALOG_MANAGE', 'CAMPAIGN_MANAGE',
  'LOCATION_READ', 'LOCATION_MANAGE', 'MACHINE_READ', 'MACHINE_CONTROL', 'MACHINE_CONFIGURE',
  'ORDER_READ', 'ORDER_REVIEW', 'REFUND_REQUEST', 'REFUND_APPROVE',
  'INVENTORY_READ', 'INVENTORY_MANAGE', 'INVENTORY_ADJUST',
  'MAINTENANCE_READ', 'MAINTENANCE_MANAGE', 'DIAGNOSTIC_SPRAY',
  'ALERT_READ', 'ALERT_MANAGE', 'REPORT_READ', 'REPORT_EXPORT', 'AUDIT_READ',
  'NOTIFICATION_READ', 'NOTIFICATION_CONFIGURE', 'KIOSK_BROWSE', 'KIOSK_ORDER',
]

const platformOnly = new Set<Permission>([
  'TENANT_MANAGE', 'PLATFORM_CONFIG_MANAGE', 'PLATFORM_ROLE_MANAGE',
  'PLATFORM_AUDIT_READ', 'PLATFORM_REPORT_READ', 'DEVICE_ACCESS_REVOKE',
])
const highRisk = new Set<Permission>([
  'REFUND_APPROVE', 'INVENTORY_ADJUST', 'DIAGNOSTIC_SPRAY',
  'MACHINE_CONFIGURE', 'DEVICE_ACCESS_REVOKE',
])

export const brandDelegablePermissions = permissions.filter((permission) =>
  !platformOnly.has(permission) && !highRisk.has(permission)
  && permission !== 'BRAND_ROLE_MANAGE' && permission !== 'BRAND_USER_MANAGE',
)

export const roleLabel: Record<Role, string> = {
  PLATFORM_SUPER_ADMIN: 'Quản trị nền tảng', BRAND_ADMIN: 'Quản trị thương hiệu',
  OPERATIONS_MANAGER: 'Quản lý vận hành', TECHNICIAN: 'Kỹ thuật viên',
  INVENTORY_STAFF: 'Nhân viên tồn kho', CUSTOMER: 'Khách hàng',
  ADMIN: 'Quản trị viên cũ', STAFF: 'Nhân viên cũ',
}

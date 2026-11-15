export type Permission =
  | 'TENANT_MANAGE' | 'PLATFORM_CONFIG_MANAGE' | 'PLATFORM_ROLE_MANAGE'
  | 'PLATFORM_AUDIT_READ' | 'PLATFORM_REPORT_READ' | 'DEVICE_ACCESS_REVOKE'
  | 'BRAND_PROFILE_READ' | 'BRAND_PROFILE_EDIT' | 'BRAND_USER_MANAGE' | 'BRAND_ROLE_MANAGE'
  | 'CATALOG_READ' | 'CATALOG_MANAGE' | 'CAMPAIGN_MANAGE'
  | 'LOCATION_READ' | 'LOCATION_MANAGE' | 'MACHINE_READ' | 'MACHINE_CONTROL' | 'MACHINE_CONFIGURE'
  | 'ORDER_READ' | 'ORDER_REVIEW' | 'REFUND_REQUEST' | 'REFUND_APPROVE'
  | 'INVENTORY_READ' | 'INVENTORY_MANAGE' | 'INVENTORY_ADJUST'
  | 'MAINTENANCE_READ' | 'MAINTENANCE_MANAGE' | 'DIAGNOSTIC_SPRAY'
  | 'ALERT_READ' | 'ALERT_MANAGE' | 'REPORT_READ' | 'REPORT_EXPORT' | 'AUDIT_READ'
  | 'NOTIFICATION_READ' | 'NOTIFICATION_CONFIGURE' | 'KIOSK_BROWSE' | 'KIOSK_ORDER'

export type Role =
  | 'PLATFORM_SUPER_ADMIN' | 'BRAND_ADMIN' | 'OPERATIONS_MANAGER'
  | 'TECHNICIAN' | 'INVENTORY_STAFF' | 'CUSTOMER'
  | 'ADMIN' | 'STAFF'

export interface Principal {
  id: number
  username: string
  tenantId: number | null
  roles: string[]
  grants: Permission[]
  locationIds: number[]
  machineIds: number[]
}

export interface AuthResponse { token: string; tokenType: string; userId: number; username: string; role: string }
export interface Tenant { id: number; code: string; name: string; logoUrl: string | null; description: string | null; contactEmail: string | null; contactPhone: string | null; kioskContent: string | null; status: 'ACTIVE' | 'SUSPENDED' }
export interface Account { id: number; username: string; email: string; fullName: string; tenantId: number | null; roles: Role[]; explicitGrants: Permission[]; locationIds: number[]; machineIds: number[]; active: boolean; locked: boolean }
export interface TenantProfileInput { logoUrl: string | null; description: string | null; contactEmail: string | null; contactPhone: string | null; kioskContent: string | null }
export interface TenantInput { code?: string; name: string; profile: TenantProfileInput }
export interface AccountCreateInput { username: string; email: string; password: string; fullName: string; tenantId: number | null; roles: Role[]; locationIds: number[]; machineIds: number[] }
export interface Product { id: number; tenantId: number; name: string; description: string | null; fragranceNotes: string | null; imageUrl: string | null; defaultPrice: number; available: boolean; createdAt: string; updatedAt: string }
export interface Campaign { id: number; tenantId: number; productId: number; name: string; discountType: string; discountValue: number; startAt: string; endAt: string; active: boolean }
export interface CampaignInput { tenantId: number; productId: number; name: string; discountType: 'PERCENTAGE' | 'FIXED_AMOUNT'; discountValue: number; startAt: string; endAt: string; active: boolean }
export interface Machine { id: number; serialNumber: string; deviceIdentity: string; tenantId: string; locationId: string; name: string; deviceRegistrationStatus: string; operationalStatus: string; lastSeenAt: string | null; maintenanceChecklistComplete: boolean; postMaintenanceTestPassed: boolean }
export interface Location { locationId: string; tenantId: string; name: string; address: string | null; active: boolean; createdAt: string; updatedAt: string }
export interface MachineInput { serialNumber?: string; deviceIdentity?: string; tenantId: string; locationId: string; name: string }
export interface Slot { id: number; machineId: number; slotNumber: number; slotCode: string; productId: string | null; cartridgeId: string | null; enabled: boolean; status: string; available: boolean; availabilityReason: string | null; stockThreshold: number; calibratedSprayDurationMs: number; doseMl: number | null; price: number }
export interface SlotInput { slotNumber?: number; slotCode?: string; productId: string | null; cartridgeId: string | null; enabled: boolean; stockThreshold: number; calibratedSprayDurationMs: number; doseMl: number | null; price: number }
export interface Alert { id: number; type: string; severity: string; status: string; source: string; tenantId: string; machineId: number; locationId: string; summary: string; lastSeenAt: string; assignedTo: string | null; resolutionNotes: string | null }
export interface MaintenanceTicket { id: number; tenantId: number; machineId: number; locationId: number | null; title: string; description: string; category: string; severity: string; priority: string; status: string; assignedToActorId: number | null; critical: boolean; createdAt: string; updatedAt: string; closedAt: string | null }
export interface InventoryBatch { id: number; tenantId: number; productId: number; batchCode: string; receivedQuantity: number; receivedAt: string; expiryDate: string; expired: boolean }
export interface Bottle { id: number; tenantId: number; productId: number; batchId: number; bottleCode: string; initialQuantity: number; currentQuantity: number; status: string; updatedAt: string }
export interface OrderItem { productId: number; productName: string; unitPrice: number; quantity: number; lineTotal: number }
export interface Order { id: number; customerId: number; tenantId: string; machineId: string; slotId: string; items: OrderItem[]; totalAmount: number; status: string; commandId: string | null; dispensingStatus: string | null; manualReviewRequired: boolean; manualReviewReason: string | null; createdAt: string; currency: string }
export interface PaymentReview { reference: string; orderId: number; reason: string; status: string; manualReviewRequired: boolean; createdAt: string; resolvedAt: string | null }
export interface Transaction { id: number; reference: string; orderId: string; amount: number; currency: string; status: string; createdAt: string; paidAt: string | null }
export interface PageResponse<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }
export interface Dashboard { transactions: Record<string, unknown>; machines: Record<string, unknown>; alerts: Record<string, unknown>; inventory: Record<string, unknown>; upstream: Record<string, string>; operations: Record<string, unknown>; access: Record<string, unknown> | null }
export type JsonRecord = Record<string, unknown>

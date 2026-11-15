export type Permission =
  | 'KIOSK_BROWSE'
  | 'KIOSK_ORDER'
  | 'CATALOG_READ'
  | 'MACHINE_READ'
  | string

export interface AuthResponse {
  token: string
  tokenType: string
  userId: number
  username: string
  role: string
}

export interface Principal {
  id: number
  username: string
  tenantId: number | null
  roles: string[]
  grants: Permission[]
  locationIds: number[]
  machineIds: number[]
}

export interface Product {
  id: number
  tenantId: number
  name: string
  description: string | null
  fragranceNotes: string | null
  imageUrl: string | null
  defaultPrice: number
  available: boolean
}

export interface Machine {
  id: number
  tenantId: string
  locationId: string
  name: string
  deviceRegistrationStatus: string
  operationalStatus: string
  operatingHoursStart: string | null
  operatingHoursEnd: string | null
  lastSeenAt: string | null
  maintenanceChecklistComplete: boolean
  postMaintenanceTestPassed: boolean
}

export interface Slot {
  id: number
  machineId: number
  slotNumber: number
  slotCode: string
  productId: string | null
  cartridgeId: string | null
  enabled: boolean
  status: 'AVAILABLE' | 'EMPTY' | 'DISABLED' | 'ERROR' | string
  available: boolean
  availabilityReason: string | null
  doseMl: number | null
  price: number
}

export interface KioskItem {
  product: Product
  slot: Slot
  pricing: EffectivePrice
}

export interface EffectivePrice {
  tenantId: number
  productId: number
  defaultPrice: number
  effectivePrice: number
  campaignId: number | null
  campaignName: string | null
  discountType: string | null
  discountValue: number | null
  promotionApplied: boolean
}

export interface OrderItem {
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface OrderResponse {
  id: number
  customerId: number
  tenantId: string
  machineId: string
  slotId: string
  items: OrderItem[]
  totalAmount: number
  status: string
  commandId: string | null
  dispensingStatus: string | null
  manualReviewRequired: boolean
  manualReviewReason: string | null
  createdAt: string
  updatedAt: string
  currency: string
  expiresAt: string
}

export interface PaymentResponse {
  reference: string
  orderId: number
  amount: number
  currency: string
  status: 'PENDING' | 'PAID' | 'FAILED' | 'EXPIRED'
  qrPayload: string
  createdAt: string
  expiresAt: string
  paidAt: string | null
}

export interface DispensingResponse {
  orderId: number
  commandId: string
  machineId: string
  slotId: string
  status: 'NOT_REQUESTED' | 'REQUESTED' | 'ACCEPTED' | 'PROCESSING' | 'DISPENSED' | 'FAILED' | 'UNKNOWN'
  manualReviewRequired: boolean
  manualReviewReason: string | null
  createdAt: string
  resultReceivedAt: string | null
}

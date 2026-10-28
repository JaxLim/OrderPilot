export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
}

export interface Page<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export type Role = 'ADMIN' | 'MANAGER' | 'STAFF' | 'VIEWER'

export interface Me {
  id: number
  email: string
  displayName: string
  role: Role
  permissions: string[]
  tenant: { id: number; code: string; name: string; plan: string }
}

export interface LoginResponse {
  token: string
  expiresAt: string
  user: Me
}

export interface Product {
  id: number
  sku: string
  name: string
  category: string
  description?: string
  price: number
  cost: number
  lowStockThreshold: number
  status: 'ACTIVE' | 'ARCHIVED'
  available: number
  reserved: number
  lowStock: boolean
  createdAt: string
  updatedAt: string
}

export interface InventoryRow {
  productId: number
  sku: string
  productName: string
  category: string
  productStatus: string
  available: number
  reserved: number
  lowStockThreshold: number
  version: number
  updatedAt: string
  lowStock: boolean
}

export interface Movement {
  id: number
  productId: number
  sku: string
  productName: string
  type: 'INBOUND' | 'ADJUST' | 'RESERVE' | 'RELEASE' | 'DEDUCT' | 'RESTOCK'
  quantity: number
  refType?: string
  refId?: string
  note?: string
  createdAt: string
}

export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED' | 'REFUNDED'

export interface Order {
  id: number
  orderNo: string
  customerName: string
  customerEmail?: string
  shippingAddress?: string
  status: OrderStatus
  totalAmount: number
  itemCount: number
  remark?: string
  expiresAt?: string
  paidAt?: string
  shippedAt?: string
  completedAt?: string
  cancelledAt?: string
  cancelReason?: string
  createdAt: string
}

export interface OrderItem {
  id: number
  productId: number
  sku: string
  productName: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface OrderDetail {
  order: Order
  items: OrderItem[]
}

export interface Payment {
  id: number
  paymentNo: string
  orderId: number
  orderNo: string
  amount: number
  method: string
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED'
  providerTxnId?: string
  failureReason?: string
  createdAt: string
  paidAt?: string
}

export interface DashboardSummary {
  days: number
  kpis: {
    revenue: number
    revenueChangePct: number | null
    orders: number
    paidOrders: number
    averageOrderValue: number
    paymentConversionPct: number
    unitsSold: number
    pendingPayment: number
    awaitingShipment: number
    lowStockCount: number
    inventoryValue: number
  }
  daily: { day: string; revenue: number; orders: number }[]
  statusBreakdown: { status: OrderStatus; count: number }[]
  topProducts: { productId: number; sku: string; productName: string; units: number; revenue: number }[]
  categories: { category: string; revenue: number }[]
  lowStock: InventoryRow[]
  generatedAt: string
}

export interface Notification {
  id: number
  type: string
  level: 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR'
  title: string
  content?: string
  params?: string
  readFlag: boolean
  createdAt: string
}

export interface User {
  id: number
  email: string
  displayName: string
  role: Role
  status: 'ACTIVE' | 'DISABLED'
  lastLoginAt?: string
  createdAt: string
}

export interface AuditLog {
  id: number
  userEmail: string
  action: string
  targetType?: string
  targetId?: string
  detail?: string
  createdAt: string
}

export interface OutboxEvent {
  id: number
  eventId: string
  eventType: string
  aggregateType: string
  aggregateId: string
  status: 'NEW' | 'SENT' | 'FAILED'
  attempts: number
  lastError?: string
  createdAt: string
  sentAt?: string
}

export interface AiStatus {
  mode: 'llm' | 'offline'
  provider?: string
  model?: string
}

export interface ChatReply {
  reply: string
  mode: 'llm' | 'offline'
  toolsUsed: string[]
  notice?: string
}

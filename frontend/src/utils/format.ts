import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import relativeTime from 'dayjs/plugin/relativeTime'
import type { OrderStatus } from '@/api/types'

dayjs.extend(utc)
dayjs.extend(relativeTime)

const money = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const compactMoney = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  maximumFractionDigits: 1,
})
const num = new Intl.NumberFormat('en-US')

export const fmtMoney = (v: number | null | undefined) => money.format(Number(v ?? 0))
export const fmtMoneyCompact = (v: number | null | undefined) => compactMoney.format(Number(v ?? 0))
export const fmtNum = (v: number | null | undefined) => num.format(Number(v ?? 0))

/** The API returns UTC timestamps without an offset; show them in the viewer's local time. */
export const toLocal = (v?: string) => (v ? dayjs.utc(v).local() : null)
export const fmtDateTime = (v?: string) => toLocal(v)?.format('MMM D, YYYY HH:mm') ?? '—'
export const fmtDate = (v?: string) => toLocal(v)?.format('MMM D, YYYY') ?? '—'
export const fmtRelative = (v?: string) => toLocal(v)?.fromNow() ?? '—'

export const STATUS_META: Record<OrderStatus, { label: string; type: 'warning' | 'primary' | 'success' | 'info' | 'danger'; color: string }> = {
  PENDING_PAYMENT: { label: 'Awaiting payment', type: 'warning', color: '#f59e0b' },
  PAID: { label: 'Paid', type: 'primary', color: '#4f46e5' },
  SHIPPED: { label: 'Shipped', type: 'info', color: '#0ea5e9' },
  COMPLETED: { label: 'Completed', type: 'success', color: '#10b981' },
  CANCELLED: { label: 'Cancelled', type: 'info', color: '#94a3b8' },
  REFUNDED: { label: 'Refunded', type: 'danger', color: '#ef4444' },
}

export const PAYMENT_METHODS = [
  { value: 'CARD', label: 'Credit card' },
  { value: 'ALIPAY', label: 'Alipay' },
  { value: 'WECHAT_PAY', label: 'WeChat Pay' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
]

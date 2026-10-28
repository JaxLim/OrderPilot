import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import relativeTime from 'dayjs/plugin/relativeTime'
import type { OrderStatus } from '@/api/types'
import { lang, t } from '@/i18n'

dayjs.extend(utc)
dayjs.extend(relativeTime)

// Amounts are USD in both languages, so the currency format stays en-US ($1,234.00).
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

const zh = () => lang.value === 'zh'

/** The API returns UTC timestamps without an offset; show them in the viewer's local time. */
export const toLocal = (v?: string) => (v ? dayjs.utc(v).local() : null)
export const fmtDateTime = (v?: string) =>
  toLocal(v)?.format(zh() ? 'YYYY年M月D日 HH:mm' : 'MMM D, YYYY HH:mm') ?? '—'
export const fmtDate = (v?: string) => toLocal(v)?.format(zh() ? 'YYYY年M月D日' : 'MMM D, YYYY') ?? '—'
export const fmtDay = (v: string) => dayjs(v).format(zh() ? 'M月D日' : 'MMM D')
export const fmtDayLong = (v: string) => dayjs(v).format(zh() ? 'M月D日 dddd' : 'ddd, MMM D')
export const fmtRelative = (v?: string) => toLocal(v)?.fromNow() ?? '—'

export const STATUS_META: Record<OrderStatus, { type: 'warning' | 'primary' | 'success' | 'info' | 'danger'; color: string }> = {
  PENDING_PAYMENT: { type: 'warning', color: '#f59e0b' },
  PAID: { type: 'primary', color: '#4f46e5' },
  SHIPPED: { type: 'info', color: '#0ea5e9' },
  COMPLETED: { type: 'success', color: '#10b981' },
  CANCELLED: { type: 'info', color: '#94a3b8' },
  REFUNDED: { type: 'danger', color: '#ef4444' },
}

export const ORDER_STATUSES = Object.keys(STATUS_META) as OrderStatus[]
export const statusLabel = (s: OrderStatus) => t(`status.${s}`)

export const PAYMENT_METHODS = ['CARD', 'ALIPAY', 'WECHAT_PAY', 'BANK_TRANSFER'] as const
export const methodLabel = (m: string) => t(`payMethod.${m}`)

import type { Notification } from '@/api/types'
import { fmtMoney } from './format'

type P = Record<string, string | number | undefined>

/**
 * Server-side titles are English. For Chinese, rebuild the text from the notification type and its
 * structured params; fall back to the stored text for unknown types or old rows without params.
 */
const ZH: Record<string, (p: P) => [string, string]> = {
  'order.created': (p) => [`新订单 ${p.orderNo}`, `${p.customerName} 下单，金额 ${fmtMoney(Number(p.totalAmount))}`],
  'order.paid': (p) => [`订单 ${p.orderNo} 已收款`, `收到 ${p.customerName} 的付款 ${fmtMoney(Number(p.totalAmount))}`],
  'order.cancelled': (p) => [`订单 ${p.orderNo} 已取消`, `原因：${reason(p.reason)}。预留库存已释放。`],
  'order.shipped': (p) => [`订单 ${p.orderNo} 已发货`, `已发货给 ${p.customerName}`],
  'order.refunded': (p) => [`订单 ${p.orderNo} 已退款`, `已退款 ${fmtMoney(Number(p.totalAmount))}，原因：${p.reason ?? ''}`],
  'payment.failed': (p) => [`支付失败（${p.paymentNo}）`, String(p.reason ?? '')],
  'inventory.low_stock': (p) => [`库存不足：${p.productName}`, `${p.sku} 仅剩 ${p.available} 件（预警线 ${p.threshold}）`],
}

function reason(r?: string | number) {
  return r === 'Payment timeout' ? '支付超时' : String(r ?? '')
}

export function notificationText(n: Notification, lang: 'en' | 'zh'): { title: string; content?: string } {
  if (lang === 'zh' && n.params && ZH[n.type]) {
    try {
      const [title, content] = ZH[n.type](JSON.parse(n.params) as P)
      return { title, content }
    } catch {
      /* fall through */
    }
  }
  return { title: n.title, content: n.content }
}

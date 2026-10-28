<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, newIdempotencyKey, notifyError } from '@/api/http'
import type { OrderDetail, Payment } from '@/api/types'
import { useAuth } from '@/stores/auth'
import StatusTag from './StatusTag.vue'
import { fmtDateTime, fmtMoney, fmtRelative, PAYMENT_METHODS, toLocal } from '@/utils/format'

const props = defineProps<{ orderId: number | null }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const auth = useAuth()
const detail = ref<OrderDetail | null>(null)
const payments = ref<Payment[]>([])
const loading = ref(false)
const busy = ref('')
const payMethod = ref('CARD')
const simulateDecline = ref(false)
let payKey = newIdempotencyKey()

const open = computed({ get: () => props.orderId !== null, set: (v) => !v && emit('close') })
const o = computed(() => detail.value?.order)

async function load() {
  if (props.orderId === null) return
  loading.value = true
  try {
    ;[detail.value, payments.value] = await Promise.all([
      api.get<OrderDetail>(`/orders/${props.orderId}`),
      api.get<Payment[]>(`/orders/${props.orderId}/payments`),
    ])
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}

watch(() => props.orderId, (id) => {
  detail.value = null
  payKey = newIdempotencyKey()
  simulateDecline.value = false
  if (id !== null) load()
})

async function act(name: string, fn: () => Promise<unknown>, success: string) {
  busy.value = name
  try {
    await fn()
    ElMessage.success(success)
    emit('changed')
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = ''
    await load()
  }
}

function pay() {
  act('pay', async () => {
    try {
      await api.post(`/orders/${props.orderId}/payments`,
        { method: payMethod.value, simulateDecline: simulateDecline.value }, { 'Idempotency-Key': payKey })
    } finally {
      // a declined attempt frees the key server-side; a fresh key is used for the next distinct attempt
      payKey = newIdempotencyKey()
    }
  }, 'Payment captured — reserved stock committed')
}

async function cancel() {
  const { value } = await ElMessageBox.prompt('Reserved stock will be released back to inventory.', 'Cancel order', {
    inputPlaceholder: 'Reason (optional)', confirmButtonText: 'Cancel order', confirmButtonClass: 'el-button--danger',
  }).catch(() => ({ value: null }))
  if (value === null) return
  act('cancel', () => api.post(`/orders/${props.orderId}/cancel`, { reason: value || undefined }), 'Order cancelled')
}

async function refund() {
  const { value } = await ElMessageBox.prompt('The payment is refunded and items are returned to stock.', 'Refund order', {
    inputPlaceholder: 'Reason', inputValidator: (v) => !!v?.trim() || 'A reason is required',
    confirmButtonText: 'Refund', confirmButtonClass: 'el-button--danger',
  }).catch(() => ({ value: null }))
  if (value === null) return
  act('refund', () => api.post(`/orders/${props.orderId}/refund`, { reason: value }), 'Order refunded')
}

const timeline = computed(() => {
  const x = o.value
  if (!x) return []
  const t = [{ label: 'Order placed', at: x.createdAt, color: '#6366f1' }]
  if (x.paidAt) t.push({ label: 'Payment captured', at: x.paidAt, color: '#4f46e5' })
  if (x.shippedAt) t.push({ label: 'Shipped', at: x.shippedAt, color: '#0ea5e9' })
  if (x.completedAt) t.push({ label: 'Delivered & completed', at: x.completedAt, color: '#10b981' })
  if (x.cancelledAt) t.push({ label: `Cancelled — ${x.cancelReason ?? ''}`, at: x.cancelledAt, color: '#94a3b8' })
  if (x.status === 'REFUNDED') t.push({ label: `Refunded — ${x.cancelReason ?? ''}`, at: x.paidAt ?? x.createdAt, color: '#ef4444' })
  return t
})

const expiresIn = computed(() => {
  const exp = toLocal(o.value?.expiresAt)
  return o.value?.status === 'PENDING_PAYMENT' && exp ? exp.fromNow() : null
})

const payTag: Record<string, string> = { SUCCEEDED: 'success', FAILED: 'danger', REFUNDED: 'warning', PENDING: 'info' }
</script>

<template>
  <el-drawer v-model="open" size="560px" :title="o ? `Order ${o.orderNo}` : 'Order'" data-testid="order-drawer">
    <div v-loading="loading && !detail">
      <template v-if="o && detail">
        <div class="summary">
          <StatusTag :status="o.status" data-testid="order-status" />
          <span class="amount num">{{ fmtMoney(o.totalAmount) }}</span>
        </div>
        <el-alert v-if="expiresIn" type="warning" :closable="false" show-icon class="mb"
                  :title="`Awaiting payment — auto-cancels ${expiresIn} if unpaid`" />

        <div v-if="o.status === 'PENDING_PAYMENT' && auth.can('payment:write')" class="card pay-box">
          <div class="pay-title">Collect payment <span class="muted">(simulated gateway)</span></div>
          <div class="pay-row">
            <el-select v-model="payMethod" style="width: 170px">
              <el-option v-for="m in PAYMENT_METHODS" :key="m.value" :value="m.value" :label="m.label" />
            </el-select>
            <el-checkbox v-model="simulateDecline">Simulate decline</el-checkbox>
            <span class="spacer" />
            <el-button type="primary" :loading="busy === 'pay'" data-testid="pay-button" @click="pay">
              Charge {{ fmtMoney(o.totalAmount) }}
            </el-button>
          </div>
        </div>

        <div class="actions">
          <el-button v-if="o.status === 'PAID' && auth.can('order:fulfil')" type="primary" plain :loading="busy === 'ship'"
                     data-testid="ship-button"
                     @click="act('ship', () => api.post(`/orders/${o!.id}/ship`), 'Marked as shipped')">Mark shipped</el-button>
          <el-button v-if="o.status === 'SHIPPED' && auth.can('order:fulfil')" type="success" plain :loading="busy === 'complete'"
                     @click="act('complete', () => api.post(`/orders/${o!.id}/complete`), 'Order completed')">Mark delivered</el-button>
          <el-button v-if="o.status === 'PENDING_PAYMENT' && auth.can('order:write')" type="danger" plain
                     :loading="busy === 'cancel'" @click="cancel">Cancel order</el-button>
          <el-button v-if="o.status === 'PAID' && auth.can('payment:refund')" type="danger" plain
                     :loading="busy === 'refund'" @click="refund">Refund</el-button>
        </div>

        <h4>Customer</h4>
        <dl class="kv">
          <dt>Name</dt><dd>{{ o.customerName }}</dd>
          <dt>Email</dt><dd>{{ o.customerEmail || '—' }}</dd>
          <dt>Ship to</dt><dd>{{ o.shippingAddress || '—' }}</dd>
          <dt v-if="o.remark">Note</dt><dd v-if="o.remark">{{ o.remark }}</dd>
        </dl>

        <h4>Items</h4>
        <el-table :data="detail.items" size="small" border>
          <el-table-column label="Product" min-width="200">
            <template #default="{ row }">
              <div>{{ row.productName }}</div><div class="muted mono">{{ row.sku }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Qty" prop="quantity" width="60" align="right" />
          <el-table-column label="Price" width="95" align="right">
            <template #default="{ row }">{{ fmtMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="Total" width="105" align="right">
            <template #default="{ row }"><b>{{ fmtMoney(row.lineTotal) }}</b></template>
          </el-table-column>
        </el-table>

        <h4>Payments</h4>
        <div v-if="!payments.length" class="muted small">No payment attempts yet.</div>
        <div v-for="p in payments" :key="p.id" class="pay-line">
          <div>
            <div class="mono">{{ p.paymentNo }}</div>
            <div class="muted small">{{ p.method }} · {{ fmtRelative(p.createdAt) }}<span v-if="p.failureReason"> · {{ p.failureReason }}</span></div>
          </div>
          <el-tag :type="payTag[p.status] as any" effect="light">{{ p.status }}</el-tag>
        </div>

        <h4>Timeline</h4>
        <el-timeline>
          <el-timeline-item v-for="(e, i) in timeline" :key="i" :timestamp="fmtDateTime(e.at)" :color="e.color">
            {{ e.label }}
          </el-timeline-item>
        </el-timeline>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.amount {
  font-size: 24px;
  font-weight: 700;
}

.mb {
  margin-bottom: 14px;
}

.pay-box {
  padding: 14px;
  margin-bottom: 14px;
  background: #fafaff;
}

.pay-title {
  font-weight: 600;
  font-size: 13.5px;
  margin-bottom: 10px;
}

.pay-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.spacer {
  flex: 1;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

h4 {
  margin: 22px 0 10px;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--op-muted);
}

.kv {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 6px 12px;
  margin: 0;
  font-size: 13.5px;
}

.kv dt {
  color: var(--op-muted);
}

.kv dd {
  margin: 0;
}

.pay-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}

.small {
  font-size: 12.5px;
}
</style>

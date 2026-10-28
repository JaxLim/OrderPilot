<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { api, notifyError } from '@/api/http'
import type { Page, Payment } from '@/api/types'
import OrderDrawer from '@/components/OrderDrawer.vue'
import { fmtDateTime, fmtMoney, PAYMENT_METHODS } from '@/utils/format'

const rows = ref<Payment[]>([])
const total = ref(0)
const loading = ref(false)
const selected = ref<number | null>(null)
const q = reactive({ status: '', page: 1, size: 20 })
const methodLabel = Object.fromEntries(PAYMENT_METHODS.map((m) => [m.value, m.label]))
const tag: Record<string, string> = { SUCCEEDED: 'success', FAILED: 'danger', REFUNDED: 'warning', PENDING: 'info' }

async function load() {
  loading.value = true
  try {
    const page = await api.get<Page<Payment>>('/payments', { ...q, status: q.status || undefined })
    rows.value = page.items
    total.value = page.total
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(() => [q.status, q.page], load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Payments</h1>
        <p>Simulated gateway. Charges run outside DB transactions; outcomes are applied with compare-and-set.</p>
      </div>
    </div>
    <div class="card">
      <div class="toolbar">
        <el-segmented v-model="q.status" :options="[{ label: 'All', value: '' }, { label: 'Succeeded', value: 'SUCCEEDED' },
          { label: 'Failed', value: 'FAILED' }, { label: 'Refunded', value: 'REFUNDED' }]" />
        <span class="spacer" />
        <span class="muted">{{ total }} payments</span>
      </div>
      <el-table :data="rows" v-loading="loading" class="el-table--clickable" @row-click="(r: Payment) => (selected = r.orderId)">
        <el-table-column label="Payment" min-width="200">
          <template #default="{ row }"><span class="mono">{{ row.paymentNo }}</span></template>
        </el-table-column>
        <el-table-column label="Order" min-width="190">
          <template #default="{ row }"><span class="mono muted">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column label="Method" width="140">
          <template #default="{ row }">{{ methodLabel[row.method] ?? row.method }}</template>
        </el-table-column>
        <el-table-column label="Status" width="130">
          <template #default="{ row }"><el-tag :type="(tag[row.status] as any)" effect="light">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="Amount" width="120" align="right">
          <template #default="{ row }"><b class="num">{{ fmtMoney(row.amount) }}</b></template>
        </el-table-column>
        <el-table-column label="Provider ref / reason" min-width="220">
          <template #default="{ row }">
            <span v-if="row.failureReason" class="muted">{{ row.failureReason }}</span>
            <span v-else class="mono muted">{{ row.providerTxnId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Created" width="170">
          <template #default="{ row }"><span class="muted">{{ fmtDateTime(row.createdAt) }}</span></template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="q.page" :page-size="q.size" :total="total" layout="prev, pager, next" background />
      </div>
    </div>
    <OrderDrawer :order-id="selected" @close="selected = null" @changed="load" />
  </div>
</template>

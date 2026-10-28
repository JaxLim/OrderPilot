<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api, notifyError } from '@/api/http'
import type { Page, Payment } from '@/api/types'
import OrderDrawer from '@/components/OrderDrawer.vue'
import { fmtDateTime, fmtMoney, methodLabel } from '@/utils/format'

const { t } = useI18n()
const rows = ref<Payment[]>([])
const total = ref(0)
const loading = ref(false)
const selected = ref<number | null>(null)
const q = reactive({ status: '', page: 1, size: 20 })
const tag: Record<string, string> = { SUCCEEDED: 'success', FAILED: 'danger', REFUNDED: 'warning', PENDING: 'info' }
const statusOptions = computed(() => [
  { label: t('common.all'), value: '' },
  ...['SUCCEEDED', 'FAILED', 'REFUNDED'].map((s) => ({ label: t(`payStatus.${s}`), value: s })),
])

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
        <h1>{{ t('payments.title') }}</h1>
        <p>{{ t('payments.subtitle') }}</p>
      </div>
    </div>
    <div class="card">
      <div class="toolbar">
        <el-segmented v-model="q.status" :options="statusOptions" />
        <span class="spacer" />
        <span class="muted">{{ t('payments.count', { n: total }) }}</span>
      </div>
      <el-table :data="rows" v-loading="loading" class="el-table--clickable" @row-click="(r: Payment) => (selected = r.orderId)">
        <el-table-column :label="t('payments.colPayment')" min-width="200">
          <template #default="{ row }"><span class="mono">{{ row.paymentNo }}</span></template>
        </el-table-column>
        <el-table-column :label="t('payments.colOrder')" min-width="190">
          <template #default="{ row }"><span class="mono muted">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column :label="t('payments.colMethod')" width="140">
          <template #default="{ row }">{{ methodLabel(row.method) }}</template>
        </el-table-column>
        <el-table-column :label="t('payments.colStatus')" width="130">
          <template #default="{ row }"><el-tag :type="(tag[row.status] as any)" effect="light">{{ t(`payStatus.${row.status}`) }}</el-tag></template>
        </el-table-column>
        <el-table-column :label="t('payments.colAmount')" width="120" align="right">
          <template #default="{ row }"><b class="num">{{ fmtMoney(row.amount) }}</b></template>
        </el-table-column>
        <el-table-column :label="t('payments.colRef')" min-width="220">
          <template #default="{ row }">
            <span v-if="row.failureReason" class="muted">{{ row.failureReason }}</span>
            <span v-else class="mono muted">{{ row.providerTxnId }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('payments.colCreated')" width="180">
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

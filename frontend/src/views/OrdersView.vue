<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { api, notifyError } from '@/api/http'
import type { Order, OrderStatus, Page } from '@/api/types'
import { useAuth } from '@/stores/auth'
import StatusTag from '@/components/StatusTag.vue'
import OrderCreateDialog from '@/components/OrderCreateDialog.vue'
import OrderDrawer from '@/components/OrderDrawer.vue'
import { fmtDateTime, fmtMoney, fmtRelative, ORDER_STATUSES } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const auth = useAuth()
const route = useRoute()
const rows = ref<Order[]>([])
const total = ref(0)
const loading = ref(false)
const creating = ref(false)
const selected = ref<number | null>(null)
const q = reactive({
  status: (route.query.status as OrderStatus | undefined) ?? ('' as OrderStatus | ''),
  keyword: '',
  range: null as [string, string] | null,
  page: 1,
  size: 20,
})

const tabs = computed<{ value: OrderStatus | ''; label: string }[]>(() => [
  { value: '', label: t('common.all') },
  ...ORDER_STATUSES.map((s) => ({ value: s, label: t(`status.${s}`) })),
])

async function load() {
  loading.value = true
  try {
    const page = await api.get<Page<Order>>('/orders', {
      status: q.status || undefined,
      keyword: q.keyword || undefined,
      from: q.range?.[0],
      to: q.range?.[1],
      page: q.page,
      size: q.size,
    })
    rows.value = page.items
    total.value = page.total
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => [q.status, q.range, q.page, q.size], load)
let timer: number | undefined
watch(() => q.keyword, () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => { q.page = 1; load() }, 300)
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>{{ t('orders.title') }}</h1>
        <p>{{ t('orders.subtitle') }}</p>
      </div>
      <el-button v-if="auth.can('order:write')" type="primary" :icon="Plus" data-testid="new-order" @click="creating = true">
        {{ t('orders.new') }}
      </el-button>
    </div>

    <div class="card">
      <el-tabs v-model="q.status" class="tabs" @tab-change="q.page = 1">
        <el-tab-pane v-for="t in tabs" :key="t.value" :name="t.value" :label="t.label" />
      </el-tabs>
      <div class="toolbar">
        <el-input v-model="q.keyword" :prefix-icon="Search" :placeholder="t('orders.searchPlaceholder')" clearable
                  style="width: 300px" data-testid="order-search" />
        <el-date-picker v-model="q.range" type="daterange" value-format="YYYY-MM-DD" :start-placeholder="t('orders.from')"
                        :end-placeholder="t('orders.to')" style="width: 260px" />
        <span class="spacer" />
        <span class="muted">{{ t('orders.count', { n: total }) }}</span>
        <el-button :icon="Refresh" circle @click="load" />
      </div>
      <el-table :data="rows" v-loading="loading" class="el-table--clickable" row-key="id"
                @row-click="(r: Order) => (selected = r.id)" data-testid="orders-table">
        <el-table-column :label="t('orders.colOrder')" min-width="190">
          <template #default="{ row }">
            <div class="mono strong">{{ row.orderNo }}</div>
            <div class="muted small">{{ fmtRelative(row.createdAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('orders.colCustomer')" min-width="200">
          <template #default="{ row }">
            <div>{{ row.customerName }}</div>
            <div class="muted small">{{ row.customerEmail }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="t('orders.colStatus')" width="170">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column :label="t('orders.colItems')" prop="itemCount" width="80" align="right" />
        <el-table-column :label="t('orders.colTotal')" width="130" align="right">
          <template #default="{ row }"><b class="num">{{ fmtMoney(row.totalAmount) }}</b></template>
        </el-table-column>
        <el-table-column :label="t('orders.colPlaced')" width="190">
          <template #default="{ row }"><span class="muted small">{{ fmtDateTime(row.createdAt) }}</span></template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total"
                       :page-sizes="[20, 50, 100]" layout="sizes, prev, pager, next" background />
      </div>
    </div>

    <OrderCreateDialog v-model="creating" @created="(d) => { load(); selected = d.order.id }" />
    <OrderDrawer :order-id="selected" @close="selected = null" @changed="load" />
  </div>
</template>

<style scoped>
.tabs {
  padding: 0 16px;
}

.tabs :deep(.el-tabs__header) {
  margin: 0;
}

.strong {
  font-weight: 600;
}

.small {
  font-size: 12.5px;
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { Box, Coin, Document, ShoppingCart, Timer, TrendCharts, Van, Wallet } from '@element-plus/icons-vue'
import { api, notifyError } from '@/api/http'
import type { DashboardSummary } from '@/api/types'
import KpiCard from '@/components/KpiCard.vue'
import { fmtMoney, fmtMoneyCompact, fmtNum, fmtRelative, STATUS_META } from '@/utils/format'
import dayjs from 'dayjs'

use([CanvasRenderer, LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

const router = useRouter()
const days = ref(30)
const data = ref<DashboardSummary | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await api.get<DashboardSummary>('/dashboard', { days: days.value })
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(days, load)

const k = computed(() => data.value?.kpis)

const revenueOption = computed(() => {
  const d = data.value?.daily ?? []
  return {
    grid: { left: 8, right: 12, top: 30, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: number) => v,
      formatter: (ps: { axisValue: string; seriesName: string; value: number }[]) =>
        `<b>${dayjs(ps[0].axisValue).format('ddd, MMM D')}</b><br/>` +
        ps.map((p) => `${p.seriesName}: ${p.seriesName === 'Revenue' ? fmtMoney(p.value) : p.value}`).join('<br/>'),
    },
    legend: { right: 0, top: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10 },
    xAxis: {
      type: 'category',
      data: d.map((p) => p.day),
      axisLabel: { formatter: (v: string) => dayjs(v).format('MMM D'), color: '#94a3b8' },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
    },
    yAxis: [
      { type: 'value', axisLabel: { formatter: (v: number) => fmtMoneyCompact(v), color: '#94a3b8' }, splitLine: { lineStyle: { color: '#f1f5f9' } } },
      { type: 'value', axisLabel: { color: '#94a3b8' }, splitLine: { show: false } },
    ],
    series: [
      {
        name: 'Revenue', type: 'line', smooth: true, symbol: 'none', data: d.map((p) => p.revenue),
        lineStyle: { width: 2.5, color: '#4f46e5' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(79,70,229,0.25)' }, { offset: 1, color: 'rgba(79,70,229,0)' }] } },
      },
      { name: 'Orders', type: 'bar', yAxisIndex: 1, data: d.map((p) => p.orders), barWidth: '45%', itemStyle: { color: '#c7d2fe', borderRadius: [3, 3, 0, 0] } },
    ],
  }
})

const statusOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, icon: 'circle', itemWidth: 8, textStyle: { fontSize: 11.5 } },
  series: [{
    type: 'pie', radius: ['52%', '76%'], center: ['50%', '42%'], avoidLabelOverlap: true,
    itemStyle: { borderColor: '#fff', borderWidth: 2 }, label: { show: false },
    data: (data.value?.statusBreakdown ?? []).map((s) => ({
      name: STATUS_META[s.status].label, value: s.count, itemStyle: { color: STATUS_META[s.status].color },
    })),
  }],
}))

const categoryOption = computed(() => {
  const c = [...(data.value?.categories ?? [])].reverse()
  return {
    grid: { left: 4, right: 56, top: 4, bottom: 4, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v: number) => fmtMoney(v) },
    xAxis: { type: 'value', show: false },
    yAxis: { type: 'category', data: c.map((x) => x.category), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#475569' } },
    series: [{ type: 'bar', data: c.map((x) => x.revenue), barWidth: 14, itemStyle: { color: '#6366f1', borderRadius: 4 },
      label: { show: true, position: 'right', formatter: (p: { value: number }) => fmtMoneyCompact(p.value), color: '#64748b', fontSize: 11 } }],
  }
})

const maxTop = computed(() => Math.max(1, ...(data.value?.topProducts ?? []).map((p) => p.revenue)))
</script>

<template>
  <div class="page" v-loading="loading && !data">
    <div class="page-header">
      <div>
        <h1>Dashboard</h1>
        <p>Live performance of your workspace<span v-if="data"> · updated {{ fmtRelative(data.generatedAt) }}</span></p>
      </div>
      <el-radio-group v-model="days" size="default">
        <el-radio-button :value="7">7 days</el-radio-button>
        <el-radio-button :value="30">30 days</el-radio-button>
        <el-radio-button :value="90">90 days</el-radio-button>
      </el-radio-group>
    </div>

    <template v-if="data && k">
      <div class="grid kpis">
        <KpiCard label="Revenue" :value="fmtMoney(k.revenue)" :change="k.revenueChangePct" hint="vs previous period" :icon="TrendCharts" data-testid="kpi-revenue" />
        <KpiCard label="Orders" :value="fmtNum(k.orders)" :hint="`${fmtNum(k.paidOrders)} paid`" :icon="ShoppingCart" />
        <KpiCard label="Average order value" :value="fmtMoney(k.averageOrderValue)" :hint="`${fmtNum(k.unitsSold)} units sold`" :icon="Wallet" />
        <KpiCard label="Payment conversion" :value="`${k.paymentConversionPct}%`" hint="paid / placed" :icon="Coin" />
      </div>
      <div class="grid kpis small">
        <div class="card mini" @click="router.push({ path: '/orders', query: { status: 'PENDING_PAYMENT' } })">
          <el-icon><Timer /></el-icon><div><b>{{ k.pendingPayment }}</b><span>awaiting payment</span></div>
        </div>
        <div class="card mini" @click="router.push({ path: '/orders', query: { status: 'PAID' } })">
          <el-icon><Van /></el-icon><div><b>{{ k.awaitingShipment }}</b><span>ready to ship</span></div>
        </div>
        <div class="card mini warn" @click="router.push({ path: '/inventory', query: { low: '1' } })">
          <el-icon><Box /></el-icon><div><b>{{ k.lowStockCount }}</b><span>low-stock products</span></div>
        </div>
        <div class="card mini">
          <el-icon><Document /></el-icon><div><b>{{ fmtMoney(k.inventoryValue) }}</b><span>inventory at cost</span></div>
        </div>
      </div>

      <div class="grid row2">
        <div class="card card-pad">
          <div class="card-title">Revenue & orders <small>daily, last {{ days }} days</small></div>
          <v-chart :option="revenueOption" autoresize style="height: 300px" />
        </div>
        <div class="card card-pad">
          <div class="card-title">Order status</div>
          <v-chart :option="statusOption" autoresize style="height: 300px" />
        </div>
      </div>

      <div class="grid row3">
        <div class="card card-pad">
          <div class="card-title">Top products <small>by revenue</small></div>
          <div v-if="!data.topProducts.length" class="muted">No paid orders yet.</div>
          <div v-for="(p, i) in data.topProducts" :key="p.productId" class="top-row">
            <span class="rank">{{ i + 1 }}</span>
            <div class="grow">
              <div class="flex"><span class="name">{{ p.productName }}</span><b class="num">{{ fmtMoney(p.revenue) }}</b></div>
              <div class="bar"><i :style="{ width: (p.revenue / maxTop) * 100 + '%' }" /></div>
              <div class="muted tiny">{{ p.sku }} · {{ p.units }} units</div>
            </div>
          </div>
        </div>
        <div class="card card-pad">
          <div class="card-title">Revenue by category</div>
          <v-chart :option="categoryOption" autoresize style="height: 250px" />
        </div>
        <div class="card card-pad">
          <div class="card-title">Restock soon <router-link to="/inventory?low=1" class="tiny">View all</router-link></div>
          <div v-if="!data.lowStock.length" class="muted">All products are well stocked.</div>
          <div v-for="r in data.lowStock" :key="r.productId" class="low-row">
            <div>
              <div class="name">{{ r.productName }}</div>
              <div class="muted tiny mono">{{ r.sku }}</div>
            </div>
            <el-tag :type="r.available === 0 ? 'danger' : 'warning'" effect="light" round>
              {{ r.available }} / {{ r.lowStockThreshold }}
            </el-tag>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.kpis {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 16px;
}

.kpis.small .mini {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: border-color 0.15s;
  font-size: 18px;
  color: var(--op-primary);
}

.mini:hover {
  border-color: var(--op-primary);
}

.mini.warn {
  color: #ea580c;
}

.mini div {
  display: flex;
  flex-direction: column;
  color: var(--op-text);
}

.mini b {
  font-size: 16px;
}

.mini span {
  font-size: 12.5px;
  color: var(--op-muted);
}

.row2 {
  grid-template-columns: 2fr 1fr;
  margin-bottom: 16px;
}

.row3 {
  grid-template-columns: 1.2fr 1fr 1fr;
}

.top-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 7px 0;
}

.rank {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--op-primary-soft);
  color: var(--op-primary);
  font-size: 12px;
  font-weight: 700;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.grow {
  flex: 1;
  min-width: 0;
}

.flex {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 13.5px;
}

.name {
  font-weight: 500;
  font-size: 13.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar {
  height: 5px;
  background: #f1f5f9;
  border-radius: 3px;
  margin: 5px 0 3px;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #6366f1, #a855f7);
}

.tiny {
  font-size: 12px;
  font-weight: 400;
}

.low-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}

.low-row:last-child {
  border-bottom: none;
}

@media (max-width: 1200px) {
  .kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .row2,
  .row3 {
    grid-template-columns: 1fr;
  }
}
</style>

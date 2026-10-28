<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { api, notifyError } from '@/api/http'
import type { InventoryRow, Movement, Page } from '@/api/types'
import { useAuth } from '@/stores/auth'
import { fmtDateTime } from '@/utils/format'

const { t } = useI18n()
const auth = useAuth()
const route = useRoute()
const tab = ref('levels')
const rows = ref<InventoryRow[]>([])
const total = ref(0)
const loading = ref(false)
const q = reactive({ keyword: '', lowOnly: route.query.low === '1', page: 1, size: 20 })

const moves = ref<Movement[]>([])
const movesTotal = ref(0)
const mq = reactive({ productId: undefined as number | undefined, page: 1, size: 20 })

const adjusting = ref<InventoryRow | null>(null)
const adj = reactive({ type: 'INBOUND', quantity: 10, note: '' })
const saving = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await api.get<Page<InventoryRow>>('/inventory', { ...q, keyword: q.keyword || undefined })
    rows.value = page.items
    total.value = page.total
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}

async function loadMoves() {
  try {
    const page = await api.get<Page<Movement>>('/inventory/movements', mq)
    moves.value = page.items
    movesTotal.value = page.total
  } catch (e) {
    notifyError(e)
  }
}

onMounted(load)
watch(() => [q.lowOnly, q.page, q.size], load)
watch(() => [mq.page, mq.productId], loadMoves)
watch(tab, (v) => v === 'movements' && loadMoves())
let timer: number | undefined
watch(() => q.keyword, () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => { q.page = 1; load() }, 300)
})

function openAdjust(r: InventoryRow) {
  adjusting.value = r
  Object.assign(adj, { type: 'INBOUND', quantity: Math.max(r.lowStockThreshold * 3 - r.available, 10), note: '' })
}

async function saveAdjust() {
  if (!adjusting.value) return
  saving.value = true
  try {
    await api.post(`/inventory/${adjusting.value.productId}/adjustments`, {
      type: adj.type,
      quantity: adj.quantity,
      note: adj.note || (adj.type === 'INBOUND' ? t('inventory.defaultNoteIn') : t('inventory.defaultNoteAdj')),
    })
    ElMessage.success(t('inventory.updatedMsg'))
    adjusting.value = null
    load()
  } catch (e) {
    notifyError(e)
  } finally {
    saving.value = false
  }
}

function showHistory(r: InventoryRow) {
  mq.productId = r.productId
  mq.page = 1
  tab.value = 'movements'
  loadMoves()
}

const pct = (r: InventoryRow) => Math.min(100, (r.available / Math.max(1, r.lowStockThreshold * 3)) * 100)
const barColor = (r: InventoryRow) => (r.available === 0 ? '#ef4444' : r.lowStock ? '#f59e0b' : '#10b981')
const moveTag: Record<string, string> = {
  INBOUND: 'success', ADJUST: 'warning', RESERVE: 'primary', RELEASE: 'info', DEDUCT: 'danger', RESTOCK: 'success',
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>{{ t('inventory.title') }}</h1>
        <p>{{ t('inventory.subtitle') }}</p>
      </div>
    </div>
    <div class="card">
      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane :label="t('inventory.levels')" name="levels" />
        <el-tab-pane :label="t('inventory.ledger')" name="movements" />
      </el-tabs>

      <template v-if="tab === 'levels'">
        <div class="toolbar">
          <el-input v-model="q.keyword" :prefix-icon="Search" :placeholder="t('products.searchPlaceholder')" clearable style="width: 260px" />
          <el-switch v-model="q.lowOnly" :active-text="t('inventory.lowOnly')" />
          <span class="spacer" />
          <span class="muted">{{ t('inventory.skus', { n: total }) }}</span>
        </div>
        <el-table :data="rows" v-loading="loading" row-key="productId">
          <el-table-column :label="t('inventory.colProduct')" min-width="240">
            <template #default="{ row }">
              <div class="name">{{ row.productName }}</div>
              <div class="muted mono">{{ row.sku }} · {{ row.category }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="t('inventory.colAvailable')" width="230">
            <template #default="{ row }">
              <div class="level">
                <b class="num" :style="{ color: barColor(row) }">{{ row.available }}</b>
                <div class="track"><i :style="{ width: pct(row) + '%', background: barColor(row) }" /></div>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('inventory.colReserved')" prop="reserved" width="100" align="right" />
          <el-table-column :label="t('inventory.colAlert')" prop="lowStockThreshold" width="90" align="right" />
          <el-table-column :label="t('inventory.colVersion')" width="90" align="right">
            <template #default="{ row }"><span class="muted num">v{{ row.version }}</span></template>
          </el-table-column>
          <el-table-column :label="t('inventory.colUpdated')" width="180">
            <template #default="{ row }"><span class="muted small">{{ fmtDateTime(row.updatedAt) }}</span></template>
          </el-table-column>
          <el-table-column width="170" align="right">
            <template #default="{ row }">
              <el-button link @click="showHistory(row)">{{ t('inventory.history') }}</el-button>
              <el-button v-if="auth.can('inventory:write')" link type="primary" @click="openAdjust(row)">{{ t('inventory.adjust') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="prev, pager, next" background />
        </div>
      </template>

      <template v-else>
        <div class="toolbar">
          <el-tag v-if="mq.productId" closable @close="mq.productId = undefined">{{ t('inventory.filtered') }}</el-tag>
          <span class="spacer" />
          <span class="muted">{{ t('inventory.movements', { n: movesTotal }) }}</span>
        </div>
        <el-table :data="moves" row-key="id">
          <el-table-column :label="t('inventory.colWhen')" width="180">
            <template #default="{ row }"><span class="small">{{ fmtDateTime(row.createdAt) }}</span></template>
          </el-table-column>
          <el-table-column :label="t('inventory.colProduct')" min-width="220">
            <template #default="{ row }">{{ row.productName }} <span class="muted mono">{{ row.sku }}</span></template>
          </el-table-column>
          <el-table-column :label="t('inventory.colType')" width="130">
            <template #default="{ row }">
              <el-tag :type="(moveTag[row.type] as any)" effect="light" size="small">{{ t(`inventory.move.${row.type}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('inventory.colQty')" width="90" align="right">
            <template #default="{ row }">
              <b class="num" :style="{ color: row.quantity > 0 ? '#059669' : '#dc2626' }">{{ row.quantity > 0 ? '+' : '' }}{{ row.quantity }}</b>
            </template>
          </el-table-column>
          <el-table-column :label="t('inventory.colRef')" min-width="200">
            <template #default="{ row }">
              <span class="mono muted">{{ row.refId || '—' }}</span>
              <span v-if="row.note" class="small"> · {{ row.note }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="mq.page" :page-size="mq.size" :total="movesTotal" layout="prev, pager, next" background />
        </div>
      </template>
    </div>

    <el-dialog :model-value="!!adjusting" :title="t('inventory.adjustTitle', { name: adjusting?.productName ?? '' })" width="480px"
               @close="adjusting = null">
      <el-form label-position="top">
        <el-form-item :label="t('inventory.type')">
          <el-radio-group v-model="adj.type">
            <el-radio-button value="INBOUND">{{ t('inventory.received') }}</el-radio-button>
            <el-radio-button value="ADJUST">{{ t('inventory.correction') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="adj.type === 'INBOUND' ? t('inventory.qtyReceived') : t('inventory.qtyChange')">
          <el-input-number v-model="adj.quantity" :min="adj.type === 'INBOUND' ? 1 : -100000" />
          <span class="muted hint">{{ t('inventory.willBe', { n: (adjusting?.available ?? 0) + adj.quantity }) }}</span>
        </el-form-item>
        <el-form-item :label="t('inventory.note')">
          <el-input v-model="adj.note" :placeholder="adj.type === 'INBOUND' ? t('inventory.notePlaceholderIn') : t('inventory.notePlaceholderAdj')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjusting = null">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveAdjust">{{ t('common.apply') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tabs {
  padding: 0 16px;
}

.tabs :deep(.el-tabs__header) {
  margin: 0;
}

.name {
  font-weight: 500;
}

.level {
  display: flex;
  align-items: center;
  gap: 10px;
}

.level b {
  width: 36px;
}

.track {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: #f1f5f9;
}

.track i {
  display: block;
  height: 100%;
  border-radius: 3px;
}

.small {
  font-size: 12.5px;
}

.hint {
  margin-left: 12px;
  font-size: 13px;
}
</style>

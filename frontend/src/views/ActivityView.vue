<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { api, notifyError } from '@/api/http'
import type { AuditLog, OutboxEvent, Page } from '@/api/types'
import { fmtDateTime, fmtRelative } from '@/utils/format'

const { t } = useI18n()
const tab = ref('audit')
const logs = ref<AuditLog[]>([])
const total = ref(0)
const q = reactive({ page: 1, size: 25 })
const events = ref<{ counts: Record<string, number>; recent: OutboxEvent[] }>({ counts: {}, recent: [] })

async function loadLogs() {
  try {
    const page = await api.get<Page<AuditLog>>('/audit-logs', q)
    logs.value = page.items
    total.value = page.total
  } catch (e) {
    notifyError(e)
  }
}

async function loadEvents() {
  try {
    events.value = await api.get('/system/events')
  } catch (e) {
    notifyError(e)
  }
}

onMounted(() => {
  loadLogs()
  loadEvents()
})
watch(() => q.page, loadLogs)

const statusTag: Record<string, string> = { SENT: 'success', NEW: 'warning', FAILED: 'danger' }
const counters = [
  { key: 'SENT', label: 'activity.delivered' },
  { key: 'NEW', label: 'activity.pending' },
  { key: 'FAILED', label: 'activity.parked' },
]
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>{{ t('activity.title') }}</h1>
        <p>{{ t('activity.subtitle') }}</p>
      </div>
      <el-button :icon="Refresh" @click="tab === 'audit' ? loadLogs() : loadEvents()">{{ t('common.refresh') }}</el-button>
    </div>
    <div class="card">
      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane :label="t('activity.audit')" name="audit" />
        <el-tab-pane :label="t('activity.pipeline')" name="events" />
      </el-tabs>

      <template v-if="tab === 'audit'">
        <el-table :data="logs">
          <el-table-column :label="t('activity.colWhen')" width="190">
            <template #default="{ row }"><span>{{ fmtDateTime(row.createdAt) }}</span></template>
          </el-table-column>
          <el-table-column :label="t('activity.colActor')" prop="userEmail" width="220" />
          <el-table-column :label="t('activity.colAction')" width="180">
            <template #default="{ row }"><el-tag effect="plain" class="mono">{{ row.action }}</el-tag></template>
          </el-table-column>
          <el-table-column :label="t('activity.colTarget')" width="230">
            <template #default="{ row }"><span class="muted">{{ row.targetType }}</span> <span class="mono">{{ row.targetId }}</span></template>
          </el-table-column>
          <el-table-column :label="t('activity.colDetail')" prop="detail" min-width="220" />
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="q.page" :page-size="q.size" :total="total" layout="prev, pager, next" background />
        </div>
      </template>

      <template v-else>
        <div class="counts">
          <div v-for="c in counters" :key="c.key" class="count">
            <span class="muted">{{ t(c.label) }}</span>
            <b>{{ events.counts[c.key] ?? 0 }}</b>
          </div>
        </div>
        <el-table :data="events.recent">
          <el-table-column :label="t('activity.colEvent')" width="200">
            <template #default="{ row }"><span class="mono">{{ row.eventType }}</span></template>
          </el-table-column>
          <el-table-column :label="t('activity.colAggregate')" width="160">
            <template #default="{ row }"><span class="muted">{{ row.aggregateType }}</span> #{{ row.aggregateId }}</template>
          </el-table-column>
          <el-table-column :label="t('activity.colStatus')" width="110">
            <template #default="{ row }"><el-tag :type="(statusTag[row.status] as any)" effect="light" size="small">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column :label="t('activity.colAttempts')" prop="attempts" width="100" align="right" />
          <el-table-column :label="t('activity.colCreated')" width="140">
            <template #default="{ row }"><span class="muted">{{ fmtRelative(row.createdAt) }}</span></template>
          </el-table-column>
          <el-table-column :label="t('activity.colEventId')" min-width="280">
            <template #default="{ row }"><span class="mono muted">{{ row.eventId }}</span></template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </div>
</template>

<style scoped>
.tabs {
  padding: 0 16px;
}

.tabs :deep(.el-tabs__header) {
  margin: 0;
}

.counts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 16px;
}

.count {
  border: 1px solid var(--op-border);
  border-radius: 10px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
}

.count b {
  font-size: 22px;
}
</style>

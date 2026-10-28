<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { api, notifyError } from '@/api/http'
import type { AuditLog, OutboxEvent, Page } from '@/api/types'
import { fmtDateTime, fmtRelative } from '@/utils/format'

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
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1>Activity</h1>
        <p>Who did what, and the health of the event pipeline (transactional outbox → RabbitMQ).</p>
      </div>
      <el-button :icon="Refresh" @click="tab === 'audit' ? loadLogs() : loadEvents()">Refresh</el-button>
    </div>
    <div class="card">
      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="Audit log" name="audit" />
        <el-tab-pane label="Event pipeline" name="events" />
      </el-tabs>

      <template v-if="tab === 'audit'">
        <el-table :data="logs">
          <el-table-column label="When" width="180">
            <template #default="{ row }"><span>{{ fmtDateTime(row.createdAt) }}</span></template>
          </el-table-column>
          <el-table-column label="Actor" prop="userEmail" width="220" />
          <el-table-column label="Action" width="180">
            <template #default="{ row }"><el-tag effect="plain" class="mono">{{ row.action }}</el-tag></template>
          </el-table-column>
          <el-table-column label="Target" width="230">
            <template #default="{ row }"><span class="muted">{{ row.targetType }}</span> <span class="mono">{{ row.targetId }}</span></template>
          </el-table-column>
          <el-table-column label="Detail" prop="detail" min-width="220" />
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="q.page" :page-size="q.size" :total="total" layout="prev, pager, next" background />
        </div>
      </template>

      <template v-else>
        <div class="counts">
          <div v-for="s in ['SENT', 'NEW', 'FAILED']" :key="s" class="count">
            <span class="muted">{{ s === 'NEW' ? 'Pending relay' : s === 'SENT' ? 'Delivered' : 'Parked (failed)' }}</span>
            <b>{{ events.counts[s] ?? 0 }}</b>
          </div>
        </div>
        <el-table :data="events.recent">
          <el-table-column label="Event" width="200">
            <template #default="{ row }"><span class="mono">{{ row.eventType }}</span></template>
          </el-table-column>
          <el-table-column label="Aggregate" width="160">
            <template #default="{ row }"><span class="muted">{{ row.aggregateType }}</span> #{{ row.aggregateId }}</template>
          </el-table-column>
          <el-table-column label="Status" width="110">
            <template #default="{ row }"><el-tag :type="(statusTag[row.status] as any)" effect="light" size="small">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="Attempts" prop="attempts" width="90" align="right" />
          <el-table-column label="Created" width="140">
            <template #default="{ row }"><span class="muted">{{ fmtRelative(row.createdAt) }}</span></template>
          </el-table-column>
          <el-table-column label="Event id" min-width="280">
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

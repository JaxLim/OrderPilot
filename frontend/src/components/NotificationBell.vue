<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { Bell } from '@element-plus/icons-vue'
import { api } from '@/api/http'
import type { Notification } from '@/api/types'
import { fmtRelative } from '@/utils/format'

const unread = ref(0)
const items = ref<Notification[]>([])
let timer: number | undefined

async function load() {
  try {
    const feed = await api.get<{ unread: number; items: Notification[] }>('/notifications')
    unread.value = feed.unread
    items.value = feed.items
  } catch {
    /* ignore polling errors */
  }
}

async function readAll() {
  await api.post('/notifications/read-all')
  await load()
}

async function read(n: Notification) {
  if (!n.readFlag) {
    await api.post(`/notifications/${n.id}/read`)
    await load()
  }
}

const dot: Record<string, string> = { INFO: '#6366f1', SUCCESS: '#10b981', WARNING: '#f59e0b', ERROR: '#ef4444' }

onMounted(() => {
  load()
  timer = window.setInterval(load, 10000)
})
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <el-popover placement="bottom-end" :width="380" trigger="click" @show="load">
    <template #reference>
      <el-badge :value="unread" :hidden="unread === 0" :max="99" class="bell" data-testid="notification-bell">
        <el-button circle text><el-icon :size="19"><Bell /></el-icon></el-button>
      </el-badge>
    </template>
    <div class="head">
      <strong>Notifications</strong>
      <el-button link type="primary" size="small" :disabled="unread === 0" @click="readAll">Mark all read</el-button>
    </div>
    <el-scrollbar max-height="420px">
      <div v-if="!items.length" class="empty">You're all caught up.</div>
      <div v-for="n in items" :key="n.id" class="item" :class="{ unread: !n.readFlag }" @click="read(n)">
        <span class="dot" :style="{ background: dot[n.level] }" />
        <div class="body">
          <div class="title">{{ n.title }}</div>
          <div v-if="n.content" class="text">{{ n.content }}</div>
          <div class="time">{{ fmtRelative(n.createdAt) }}</div>
        </div>
      </div>
    </el-scrollbar>
    <div class="foot muted">Delivered asynchronously via RabbitMQ</div>
  </el-popover>
</template>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--op-border);
}

.item {
  display: flex;
  gap: 10px;
  padding: 10px 4px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
}

.item.unread {
  background: #fafaff;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}

.title {
  font-weight: 600;
  font-size: 13px;
}

.text {
  font-size: 12.5px;
  color: var(--op-muted);
  margin-top: 2px;
}

.time {
  font-size: 11.5px;
  color: var(--op-subtle);
  margin-top: 3px;
}

.empty {
  padding: 24px;
  text-align: center;
  color: var(--op-muted);
}

.foot {
  font-size: 11px;
  text-align: center;
  padding-top: 8px;
}
</style>

<script setup lang="ts">
defineProps<{
  label: string
  value: string
  hint?: string
  change?: number | null
  tone?: 'default' | 'warning' | 'danger'
  icon?: object
}>()
</script>

<template>
  <div class="card kpi" :class="tone">
    <div class="top">
      <span class="label">{{ label }}</span>
      <span v-if="icon" class="icon"><el-icon :size="16"><component :is="icon" /></el-icon></span>
    </div>
    <div class="value num">{{ value }}</div>
    <div class="bottom">
      <span v-if="change !== undefined && change !== null" class="change" :class="change >= 0 ? 'up' : 'down'">
        {{ change >= 0 ? '▲' : '▼' }} {{ Math.abs(change) }}%
      </span>
      <span v-if="hint" class="hint">{{ hint }}</span>
    </div>
  </div>
</template>

<style scoped>
.kpi {
  padding: 16px 18px;
  min-height: 112px;
  display: flex;
  flex-direction: column;
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.label {
  font-size: 13px;
  color: var(--op-muted);
  font-weight: 500;
}

.icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--op-primary-soft);
  color: var(--op-primary);
}

.warning .icon {
  background: #fff7ed;
  color: #ea580c;
}

.danger .icon {
  background: #fef2f2;
  color: var(--op-danger);
}

.value {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 6px 0 4px;
}

.bottom {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12.5px;
}

.change {
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 6px;
}

.change.up {
  color: #047857;
  background: #ecfdf5;
}

.change.down {
  color: #b91c1c;
  background: #fef2f2;
}

.hint {
  color: var(--op-muted);
}
</style>

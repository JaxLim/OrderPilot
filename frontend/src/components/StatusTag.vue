<script setup lang="ts">
import { computed } from 'vue'
import type { OrderStatus } from '@/api/types'
import { STATUS_META } from '@/utils/format'

const props = defineProps<{ status: OrderStatus }>()
const meta = computed(() => STATUS_META[props.status])
</script>

<template>
  <span class="status" :style="{ '--c': meta.color }">
    <i />{{ meta.label }}
  </span>
</template>

<style scoped>
.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 500;
  padding: 2px 9px;
  border-radius: 999px;
  color: var(--c);
  background: color-mix(in srgb, var(--c) 12%, white);
  white-space: nowrap;
}

i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--c);
}
</style>

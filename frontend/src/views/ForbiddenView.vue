<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="page">
    <el-result icon="warning" :title="t('forbidden.title')" :sub-title="t('forbidden.subtitle')">
      <template #extra>
        <router-link to="/orders"><el-button type="primary">{{ t('forbidden.back') }}</el-button></router-link>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { lang, setLang } from '@/i18n'
</script>

<template>
  <div class="lang" role="group" aria-label="Language" data-testid="lang-switch">
    <button type="button" :class="{ on: lang === 'en' }" data-testid="lang-en" @click="setLang('en')">EN</button>
    <button type="button" :class="{ on: lang === 'zh' }" data-testid="lang-zh" @click="setLang('zh')">中文</button>
  </div>
</template>

<style scoped>
.lang {
  display: inline-flex;
  padding: 2px;
  border-radius: 8px;
  background: #f1f5f9;
  border: 1px solid var(--op-border);
}

button {
  font: inherit;
  font-size: 12.5px;
  font-weight: 500;
  border: 0;
  background: transparent;
  color: var(--op-muted);
  padding: 4px 10px;
  border-radius: 6px;
  cursor: pointer;
}

button.on {
  background: #fff;
  color: var(--op-text);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}
</style>

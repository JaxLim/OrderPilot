<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import { ApiError } from '@/api/http'
import LangSwitch from '@/components/LangSwitch.vue'

const { t } = useI18n()
const auth = useAuth()
const router = useRouter()
const route = useRoute()
const form = reactive({ email: 'admin@acme.test', password: 'Passw0rd!' })
const loading = ref(false)
const error = ref('')

const demoAccounts = [
  { email: 'admin@acme.test', role: 'ADMIN' },
  { email: 'manager@acme.test', role: 'MANAGER' },
  { email: 'staff@acme.test', role: 'STAFF' },
  { email: 'viewer@acme.test', role: 'VIEWER' },
  { email: 'admin@nova.test', role: 'ADMIN', tenant: 'Nova' },
]

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await auth.login(form.email, form.password)
    router.push((route.query.redirect as string) || '/dashboard')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function pick(email: string) {
  form.email = email
  form.password = 'Passw0rd!'
  error.value = ''
}
</script>

<template>
  <div class="screen">
    <div class="corner"><LangSwitch /></div>

    <div class="card box">
      <div class="brand">
        <img src="/favicon.svg" width="40" height="40" alt="" />
        <span>OrderPilot</span>
      </div>
      <p class="welcome">{{ t('auth.signInSubtitle') }}</p>

      <el-form label-position="top" class="form" @submit.prevent="submit">
        <el-form-item :label="t('auth.email')">
          <el-input v-model="form.email" size="large" autocomplete="username" data-testid="login-email" />
        </el-form-item>
        <el-form-item :label="t('auth.password')">
          <el-input v-model="form.password" type="password" size="large" show-password autocomplete="current-password"
                    data-testid="login-password" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="err" />
        <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit"
                   data-testid="login-submit">{{ t('auth.signIn') }}</el-button>
      </el-form>

      <div class="divider"><span>{{ t('auth.demo') }}</span></div>
      <div class="chips">
        <button v-for="a in demoAccounts" :key="a.email" type="button" class="chip"
                :class="{ on: form.email === a.email }" :title="a.email" @click="pick(a.email)">
          {{ a.tenant ? `${a.tenant} · ` : '' }}{{ t(`roles.${a.role}`) }}
        </button>
      </div>
    </div>

    <p class="foot">
      {{ t('auth.noAccount') }} <router-link to="/register">{{ t('auth.createWorkspace') }}</router-link>
    </p>
  </div>
</template>

<style scoped>
.screen {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  background:
    radial-gradient(900px 500px at 50% -10%, #e0e7ff 0%, transparent 70%),
    var(--op-bg);
}

.corner {
  position: fixed;
  top: 18px;
  right: 20px;
}

.box {
  width: 100%;
  max-width: 380px;
  padding: 36px 32px 28px;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(15, 23, 42, 0.08);
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.welcome {
  text-align: center;
  color: var(--op-muted);
  margin: 6px 0 26px;
  font-size: 14px;
}

.form :deep(.el-form-item__label) {
  font-weight: 500;
}

.err {
  margin: -4px 0 14px;
}

.submit {
  width: 100%;
  margin-top: 4px;
}

.divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 24px 0 12px;
  color: var(--op-subtle);
  font-size: 12px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--op-border);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}

.chip {
  font: inherit;
  font-size: 12.5px;
  border: 1px solid var(--op-border);
  background: #fff;
  color: var(--op-muted);
  border-radius: 999px;
  padding: 4px 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.chip:hover,
.chip.on {
  border-color: var(--op-primary);
  color: var(--op-primary);
  background: var(--op-primary-soft);
}

.foot {
  margin-top: 20px;
  font-size: 13.5px;
  color: var(--op-muted);
}
</style>

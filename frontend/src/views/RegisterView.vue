<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuth } from '@/stores/auth'
import { notifyError } from '@/api/http'
import LangSwitch from '@/components/LangSwitch.vue'

const { t } = useI18n()
const auth = useAuth()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ companyName: '', tenantCode: '', adminName: '', email: '', password: '' })
const codeTouched = ref(false)

watch(() => form.companyName, (name) => {
  if (!codeTouched.value) {
    form.tenantCode = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 40)
  }
})

const rules = computed<FormRules>(() => ({
  companyName: [{ required: true, message: t('auth.rules.company') }],
  tenantCode: [{ required: true, pattern: /^[a-z0-9-]{3,40}$/, message: t('auth.rules.code') }],
  adminName: [{ required: true, message: t('auth.rules.name') }],
  email: [{ required: true, type: 'email', message: t('auth.rules.email') }],
  password: [{ required: true, min: 8, message: t('auth.rules.password') }],
}))

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await auth.register(form)
    router.push('/products')
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="wrap">
    <div class="corner"><LangSwitch /></div>
    <div class="card box">
      <div class="brand"><img src="/favicon.svg" width="30" height="30" alt="" /> OrderPilot</div>
      <h2>{{ t('auth.registerTitle') }}</h2>
      <p class="muted">{{ t('auth.registerSubtitle') }}</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item :label="t('auth.companyName')" prop="companyName"><el-input v-model="form.companyName" /></el-form-item>
        <el-form-item :label="t('auth.workspaceCode')" prop="tenantCode">
          <el-input v-model="form.tenantCode" @input="codeTouched = true"><template #prepend>orderpilot/</template></el-input>
        </el-form-item>
        <el-form-item :label="t('auth.yourName')" prop="adminName"><el-input v-model="form.adminName" /></el-form-item>
        <el-form-item :label="t('auth.workEmail')" prop="email"><el-input v-model="form.email" /></el-form-item>
        <el-form-item :label="t('auth.password')" prop="password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-button type="primary" size="large" native-type="submit" :loading="loading" style="width: 100%">
          {{ t('auth.createWorkspace') }}
        </el-button>
      </el-form>
      <p class="muted small">{{ t('auth.haveAccount') }} <router-link to="/login">{{ t('auth.signIn') }}</router-link></p>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 40px 16px;
  background: radial-gradient(900px 500px at 50% -10%, #e0e7ff 0%, transparent 70%), var(--op-bg);
}

.corner {
  position: fixed;
  top: 18px;
  right: 20px;
}

.box {
  width: 100%;
  max-width: 420px;
  padding: 32px;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(15, 23, 42, 0.08);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
  font-size: 17px;
}

h2 {
  margin: 22px 0 4px;
  letter-spacing: -0.02em;
}

.box > p {
  margin: 0 0 20px;
  font-size: 13.5px;
}

.small {
  font-size: 13px;
  margin: 18px 0 0;
}
</style>

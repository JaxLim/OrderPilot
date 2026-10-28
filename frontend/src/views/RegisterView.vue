<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuth } from '@/stores/auth'
import { notifyError } from '@/api/http'

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

const rules: FormRules = {
  companyName: [{ required: true, message: 'Company name is required' }],
  tenantCode: [{ required: true, pattern: /^[a-z0-9-]{3,40}$/, message: '3-40 lowercase letters, digits or dashes' }],
  adminName: [{ required: true, message: 'Your name is required' }],
  email: [{ required: true, type: 'email', message: 'A valid email is required' }],
  password: [{ required: true, min: 8, message: 'At least 8 characters' }],
}

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
    <div class="card box">
      <div class="brand"><img src="/favicon.svg" width="30" height="30" alt="" /> OrderPilot</div>
      <h2>Create your workspace</h2>
      <p class="muted">Your data lives in an isolated tenant. You'll be its first admin.</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="Company name" prop="companyName"><el-input v-model="form.companyName" /></el-form-item>
        <el-form-item label="Workspace code" prop="tenantCode">
          <el-input v-model="form.tenantCode" @input="codeTouched = true"><template #prepend>orderpilot/</template></el-input>
        </el-form-item>
        <el-form-item label="Your name" prop="adminName"><el-input v-model="form.adminName" /></el-form-item>
        <el-form-item label="Work email" prop="email"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="Password" prop="password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-button type="primary" size="large" native-type="submit" :loading="loading" style="width: 100%">
          Create workspace
        </el-button>
      </el-form>
      <p class="muted small">Already have an account? <router-link to="/login">Sign in</router-link></p>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 40px 16px;
  background: radial-gradient(circle at 30% 10%, #e0e7ff, var(--op-bg) 60%);
}

.box {
  width: 100%;
  max-width: 440px;
  padding: 32px;
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

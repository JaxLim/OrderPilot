<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { ApiError } from '@/api/http'

const auth = useAuth()
const router = useRouter()
const route = useRoute()
const form = reactive({ email: 'admin@acme.test', password: 'Passw0rd!' })
const loading = ref(false)
const error = ref('')

const demoAccounts = [
  { email: 'admin@acme.test', role: 'Admin', tenant: 'Acme Outdoor' },
  { email: 'manager@acme.test', role: 'Manager', tenant: 'Acme Outdoor' },
  { email: 'staff@acme.test', role: 'Staff', tenant: 'Acme Outdoor' },
  { email: 'viewer@acme.test', role: 'Viewer', tenant: 'Acme Outdoor' },
  { email: 'admin@nova.test', role: 'Admin', tenant: 'Nova Electronics' },
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
}
</script>

<template>
  <div class="auth">
    <section class="hero">
      <div class="brand"><img src="/favicon.svg" width="34" height="34" alt="" /> OrderPilot</div>
      <h1>Orders, inventory and payments for growing commerce teams.</h1>
      <ul>
        <li><b>Oversell-proof stock</b> — atomic reservations under heavy concurrency</li>
        <li><b>Exactly-once effects</b> — idempotent APIs and an outbox-driven event pipeline</li>
        <li><b>Secure multi-tenancy</b> — every workspace is isolated by design</li>
        <li><b>AI Copilot</b> — ask questions about your live business data</li>
      </ul>
      <div class="foot">Java 21 · Spring Boot · MySQL · Redis · RabbitMQ · Vue 3</div>
    </section>

    <section class="panel">
      <div class="box">
        <h2>Sign in</h2>
        <p class="muted">Welcome back. Use a demo account or your own workspace.</p>
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="err" />
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="Email">
            <el-input v-model="form.email" size="large" autocomplete="username" data-testid="login-email" />
          </el-form-item>
          <el-form-item label="Password">
            <el-input v-model="form.password" type="password" size="large" show-password autocomplete="current-password"
                      data-testid="login-password" />
          </el-form-item>
          <el-button type="primary" size="large" native-type="submit" :loading="loading" class="w100"
                     data-testid="login-submit">Sign in</el-button>
        </el-form>
        <div class="demo">
          <div class="demo-title">Demo accounts <span class="muted">(password Passw0rd!)</span></div>
          <div class="chips">
            <button v-for="a in demoAccounts" :key="a.email" type="button" class="chip" @click="pick(a.email)">
              <b>{{ a.role }}</b><span>{{ a.tenant }}</span>
            </button>
          </div>
        </div>
        <p class="muted small">New here? <router-link to="/register">Create a workspace</router-link></p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  min-height: 100%;
}

.hero {
  background: radial-gradient(circle at 20% 20%, #4338ca 0, #1e1b4b 55%, #0f172a 100%);
  color: #e0e7ff;
  padding: 56px 64px;
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 700;
  font-size: 20px;
  color: #fff;
}

.hero h1 {
  font-size: 34px;
  line-height: 1.2;
  letter-spacing: -0.02em;
  color: #fff;
  margin: auto 0 28px;
  max-width: 520px;
}

.hero ul {
  list-style: none;
  padding: 0;
  margin: 0 0 auto;
  display: grid;
  gap: 14px;
  font-size: 15px;
  max-width: 520px;
}

.hero li b {
  color: #fff;
}

.foot {
  font-size: 12.5px;
  color: #a5b4fc;
  margin-top: 40px;
}

.panel {
  display: grid;
  place-items: center;
  padding: 40px;
  background: #fff;
}

.box {
  width: 100%;
  max-width: 400px;
}

.box h2 {
  margin: 0;
  font-size: 26px;
  letter-spacing: -0.02em;
}

.box > p {
  margin: 6px 0 24px;
}

.err {
  margin-bottom: 16px;
}

.w100 {
  width: 100%;
}

.demo {
  margin-top: 26px;
  padding: 14px;
  border: 1px dashed var(--op-border);
  border-radius: 10px;
  background: #fafbff;
}

.demo-title {
  font-size: 12.5px;
  font-weight: 600;
  margin-bottom: 10px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  border: 1px solid var(--op-border);
  background: #fff;
  border-radius: 8px;
  padding: 6px 10px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  font: inherit;
  font-size: 12px;
  transition: border-color 0.15s;
}

.chip:hover {
  border-color: var(--op-primary);
}

.chip span {
  color: var(--op-muted);
  font-size: 11px;
}

.small {
  font-size: 13px;
  margin-top: 18px;
}

@media (max-width: 900px) {
  .auth {
    grid-template-columns: 1fr;
  }

  .hero {
    display: none;
  }
}
</style>

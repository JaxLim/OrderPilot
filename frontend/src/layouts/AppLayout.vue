<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Box, ChatDotRound, Coin, DataAnalysis, Document, Goods, Histogram, SwitchButton, User,
} from '@element-plus/icons-vue'
import { useAuth } from '@/stores/auth'
import NotificationBell from '@/components/NotificationBell.vue'
import LangSwitch from '@/components/LangSwitch.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const auth = useAuth()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)

const nav = [
  { section: 'nav.overview' },
  { to: '/dashboard', label: 'nav.dashboard', icon: DataAnalysis, perm: 'report:read' },
  { to: '/assistant', label: 'nav.assistant', icon: ChatDotRound, perm: 'ai:use', badge: 'AI' },
  { section: 'nav.commerce' },
  { to: '/orders', label: 'nav.orders', icon: Document, perm: 'order:read' },
  { to: '/payments', label: 'nav.payments', icon: Coin, perm: 'order:read' },
  { section: 'nav.catalog' },
  { to: '/products', label: 'nav.products', icon: Goods, perm: 'product:read' },
  { to: '/inventory', label: 'nav.inventory', icon: Box, perm: 'inventory:read' },
  { section: 'nav.workspace' },
  { to: '/team', label: 'nav.team', icon: User, perm: 'user:manage' },
  { to: '/activity', label: 'nav.activity', icon: Histogram, perm: 'audit:read' },
] as const

const visibleNav = computed(() => {
  const items = nav.filter((n) => !('perm' in n) || auth.can(n.perm))
  // drop section headers that have no visible items under them
  return items.filter((n, i) => !('section' in n) || (items[i + 1] && !('section' in items[i + 1])))
})

const initials = computed(() =>
  (auth.me?.displayName ?? '?').split(' ').map((s) => s[0]).join('').slice(0, 2).toUpperCase(),
)

function onResize() {
  collapsed.value = window.innerWidth < 1000
}
onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
})
onUnmounted(() => window.removeEventListener('resize', onResize))

async function logout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="shell" :class="{ collapsed }">
    <aside class="sidebar">
      <div class="brand">
        <img src="/favicon.svg" alt="" width="28" height="28" />
        <span v-if="!collapsed">OrderPilot</span>
      </div>
      <nav>
        <template v-for="(item, i) in visibleNav" :key="i">
          <div v-if="'section' in item" class="section">{{ collapsed ? '' : t(item.section) }}</div>
          <router-link v-else :to="item.to" class="nav-item" :class="{ active: route.path.startsWith(item.to) }"
                       :title="t(item.label)">
            <el-icon :size="18"><component :is="item.icon" /></el-icon>
            <span v-if="!collapsed" class="label">{{ t(item.label) }}</span>
            <span v-if="!collapsed && 'badge' in item" class="badge">{{ item.badge }}</span>
          </router-link>
        </template>
      </nav>
      <div v-if="!collapsed" class="tenant-card">
        <div class="tenant-name">{{ auth.me?.tenant.name }}</div>
        <div class="tenant-plan">{{ t('nav.plan', { plan: auth.me?.tenant.plan }) }} · {{ auth.me?.tenant.code }}</div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="crumb">
          <span class="muted">{{ auth.me?.tenant.name }}</span>
          <span class="sep">/</span>
          <span>{{ route.meta.title ? t(route.meta.title as string) : '' }}</span>
        </div>
        <div class="actions">
          <LangSwitch />
          <NotificationBell />
          <el-dropdown trigger="click">
            <div class="user" data-testid="user-menu">
              <div class="avatar">{{ initials }}</div>
              <div class="who">
                <div class="name">{{ auth.me?.displayName }}</div>
                <div class="role">{{ auth.me ? t(`roles.${auth.me.role}`) : '' }}</div>
              </div>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ auth.me?.email }}</el-dropdown-item>
                <el-dropdown-item divided :icon="SwitchButton" @click="logout">{{ t('common.signOut') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  height: 100%;
}

.sidebar {
  width: 236px;
  flex-shrink: 0;
  background: var(--op-sidebar);
  color: var(--op-sidebar-text);
  display: flex;
  flex-direction: column;
  padding: 18px 12px;
  transition: width 0.2s;
}

.collapsed .sidebar {
  width: 68px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px 18px;
  font-weight: 700;
  font-size: 17px;
  color: #fff;
  letter-spacing: -0.01em;
}

nav {
  flex: 1;
  overflow-y: auto;
}

.section {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #64748b;
  padding: 16px 10px 6px;
  min-height: 12px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 10px;
  margin: 2px 0;
  border-radius: 8px;
  color: var(--op-sidebar-text);
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background: rgba(148, 163, 184, 0.12);
  color: #fff;
}

.nav-item.active {
  background: var(--op-primary);
  color: #fff;
}

.nav-item .label {
  flex: 1;
}

.badge {
  font-size: 10px;
  font-weight: 700;
  background: linear-gradient(135deg, #a855f7, #6366f1);
  color: #fff;
  padding: 1px 6px;
  border-radius: 6px;
}

.tenant-card {
  margin-top: 12px;
  padding: 12px;
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.1);
}

.tenant-name {
  color: #fff;
  font-weight: 600;
  font-size: 13.5px;
}

.tenant-plan {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--op-surface);
  border-bottom: 1px solid var(--op-border);
}

.crumb {
  font-size: 14px;
  font-weight: 500;
}

.crumb .sep {
  margin: 0 8px;
  color: var(--op-subtle);
}

.actions {
  display: flex;
  align-items: center;
  gap: 18px;
}

.user {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  outline: none;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 600;
  font-size: 13px;
}

.who .name {
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.2;
}

.who .role {
  font-size: 11.5px;
  color: var(--op-muted);
}

.content {
  flex: 1;
  overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.12s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

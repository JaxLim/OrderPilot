import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { lang, t } from '@/i18n'
import { watch } from 'vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    { path: '/register', name: 'register', component: () => import('@/views/RegisterView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: 'nav.dashboard', perm: 'report:read' } },
        { path: 'orders', name: 'orders', component: () => import('@/views/OrdersView.vue'), meta: { title: 'nav.orders', perm: 'order:read' } },
        { path: 'products', name: 'products', component: () => import('@/views/ProductsView.vue'), meta: { title: 'nav.products', perm: 'product:read' } },
        { path: 'inventory', name: 'inventory', component: () => import('@/views/InventoryView.vue'), meta: { title: 'nav.inventory', perm: 'inventory:read' } },
        { path: 'payments', name: 'payments', component: () => import('@/views/PaymentsView.vue'), meta: { title: 'nav.payments', perm: 'order:read' } },
        { path: 'assistant', name: 'assistant', component: () => import('@/views/AssistantView.vue'), meta: { title: 'nav.assistant', perm: 'ai:use' } },
        { path: 'team', name: 'team', component: () => import('@/views/TeamView.vue'), meta: { title: 'nav.team', perm: 'user:manage' } },
        { path: 'activity', name: 'activity', component: () => import('@/views/ActivityView.vue'), meta: { title: 'nav.activity', perm: 'audit:read' } },
        { path: 'forbidden', name: 'forbidden', component: () => import('@/views/ForbiddenView.vue'), meta: { title: 'nav.forbidden' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuth()
  if (to.meta.public) {
    return auth.isAuthenticated && (to.name === 'login' || to.name === 'register') ? '/dashboard' : true
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!auth.me) {
    try {
      await auth.loadMe()
    } catch {
      return { name: 'login' }
    }
  }
  const perm = to.meta.perm as string | undefined
  if (perm && !auth.can(perm)) {
    return to.name === 'dashboard' ? '/orders' : '/forbidden'
  }
  return true
})

function setTitle() {
  const key = router.currentRoute.value.meta.title as string | undefined
  document.title = key ? `${t(key)} · OrderPilot` : 'OrderPilot'
}
router.afterEach(setTitle)
watch(lang, setTitle)

export default router

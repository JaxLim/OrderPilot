import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/stores/auth'

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
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: 'Dashboard', perm: 'report:read' } },
        { path: 'orders', name: 'orders', component: () => import('@/views/OrdersView.vue'), meta: { title: 'Orders', perm: 'order:read' } },
        { path: 'products', name: 'products', component: () => import('@/views/ProductsView.vue'), meta: { title: 'Products', perm: 'product:read' } },
        { path: 'inventory', name: 'inventory', component: () => import('@/views/InventoryView.vue'), meta: { title: 'Inventory', perm: 'inventory:read' } },
        { path: 'payments', name: 'payments', component: () => import('@/views/PaymentsView.vue'), meta: { title: 'Payments', perm: 'order:read' } },
        { path: 'assistant', name: 'assistant', component: () => import('@/views/AssistantView.vue'), meta: { title: 'AI Copilot', perm: 'ai:use' } },
        { path: 'team', name: 'team', component: () => import('@/views/TeamView.vue'), meta: { title: 'Team', perm: 'user:manage' } },
        { path: 'activity', name: 'activity', component: () => import('@/views/ActivityView.vue'), meta: { title: 'Activity', perm: 'audit:read' } },
        { path: 'forbidden', name: 'forbidden', component: () => import('@/views/ForbiddenView.vue'), meta: { title: 'No access' } },
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

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · OrderPilot` : 'OrderPilot'
})

export default router

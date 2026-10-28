import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api, TOKEN_KEY } from '@/api/http'
import type { LoginResponse, Me } from '@/api/types'

export const useAuth = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const me = ref<Me | null>(null)

  const isAuthenticated = computed(() => !!token.value)
  const permissions = computed(() => new Set(me.value?.permissions ?? []))

  function can(permission: string): boolean {
    return permissions.value.has(permission)
  }

  function applyLogin(res: LoginResponse) {
    token.value = res.token
    me.value = res.user
    localStorage.setItem(TOKEN_KEY, res.token)
  }

  async function login(email: string, password: string) {
    applyLogin(await api.post<LoginResponse>('/auth/login', { email, password }))
  }

  async function register(payload: {
    companyName: string
    tenantCode: string
    adminName: string
    email: string
    password: string
  }) {
    applyLogin(await api.post<LoginResponse>('/auth/register', payload))
  }

  async function loadMe() {
    if (!token.value) return
    me.value = await api.get<Me>('/auth/me')
  }

  async function logout() {
    try {
      await api.post('/auth/logout')
    } catch {
      /* token may already be invalid */
    }
    token.value = null
    me.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  return { token, me, isAuthenticated, can, login, register, loadMe, logout }
})

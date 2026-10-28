import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiEnvelope } from './types'

export const TOKEN_KEY = 'orderpilot.token'

export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public status: number,
  ) {
    super(message)
  }
}

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (res) => res,
  (err: AxiosError<ApiEnvelope<unknown>>) => {
    const status = err.response?.status ?? 0
    const body = err.response?.data
    if (status === 401 && !err.config?.url?.startsWith('/auth/login')) {
      localStorage.removeItem(TOKEN_KEY)
      if (!location.pathname.startsWith('/login')) {
        location.href = `/login?redirect=${encodeURIComponent(location.pathname)}`
      }
    }
    const message = body?.message ?? (status ? `Request failed (${status})` : 'Network error — is the backend running?')
    return Promise.reject(new ApiError(body?.code ?? 'NETWORK', message, status))
  },
)

async function unwrap<T>(p: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  const res = await p
  return res.data.data
}

export const api = {
  get: <T>(url: string, params?: object) => unwrap<T>(http.get(url, { params })),
  post: <T>(url: string, body?: unknown, headers?: Record<string, string>) =>
    unwrap<T>(http.post(url, body ?? {}, { headers })),
  put: <T>(url: string, body?: unknown) => unwrap<T>(http.put(url, body ?? {})),
}

/** Shows a toast for an API error; returns the error for further handling. */
export function notifyError(e: unknown): ApiError | undefined {
  if (e instanceof ApiError) {
    ElMessage.error(e.message)
    return e
  }
  ElMessage.error(String(e))
  return undefined
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

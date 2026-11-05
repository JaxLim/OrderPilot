/**
 * Demo sign-in helpers exist only in `npm run dev` and builds made with VITE_DEMO_MODE=true (Docker Compose).
 * Everywhere else the values below fold to empty and the credentials are not in the bundle.
 */
export const DEMO_PASSWORD = import.meta.env.DEV || import.meta.env.VITE_DEMO_MODE === 'true' ? 'Passw0rd!' : ''

export const DEMO_ACCOUNTS: { email: string; role: string; tenant?: string }[] =
  import.meta.env.DEV || import.meta.env.VITE_DEMO_MODE === 'true'
    ? [
        { email: 'admin@acme.test', role: 'ADMIN' },
        { email: 'manager@acme.test', role: 'MANAGER' },
        { email: 'staff@acme.test', role: 'STAFF' },
        { email: 'viewer@acme.test', role: 'VIEWER' },
        { email: 'admin@nova.test', role: 'ADMIN', tenant: 'Nova' },
      ]
    : []

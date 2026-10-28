# PROJECT_STATUS

_Last updated: 2026-10-28 (session 1)_

## Summary

V1 is functionally complete end to end: backend, frontend, AI copilot, demo data, automated tests, Docker Compose, CI
workflow and documentation. Everything listed under *Completed* was exercised by the tests or manual checks recorded below.

## Completed features

**Backend (Spring Boot 3.5.9, Java 21, modular monolith)**
- Auth: register workspace (tenant + first admin), login (BCrypt, rate-limited, anti-enumeration), JWT (HS384, 8 h), logout via
  Redis `jti` deny-list, `/api/auth/me`.
- RBAC: ADMIN / MANAGER / STAFF / VIEWER → 13 permissions, enforced with `@PreAuthorize` on every endpoint; user management
  (create, change role/status, last-admin protection).
- Multi-tenancy: tenant from JWT → `TenantContext`; `tenant_id` in every query; `TenantGuardInterceptor` rejects unscoped SQL.
- Catalog: CRUD, archive/restore, search/filter/paging, categories; Redis cache-aside (jitter, null sentinel, after-commit evict,
  hit/miss Micrometer counters).
- Inventory: atomic reserve/release/commit/restock/adjust via conditional UPDATEs, sorted locking, movement ledger, low-stock
  events on threshold crossing.
- Orders: create (server-side pricing, idempotent), state machine with SQL CAS, cancel, ship, complete, search by
  status/keyword/date, payment-window expiry via RabbitMQ TTL+DLX and a sweeper.
- Payments: simulated gateway (methods, forced decline, risk limit), 3-step flow with no lock held during the gateway call,
  idempotent pay, double-charge protection, auto-refund on pay/timeout race, refunds with restock.
- Messaging: transactional outbox, relay with publisher confirms + returns + back-off + FAILED parking, SKIP LOCKED,
  idempotent consumers, DLQs; notification consumer.
- Dashboard analytics (KPIs, period-over-period change, daily series, status mix, top products, categories, low stock),
  Redis-cached 30 s.
- AI copilot: Spring AI ChatClient + 7 read-only tenant-bound `@Tool`s, history, per-user rate limit, offline analyst fallback.
- Audit log; outbox health endpoint; OpenAPI/Swagger UI; actuator health.
- Demo data seeder: 2 tenants, 5 users, 34 products, ~75 days of orders/payments, low-stock items, notifications.

**Frontend (Vue 3 + TS + Element Plus + ECharts)**
- Login (demo-account chips), register workspace, permission-aware sidebar and routing, 403 page.
- Dashboard, Orders (tabs, search, date range, create dialog, detail drawer with pay/decline/cancel/ship/complete/refund and
  timeline), Products (CRUD dialog), Inventory (levels with stock bars, adjustments, movement ledger), Payments, AI Copilot chat
  (Markdown rendering, tool provenance, mode badge), Team, Activity (audit log + event pipeline), notification bell (polling).

**DevOps / docs**
- Multi-stage Dockerfiles (backend, frontend + nginx reverse proxy), `docker-compose.yml` with health checks.
- GitHub Actions: backend `mvn verify` (Testcontainers), frontend build, E2E job (compose + Playwright).
- README, docs/ARCHITECTURE.md, docs/INTERVIEW_GUIDE.md, .env.example.

## Verified test results (this session)

| Suite | Command | Result |
|---|---|---|
| Backend unit + integration (Testcontainers: MySQL 8.4, Redis 7, RabbitMQ 3) | `cd backend && mvn test` | **28 passed, 0 failed** |
| Frontend type-check + production build | `cd frontend && npm run build` | **passes** |
| Playwright E2E against dev stack (Vite :5173 + backend :8080, demo data) | `cd frontend && npm run e2e` | **5 passed** |
| Docker Compose full stack (fresh volumes, image build, health checks) | `docker compose up -d --build --wait` | **all 5 containers healthy** |
| Playwright E2E against the Docker stack (nginx :8081) | `E2E_BASE_URL=http://localhost:8081 npm run e2e` | **5 passed** |
| Manual API smoke test | curl script (login, dashboard, idempotent create, oversell rejection, pay, double-pay rejection, notifications, RBAC 403, cross-tenant 404, AI) | all as expected |
| Manual UI check in browser | login → dashboard → create order → pay → AI copilot | works |

## Known issues / limitations

- First `docker compose up --build` downloads all Maven dependencies inside the build container (took ~12 min on this
  network); later builds reuse the cached layer.
- GitHub Actions workflow is written but has **not been run on GitHub** (no remote configured in this environment).
- The LLM path of the copilot (with a real `OPENAI_API_KEY`) is implemented but **not exercised** here (no key available);
  the offline analyst path is tested. If the provider call fails, the service falls back to offline mode with a notice.
- Payment gateway is simulated; no webhooks/reconciliation job for PENDING payments stuck by a crash between TX1 and TX2
  (they stop blocking new attempts after 2 minutes).
- Demo-seeded historical orders are inserted directly (no outbox events, no per-order ledger rows).
- RabbitMQ queue arguments are immutable: changing `ORDER_PAYMENT_TIMEOUT` requires deleting `op.order.delay` (or
  `docker compose down -v`).
- Notifications use 10 s polling (no SSE/WebSocket push yet).
- Frontend bundle: Element Plus and ECharts are imported fully (~300 kB + ~180 kB gzip); could be tree-shaken.

## Exact next steps

1. Push to GitHub and confirm the three CI jobs pass; fix any Linux-specific issues.
2. Run the copilot with a real key (`OPENAI_API_KEY=... docker compose up -d`) and verify tool calling end to end; add a
   `@SpringBootTest` with a stubbed `ChatModel` that asserts tools are invoked.
3. Add a reconciliation job for `payment.status = 'PENDING'` older than N minutes (query provider, then settle or fail).
4. Replace notification polling with SSE (`SseEmitter`) fed by the notification consumer.
5. Add an ArchUnit test enforcing module boundaries (e.g. only `inventory` may use `InventoryMapper`).
6. Pre-aggregate daily sales into a summary table updated by an event consumer; point the dashboard at it.
7. Tree-shake Element Plus (unplugin-vue-components) and ECharts imports to cut bundle size.

## How to resume

```bash
docker compose up -d mysql redis rabbitmq
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev      # http://localhost:5173, admin@acme.test / Passw0rd!
cd backend && mvn test                          # needs Docker
```

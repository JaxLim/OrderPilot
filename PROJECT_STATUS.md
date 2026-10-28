# PROJECT_STATUS

_Last updated: 2026-10-28 (session 1, part 2: i18n, DeepSeek, login redesign)_

## Summary

V1 is functionally complete end to end: backend, frontend (English / 简体中文), AI copilot on DeepSeek, demo data, automated
tests, Docker Compose, CI workflow and documentation. Everything listed under *Completed* was exercised by the tests or
manual checks recorded below.

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
  idempotent consumers, DLQs; notification consumer (stores structured `params` for client-side i18n, migration V2).
- Dashboard analytics (KPIs, period-over-period change, daily series, status mix, top products, categories, low stock),
  Redis-cached 30 s.
- AI copilot: Spring AI ChatClient → **DeepSeek** (`deepseek-chat`, OpenAI-compatible) + 7 read-only tenant-bound `@Tool`s,
  history, UI-language-aware prompt (answers in Chinese when `lang=zh`), per-user rate limit, bilingual offline analyst
  fallback. Key only via `AI_API_KEY` env / git-ignored `.env`; `AiKeyEnvironmentPostProcessor` decides LLM vs offline;
  errors logged through a key redactor; `/api/ai/status` exposes mode/provider/model only.
- Audit log; outbox health endpoint; OpenAPI/Swagger UI; actuator health.
- Demo data seeder: 2 tenants, 5 users, 34 products, ~75 days of orders/payments, low-stock items, notifications.

**Frontend (Vue 3 + TS + Element Plus + ECharts + vue-i18n)**
- English / 简体中文 across every page, dialog, toast, chart, Element Plus component and relative time; EN/中文 toggle on the
  login page and top bar, remembered in `localStorage`, defaults to the browser language; API errors localised by code;
  notifications rendered from params.
- Redesigned login: single centred card (logo, email, password, sign-in, compact demo-account chips, language toggle).
- Register workspace, permission-aware sidebar and routing, 403 page.
- Dashboard, Orders (tabs, search, date range, create dialog, detail drawer with pay/decline/cancel/ship/complete/refund and
  timeline), Products (CRUD dialog), Inventory (levels, adjustments, movement ledger), Payments, AI Copilot chat (Markdown,
  tool provenance, provider/model badge), Team, Activity (audit log + event pipeline), notification bell.

**DevOps / docs**
- Multi-stage Dockerfiles (backend, frontend + nginx reverse proxy), `docker-compose.yml` with health checks; Compose passes
  `AI_*` from `.env` to the backend container only; `.env` excluded from Git and Docker build contexts.
- GitHub Actions: backend `mvn verify` (Testcontainers), frontend build, E2E job (compose + Playwright).
- README, docs/ARCHITECTURE.md (incl. §11 i18n), docs/INTERVIEW_GUIDE.md, `.env.example`.

## Verified test results (this session)

| Suite | Command | Result |
|---|---|---|
| Backend unit + integration (Testcontainers: MySQL 8.4, Redis 7, RabbitMQ 3) | `cd backend && mvn test` | **31 passed, 0 failed** |
| Frontend type-check + production build | `cd frontend && npm run build` | **passes** |
| Docker Compose full stack (fresh volumes, image build, health checks) | `docker compose up -d --build --wait` | **all 5 containers healthy** |
| Playwright E2E against the Docker stack (nginx :8081, DeepSeek LLM mode) | `E2E_BASE_URL=http://localhost:8081 npm run e2e` | **6 passed** (incl. EN↔中文 switch) |
| DeepSeek live check | `/api/ai/status` → `llm / DeepSeek / deepseek-chat`; English and Chinese questions answered with tool calls on real data | works |
| Key leak checks | `sk-` in backend logs: 0; in served/built frontend and nginx container: 0; in Git-tracked files: 0; `git check-ignore .env` ✓ | clean |
| Manual UI check | new login page, Chinese dashboard/orders/drawer/AI copilot screenshots (docs/screenshots/*-zh.png) | works |

## Known issues / limitations

- **Rotate the DeepSeek key**: it was shared in plain text in the chat that set it up. It is stored only in the local,
  git-ignored `.env`.
- The E2E AI test only asserts that an answer with tool provenance appears (LLM wording is non-deterministic).
- LLM answers can still be imperfect (e.g. phrasing); the prompt lists the real screens to stop it inventing features.
- First `docker compose up --build` downloads all Maven dependencies inside the build container (~12 min on this network).
- GitHub Actions workflow is written but has **not been run on GitHub** (no remote configured).
- Payment gateway is simulated; no reconciliation job for PENDING payments stuck by a crash between TX1 and TX2.
- Demo-seeded historical orders are inserted directly (no outbox events, no per-order ledger rows).
- Business data (product/customer names, audit details, payment failure reasons) is not translated.
- RabbitMQ queue arguments are immutable: changing `ORDER_PAYMENT_TIMEOUT` requires deleting `op.order.delay`.
- Notifications use 10 s polling (no SSE yet). Bundle is not tree-shaken (Element Plus + ECharts imported fully).

## Exact next steps

1. Rotate the DeepSeek key and update `.env`; push to GitHub and confirm CI (no key needed: tests force offline mode).
2. Add a `@SpringBootTest` with a stubbed `ChatModel` asserting that tool calls and the `lang` instruction are sent.
3. Stream copilot answers (SSE) for faster perceived latency; persist conversations.
4. Reconciliation job for `payment.status = 'PENDING'` older than N minutes.
5. Replace notification polling with SSE; ArchUnit module-boundary test; pre-aggregated daily sales table.
6. Tree-shake Element Plus / ECharts.

## How to resume

```bash
cp .env.example .env                            # then set AI_API_KEY (optional)
docker compose up -d mysql redis rabbitmq
cd backend && mvn spring-boot:run               # reads ../.env
cd frontend && npm install && npm run dev       # http://localhost:5173, admin@acme.test / Passw0rd!
cd backend && mvn test                          # needs Docker; never calls the real LLM
```

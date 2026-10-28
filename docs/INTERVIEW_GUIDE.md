# OrderPilot — interview preparation guide

Use this to present the project and to answer the deep-dive questions interviewers ask. Every claim below points to code or a
test you can open during the interview.

---

## 1. The 60-second pitch

> "OrderPilot is a multi-tenant SaaS for order and inventory management. It's a Spring Boot modular monolith on MySQL, Redis and
> RabbitMQ with a Vue 3 dashboard. The interesting part is correctness under failure and concurrency: stock can't be oversold
> even with 40 concurrent buyers for 10 units, every unsafe endpoint is idempotent, domain events use a transactional outbox
> with publisher confirms and idempotent consumers, unpaid orders are cancelled by a RabbitMQ TTL/dead-letter delay queue, and
> tenant isolation is enforced by a MyBatis interceptor that rejects SQL without a tenant filter. There's also an AI copilot on
> Spring AI with read-only, tenant-bound function calling. All of this is verified by Testcontainers integration tests against
> real MySQL, Redis and RabbitMQ, plus Playwright end-to-end tests."

## 2. Demo script (5 minutes)

1. Log in as `admin@acme.test` → **Dashboard**: KPIs, revenue trend, status mix, top products, low stock (Redis-cached 30 s).
2. **Orders → New order**: pick a product, show the live total. Create → drawer shows *Awaiting payment* and the auto-cancel
   countdown. Open **Inventory**: the product's *reserved* went up and *available* went down.
3. In the drawer, tick **Simulate decline** → Charge → 402 + failed attempt recorded. Untick → Charge → *Paid*; reserved stock
   is committed. Bell icon: "Payment received" notification arrived via RabbitMQ.
4. **Mark shipped**, then show **Inventory → Movement ledger** (RESERVE → DEDUCT).
5. **Activity → Event pipeline**: outbox events `SENT`, attempts = 1.
6. **AI Copilot**: "Which products need restocking?" → table + "Data from getLowStockProducts()".
7. Log in as `viewer@acme.test` → no "New order" button, Team page forbidden. Log in as `admin@nova.test` → completely
   different catalog (tenant isolation).
8. Show the tests: `InventoryConcurrencyIT`, `IdempotencyIT`, `OrderLifecycleIT.unpaidOrderIsCancelledByTheRabbitMqDelayQueue`.

## 3. Deep-dive questions and answers

### Concurrency & inventory

**Q: How do you prevent overselling?**
A single conditional update: `UPDATE inventory SET available = available - ?, reserved = reserved + ? WHERE tenant_id = ? AND
product_id = ? AND available >= ?`. InnoDB takes an exclusive row lock and re-evaluates the predicate on the latest committed
row, so check-and-decrement is atomic. 0 rows affected ⇒ `INSUFFICIENT_STOCK`. A `CHECK (available >= 0)` constraint is the
belt-and-braces. Test: `InventoryConcurrencyIT.concurrentBuyersNeverOversell`.

**Q: Why not optimistic locking with a version column?**
Under contention on a hot SKU most attempts fail and retry — wasted round trips and tail latency. The conditional update
succeeds as long as stock exists; contention just serialises on the row lock for microseconds. I keep `version` for
observability and could use it for optimistic edits of other fields.

**Q: Why not a Redis distributed lock or Lua deduction?**
A lock adds a network hop, lease-expiry edge cases, and still needs the DB write. Redis Lua pre-deduction is the right tool for
flash sales at tens of thousands of QPS, but it creates two sources of truth that need reconciliation. For this load profile the
database row lock is simpler and provably correct. Scale-up path: Redis pre-deduction + async order creation via MQ +
DB as final arbiter.

**Q: Multi-item orders — deadlocks?**
Two orders `[A,B]` and `[B,A]` can deadlock. I merge duplicate lines and sort by product id before reserving, so all
transactions acquire locks in the same global order. Test: `multiLineOrdersInOppositeOrderDoNotDeadlock` (30 concurrent orders).

**Q: What if the second line of an order has no stock?**
The reservation is inside the order transaction; the exception rolls back the first line's reservation too.
Test: `failedLineRollsBackTheWholeOrder`.

### Transactions & consistency

**Q: Where are your transaction boundaries?**
Order creation = one local transaction: reserve stock + insert order/items + outbox row. Inventory/outbox methods are
`Propagation.MANDATORY`, so calling them without a surrounding transaction is a bug that fails immediately. Payment is split in
two transactions around the gateway call, because holding a row lock across a network call is a classic outage cause.

**Q: Payment succeeds at the same moment the order times out. What happens?**
Both paths use compare-and-set on status (`WHERE status = 'PENDING_PAYMENT'`). Exactly one wins. If timeout wins, TX2 sees the
order CAS fail, refunds the captured charge and marks the payment `REFUNDED` with a reason. No lost money, no paid-but-cancelled
order with stock released.

**Q: How do you avoid charging twice?**
(1) Idempotency-Key replay for the same request, (2) TX1 locks the order row (`FOR UPDATE`) and rejects if a recent PENDING or
SUCCEEDED payment exists, (3) order CAS guarantees only one payment can move the order to PAID. Test:
`declinedPaymentCanBeRetriedWithTheSameKey` asserts exactly one `SUCCEEDED` row.

### Idempotency

**Q: How does your Idempotency-Key work?**
`SET key PROCESSING NX EX 24h` claims it; concurrent duplicate gets `409 REQUEST_IN_PROGRESS`; on success the response JSON is
stored and replayed; on failure the key is deleted so the client can retry. Keys are namespaced per tenant and per operation.
**Q: And if Redis is down?** Correctness doesn't depend on Redis: a `UNIQUE(tenant_id, idempotency_key)` index rejects the
duplicate insert and the service returns the existing order. Test: `concurrentDuplicatesCreateExactlyOneOrder`.

### Messaging

**Q: Why an outbox instead of publishing to RabbitMQ in the service?**
Publishing inside the transaction can send an event for a rollback; publishing after commit can lose the event if the process
dies. The outbox row commits atomically with the business change; a relay publishes it later. That converts a distributed
transaction into "at-least-once delivery", which consumers handle with idempotency.

**Q: How do you know the broker actually got the message?**
Publisher confirms (correlated) + `mandatory` returns. SENT only after ack and routed. Otherwise exponential back-off
(2^n s, capped 5 min), FAILED after 10 attempts, visible on the Activity page for replay.

**Q: Multiple app instances?**
The relay selects with `FOR UPDATE SKIP LOCKED`, so instances partition the backlog without double-sending. Ordering is
per-batch best effort; consumers don't rely on global ordering.

**Q: How are consumers idempotent?**
`INSERT IGNORE INTO consumed_message(message_id, consumer)` inside the consumer's transaction. If it inserted 0 rows, it's a
duplicate → skip. If the handler fails, the marker rolls back too, so the redelivery is processed. Poison messages: 3 retries
with back-off, then dead-lettered to `*.dlq`.

**Q: How does the delayed cancellation work without the delayed-message plugin?**
`order.created` is bound to a queue with `x-message-ttl` = payment timeout and no consumer; expired messages dead-letter to the
timeout queue. Caveat I'd mention: per-queue TTL means all messages share one delay (fine here); per-message TTLs in one queue
suffer head-of-line blocking — use the delayed-message plugin or multiple TTL tiers then. A sweeper job is the safety net.

### Caching

**Q: Cache consistency strategy?**
Cache-aside; on update, delete the key *after commit* (TransactionSynchronization). Deleting before commit lets a concurrent
reader re-cache the old row. The remaining window (reader loads old value before commit, writes after delete) is bounded by
TTL; delayed double-delete or binlog-based invalidation (Canal) closes it further if needed.

**Q: Penetration, breakdown, avalanche?**
Penetration: cache a `__NULL__` sentinel for missing ids (2 min). Avalanche: TTL jitter (+0–5 min). Breakdown on a hot key: TTL
is long and invalidation is per key; a mutex rebuild (SETNX) would be the next step. Stock is never cached.

### Multi-tenancy & security

**Q: How do you guarantee tenant isolation?**
Tenant from signed JWT only → ThreadLocal context cleared in `finally` → explicit `tenant_id` in every query → MyBatis
interceptor that rejects tenant-table SQL without `tenant_id` (unit-tested), with `*Global` naming for the few deliberate
cross-tenant statements. Cross-tenant ids return 404. Test: `tenantsCannotSeeOrTouchEachOthersData`.

**Q: Shared schema vs schema-per-tenant vs DB-per-tenant?**
Shared schema: cheapest, simplest migrations, best for many small tenants; risk is leakage → mitigated by the guard rail.
Schema/DB-per-tenant: stronger isolation and per-tenant backup/restore, but N migrations and connection pools. A hybrid (big
tenants get their own DB via a routing DataSource) is the usual evolution.

**Q: JWT logout?**
JWTs are stateless, so logout stores the `jti` in a Redis deny-list until the token's natural expiry. Test:
`logoutRevokesTheTokenImmediately`.

**Q: RBAC design?**
Roles are bundles of permissions; controllers check permissions (`hasAuthority('payment:refund')`). Moving the mapping to tables
for custom roles doesn't touch controllers. Test: `rolesAreEnforcedByPermission`.

### AI

**Q: How does the copilot avoid making up numbers?**
The system prompt requires tools for every figure; tools are the only data source; responses list which tools ran (provenance).
Tools are read-only and their tenant id is injected server-side, so prompt injection can neither write data nor reach another
tenant. History is capped, requests are rate-limited per user, and failures fall back to a deterministic analyst.

**Q: How do you handle the LLM API key?**
Environment variable only (`AI_API_KEY`), sourced from a git-ignored `.env` locally and from the orchestrator's secret store in
production. It's never in source or YAML, never sent to the browser (the UI asks `/api/ai/status`, which returns only
mode/provider/model), and never logged (provider errors pass through a regex redactor; there's a unit test for it). Tests pin
`AI_API_KEY=` so CI can't spend money or leak the key. Next step in production: a secret manager (Vault / AWS Secrets Manager)
and key rotation.

**Q: How did you add Chinese support?**
vue-i18n with typed message catalogs (`zh: typeof en`, so missing translations fail the build), Element Plus and dayjs locales
switched together, and error localisation by stable error code rather than translating server strings. Notifications keep
structured params so they render in the reader's language. The AI gets the UI language and answers in it; the offline analyst
has Chinese templates.

**Q: What would you add for production?**
Streaming responses (SSE), conversation persistence, evaluation set of business questions with expected tool calls,
token/cost metrics, RAG over product docs, a confirm-before-execute pattern for any future write tools.

### Architecture

**Q: Why a modular monolith?**
Strong consistency between stock and orders, small team, single DB. Modules expose services, not tables; integration across
modules is event-driven. Notification is already a pure event consumer and could be extracted with zero changes to producers.

**Q: How would you scale this to 100×?**
Stateless app → horizontal scale behind a LB (relay is already multi-instance safe). Read replicas for analytics; pre-aggregated
daily tables instead of on-the-fly GROUP BY; shard by `tenant_id` when one MySQL is not enough; hot-SKU path via Redis
pre-deduction; RabbitMQ quorum queues; observability with Micrometer/OTel (cache hit counter already exported).

## 4. Known trade-offs to volunteer (shows judgement)

- Payment provider is simulated; a real one needs webhooks + signature verification + reconciliation of stuck PENDING payments.
- Analytics queries run on the OLTP DB (cached 30 s); fine for demo scale.
- Demo-seeded historic orders are inserted directly (no outbox events) for speed.
- Changing `ORDER_PAYMENT_TIMEOUT` requires deleting `op.order.delay` (RabbitMQ queue arguments are immutable).

## 5. Numbers to remember

- 28 backend tests (unit + Testcontainers), 5 Playwright E2E tests.
- Concurrency test: 40 parallel buyers, 10 units → exactly 10 orders, 0 negative stock.
- Idempotency test: 15 parallel identical requests → 1 order.
- Delay queue test: payment timeout 4 s in tests; order cancelled and stock released via the dead-letter path only.

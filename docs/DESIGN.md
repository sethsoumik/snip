# snip — design notes

This doc explains the decisions behind the URL shortener: how the codes are
generated, how the read/write paths work, and what I'd change if it needed to
scale past a single box. It's a "why", not just a "what".

## 1. Goals

- produce the shortest possible link for a given URL
- keep codes non-guessable and collision-free
- keep the redirect path fast (that's the 99% of traffic)
- keep the whole thing simple enough to reason about and test

Explicitly out of scope for this version: user accounts, custom slugs, per-link
permissions, and a full analytics product. Those are features, not architecture.

## 2. Shape of the system

```
 POST /api/v1/links  ──▶ ┌──────────────────────┐
 GET  /{slug}        ──▶ │      Spring Boot     │
                         │      (Java 21)       │
                         │                      │
                         │  Controller          │
                         │    │                 │
                         │    ▼                 │
                         │  Service             │
                         │   ├─ ShortCodeGen ───┼──▶ (pure function)
                         │   ├─ UrlCache ───────┼──▶ Redis
                         │   ├─ LinkRepository ─┼──▶ PostgreSQL
                         │   └─ ClickCounter ───┼──▶ async pool
                         └──────────────────────┘
```

Controllers are thin; the business rules live in `ShortLinkService`. The codec is
a pure function, which makes it the easiest thing in the codebase to test.

## 3. How codes are generated

The algorithm is "minimal-length seeded-permutation base64". The point is to get
base62's shortness without base62's predictability, and a hash's
non-guessability without a hash's collisions.

- **Alphabet** — the 64 URL-safe characters `A-Z a-z 0-9 - _` (base64url). At 6
  bits per character it's the densest set that's safe to drop in a URL, which is
  what keeps codes short.
- **Encoding** — the numeric id is written out in base-64 with no padding, so
  length follows magnitude: ids 0–63 are one character, 64–4095 are two, and so
  on. Codes are never longer than they need to be.
- **Permutation** — before anything is encoded, the alphabet is shuffled with a
  secret seed. Sequential ids therefore don't produce obvious codes, but the
  mapping stays a strict bijection, so collisions are impossible by construction.

That last bit is the whole trick: a counter is minimal and collision-free but
enumerable; a hash is non-enumerable but fixed-length and colliding. Shuffling
the alphabet of a counter gets both properties at once.

### Code length and scale

You'll see one-character codes like `/q` and `/t` right now because the encoding
is base-64 with no padding — length tracks the number itself, not a fixed width.
The first 64 links are one character, then two, and so on:

| Links | Code length |
|---|---|
| 1 – 64 | 1 |
| 65 – 4,096 | 2 |
| 4,097 – 262,144 | 3 |
| 262,145 – 16,777,216 | 4 |
| 16.7M – 1.07B | 5 |
| 1.07B – 68.7B | 6 |

That's the point of using the full 64-symbol base64url alphabet: it's the densest
set of characters that are always safe in a URL, so it yields the shortest
possible codes. A base62 shortener needs more characters for the same id; a hash
needs a fixed length plus collision handling; a UUID is 22+ characters. This
stays shorter at every scale and never has a collision.

For "millions of users", the codec isn't the constraint — the id source is. A
single Postgres sequence is a single-writer, so past some write throughput you'd
swap it for Snowflake ids or a Redis `INCR`. The codec doesn't care where the id
comes from: feed it any non-negative integer and it comes back as the shortest
possible, non-guessable code.

## 4. Data model

```sql
links (
  id         BIGSERIAL PRIMARY KEY,
  slug       VARCHAR(16) UNIQUE NOT NULL,
  url        TEXT NOT NULL,
  url_hash   VARCHAR(64) UNIQUE NOT NULL,  -- sha256(url)
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ,
  clicks     BIGINT NOT NULL DEFAULT 0
)
```

A couple of details worth calling out:

- `id` is the source of truth for the code. It comes from a Postgres sequence
  (`link_seq`), which stays contiguous, which keeps codes minimal.
- `url_hash` gives idempotency: a unique index on the hash of the URL means the
  same URL always finds its existing row, without having to index the full URL
  text (which can be huge).
- `slug` is unique-indexed so a redirect lookup is an index probe.

Because the slug is derived from `id`, I fetch the next id from the sequence
*before* saving and set both `id` and `slug` on the entity up front. Early on I
had a bug where the slug was set after `save()`, which meant Hibernate flushed an
insert with a null slug and Postgres rejected it.

## 5. Write path

1. Normalize and validate the URL (scheme must be `http`/`https`, host required).
2. Compute `sha256(url)`.
3. Look up by hash — if it exists, return the existing code (idempotent).
4. Otherwise fetch `nextval('link_seq')`, build the entity with `id` and `slug`
   already set, and save.
5. Warm the cache and return.

## 6. Read path (the hot path)

```
GET /{slug}
  1. Redis GET slug        ── hit ──▶ 302
  2. Postgres findBySlug   ── miss
  3. write back to Redis (cache-aside, 30-day TTL)
  4. queue async click increment
  5. 302 Found → Location
```

- Cache-aside keeps Postgres off the hot path.
- Redis is fail-open: if it throws, the service just falls through to the DB.
- Redirects are `302` on purpose, not `301`, so clients keep coming back and
  clicks keep getting counted.
- Click counting is fire-and-forget on a small bounded pool, so it adds nothing
  to redirect latency.

### Cache design: why Redis cache-aside, and nothing else

**What it is** — a remote, distributed cache (L2) using Redis, filled lazily on the
read path. There's deliberately no L1 in-process cache.

**Why cache-aside (not write-through / write-behind)** — reads dominate this
workload; creates are rare relative to redirects. Cache-aside only touches the
cache when a redirect actually happens, so we never pre-warm keys nobody clicks or
pay a cache write on the create path. Write-through would add a Redis write to
every create and complicate failure handling; write-behind means queuing writes
and accepting eventual-consistency risk for no real gain here.

**Why Redis (L2) and not a local cache (L1)** — the service is stateless and meant
to scale horizontally. An in-process cache (Caffeine/Guava) lives per instance, so
each replica keeps its own cold cache and the hit rate drops, and you inherit a
cache-invalidation problem across instances. A shared Redis cache means one
instance warms a key and every other instance benefits. The cost is a network hop,
which is negligible next to the DB round-trip it avoids.

**Why fail-open** — Redis is an accelerator, not a correctness dependency. If it's
down we fall through to Postgres and still redirect correctly. Losing the cache
degrades latency, never availability.

**Why not Memcached / CDN / browser cache** — Redis has TTLs and is already in the
stack. A CDN or browser cache would break click counting (and we already use 302,
not 301, for exactly that reason).

**When we'd add an L1** — only if a very hot key started saturating Redis or the
network; then a small Caffeine layer in front of Redis (L1 + L2) with short TTLs
would make sense. At this scale it's unnecessary complexity.

## 7. Observability

There's a password-protected `/admin` dashboard (a terminal-styled login page,
session-based auth, user `admin`) that polls two JSON endpoints. `OpsMetrics`
keeps process-local counters plus a bounded ring buffer of recent events:

- postgres and redis connection status
- total links and total clicks (queried from Postgres)
- cache hits vs misses, and the hit rate — i.e. the Redis-vs-DB split
- links created, 404s, and uptime
- a live log of `CREATE` / `CACHE` / `DB` / `CLICK` / `404` events

Spring Boot Actuator is also enabled for health/liveness/readiness, which is what
Render's health check hits.

## 8. Scaling

- The API is stateless, so it scales horizontally behind a load balancer.
- A single Postgres sequence is the correct id source at this scale. If writes
  become the bottleneck, swap it for Snowflake ids or a Redis `INCR` — the codec
  doesn't care where the id comes from.
- If one database stops being enough, shard by hashing the slug (consistent
  hashing). The bijective codec needs no cross-shard coordination.
- Rate limiting on create is the obvious next addition (Bucket4j or Redis).

## 9. Security

- URL scheme allow-list blocks `javascript:` / `data:` and friends.
- Slugs are validated against the base64url alphabet before touching the DB, and
  every query is parameterized (JPA derived queries / JPQL `:slug`), so there's no
  SQL injection surface.
- The admin dashboard and its endpoints sit behind a login page with
  session-based auth and a constant-time password comparison; the password comes
  from `ADMIN_PASSWORD`.
- The seeded shuffle makes codes hard to enumerate, but it's obfuscation, not
  authentication — it doesn't replace auth on any management endpoint.

## 10. Trade-offs

| Decision | Picked | Rejected | Why |
|---|---|---|---|
| Codec | permuted base64 | base62 / hash / UUID | shortest + non-guessable + no collisions |
| Id source | DB sequence | Snowflake / Redis INCR | simple and correct; swap when scale demands |
| Cache | Redis cache-aside (L2) | Caffeine L1 / write-through / CDN | shared + fail-open; L1 adds per-instance invalidation |
| Click analytics | async in-DB | Kafka + time-series | v1 simplicity; queue it when volume grows |
| Migrations | `ddl-auto: update` + `schema.sql` | Flyway/Liquibase | fine for a demo; switch for real releases |

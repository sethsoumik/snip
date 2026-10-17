# snip — interview Q&A

Notes for talking through this project in an interview. I wrote these to stay
honest about what's original and what's just a deliberate, well-understood
decision, because the second thing reads a lot better than overclaiming the first.

## Is the short-code algorithm unique?

Not really. The core idea — shuffle an alphabet with a secret seed, then do base
conversion on a numeric id — is the same trick that `hashids` / `sqids` uses. It's
a known technique, and the "minimal length, no padding" part is just standard
bijective base-N encoding. So I would never pitch it as "something no one else
has done," because anyone who knows sqids will see through that immediately.

What I *do* claim is that it's a deliberate choice with three concrete wins:

- **shortest possible** — a 64-symbol base64url alphabet is the densest set of
  characters that are always safe in a URL (6 bits per char), denser than base62
- **collision-free by construction** — it's a bijection, not a hash, so there's
  no collision handling at all
- **non-guessable** — the seed shuffle hides the underlying sequence

That last point is the one that matters in practice: a plain base62 counter is
trivially enumerable (`/1`, `/2`, `/3`...), a truncated hash has fixed length and
collisions, and this gets the counter's minimal length *and* a hash's
non-guessability without a hash's downsides.

## So what's actually the interesting part?

The engineering around it, not the codec. Clean layering, a cache-aside read path
that fails open to Postgres, idempotent creates, async click counting, and an
observability story (admin dashboard with live metrics and connection status).
Those are the things an architect will actually probe, and they're the parts of
this project that hold up.

## Why do codes start at one character?

Because it's base-64 with no padding, so length tracks the number itself, not a
fixed width. The first 64 links are one character, the next 4,096 are two, and so
on. You see `/q` and `/t` while the table is nearly empty; that's correct, not a
bug.

| Links | Code length |
|---|---|
| 1 – 64 | 1 |
| 65 – 4,096 | 2 |
| 4,097 – 262,144 | 3 |
| 262,145 – 16,777,216 | 4 |
| 16.7M – 1.07B | 5 |
| 1.07B – 68.7B | 6 |

## Does it scale to millions of users?

The codec does, trivially — it's a pure integer↔string function and never has a
collision at any size. The thing that *doesn't* scale forever is the id source. A
single Postgres sequence is a single writer, so past some write throughput it
becomes the bottleneck. That's a property of the id generator, not the codec.

## What breaks first under load?

The write path. Reads are served from Redis (fail-open), so they scale
horizontally. Writes funnel through one sequence, so if the create rate gets high
enough, that's your constraint. The fix is to swap the id source — Snowflake ids
or a Redis `INCR` — and the codec doesn't change at all, because it doesn't care
where the number came from.

## Why not base62 / hash / UUID?

- base62 counter: shortest-ish but enumerable
- truncated hash: fixed length and collisions, needs re-hash logic
- UUID: long and not URL-friendly

The seeded-permutation base64 approach keeps minimal length and adds
non-guessability without introducing collisions. It's the intersection of the
three, and there's a library (sqids) built on the same insight, which is a good
sign the idea is sound rather than a bad sign.

## Why 302 and not 301?

`302` (temporary) so clients keep coming back to us on every request, which is
what lets click counting work. A `301` gets cached by browsers and the redirect
short-circuits without hitting the server again.

## Why cache-aside and fail-open?

Cache-aside keeps Postgres off the hot path: hit Redis first, fall back to the DB
on a miss, then backfill. It's "fail-open" because if Redis throws, the service
just falls through to Postgres instead of erroring — so Redis is an accelerator,
not a hard dependency. That's a real availability decision: losing the cache
degrades latency, not correctness.

## How does idempotency work?

Each URL is hashed with SHA-256, and `url_hash` has a unique index. On create I
look up by hash first; if it exists I return the existing code, otherwise I insert.
So the same URL always maps to the same code no matter how many times it's
submitted, without ever indexing the full (potentially huge) URL text.

## Why async click counting?

The redirect is the hot path and should stay as fast as possible. Click increments
run on a small bounded thread pool (`@Async`), so they never add latency to the
redirect. The trade-off is that click counts are eventually consistent — if the
process dies, a few in-flight increments might be lost. For a URL shortener that's
an acceptable trade; if it mattered, I'd queue them (Kafka/PubSub) instead.

## What about SQL injection / security?

Every query is parameterized — JPA derived queries (`findBySlug`,
`findByUrlHash`) and JPQL with a bound `:slug` (`incrementClicks`). On top of
that, slugs are validated against the base64url alphabet before touching the DB,
and the redirect route itself is constrained by a regex. The URL input is
allow-listed to `http`/`https` with a required host, which blocks
`javascript:`/`data:` and friends. The admin area uses a login page with a
constant-time password comparison.

## How does the admin dashboard work?

`/admin` is a terminal-styled page behind a login (session-based, `ADMIN_PASSWORD`).
It polls two JSON endpoints every couple seconds: one for metrics and one for a
live event log. `OpsMetrics` keeps process-local counters plus a bounded ring
buffer of `CREATE` / `CACHE` / `DB` / `CLICK` / `404` events, and the controller
folds in the persisted totals (links, clicks) and the Postgres/Redis connection
status from the DB and a Redis `PING`. Spring Boot Actuator handles the
health/liveness/readiness checks that Render pings.

## What would you change for a real production release?

- **Migrations** — swap `ddl-auto: update` for Flyway/Liquibase
- **Id source** — Snowflake or Redis `INCR` once the sequence becomes the write
  bottleneck
- **Rate limiting** — Bucket4j or Redis to throttle abusive creates
- **Click analytics** — offload to a queue + time-series store instead of
  incrementing rows in place
- **Auth** — move the admin auth from a hand-rolled filter to Spring Security if
  the surface grows (roles, CSRF, brute-force protection)

Those are all known, named trade-offs rather than surprises, which is the point:
the current version is deliberately simple, and I can say exactly when each
simplification stops being the right call.

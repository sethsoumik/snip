# snip

A URL shortener I built to make the *shortest possible* links without any of the
usual trade-offs. It's Java 21 + Spring Boot, and the interesting part is the way
it generates codes — more on that below.

- codes are as short as they can possibly be, and grow only when the link count does
- codes aren't guessable (sequential ids are scrambled)
- zero collisions, because it's a bijection rather than a hash
- Redis cache on the redirect path, but it fails open to Postgres if Redis is down
- idempotent creates (the same URL always maps to the same code)
- async click counting so redirects stay fast
- a password-protected `/admin` dashboard with metrics and a live log
- unit + controller tests

---

## The short-code algorithm

Most shorteners pick one of two things: a plain base62 counter (short, but you can
just walk the sequence) or a truncated hash (fixed length, and you have to deal
with collisions). I didn't love either, so I used a third option:

1. **A 64-symbol alphabet** — `A–Z a–z 0–9 - _` (base64url). That's 6 bits per
   character, denser than base62, which is what lets codes stay short.
2. **Minimal-length base-64 encoding** — the row id is converted directly to
   base-64 with no padding. Length is driven by magnitude, not a fixed width: the
   first 64 links get a 1-character code, the next 4,096 get 2, and so on.
3. **A seeded shuffle of the alphabet** — the alphabet is permuted using a secret
   seed before encoding. Sequential ids still produce short codes, but they no
   longer look sequential.

The encoding is a pure integer↔string bijection, so there's no collision handling
at all. The full write-up is in [`docs/DESIGN.md`](docs/DESIGN.md), and
[`docs/QA.md`](docs/QA.md) has an interview-style Q&A covering the trade-offs.

---

## Architecture

```
            browser
               │
               ▼
        ┌─────────────┐
        │ Spring Boot │   Java 21, stateless
        │ (Web MVC)   │
        └─────┬───┬───┘
              │   │
        ┌─────▼┐  ┌▼────────────┐
        │ Redis│  │ PostgreSQL  │   async click counter
        │ cache│  │ (source)    │   on a separate pool
        └──────┘  └─────────────┘
```

The hot path (`GET /{slug}`) hits Redis first and only falls back to Postgres on a
miss. Click increments are queued onto a small async pool so they never add
latency to a redirect.

## Tech stack

| Layer | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.3 (Web, Data JPA, Data Redis, Validation, Actuator) |
| Database | PostgreSQL |
| Cache | Redis (optional — fail-open) |
| Build | Maven |
| Tests | JUnit 5, Mockito, AssertJ, MockMvc |

## Getting started

You need JDK 21 (Maven comes via the included wrapper, so `./mvnw` works without a
global Maven install).

### Docker (easiest)

```bash
docker compose up --build
```

This brings up Postgres, Redis, and the app together. The app is on
`http://localhost:8080`.

### Run it directly

```bash
cp src/main/resources/application-example.yml src/main/resources/application.yml
./mvnw spring-boot:run
```

`application.yml` is gitignored (it can hold real credentials), so copy the
committed example and fill in your own values. Postgres and Redis need to be
running, or set `SPRING_DATASOURCE_*` / `REDIS_*`.

## API

| Method | Path | What it does |
|---|---|---|
| `POST` | `/api/v1/links` | create a short link |
| `GET` | `/api/v1/links/{slug}` | fetch link metadata |
| `GET` | `/{slug}` | 302 redirect to the target |
| `GET` | `/actuator/health` | health check |
| `GET` | `/admin` | dashboard (login-protected) |
| `GET` | `/api/v1/admin/metrics` | dashboard metrics JSON |
| `GET` | `/api/v1/admin/logs` | live activity log JSON |

Example:

```bash
curl -X POST http://localhost:8080/api/v1/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

```json
{ "slug": "K", "shortUrl": "http://localhost:8080/K", "url": "https://example.com", "clicks": 0 }
```

## Admin dashboard

`/admin` is a small terminal-styled dashboard behind a login page (user `admin`,
password from `ADMIN_PASSWORD`, session-based). It shows:

- postgres and redis connection status
- total links and total clicks (from Postgres)
- cache hits vs misses, and the hit rate (this is the Redis-vs-DB split)
- links created, 404s, and uptime
- a rolling live log of the last few hundred `CREATE` / `CACHE` / `DB` / `CLICK` /
  `404` events

The counters are process-local, so they reset on restart; the link and click
totals come straight from the database.

## Configuration

Everything is env-driven. Copy [application-example.yml](src/main/resources/application-example.yml)
to `application.yml` and fill in your values, or set the matching env vars. Real
credentials are never committed (`application.yml` is gitignored).

| Env var | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL for Postgres |
| `SPRING_DATASOURCE_USERNAME` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `REDIS_URL` | full Redis URL (e.g. Upstash `rediss://...`); optional |
| `ADMIN_PASSWORD` | password for the `/admin` dashboard (user `admin`) |
| `APP_BASE_URL` | public base URL used when building short links |
| `SHORT_CODE_SEED` | secret seed that shuffles the code alphabet |

When `REDIS_URL` is set (e.g. to an Upstash `rediss://` URL) the app enables TLS
automatically. Otherwise it falls back to `REDIS_HOST` / `REDIS_PORT` for local
Redis.

## Tests

```bash
./mvnw test
```

- `ShortCodeGeneratorTest` — round-trip, minimal length, uniqueness, bad input
- `ShortLinkServiceTest` — idempotency, cache hit/miss, not-found, URL validation
- `ShortLinkControllerTest` — create/get endpoints and validation

## Deployment

A [`render.yaml`](render.yaml) blueprint and a multi-stage [`Dockerfile`](Dockerfile)
are included. The app runs fine on Render's free tier with Supabase/Neon for
Postgres and Upstash for Redis. The Dockerfile forces IPv4 because Render's free
tier has no IPv6 egress and Supabase can resolve to IPv6 first.

## Project layout

```
src/main/java/in/soumikseth/links/
├── LinkShortenerApplication.java
├── admin/         AdminController, OpsMetrics
├── config/        AsyncConfig, RedisConfig, AdminAuthFilter
├── controller/    RedirectController, ShortLinkController
├── core/          ShortCodeGenerator  (the codec)
├── domain/        Link entity
├── dto/           request/response + metrics records
├── exception/     NotFoundException, ApiExceptionHandler
├── repository/    LinkRepository
└── service/       ShortLinkService, UrlCache, ClickCounter
```

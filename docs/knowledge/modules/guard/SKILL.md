---
name: guard
description: Rate limiting, idempotency (SingleFlight), concurrency gating, guard metrics — protective layer
---

# Guard

Protective middleware layer: rate limiting, request idempotency, concurrency gating, and internal observability.

## Code Map — Core

| Class | Role |
|-------|------|
| `GenerationGuardService` | Main entry: `executeMangaAgentRun()`, `executeSceneGeneration()` — wraps idempotency + rate limit |
| `IdempotencyService` | Redis-backed idempotency: deduplicate requests by key, follower wait |
| `MangaGenerationConcurrencyGate` | Semaphore-based concurrency limiter (default 4) |
| `RequestCanonicalizer` | Normalize request content into stable keys |
| `GenerationRequestKeyBuilder` | Build idempotency keys from user + operation |

## Code Map — Metrics & Events

| Class | Role |
|-------|------|
| `GuardEventRecorder` | Record guard events (rate limit hits, idempotency hits) |
| `GuardEventService` | Query guard events |
| `GuardEventPersistenceService` | Persist guard events to DB |
| `GuardMetricsService` | Aggregate metrics by time window |
| `GuardMetricBucketService` | Bucket metrics for time-series queries |
| `GuardStatsService` | Internal stats endpoint data |
| `Hashing` | SHA-256 utility for key hashing |
| `GuardNonTerminalException` | Non-terminal exception for guard failures that should not abort |

## Key Mechanisms

### Idempotency (SingleFlight)
- `@SingleFlight` AOP aspect — first request executes, subsequent requests wait for result or timeout
- Redis-backed with configurable TTL
- Config: `artverse.single-flight.enabled`, `artverse.idempotency.*`

### Rate Limiting
- `@RateLimit` AOP aspect — Redis Lua sliding window
- Per-IP or per-userId key resolution
- Config: `artverse.rate-limit.enabled`, `artverse.rate-limit.default-window-seconds`

### Concurrency Gate
- `MangaGenerationConcurrencyGate` — `Semaphore` with configurable permits
- Applied to manga generation and agent runs
- Config: `artverse.manga-generation.max-concurrent-jobs`

## Invariants

- Guard must fail-open when Redis is unavailable (log warning, allow request).
- Idempotency keys include user ID + operation + canonical content hash.
- Concurrency gate acquire must have a timeout — never block indefinitely.
- Guard events are internal observability only — not exposed to user-facing APIs.

---
name: infrastructure
description: Configuration, common utilities, MinIO storage, media management, prompt engineering
---

# Infrastructure

Cross-cutting concerns: Spring configuration, exception handling, AOP aspects, object storage, media management, and prompt building.

## Sub-Modules

### config/ — Spring Boot Configuration

| Class | Role |
|-------|------|
| `ArtVerseProperties` | All `@ConfigurationProperties(prefix = "artverse")` — typed config tree |
| `AgentScopeConfig` | AgentScope workspace, compaction config, default DeepSeek model bean |
| `CorsConfig` | CORS settings from `artverse.cors-origins` |
| `RedisConfig` | Redis connection factory, RedisTemplate |
| `SaTokenConfig` | Sa-Token interceptor, password encoder, Redis session storage |
| `StpInterfaceImpl` | Role/permission provider for Sa-Token |
| `BCryptPasswordEncoder` | BCrypt hashing (jBCrypt, no Spring Security) |
| `TaskExecutorConfig` | Virtual thread executors: `mangaGenerationExecutor` |

### common/ — Shared Utilities

| Class | Role |
|-------|------|
| `BusinessException` | Runtime exception with HTTP status code |
| `GlobalExceptionHandler` | `@ControllerAdvice` — maps exceptions to HTTP responses |
| `@RateLimit` | AOP annotation for rate limiting |
| `RateLimitAspect` | Redis sliding window rate limiter |
| `@SingleFlight` | AOP annotation for idempotency |
| `SingleFlightAspect` | Redis-backed request deduplication |

### storage/ — MinIO Object Storage

| Class | Role |
|-------|------|
| `ObjectStorageService` | Interface: `store()`, `getUrl()`, `delete()` |
| `MinioStorageService` | MinIO implementation |
| `StoredObject` | Record: key, bucket, metadata |

### media/ — Media File Management

| Class | Role |
|-------|------|
| `MediaStorageService` | Local path resolution, unique filename generation |

### prompt/ — Prompt Engineering

| Class | Role |
|-------|------|
| `MangaPromptBuilder` | Builds image generation prompts from storyboard scenes |
| `MangaPromptTemplates` | Prompt templates for manga styles |
| `MangaPromptPolicy` | Prompt validation and quality enforcement |

## Invariants

- `config` classes must not depend on `application`, `domain`, `agent`, or `api`.
- `common` classes must be framework-agnostic where possible.
- `storage` interface must allow swapping implementations (currently only MinIO).
- Prompt templates should be externalized to `src/main/resources/prompts/` where possible.

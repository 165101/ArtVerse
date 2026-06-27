# Guard — Call Graph

## Called By (inbound)

```
api controllers (via @RateLimit AOP)
  → RateLimitAspect

api/MangaAgentController, api/MangaGenerationController
  → GenerationGuardService.executeMangaAgentRun()

application/workflow/MangaWorkflowOrchestrator
  → GenerationGuardService.executeMangaAgentRun()

application/SceneService
  → GenerationGuardService.executeSceneGeneration()

agent/gateway/AgentScopeAgentFactory
  → GenerationGuardService
```

## Calls Into (outbound)

```
GenerationGuardService
  → IdempotencyService           (Redis idempotency)
  → MangaGenerationConcurrencyGate (semaphore gate)
  → GuardEventRecorder           (event recording)
  → config/ArtVerseProperties    (config)

IdempotencyService
  → RedisTemplate                (Spring Data Redis)
  → config/ArtVerseProperties

RateLimitAspect
  → RedisTemplate                (Lua script execution)

GuardEventRecorder
  → GuardEventPersistenceService
  → GuardMetricsService

GuardMetricsService
  → GuardMetricBucketService
```

## Redis Dependency

```
guard/*
  → RedisTemplate (Spring Data Redis)
    → Redis (localhost:6379, db 0)
```

Redis is critical for guard operations. When Redis is unavailable, guard must degrade gracefully (fail-open).

## Key Dependencies

| Downstream | Purpose |
|-----------|---------|
| `config` | ArtVerseProperties (all guard config) |
| `common` | BusinessException, AOP annotations |
| `domain` | User for guard operations |
| `persistence` | Repository lookups |
| **Redis** | Idempotency, rate limiting |

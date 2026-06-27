# Infrastructure — Call Graph

## config/

### Called By (inbound)
```
EVERYTHING — config is the most-depended-upon module:
  api, application, workflow, agent, ai, guard, common, storage, media
  → ArtVerseProperties, SaTokenConfig, TaskExecutorConfig
```

### Calls Into (outbound)
```
config/*
  → persistence/UserRepository    (StpInterfaceImpl for role resolution)
  → Spring Boot auto-configuration
```

## common/

### Called By (inbound)
```
api, application, workflow, guard, agent, ai, media, prompt
  → BusinessException, GlobalExceptionHandler
  → @RateLimit, @SingleFlight annotations
```

### Calls Into (outbound)
```
common/*
  → config/ArtVerseProperties
  → Spring AOP (for aspects)
  → RedisTemplate (for RateLimit and SingleFlight aspects)
```

## storage/

### Called By (inbound)
```
api      → ObjectStorageService (reference image upload)
application → ObjectStorageService (manga image persistence)
```

### Calls Into (outbound)
```
MinioStorageService
  → config/ArtVerseProperties
  → MinIO Java SDK
```

## media/

### Called By (inbound)
```
api         → MediaStorageService (static media paths)
application → MediaStorageService (image path resolution)
```

### Calls Into (outbound)
```
MediaStorageService
  → config/ArtVerseProperties
  → common/BusinessException
```

## prompt/

### Called By (inbound)
```
application → MangaPromptBuilder (manga gen prompts)
ai          → MangaPromptPolicy  (prompt validation)
```

### Calls Into (outbound)
```
prompt/*
  → common/BusinessException
```

## Overall Dependency Graph

```
                    ┌──────────────────┐
                    │  infrastructure  │
                    │  (config, common,│
                    │   storage, media,│
                    │   prompt)        │
                    └────────┬─────────┘
                             │
            ┌────────────────┼────────────────┐
            │                │                │
        api/*          application/*      guard
```

Infrastructure is the foundation. Every functional module depends on it, but it depends on very little else.

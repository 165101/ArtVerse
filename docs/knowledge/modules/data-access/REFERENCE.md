# Data Access — Call Graph

## Called By (inbound)

```
api controllers        → UserRepository, StoryRepository, ChapterRepository
application services   → ALL repositories (primary consumer)
application/workflow   → MangaImageRepository, ChapterRepository
agent integration      → ChapterRepository, MangaImageRepository
guard                  → Repository lookups for idempotency
config                 → UserRepository
```

## Calls Into (outbound)

```
persistence (all repositories)
  → domain.*     (entity return types + method parameters only)
  → Spring Data JPA (framework interface)
```

The persistence layer is the thinnest dependency layer — it only imports `domain.*` and Spring Data interfaces.

## Dependency Graph

```
                    ┌──────────────┐
                    │  persistence │
                    └──────┬───────┘
                           │
                      domain.*
                           │
            ┌──────────────┼──────────────┐
            │              │              │
        api/*      application/*     agent/*
```

**Direction**: Everything depends on persistence, but persistence only depends on domain.

## Key Consumers by Repository

| Repository | Primary Consumers |
|-----------|------------------|
| `UserRepository` | `AuthService`, `CurrentUserService`, `SaTokenConfig` |
| `UserApiKeyRepository` | `ApiKeyService` |
| `StoryRepository` | `StoryService`, `ChapterService`, `ExportImportService` |
| `ChapterRepository` | `ChapterService`, `ChapterAccessService`, `AgentWorkspaceSyncService`, `MangaWorkflowOrchestrator`, `MangaGenerationService` |
| `MangaImageRepository` | `MangaGenerationService`, `AgentWorkspaceSyncService`, `MangaWorkflowOrchestrator`, `MangaContextTools` |
| `MangaAgentConversationRepository` | `MangaAgentConversationService`, `MangaAgentService` |
| `MangaAgentMessageRepository` | `MangaAgentConversationService` |
| `MangaAgentRunRepository` | `MangaAgentRunService` |
| `MangaAgentRunEventRepository` | `MangaAgentRunEventPublisher` |

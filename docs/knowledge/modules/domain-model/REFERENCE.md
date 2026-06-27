# Domain Model — Call Graph

## Called By (inbound)

The domain model is the most-depended-upon module. Every layer references entities:

```
api controllers       → domain.* (request/response mapping)
application services  → domain.* (business logic)
application/workflow  → domain.* (execution context)
agent integration     → domain.* (MangaAgentRuntimeContext carries entity IDs)
data-access           → domain.* (repository return types)
guard                 → domain.* (User, Chapter for guard operations)
```

## Calls Into (outbound)

```
domain entities call into:
  → domain.*     (internal references: Story → Chapter, etc.)
  → application.workflow (MangaAgentRun references MangaWorkflowRoute)
```

The domain layer is nearly pure — it imports only other domain classes and Lombok annotations.

## Dependency Graph

```
                         ┌─────────────┐
                         │   domain    │
                         └──────┬──────┘
                                │
        ┌───────────┬───────────┼───────────┬───────────┐
        │           │           │           │           │
    api/*    application  persistence   agent/*     guard
```

Every module (except `config`, `common`, `ai`, `storage`, `media`, `prompt`) depends on `domain`.

## Key Consumers

| Consumer Module | Primary Entities Used |
|----------------|----------------------|
| `api` | `User`, `Story`, `Chapter`, `MangaAgentConversation`, `MangaAgentRun` |
| `application` | All entities |
| `application/workflow` | `MangaAgentConversation`, `MangaAgentMessage`, `MangaAgentRun`, `Chapter`, `Story` |
| `persistence` | All entities (return types) |
| `agent` | `Chapter`, `MangaImage`, `Story` (via sync service) |
| `guard` | `User`, `Chapter` (for guard operations) |

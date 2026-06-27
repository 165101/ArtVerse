---
name: data-access
description: Spring Data JPA repositories — database access layer
---

# Data Access

Spring Data JPA repositories. Thin interfaces extending `JpaRepository` or `JpaSpecificationExecutor`.

## Repositories

| Repository | Entity | Notable Query Methods |
|-----------|--------|----------------------|
| `UserRepository` | `User` | `findByUsername()`, `findByEmail()` |
| `UserApiKeyRepository` | `UserApiKey` | `findByUserIdAndProvider()` |
| `StoryRepository` | `Story` | `findByUserId()`, `findByIdForIdempotency()` |
| `ChapterRepository` | `Chapter` | `findByIdForIdempotency()`, query by story/status |
| `StoryAssetGroupRepository` | `StoryAssetGroup` | `findByStoryId()` |
| `CharacterProfileRepository` | `CharacterProfile` | `findByAssetGroupId()` |
| `MangaImageRepository` | `MangaImage` | `findByChapterIdOrderByImageNumberAsc()` |
| `ChatMessageRepository` | `ChatMessage` | `findByUserAndChapter()` |
| `MangaAgentConversationRepository` | `MangaAgentConversation` | `findFirstByUserIdAndChapterIdAndStatusOrderByUpdatedAtDesc()` |
| `MangaAgentMessageRepository` | `MangaAgentMessage` | `findByConversationIdAndRequestIdAndRole()`, `findByConversationId()` |
| `MangaAgentRunRepository` | `MangaAgentRun` | `findByConversationIdAndRequestId()`, `findFirstByConversationIdAndStatusInOrderByCreatedAtDesc()` |
| `MangaAgentRunEventRepository` | `MangaAgentRunEventRecord` | `findByRunIdOrderByCreatedAtAsc()` |
| `ImageGenRecordRepository` | `ImageGenRecord` | Records query by user/story |

## Key Patterns

- **`findByIdForIdempotency()`**: Optimized query for idempotency checks (smaller projection than full entity load).
- **`@Transactional(readOnly = true)`**: All query methods are read-only at the caller level.
- **NO custom business logic**: Repositories are pure data access — no validation, no transformation.

## Invariants

- Repositories must not contain `@Transactional` annotations — transaction boundaries belong to services.
- `@ManyToMany` fetches must use `LEFT JOIN FETCH` to avoid N+1 queries.
- New query methods must use Spring Data method naming conventions — avoid `@Query` unless the query is complex.

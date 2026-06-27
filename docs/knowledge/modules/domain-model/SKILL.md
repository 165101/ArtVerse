---
name: domain-model
description: JPA entities and enums — Story, Chapter, MangaAgentConversation, MangaAgentRun, etc.
---

# Domain Model

JPA entities and enums. Pure data objects — no business logic.

## Entities

| Entity | Table | Key Relationships |
|--------|-------|-------------------|
| `User` | `users` | Has API keys, stories |
| `Story` | `stories` | Belongs to User, has Chapters |
| `Chapter` | `chapters` | Belongs to Story, has MangaImages, scenes |
| `StoryAssetGroup` | `story_asset_groups` | Belongs to Story, N↔M Chapters, has CharacterProfiles |
| `CharacterProfile` | `character_profiles` | Belongs to StoryAssetGroup |
| `MangaImage` | `manga_images` | Belongs to Chapter, ordered by `imageNumber` |
| `MangaAgentConversation` | `manga_agent_conversations` | Belongs to User + Chapter, has messages + runs |
| `MangaAgentMessage` | `manga_agent_messages` | Belongs to Conversation, role + content |
| `MangaAgentRun` | `manga_agent_runs` | Belongs to Conversation, status + lifecycle |
| `MangaAgentRunEventRecord` | `manga_agent_run_events` | Belongs to Run, event payload for recovery |
| `ChatMessage` | `chat_messages` | Belongs to User + Chapter, AI novel chat |
| `UserApiKey` | `user_api_keys` | Belongs to User, encrypted provider keys |
| `ImageGenRecord` | `image_gen_records` | Belongs to User + Story, standalone gen history |

## Enums

| Enum | Values |
|------|--------|
| `ChapterStatus` | Chapter lifecycle states |
| `ColorMode` | `BW`, `COLOR` |
| `ContentSource` | `CHAT`, `NOVEL` |
| `MangaStyle` | Manga art style labels |
| `MessageRole` | `USER`, `ASSISTANT`, `SYSTEM` |
| `MangaAgentConversationStatus` | `ACTIVE`, `ARCHIVED` |
| `MangaAgentRunStatus` | `RUNNING`, `WAITING_USER`, `SUCCEEDED`, `DEGRADED`, `FAILED`, `CANCELLED`, `INTERRUPTED` |
| `Role` | `USER`, `ADMIN` |
| `StorageProvider` | `MINIO`, `LOCAL` |

## Key Domain Methods

- `Chapter.novelContentOrJoinedMessages()` — returns `novelContent` if present, otherwise joins chat messages. This is the canonical source for agent context.
- `User.getRole()` — returns `Role` enum for Sa-Token permission checks.

## Invariants

- Entities must NOT contain business logic — they are data carriers only.
- `@ManyToMany` relationships need `LEFT JOIN FETCH` in repository queries (e.g., `StoryAssetGroup` → `CharacterProfile`).
- Entity IDs are `Long` (auto-increment). Do not expose them as natural keys externally — use UUIDs (e.g., `conversationId`, `requestId`).

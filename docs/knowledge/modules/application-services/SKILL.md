---
name: application-services
description: Core service layer — auth, chapter, story, character, chat, novel, export/import, API key management
---

# Application Services

Core business logic services excluding agent workflow and manga generation (see separate modules).

## Code Map

| Class | Role |
|-------|------|
| `AuthService` | Registration, login, password validation, BCrypt |
| `ApiKeyService` | Encrypt/decrypt user API keys (DeepSeek, Coze) |
| `ChapterService` | Chapter CRUD, content source management |
| `ChapterAccessService` | Visibility checks (chapter ownership) |
| `StoryService` | Story CRUD, workspace management |
| `NovelService` | Novel content import, character counting |
| `CharacterProfileService` | Character profile resolution, inheritance |
| `ChatService` | AI novel-writing chat (non-agent) |
| `AssetGroupService` | Story asset group management |
| `WorksService` | Published works management |
| `SquareService` | Public content discovery |
| `ExportImportService` | Story ZIP export/import |
| `CurrentUserService` | Resolve current user from Sa-Token session |
| `RefreshTokenService` | Refresh token lifecycle |
| `ImageGenService` | Standalone image generation |
| `SceneService` | Storyboard scene generation + management |
| `StructuredStoryboardService` | Structured page/panel normalization |
| `MangaImageStorageService` | Manga image persistence to MinIO |

## Key Patterns

- **Service isolation**: Each service is a `@Service` with `@RequiredArgsConstructor`. No circular dependencies.
- **Transaction boundaries**: `@Transactional(readOnly = true)` on query methods, `@Transactional` on mutations. Lazy fields must be extracted before crossing transaction boundaries.
- **Visibility checks**: `ChapterAccessService.requireVisible()` enforces chapter ownership before mutations.
- **API key encryption**: `ApiKeyService` encrypts user API keys at rest using AES, decrypts on demand.

## Invariants

- Services must not depend on API-layer classes (controllers, DTOs).
- `@Transactional` scope must not cross thread boundaries (`executor.submit()`).
- Character profile resolution follows inheritance chain: chapter → asset group → story defaults.

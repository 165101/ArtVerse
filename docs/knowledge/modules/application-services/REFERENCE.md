# Application Services — Call Graph

## Called By (inbound)

```
api controllers
  → AuthService            (login, register)
  → ChapterService         (chapter CRUD)
  → StoryService           (story CRUD)
  → NovelService           (novel import)
  → CharacterProfileService
  → ChatService            (AI chat)
  → AssetGroupService
  → WorksService
  → SquareService
  → ExportImportService
  → CurrentUserService     (user resolution)
  → ApiKeyService          (key management)
  → ImageGenService
  → SceneService           (storyboard scenes)
  → StructuredStoryboardService
  → MangaImageStorageService

application/workflow
  → SceneService
  → StructuredStoryboardService
  → ChapterAccessService

agent integration
  → CharacterProfileService
  → SceneService
  → StructuredStoryboardService
  → ChapterAccessService
```

## Calls Into (outbound)

```
application services
  → domain.*              (JPA entities)
  → persistence.*         (repositories)
  → config.*              (ArtVerseProperties)
  → common.*              (BusinessException)
  → guard.*               (GenerationGuardService)
  → ai.*                  (CozeClient for scene generation)
  → prompt.*              (MangaPromptBuilder)
  → storage.*             (MinIO)
  → media.*               (media path resolution)
```

## Key Dependencies

| Dependency | Purpose |
|-----------|---------|
| `*Repository` (persistence) | Database access |
| `ArtVerseProperties` | Application configuration |
| `GenerationGuardService` | Idempotency + concurrency gates |
| `CozeClient` | Scene generation via Coze workflow |
| `MangaPromptBuilder` | Prompt construction |
| `ObjectStorageService` | Image persistence to MinIO |
| `BCryptPasswordEncoder` | Password hashing |

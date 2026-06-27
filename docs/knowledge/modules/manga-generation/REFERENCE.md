# Manga Generation — Call Graph

## Called By (inbound)

```
api/MangaGenerationController
  → MangaGenerationService.generateMangaStream()

api/ImageGenController
  → ImageGenService

api/StoryboardController
  → SceneService
  → StructuredStoryboardService

application/workflow agent tools
  → SceneService.generateScenes()
  → StructuredStoryboardService.normalize()
```

## Calls Into (outbound)

```
MangaGenerationService
  → MangaPromptBuilder           (prompt construction)
  → WebClientImage2Client        (AI image API)
  → MangaImageStorageService     (MinIO upload)
  → MangaImageRepository         (persist image metadata)
  → ChapterRepository            (update chapter)
  → MangaGenerationConcurrencyGate (concurrency gate)
  → MangaGenerationJob           (subscriber management)
  → SseEmitter                   (SSE streaming)

SceneService
  → CozeClient                   (Coze workflow for scene generation)
  → ChapterRepository

MangaImageStorageService
  → MinioStorageService          (MinIO client)
  → MediaStorageService          (path resolution)

ImageGenService
  → WebClientImage2Client        (AI image API)
  → MangaImageStorageService

StructuredStoryboardService
  → (pure normalization logic, no downstream services)
```

## Key Dependencies

| Downstream | Purpose |
|-----------|---------|
| `ai-clients` | Coze (scene gen), WebClientImage2 (image gen) |
| `prompt-engineering` | MangaPromptBuilder |
| `data-access` | ChapterRepository, MangaImageRepository |
| `storage` | MinioStorageService |
| `media` | MediaStorageService |
| `guard` | MangaGenerationConcurrencyGate |

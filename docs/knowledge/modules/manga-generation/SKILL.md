---
name: manga-generation
description: Manga image generation pipeline — SSE streaming, batch job management, prompt building, image persistence
---

# Manga Generation Pipeline

End-to-end manga image generation: storyboard → prompts → AI image API → MinIO storage.

## Code Map

| Class | Role |
|-------|------|
| `MangaGenerationService` | Core pipeline: batch generation, SSE streaming, failure recovery |
| `MangaGenerationJob` | Active job tracking with multiple SSE subscribers per chapter |
| `MangaImageStorageService` | Persists generated images to MinIO with structured keys |
| `ImageGenService` | Standalone single-image generation |
| `SceneService` | Storyboard scene text generation (via Coze workflow) |
| `StructuredStoryboardService` | Structured page/panel → flat scene list normalization |

## Key Patterns

### SSE Streaming
- `SseEmitter` with infinite timeout (`0L`). Progress events: `scenes`, `progress`, `image`, `error`, `done`.
- Multiple subscribers per chapter via `MangaGenerationJob.subscribe()`.
- Background work on `mangaGenerationExecutor` (virtual threads).

### Transaction Boundaries
```java
// MUST extract lazy fields before executor.submit():
Long chapterId = chapter.getId();
String novelContent = chapter.novelContentOrJoinedMessages();
int imageCount = chapter.getImageCount();
// Then pass primitives to background thread
```

### Batch Generation Flow
1. Build prompts from storyboard scenes via `MangaPromptBuilder`
2. Queue image generation requests to `WebClientImage2Client`
3. Stream results via SSE: upload to MinIO → emit `image` event
4. Update chapter `scenesText` with generated image references
5. Emit `done` when all images complete

### Concurrency
- Virtual thread executor — no pool sizing.
- `MangaGenerationConcurrencyGate` semaphore gates concurrent jobs (default 4).

### MinIO Storage Pattern
- Key: `stories/{storyId}/chapters/{chapterId}/panels/{filename}`
- Reference images: `ref_images/{storyId}/...`

## Invariants

- `@Transactional` does NOT propagate into `executor.submit()` callbacks.
- Lazy-loaded entity fields must be extracted as primitives on the request thread.
- SSE emitters must complete exactly once — use `finally { sink.complete(); }`.
- `MangaGenerationJob` must clean up subscribers on completion or error.

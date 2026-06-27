# AI Clients — Call Graph

## Called By (inbound)

```
application/MangaGenerationService
  → WebClientImage2Client    (manga image generation)

application/ImageGenService
  → WebClientImage2Client    (standalone image generation)

application/SceneService
  → CozeClient               (scene generation via Coze workflow)
```

## Calls Into (outbound)

```
WebClientImage2Client
  → config/ArtVerseProperties    (image API config)
  → common/BusinessException     (error mapping)
  → prompt/MangaPromptPolicy     (prompt validation)
  → org.springframework.web.reactive.function.client.WebClient

CozeClient
  → config/ArtVerseProperties    (Coze API config)
  → common/BusinessException
```

## External API Endpoints

| Client | External API | Config Path |
|--------|-------------|-------------|
| `WebClientImage2Client` | `artverse.image.base-url` | Default: `https://api.duojie.games/v1` |
| `CozeClient` | `artverse.coze.base-url` | Default: `https://api.coze.cn` |

## Dependency Graph

```
                    ┌──────────────┐
                    │   ai/        │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
           config      common       prompt
        (properties)  (exceptions)  (validation)
```

The `ai/` package is independent of `domain`, `application`, `persistence`, and `agent` — it only depends on `config` and `common`.

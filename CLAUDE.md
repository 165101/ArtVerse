# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

Business domain details live under `docs/knowledge/*.md` — all files there are loaded as additional project instructions via `.claude/settings.json`.

## Build & Run Commands

All Maven commands run from the `ArtVerse/` directory.

```bash
# Compile
cd ArtVerse && mvn compile

# Run all tests
mvn test

# Run a single test class / method
mvn test -Dtest=MangaGenerationServiceTest
mvn test -Dtest=MangaGenerationServiceTest#generatesImagesFromStoryboardScenes

# Package (skip tests)
mvn package -DskipTests

# Start backend (Spring Boot, port 8000)
mvn spring-boot:run
```

Frontend commands run from `frontend/`:

```bash
cd frontend
npm run dev        # Vite dev server on port 5173
npm run build      # TypeScript check + production build
npm run lint       # ESLint
```

## Required Infrastructure

| Service | Host/Port | Database |
|---------|-----------|----------|
| PostgreSQL | localhost:5432 | manga_novel |
| Redis | localhost:6379 | db 0 |
| MinIO | localhost:9000 | artverse-manga |

Default credentials are in `ArtVerse/src/main/resources/application.yml`. Flyway is enabled for schema migrations.

JVM DNS: `-Dsun.net.inetaddr.negative.ttl=0 -Dnetworkaddress.cache.ttl=10`

## Backend Package Structure

```
com.artverse
├── api/              REST controllers
├── application/      Service layer + manga workflow orchestration
│   ├── workflow/     Agent workflow engine (nodes, orchestrator, intent classifier)
│   └── tools/        Agent tools: MangaContextTools, MangaStoryboardTools, MangaHitlTools
├── domain/           JPA entities
├── persistence/      Spring Data JPA repositories
├── ai/               External AI API clients (WebClient-based)
├── agent/            AgentScope AI agent integration (gateway, factories, events)
├── guard/            Rate limiting, idempotency (SingleFlight), concurrency gating
├── config/           Spring Boot configuration (CORS, Redis, Sa-Token, task executors, properties)
├── common/           BusinessException, GlobalExceptionHandler, AOP aspects
├── storage/          MinIO object storage service
├── media/            Media file management
└── prompt/           Manga prompt building, templates, and policy validation
```

## Critical Patterns

**Transaction boundaries:** `@Transactional` does NOT propagate into `executor.submit()` callbacks. Extract all lazy-loaded fields as primitives on the request thread before handing off to background executors. See `MangaGenerationService.generateMangaStream()` for the pattern.

**WebClient lifecycle:** `@PreDestroy` must call `connectionProvider.dispose()`. Force HTTP/1.1 and JVM DNS resolver on Windows. SSL needs `-Dio.netty.handler.ssl.noOpenSsl=true`.

**Auth:** Sa-Token with Redis-backed session storage. Token in cookies (`satoken`), 12h hard timeout, 30min renewal. All `/api/**` routes require auth except `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`.

## Agent Workflow (see `docs/knowledge/agent-workflow.md` for details)

- `MangaWorkflowOrchestrator` routes via `MangaIntentClassifierService` (LLM primary + keyword fallback)
- Routes: DIRECTOR (LLM with tools), REVIEW / CHAT / HITL (static replies, not yet wired to LLM)
- `AgentScopeAgentFactory` caches `HarnessAgent` by `ConcurrentHashMap` (no eviction — monitor in prod)
- Intent classification config: `artverse.agent.intent-classification.enabled` / `.timeout-seconds`

## Domain Model

- `Story` 1→N `Chapter`
- `Chapter` 1→N `MangaImage` (generated panels)
- `StoryAssetGroup` 1→N `CharacterProfile` (via `@ManyToMany`, needs `LEFT JOIN FETCH`)
- `MangaAgentConversation` belongs to `User` + `Chapter`
- MinIO key pattern: `stories/{storyId}/chapters/{chapterId}/panels/{filename}`

## Additional Context

Business skill documentation is in `docs/knowledge/modules/` — see `INDEX.md` for routing. Each module has:
- `SKILL.md` (purpose, code map, patterns, invariants)
- `REFERENCE.md` (who calls this module, who it calls)

Key modules: `api-layer`, `application-services`, `workflow-engine`, `agent-integration`, `manga-generation`, `domain-model`, `data-access`, `ai-clients`, `guard`, `infrastructure`

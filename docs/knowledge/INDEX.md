# ArtVerse Business Knowledge Index

Use this index to route AI-assisted development to the smallest useful business knowledge file.

## Module Skills

Each module has a `SKILL.md` (purpose, code map, patterns, invariants) and a `REFERENCE.md` (call graph, dependencies).

| Module | Skill | Description |
|--------|-------|-------------|
| `modules/api-layer` | [SKILL](modules/api-layer/SKILL.md) | REST controllers, DTOs, SSE streaming — all HTTP entry points |
| `modules/application-services` | [SKILL](modules/application-services/SKILL.md) | Core services — auth, chapter, story, character, chat, novel |
| `modules/workflow-engine` | [SKILL](modules/workflow-engine/SKILL.md) | Agent workflow engine — orchestrator, nodes, intent classifier, tools |
| `modules/agent-integration` | [SKILL](modules/agent-integration/SKILL.md) | AgentScope SDK bridge — harness, factory, workspace, tool registration |
| `modules/manga-generation` | [SKILL](modules/manga-generation/SKILL.md) | Manga image generation pipeline — SSE, batch jobs, prompts |
| `modules/domain-model` | [SKILL](modules/domain-model/SKILL.md) | JPA entities + enums — pure data objects |
| `modules/data-access` | [SKILL](modules/data-access/SKILL.md) | Spring Data JPA repositories |
| `modules/ai-clients` | [SKILL](modules/ai-clients/SKILL.md) | External AI API clients — WebClient, Coze |
| `modules/guard` | [SKILL](modules/guard/SKILL.md) | Rate limiting, idempotency, concurrency gating, metrics |
| `modules/infrastructure` | [SKILL](modules/infrastructure/SKILL.md) | Config, exceptions, AOP, MinIO storage, media, prompt building |
| `modules/frontend` | [SKILL](modules/frontend/SKILL.md) | React/TypeScript SPA — AG-UI client, manga agent page |

## Legacy Module Skills

These remain for compatibility — plan to migrate into the module structure above:

- `modules/manga-agent/SKILL.md`: Original manga agent skill (now also covered by `workflow-engine` + `agent-integration`)
- `modules/auth/SKILL.md`: Auth module skill (now also covered by `application-services` + `api-layer`)

## Knowledge Hygiene

- Prefer module skills over asking the user to repeat background.
- Keep each `SKILL.md` short enough to load cheaply; move call-graph details into `REFERENCE.md`.
- When code changes a documented flow, update the skill or note why no knowledge update is needed.
- Treat stale knowledge as a defect. If a skill disagrees with code, trust code first and repair the skill.
- `REFERENCE.md` is the authoritative call graph — update it when adding cross-module dependencies.

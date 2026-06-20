---
name: manga-agent
description: Use when changing or reviewing ArtVerse Manga Agent behavior, including chat messages, AgentScope execution, run-stream/resume-stream SSE, human-in-the-loop questions, MangaAgentRun persistence, tool audit events, storyboard tool calls, and the frontend MangaAgentPage/API contract.
---

# Manga Agent Skill

Use this skill for changes under the Manga Agent workflow. Read `flow.md` when you need event ordering, run status transitions, API contract details, or tool behavior beyond the summary below.

## Domain Model

The Manga Agent is a chapter-scoped assistant. It helps the user inspect the selected chapter, create or rewrite storyboard scenes, save those scenes, and ask for a user decision when continuing would be unsafe.

The selected workspace chapter is authoritative. Do not silently switch chapters based on free-text user intent. The agent should ask the user to switch the workspace if another chapter is intended.

Image generation is not performed by the Manga Agent. The agent prepares or refines storyboard scenes, then tells the user to use the existing Generate Manga action.

## Code Map

- REST/SSE entrypoint: `ArtVerse/src/main/java/com/artverse/api/MangaAgentController.java`.
- Request/response DTOs: `ArtVerse/src/main/java/com/artverse/api/dto/MangaAgentDtos.java`.
- Main orchestration: `ArtVerse/src/main/java/com/artverse/application/MangaAgentService.java`.
- Conversation history and prompt construction: `ArtVerse/src/main/java/com/artverse/application/MangaAgentConversationService.java`.
- Run state and event snapshots: `ArtVerse/src/main/java/com/artverse/application/MangaAgentRunService.java`.
- SSE publishing and event persistence: `ArtVerse/src/main/java/com/artverse/application/MangaAgentRunEventPublisher.java`.
- AgentScope bridge and tool registration: `ArtVerse/src/main/java/com/artverse/agents/AgentScopeHarnessAgentGateway.java`.
- Story knowledge sync: `ArtVerse/src/main/java/com/artverse/agents/AgentWorkspaceSyncService.java`.
- Runtime workspace files: `ArtVerse/src/main/java/com/artverse/agents/AgentWorkspaceService.java`.
- Agent tools: `ArtVerse/src/main/java/com/artverse/application/MangaAgentToolFactory.java`.
- Frontend API and stream parser: `frontend/src/api.ts`.
- Frontend UI state machine: `frontend/src/components/MangaAgentPage.tsx`.

## Core Flow

The controller resolves the current user and delegates to `MangaAgentService`. Synchronous calls return a final reply. Stream calls return an `SseEmitter` and run on `mangaGenerationExecutor`.

For a new run, `MangaAgentService` validates the message, starts or reuses a `MangaAgentRun`, checks idempotency through `GenerationGuardService.executeMangaAgentRun`, saves the user message, builds agent messages, syncs chapter knowledge to the AgentScope workspace, builds an `AgentRunRequest`, and executes the AgentScope gateway.

For resume, the service requires an existing `WAITING_USER` run, reconstructs a continuation message from the stored user-input request and the user's answer, clears waiting state, and continues the same request id.

`AgentScopeHarnessAgentGateway` creates or reuses a per-user/story/chapter/task/model/workspace agent. For `AgentTaskType.MANGA_DIRECTOR`, it registers `MangaAgentToolFactory.Tools`.

## Tools

- `get_chapter_context`: read-only; returns story, chapter, source excerpt, storyboard scenes, and generated image status.
- `generate_storyboard`: mutating; generates scenes through `SceneService.generateScenes` and saves them through the existing scene flow.
- `save_storyboard`: mutating; saves a complete list of storyboard scenes.
- `save_structured_storyboard`: mutating; normalizes page/panel objects through `StructuredStoryboardService` before saving scenes. Prefer this for creating or rewriting storyboard pages.
- `ask_user`: read-only at the tool level but pauses the run by storing `AgentUserInputRequest` and throwing `ToolSuspendException`.

After a mutating tool succeeds, failures in the final agent response may degrade rather than fail the whole user action. Preserve this behavior unless deliberately changing recovery semantics.

## Invariants

- `requestId` is the idempotency and resume key. Preserve it across stream retries and resume calls.
- Only `RUNNING` and `WAITING_USER` are open statuses. Terminal statuses are `SUCCEEDED`, `DEGRADED`, and `FAILED`.
- Persist non-`text_delta` run events so the frontend can restore an interrupted stream.
- Use `ask_user` for blocking decisions instead of plain-text questions.
- Keep controllers thin. Put workflow behavior in application services.
- Do not expose internal Guard endpoints from user-facing navigation.

## Change Checklist

- If API payloads or SSE event names change, update `MangaAgentDtos`, `frontend/src/api.ts`, and `MangaAgentPage.tsx` together.
- If tool return shapes change, update frontend timeline handling and tests around `AgentRunToolStatus`.
- If run status transitions change, update `MangaAgentRunService` tests and open-run restore behavior.
- If prompt or workspace knowledge changes, check both `MangaAgentConversationService.buildSystemPrompt` and `AgentWorkspaceSyncService.buildKnowledge`.
- If AgentScope session/cache key inputs change, update `AgentScopeHarnessAgentGatewayTest` and `AgentSessionIdFactoryTest`.
- If this skill disagrees with code, trust code first and update this skill or `flow.md`.

## Validation

For backend-only changes, run from `ArtVerse/`:

```bash
mvn -q -DskipTests compile
```

For frontend contract or UI changes, run from `frontend/`:

```bash
npm run build
```

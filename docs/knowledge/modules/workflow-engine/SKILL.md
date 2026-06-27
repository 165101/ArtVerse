---
name: workflow-engine
description: Manga agent workflow orchestration — intent classification, node dispatch, streaming execution, HITL resume
---

# Workflow Engine

The agent workflow engine: routes user messages to nodes, classifies intent, executes LLM calls, and manages human-in-the-loop flows.

## Code Map — Workflow Core

| Class | Role |
|-------|------|
| `MangaWorkflowOrchestrator` | Top-level coordinator: context assembly, routing, guard lifecycle, node dispatch |
| `MangaIntentClassifierService` | Two-tier intent classification: LLM primary + keyword fallback |
| `ClassificationPromptBuilder` | Builds classification prompt for LLM |
| `IntentClassificationModelProvider` | Lightweight non-streaming model for classification |
| `MangaWorkflowNodeRegistry` | Spring DI-based node registry (EnumMap) |
| `MangaWorkflowNodeHandler` | Interface: `route()`, `run()`, `stream()` |
| `MangaWorkflowExecutionContext` | Immutable context record for node execution |
| `MangaWorkflowContextSnapshot` | Chapter state snapshot for routing decisions |
| `MangaWorkflowStreamContext` | SSE sink + run reference for streaming |
| `MangaWorkflowRoute` | Enum: `AUTO`, `CHAT`, `DIRECTOR`, `HITL`, `REVIEW` |
| `MangaWorkflowNode` | Pipeline stages: `ROUTING`, `CLASSIFYING_INTENT`, `COLLECTING_CONTEXT`, `GENERATING`, `EVALUATING`, `WAITING_USER`, `COMPLETED` |
| `MangaIntentResult` | Classification result with route, confidence, confirmation flag |
| `MangaWorkflowResult` | Node execution result with degraded flag |

## Code Map — Nodes

| Class | Route | Behavior |
|-------|-------|----------|
| `MangaDirectorAgentNode` | `DIRECTOR` | Calls LLM via AgentScope harness with tools |
| `MangaReviewNode` | `REVIEW` | Static reply (not yet wired to LLM) |
| `MangaChatNode` | `CHAT` | Static reply + progress-aware responses |
| `MangaHitlNode` | `HITL` | Static reply for decision mode |
| `AbstractStaticReplyNode` | base | Shared save-user-message + save-reply pattern |

## Code Map — Agent Tools

| Class | Tool | Group |
|-------|------|-------|
| `MangaContextTools` | `get_chapter_context` | `context-tools` (read-only) |
| `MangaStoryboardTools` | `generate_storyboard`, `save_storyboard`, `save_structured_storyboard` | `storyboard-tools` (mutating) |
| `MangaHitlTools` | `ask_user` | `hitl-tools` (suspends agent) |
| `MangaToolSupport` | Shared helpers for tools | - |

## Code Map — Application Services

| Class | Role |
|-------|------|
| `MangaAgentService` | Public facade: `run()`, `runStream()`, `resume()`, `cancel()` |
| `MangaAgentConversationService` | Conversation lifecycle, message persistence, prompt building |
| `MangaAgentRunService` | Run lifecycle: start, mark* (status transitions) |
| `MangaAgentRunEventPublisher` | SSE event publishing + AG-UI protocol mapping |
| `AgentRunToolStatus` | Per-run tool tracking scope |
| `AgentToolAuditService` | Tool invocation audit logging |
| `AgentUserInputRequest` | HITL question/options payload |
| `AgentUserInputRequiredException` | Thrown when agent needs user input |
| `AgUiEventFactory` | AG-UI protocol event mapping |

## Key Flows

### New Stream Run
1. `MangaAgentService` opens tool scope → submits to executor
2. `MangaWorkflowOrchestrator.runStreamLeader()` assembles context
3. `resolveExecutionRoute()` → LLM classification → fallback to keywords
4. `MangaWorkflowNodeRegistry.handlerFor(route).stream()`
5. `MangaDirectorAgentNode` syncs workspace → builds AgentRunRequest → calls AgentScope
6. Events streamed via SSE + persisted for recovery

### HITL Resume
1. `ask_user` tool stores `AgentUserInputRequest` + throws `ToolSuspendException`
2. Node catches → `AgentUserInputRequiredException`
3. Run marked `WAITING_USER`, frontend shows options
4. Resume: reconstructs continuation message → continues same `requestId`

## Invariants

- Workflow route is an explicit contract. `AUTO` may be classified; explicit routes must NOT be reclassified.
- `requestId` is the idempotency/resume key — preserve across retries and resumes.
- Mutating tools that succeed but fail the final reply → `DEGRADED`, not `FAILED`.
- Classification failures always fall back to keyword matching — never worse than baseline.
- See `docs/knowledge/modules/manga-agent/flow.md` for detailed run lifecycle and event ordering.

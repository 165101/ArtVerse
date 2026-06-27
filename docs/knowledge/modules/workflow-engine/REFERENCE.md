# Workflow Engine — Call Graph

## Called By (inbound)

```
api/MangaAgentController  (SSE + sync endpoints)
  → MangaAgentService.run() / runStream() / resume()
    → MangaWorkflowOrchestrator.runStreamLeader()
      → MangaIntentClassifierService.classify()
      → MangaWorkflowNodeRegistry.handlerFor(route)
        → MangaDirectorAgentNode.stream()
```

## Calls Into (outbound)

```
MangaWorkflowOrchestrator
  → MangaAgentConversationService  (message persistence, prompt building)
  → MangaAgentRunService           (run lifecycle)
  → AgentModelSpecFactory          (model spec creation)
  → ApiKeyService                  (user API key)
  → GenerationGuardService         (idempotency + rate limiting)
  → MangaImageRepository           (image list)
  → CharacterProfileService        (character profiles)
  → MangaIntentClassifierService   (intent classification)
  → MangaWorkflowNodeRegistry      (node dispatch)

MangaIntentClassifierService
  → ClassificationPromptBuilder    (prompt construction)
  → IntentClassificationModelProvider (model resolution)
  → Jackson ObjectMapper           (JSON parsing)

MangaDirectorAgentNode
  → AgentScopeHarnessAgentGateway  (LLM communication)
  → AgentWorkspaceSyncService      (knowledge sync)
  → MangaAgentConversationService  (message management)
  → MangaAgentRunService           (terminal checks)

Agent Tools (MangaContextTools, etc.)
  → ChapterAccessService           (visibility checks)
  → SceneService                   (storyboard generation)
  → StructuredStoryboardService    (structured storyboard)
  → MangaImageRepository           (image data)
  → GenerationGuardService         (concurrency guard)
  → AgentToolAuditService          (audit logging)
  → AgentRunToolStatus             (per-run state)
```

## Key Dependencies

| Downstream Module | Purpose |
|-------------------|---------|
| `agent-integration` | AgentScope SDK execution |
| `application-services` | Business logic (SceneService, etc.) |
| `domain-model` | Entities (MangaAgentRun, etc.) |
| `data-access` | Repositories |
| `guard` | Idempotency, rate limiting |
| `config` | ArtVerseProperties |

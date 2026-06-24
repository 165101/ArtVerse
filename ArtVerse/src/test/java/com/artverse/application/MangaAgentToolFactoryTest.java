package com.artverse.application;

import com.artverse.agent.AgentRunContext;
import com.artverse.agent.MangaAgentRuntimeContext;
import com.artverse.common.BusinessException;
import com.artverse.domain.Chapter;
import com.artverse.domain.ColorMode;
import com.artverse.domain.Story;
import com.artverse.domain.User;
import com.artverse.guard.GenerationGuardService;
import com.artverse.persistence.ChapterRepository;
import com.artverse.persistence.MangaImageRepository;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.ToolSuspendException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MangaAgentToolFactoryTest {

    private static final Long USER_ID = 1L;
    private static final Long CHAPTER_ID = 7L;
    private static final Long STORY_ID = 3L;
    private static final String COZE_KEY = "coze-key";

    @Test
    void generateStoryboardUsesGenerationGuard() {
        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        MangaImageRepository mangaImageRepository = mock(MangaImageRepository.class);
        SceneService sceneService = mock(SceneService.class);
        StructuredStoryboardService structuredStoryboardService = mock(StructuredStoryboardService.class);
        GenerationGuardService generationGuardService = mock(GenerationGuardService.class);
        AgentToolAuditService auditService = new AgentToolAuditService(new AgentRunToolStatus());
        Chapter chapter = chapterWithOwner(CHAPTER_ID, USER_ID);

        when(chapterRepository.findByIdForIdempotency(CHAPTER_ID)).thenReturn(Optional.of(chapter));
        when(sceneService.generateScenes(CHAPTER_ID, COZE_KEY)).thenReturn(List.of("scene 1"));
        when(generationGuardService.executeSceneGeneration(eq(USER_ID), eq(CHAPTER_ID), any()))
                .thenAnswer(invocation -> invocation.<Callable<Map<String, Object>>>getArgument(2).call());

        Fixture fixture = fixture(chapterRepository, mangaImageRepository, sceneService,
                structuredStoryboardService, generationGuardService, auditService);

        Map<String, Object> result = fixture.tools().generateStoryboard(fixture.runtimeContext);

        assertThat(result).containsEntry("scenes_count", 1);
        verify(generationGuardService).executeSceneGeneration(eq(USER_ID), eq(CHAPTER_ID), any());
        verify(sceneService).generateScenes(CHAPTER_ID, COZE_KEY);
    }

    @Test
    void generateStoryboardCanUseRuntimeContextInsteadOfFactoryCapturedFields() {
        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        MangaImageRepository mangaImageRepository = mock(MangaImageRepository.class);
        SceneService sceneService = mock(SceneService.class);
        StructuredStoryboardService structuredStoryboardService = mock(StructuredStoryboardService.class);
        GenerationGuardService generationGuardService = mock(GenerationGuardService.class);
        AgentToolAuditService auditService = new AgentToolAuditService(new AgentRunToolStatus());
        Chapter chapter = chapterWithOwner(CHAPTER_ID, USER_ID);

        when(chapterRepository.findByIdForIdempotency(CHAPTER_ID)).thenReturn(Optional.of(chapter));
        when(sceneService.generateScenes(CHAPTER_ID, "coze-from-context")).thenReturn(List.of("scene 1"));
        when(generationGuardService.executeSceneGeneration(eq(USER_ID), eq(CHAPTER_ID), any()))
                .thenAnswer(invocation -> invocation.<Callable<Map<String, Object>>>getArgument(2).call());

        Fixture fixture = fixture(chapterRepository, mangaImageRepository, sceneService,
                structuredStoryboardService, generationGuardService, auditService);
        RuntimeContext runtimeContext = runtimeContext(USER_ID, STORY_ID, CHAPTER_ID, "coze-from-context");

        Map<String, Object> result = fixture.tools().generateStoryboard(runtimeContext);

        assertThat(result).containsEntry("scenes_count", 1);
        verify(sceneService).generateScenes(CHAPTER_ID, "coze-from-context");
    }

    @Test
    void saveStoryboardToVisibleChapterFindsAndPersists() {
        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        MangaImageRepository mangaImageRepository = mock(MangaImageRepository.class);
        SceneService sceneService = mock(SceneService.class);
        StructuredStoryboardService structuredStoryboardService = mock(StructuredStoryboardService.class);
        GenerationGuardService generationGuardService = mock(GenerationGuardService.class);
        AgentToolAuditService auditService = new AgentToolAuditService(new AgentRunToolStatus());
        Chapter chapter = chapterWithOwner(CHAPTER_ID, USER_ID);

        when(chapterRepository.findByIdForIdempotency(CHAPTER_ID)).thenReturn(Optional.of(chapter));
        when(sceneService.updateScenes(CHAPTER_ID, List.of("A", "B"))).thenReturn(List.of("A", "B"));

        Fixture fixture = fixture(chapterRepository, mangaImageRepository, sceneService,
                structuredStoryboardService, generationGuardService, auditService);

        Map<String, Object> result = fixture.tools().saveStoryboard(List.of("A", "B"), fixture.runtimeContext);

        assertThat(result).containsEntry("scenes_count", 2);
        assertThat(result).containsEntry("saved", true);
    }

    @Test
    void saveStoryboardBlockedForNonOwner() {
        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        MangaImageRepository mangaImageRepository = mock(MangaImageRepository.class);
        SceneService sceneService = mock(SceneService.class);
        StructuredStoryboardService structuredStoryboardService = mock(StructuredStoryboardService.class);
        GenerationGuardService generationGuardService = mock(GenerationGuardService.class);
        AgentToolAuditService auditService = new AgentToolAuditService(new AgentRunToolStatus());

        when(chapterRepository.findByIdForIdempotency(CHAPTER_ID)).thenReturn(Optional.of(chapterWithOwner(CHAPTER_ID, 2L)));

        Fixture fixture = fixture(chapterRepository, mangaImageRepository, sceneService,
                structuredStoryboardService, generationGuardService, auditService);
        RuntimeContext blockedContext = runtimeContext(999L, STORY_ID, CHAPTER_ID, COZE_KEY);

        assertThatThrownBy(() -> fixture.tools().saveStoryboard(List.of("A"), blockedContext))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Forbidden");

        verifyNoInteractions(sceneService);
    }

    @Test
    void askUserStoresInputThenSuspends() {
        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        MangaImageRepository mangaImageRepository = mock(MangaImageRepository.class);
        SceneService sceneService = mock(SceneService.class);
        StructuredStoryboardService structuredStoryboardService = mock(StructuredStoryboardService.class);
        GenerationGuardService generationGuardService = mock(GenerationGuardService.class);
        AgentRunToolStatus runStatus = new AgentRunToolStatus();
        AgentToolAuditService auditService = new AgentToolAuditService(runStatus);
        UUID requestId = UUID.randomUUID();

        try (AgentRunToolStatus.RunScope ignored = runStatus.start(USER_ID, CHAPTER_ID, requestId)) {
            Fixture fixture = fixture(chapterRepository, mangaImageRepository, sceneService,
                    structuredStoryboardService, generationGuardService, auditService, runStatus);
            RuntimeContext ctx = runtimeContext(USER_ID, STORY_ID, CHAPTER_ID, COZE_KEY, requestId);

            assertThatThrownBy(() -> fixture.tools().askUser(
                    "Select database?",
                    List.of(Map.of("label", "PostgreSQL", "recommended", true), Map.of("label", "MySQL")),
                    true,
                    "Need persistence strategy",
                    ctx
            )).isInstanceOf(ToolSuspendException.class);
        }

        AgentUserInputRequest waiting = runStatus.waitingInput(USER_ID, CHAPTER_ID, requestId);
        assertThat(waiting).isNotNull();
        assertThat(waiting.question()).isEqualTo("Select database?");
        assertThat(waiting.options()).extracting(AgentUserInputRequest.Option::label)
                .containsExactly("PostgreSQL", "MySQL");
        assertThat(waiting.allowFreeText()).isTrue();
    }

    @Test
    void askUserUsesRuntimeContextRequestIdWhenAvailable() {
        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        MangaImageRepository mangaImageRepository = mock(MangaImageRepository.class);
        SceneService sceneService = mock(SceneService.class);
        StructuredStoryboardService structuredStoryboardService = mock(StructuredStoryboardService.class);
        GenerationGuardService generationGuardService = mock(GenerationGuardService.class);
        AgentRunToolStatus runStatus = new AgentRunToolStatus();
        AgentToolAuditService auditService = new AgentToolAuditService(runStatus);
        UUID requestId = UUID.randomUUID();

        try (AgentRunToolStatus.RunScope ignored = runStatus.start(USER_ID, CHAPTER_ID, requestId)) {
            Fixture fixture = fixture(chapterRepository, mangaImageRepository, sceneService,
                    structuredStoryboardService, generationGuardService, auditService, runStatus);
            RuntimeContext ctx = runtimeContext(USER_ID, STORY_ID, CHAPTER_ID, COZE_KEY, requestId);

            assertThatThrownBy(() -> fixture.tools().askUser(
                    "Choose storyboard save strategy",
                    List.of(Map.of("label", "PostgreSQL", "recommended", true), Map.of("label", "MySQL")),
                    true,
                    "Need to confirm persistence path",
                    ctx
            )).isInstanceOf(ToolSuspendException.class);
        }

        assertThat(runStatus.waitingInput(USER_ID, CHAPTER_ID, requestId)).isNotNull();
    }

    // ---- helpers ----

    private Fixture fixture(ChapterRepository chapterRepository, MangaImageRepository mangaImageRepository,
                            SceneService sceneService, StructuredStoryboardService structuredStoryboardService,
                            GenerationGuardService generationGuardService, AgentToolAuditService auditService) {
        return fixture(chapterRepository, mangaImageRepository, sceneService, structuredStoryboardService,
                generationGuardService, auditService, new AgentRunToolStatus());
    }

    private Fixture fixture(ChapterRepository chapterRepository, MangaImageRepository mangaImageRepository,
                            SceneService sceneService, StructuredStoryboardService structuredStoryboardService,
                            GenerationGuardService generationGuardService, AgentToolAuditService auditService,
                            AgentRunToolStatus runStatus) {
        MangaAgentToolFactory factory = new MangaAgentToolFactory(
                mangaImageRepository, sceneService, structuredStoryboardService,
                new ChapterAccessService(chapterRepository), generationGuardService, auditService, runStatus);
        MangaAgentToolFactory.Tools tools = factory.create();
        RuntimeContext runtimeContext = runtimeContext(USER_ID, STORY_ID, CHAPTER_ID, COZE_KEY);
        return new Fixture(tools, runtimeContext);
    }

    private static RuntimeContext runtimeContext(Long userId, Long storyId, Long chapterId, String cozeKey) {
        return runtimeContext(userId, storyId, chapterId, cozeKey, UUID.randomUUID());
    }

    private static RuntimeContext runtimeContext(Long userId, Long storyId, Long chapterId, String cozeKey, UUID requestId) {
        return RuntimeContext.builder()
                .userId(String.valueOf(userId))
                .sessionId("u-" + userId + "-story-" + storyId + "-chapter-" + chapterId)
                .put(MangaAgentRuntimeContext.class, new MangaAgentRuntimeContext(
                        userId, storyId, chapterId,
                        UUID.randomUUID(), requestId, cozeKey))
                .put(AgentRunContext.class, new AgentRunContext(requestId))
                .build();
    }

    private static Chapter chapterWithOwner(Long chapterId, Long ownerId) {
        User user = new User();
        user.setId(ownerId);
        Story story = new Story();
        story.setId(STORY_ID);
        story.setTitle("Story");
        story.setUser(user);
        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        chapter.setStory(story);
        chapter.setChapterNumber(1);
        chapter.setImageCount(1);
        chapter.setColorMode(ColorMode.BW);
        chapter.setNovelContent("source");
        return chapter;
    }

    private record Fixture(MangaAgentToolFactory.Tools tools, RuntimeContext runtimeContext) {}
}
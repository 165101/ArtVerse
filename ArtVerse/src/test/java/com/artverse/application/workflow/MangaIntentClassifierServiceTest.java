package com.artverse.application.workflow;

import com.artverse.agent.AgentModelSpec;
import com.artverse.config.ArtVerseProperties;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MangaIntentClassifierServiceTest {

    private ClassificationPromptBuilder promptBuilder;
    private IntentClassificationModelProvider modelProvider;
    private MangaIntentClassifierService service;

    private static final AgentModelSpec MODEL_SPEC = new AgentModelSpec(
            "deepseek", "https://api.deepseek.com", "deepseek-chat", "none");

    @BeforeEach
    void setUp() {
        promptBuilder = new ClassificationPromptBuilder();
        // modelProvider is set per-test
    }

    // ------------------------------------------------------------------
    // LLM disabled — falls back to keywords
    // ------------------------------------------------------------------

    @Test
    void llmDisabled_classifiesViaKeywords() {
        modelProvider = new IntentClassificationModelProvider(null, stubProperties(false, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        MangaIntentResult result = service.classify("请帮我生成分镜并保存", context(0), "", MODEL_SPEC);

        assertThat(result.route()).isEqualTo(MangaWorkflowRoute.DIRECTOR);
        assertThat(result.requiresConfirmation()).isFalse();
    }

    // ------------------------------------------------------------------
    // LLM enabled + successful classification
    // ------------------------------------------------------------------

    @Test
    void llmEnabled_returnsLLMClassification() {
        AtomicInteger callCount = new AtomicInteger(0);
        Model stubModel = stubModel("{\"route\":\"DIRECTOR\",\"intent\":\"generate_storyboard\",\"confidence\":0.92,\"reason\":\"用户明确要求生成分镜\"}", callCount);
        modelProvider = new IntentClassificationModelProvider(stubModel, stubProperties(true, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        MangaIntentResult result = service.classify("帮我生成分镜", context(0), "", MODEL_SPEC);

        assertThat(result.route()).isEqualTo(MangaWorkflowRoute.DIRECTOR);
        assertThat(result.intent()).isEqualTo("generate_storyboard");
        assertThat(result.confidence()).isGreaterThan(0.9);
        assertThat(result.requiresConfirmation()).isFalse();
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void llmEnabled_classifiesReview() {
        AtomicInteger callCount = new AtomicInteger(0);
        Model stubModel = stubModel("{\"route\":\"REVIEW\",\"intent\":\"review_storyboard\",\"confidence\":0.88,\"reason\":\"用户要求质检分镜\"}", callCount);
        modelProvider = new IntentClassificationModelProvider(stubModel, stubProperties(true, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        MangaIntentResult result = service.classify("质检一下分镜有什么风险", context(3), "", MODEL_SPEC);
        assertThat(result.route()).isEqualTo(MangaWorkflowRoute.REVIEW);
    }

    @Test
    void llmEnabled_classifiesChat() {
        AtomicInteger callCount = new AtomicInteger(0);
        Model stubModel = stubModel("{\"route\":\"CHAT\",\"intent\":\"check_progress\",\"confidence\":0.85,\"reason\":\"用户在询问进度\"}", callCount);
        modelProvider = new IntentClassificationModelProvider(stubModel, stubProperties(true, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        MangaIntentResult result = service.classify("进度怎么样了", context(1), "", MODEL_SPEC);
        assertThat(result.route()).isEqualTo(MangaWorkflowRoute.CHAT);
    }

    // ------------------------------------------------------------------
    // LLM failure → keyword fallback
    // ------------------------------------------------------------------

    @Test
    void llmFails_fallsBackToKeywords() {
        Model failingModel = new Model() {
            @Override
            public Flux<ChatResponse> stream(List<Msg> msgs, List<io.agentscope.core.model.ToolSchema> tools, GenerateOptions opts) {
                throw new RuntimeException("Network error");
            }

            @Override
            public String getModelName() {
                return "failing-model";
            }
        };
        modelProvider = new IntentClassificationModelProvider(failingModel, stubProperties(true, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        // This message matches DIRECTOR keyword "生成分镜"
        MangaIntentResult result = service.classify("生成分镜", context(0), "", MODEL_SPEC);

        assertThat(result.route()).isEqualTo(MangaWorkflowRoute.DIRECTOR);
    }

    @Test
    void llmReturnsEmpty_fallsBackToKeywords() {
        Model emptyModel = stubModel("", new AtomicInteger(0));
        modelProvider = new IntentClassificationModelProvider(emptyModel, stubProperties(true, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        MangaIntentResult result = service.classify("检查分镜", context(3), "", MODEL_SPEC);
        assertThat(result.route()).isEqualTo(MangaWorkflowRoute.REVIEW);
    }

    @Test
    void llmReturnsBadJson_fallsBackToKeywords() {
        Model badJsonModel = stubModel("not valid json {{}", new AtomicInteger(0));
        modelProvider = new IntentClassificationModelProvider(badJsonModel, stubProperties(true, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        MangaIntentResult result = service.classify("保存分镜", context(0), "", MODEL_SPEC);
        assertThat(result.route()).isEqualTo(MangaWorkflowRoute.DIRECTOR);
    }

    @Test
    void llmReturnsUnknownRoute_defaultsToChat() {
        Model weirdModel = stubModel("{\"route\":\"UNKNOWN_ROUTE\",\"intent\":\"weird\",\"confidence\":0.9,\"reason\":\"test\"}", new AtomicInteger(0));
        modelProvider = new IntentClassificationModelProvider(weirdModel, stubProperties(true, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        MangaIntentResult result = service.classify("随便说点什么", context(0), "", MODEL_SPEC);
        assertThat(result.route()).isEqualTo(MangaWorkflowRoute.CHAT);
    }

    // ------------------------------------------------------------------
    // LLM response with markdown code fence
    // ------------------------------------------------------------------

    @Test
    void llmReturnsJsonInMarkdownFence_parsesCorrectly() {
        Model fenceModel = stubModel("```json\n{\"route\":\"DIRECTOR\",\"intent\":\"gen\",\"confidence\":0.9,\"reason\":\"clear\"}\n```", new AtomicInteger(0));
        modelProvider = new IntentClassificationModelProvider(fenceModel, stubProperties(true, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        MangaIntentResult result = service.classify("做分镜", context(0), "", MODEL_SPEC);
        assertThat(result.route()).isEqualTo(MangaWorkflowRoute.DIRECTOR);
    }

    // ------------------------------------------------------------------
    // Cache behavior
    // ------------------------------------------------------------------

    @Test
    void cacheHit_doesNotCallLLMTwice() {
        AtomicInteger callCount = new AtomicInteger(0);
        Model stubModel = stubModel("{\"route\":\"CHAT\",\"intent\":\"status\",\"confidence\":0.8,\"reason\":\"status check\"}", callCount);
        modelProvider = new IntentClassificationModelProvider(stubModel, stubProperties(true, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        // First call — should invoke LLM
        MangaIntentResult r1 = service.classify("查看本章进度", context(2), "", MODEL_SPEC);
        assertThat(r1.route()).isEqualTo(MangaWorkflowRoute.CHAT);
        assertThat(callCount.get()).isEqualTo(1);

        // Second call with same message + context — should hit cache
        MangaIntentResult r2 = service.classify("查看本章进度", context(2), "", MODEL_SPEC);
        assertThat(r2.route()).isEqualTo(MangaWorkflowRoute.CHAT);
        // LLM should NOT have been called again
        assertThat(callCount.get()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Edge cases
    // ------------------------------------------------------------------

    @Test
    void emptyMessage_requiresConfirmation() {
        modelProvider = new IntentClassificationModelProvider(null, stubProperties(false, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        MangaIntentResult result = service.classify("", context(0), "", MODEL_SPEC);
        assertThat(result.requiresConfirmation()).isTrue();
    }

    @Test
    void nullContext_doesNotThrow() {
        modelProvider = new IntentClassificationModelProvider(null, stubProperties(false, 8));
        service = new MangaIntentClassifierService(promptBuilder, modelProvider);

        MangaIntentResult result = service.classify("生成分镜", null, "", MODEL_SPEC);
        assertThat(result.route()).isEqualTo(MangaWorkflowRoute.DIRECTOR);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private MangaWorkflowContextSnapshot context(int sceneCount) {
        return new MangaWorkflowContextSnapshot(
                1L, 2L, "Test Story", "Chapter 1", "黑白漫画",
                sceneCount, 0, "章节测试内容", "", "",
                MangaWorkflowRoute.AUTO, List.of()
        );
    }

    private static Model stubModel(String jsonResponse, AtomicInteger callCount) {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(List<Msg> msgs, List<io.agentscope.core.model.ToolSchema> tools, GenerateOptions opts) {
                callCount.incrementAndGet();
                if (jsonResponse == null || jsonResponse.isEmpty()) {
                    return Flux.empty();
                }
                TextBlock textBlock = TextBlock.builder().text(jsonResponse).build();
                ChatResponse response = ChatResponse.builder()
                        .content(List.of(textBlock))
                        .finishReason("stop")
                        .build();
                return Flux.just(response);
            }

            @Override
            public String getModelName() {
                return "stub-model";
            }
        };
    }

    private static ArtVerseProperties stubProperties(boolean llmEnabled, int timeoutSeconds) {
        ArtVerseProperties properties = new ArtVerseProperties();
        ArtVerseProperties.Agent.IntentClassification ic = new ArtVerseProperties.Agent.IntentClassification();
        ic.setEnabled(llmEnabled);
        ic.setTimeoutSeconds(timeoutSeconds);
        properties.getAgent().setIntentClassification(ic);
        return properties;
    }
}

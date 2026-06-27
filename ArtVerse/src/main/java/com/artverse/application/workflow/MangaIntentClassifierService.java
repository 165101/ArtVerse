package com.artverse.application.workflow;

import com.artverse.agent.AgentModelSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Classifies user intent into a {@link MangaWorkflowRoute} for the manga agent workflow.
 *
 * <h3>Strategy</h3>
 * <ol>
 *   <li>Check the LRU cache for a recent classification of a similar message.</li>
 *   <li>If LLM classification is enabled, send a lightweight prompt to the LLM
 *       asking for a structured JSON classification. Parse and return the result.</li>
 *   <li>If the LLM call fails (network, timeout, bad JSON, disabled), fall back
 *       to keyword-based classification — which is the original logic kept intact.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * The LRU cache is wrapped in {@link Collections#synchronizedMap}. All other state
 * is immutable or method-local.
 */
@Slf4j
@Service
public class MangaIntentClassifierService {

    private static final int CACHE_MAX_ENTRIES = 100;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private static final List<String> DIRECTOR_KEYWORDS = List.of(
            "导演", "生成分镜", "创建分镜", "做分镜", "画面脚本", "保存分镜", "改分镜", "重写分镜",
            "拆分镜头", "推进制作", "生成 storyboard", "save storyboard", "generate storyboard"
    );
    private static final List<String> REVIEW_KEYWORDS = List.of(
            "质检", "检查", "审核", "评估", "风险", "问题", "哪里不对", "是否合理", "review", "check"
    );
    private static final List<String> HITL_KEYWORDS = List.of(
            "确认", "同意", "继续", "选择", "选第", "就用", "可以执行", "暂停", "等待我", "approve", "confirm"
    );
    private static final List<String> CHAT_KEYWORDS = List.of(
            "普通对话", "进度", "状态", "查看本章", "本章漫画", "完成了吗", "到哪",
            "解释", "为什么", "怎么设计", "架构", "说明", "介绍", "是什么", "如何", "聊聊",
            "progress", "status", "help"
    );

    private final ClassificationPromptBuilder promptBuilder;
    private final IntentClassificationModelProvider modelProvider;
    private final ObjectMapper objectMapper;

    private final Map<CacheKey, CacheEntry> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, CacheEntry> eldest) {
                    return size() > CACHE_MAX_ENTRIES;
                }
            });

    public MangaIntentClassifierService(ClassificationPromptBuilder promptBuilder,
                                        IntentClassificationModelProvider modelProvider) {
        this.promptBuilder = promptBuilder;
        this.modelProvider = modelProvider;
        this.objectMapper = new ObjectMapper();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Classify the user message into a workflow route.
     *
     * @param message   the latest user message (or conversation summary ending with the user message)
     * @param context   snapshot of the current chapter state
     * @param apiKey    the user's DeepSeek API key (may be blank for system default)
     * @param modelSpec the model specification (provider, baseUrl, model name)
     * @return a resolved intent result
     */
    public MangaIntentResult classify(String message, MangaWorkflowContextSnapshot context,
                                      String apiKey, AgentModelSpec modelSpec) {
        // 1. Check cache
        CacheKey cacheKey = cacheKey(message, context);
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Intent classification cache hit for key={}", cacheKey);
            return cached.result;
        }

        // 2. Try LLM classification
        if (modelProvider.isEnabled()) {
            try {
                MangaIntentResult result = classifyWithLLM(message, context, apiKey, modelSpec);
                cache.put(cacheKey, new CacheEntry(result, Instant.now()));
                return result;
            } catch (Exception e) {
                log.warn("LLM intent classification failed, falling back to keywords: {}", e.getMessage());
            }
        }

        // 3. Fall back to keyword classification
        MangaIntentResult result = classifyWithKeywords(message, context);
        cache.put(cacheKey, new CacheEntry(result, Instant.now()));
        return result;
    }

    // ------------------------------------------------------------------
    // LLM-based classification
    // ------------------------------------------------------------------

    private MangaIntentResult classifyWithLLM(String message, MangaWorkflowContextSnapshot context,
                                               String apiKey, AgentModelSpec modelSpec) {
        Model model = modelProvider.resolve(apiKey, modelSpec);
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userMessage = promptBuilder.buildUserMessage(message, context);

        List<Msg> messages = List.of(
                Msg.builder().role(MsgRole.SYSTEM).textContent(systemPrompt).build(),
                Msg.builder().role(MsgRole.USER).textContent(userMessage).build()
        );

        GenerateOptions options = GenerateOptions.builder()
                .temperature(0.0)
                .maxTokens(256)
                .build();

        String rawJson = model.stream(messages, List.of(), options)
                .mapNotNull(response -> {
                    StringBuilder sb = new StringBuilder();
                    for (ContentBlock block : response.getContent()) {
                        if (block instanceof TextBlock text) {
                            sb.append(text.getText());
                        }
                    }
                    return sb.isEmpty() ? null : sb.toString();
                })
                .collectList()
                .map(chunks -> String.join("", chunks))
                .block(Duration.ofSeconds(modelProvider.timeoutSeconds()));

        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalStateException("LLM returned empty classification response");
        }
        return parseClassificationResponse(rawJson.trim());
    }

    @SuppressWarnings("unchecked")
    private MangaIntentResult parseClassificationResponse(String rawJson) {
        // Strip markdown code fences if present
        String json = rawJson;
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            if (start >= 0 && end > start) {
                json = json.substring(start, end).trim();
            } else {
                json = json.replaceAll("```", "").trim();
            }
        }

        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse LLM classification JSON: " + e.getMessage());
        }

        String routeStr = stringField(parsed, "route");
        String intent = stringField(parsed, "intent");
        double confidence = doubleField(parsed, "confidence", 0.5);
        String reason = stringField(parsed, "reason");

        MangaWorkflowRoute route = parseRoute(routeStr);
        return new MangaIntentResult(route, intent, confidence, reason, false);
    }

    private static MangaWorkflowRoute parseRoute(String routeStr) {
        if (routeStr == null || routeStr.isBlank()) {
            return MangaWorkflowRoute.CHAT;
        }
        try {
            return MangaWorkflowRoute.valueOf(routeStr.toUpperCase(Locale.ROOT).trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown route from LLM: '{}', defaulting to CHAT", routeStr);
            return MangaWorkflowRoute.CHAT;
        }
    }

    private static String stringField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static double doubleField(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // Keyword-based classification (fallback)
    // ------------------------------------------------------------------

    MangaIntentResult classifyWithKeywords(String message, MangaWorkflowContextSnapshot context) {
        String normalized = normalize(latestUserMessage(message));
        if (normalized.isBlank()) {
            return MangaIntentResult.needsConfirmation("empty", "用户输入为空，无法判断任务意图");
        }

        Score director = score(normalized, DIRECTOR_KEYWORDS, MangaWorkflowRoute.DIRECTOR);
        Score review = score(normalized, REVIEW_KEYWORDS, MangaWorkflowRoute.REVIEW);
        Score hitl = score(normalized, HITL_KEYWORDS, MangaWorkflowRoute.HITL);
        Score chat = score(normalized, CHAT_KEYWORDS, MangaWorkflowRoute.CHAT);
        Score winner = List.of(director, review, hitl, chat).stream()
                .max(Score::compareTo)
                .orElse(chat);

        if (winner.score <= 0) {
            return MangaIntentResult.needsConfirmation("unknown", "没有命中稳定的任务意图关键词");
        }
        double confidence = Math.min(0.95, 0.48 + winner.score * 0.16 + contextBoost(winner.route, context));
        return MangaIntentResult.resolved(
                winner.route,
                winner.route.name().toLowerCase(Locale.ROOT),
                confidence,
                winner.reason
        );
    }

    private double contextBoost(MangaWorkflowRoute route, MangaWorkflowContextSnapshot context) {
        if (context == null) {
            return 0.0;
        }
        if (route == MangaWorkflowRoute.REVIEW && context.sceneCount() > 0) {
            return 0.08;
        }
        if (route == MangaWorkflowRoute.DIRECTOR && context.sceneCount() == 0) {
            return 0.08;
        }
        return 0.0;
    }

    private Score score(String normalized, List<String> keywords, MangaWorkflowRoute route) {
        int score = 0;
        String matched = "";
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                score++;
                if (matched.isBlank()) {
                    matched = keyword;
                }
            }
        }
        String reason = score == 0 ? "" : "命中关键词：" + matched;
        return new Score(route, score, reason);
    }

    private String normalize(String message) {
        return message == null ? "" : message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String latestUserMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String[] lines = message.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.regionMatches(true, 0, "user:", 0, 5)) {
                return line.substring(5).trim();
            }
        }
        return message;
    }

    // ------------------------------------------------------------------
    // Cache support
    // ------------------------------------------------------------------

    private static CacheKey cacheKey(String message, MangaWorkflowContextSnapshot context) {
        String normalized = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        int sceneCount = context == null ? 0 : context.sceneCount();
        int imageCount = context == null ? 0 : context.imageCount();
        return new CacheKey(normalized, sceneCount, imageCount);
    }

    private record Score(MangaWorkflowRoute route, int score, String reason) implements Comparable<Score> {
        @Override
        public int compareTo(Score other) {
            return Integer.compare(this.score, other.score);
        }
    }

    private record CacheKey(String message, int sceneCount, int imageCount) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey that)) return false;
            return sceneCount == that.sceneCount
                    && imageCount == that.imageCount
                    && Objects.equals(message, that.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(message, sceneCount, imageCount);
        }
    }

    private record CacheEntry(MangaIntentResult result, Instant createdAt) {
        boolean isExpired() {
            return Duration.between(createdAt, Instant.now()).compareTo(CACHE_TTL) > 0;
        }
    }
}

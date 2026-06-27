package com.artverse.application.workflow;

import com.artverse.agent.AgentModelSpec;
import com.artverse.config.ArtVerseProperties;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Provides a lightweight, non-streaming {@link OpenAIChatModel} for intent classification.
 * <p>
 * Unlike the main agent model (which uses streaming and is wired through the full
 * AgentScope harness), classification only needs a single-turn text generation with
 * structured JSON output. This provider creates a dedicated model instance with
 * streaming disabled for lower latency and simpler response handling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentClassificationModelProvider {

    private final Model defaultModel;
    private final ArtVerseProperties properties;

    /**
     * Resolves the model to use for classification.
     * <p>
     * If the user has provided their own API key, a new non-streaming model is created
     * with that key. Otherwise, the system-configured default model is reused (its
     * streaming mode is acceptable — we block on the Mono anyway).
     *
     * @param userApiKey the user's DeepSeek API key (may be blank)
     * @param modelSpec  the model specification (provider, baseUrl, model name)
     * @return a model ready for classification calls
     */
    public Model resolve(String userApiKey, AgentModelSpec modelSpec) {
        if (userApiKey != null && !userApiKey.isBlank()) {
            log.debug("Creating dedicated classification model with user API key");
            return OpenAIChatModel.builder()
                    .apiKey(userApiKey)
                    .modelName(modelSpec.model())
                    .baseUrl(modelSpec.baseUrl())
                    .stream(false)
                    .build();
        }
        log.debug("Using default model for classification (no user API key)");
        return defaultModel;
    }

    public int timeoutSeconds() {
        return Math.max(1, properties.getAgent().getIntentClassification().getTimeoutSeconds());
    }

    public boolean isEnabled() {
        return properties.getAgent().getIntentClassification().isEnabled();
    }
}

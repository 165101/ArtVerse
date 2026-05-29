package com.artverse.ai;

import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebClientDeepSeekClient implements DeepSeekClient {

    private final ArtVerseProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Flux<String> streamChat(List<AiMessage> messages, String apiKey) {
        String key = resolveApiKey(apiKey);
        WebClient client = WebClient.builder()
                .baseUrl(properties.getDeepseek().getBaseUrl())
                .build();

        ObjectNode body = buildRequestBody(messages, true);

        return client.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.equals("[DONE]"))
                .mapNotNull(line -> {
                    try {
                        JsonNode node = objectMapper.readTree(line);
                        JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                        return delta.isMissingNode() ? null : delta.asText();
                    } catch (Exception e) {
                        return null;
                    }
                });
    }

    @Override
    public Mono<String> generateText(List<AiMessage> messages, String apiKey) {
        String key = resolveApiKey(apiKey);
        WebClient client = WebClient.builder()
                .baseUrl(properties.getDeepseek().getBaseUrl())
                .build();

        ObjectNode body = buildRequestBody(messages, false);

        return client.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        JsonNode node = objectMapper.readTree(response);
                        return node.path("choices").path(0).path("message").path("content").asText();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse DeepSeek response", e);
                    }
                });
    }

    private ObjectNode buildRequestBody(List<AiMessage> messages, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getDeepseek().getModel());
        body.put("stream", stream);

        ArrayNode messagesNode = body.putArray("messages");
        for (AiMessage msg : messages) {
            ObjectNode msgNode = messagesNode.addObject();
            msgNode.put("role", msg.role());
            msgNode.put("content", msg.content());
        }
        return body;
    }

    private String resolveApiKey(String requestApiKey) {
        if (requestApiKey != null && !requestApiKey.isBlank()) {
            return requestApiKey;
        }
        String configKey = properties.getDeepseek().getApiKey();
        if (configKey == null || configKey.isBlank()) {
            throw new BusinessException(400, "DeepSeek API Key is missing. Please set it in the frontend settings or backend .env.", "DeepSeek");
        }
        return configKey;
    }
}

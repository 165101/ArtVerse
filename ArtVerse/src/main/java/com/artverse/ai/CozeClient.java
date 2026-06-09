package com.artverse.ai;

import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CozeClient {

    private final ArtVerseProperties properties;
    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, List<String>>> RES_TYPE = new TypeReference<>() {};

    public List<String> generateScenes(String context, int number, String userApiKey) {
        ArtVerseProperties.Coze config = properties.getCoze();
        String apiKey = resolveApiKey(config, userApiKey);

        Map<String, Object> body = Map.of(
                "workflow_id", config.getWorkflowId(),
                "parameters", Map.of("context", context, "number", number)
        );

        String raw;
        try {
            raw = WebClient.builder()
                    .baseUrl(config.getBaseUrl())
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                    .build()
                    .post()
                    .uri("/v1/workflow/run")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(120));
        } catch (Exception e) {
            throw new BusinessException(502, "Coze 工作流调用失败: " + e.getMessage());
        }

        if (raw == null || raw.isBlank()) {
            throw new BusinessException(502, "Coze returned empty response");
        }

        log.info("Coze raw response (first 500 chars): {}", raw.length() > 500 ? raw.substring(0, 500) + "..." : raw);

        try {
            Map<String, Object> outer = objectMapper.readValue(raw, MAP_TYPE);
            int code = ((Number) outer.getOrDefault("code", -1)).intValue();
            if (code != 0) {
                String msg = (String) outer.getOrDefault("msg", "unknown error");
                throw new BusinessException(502, "Coze 工作流返回错误: " + msg);
            }
            String dataStr = (String) outer.get("data");
            if (dataStr == null || dataStr.isBlank()) {
                throw new BusinessException(502, "Coze returned empty data field");
            }
            Map<String, List<String>> data = objectMapper.readValue(dataStr, RES_TYPE);
            List<String> scenes = data.get("prompts");
            if (scenes == null || scenes.isEmpty()) {
                log.error("Coze data field has no 'prompts' array. Data keys: {}, content: {}",
                        data.keySet(), dataStr.length() > 500 ? dataStr.substring(0, 500) + "..." : dataStr);
                throw new BusinessException(502, "Coze returned empty prompts array");
            }
            return scenes;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "Coze 响应解析失败: " + e.getMessage());
        }
    }

    private String resolveApiKey(ArtVerseProperties.Coze config, String userApiKey) {
        if (userApiKey != null && !userApiKey.isBlank()) return userApiKey;
        String key = config.getApiKey();
        if (key != null && !key.isBlank()) return key;
        try {
            Dotenv dotenv = Dotenv.load();
            key = dotenv.get("COZE_API_KEY", "");
        } catch (Exception ignored) {
        }
        if (key == null || key.isBlank()) {
            throw new BusinessException(502, "Coze API key not configured");
        }
        return key;
    }
}

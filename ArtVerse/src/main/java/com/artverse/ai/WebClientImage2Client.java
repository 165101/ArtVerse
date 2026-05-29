package com.artverse.ai;

import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebClientImage2Client implements Image2Client {

    private final ArtVerseProperties properties;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(600);

    @Override
    public Mono<GeneratedImage> generate(ImageGenerationRequest request, String apiKey) {
        String key = resolveApiKey(apiKey);
        boolean hasReferences = request.referenceImages() != null && !request.referenceImages().isEmpty();

        return (hasReferences ? generateWithReferences(request, key) : generateWithoutReferences(request, key))
                .retryWhen(reactor.util.retry.Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                        .doBeforeRetry(s -> log.warn("Retrying image generation, attempt {}", s.totalRetries() + 1)));
    }

    private Mono<GeneratedImage> generateWithoutReferences(ImageGenerationRequest request, String apiKey) {
        WebClient client = WebClient.builder()
                .baseUrl(properties.getImage().getBaseUrl())
                .build();

        String body = buildGenerationsRequest(request);

        return client.post()
                .uri("/images/generations")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> parseImageResponse(response, request.prompt()));
    }

    private Mono<GeneratedImage> generateWithReferences(ImageGenerationRequest request, String apiKey) {
        WebClient client = WebClient.builder()
                .baseUrl(properties.getImage().getBaseUrl())
                .build();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("prompt", request.prompt());
        builder.part("model", properties.getImage().getModel());
        builder.part("size", properties.getImage().getSize());
        builder.part("response_format", "b64_json");

        List<Path> refs = request.referenceImages();
        if (refs.size() == 1) {
            builder.part("image", new FileSystemResource(refs.get(0)));
        } else {
            for (Path ref : refs) {
                builder.part("image[]", new FileSystemResource(ref));
            }
        }

        return client.post()
                .uri("/images/edits")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> parseImageResponse(response, request.prompt()));
    }

    private Mono<GeneratedImage> parseImageResponse(String response, String prompt) {
        try {
            JsonNode node = objectMapper.readTree(response);
            JsonNode data = node.path("data").path(0);

            byte[] imageBytes;
            if (data.has("b64_json")) {
                imageBytes = Base64.getDecoder().decode(data.get("b64_json").asText());
            } else if (data.has("url")) {
                WebClient client = WebClient.create();
                imageBytes = client.get().uri(data.get("url").asText())
                        .retrieve()
                        .bodyToMono(byte[].class)
                        .block();
            } else {
                return Mono.error(new BusinessException(502, "Image2 returned no image data"));
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return Mono.error(new BusinessException(502, "Invalid image format from Image2"));
            }

            Path tempDir = Files.createTempDirectory("artverse_img_");
            String filename = "panel_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
            Path tempFile = tempDir.resolve(filename);
            ImageIO.write(image, "png", tempFile.toFile());

            return Mono.just(new GeneratedImage(tempFile, "image/png", Files.size(tempFile)));
        } catch (BusinessException e) {
            return Mono.error(e);
        } catch (Exception e) {
            return Mono.error(new BusinessException(502, "Failed to process Image2 response: " + e.getMessage()));
        }
    }

    private String buildGenerationsRequest(ImageGenerationRequest request) {
        try {
            var node = objectMapper.createObjectNode();
            node.put("model", properties.getImage().getModel());
            node.put("prompt", request.prompt());
            node.put("size", properties.getImage().getSize());
            node.put("response_format", "b64_json");
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request", e);
        }
    }

    private String resolveApiKey(String requestApiKey) {
        if (requestApiKey != null && !requestApiKey.isBlank()) {
            return requestApiKey;
        }
        String configKey = properties.getImage().getApiKey();
        if (configKey == null || configKey.isBlank()) {
            throw new BusinessException(400, "Image API Key is missing. Please set it in the frontend settings or backend .env.", "Image");
        }
        return configKey;
    }
}

package com.artverse.ai;

import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientImage2ClientTest {

    @Test
    void mapsUnauthorizedToClearApiKeyMessage() {
        WebClientImage2Client client = new WebClientImage2Client(new ArtVerseProperties(), new ObjectMapper());

        WebClientResponseException ex = WebClientResponseException.create(
                401,
                "Unauthorized",
                null,
                "{\"error\":{\"message\":\"Invalid token\"}}".getBytes(),
                null
        );

        BusinessException mapped = invokeMapHttpError(client, ex);

        assertThat(mapped.getStatus()).isEqualTo(401);
        assertThat(mapped.getProvider()).isEqualTo("Image2");
        assertThat(mapped.getMessage()).contains("Image2 API Key 无效或已过期");
    }

    private BusinessException invokeMapHttpError(WebClientImage2Client client, WebClientResponseException ex) {
        try {
            var method = WebClientImage2Client.class.getDeclaredMethod("mapHttpError", WebClientResponseException.class);
            method.setAccessible(true);
            return (BusinessException) method.invoke(client, ex);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

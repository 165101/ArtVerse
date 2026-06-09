package com.artverse.application;

import com.artverse.ai.CozeClient;
import com.artverse.persistence.ChapterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SceneServiceTest {

    @Test
    void parsesWrappedScenesArray() {
        SceneService service = new SceneService(
                mock(ChapterRepository.class),
                mock(CozeClient.class),
                new ObjectMapper()
        );

        assertThat(service.parseScenesText("{\"scenes\":[\"第1页\",\"第2页\"]}"))
                .containsExactly("第1页", "第2页");
    }
}

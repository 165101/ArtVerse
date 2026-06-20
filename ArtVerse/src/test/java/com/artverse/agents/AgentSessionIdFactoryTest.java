package com.artverse.agents;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionIdFactoryTest {

    private final AgentSessionIdFactory factory = new AgentSessionIdFactory();

    @Test
    void createsStableSessionIdIndependentFromModelSpec() {
        AgentRunRequest first = request(new AgentModelSpec("deepseek", "https://api.deepseek.com", "deepseek-chat", "key-a"));
        AgentRunRequest second = request(new AgentModelSpec("deepseek", "https://api.deepseek.com", "deepseek-reasoner", "key-b"));

        assertThat(factory.create(first)).isEqualTo(factory.create(second));
        assertThat(factory.create(first)).isEqualTo("u-user-1-story-10-chapter-20-manga-director");
    }

    @Test
    void changesWhenBusinessIdentityChanges() {
        String base = factory.create("user-1", 10L, 20L, AgentTaskType.MANGA_DIRECTOR);

        assertThat(factory.create("user-2", 10L, 20L, AgentTaskType.MANGA_DIRECTOR)).isNotEqualTo(base);
        assertThat(factory.create("user-1", 11L, 20L, AgentTaskType.MANGA_DIRECTOR)).isNotEqualTo(base);
        assertThat(factory.create("user-1", 10L, 21L, AgentTaskType.MANGA_DIRECTOR)).isNotEqualTo(base);
        assertThat(factory.create("user-1", 10L, 20L, AgentTaskType.CHAT)).isNotEqualTo(base);
    }

    @Test
    void sanitizesSegments() {
        assertThat(factory.create("User 1/../../secret", 10L, 20L, AgentTaskType.CHAT))
                .isEqualTo("u-user-1-----secret-story-10-chapter-20-chat");
    }

    private AgentRunRequest request(AgentModelSpec modelSpec) {
        return new AgentRunRequest(
                "user-1",
                10L,
                20L,
                AgentTaskType.MANGA_DIRECTOR,
                List.of(new AgentMessage("user", "hi")),
                Map.of(),
                modelSpec,
                "secret"
        );
    }
}

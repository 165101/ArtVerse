package com.artverse.agents;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentScopeHarnessAgentGatewayTest {

    @Test
    void mergesSystemMessagesIntoFirstUserMessage() {
        AgentRunRequest request = new AgentRunRequest(
                "default",
                1L,
                2L,
                AgentTaskType.STORYBOARD,
                List.of(
                        new AgentMessage("system", "系统提示"),
                        new AgentMessage("user", "用户内容")
                ),
                Map.of()
        );

        List<AgentMessage> messages = AgentScopeHarnessAgentGateway.prepareInputMessages(request);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).role()).isEqualTo("user");
        assertThat(messages.get(0).content()).contains("系统提示", "用户内容");
    }

    @Test
    void cacheKeyChangesWhenModelChanges() {
        AgentRunRequest first = requestWithSpec("user-1", new AgentModelSpec(
                "deepseek",
                "https://api.deepseek.com",
                "deepseek-chat",
                "key-a"
        ));
        AgentRunRequest second = requestWithSpec("user-1", new AgentModelSpec(
                "deepseek",
                "https://api.deepseek.com",
                "deepseek-reasoner",
                "key-a"
        ));

        assertThat(cacheKey(first)).isNotEqualTo(cacheKey(second));
    }

    @Test
    void cacheKeyChangesWhenBaseUrlChanges() {
        AgentRunRequest first = requestWithSpec("user-1", new AgentModelSpec(
                "deepseek",
                "https://api.deepseek.com",
                "deepseek-chat",
                "key-a"
        ));
        AgentRunRequest second = requestWithSpec("user-1", new AgentModelSpec(
                "deepseek",
                "https://gateway.example.com",
                "deepseek-chat",
                "key-a"
        ));

        assertThat(cacheKey(first)).isNotEqualTo(cacheKey(second));
    }

    @Test
    void cacheKeyChangesWhenApiKeyHashChanges() {
        AgentRunRequest first = requestWithSpec("user-1", new AgentModelSpec(
                "deepseek",
                "https://api.deepseek.com",
                "deepseek-chat",
                "key-a"
        ));
        AgentRunRequest second = requestWithSpec("user-1", new AgentModelSpec(
                "deepseek",
                "https://api.deepseek.com",
                "deepseek-chat",
                "key-b"
        ));

        assertThat(cacheKey(first)).isNotEqualTo(cacheKey(second));
    }

    @Test
    void cacheKeyChangesWhenUserChanges() {
        AgentModelSpec spec = new AgentModelSpec(
                "deepseek",
                "https://api.deepseek.com",
                "deepseek-chat",
                "key-a"
        );

        assertThat(cacheKey(requestWithSpec("user-1", spec)))
                .isNotEqualTo(cacheKey(requestWithSpec("user-2", spec)));
    }

    private static AgentRunRequest requestWithSpec(String userId, AgentModelSpec spec) {
        return new AgentRunRequest(
                userId,
                1L,
                2L,
                AgentTaskType.CHAT,
                List.of(new AgentMessage("user", "hi")),
                Map.of(),
                spec,
                "secret"
        );
    }

    private static String cacheKey(AgentRunRequest request) {
        return AgentScopeHarnessAgentGateway.buildAgentCacheKey(
                request,
                new AgentModelSpec("deepseek", "https://api.deepseek.com", "deepseek-chat", "fallback")
        );
    }
}

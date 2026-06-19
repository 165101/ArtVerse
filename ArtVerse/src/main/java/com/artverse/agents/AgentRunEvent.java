package com.artverse.agents;

import java.time.OffsetDateTime;
import java.util.Map;

public record AgentRunEvent(
        String type,
        String phase,
        String label,
        String toolName,
        String status,
        String text,
        Map<String, Object> data,
        OffsetDateTime createdAt
) {
    public static AgentRunEvent of(String type, String phase, String label) {
        return new AgentRunEvent(type, phase, label, null, null, null, Map.of(), OffsetDateTime.now());
    }

    public static AgentRunEvent text(String delta) {
        return new AgentRunEvent("text_delta", "replying", "正在整理回复", null, "running",
                delta, Map.of(), OffsetDateTime.now());
    }

    public static AgentRunEvent tool(String type, String label, String toolName, String status, Map<String, Object> data) {
        return new AgentRunEvent(type, "tool", label, toolName, status, null,
                data == null ? Map.of() : Map.copyOf(data), OffsetDateTime.now());
    }
}

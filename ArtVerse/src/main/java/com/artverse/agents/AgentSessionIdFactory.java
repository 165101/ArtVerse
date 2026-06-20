package com.artverse.agents;

import org.springframework.stereotype.Component;

@Component
public class AgentSessionIdFactory {

    public String create(AgentRunRequest request) {
        return create(request.userId(), request.storyId(), request.chapterId(), request.taskType());
    }

    public String create(String userId, Long storyId, Long chapterId, AgentTaskType taskType) {
        return String.join("-",
                "u", safeSegment(userId),
                "story", safeSegment(storyId),
                "chapter", safeSegment(chapterId),
                safeSegment(taskType == null ? "unknown" : taskType.sessionSuffix())
        );
    }

    static String safeSegment(Object value) {
        if (value == null) {
            return "none";
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        if (normalized.isBlank()) {
            return "none";
        }
        String safe = normalized.replaceAll("[^a-z0-9._-]", "-")
                .replace("..", "-");
        return safe.isBlank() ? "none" : safe;
    }
}

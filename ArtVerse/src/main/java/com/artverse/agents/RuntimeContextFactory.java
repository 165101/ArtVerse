package com.artverse.agents;

import org.springframework.stereotype.Component;

@Component
public class RuntimeContextFactory {

    public String createSessionId(String userId, Long storyId, Long chapterId, AgentTaskType taskType) {
        return "story-" + storyId + "-chapter-" + chapterId + "-" + taskType.sessionSuffix();
    }
}

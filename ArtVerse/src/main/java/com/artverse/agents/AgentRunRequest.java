package com.artverse.agents;

import java.util.List;
import java.util.Map;

public record AgentRunRequest(
    String userId,
    Long storyId,
    Long chapterId,
    AgentTaskType taskType,
    List<AgentMessage> messages,
    Map<String, Object> variables
) {}

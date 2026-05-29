package com.artverse.agents;

public enum AgentTaskType {
    CHAT("chat"),
    NOVEL("novel"),
    STORYBOARD("storyboard"),
    IMAGE("image");

    private final String sessionSuffix;

    AgentTaskType(String sessionSuffix) {
        this.sessionSuffix = sessionSuffix;
    }

    public String sessionSuffix() {
        return sessionSuffix;
    }
}

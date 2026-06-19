package com.artverse.application;

public class AgentUserInputRequiredException extends RuntimeException {

    private final AgentUserInputRequest request;

    public AgentUserInputRequiredException(AgentUserInputRequest request) {
        super("Agent requires user input");
        this.request = request;
    }

    public AgentUserInputRequest request() {
        return request;
    }
}

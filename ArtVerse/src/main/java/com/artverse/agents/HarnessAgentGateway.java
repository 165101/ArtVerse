package com.artverse.agents;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface HarnessAgentGateway {

    Flux<String> streamChat(AgentRunRequest request);

    Mono<String> generateText(AgentRunRequest request);
}

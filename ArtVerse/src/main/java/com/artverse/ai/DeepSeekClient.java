package com.artverse.ai;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface DeepSeekClient {

    Flux<String> streamChat(List<AiMessage> messages, String apiKey);

    Mono<String> generateText(List<AiMessage> messages, String apiKey);
}

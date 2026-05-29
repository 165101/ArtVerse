package com.artverse.agents;

import com.artverse.ai.AiMessage;
import com.artverse.ai.DeepSeekClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DeepSeekHarnessAgentGateway implements HarnessAgentGateway {

    private final DeepSeekClient deepSeekClient;

    @Override
    public Flux<String> streamChat(AgentRunRequest request) {
        List<AiMessage> messages = request.messages().stream()
                .map(m -> new AiMessage(m.role(), m.content()))
                .toList();
        return deepSeekClient.streamChat(messages, null);
    }

    @Override
    public Mono<String> generateText(AgentRunRequest request) {
        List<AiMessage> messages = request.messages().stream()
                .map(m -> new AiMessage(m.role(), m.content()))
                .toList();
        return deepSeekClient.generateText(messages, null);
    }
}

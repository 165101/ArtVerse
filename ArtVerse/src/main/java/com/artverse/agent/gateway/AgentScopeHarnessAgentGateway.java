package com.artverse.agent.gateway;

import com.artverse.agent.AgentMessage;
import com.artverse.agent.AgentRunRequest;
import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.HarnessAgent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@Slf4j
@Component
@Primary
public class AgentScopeHarnessAgentGateway {

    private static final String CB_NAME = "manga-agent-llm";
    private static final String RETRY_NAME = "manga-agent-llm";

    private final AgentScopeAgentFactory agentFactory;
    private final AgentScopeRuntimeContextFactory runtimeContextFactory;
    private final ArtVerseProperties properties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    private CircuitBreaker circuitBreaker;
    private Retry retry;

    public AgentScopeHarnessAgentGateway(
            AgentScopeAgentFactory agentFactory,
            AgentScopeRuntimeContextFactory runtimeContextFactory,
            ArtVerseProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {
        this.agentFactory = agentFactory;
        this.runtimeContextFactory = runtimeContextFactory;
        this.properties = properties;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    @PostConstruct
    public void init() {
        ArtVerseProperties.Agent agentProps = properties.getAgent();

        // Shared predicate: only record / retry on transient failures,
        // not on business-logic rejections or CB-internal exceptions.
        Predicate<Throwable> isTransient = throwable ->
                (throwable instanceof IOException)
                        || (throwable instanceof RuntimeException
                        && !(throwable instanceof BusinessException)
                        && !(throwable instanceof CallNotPermittedException));

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(agentProps.getCircuitBreakerSlidingWindowSize())
                .minimumNumberOfCalls(agentProps.getCircuitBreakerFailureThreshold())
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(agentProps.getCircuitBreakerWaitSeconds()))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallDurationThreshold(Duration.ofMillis(agentProps.getCircuitBreakerSlowCallThresholdMs()))
                .slowCallRateThreshold(50)
                .recordException(isTransient)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CB_NAME, cbConfig);

        RetryConfig retryConfig = RetryConfig.<Throwable>custom()
                .maxAttempts(agentProps.getMaxRetries() + 1) // +1 for the initial attempt
                .waitDuration(Duration.ofMillis(agentProps.getRetryMinBackoffMs()))
                .intervalBiFunction((attempt, either) -> {
                    long base = agentProps.getRetryMinBackoffMs();
                    long delay = (long) (base * Math.pow(agentProps.getRetryMultiplier(), attempt));
                    return Math.min(delay, agentProps.getRetryMaxBackoffMs());
                })
                .retryOnException(isTransient)
                .build();
        this.retry = retryRegistry.retry(RETRY_NAME, retryConfig);

        log.info("Agent gateway initialized: cb={} retry={} maxRetries={}",
                CB_NAME, RETRY_NAME, agentProps.getMaxRetries());
    }

    public Flux<String> streamChat(AgentRunRequest request) {
        return streamEvents(request)
                .ofType(TextBlockDeltaEvent.class)
                .map(TextBlockDeltaEvent::getDelta)
                .filter(delta -> delta != null && !delta.isEmpty());
    }

    public Flux<AgentEvent> streamEvents(AgentRunRequest request) {
        HarnessAgent agent = agentFactory.getOrCreate(request);
        RuntimeContext ctx = runtimeContextFactory.create(request);
        List<Msg> messages = convertMessages(prepareInputMessages(request));

        return agent.streamEvents(messages, ctx)
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> logGatewayError(e, "stream", request.requestId()));
    }

    public Mono<String> generateText(AgentRunRequest request) {
        HarnessAgent agent = agentFactory.getOrCreate(request);
        RuntimeContext ctx = runtimeContextFactory.create(request);
        List<Msg> messages = convertMessages(prepareInputMessages(request));

        return agent.call(messages, ctx)
                .map(Msg::getTextContent)
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnError(e -> logGatewayError(e, "generate", request.requestId()));
    }

    private void logGatewayError(Throwable e, String method, UUID requestId) {
        if (e instanceof CallNotPermittedException) {
            log.warn("Circuit breaker open for agent LLM, fast-failing {} request={}", method, requestId);
        } else {
            log.error("Agent {} failed: requestId={}, error={}", method, requestId, e.getMessage());
        }
    }

    static List<AgentMessage> prepareInputMessages(AgentRunRequest request) {
        List<String> systemMessages = new ArrayList<>();
        List<AgentMessage> inputMessages = new ArrayList<>();

        for (AgentMessage message : request.messages()) {
            if ("system".equalsIgnoreCase(message.role())) {
                systemMessages.add(message.content());
            } else {
                inputMessages.add(message);
            }
        }

        if (systemMessages.isEmpty()) {
            return inputMessages;
        }

        String systemPrompt = String.join("\n\n", systemMessages);
        if (inputMessages.isEmpty()) {
            return List.of(new AgentMessage("user", systemPrompt));
        }

        AgentMessage first = inputMessages.get(0);
        List<AgentMessage> prepared = new ArrayList<>(inputMessages);
        prepared.set(0, new AgentMessage(first.role(), systemPrompt + "\n\n" + first.content()));
        return prepared;
    }

    private List<Msg> convertMessages(List<AgentMessage> messages) {
        return messages.stream()
                .map(m -> Msg.builder()
                        .role(convertRole(m.role()))
                        .textContent(m.content())
                        .build())
                .toList();
    }

    private MsgRole convertRole(String role) {
        return switch (role.toLowerCase()) {
            case "user" -> MsgRole.USER;
            case "assistant" -> MsgRole.ASSISTANT;
            case "system" -> MsgRole.SYSTEM;
            default -> MsgRole.USER;
        };
    }
}

package com.artverse.application;

import com.artverse.agents.AgentRunEvent;
import com.artverse.domain.MangaAgentRun;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MangaAgentRunEventPublisher {

    private final MangaAgentRunService mangaAgentRunService;
    private final ObjectMapper objectMapper;

    public void sendStatus(MangaAgentRun run, SseEmitter emitter, String message, UUID requestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("requestId", requestId);
        publish(run, emitter, "status", payload);
    }

    public void sendToolEvent(MangaAgentRun run, SseEmitter emitter, AgentRunToolStatus.ToolEvent event) {
        publish(run, emitter, "tool", toolEventPayload(event));
    }

    public void sendRunEvent(MangaAgentRun run, SseEmitter emitter, AgentRunEvent event) {
        Map<String, Object> payload = mangaAgentRunService.toPayload(event);
        if (!"text_delta".equals(event.type())) {
            appendRunEvent(run, "run_event", payload);
        }
        sendSse(emitter, "run_event", payload);
    }

    public void sendUserInputRequested(MangaAgentRun run, SseEmitter emitter, UUID requestId,
                                       AgentUserInputRequest request) {
        publish(run, emitter, "user_input_requested", userInputPayload(requestId, request));
    }

    public void sendUserAnswerEvent(MangaAgentRun run, SseEmitter emitter, UUID requestId, String answer) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "user_answered");
        payload.put("phase", "human_input");
        payload.put("label", "已收到用户选择");
        payload.put("status", "success");
        payload.put("requestId", requestId);
        payload.put("answer", answer == null || answer.isBlank() ? "继续默认方案" : answer.trim());
        payload.put("createdAt", OffsetDateTime.now().toString());
        publish(run, emitter, "run_event", payload);
    }

    public void sendDone(MangaAgentRun run, SseEmitter emitter, String reply, UUID requestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reply", reply);
        payload.put("requestId", requestId);
        publish(run, emitter, "done", payload);
    }

    public void sendError(MangaAgentRun run, SseEmitter emitter, UUID requestId, String detail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("detail", detail);
        payload.put("requestId", requestId);
        publish(run, emitter, "error", payload);
    }

    private void publish(MangaAgentRun run, SseEmitter emitter, String eventName, Map<String, Object> payload) {
        appendRunEvent(run, eventName, payload);
        sendSse(emitter, eventName, payload);
    }

    private Map<String, Object> toolEventPayload(AgentRunToolStatus.ToolEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool", event.toolName());
        payload.put("succeeded", event.succeeded());
        payload.put("durationMs", event.durationMs());
        if (event.error() != null && !event.error().isBlank()) {
            payload.put("error", event.error());
        }
        Object saved = event.result().get("saved");
        if (saved != null) {
            payload.put("saved", saved);
        }
        Object scenesCount = event.result().get("scenes_count");
        if (scenesCount != null) {
            payload.put("scenes_count", scenesCount);
        }
        return payload;
    }

    private Map<String, Object> userInputPayload(UUID requestId, AgentUserInputRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", requestId);
        payload.put("question", request.question());
        payload.put("options", request.options());
        payload.put("allowFreeText", request.allowFreeText());
        payload.put("reason", request.reason());
        return payload;
    }

    private void appendRunEvent(MangaAgentRun run, String eventName, Map<String, Object> payload) {
        if (run == null) {
            return;
        }
        try {
            mangaAgentRunService.appendEvent(run, eventName, payload);
        } catch (Exception e) {
            log.debug("Failed to persist manga agent run event {}: {}", eventName, e.getMessage());
        }
    }

    private void sendSse(SseEmitter emitter, String eventName, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.debug("Failed to send manga agent SSE {}: {}", eventName, e.getMessage());
        }
    }
}

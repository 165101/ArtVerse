package com.artverse.api;

import com.artverse.application.ChatService;
import com.artverse.domain.ChatMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chapters/{chapterId}")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/chat")
    public SseEmitter chat(@PathVariable Long chapterId,
                           @RequestBody Map<String, String> body,
                           HttpServletRequest request) {
        String content = body.get("message");
        String deepSeekKey = request.getHeader("X-DeepSeek-API-Key");

        // Save user message first
        chatService.saveUserMessage(chapterId, content);

        // Stream response
        return chatService.streamChat(chapterId, content, deepSeekKey);
    }

    @GetMapping("/messages")
    public List<ChatMessage> getMessages(@PathVariable Long chapterId) {
        return chatService.getMessages(chapterId);
    }
}

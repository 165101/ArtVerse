package com.artverse.api;

import com.artverse.application.ChatService;
import com.artverse.domain.ChatMessage;
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
                           @RequestBody Map<String, String> body) {
        String content = body.get("message");

        // Save user message first
        chatService.saveUserMessage(chapterId, content);

        // Stream response
        return chatService.streamChat(chapterId, content);
    }

    @GetMapping("/messages")
    public List<ChatMessage> getMessages(@PathVariable Long chapterId) {
        return chatService.getMessages(chapterId);
    }
}

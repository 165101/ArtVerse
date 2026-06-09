package com.artverse.api;

import com.artverse.application.ApiKeyService;
import com.artverse.application.SceneService;
import com.artverse.domain.User;
import com.artverse.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chapters/{chapterId}")
@RequiredArgsConstructor
public class StoryboardController {

    private final SceneService sceneService;
    private final UserRepository userRepository;
    private final ApiKeyService apiKeyService;

    @PostMapping("/generate-scenes")
    public Map<String, List<String>> generateScenes(@PathVariable Long chapterId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();
        String cozeApiKey = apiKeyService.getDecryptedKey(user, "coze");
        List<String> scenes = sceneService.generateScenes(chapterId, cozeApiKey);
        return Map.of("scenes", scenes);
    }

    @GetMapping("/scenes")
    public Map<String, List<String>> getScenes(@PathVariable Long chapterId) {
        List<String> scenes = sceneService.getScenes(chapterId);
        return Map.of("scenes", scenes);
    }

    @PutMapping("/scenes")
    public Map<String, List<String>> updateScenes(@PathVariable Long chapterId, @RequestBody List<String> scenes) {
        List<String> updated = sceneService.updateScenes(chapterId, scenes);
        return Map.of("scenes", updated);
    }
}

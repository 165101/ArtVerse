package com.artverse.api;

import com.artverse.application.ApiKeyService;
import com.artverse.application.MangaGenerationService;
import com.artverse.domain.MangaImage;
import com.artverse.domain.User;
import com.artverse.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/chapters/{chapterId}")
@RequiredArgsConstructor
public class MangaGenerationController {

    private final MangaGenerationService mangaGenerationService;
    private final UserRepository userRepository;
    private final ApiKeyService apiKeyService;

    @PostMapping("/generate-manga-stream")
    public SseEmitter generateMangaStream(@PathVariable Long chapterId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();
        String imageApiKey = apiKeyService.getDecryptedKey(user, "image2");
        String deepseekApiKey = apiKeyService.getDecryptedKey(user, "deepseek");
        return mangaGenerationService.generateMangaStream(chapterId, imageApiKey, deepseekApiKey);
    }

    @PostMapping("/regenerate-image/{imageNumber}")
    public MangaImage regenerateImage(@PathVariable Long chapterId,
                                      @PathVariable int imageNumber,
                                      @RequestBody Map<String, String> body) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();
        String imageApiKey = apiKeyService.getDecryptedKey(user, "image2");
        String deepseekApiKey = apiKeyService.getDecryptedKey(user, "deepseek");
        return mangaGenerationService.regenerateImage(chapterId, imageNumber, body.get("prompt"), imageApiKey, deepseekApiKey);
    }
}

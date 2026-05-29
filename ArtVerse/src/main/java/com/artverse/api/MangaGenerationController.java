package com.artverse.api;

import com.artverse.application.MangaGenerationService;
import com.artverse.domain.MangaImage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/chapters/{chapterId}")
@RequiredArgsConstructor
public class MangaGenerationController {

    private final MangaGenerationService mangaGenerationService;

    @PostMapping("/generate-manga-stream")
    public SseEmitter generateMangaStream(@PathVariable Long chapterId, HttpServletRequest request) {
        String imageApiKey = request.getHeader("X-Image-API-Key");
        return mangaGenerationService.generateMangaStream(chapterId, imageApiKey);
    }

    @PostMapping("/regenerate-image/{imageNumber}")
    public MangaImage regenerateImage(@PathVariable Long chapterId,
                                      @PathVariable int imageNumber,
                                      @RequestBody Map<String, String> body,
                                      HttpServletRequest request) {
        String imageApiKey = request.getHeader("X-Image-API-Key");
        return mangaGenerationService.regenerateImage(chapterId, imageNumber, body.get("prompt"), imageApiKey);
    }
}

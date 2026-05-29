package com.artverse.api;

import com.artverse.application.SceneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chapters/{chapterId}")
@RequiredArgsConstructor
public class StoryboardController {

    private final SceneService sceneService;

    @PostMapping("/generate-scenes")
    public Map<String, List<String>> generateScenes(@PathVariable Long chapterId) {
        List<String> scenes = sceneService.generateScenes(chapterId);
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

package com.artverse.api;

import com.artverse.application.StoryService;
import com.artverse.domain.Story;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    @GetMapping
    public List<Story> list() {
        return storyService.listAll();
    }

    @GetMapping("/{id}")
    public Story get(@PathVariable Long id) {
        return storyService.getRequired(id);
    }

    @PostMapping
    public Story create(@RequestBody Map<String, String> body) {
        return storyService.create(body.get("title"), body.get("description"));
    }

    @PutMapping("/{id}")
    public Story update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return storyService.update(id, body.get("title"), body.get("description"), body.get("character_profiles"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        storyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/upload-cover")
    public Story uploadCover(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return storyService.updateCoverImage(id, body.get("cover_image"));
    }

    @GetMapping("/{id}/manga-style")
    public Map<String, String> getMangaStyle(@PathVariable Long id) {
        return Map.of("manga_style", storyService.getMangaStyle(id));
    }

    @PutMapping("/{id}/manga-style")
    public Map<String, String> setMangaStyle(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String style = body.get("manga_style");
        if (style == null || style.isBlank()) {
            style = "japanese";
        }
        Set<String> allowed = Set.of("japanese", "korean", "american", "european", "chinese_ink", "semi_realistic");
        if (!allowed.contains(style)) {
            throw new com.artverse.common.BusinessException(400, "Invalid manga style: " + style);
        }
        storyService.setMangaStyle(id, style);
        return Map.of("manga_style", style);
    }
}

package com.artverse.api;

import com.artverse.application.CharacterProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterProfileService characterProfileService;

    // Backend canonical paths
    @GetMapping("/chapters/{chapterId}/character-profiles")
    public Map<String, Object> getEffective(@PathVariable Long chapterId) {
        return characterProfileService.resolveEffective(chapterId);
    }

    @PutMapping("/chapters/{chapterId}/character-profiles")
    public void saveForChapter(@PathVariable Long chapterId, @RequestBody Map<String, String> body) {
        characterProfileService.saveForChapter(chapterId, body.get("content"));
    }

    @PutMapping("/stories/{storyId}/character-profiles")
    public void saveForStory(@PathVariable Long storyId, @RequestBody Map<String, String> body) {
        characterProfileService.saveForStory(storyId, body.get("content"));
    }

    @PutMapping("/asset-groups/{groupId}/character-profiles")
    public void saveForAssetGroup(@PathVariable Long groupId, @RequestBody Map<String, String> body) {
        characterProfileService.saveForAssetGroup(groupId, body.get("content"));
    }

    // Frontend compatibility paths (/characters instead of /character-profiles)
    @GetMapping("/chapters/{chapterId}/characters")
    public Map<String, Object> getCharacters(@PathVariable Long chapterId) {
        Map<String, Object> result = characterProfileService.resolveEffective(chapterId);
        // Frontend expects "characters" field, backend returns "content"
        return Map.of(
                "characters", result.getOrDefault("content", ""),
                "source", result.getOrDefault("source", "none")
        );
    }

    @PutMapping("/chapters/{chapterId}/characters")
    public void saveCharacters(@PathVariable Long chapterId, @RequestBody Map<String, String> body) {
        characterProfileService.saveForChapter(chapterId, body.get("characters"));
    }

    @DeleteMapping("/chapters/{chapterId}/characters")
    public void deleteCharacters(@PathVariable Long chapterId) {
        characterProfileService.saveForChapter(chapterId, "");
    }

    @GetMapping("/stories/{storyId}/characters")
    public Map<String, Object> getStoryCharacters(@PathVariable Long storyId) {
        return characterProfileService.resolveForStory(storyId);
    }

    @PutMapping("/stories/{storyId}/characters")
    public void saveStoryCharacters(@PathVariable Long storyId, @RequestBody Map<String, String> body) {
        characterProfileService.saveForStory(storyId, body.get("characters"));
    }
}

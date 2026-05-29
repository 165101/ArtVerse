package com.artverse.api;

import com.artverse.application.AssetGroupService;
import com.artverse.domain.StoryAssetGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssetGroupController {

    private final AssetGroupService assetGroupService;

    @GetMapping("/stories/{storyId}/asset-groups")
    public List<StoryAssetGroup> listByStory(@PathVariable Long storyId) {
        return assetGroupService.listByStory(storyId);
    }

    @PostMapping("/stories/{storyId}/asset-groups")
    public StoryAssetGroup create(@PathVariable Long storyId, @RequestBody Map<String, String> body) {
        return assetGroupService.create(storyId, body.get("name"));
    }

    @GetMapping("/asset-groups/{groupId}")
    public StoryAssetGroup get(@PathVariable Long groupId) {
        return assetGroupService.getRequired(groupId);
    }

    @PutMapping("/asset-groups/{groupId}")
    public StoryAssetGroup update(@PathVariable Long groupId, @RequestBody Map<String, String> body) {
        return assetGroupService.update(groupId, body.get("name"));
    }

    @DeleteMapping("/asset-groups/{groupId}")
    public ResponseEntity<Void> delete(@PathVariable Long groupId) {
        assetGroupService.delete(groupId);
        return ResponseEntity.noContent().build();
    }
}

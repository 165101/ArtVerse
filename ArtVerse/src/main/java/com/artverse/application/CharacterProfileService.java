package com.artverse.application;

import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import com.artverse.domain.Chapter;
import com.artverse.domain.Story;
import com.artverse.domain.StoryAssetGroup;
import com.artverse.persistence.ChapterRepository;
import com.artverse.persistence.StoryAssetGroupRepository;
import com.artverse.persistence.StoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CharacterProfileService {

    private final ChapterRepository chapterRepository;
    private final StoryRepository storyRepository;
    private final StoryAssetGroupRepository assetGroupRepository;
    private final ArtVerseProperties properties;

    @Transactional(readOnly = true)
    public Map<String, Object> resolveEffective(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(404, "Chapter not found"));

        // 1. Chapter DB field
        if (chapter.getCharacterProfiles() != null && !chapter.getCharacterProfiles().isBlank()) {
            return Map.of("content", chapter.getCharacterProfiles(), "source", "chapter");
        }

        // 2. Asset group
        if (chapter.getAssetGroup() != null) {
            StoryAssetGroup group = chapter.getAssetGroup();
            if (group.getCharacterProfiles() != null && !group.getCharacterProfiles().isBlank()) {
                return Map.of("content", group.getCharacterProfiles(), "source", "asset_group");
            }
        }

        // 4. Story default
        Story story = chapter.getStory();
        if (story.getCharacterProfiles() != null && !story.getCharacterProfiles().isBlank()) {
            return Map.of("content", story.getCharacterProfiles(), "source", "story");
        }

        // 5. Empty
        return Map.of("content", "", "source", "none");
    }

    @Transactional
    public void saveForChapter(Long chapterId, String content) {
        if (content != null && content.length() > properties.getCharacter().getMaxChars()) {
            throw new BusinessException(400, "Character profiles exceed max length");
        }
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(404, "Chapter not found"));
        chapter.setCharacterProfiles(content);
        chapterRepository.save(chapter);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolveForStory(Long storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new BusinessException(404, "Story not found"));
        String content = story.getCharacterProfiles();
        return Map.of("characters", content != null ? content : "");
    }

    @Transactional
    public void saveForStory(Long storyId, String content) {
        if (content != null && content.length() > properties.getCharacter().getMaxChars()) {
            throw new BusinessException(400, "Character profiles exceed max length");
        }
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new BusinessException(404, "Story not found"));
        story.setCharacterProfiles(content);
        storyRepository.save(story);
    }

    @Transactional
    public void saveForAssetGroup(Long groupId, String content) {
        if (content != null && content.length() > properties.getCharacter().getMaxChars()) {
            throw new BusinessException(400, "Character profiles exceed max length");
        }
        StoryAssetGroup group = assetGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(404, "Asset group not found"));
        group.setCharacterProfiles(content);
        assetGroupRepository.save(group);
    }
}

package com.artverse.application;

import com.artverse.common.BusinessException;
import com.artverse.domain.Chapter;
import com.artverse.domain.Story;
import com.artverse.domain.StoryAssetGroup;
import com.artverse.persistence.ChapterRepository;
import com.artverse.persistence.StoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoryService {

    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;

    @Transactional(readOnly = true)
    public List<Story> listAll() {
        return storyRepository.findAllWithChaptersAndGroups();
    }

    @Transactional(readOnly = true)
    public Story getRequired(Long id) {
        return storyRepository.findByIdWithChaptersAndGroups(id)
                .orElseThrow(() -> new BusinessException(404, "Story not found"));
    }

    @Transactional
    public Story create(String title, String description) {
        Story story = new Story();
        story.setTitle(title);
        story.setDescription(description);
        story = storyRepository.save(story);

        Chapter chapter = new Chapter();
        chapter.setStory(story);
        chapter.setChapterNumber(1);
        chapterRepository.save(chapter);

        return story;
    }

    @Transactional
    public Story update(Long id, String title, String description, String characterProfiles) {
        Story story = getRequired(id);
        if (title != null) story.setTitle(title);
        if (description != null) story.setDescription(description);
        if (characterProfiles != null) story.setCharacterProfiles(characterProfiles);
        return storyRepository.save(story);
    }

    @Transactional
    public void delete(Long id) {
        Story story = getRequired(id);
        storyRepository.delete(story);
    }

    @Transactional
    public Story updateCoverImage(Long id, String coverImagePath) {
        Story story = getRequired(id);
        story.setCoverImage(coverImagePath);
        return storyRepository.save(story);
    }
}

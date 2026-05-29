package com.artverse.application;

import com.artverse.common.BusinessException;
import com.artverse.domain.Story;
import com.artverse.domain.StoryAssetGroup;
import com.artverse.persistence.StoryAssetGroupRepository;
import com.artverse.persistence.StoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetGroupService {

    private final StoryAssetGroupRepository assetGroupRepository;
    private final StoryRepository storyRepository;

    @Transactional(readOnly = true)
    public List<StoryAssetGroup> listByStory(Long storyId) {
        return assetGroupRepository.findByStoryIdOrderByIdAsc(storyId);
    }

    @Transactional(readOnly = true)
    public StoryAssetGroup getRequired(Long id) {
        return assetGroupRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Asset group not found"));
    }

    @Transactional
    public StoryAssetGroup create(Long storyId, String name) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new BusinessException(404, "Story not found"));
        StoryAssetGroup group = new StoryAssetGroup();
        group.setStory(story);
        group.setName(name);
        return assetGroupRepository.save(group);
    }

    @Transactional
    public StoryAssetGroup update(Long id, String name) {
        StoryAssetGroup group = getRequired(id);
        if (name != null) group.setName(name);
        return assetGroupRepository.save(group);
    }

    @Transactional
    public void delete(Long id) {
        StoryAssetGroup group = getRequired(id);
        assetGroupRepository.delete(group);
    }
}

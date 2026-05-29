package com.artverse.persistence;

import com.artverse.domain.StoryAssetGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryAssetGroupRepository extends JpaRepository<StoryAssetGroup, Long> {

    List<StoryAssetGroup> findByStoryIdOrderByIdAsc(Long storyId);
}

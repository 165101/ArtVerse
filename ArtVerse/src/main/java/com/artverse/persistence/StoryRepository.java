package com.artverse.persistence;

import com.artverse.domain.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long> {

    @Query("SELECT DISTINCT s FROM Story s LEFT JOIN FETCH s.chapters LEFT JOIN FETCH s.assetGroups ORDER BY s.createdAt DESC")
    List<Story> findAllWithChaptersAndGroups();

    @Query("SELECT DISTINCT s FROM Story s LEFT JOIN FETCH s.chapters LEFT JOIN FETCH s.assetGroups WHERE s.id = :id")
    Optional<Story> findByIdWithChaptersAndGroups(Long id);

    @Modifying
    @Query("UPDATE Story s SET s.mangaStyle = :mangaStyle WHERE s.id = :id")
    void setMangaStyle(Long id, String mangaStyle);
}

package com.artverse.persistence;

import com.artverse.domain.MangaImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MangaImageRepository extends JpaRepository<MangaImage, Long> {

    List<MangaImage> findByChapterIdOrderByImageNumberAsc(Long chapterId);

    Optional<MangaImage> findByChapterIdAndImageNumber(Long chapterId, Integer imageNumber);

    void deleteByChapterId(Long chapterId);

    boolean existsByChapterId(Long chapterId);
}

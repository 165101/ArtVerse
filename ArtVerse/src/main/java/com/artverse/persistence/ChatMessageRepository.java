package com.artverse.persistence;

import com.artverse.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChapterIdOrderByCreatedAtAsc(Long chapterId);

    void deleteByChapterId(Long chapterId);

    boolean existsByChapterId(Long chapterId);
}

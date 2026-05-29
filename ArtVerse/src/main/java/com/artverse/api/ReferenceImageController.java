package com.artverse.api;

import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import com.artverse.domain.Chapter;
import com.artverse.domain.Story;
import com.artverse.media.MediaStorageService;
import com.artverse.persistence.ChapterRepository;
import com.artverse.persistence.StoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReferenceImageController {

    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    private final MediaStorageService mediaStorageService;
    private final ArtVerseProperties properties;

    @Transactional(readOnly = true)
    @GetMapping("/stories/{storyId}/ref-images")
    public Map<String, Object> getStoryRefImages(@PathVariable Long storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new BusinessException(404, "Story not found"));

        List<Map<String, Object>> images = new ArrayList<>();
        Path refDir = mediaStorageService.getStoryDir(storyId).resolve("ref_images");
        if (Files.exists(refDir)) {
            try {
                Files.list(refDir)
                        .filter(p -> isImageFile(p))
                        .sorted()
                        .limit(properties.getRef().getMaxImagesPerLevel())
                        .forEach(p -> {
                            String relative = mediaStorageService.toRelativePath(p);
                            try {
                                long sizeKb = Files.size(p) / 1024;
                                images.add(Map.of(
                                        "filename", p.getFileName().toString(),
                                        "image_path", relative,
                                        "size_kb", sizeKb
                                ));
                            } catch (Exception ignored) {
                            }
                        });
            } catch (Exception ignored) {
            }
        }

        String source = images.isEmpty() ? "none" : "story";
        return Map.of("images", images, "max", properties.getRef().getMaxImagesPerLevel(), "source", source);
    }

    @Transactional(readOnly = true)
    @GetMapping("/chapters/{chapterId}/ref-images")
    public Map<String, Object> getChapterRefImages(@PathVariable Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(404, "Chapter not found"));

        List<Map<String, Object>> images = new ArrayList<>();
        String source = "none";

        // Helper to add image entry
        java.util.function.BiConsumer<Path, String> addImage = (p, relative) -> {
            try {
                long sizeKb = Files.size(p) / 1024;
                images.add(Map.of(
                        "filename", p.getFileName().toString(),
                        "image_path", relative,
                        "size_kb", sizeKb
                ));
            } catch (Exception ignored) {
            }
        };

        // Chapter ref images
        Path refDir = mediaStorageService.getChapterDir(chapterId).resolve("ref_images");
        if (Files.exists(refDir)) {
            try {
                Files.list(refDir)
                        .filter(p -> isImageFile(p))
                        .sorted()
                        .limit(properties.getRef().getMaxImagesPerLevel())
                        .forEach(p -> {
                            String relative = mediaStorageService.toRelativePath(p);
                            addImage.accept(p, relative);
                        });
            } catch (Exception ignored) {
            }
        }
        if (!images.isEmpty()) {
            source = "chapter";
        }

        // Chapter old single ref
        if (images.isEmpty() && chapter.getRefImage() != null && !chapter.getRefImage().isBlank()) {
            images.add(Map.of("filename", Path.of(chapter.getRefImage()).getFileName().toString(), "image_path", chapter.getRefImage(), "size_kb", 0));
            source = "chapter";
        }

        // Asset group refs
        if (images.isEmpty() && chapter.getAssetGroup() != null) {
            Path groupRefDir = mediaStorageService.getAssetGroupDir(chapter.getAssetGroup().getId()).resolve("ref_images");
            if (Files.exists(groupRefDir)) {
                try {
                    Files.list(groupRefDir)
                            .filter(p -> isImageFile(p))
                            .sorted()
                            .limit(properties.getRef().getMaxImagesPerLevel())
                            .forEach(p -> {
                                String relative = mediaStorageService.toRelativePath(p);
                                addImage.accept(p, relative);
                            });
                } catch (Exception ignored) {
                }
            }
            if (!images.isEmpty()) source = "asset_group";
        }

        // Story refs
        if (images.isEmpty()) {
            Path storyRefDir = mediaStorageService.getStoryDir(chapter.getStory().getId()).resolve("ref_images");
            if (Files.exists(storyRefDir)) {
                try {
                    Files.list(storyRefDir)
                            .filter(p -> isImageFile(p))
                            .sorted()
                            .limit(properties.getRef().getMaxImagesPerLevel())
                            .forEach(p -> {
                                String relative = mediaStorageService.toRelativePath(p);
                                addImage.accept(p, relative);
                            });
                } catch (Exception ignored) {
                }
            }
            if (!images.isEmpty()) source = "story";
        }

        // Story old single ref
        if (images.isEmpty() && chapter.getStory().getRefImage() != null && !chapter.getStory().getRefImage().isBlank()) {
            String ref = chapter.getStory().getRefImage();
            images.add(Map.of("filename", Path.of(ref).getFileName().toString(), "image_path", ref, "size_kb", 0));
            source = "story";
        }

        return Map.of("images", images, "max", properties.getRef().getMaxImagesPerLevel(), "source", source);
    }

    private boolean isImageFile(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
    }
}

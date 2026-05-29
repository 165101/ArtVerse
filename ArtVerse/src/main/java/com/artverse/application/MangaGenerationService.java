package com.artverse.application;

import com.artverse.ai.GeneratedImage;
import com.artverse.ai.Image2Client;
import com.artverse.ai.ImageGenerationRequest;
import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import com.artverse.domain.*;
import com.artverse.media.MediaStorageService;
import com.artverse.persistence.ChapterRepository;
import com.artverse.persistence.MangaImageRepository;
import com.artverse.storage.ObjectStorageService;
import com.artverse.storage.StoredObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MangaGenerationService {

    private final ChapterRepository chapterRepository;
    private final MangaImageRepository mangaImageRepository;
    private final Image2Client image2Client;
    private final ObjectStorageService objectStorageService;
    private final MediaStorageService mediaStorageService;
    private final CharacterProfileService characterProfileService;
    private final ArtVerseProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<Long, MangaGenerationJob> activeJobs = new ConcurrentHashMap<>();
    private ExecutorService executor;

    @PostConstruct
    void init() {
        executor = Executors.newCachedThreadPool();
    }

    public SseEmitter generateMangaStream(Long chapterId, String imageApiKey) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(404, "Chapter not found"));

        // Check if already running
        MangaGenerationJob existingJob = activeJobs.get(chapterId);
        if (existingJob != null && existingJob.isRunning()) {
            SseEmitter emitter = new SseEmitter(0L);
            existingJob.addSubscriber(emitter);
            return emitter;
        }

        // Validate scenes
        List<String> scenes = parseScenes(chapter.getScenesText());
        if (scenes.isEmpty()) {
            throw new BusinessException(400, "No scenes available. Generate scenes first.");
        }
        if (scenes.size() != chapter.getImageCount()) {
            throw new BusinessException(400, "Scenes count (" + scenes.size() + ") does not match image count (" + chapter.getImageCount() + ")");
        }

        MangaGenerationJob job = new MangaGenerationJob(chapterId, scenes);
        activeJobs.put(chapterId, job);

        SseEmitter emitter = new SseEmitter(0L);
        job.addSubscriber(emitter);

        // Start generation in background
        executor.submit(() -> runGenerationJob(job, chapter, imageApiKey));

        return emitter;
    }

    private void runGenerationJob(MangaGenerationJob job, Chapter chapter, String imageApiKey) {
        try {
            // Send scenes event
            job.broadcastEvent("event: scenes\ndata: " + objectMapper.writeValueAsString(Map.of("scenes", job.getScenes())) + "\n\n");

            Map<String, Object> profileResult = characterProfileService.resolveEffective(chapter.getId());
            String profiles = (String) profileResult.get("content");

            List<Path> refImages = computeEffectiveRefImages(chapter);

            for (int i = 0; i < job.getScenes().size(); i++) {
                if (!job.isRunning()) break;

                int imageNumber = i + 1;
                String scene = job.getScenes().get(i);

                // Check if image already exists
                Optional<MangaImage> existing = mangaImageRepository.findByChapterIdAndImageNumber(chapter.getId(), imageNumber);
                if (existing.isPresent()) {
                    MangaImage img = existing.get();
                    String url = "/static/manga/" + img.getImagePath();
                    job.broadcastEvent("event: image\ndata: " + objectMapper.writeValueAsString(Map.of(
                            "image_number", imageNumber,
                            "image_path", img.getImagePath(),
                            "url", url
                    )) + "\n\n");
                    continue;
                }

                // Send progress
                job.broadcastEvent("event: progress\ndata: " + objectMapper.writeValueAsString(Map.of(
                        "image_number", imageNumber,
                        "total", job.getScenes().size(),
                        "message", "Generating page " + imageNumber + "/" + job.getScenes().size()
                )) + "\n\n");

                // Build prompt
                String prompt = buildImagePrompt(scene, profiles, chapter.getColorMode());

                // Generate image
                ImageGenerationRequest request = new ImageGenerationRequest(
                        prompt,
                        properties.getImage().getModel(),
                        properties.getImage().getSize(),
                        refImages,
                        chapter.getColorMode().name().toLowerCase()
                );

                GeneratedImage generated = image2Client.generate(request, imageApiKey).block();
                if (generated == null) {
                    throw new BusinessException(502, "Image generation returned null for page " + imageNumber);
                }

                // Save to local
                String filename = mediaStorageService.generateUniqueFilename("panel_" + String.format("%02d", imageNumber), ".png");
                Path chapterDir = mediaStorageService.getChapterDir(chapter.getId());
                Files.createDirectories(chapterDir);
                Path localPath = chapterDir.resolve(filename);
                Files.copy(generated.localFile(), localPath);

                // Upload to MinIO
                String objectKey = "stories/" + chapter.getStory().getId() + "/chapters/" + chapter.getId() + "/panels/" + filename;
                StoredObject stored = objectStorageService.putPng(objectKey, localPath, "image/png");

                // Save to DB
                MangaImage mangaImage = new MangaImage();
                mangaImage.setChapter(chapter);
                mangaImage.setImageNumber(imageNumber);
                mangaImage.setImagePath(mediaStorageService.toRelativePath(localPath));
                mangaImage.setStorageProvider(StorageProvider.MINIO);
                mangaImage.setBucket(stored.bucket());
                mangaImage.setObjectKey(stored.objectKey());
                mangaImage.setContentType(stored.contentType());
                mangaImage.setSizeBytes(stored.sizeBytes());
                mangaImage.setPrompt(prompt);
                mangaImageRepository.save(mangaImage);

                // Send image event
                String url = "/static/manga/" + mangaImage.getImagePath();
                job.broadcastEvent("event: image\ndata: " + objectMapper.writeValueAsString(Map.of(
                        "image_number", imageNumber,
                        "image_path", mangaImage.getImagePath(),
                        "url", url
                )) + "\n\n");

                // Cleanup temp file
                try {
                    Files.deleteIfExists(generated.localFile());
                    Files.deleteIfExists(generated.localFile().getParent());
                } catch (Exception ignored) {
                }
            }

            // Send done
            job.broadcastEvent("event: done\ndata: " + objectMapper.writeValueAsString(Map.of("images", job.getScenes().size())) + "\n\n");
            job.complete();

        } catch (Exception e) {
            log.error("Manga generation failed for chapter {}: {}", chapter.getId(), e.getMessage(), e);
            try {
                job.broadcastEvent("event: error\ndata: " + objectMapper.writeValueAsString(Map.of("detail", e.getMessage())) + "\n\n");
            } catch (Exception ignored) {
            }
            job.error(e.getMessage());
        } finally {
            activeJobs.remove(chapter.getId());
        }
    }

    @Transactional
    public MangaImage regenerateImage(Long chapterId, int imageNumber, String prompt, String imageApiKey) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(404, "Chapter not found"));

        if (imageNumber < 1 || imageNumber > chapter.getImageCount()) {
            throw new BusinessException(400, "Image number must be between 1 and " + chapter.getImageCount());
        }
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(400, "Prompt cannot be empty");
        }

        // Update scene if full scenes exist
        List<String> scenes = parseScenes(chapter.getScenesText());
        if (scenes.size() == chapter.getImageCount()) {
            scenes.set(imageNumber - 1, prompt);
            chapter.setScenesText(objectMapper.valueToTree(scenes).toString());
            chapterRepository.save(chapter);
        }

        Map<String, Object> profileResult = characterProfileService.resolveEffective(chapterId);
        String profiles = (String) profileResult.get("content");
        List<Path> refImages = computeEffectiveRefImages(chapter);

        String fullPrompt = buildImagePrompt(prompt, profiles, chapter.getColorMode());

        ImageGenerationRequest request = new ImageGenerationRequest(
                fullPrompt,
                properties.getImage().getModel(),
                properties.getImage().getSize(),
                refImages,
                chapter.getColorMode().name().toLowerCase()
        );

        GeneratedImage generated = image2Client.generate(request, imageApiKey).block();
        if (generated == null) {
            throw new BusinessException(502, "Image generation returned null");
        }

        // Save to local
        String filename = mediaStorageService.generateUniqueFilename("panel_" + String.format("%02d", imageNumber), ".png");
        Path chapterDir = mediaStorageService.getChapterDir(chapterId);
        Path localPath;
        try {
            Files.createDirectories(chapterDir);
            localPath = chapterDir.resolve(filename);
            Files.copy(generated.localFile(), localPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save generated image", e);
        }

        // Upload to MinIO
        String objectKey = "stories/" + chapter.getStory().getId() + "/chapters/" + chapterId + "/panels/" + filename;
        StoredObject stored = objectStorageService.putPng(objectKey, localPath, "image/png");

        // Update or create DB record
        Optional<MangaImage> existingOpt = mangaImageRepository.findByChapterIdAndImageNumber(chapterId, imageNumber);

        String oldObjectKey = null;
        String oldBucket = null;

        if (existingOpt.isPresent()) {
            MangaImage existing = existingOpt.get();
            oldObjectKey = existing.getObjectKey();
            oldBucket = existing.getBucket();
            existing.setImagePath(mediaStorageService.toRelativePath(localPath));
            existing.setStorageProvider(StorageProvider.MINIO);
            existing.setBucket(stored.bucket());
            existing.setObjectKey(stored.objectKey());
            existing.setContentType(stored.contentType());
            existing.setSizeBytes(stored.sizeBytes());
            existing.setPrompt(fullPrompt);
            MangaImage saved = mangaImageRepository.save(existing);

            // Delete old object after successful save
            cleanupOldObject(oldBucket, oldObjectKey);

            // Cleanup temp
            cleanupTempFile(generated.localFile());

            return saved;
        } else {
            MangaImage mangaImage = new MangaImage();
            mangaImage.setChapter(chapter);
            mangaImage.setImageNumber(imageNumber);
            mangaImage.setImagePath(mediaStorageService.toRelativePath(localPath));
            mangaImage.setStorageProvider(StorageProvider.MINIO);
            mangaImage.setBucket(stored.bucket());
            mangaImage.setObjectKey(stored.objectKey());
            mangaImage.setContentType(stored.contentType());
            mangaImage.setSizeBytes(stored.sizeBytes());
            mangaImage.setPrompt(fullPrompt);
            MangaImage saved = mangaImageRepository.save(mangaImage);

            cleanupTempFile(generated.localFile());

            return saved;
        }
    }

    private void cleanupOldObject(String bucket, String objectKey) {
        if (bucket != null && objectKey != null) {
            try {
                objectStorageService.deleteBestEffort(bucket, objectKey);
            } catch (Exception e) {
                log.warn("Failed to delete old MinIO object {}/{}: {}", bucket, objectKey, e.getMessage());
            }
        }
    }

    private void cleanupTempFile(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(tempFile.getParent());
        } catch (Exception ignored) {
        }
    }

    private List<Path> computeEffectiveRefImages(Chapter chapter) {
        List<Path> refs = new ArrayList<>();

        // Chapter ref images
        Path chapterRefDir = mediaStorageService.getChapterDir(chapter.getId()).resolve("ref_images");
        if (Files.exists(chapterRefDir)) {
            try {
                Files.list(chapterRefDir).filter(p -> isImageFile(p)).limit(4).forEach(refs::add);
            } catch (Exception ignored) {
            }
        }

        // Chapter old single ref
        if (refs.isEmpty() && chapter.getRefImage() != null && !chapter.getRefImage().isBlank()) {
            Path ref = mediaStorageService.resolveRelativePath(chapter.getRefImage());
            if (ref != null && Files.exists(ref)) refs.add(ref);
        }

        // Asset group refs
        if (refs.isEmpty() && chapter.getAssetGroup() != null) {
            Path groupRefDir = mediaStorageService.getAssetGroupDir(chapter.getAssetGroup().getId()).resolve("ref_images");
            if (Files.exists(groupRefDir)) {
                try {
                    Files.list(groupRefDir).filter(p -> isImageFile(p)).limit(4).forEach(refs::add);
                } catch (Exception ignored) {
                }
            }
        }

        // Story ref images
        if (refs.isEmpty()) {
            Path storyRefDir = mediaStorageService.getStoryDir(chapter.getStory().getId()).resolve("ref_images");
            if (Files.exists(storyRefDir)) {
                try {
                    Files.list(storyRefDir).filter(p -> isImageFile(p)).limit(4).forEach(refs::add);
                } catch (Exception ignored) {
                }
            }
        }

        // Story old single ref
        if (refs.isEmpty() && chapter.getStory().getRefImage() != null && !chapter.getStory().getRefImage().isBlank()) {
            Path ref = mediaStorageService.resolveRelativePath(chapter.getStory().getRefImage());
            if (ref != null && Files.exists(ref)) refs.add(ref);
        }

        return refs;
    }

    private boolean isImageFile(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
    }

    private String buildImagePrompt(String scene, String profiles, ColorMode colorMode) {
        StringBuilder sb = new StringBuilder();

        if (colorMode == ColorMode.BW) {
            sb.append("Japanese manga style black and white page, vertical multi-panel layout, clear panel borders, ");
            sb.append("Chinese speech bubbles, sound effects, high contrast lighting, fine lines and screentone. ");
        } else {
            sb.append("Japanese manga style color illustration page, cel-shading, vibrant colors, soft lighting, ");
            sb.append("Chinese speech bubbles, sound effects. ");
        }

        if (profiles != null && !profiles.isBlank()) {
            sb.append("Character profiles: ").append(profiles).append(". ");
        }

        sb.append("Scene: ").append(scene);

        return sb.toString();
    }

    private List<String> parseScenes(String scenesText) {
        if (scenesText == null || scenesText.isBlank()) return List.of();
        try {
            return objectMapper.readValue(scenesText, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}

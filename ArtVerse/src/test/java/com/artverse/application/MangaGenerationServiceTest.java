package com.artverse.application;

import com.artverse.ai.GeneratedImage;
import com.artverse.ai.Image2Client;
import com.artverse.ai.ImageGenerationRequest;
import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import com.artverse.domain.Chapter;
import com.artverse.domain.ColorMode;
import com.artverse.domain.MangaImage;
import com.artverse.domain.Story;
import com.artverse.media.MediaStorageService;
import com.artverse.persistence.ChapterRepository;
import com.artverse.persistence.MangaImageRepository;
import com.artverse.storage.ObjectStorageService;
import com.artverse.storage.StoredObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MangaGenerationServiceTest {

    @Test
    void generatesImagesFromNovelContentWhenScenesAreMissing() throws Exception {
        ArtVerseProperties properties = new ArtVerseProperties();
        properties.getImage().setModel("gpt-image-2");
        properties.getMinio().setBucket("artverse-test");
        properties.getStorage().setRoot(Files.createTempDirectory("artverse-manga-test-").toString());

        Story story = new Story();
        story.setId(3L);
        Chapter chapter = new Chapter();
        chapter.setId(7L);
        chapter.setStory(story);
        chapter.setImageCount(1);
        chapter.setColorMode(ColorMode.BW);
        chapter.setNovelContent("主角推开雨夜里的门，看到远处亮起的城市霓虹。");
        chapter.setScenesText("[\"主角推开雨夜里的门，看到远处亮起的城市霓虹。\"]");

        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        MangaImageRepository mangaImageRepository = mock(MangaImageRepository.class);
        CharacterProfileService characterProfileService = mock(CharacterProfileService.class);
        CapturingImage2Client image2Client = new CapturingImage2Client();

        when(chapterRepository.findById(7L)).thenReturn(Optional.of(chapter));
        when(mangaImageRepository.findByChapterIdAndImageNumber(7L, 1)).thenReturn(Optional.empty());
        when(mangaImageRepository.save(any(MangaImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(characterProfileService.resolveEffective(7L)).thenReturn(Map.of("content", ""));

        MangaGenerationService service = new MangaGenerationService(
                chapterRepository,
                mangaImageRepository,
                image2Client,
                new CapturingObjectStorage(),
                new MediaStorageService(properties),
                characterProfileService,
                properties,
                new ObjectMapper()
        );
        service.init();

        service.generateMangaStream(7L, "image-key", null);

        assertThat(image2Client.awaitRequest()).isTrue();
        ImageGenerationRequest request = image2Client.request.get();
        assertThat(request.model()).isEqualTo("gpt-image-2");
        assertThat(request.prompt()).contains("主角推开雨夜里的门");
    }

    @Test
    void failsFastWhenImageGenerationDoesNotComplete() throws Exception {
        ArtVerseProperties properties = new ArtVerseProperties();
        properties.getImage().setModel("gpt-image-2");
        properties.getMinio().setBucket("artverse-test");
        properties.getStorage().setRoot(Files.createTempDirectory("artverse-manga-test-").toString());

        Story story = new Story();
        story.setId(3L);
        Chapter chapter = new Chapter();
        chapter.setId(7L);
        chapter.setStory(story);
        chapter.setImageCount(1);
        chapter.setColorMode(ColorMode.BW);
        chapter.setNovelContent("主角推开雨夜里的门，看到远处亮起的城市霓虹。");
        chapter.setScenesText("[\"主角推开雨夜里的门，看到远处亮起的城市霓虹。\"]");

        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        MangaImageRepository mangaImageRepository = mock(MangaImageRepository.class);
        CharacterProfileService characterProfileService = mock(CharacterProfileService.class);

        when(chapterRepository.findById(7L)).thenReturn(Optional.of(chapter));
        when(mangaImageRepository.findByChapterIdAndImageNumber(7L, 1)).thenReturn(Optional.empty());
        when(characterProfileService.resolveEffective(7L)).thenReturn(Map.of("content", ""));

        MangaGenerationService service = new MangaGenerationService(
                chapterRepository,
                mangaImageRepository,
                (request, apiKey) -> Mono.never(),
                new CapturingObjectStorage(),
                new MediaStorageService(properties),
                characterProfileService,
                properties,
                new ObjectMapper()
        );
        service.init();

        assertThatThrownBy(() -> service.generateImageForJob(chapter, List.of(), "image-key", "test prompt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Image generation timed out");
    }

    private static class CapturingImage2Client implements Image2Client {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<ImageGenerationRequest> request = new AtomicReference<>();

        @Override
        public Mono<GeneratedImage> generate(ImageGenerationRequest request, String apiKey) {
            this.request.set(request);
            latch.countDown();
            try {
                Path dir = Files.createTempDirectory("artverse-generated-");
                Path file = dir.resolve("panel.png");
                Files.write(file, new byte[] {1, 2, 3});
                return Mono.just(new GeneratedImage(file, "image/png", Files.size(file)));
            } catch (Exception e) {
                return Mono.error(e);
            }
        }

        boolean awaitRequest() throws InterruptedException {
            return latch.await(2, TimeUnit.SECONDS);
        }
    }

    private static class CapturingObjectStorage implements ObjectStorageService {
        @Override
        public StoredObject putPng(String objectKey, Path localFile, String contentType) {
            try {
                return new StoredObject("artverse-test", objectKey, contentType, Files.size(localFile));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public InputStream get(String bucket, String objectKey) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public List<StoredObject> list(String bucket, String prefix, int limit) {
            return List.of();
        }

        @Override
        public Optional<URI> publicOrPresignedUrl(String bucket, String objectKey, Duration ttl) {
            return Optional.empty();
        }

        @Override
        public void deleteBestEffort(String bucket, String objectKey) {
        }
    }
}

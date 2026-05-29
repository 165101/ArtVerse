package com.artverse.media;

import com.artverse.common.BusinessException;
import com.artverse.config.ArtVerseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaStorageService {

    private final ArtVerseProperties properties;

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".webp");
    private static final int THUMB_WIDTH = 720;

    public Path getStorageRoot() {
        return Paths.get(properties.getStorage().getRoot()).toAbsolutePath().normalize();
    }

    public Path getChapterDir(Long chapterId) {
        return getStorageRoot().resolve("chapter_" + chapterId);
    }

    public Path getStoryDir(Long storyId) {
        return getStorageRoot().resolve("story_" + storyId);
    }

    public Path getCoversDir() {
        return getStorageRoot().resolve("covers");
    }

    public Path getAssetGroupDir(Long groupId) {
        return getStorageRoot().resolve("asset_groups").resolve("group_" + groupId);
    }

    public Path getThumbsDir() {
        return getStorageRoot().resolve(".thumbs").resolve("w" + THUMB_WIDTH);
    }

    public String generateUniqueFilename(String prefix, String extension) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + extension;
    }

    public void validateImagePath(String path) {
        if (path == null || path.isBlank()) {
            throw new BusinessException(400, "Image path cannot be empty");
        }
        Path normalized = Paths.get(path).normalize();
        if (normalized.isAbsolute()) {
            throw new BusinessException(400, "Absolute paths are not allowed");
        }
        if (normalized.toString().contains("..")) {
            throw new BusinessException(400, "Path traversal is not allowed");
        }
        String lower = path.toLowerCase();
        if (ALLOWED_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            throw new BusinessException(415, "Unsupported image format");
        }
    }

    public void validateImageBytes(byte[] data, long maxSize) {
        if (data == null || data.length == 0) {
            throw new BusinessException(400, "Image data cannot be empty");
        }
        if (data.length > maxSize) {
            throw new BusinessException(413, "Image size exceeds limit");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw new BusinessException(415, "Invalid image format");
            }
        } catch (IOException e) {
            throw new BusinessException(415, "Failed to read image");
        }
    }

    public byte[] decodeBase64Image(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new BusinessException(400, "Base64 image data cannot be empty");
        }
        String data = base64;
        if (data.contains(",")) {
            data = data.substring(data.indexOf(",") + 1);
        }
        try {
            return Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Invalid base64 image data");
        }
    }

    public Path savePng(byte[] imageData, Path targetPath) {
        try {
            Files.createDirectories(targetPath.getParent());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                throw new BusinessException(415, "Invalid image format");
            }
            ImageIO.write(image, "png", targetPath.toFile());
            return targetPath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image: " + e.getMessage(), e);
        }
    }

    public Path generateThumbnail(Path sourcePath) {
        try {
            Path thumbDir = getThumbsDir();
            Files.createDirectories(thumbDir);
            String filename = sourcePath.getFileName().toString();
            Path thumbPath = thumbDir.resolve(filename);

            Thumbnails.of(sourcePath.toFile())
                    .width(THUMB_WIDTH)
                    .outputFormat("png")
                    .toFile(thumbPath.toFile());

            return thumbPath;
        } catch (IOException e) {
            log.warn("Failed to generate thumbnail for {}: {}", sourcePath, e.getMessage());
            return null;
        }
    }

    public void deleteFileIfExists(Path path) {
        try {
            if (path != null && Files.exists(path)) {
                Path normalized = path.toAbsolutePath().normalize();
                Path root = getStorageRoot();
                if (!normalized.startsWith(root)) {
                    log.warn("Refusing to delete file outside storage root: {}", path);
                    return;
                }
                Files.deleteIfExists(normalized);
            }
        } catch (IOException e) {
            log.warn("Failed to delete file {}: {}", path, e.getMessage());
        }
    }

    public Path resolveRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return getStorageRoot().resolve(relativePath).normalize();
    }

    public String toRelativePath(Path absolutePath) {
        return getStorageRoot().relativize(absolutePath).toString().replace("\\", "/");
    }
}

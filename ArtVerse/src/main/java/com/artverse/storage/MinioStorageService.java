package com.artverse.storage;

import com.artverse.config.ArtVerseProperties;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService implements ObjectStorageService {

    private final ArtVerseProperties properties;
    private MinioClient minioClient;

    @PostConstruct
    void init() {
        ArtVerseProperties.Minio cfg = properties.getMinio();
        minioClient = MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .region(cfg.getRegion())
                .build();
    }

    @Override
    public StoredObject putPng(String objectKey, Path localFile, String contentType) {
        try {
            ArtVerseProperties.Minio cfg = properties.getMinio();
            long size = Files.size(localFile);

            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(cfg.getBucket()).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(cfg.getBucket()).build());
            }

            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(cfg.getBucket())
                            .object(objectKey)
                            .filename(localFile.toString())
                            .contentType(contentType)
                            .build()
            );

            return new StoredObject(cfg.getBucket(), objectKey, contentType, size);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload to MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream get(String bucket, String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to get from MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<URI> publicOrPresignedUrl(String bucket, String objectKey, Duration ttl) {
        ArtVerseProperties.Minio cfg = properties.getMinio();
        if (cfg.getPublicBaseUrl() != null && !cfg.getPublicBaseUrl().isBlank()) {
            String base = cfg.getPublicBaseUrl().replaceAll("/+$", "");
            return Optional.of(URI.create(base + "/" + objectKey));
        }
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) ttl.getSeconds(), TimeUnit.SECONDS)
                            .build()
            );
            return Optional.of(URI.create(url));
        } catch (Exception e) {
            log.warn("Failed to generate presigned URL for {}/{}: {}", bucket, objectKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void deleteBestEffort(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.warn("Failed to delete MinIO object {}/{}: {}", bucket, objectKey, e.getMessage());
        }
    }
}

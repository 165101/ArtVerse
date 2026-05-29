package com.artverse.storage;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public interface ObjectStorageService {

    StoredObject putPng(String objectKey, Path localFile, String contentType);

    InputStream get(String bucket, String objectKey);

    Optional<URI> publicOrPresignedUrl(String bucket, String objectKey, Duration ttl);

    void deleteBestEffort(String bucket, String objectKey);
}

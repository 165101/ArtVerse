package com.artverse.storage;

public record StoredObject(
    String bucket,
    String objectKey,
    String contentType,
    long sizeBytes
) {}

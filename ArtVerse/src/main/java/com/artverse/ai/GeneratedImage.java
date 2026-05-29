package com.artverse.ai;

import java.nio.file.Path;

public record GeneratedImage(
    Path localFile,
    String contentType,
    long sizeBytes
) {}

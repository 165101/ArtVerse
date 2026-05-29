package com.artverse.ai;

import java.nio.file.Path;
import java.util.List;

public record ImageGenerationRequest(
    String prompt,
    String model,
    String size,
    List<Path> referenceImages,
    String colorMode
) {}

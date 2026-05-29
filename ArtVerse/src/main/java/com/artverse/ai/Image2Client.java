package com.artverse.ai;

import reactor.core.publisher.Mono;

public interface Image2Client {

    Mono<GeneratedImage> generate(ImageGenerationRequest request, String apiKey);
}

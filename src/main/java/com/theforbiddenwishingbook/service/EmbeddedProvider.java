package com.theforbiddenwishingbook.service;

import com.theforbiddenwishingbook.config.ModConfig;

import java.util.concurrent.CompletableFuture;

public class EmbeddedProvider extends LLMProvider {
    @Override
    public CompletableFuture<OllamaClient.OllamaResponse> generate(String prompt, String model) {
        String modelPath = ModConfig.EMBEDDED_MODEL_PATH.get();
        if (modelPath == null || modelPath.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No embedded model path configured"));
        }

        return EmbeddedLLMService.generate(prompt)
                .thenApply(OllamaClient.OllamaResponse::ok);
    }

    @Override
    public String getProviderName() {
        return "embedded";
    }
}

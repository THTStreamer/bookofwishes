package com.theforbiddenwishingbook.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import com.theforbiddenwishingbook.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class LLMProvider {
    protected static final Gson GSON = new Gson();
    protected final HttpClient httpClient;

    protected LLMProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(ModConfig.OLLAMA_TIMEOUT_SECONDS.get()))
                .build();
    }

    public abstract CompletableFuture<OllamaClient.OllamaResponse> generate(String prompt, String model);

    public abstract String getProviderName();

    public static LLMProvider create(String providerType) {
        return switch (providerType.toLowerCase()) {
            case "ollama" -> new OllamaProvider();
            case "openai" -> new OpenAIProvider();
            case "anthropic" -> new AnthropicProvider();
            case "embedded" -> new EmbeddedProvider();
            default -> {
                TheForbiddenWishingBook.LOGGER.warn("Unknown LLM provider: {}, falling back to Ollama", providerType);
                yield new OllamaProvider();
            }
        };
    }
}

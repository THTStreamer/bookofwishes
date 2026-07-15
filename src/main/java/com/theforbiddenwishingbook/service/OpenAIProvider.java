package com.theforbiddenwishingbook.service;

import com.google.gson.JsonObject;
import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import com.theforbiddenwishingbook.config.ModConfig;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class OpenAIProvider extends LLMProvider {

    @Override
    public CompletableFuture<OllamaClient.OllamaResponse> generate(String prompt, String model) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Uses OpenAI-compatible API endpoint (also works with local proxies)
                String endpoint = ModConfig.OLLAMA_ENDPOINT.get();
                String url = endpoint + "/v1/chat/completions";

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", model);

                com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
                JsonObject systemMsg = new JsonObject();
                systemMsg.addProperty("role", "system");
                systemMsg.addProperty("content", "You are an ancient wish-granting entity in Minecraft.");
                messages.add(systemMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", prompt);
                messages.add(userMsg);

                requestBody.add("messages", messages);
                requestBody.addProperty("temperature", ModConfig.OLLAMA_TEMPERATURE.get().floatValue());
                requestBody.addProperty("max_tokens", ModConfig.OLLAMA_MAX_TOKENS.get());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + getApiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                        .timeout(Duration.ofSeconds(ModConfig.OLLAMA_TIMEOUT_SECONDS.get()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return OllamaClient.OllamaResponse.fail("HTTP " + response.statusCode());
                }

                JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
                String content = responseJson
                        .getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();

                return OllamaClient.OllamaResponse.ok(content);
            } catch (Exception e) {
                return OllamaClient.OllamaResponse.fail(e.getMessage());
            }
        });
    }

    private String getApiKey() {
        // Read from environment variable or config
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = com.theforbiddenwishingbook.config.ModConfig.OLLAMA_ENDPOINT.get(); // Fallback
        }
        return apiKey != null ? apiKey : "";
    }

    @Override
    public String getProviderName() {
        return "OpenAI";
    }
}

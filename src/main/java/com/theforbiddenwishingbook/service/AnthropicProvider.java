package com.theforbiddenwishingbook.service;

import com.google.gson.JsonObject;
import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import com.theforbiddenwishingbook.config.ModConfig;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AnthropicProvider extends LLMProvider {

    @Override
    public CompletableFuture<OllamaClient.OllamaResponse> generate(String prompt, String model) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://api.anthropic.com/v1/messages";

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", model);
                requestBody.addProperty("max_tokens", ModConfig.OLLAMA_MAX_TOKENS.get());
                requestBody.addProperty("system", "You are an ancient wish-granting entity in Minecraft.");

                com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", prompt);
                messages.add(userMsg);
                requestBody.add("messages", messages);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", getApiKey())
                        .header("anthropic-version", "2023-06-01")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                        .timeout(Duration.ofSeconds(ModConfig.OLLAMA_TIMEOUT_SECONDS.get()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return OllamaClient.OllamaResponse.fail("HTTP " + response.statusCode());
                }

                JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
                String content = responseJson
                        .getAsJsonArray("content")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();

                return OllamaClient.OllamaResponse.ok(content);
            } catch (Exception e) {
                return OllamaClient.OllamaResponse.fail(e.getMessage());
            }
        });
    }

    private String getApiKey() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        return apiKey != null ? apiKey : "";
    }

    @Override
    public String getProviderName() {
        return "Anthropic";
    }
}

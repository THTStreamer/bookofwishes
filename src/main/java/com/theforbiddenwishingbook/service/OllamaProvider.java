package com.theforbiddenwishingbook.service;

import com.google.gson.JsonObject;
import com.theforbiddenwishingbook.config.ModConfig;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class OllamaProvider extends LLMProvider {

    @Override
    public CompletableFuture<OllamaClient.OllamaResponse> generate(String prompt, String model) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String endpoint = ModConfig.OLLAMA_ENDPOINT.get();
                String url = endpoint + "/api/generate";

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", model);
                requestBody.addProperty("prompt", prompt);
                requestBody.addProperty("stream", false);

                JsonObject options = new JsonObject();
                options.addProperty("temperature", ModConfig.OLLAMA_TEMPERATURE.get().floatValue());
                options.addProperty("num_predict", ModConfig.OLLAMA_MAX_TOKENS.get());
                requestBody.add("options", options);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                        .timeout(Duration.ofSeconds(ModConfig.OLLAMA_TIMEOUT_SECONDS.get()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return OllamaClient.OllamaResponse.fail("HTTP " + response.statusCode() + ": " + response.body());
                }

                JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
                if (responseJson.has("response")) {
                    return OllamaClient.OllamaResponse.ok(responseJson.get("response").getAsString());
                }

                return OllamaClient.OllamaResponse.fail("No response field in output");
            } catch (Exception e) {
                return OllamaClient.OllamaResponse.fail(e.getMessage());
            }
        });
    }

    @Override
    public String getProviderName() {
        return "Ollama";
    }
}

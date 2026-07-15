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
import java.util.concurrent.CompletableFuture;

public class OllamaClient {
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;

    public record OllamaResponse(boolean success, String response, String error) {
        public static OllamaResponse ok(String response) {
            return new OllamaResponse(true, response, null);
        }

        public static OllamaResponse fail(String error) {
            return new OllamaResponse(false, null, error);
        }
    }

    public OllamaClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(ModConfig.OLLAMA_TIMEOUT_SECONDS.get()))
                .build();
    }

    public CompletableFuture<OllamaResponse> generate(String prompt) {
        return generate(prompt, ModConfig.OLLAMA_MODEL.get());
    }

    public CompletableFuture<OllamaResponse> generate(String prompt, String model) {
        return CompletableFuture.supplyAsync(() -> {
            int maxRetries = ModConfig.OLLAMA_RETRY_ATTEMPTS.get();
            String lastError = null;

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    return makeRequest(prompt, model);
                } catch (Exception e) {
                    lastError = e.getMessage();
                    TheForbiddenWishingBook.LOGGER.warn("Ollama request failed (attempt {}/{}): {}",
                            attempt + 1, maxRetries + 1, lastError);

                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(1000L * (attempt + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return OllamaResponse.fail("Request interrupted");
                        }
                    }
                }
            }

            return OllamaResponse.fail("All attempts failed: " + lastError);
        });
    }

    private OllamaResponse makeRequest(String prompt, String model) throws Exception {
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
            return OllamaResponse.fail("HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
        if (responseJson.has("response")) {
            return OllamaResponse.ok(responseJson.get("response").getAsString());
        }

        return OllamaResponse.fail("No response field in Ollama output");
    }
}

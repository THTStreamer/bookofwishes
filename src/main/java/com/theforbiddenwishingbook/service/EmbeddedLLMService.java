package com.theforbiddenwishingbook.service;

import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import com.theforbiddenwishingbook.config.ModConfig;
import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.MiroStat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class EmbeddedLLMService {
    private static final AtomicReference<LlamaModel> MODEL = new AtomicReference<>();
    private static final AtomicBoolean LOADING = new AtomicBoolean(false);
    private static final AtomicBoolean READY = new AtomicBoolean(false);
    private static final AtomicReference<String> LOAD_ERROR = new AtomicReference<>();

    public static boolean isReady() {
        return READY.get();
    }

    public static boolean isLoading() {
        return LOADING.get();
    }

    public static String getLoadError() {
        return LOAD_ERROR.get();
    }

    public static CompletableFuture<Void> loadModel() {
        if (READY.get()) return CompletableFuture.completedFuture(null);
        if (LOADING.get()) return CompletableFuture.failedFuture(new IllegalStateException("Model already loading"));

        LOADING.set(true);
        LOAD_ERROR.set(null);

        return CompletableFuture.runAsync(() -> {
            try {
                String modelPath = ModConfig.EMBEDDED_MODEL_PATH.get();
                if (modelPath == null || modelPath.isBlank()) {
                    throw new IllegalStateException("No model path configured. Set 'embedded_model_path' in config.");
                }

                TheForbiddenWishingBook.LOGGER.info("Loading embedded AI model from: {}", modelPath);

                ModelParameters modelParams = new ModelParameters()
                        .setModel(modelPath)
                        .setGpuLayers(ModConfig.EMBEDDED_GPU_LAYERS.get())
                        .setCtxSize(ModConfig.EMBEDDED_CONTEXT_SIZE.get())
                        .setThreads(ModConfig.EMBEDDED_THREADS.get());

                LlamaModel model = new LlamaModel(modelParams);
                MODEL.set(model);
                READY.set(true);

                TheForbiddenWishingBook.LOGGER.info("Embedded AI model loaded successfully");
            } catch (Exception e) {
                TheForbiddenWishingBook.LOGGER.error("Failed to load embedded AI model", e);
                LOAD_ERROR.set(e.getMessage());
                throw new RuntimeException(e);
            } finally {
                LOADING.set(false);
            }
        });
    }

    public static CompletableFuture<String> generate(String prompt) {
        if (!READY.get()) {
            return loadModel().thenCompose(v -> doGenerate(prompt));
        }
        return doGenerate(prompt);
    }

    public static CompletableFuture<String> generate(String prompt, Consumer<String> onToken) {
        if (!READY.get()) {
            return loadModel().thenCompose(v -> doGenerateWithStreaming(prompt, onToken));
        }
        return doGenerateWithStreaming(prompt, onToken);
    }

    private static CompletableFuture<String> doGenerate(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            LlamaModel model = MODEL.get();
            if (model == null) throw new IllegalStateException("Model not loaded");

            try {
                InferenceParameters inferParams = new InferenceParameters(prompt)
                        .setTemperature(ModConfig.OLLAMA_TEMPERATURE.get().floatValue())
                        .setPenalizeNl(true)
                        .setMiroStat(MiroStat.V2)
                        .setNPredict(ModConfig.OLLAMA_MAX_TOKENS.get());

                StringBuilder response = new StringBuilder();
                for (LlamaOutput output : model.generate(inferParams)) {
                    response.append(output);
                }
                return response.toString();
            } catch (Exception e) {
                throw new RuntimeException("Inference failed", e);
            }
        });
    }

    private static CompletableFuture<String> doGenerateWithStreaming(String prompt, Consumer<String> onToken) {
        return CompletableFuture.supplyAsync(() -> {
            LlamaModel model = MODEL.get();
            if (model == null) throw new IllegalStateException("Model not loaded");

            try {
                InferenceParameters inferParams = new InferenceParameters(prompt)
                        .setTemperature(ModConfig.OLLAMA_TEMPERATURE.get().floatValue())
                        .setPenalizeNl(true)
                        .setMiroStat(MiroStat.V2)
                        .setNPredict(ModConfig.OLLAMA_MAX_TOKENS.get());

                StringBuilder response = new StringBuilder();
                for (LlamaOutput output : model.generate(inferParams)) {
                    String token = output.toString();
                    response.append(token);
                    if (onToken != null) {
                        onToken.accept(token);
                    }
                }
                return response.toString();
            } catch (Exception e) {
                throw new RuntimeException("Inference failed", e);
            }
        });
    }

    public static void shutdown() {
        LlamaModel model = MODEL.getAndSet(null);
        if (model != null) {
            try {
                model.close();
                TheForbiddenWishingBook.LOGGER.info("Embedded AI model unloaded");
            } catch (Exception e) {
                TheForbiddenWishingBook.LOGGER.warn("Error closing AI model", e);
            }
        }
        READY.set(false);
    }
}

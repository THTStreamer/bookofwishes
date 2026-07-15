# Embedded AI Architecture Research

## Executive Summary

Replace external Ollama dependency with an embedded llama.cpp runtime via `java-llama.cpp` (kherud/java-llama.cpp). The mod becomes fully self-contained — no external server required.

## Library Evaluation

### Candidate Libraries

| Library | Binding | License | Platforms | GGUF | Streaming | Java Version | Maintenance |
|---------|---------|---------|-----------|------|-----------|--------------|-------------|
| **java-llama.cpp** | JNI | MIT | Win/Linux/macOS x86-64, aarch64 | Yes | Yes | 8+ | Active |
| llama-cpp-jna | JNA | Apache-2.0 | Win/Linux/macOS | Yes | Yes | 8+ | Active |
| Llamaj.cpp | FFM | Apache-2.0 | macOS M-series, Linux x86-64 | Yes | Yes | 25+ | Active |
| Jlama | Vector API | Apache-2.0 | Win/Linux/macOS | No (SafeTensors) | Yes | 20+ | Active |
| llama3.java | Vector API | MIT | Win/Linux/macOS | Yes (Llama only) | Yes | 21+ | Moderate |

### Recommended: java-llama.cpp

**Why java-llama.cpp wins:**

1. **Pre-built binaries** — Ships native libraries for Windows x86-64, Linux x86-64/aarch64, macOS x86-64/aarch64. No compilation needed.
2. **GGUF support** — Full GGUF format support including all quantization types (Q4_K_M, Q5_K_M, Q8_0, etc.)
3. **Streaming inference** — `model.generate()` returns `Iterable<LlamaOutput>` for token-by-token streaming
4. **MIT license** — Compatible with mod distribution
5. **Maven Central** — `de.kherud:llama:4.1.0`
6. **Java 8+** — Works with our Java 21 target
7. **Active development** — Updated fork supports Gemma 3/4, DeepSeek R1, models through April 2026
8. **Memory management** — `AutoCloseable` model for proper native memory cleanup

### Architecture

```
The Book of Wishes Mod
├── bookofwishes (main module)
│   ├── TheForbiddenWishingBook.java          # @Mod entry
│   ├── client/gui/BookOfWishesEditScreen.java # GUI
│   ├── server/WishProcessingManager.java      # Wish orchestration
│   ├── service/
│   │   ├── AIService.java                     # AI abstraction layer
│   │   ├── OllamaClient.java                  # External Ollama (legacy)
│   │   ├── EmbeddedLLMService.java            # NEW: Embedded inference
│   │   ├── PromptBuilder.java                 # Prompt construction
│   │   ├── ResponseParser.java                # JSON response extraction
│   │   └── ...
│   └── config/ModConfig.java                  # Configuration
│
└── build/libs/thebookofwishes-1.0.0.jar       # Fat JAR with native libs
```

### Data Flow

```
Player writes wish in book
    ↓
BookOfWishesEditScreen.submitWishes()
    ↓
WishProcessingManager.handleWishSubmission()
    ↓
AIService.generate(prompt)           ← Abstraction layer
    ↓
EmbeddedLLMService.generate(prompt)  ← Routes to embedded or external
    ↓
LlamaModel.generate(inferenceParams) ← java-llama.cpp
    ↓
LlamaOutput (streaming tokens)
    ↓
ResponseParser.extractJSON(response)
    ↓
WishExecutor.execute(actions)
    ↓
Result written back to book
```

### EmbeddedLLMService Interface

```java
package com.theforbiddenwishingbook.service;

public class EmbeddedLLMService {
    private LlamaModel model;
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);

    // Lazy-load model on first wish
    public CompletableFuture<String> generate(String prompt) {
        if (!ready.get()) {
            return loadModel().thenCompose(v -> doGenerate(prompt));
        }
        return doGenerate(prompt);
    }

    private CompletableFuture<String> loadModel() {
        // Load GGUF from config path
        // Report progress to player
        // Initialize model with parameters
    }

    private CompletableFuture<String> doGenerate(String prompt) {
        // Run inference on background thread
        // Stream tokens to StringBuilder
        // Return complete response
    }

    public void shutdown() {
        // Close model, free native memory
    }
}
```

### Model Loading Strategy

1. **Config path** — Player configures GGUF file path in `config/thebookofwishes-common.toml`
2. **Lazy loading** — Model loads on first wish submission (not at startup)
3. **Background thread** — Loading happens off main thread to avoid server freeze
4. **Progress feedback** — Action bar shows "Loading AI model..." during load
5. **Error handling** — Clear error messages if model fails to load
6. **Resource cleanup** — `AutoCloseable` model, shutdown hook for cleanup

### Recommended Model

For a Minecraft mod, use a small, efficient model:

| Model | Size | RAM | Quality | Speed |
|-------|------|-----|---------|-------|
| Qwen3-1.7B-Q4_K_M | ~1.1 GB | ~2 GB | Good | Fast |
| Llama-3.2-3B-Q4_K_M | ~2 GB | ~3 GB | Better | Medium |
| Gemma-3-1B-Q4_K_M | ~0.7 GB | ~1.5 GB | Acceptable | Very Fast |

**Recommendation:** Ship with Qwen3-1.7B-Q4_K_M as default — good balance of quality, size, and speed.

### Configuration Changes

```toml
[ai]
  # AI backend: "embedded" or "ollama"
  backend = "embedded"
  
  # Embedded AI settings
  embedded_model_path = ""
  embedded_context_size = 2048
  embedded_gpu_layers = 0
  embedded_threads = 4
  
  # Ollama settings (legacy, used when backend = "ollama")
  ollama_endpoint = "http://localhost:11434"
  ollama_model = "llama3.1:8b"
```

### Performance Considerations

| Metric | Ollama (current) | Embedded (target) |
|--------|------------------|-------------------|
| Startup time | N/A (external) | 2-5 seconds (model load) |
| First wish latency | ~1-2s | ~3-5s (cold) / ~1-2s (warm) |
| Subsequent wishes | ~1-2s | ~1-2s |
| Memory overhead | None (external) | ~1.5-3 GB RAM |
| Network latency | HTTP round-trip | Zero (in-process) |
| Dependencies | Ollama installed | None (self-contained) |

### Thread Safety

- Model loading: `AtomicBoolean` guards
- Inference: Runs on `CompletableFuture` background threads
- Response parsing: Main thread via `server.execute()`
- Model access: Single-threaded inference queue (llama.cpp handles internally)

### Cross-Platform Native Library Loading

java-llama.cpp handles this automatically:
- Libraries embedded in JAR under platform-specific paths
- Auto-extracted to temp directory on first load
- `System.load()` called automatically
- No manual library management needed

### Migration Plan

1. Add `de.kherud:llama:4.1.0` dependency to `build.gradle`
2. Create `EmbeddedLLMService` wrapping `LlamaModel`
3. Update `AIService` to route between embedded and Ollama
4. Add model path config to `ModConfig`
5. Add model download helper (optional — download from HuggingFace)
6. Remove Ollama dependency (keep as optional backend)
7. Test on Windows, Linux, macOS

### License Compliance

- java-llama.cpp: MIT license —允许 redistribution
- llama.cpp: MIT license —允许 redistribution
- Both are compatible with mod distribution
- Must include license notices in mod JAR

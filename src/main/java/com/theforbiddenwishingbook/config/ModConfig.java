package com.theforbiddenwishingbook.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModContainer;

public class ModConfig {
    private static ModConfigSpec COMMON_CONFIG;

    public static ModConfigSpec.ConfigValue<String> OLLAMA_ENDPOINT;
    public static ModConfigSpec.ConfigValue<String> OLLAMA_MODEL;
    public static ModConfigSpec.DoubleValue OLLAMA_TEMPERATURE;
    public static ModConfigSpec.IntValue OLLAMA_MAX_TOKENS;
    public static ModConfigSpec.IntValue OLLAMA_TIMEOUT_SECONDS;
    public static ModConfigSpec.IntValue OLLAMA_RETRY_ATTEMPTS;
    public static ModConfigSpec.ConfigValue<String> LLM_PROVIDER;

    public static ModConfigSpec.ConfigValue<String> EMBEDDED_MODEL_PATH;
    public static ModConfigSpec.IntValue EMBEDDED_CONTEXT_SIZE;
    public static ModConfigSpec.IntValue EMBEDDED_GPU_LAYERS;
    public static ModConfigSpec.IntValue EMBEDDED_THREADS;

    public static ModConfigSpec.IntValue COOLDOWN_SECONDS;
    public static ModConfigSpec.IntValue MAX_WISHES_PER_BOOK;
    public static ModConfigSpec.BooleanValue ENABLE_PAYMENT_SYSTEM;
    public static ModConfigSpec.BooleanValue LOG_WISH_HISTORY;

    public static ModConfigSpec.IntValue MAX_DESTRUCTION_RADIUS;
    public static ModConfigSpec.BooleanValue REQUIRE_ADMIN_FOR_PVP;
    public static ModConfigSpec.BooleanValue DENY_WISHES_TARGETING_ADMINS;
    public static ModConfigSpec.IntValue MAX_IMMORTALITY_DURATION;

    public static ModConfigSpec.BooleanValue ENABLE_AI_PERSONALITIES;
    public static ModConfigSpec.IntValue CONTEXT_CACHE_TTL_SECONDS;

    public static ModConfigSpec.ConfigValue<String> CUSTOM_STRUCTURES;
    public static ModConfigSpec.BooleanValue ENABLE_REGISTRY_SCANNING;
    public static ModConfigSpec.IntValue MAX_REGISTRY_ITEMS_PER_CATEGORY;

    public static void register(ModContainer modContainer) {
        buildConfig();
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, COMMON_CONFIG);
    }

    private static void buildConfig() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Ollama AI Integration Settings").push("ollama");

        OLLAMA_ENDPOINT = builder
                .comment("The Ollama API endpoint URL")
                .define("endpoint", "http://localhost:11434");

        OLLAMA_MODEL = builder
                .comment("The model to use for wish processing")
                .define("model", "llama3.1:8b");

        OLLAMA_TEMPERATURE = builder
                .comment("AI temperature (0.0 = deterministic, 1.0 = creative)")
                .defineInRange("temperature", 0.4, 0.0, 1.0);

        OLLAMA_MAX_TOKENS = builder
                .comment("Maximum tokens for AI response")
                .defineInRange("max_tokens", 700, 100, 4096);

        OLLAMA_TIMEOUT_SECONDS = builder
                .comment("Timeout for Ollama API calls in seconds")
                .defineInRange("timeout_seconds", 60, 10, 300);

        OLLAMA_RETRY_ATTEMPTS = builder
                .comment("Number of retry attempts for failed API calls")
                .defineInRange("retry_attempts", 3, 0, 10);

        LLM_PROVIDER = builder
                .comment("LLM provider to use (ollama, openai, anthropic, embedded)")
                .define("llm_provider", "ollama");

        builder.pop();

        builder.comment("Embedded AI Settings (uses llama.cpp)").push("embedded");

        EMBEDDED_MODEL_PATH = builder
                .comment(
                        "Path to the GGUF model file for embedded AI.",
                        "If empty, uses Ollama instead.",
                        "Example: './config/my-model.gguf'"
                )
                .define("model_path", "");

        EMBEDDED_CONTEXT_SIZE = builder
                .comment("Context window size for embedded AI model")
                .defineInRange("context_size", 2048, 512, 32768);

        EMBEDDED_GPU_LAYERS = builder
                .comment("Number of layers to offload to GPU (0 = CPU only)")
                .defineInRange("gpu_layers", 0, 0, 100);

        EMBEDDED_THREADS = builder
                .comment("Number of CPU threads for inference")
                .defineInRange("threads", 4, 1, 32);

        builder.pop();

        builder.comment("Gameplay Settings").push("gameplay");

        COOLDOWN_SECONDS = builder
                .comment("Cooldown between wish submissions per player")
                .defineInRange("cooldown_seconds", 30, 0, 600);

        MAX_WISHES_PER_BOOK = builder
                .comment("Maximum number of wishes per book")
                .defineInRange("max_wishes_per_book", 10, 1, 100);

        ENABLE_PAYMENT_SYSTEM = builder
                .comment("Whether the payment system is enabled")
                .define("enable_payment_system", true);

        LOG_WISH_HISTORY = builder
                .comment("Whether to log wish history for admin review")
                .define("log_wish_history", true);

        builder.pop();

        builder.comment("Safety and Permission Settings").push("safety");

        MAX_DESTRUCTION_RADIUS = builder
                .comment("Maximum radius for area-of-effect destruction wishes")
                .defineInRange("max_destruction_radius", 128, 1, 1024);

        REQUIRE_ADMIN_FOR_PVP = builder
                .comment("If true, only admins can submit wishes targeting other players")
                .define("require_admin_for_pvp", false);

        DENY_WISHES_TARGETING_ADMINS = builder
                .comment("If true, the AI cannot grant wishes that target server admins")
                .define("deny_wishes_targeting_admins", true);

        MAX_IMMORTALITY_DURATION = builder
                .comment("Maximum duration of immortality effect in seconds (0 = unlimited)")
                .defineInRange("max_immortality_duration", 600, 0, 86400);

        builder.pop();

        builder.comment("Structure Configuration").push("structures");

        CUSTOM_STRUCTURES = builder
                .comment(
                        "Comma-separated list of structures the AI can find and teleport to.",
                        "Format: 'display_name:tag_path' (uses minecraft namespace)",
                        "Or: 'display_name:tag_namespace:tag_path' (for modded structures)",
                        "Examples:",
                        "  village:village -> minecraft:village",
                        "  my_structure:mymod:my_structure_tag -> mymod:my_structure_tag"
                )
                .define("custom_structures",
                        "village:village, stronghold:stronghold, mineshaft:mineshaft, " +
                        "desert_pyramid:desert_pyramid, jungle_pyramid:jungle_pyramid, " +
                        "swamp_hut:swamp_hut, ocean_monument:ocean_monument, igloo:igloo, " +
                        "pillager_outpost:pillager_outpost, nether_fortress:fortress, " +
                        "bastion_remnant:bastion_remnant, end_city:end_city, " +
                        "ruined_portal:ruined_portal, shipwreck:shipwreck, " +
                        "ocean_ruin:ocean_ruin, trail_ruins:trail_ruins, " +
                        "ancient_city:ancient_city"
                );

        builder.pop();

        builder.comment("Mod Compatibility Settings").push("mod_compat");

        ENABLE_REGISTRY_SCANNING = builder
                .comment(
                        "Scan the item registry at startup and provide modded item info to the AI.",
                        "This lets the AI know about items from other mods in the server."
                )
                .define("enable_registry_scanning", true);

        MAX_REGISTRY_ITEMS_PER_CATEGORY = builder
                .comment("Maximum number of items to list per mod namespace in the AI prompt (0 = unlimited)")
                .defineInRange("max_registry_items_per_category", 50, 0, 500);

        builder.pop();

        builder.comment("Advanced Settings").push("advanced");

        ENABLE_AI_PERSONALITIES = builder
                .comment("Enable different AI personality modes")
                .define("enable_ai_personalities", false);

        CONTEXT_CACHE_TTL_SECONDS = builder
                .comment("Time-to-live for cached player context data in seconds")
                .defineInRange("context_cache_ttl_seconds", 60, 10, 600);

        builder.pop();

        COMMON_CONFIG = builder.build();
    }
}

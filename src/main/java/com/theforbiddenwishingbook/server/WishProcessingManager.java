package com.theforbiddenwishingbook.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import com.theforbiddenwishingbook.config.ModConfig;
import com.theforbiddenwishingbook.data.WishBookData;
import com.theforbiddenwishingbook.network.WishResponsePayload;
import com.theforbiddenwishingbook.registry.ModDataComponents;
import com.theforbiddenwishingbook.reputation.ReputationService;
import com.theforbiddenwishingbook.reputation.WishMemoryService;
import com.theforbiddenwishingbook.service.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class WishProcessingManager {
    private static final Gson GSON = new Gson();
    private static LLMProvider llmProvider;
    private static final WorldContextScanner CONTEXT_SCANNER = new WorldContextScanner();
    private static final WishExecutor WISH_EXECUTOR = new WishExecutor();
    private static final PaymentService PAYMENT_SERVICE = new PaymentService();
    private static final BookWriterService BOOK_WRITER = new BookWriterService();
    private static final WishActionRegistry ACTION_REGISTRY = new WishActionRegistry();

    private static final Map<UUID, Long> COOLDOWN_MAP = new ConcurrentHashMap<>();
    private static final Set<UUID> PROCESSING_PLAYERS = ConcurrentHashMap.newKeySet();

    public static void handleWishSubmission(com.theforbiddenwishingbook.network.WishSubmissionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;

            UUID playerUUID = player.getUUID();

            // Check cooldown
            long now = System.currentTimeMillis();
            long cooldownMs = ModConfig.COOLDOWN_SECONDS.get() * 1000L;
            Long lastWishTime = COOLDOWN_MAP.get(playerUUID);
            if (lastWishTime != null && (now - lastWishTime) < cooldownMs) {
                long remaining = (cooldownMs - (now - lastWishTime)) / 1000;
                sendDenialResponse(player, payload.inventorySlot(), "You must wait " + remaining + " more seconds before making another wish.");
                return;
            }

            // Check if already processing
            if (!PROCESSING_PLAYERS.add(playerUUID)) {
                sendDenialResponse(player, payload.inventorySlot(), "A wish is already being processed. Please wait.");
                return;
            }

            // Validate the book
            ItemStack bookStack = player.getInventory().getItem(payload.inventorySlot());
            if (!bookStack.is(com.theforbiddenwishingbook.registry.ModItems.BOOK_OF_WISHES.get())) {
                PROCESSING_PLAYERS.remove(playerUUID);
                sendDenialResponse(player, payload.inventorySlot(), "You must be holding a Book of Wishes.");
                return;
            }

            // Check max wishes per book
            WishBookData currentData = bookStack.getOrDefault(
                    ModDataComponents.WISH_BOOK_DATA.get(), WishBookData.EMPTY);
            long processedCount = currentData.wishes().stream()
                    .filter(w -> w.status() != WishBookData.WishStatus.PENDING)
                    .count();
            if (processedCount >= ModConfig.MAX_WISHES_PER_BOOK.get()) {
                PROCESSING_PLAYERS.remove(playerUUID);
                sendDenialResponse(player, payload.inventorySlot(),
                        "This book has reached its maximum number of wishes. The entity will grant no more.");
                return;
            }

            // Filter to only new/empty pages
            List<int[]> newWishPages = new ArrayList<>();
            for (int i = 0; i < payload.pages().size(); i++) {
                String pageText = payload.pages().get(i);
                if (pageText != null && !pageText.isBlank()) {
                    final int pageNum = i;
                    boolean alreadyProcessed = currentData.wishes().stream()
                            .anyMatch(w -> w.pageNumber() == pageNum && w.status() != WishBookData.WishStatus.PENDING);
                    if (!alreadyProcessed) {
                        newWishPages.add(new int[]{pageNum, i});
                    }
                }
            }

            if (newWishPages.isEmpty()) {
                PROCESSING_PLAYERS.remove(playerUUID);
                sendDenialResponse(player, payload.inventorySlot(), "No new wishes found in the book.");
                return;
            }

            // Build parallel lists: wish texts and their page numbers
            List<String> newWishes = new ArrayList<>();
            List<Integer> newWishPageNumbers = new ArrayList<>();
            for (int[] pair : newWishPages) {
                newWishPageNumbers.add(pair[0]);
                newWishes.add(payload.pages().get(pair[1]));
            }

            // Mark cooldown
            COOLDOWN_MAP.put(playerUUID, now);

            // Send processing particles to player
            sendProcessingParticles(player);

            // Begin async processing
            processWishesAsync(player, payload.inventorySlot(), newWishes, newWishPageNumbers);
        });
    }

    private static void processWishesAsync(ServerPlayer player, int inventorySlot, List<String> wishes, List<Integer> pageNumbers) {
        CompletableFuture.runAsync(() -> {
            try {
                // Initialize provider if needed
                if (llmProvider == null) {
                    llmProvider = LLMProvider.create(ModConfig.LLM_PROVIDER.get());
                }

                // Gather world context
                int cacheTtl = ModConfig.CONTEXT_CACHE_TTL_SECONDS.get();
                String worldContext = CONTEXT_SCANNER.scanAndSerialize(player, cacheTtl);

                // Build prompt
                String prompt;
                if (wishes.size() == 1) {
                    prompt = AIPromptBuilder.buildWishPrompt(player, wishes.get(0), worldContext);
                } else {
                    prompt = AIPromptBuilder.buildMultiWishPrompt(player, wishes, worldContext);
                }

                TheForbiddenWishingBook.LOGGER.info("Sending wish to {} ({} wishes, {} chars)",
                        llmProvider.getProviderName(), wishes.size(), prompt.length());

                // Call LLM provider
                String model = ModConfig.OLLAMA_MODEL.get();
                OllamaClient.OllamaResponse response = llmProvider.generate(prompt, model).join();

                if (!response.success()) {
                    TheForbiddenWishingBook.LOGGER.error("Ollama failed: {}", response.error());
                    sendDenialResponse(player, inventorySlot,
                            "The ancient entity could not hear your wish. The connection to the spirit realm has been severed.");
                    PROCESSING_PLAYERS.remove(player.getUUID());
                    return;
                }

                // Parse AI response
                JsonObject aiResponse = parseAIResponse(response.response());
                if (aiResponse == null) {
                    sendDenialResponse(player, inventorySlot,
                            "The entity speaks in riddles that cannot be understood. Try again.");
                    PROCESSING_PLAYERS.remove(player.getUUID());
                    return;
                }

                // Schedule execution on main thread
                final List<String> finalWishes = wishes;
                final List<Integer> finalPageNumbers = pageNumbers;
                player.getServer().execute(() -> {
                    try {
                        executeAIResponse(player, inventorySlot, aiResponse, finalWishes, finalPageNumbers);
                    } finally {
                        PROCESSING_PLAYERS.remove(player.getUUID());
                    }
                });

            } catch (Exception e) {
                TheForbiddenWishingBook.LOGGER.error("Wish processing failed", e);
                player.getServer().execute(() -> {
                    sendDenialResponse(player, inventorySlot,
                            "An error occurred while processing your wish. The entity retreats into shadow.");
                    PROCESSING_PLAYERS.remove(player.getUUID());
                });
            }
        });
    }

    private static boolean isElevated(ServerPlayer player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    private static void executeAIResponse(ServerPlayer player, int inventorySlot, JsonObject aiResponse, List<String> wishes, List<Integer> pageNumbers) {
        boolean elevated = isElevated(player);
        boolean granted = elevated || (aiResponse.has("granted") && aiResponse.get("granted").getAsBoolean());
        String responseText = aiResponse.has("response_text") ? aiResponse.get("response_text").getAsString() : "The entity remains silent.";

        if (elevated) {
            responseText = "[The entity bows before your power.] " + responseText;
        }

        ItemStack bookStack = player.getInventory().getItem(inventorySlot);
        List<WishResponsePayload.WishResult> results = new ArrayList<>();
        List<String> allPayments = new ArrayList<>();

        // Execute actions if granted
        if (granted && aiResponse.has("actions")) {
            JsonArray actions = aiResponse.getAsJsonArray("actions");
            for (JsonElement actionEl : actions) {
                if (actionEl.isJsonObject()) {
                    Map<String, Object> action = jsonObjectToMap(actionEl.getAsJsonObject());
                    WishExecutor.ExecutionResult result = WISH_EXECUTOR.execute(player, action);
                    if (!result.success()) {
                        TheForbiddenWishingBook.LOGGER.warn("Action failed: {}", result.messages());
                    }
                }
            }
        }

        // Extract payment if payment system is enabled (skip for OP/Creative)
        if (!elevated && ModConfig.ENABLE_PAYMENT_SYSTEM.get() && aiResponse.has("payment")) {
            JsonArray payments = aiResponse.getAsJsonArray("payment");
            List<Map<String, Object>> paymentList = new ArrayList<>();
            for (JsonElement paymentEl : payments) {
                if (paymentEl.isJsonObject()) {
                    paymentList.add(jsonObjectToMap(paymentEl.getAsJsonObject()));
                }
            }

            PaymentService.PaymentResult paymentResult = PAYMENT_SERVICE.extractPayment(player, paymentList);
            allPayments.addAll(paymentResult.extracted());

            if (!paymentResult.failed().isEmpty()) {
                TheForbiddenWishingBook.LOGGER.warn("Partial payment extraction. Failed: {}", paymentResult.failed());
                allPayments.addAll(paymentResult.failed());
            }
        }

        // Determine final status
        WishBookData.WishStatus finalStatus = granted ? WishBookData.WishStatus.GRANTED : WishBookData.WishStatus.DENIED;

        // Record wish in memory
        for (String wish : wishes) {
            String wishType = determineWishType(wish);
            int estimatedValue = estimateWishValue(wish);
            WishMemoryService.recordWish(player, wish, granted,
                    String.join(", ", allPayments), wishType, estimatedValue);
        }

        // Update reputation
        int trustChange = granted ? 5 : -2;
        int valueExchanged = wishes.stream().mapToInt(WishProcessingManager::estimateWishValue).sum();
        ReputationService.modifyReputation(player, trustChange, valueExchanged, granted);

        // Write responses to book for each wish
        for (int i = 0; i < wishes.size(); i++) {
            int pageNum = pageNumbers.get(i);
            String wishText = wishes.get(i);

            results.add(new WishResponsePayload.WishResult(
                    pageNum,
                    finalStatus,
                    responseText,
                    allPayments
            ));

            // Also write to the book's persistent data
            BOOK_WRITER.writeResponseToBook(
                    bookStack,
                    pageNum,
                    wishText,
                    finalStatus,
                    responseText,
                    allPayments
            );
        }

        // Send response to client
        WishResponsePayload responsePayload = new WishResponsePayload(inventorySlot, results);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, responsePayload);

        // Send visual feedback
        if (granted) {
            sendGrantParticles(player);
            sendPaymentNotification(player, allPayments);
        } else {
            sendDenialParticles(player);
        }

        TheForbiddenWishingBook.LOGGER.info("Wish processed for {}: {} (granted={})",
                player.getName().getString(), wishes.size(), granted);
    }

    private static JsonObject parseAIResponse(String responseText) {
        try {
            // Try to extract JSON from the response (may have surrounding text)
            String trimmed = responseText.strip();

            // Find the first { and last }
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');

            if (start >= 0 && end > start) {
                String jsonStr = trimmed.substring(start, end + 1);
                return GSON.fromJson(jsonStr, JsonObject.class);
            }

            return GSON.fromJson(trimmed, JsonObject.class);
        } catch (Exception e) {
            TheForbiddenWishingBook.LOGGER.error("Failed to parse AI response as JSON: {}", e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> jsonObjectToMap(JsonObject obj) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive()) {
                var prim = value.getAsJsonPrimitive();
                if (prim.isNumber()) {
                    map.put(entry.getKey(), prim.getAsNumber());
                } else if (prim.isBoolean()) {
                    map.put(entry.getKey(), prim.getAsBoolean());
                } else {
                    map.put(entry.getKey(), prim.getAsString());
                }
            } else if (value.isJsonArray()) {
                List<Object> list = new ArrayList<>();
                for (JsonElement el : value.getAsJsonArray()) {
                    if (el.isJsonObject()) {
                        list.add(jsonObjectToMap(el.getAsJsonObject()));
                    } else if (el.isJsonPrimitive()) {
                        list.add(el.getAsString());
                    }
                }
                map.put(entry.getKey(), list);
            } else if (value.isJsonObject()) {
                map.put(entry.getKey(), jsonObjectToMap(value.getAsJsonObject()));
            } else {
                map.put(entry.getKey(), value.toString());
            }
        }
        return map;
    }

    private static void sendDenialResponse(ServerPlayer player, int inventorySlot, String message) {
        WishResponsePayload.WishResult result = new WishResponsePayload.WishResult(
                0,
                WishBookData.WishStatus.DENIED,
                message,
                List.of()
        );
        WishResponsePayload payload = new WishResponsePayload(inventorySlot, List.of(result));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }

    private static void sendProcessingParticles(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Enchantment particles swirl around the player while AI thinks
        for (int i = 0; i < 20; i++) {
            double x = player.getX() + (level.random.nextDouble() - 0.5) * 2;
            double y = player.getY() + level.random.nextDouble() * 2;
            double z = player.getZ() + (level.random.nextDouble() - 0.5) * 2;
            level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private static void sendGrantParticles(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Gold dust particles burst when wish is granted
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
                30, 0.5, 0.5, 0.5, 0.5);
        // Play success sound
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void sendDenialParticles(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Dark smoke particles when wish is denied
        level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1, player.getZ(),
                15, 0.3, 0.3, 0.3, 0.02);
        // Play denial sound
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.3F, 1.5F);
    }

    private static void sendPaymentNotification(ServerPlayer player, List<String> payments) {
        if (payments.isEmpty()) return;
        // Send action bar message showing what was taken
        String paymentText = String.join(" | ", payments);
        if (paymentText.length() > 80) {
            paymentText = paymentText.substring(0, 77) + "...";
        }
        player.displayClientMessage(
                Component.literal("\u00A76\u00A7oThe book takes its due: ").append(Component.literal(paymentText).withStyle(net.minecraft.ChatFormatting.GOLD)),
                true // action bar
        );
    }

    private static String determineWishType(String wishText) {
        String lower = wishText.toLowerCase();
        if (lower.contains("give") || lower.contains("item") || lower.contains("diamond") || lower.contains("netherite") || lower.contains("craft")) return "items";
        if (lower.contains("kill") || lower.contains("destroy") || lower.contains("steal") || lower.contains("pvp") || lower.contains("attack")) return "pvp";
        if (lower.contains("teleport") || lower.contains("tp") || lower.contains("transport")) return "teleport";
        if (lower.contains("effect") || lower.contains("immortal") || lower.contains("fly") || lower.contains("speed") || lower.contains("regeneration")) return "effects";
        if (lower.contains("spawn") || lower.contains("mob") || lower.contains("boss") || lower.contains("summon")) return "entities";
        if (lower.contains("weather") || lower.contains("time") || lower.contains("day") || lower.contains("night") || lower.contains("rain")) return "world";
        if (lower.contains("xp") || lower.contains("experience") || lower.contains("level")) return "progression";
        if (lower.contains("build") || lower.contains("structure") || lower.contains("castle") || lower.contains("house")) return "building";
        return "general";
    }

    private static int estimateWishValue(String wishText) {
        String lower = wishText.toLowerCase();
        int value = 10;

        if (lower.contains("netherite")) value += 50;
        if (lower.contains("diamond")) value += 30;
        if (lower.contains("enchanted") || lower.contains("ench")) value += 20;
        if (lower.contains("immortal")) value += 100;
        if (lower.contains("kill")) value += 40;
        if (lower.contains("destroy")) value += 50;
        if (lower.contains("steal")) value += 45;
        if (lower.contains("teleport")) value += 15;
        if (lower.contains("boss")) value += 80;
        if (lower.contains("beacon")) value += 60;
        if (lower.contains("elytra")) value += 70;
        if (lower.contains("totem")) value += 55;
        if (lower.contains("shulker")) value += 40;
        if (lower.contains("dragon")) value += 90;
        if (lower.contains("wither")) value += 85;
        if (lower.contains("flight") || lower.contains("fly")) value += 35;
        if (lower.contains("regeneration") || lower.contains("regen")) value += 25;
        if (lower.contains("resistance")) value += 20;

        return value;
    }
}

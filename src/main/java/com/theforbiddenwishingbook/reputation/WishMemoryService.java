package com.theforbiddenwishingbook.reputation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WishMemoryService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, WishMemory> MEMORY_CACHE = new ConcurrentHashMap<>();

    public record WishMemory(
            UUID playerUUID,
            List<WishRecord> pastWishes,
            int totalWishesGranted,
            int totalWishesDenied,
            int totalPaymentMade,
            long firstWishTime,
            long lastWishTime,
            List<String> favoriteWishTypes,
            String strongestPayment
    ) {
        public static WishMemory empty(UUID uuid) {
            return new WishMemory(uuid, List.of(), 0, 0, 0, 0, 0, List.of(), "");
        }
    }

    public record WishRecord(
            long timestamp,
            String wishText,
            boolean granted,
            String paymentDescription,
            String wishType,
            int estimatedValue
    ) {}

    public static WishMemory getMemory(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (MEMORY_CACHE.containsKey(uuid)) {
            return MEMORY_CACHE.get(uuid);
        }
        return loadMemory(uuid);
    }

    public static void recordWish(ServerPlayer player, String wishText, boolean granted,
                                   String paymentDescription, String wishType, int estimatedValue) {
        UUID uuid = player.getUUID();
        WishMemory current = getMemory(player);

        WishRecord record = new WishRecord(
                System.currentTimeMillis(),
                wishText,
                granted,
                paymentDescription,
                wishType,
                estimatedValue
        );

        List<WishRecord> newRecords = new ArrayList<>(current.pastWishes());
        newRecords.add(record);

        // Keep only last 50 wishes
        if (newRecords.size() > 50) {
            newRecords = newRecords.subList(newRecords.size() - 50, newRecords.size());
        }

        // Update favorite types
        Map<String, Integer> typeCounts = new HashMap<>();
        for (WishRecord r : newRecords) {
            typeCounts.merge(r.wishType(), 1, Integer::sum);
        }
        List<String> favorites = typeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        // Find strongest payment
        String strongest = current.strongestPayment();
        int maxPayment = 0;
        for (WishRecord r : newRecords) {
            if (r.estimatedValue() > maxPayment) {
                maxPayment = r.estimatedValue();
                strongest = r.paymentDescription();
            }
        }

        WishMemory updated = new WishMemory(
                uuid,
                newRecords,
                current.totalWishesGranted() + (granted ? 1 : 0),
                current.totalWishesDenied() + (granted ? 0 : 1),
                current.totalPaymentMade() + estimatedValue,
                current.firstWishTime() == 0 ? System.currentTimeMillis() : current.firstWishTime(),
                System.currentTimeMillis(),
                favorites,
                strongest
        );

        MEMORY_CACHE.put(uuid, updated);
        saveMemory(uuid, updated);
    }

    private static WishMemory loadMemory(UUID uuid) {
        Path memoryFile = getMemoryFile(uuid);
        if (Files.exists(memoryFile)) {
            try {
                String json = Files.readString(memoryFile);
                WishMemory memory = GSON.fromJson(json, WishMemory.class);
                MEMORY_CACHE.put(uuid, memory);
                return memory;
            } catch (Exception e) {
                TheForbiddenWishingBook.LOGGER.error("Failed to load wish memory for {}: {}", uuid, e.getMessage());
            }
        }
        return WishMemory.empty(uuid);
    }

    private static void saveMemory(UUID uuid, WishMemory memory) {
        try {
            Path memoryDir = Path.of("config", "thebookofwishes", "memories");
            Files.createDirectories(memoryDir);
            Path memoryFile = memoryDir.resolve(uuid + ".json");
            Files.writeString(memoryFile, GSON.toJson(memory));
        } catch (IOException e) {
            TheForbiddenWishingBook.LOGGER.error("Failed to save wish memory for {}: {}", uuid, e.getMessage());
        }
    }

    private static Path getMemoryFile(UUID uuid) {
        return Path.of("config", "thebookofwishes", "memories", uuid + ".json");
    }

    public static String buildMemoryContext(ServerPlayer player) {
        WishMemory memory = getMemory(player);

        if (memory.pastWishes().isEmpty()) {
            return "This is the first time this mortal has opened the book. They are unknown to you.";
        }

        StringBuilder context = new StringBuilder();
        context.append("MEMORY OF THIS MORTAL:\n");
        context.append("- Total wishes made: ").append(memory.pastWishes().size()).append("\n");
        context.append("- Wishes granted: ").append(memory.totalWishesGranted()).append("\n");
        context.append("- Wishes denied: ").append(memory.totalWishesDenied()).append("\n");
        context.append("- Total value extracted: ").append(memory.totalPaymentMade()).append("\n");

        if (!memory.favoriteWishTypes().isEmpty()) {
            context.append("- Frequent wish types: ").append(String.join(", ", memory.favoriteWishTypes())).append("\n");
        }

        if (!memory.strongestPayment().isEmpty()) {
            context.append("- Most expensive payment taken: ").append(memory.strongestPayment()).append("\n");
        }

        // Include recent wishes for context
        List<WishRecord> recent = memory.pastWishes().stream()
                .skip(Math.max(0, memory.pastWishes().size() - 5))
                .toList();

        if (!recent.isEmpty()) {
            context.append("- Recent wishes:\n");
            for (WishRecord record : recent) {
                context.append("  * \"").append(record.wishText()).append("\" - ");
                context.append(record.granted() ? "GRANTED" : "DENIED");
                if (!record.paymentDescription().isEmpty()) {
                    context.append(" (payment: ").append(record.paymentDescription()).append(")");
                }
                context.append("\n");
            }
        }

        return context.toString();
    }
}

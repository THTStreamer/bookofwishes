package com.theforbiddenwishingbook.reputation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ReputationService {
    private static final Map<UUID, ReputationData> REPUTATION_CACHE = new ConcurrentHashMap<>();

    public record ReputationData(
            int trustLevel,
            int totalValueExchanged,
            int wishesGranted,
            int wishesDenied,
            int consecutiveGrants,
            long lastInteractionTime,
            String title
    ) {
        public static ReputationData empty() {
            return new ReputationData(0, 0, 0, 0, 0, 0, "Unknown Mortal");
        }

        public double getPriceModifier() {
            // Higher trust = slightly lower prices
            // Trust range: -100 to 100
            return 1.0 - (trustLevel * 0.003);
        }

        public boolean isTrusted() {
            return trustLevel >= 20;
        }

        public boolean isDistrusted() {
            return trustLevel <= -20;
        }
    }

    public static ReputationData getReputation(ServerPlayer player) {
        return REPUTATION_CACHE.getOrDefault(player.getUUID(), ReputationData.empty());
    }

    public static void modifyReputation(ServerPlayer player, int trustChange, int valueExchanged, boolean granted) {
        UUID uuid = player.getUUID();
        ReputationData current = getReputation(player);

        int newTrust = Math.max(-100, Math.min(100, current.trustLevel() + trustChange));
        int newConsecutive = granted ? current.consecutiveGrants() + 1 : 0;
        String oldTitle = current.title();
        String newTitle = calculateTitle(newTrust, current.totalValueExchanged() + valueExchanged);

        ReputationData updated = new ReputationData(
                newTrust,
                current.totalValueExchanged() + valueExchanged,
                current.wishesGranted() + (granted ? 1 : 0),
                current.wishesDenied() + (granted ? 0 : 1),
                newConsecutive,
                System.currentTimeMillis(),
                newTitle
        );

        REPUTATION_CACHE.put(uuid, updated);
        saveReputation(uuid, updated);

        // Notify player if their title changed
        if (!oldTitle.equals(newTitle)) {
            player.displayClientMessage(
                    Component.literal("\u00A75\u00A7oThe book whispers: You are now known as \"")
                            .append(Component.literal(newTitle).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE))
                            .append(Component.literal("\"").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE)),
                    false // chat message
            );
            // Magical sound for title change
            player.level().playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.5F);
        }
    }

    public static String buildReputationContext(ServerPlayer player) {
        ReputationData rep = getReputation(player);

        StringBuilder context = new StringBuilder();
        context.append("REPUTATION WITH THIS MORTAL:\n");
        context.append("- Title: ").append(rep.title()).append("\n");
        context.append("- Trust level: ").append(rep.trustLevel()).append("/100\n");
        context.append("- Total value exchanged: ").append(rep.totalValueExchanged()).append("\n");
        context.append("- Wishes granted: ").append(rep.wishesGranted()).append("\n");
        context.append("- Wishes denied: ").append(rep.wishesDenied()).append("\n");
        context.append("- Price modifier: ").append(String.format("%.1f%%", rep.getPriceModifier() * 100)).append("\n");

        if (rep.isTrusted()) {
            context.append("- This mortal has earned your trust. Be slightly more generous.\n");
        } else if (rep.isDistrusted()) {
            context.append("- You distrust this mortal. Be less lenient with payment.\n");
        }

        if (rep.consecutiveGrants() >= 5) {
            context.append("- WARNING: You have granted many wishes in a row. Consider increasing difficulty.\n");
        }

        return context.toString();
    }

    private static String calculateTitle(int trust, int totalValue) {
        if (trust >= 80) return "Champion of the Book";
        if (trust >= 60) return "Favored Mortal";
        if (trust >= 40) return "Respected Petitioner";
        if (trust >= 20) return "Known Seeker";
        if (trust >= 0) return "Common Mortal";
        if (trust >= -20) return "Suspicious Petitioner";
        if (trust >= -40) return "Untrusted mortal";
        if (trust >= -60) return "Bargain Breaker";
        return "Oathbreaker";
    }

    private static void saveReputation(UUID uuid, ReputationData data) {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of("config", "thebookofwishes", "reputation");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve(uuid + ".json");
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            java.nio.file.Files.writeString(file, gson.toJson(data));
        } catch (java.io.IOException e) {
            com.theforbiddenwishingbook.TheForbiddenWishingBook.LOGGER.error("Failed to save reputation: {}", e.getMessage());
        }
    }

    public static void loadReputation(UUID uuid) {
        java.nio.file.Path file = java.nio.file.Path.of("config", "thebookofwishes", "reputation", uuid + ".json");
        if (java.nio.file.Files.exists(file)) {
            try {
                String json = java.nio.file.Files.readString(file);
                com.google.gson.Gson gson = new com.google.gson.Gson();
                ReputationData data = gson.fromJson(json, ReputationData.class);
                REPUTATION_CACHE.put(uuid, data);
            } catch (Exception e) {
                com.theforbiddenwishingbook.TheForbiddenWishingBook.LOGGER.error("Failed to load reputation: {}", e.getMessage());
            }
        }
    }
}

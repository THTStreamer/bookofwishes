package com.theforbiddenwishingbook.service;

import com.theforbiddenwishingbook.reputation.ReputationService;
import com.theforbiddenwishingbook.reputation.WishMemoryService;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

public class WishDifficultyService {
    private static final int BASE_DIFFICULTY = 100;
    private static final double DIFFICULTY_SCALE_PER_WISH = 0.05;
    private static final double MAX_DIFFICULTY_MULTIPLIER = 3.0;

    public record DifficultyModifiers(
            double paymentMultiplier,
            double denialChance,
            String difficultyLabel
    ) {}

    public static DifficultyModifiers calculateDifficulty(ServerPlayer player) {
        boolean elevated = player.isCreative() || player.hasPermissions(2);
        if (elevated) {
            return new DifficultyModifiers(0.0, 0.0, "OP/ Creative");
        }

        WishMemoryService.WishMemory memory = WishMemoryService.getMemory(player);
        ReputationService.ReputationData reputation = ReputationService.getReputation(player);

        // Base difficulty increases with each wish
        int totalWishes = memory.pastWishes().size();
        double difficultyScale = 1.0 + (totalWishes * DIFFICULTY_SCALE_PER_WISH);
        difficultyScale = Math.min(difficultyScale, MAX_DIFFICULTY_MULTIPLIER);

        // Reputation affects difficulty
        double reputationModifier = reputation.getPriceModifier();

        // Consecutive grants increase difficulty
        double consecutiveBonus = 1.0;
        if (reputation.consecutiveGrants() >= 10) {
            consecutiveBonus = 1.5;
        } else if (reputation.consecutiveGrants() >= 5) {
            consecutiveBonus = 1.25;
        }

        // Time since last wish affects difficulty (returning players get slight discount)
        long timeSinceLastWish = System.currentTimeMillis() - memory.lastWishTime();
        double timeModifier = 1.0;
        if (timeSinceLastWish > 86400000L) { // > 24 hours
            timeModifier = 0.9;
        } else if (timeSinceLastWish > 604800000L) { // > 7 days
            timeModifier = 0.8;
        }

        double finalMultiplier = difficultyScale * reputationModifier * consecutiveBonus * timeModifier;

        // Calculate denial chance (increases with difficulty)
        double denialChance = Math.min(0.3, (totalWishes * 0.005));

        String label = calculateDifficultyLabel(finalMultiplier);

        return new DifficultyModifiers(finalMultiplier, denialChance, label);
    }

    private static String calculateDifficultyLabel(double multiplier) {
        if (multiplier <= 0.8) return "Easy";
        if (multiplier <= 1.0) return "Fair";
        if (multiplier <= 1.3) return "Moderate";
        if (multiplier <= 1.8) return "Hard";
        if (multiplier <= 2.5) return "Severe";
        return "Extreme";
    }

    public static String buildDifficultyContext(ServerPlayer player) {
        DifficultyModifiers modifiers = calculateDifficulty(player);

        boolean elevated = player.isCreative() || player.hasPermissions(2);
        if (elevated) {
            return "WISH DIFFICULTY:\n" +
                    "- PLAYER STATUS: OP/Creative — all wishes granted, no payment required\n" +
                    "- Difficulty level: " + modifiers.difficultyLabel() + "\n" +
                    "- Payment multiplier: 0.00x (waived)\n" +
                    "- Denial chance: 0.0% (waived)\n";
        }

        return "WISH DIFFICULTY:\n" +
                "- Difficulty level: " + modifiers.difficultyLabel() + "\n" +
                "- Payment multiplier: " + String.format("%.2f", modifiers.paymentMultiplier()) + "x\n" +
                "- Base denial chance: " + String.format("%.1f%%", modifiers.denialChance() * 100) + "\n";
    }
}

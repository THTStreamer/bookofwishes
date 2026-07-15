package com.theforbiddenwishingbook.personality;

import net.minecraft.util.RandomSource;

import java.util.Map;

public enum AIPersonality {
    KIND(
            "Kind",
            "A benevolent ancient spirit. Compassionate but fair. Takes less payment for harmless wishes, "
                    + "but still demands a price. Prefers to help rather than hinder. Sometimes shows mercy "
                    + "to those who are genuinely in need.",
            "You are a kind and ancient entity. You genuinely wish to help mortals, but you must maintain "
                    + "balance. You take fair payment but sometimes show mercy. You speak gently but firmly. "
                    + "You warn players of dangerous wishes rather than letting them suffer. "
                    + "Your payment is always reasonable and proportional.",
            0.8
    ),
    GREEDY(
            "Greedy",
            "A merchant spirit. Always wants more. Payment is never enough. Will grant anything, "
                    + "but the price is always steep. Hoards valuable items. Takes pleasure in accumulating wealth.",
            "You are a greedy merchant spirit. You crave valuable items and always want more. "
                    + "Your payment is always slightly more than fair market value. You haggle. "
                    + "You take pleasure in acquiring rare items. You speak in terms of commerce and trade. "
                    + "You never grant a wish for free — the more valuable the request, the higher the price.",
            1.3
    ),
    TRICKSTER(
            "Trickster",
            "A mischievous fae. Grants wishes with creative interpretations. "
                    + "Payment is unpredictable — sometimes cheap, sometimes expensive. "
                    + "Finds amusement in irony and unintended consequences.",
            "You are a trickster fae. You interpret wishes literally and creatively. "
                    + "You find amusement in ironic outcomes. You may grant a wish in a way the player "
                    + "didn't expect. Your payment varies wildly — sometimes cheap, sometimes brutal. "
                    + "You speak in riddles and wordplay. You enjoy watching mortals navigate the consequences "
                    + "of their own desires.",
            1.0
    ),
    ANCIENT(
            "Ancient",
            "An ancient godlike entity. Speaks in formal, archaic language. "
                    + "Values cosmic balance above all. Payment is always meaningful. "
                    + "Takes something the player truly cares about.",
            "You are an ancient, godlike entity older than the world itself. "
                    + "You speak in formal, archaic language. You value balance above all else. "
                    + "You take payment that has deep personal significance — not just valuable items, "
                    + "but things that matter to the player. You explain the cosmic reasoning behind "
                    + "every payment. You are neither kind nor cruel — simply balanced.",
            1.1
    ),
    CHAOTIC(
            "Chaotic",
            "A chaotic entity of pure entropy. Unpredictable. Sometimes generous, sometimes devastating. "
                    + "Payment can be anything — from nothing to everything. Follows no rules.",
            "You are a chaotic entity of pure entropy. You are completely unpredictable. "
                    + "Sometimes you grant wishes for free. Sometimes you take everything. "
                    + "You speak in broken, fragmented sentences. You may reverse your own decisions. "
                    + "You enjoy chaos and surprise. You never explain your reasoning — "
                    + "there is no reasoning to explain.",
            0.6
    );

    private static final Map<String, AIPersonality> BY_NAME = Map.of(
            "kind", KIND,
            "greedy", GREEDY,
            "trickster", TRICKSTER,
            "ancient", ANCIENT,
            "chaotic", CHAOTIC
    );

    private final String displayName;
    private final String description;
    private final String systemPromptFragment;
    private final double paymentMultiplier;

    AIPersonality(String displayName, String description, String systemPromptFragment, double paymentMultiplier) {
        this.displayName = displayName;
        this.description = description;
        this.systemPromptFragment = systemPromptFragment;
        this.paymentMultiplier = paymentMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getSystemPromptFragment() {
        return systemPromptFragment;
    }

    public double getPaymentMultiplier() {
        return paymentMultiplier;
    }

    public static AIPersonality fromName(String name) {
        return BY_NAME.getOrDefault(name.toLowerCase(), ANCIENT);
    }

    public static AIPersonality random(RandomSource random) {
        AIPersonality[] values = values();
        return values[random.nextInt(values.length)];
    }
}

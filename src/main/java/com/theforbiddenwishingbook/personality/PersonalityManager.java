package com.theforbiddenwishingbook.personality;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PersonalityManager {
    private static final Map<UUID, AIPersonality> PLAYER_PERSONALITIES = new ConcurrentHashMap<>();
    private static AIPersonality serverDefault = AIPersonality.ANCIENT;

    public static AIPersonality getPersonality(ServerPlayer player) {
        return PLAYER_PERSONALITIES.getOrDefault(player.getUUID(), serverDefault);
    }

    public static void setPersonality(ServerPlayer player, AIPersonality personality) {
        PLAYER_PERSONALITIES.put(player.getUUID(), personality);
    }

    public static AIPersonality getServerDefault() {
        return serverDefault;
    }

    public static void setServerDefault(AIPersonality personality) {
        serverDefault = personality;
    }

    public static CompoundTag savePlayerData(UUID playerUUID) {
        CompoundTag tag = new CompoundTag();
        AIPersonality personality = PLAYER_PERSONALITIES.get(playerUUID);
        if (personality != null) {
            tag.putString("personality", personality.name());
        }
        return tag;
    }

    public static void loadPlayerData(UUID playerUUID, CompoundTag tag) {
        if (tag.contains("personality")) {
            try {
                AIPersonality personality = AIPersonality.valueOf(tag.getString("personality"));
                PLAYER_PERSONALITIES.put(playerUUID, personality);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}

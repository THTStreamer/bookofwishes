package com.theforbiddenwishingbook.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.theforbiddenwishingbook.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

import java.util.*;

public class WorldContextScanner {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, PlayerContextCache> CONTEXT_CACHE = new HashMap<>();

    public record PlayerContextCache(
            String contextJson,
            long timestamp,
            int chunkX,
            int chunkZ
    ) {
        public boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    public String scanAndSerialize(ServerPlayer player, int cacheTtlSeconds) {
        UUID uuid = player.getUUID();
        long ttlMs = cacheTtlSeconds * 1000L;

        PlayerContextCache cached = CONTEXT_CACHE.get(uuid);
        if (cached != null && !cached.isExpired(ttlMs)) {
            int currentChunkX = player.chunkPosition().x;
            int currentChunkZ = player.chunkPosition().z;
            if (currentChunkX == cached.chunkX && currentChunkZ == cached.chunkZ) {
                return cached.contextJson;
            }
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("player_name", player.getName().getString());
        context.put("uuid", uuid.toString());
        context.put("gamemode", player.gameMode.getGameModeForPlayer().getName());
        context.put("difficulty", player.level().getDifficulty().getDisplayName().getString());
        context.put("dimension", player.level().dimension().location().toString());
        context.put("coordinates", Map.of(
                "x", player.blockPosition().getX(),
                "y", player.blockPosition().getY(),
                "z", player.blockPosition().getZ()
        ));
        context.put("biome", getBiomeName(player));

        context.put("health", Map.of(
                "current", player.getHealth(),
                "max", player.getMaxHealth()
        ));
        context.put("food", Map.of(
                "level", player.getFoodData().getFoodLevel(),
                "saturation", player.getFoodData().getSaturationLevel()
        ));
        context.put("xp", Map.of(
                "level", player.experienceLevel,
                "total", player.totalExperience
        ));

        context.put("inventory_summary", summarizeInventory(player.getInventory()));
        context.put("armor", summarizeArmor(player));
        context.put("nearby_mobs", scanNearbyMobs(player, 32));
        context.put("nearby_villagers", scanNearbyVillagers(player, 48));
        context.put("nearby_players", scanNearbyPlayers(player, 32));
        context.put("nearby_animals", scanNearbyAnimals(player, 32));
        context.put("nearby_storage", scanNearbyStorage(player, 16));

        ServerLevel level = player.serverLevel();
        context.put("weather", Map.of(
                "is_raining", level.isRaining(),
                "is_thundering", level.isThundering()
        ));
        context.put("time", Map.of(
                "day_time", level.getDayTime(),
                "game_time", level.getGameTime()
        ));

        context.put("nearby_structures", scanNearbyStructures(player, 100));
        context.put("game_stage", estimateGameStage(player));

        // Add modded item registry summary
        if (ModConfig.ENABLE_REGISTRY_SCANNING.get()) {
            Map<String, Object> registryInfo = new LinkedHashMap<>();
            registryInfo.put("total_mods", ModdedItemScanner.getModCount());
            registryInfo.put("total_items", ModdedItemScanner.getTotalItemCount());
            context.put("modded_registry", registryInfo);
        }

        String json = GSON.toJson(context);

        CONTEXT_CACHE.put(uuid, new PlayerContextCache(
                json,
                System.currentTimeMillis(),
                player.chunkPosition().x,
                player.chunkPosition().z
        ));

        return json;
    }

    private String getBiomeName(ServerPlayer player) {
        return player.level().getBiome(player.blockPosition()).toString();
    }

    private Map<String, Object> summarizeInventory(Inventory inventory) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Integer> itemCounts = new LinkedHashMap<>();
        int totalSlots = inventory.getContainerSize();
        int occupiedSlots = 0;

        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                occupiedSlots++;
                String itemName = stack.getItem().builtInRegistryHolder().key().location().toString();
                itemCounts.merge(itemName, stack.getCount(), Integer::sum);
            }
        }

        summary.put("total_slots", totalSlots);
        summary.put("occupied_slots", occupiedSlots);
        summary.put("unique_items", itemCounts.size());
        summary.put("top_items", getTopNItems(itemCounts, 10));
        return summary;
    }

    private Map<String, Integer> getTopNItems(Map<String, Integer> items, int n) {
        return items.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private List<Map<String, Object>> summarizeArmor(ServerPlayer player) {
        List<Map<String, Object>> armor = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ItemStack stack = player.getInventory().getArmor(i);
            if (!stack.isEmpty()) {
                Map<String, Object> piece = new LinkedHashMap<>();
                piece.put("slot", i);
                piece.put("item", stack.getItem().builtInRegistryHolder().key().location().toString());
                piece.put("count", stack.getCount());
                armor.add(piece);
            }
        }
        return armor;
    }

    private List<Map<String, Object>> scanNearbyMobs(ServerPlayer player, int radius) {
        List<Map<String, Object>> mobs = new ArrayList<>();
        List<Entity> entities = player.level().getEntities(player,
                player.getBoundingBox().inflate(radius),
                e -> e instanceof net.minecraft.world.entity.Mob && !(e instanceof Villager));

        for (int i = 0; i < Math.min(entities.size(), 20); i++) {
            Entity e = entities.get(i);
            mobs.add(Map.of(
                    "type", e.getType().toShortString(),
                    "distance", (int) player.distanceTo(e)
            ));
        }
        return mobs;
    }

    private List<Map<String, Object>> scanNearbyVillagers(ServerPlayer player, int radius) {
        List<Map<String, Object>> villagers = new ArrayList<>();
        List<Entity> entities = player.level().getEntities(player,
                player.getBoundingBox().inflate(radius),
                e -> e instanceof Villager);

        for (Entity e : entities) {
            Villager v = (Villager) e;
            villagers.add(Map.of(
                    "profession", v.getVillagerData().getProfession().toString(),
                    "distance", (int) player.distanceTo(e),
                    "level", v.getVillagerData().getLevel()
            ));
        }
        return villagers;
    }

    private List<Map<String, Object>> scanNearbyPlayers(ServerPlayer player, int radius) {
        List<Map<String, Object>> players = new ArrayList<>();
        List<? extends Player> levelPlayers = player.level().players();
        for (Player p : levelPlayers) {
            if (p.equals(player)) continue;
            if (player.distanceTo(p) <= radius) {
                players.add(Map.of(
                        "name", p.getName().getString(),
                        "distance", (int) player.distanceTo(p),
                        "health", p.getHealth()
                ));
            }
        }
        return players;
    }

    private List<Map<String, Object>> scanNearbyAnimals(ServerPlayer player, int radius) {
        List<Map<String, Object>> animals = new ArrayList<>();
        List<Entity> entities = player.level().getEntities(player,
                player.getBoundingBox().inflate(radius),
                e -> e instanceof Animal);

        for (int i = 0; i < Math.min(entities.size(), 20); i++) {
            Animal a = (Animal) entities.get(i);
            animals.add(Map.of(
                    "type", a.getType().toShortString(),
                    "distance", (int) player.distanceTo(a)
            ));
        }
        return animals;
    }

    private List<Map<String, Object>> scanNearbyStorage(ServerPlayer player, int radius) {
        List<Map<String, Object>> storage = new ArrayList<>();
        BlockPos playerPos = player.blockPosition();

        for (int x = -radius; x <= radius; x += 4) {
            for (int y = -radius; y <= radius; y += 4) {
                for (int z = -radius; z <= radius; z += 4) {
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    if (!player.level().isLoaded(checkPos)) continue;

                    BlockEntity be = player.level().getBlockEntity(checkPos);
                    if (be instanceof ChestBlockEntity || be instanceof ShulkerBoxBlockEntity) {
                        String type = be instanceof ShulkerBoxBlockEntity ? "shulker_box" : "chest";
                        storage.add(Map.of(
                                "type", type,
                                "x", checkPos.getX(),
                                "y", checkPos.getY(),
                                "z", checkPos.getZ(),
                                "distance", (int) player.blockPosition().distToCenterSqr(checkPos.getX(), checkPos.getY(), checkPos.getZ())
                        ));
                    }
                }
            }
        }
        return storage;
    }

    private List<Map<String, Object>> scanNearbyStructures(ServerPlayer player, int radius) {
        List<Map<String, Object>> structures = new ArrayList<>();
        StructureTracker structureTracker = new StructureTracker();
        List<StructureTracker.StructureInfo> nearbyStructures = structureTracker.getNearbyStructures(player);

        for (StructureTracker.StructureInfo info : nearbyStructures) {
            structures.add(Map.of(
                "type", info.name(),
                "distance", info.distance(),
                "x", info.pos().getX(),
                "y", info.pos().getY(),
                "z", info.pos().getZ()
            ));
        }

        return structures;
    }

    private Map<String, Object> estimateGameStage(ServerPlayer player) {
        String stage;
        List<String> indicators = new ArrayList<>();

        boolean hasNetherite = false;
        boolean hasDiamond = false;
        for (int i = 0; i < 4; i++) {
            ItemStack armor = player.getInventory().getArmor(i);
            if (!armor.isEmpty()) {
                String name = armor.toString().toLowerCase();
                if (name.contains("netherite")) hasNetherite = true;
                if (name.contains("diamond")) hasDiamond = true;
            }
        }

        int xpLevel = player.experienceLevel;

        if (hasNetherite) {
            stage = "late_game";
            indicators.add("netherite_equipment");
        } else if (hasDiamond) {
            stage = "mid_game";
            indicators.add("diamond_equipment");
        } else if (xpLevel > 20) {
            stage = "mid_game";
            indicators.add("high_xp");
        } else {
            stage = "early_game";
            indicators.add("basic_equipment");
        }

        return Map.of("stage", stage, "indicators", indicators);
    }
}

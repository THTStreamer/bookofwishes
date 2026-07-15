package com.theforbiddenwishingbook.service;

import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import com.theforbiddenwishingbook.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StructureTracker {
    private static final Map<UUID, PlayerStructureCache> CACHE = new ConcurrentHashMap<>();
    private static final int SEARCH_RADIUS = 256;
    private static final long CACHE_TTL_MS = 30_000;

    private static List<StructureEntry> parsedStructures = null;

    public record StructureEntry(String name, TagKey<Structure> tag) {}

    public record StructureInfo(String name, BlockPos pos, long distance) {}

    public record PlayerStructureCache(List<StructureInfo> structures, long timestamp, int chunkX, int chunkZ) {
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    private static synchronized List<StructureEntry> getStructures() {
        if (parsedStructures != null) {
            return parsedStructures;
        }

        List<StructureEntry> structures = new ArrayList<>();
        String configValue = ModConfig.CUSTOM_STRUCTURES.get();
        if (configValue == null || configValue.isBlank()) {
            return structures;
        }

        String[] entries = configValue.split(",");
        for (String entry : entries) {
            StructureEntry parsed = parseStructureEntry(entry.trim());
            if (parsed != null) {
                structures.add(parsed);
            }
        }

        parsedStructures = structures;
        return parsedStructures;
    }

    private static StructureEntry parseStructureEntry(String entry) {
        String[] parts = entry.split(":");
        if (parts.length < 2) return null;

        String name = parts[0].trim();
        String tagPath = parts[1].trim();
        // If only two parts, use minecraft as the tag namespace
        // If three parts: name:tagNamespace:tagPath
        String tagNamespace = parts.length >= 3 ? parts[2].trim() : "minecraft";

        if (name.isEmpty() || tagPath.isEmpty()) return null;

        try {
            ResourceLocation tagId = ResourceLocation.fromNamespaceAndPath(tagNamespace, tagPath);
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, tagId);
            return new StructureEntry(name, tag);
        } catch (Exception e) {
            return null;
        }
    }

    public static void invalidateConfigCache() {
        parsedStructures = null;
    }

    public List<StructureInfo> getNearbyStructures(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int chunkX = player.chunkPosition().x;
        int chunkZ = player.chunkPosition().z;

        PlayerStructureCache cached = CACHE.get(uuid);
        if (cached != null && !cached.isExpired() && cached.chunkX == chunkX && cached.chunkZ == chunkZ) {
            return cached.structures;
        }

        List<StructureInfo> structures = new ArrayList<>();
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        List<StructureEntry> structureEntries = getStructures();
        TheForbiddenWishingBook.LOGGER.info("Searching for {} structure types from {} chunks", structureEntries.size(), SEARCH_RADIUS);

        for (StructureEntry entry : structureEntries) {
            try {
                BlockPos found = level.findNearestMapStructure(entry.tag, playerPos, SEARCH_RADIUS, true);
                if (found != null) {
                    double distance = Math.sqrt(playerPos.distSqr(found));
                    structures.add(new StructureInfo(entry.name, found, Math.round(distance)));
                    TheForbiddenWishingBook.LOGGER.info("Found structure '{}' at {} (distance: {})", entry.name, found, Math.round(distance));
                } else {
                    TheForbiddenWishingBook.LOGGER.debug("Structure '{}' not found within {} chunks", entry.name, SEARCH_RADIUS);
                }
            } catch (Exception e) {
                TheForbiddenWishingBook.LOGGER.warn("Error searching for structure '{}': {}", entry.name, e.getMessage());
            }
        }

        structures.sort(Comparator.comparingDouble(StructureInfo::distance));
        TheForbiddenWishingBook.LOGGER.info("Found {} nearby structures", structures.size());

        CACHE.put(uuid, new PlayerStructureCache(structures, System.currentTimeMillis(), chunkX, chunkZ));

        return structures;
    }

    public void invalidateCache(UUID playerUUID) {
        CACHE.remove(playerUUID);
    }

    public void clearAll() {
        CACHE.clear();
    }
}

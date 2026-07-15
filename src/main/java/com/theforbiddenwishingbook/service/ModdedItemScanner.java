package com.theforbiddenwishingbook.service;

import com.theforbiddenwishingbook.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModdedItemScanner {
    private static final Map<String, List<String>> ITEM_CACHE = new ConcurrentHashMap<>();
    private static long lastScanTime = 0;
    private static final long SCAN_INTERVAL_MS = 300_000;

    public static Map<String, List<String>> getItemsByMod() {
        long now = System.currentTimeMillis();
        if (!ITEM_CACHE.isEmpty() && (now - lastScanTime) < SCAN_INTERVAL_MS) {
            return ITEM_CACHE;
        }
        return scanRegistry();
    }

    public static synchronized Map<String, List<String>> scanRegistry() {
        long now = System.currentTimeMillis();
        if (!ITEM_CACHE.isEmpty() && (now - lastScanTime) < SCAN_INTERVAL_MS) {
            return ITEM_CACHE;
        }

        ITEM_CACHE.clear();
        int maxPerCategory = ModConfig.MAX_REGISTRY_ITEMS_PER_CATEGORY.get();

        Map<String, List<String>> grouped = new TreeMap<>();

        BuiltInRegistries.ITEM.forEach(item -> {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) return;

            String namespace = key.getPath();
            String path = key.getPath();

            // Skip air and internal items
            if (path.equals("air") || path.startsWith("__")) return;

            String modId = key.getNamespace();
            String fullName = key.toString();

            grouped.computeIfAbsent(modId, k -> new ArrayList<>()).add(fullName);
        });

        // Apply per-category limit
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            List<String> items = entry.getValue();
            if (maxPerCategory > 0 && items.size() > maxPerCategory) {
                entry.setValue(items.subList(0, maxPerCategory));
            }
            Collections.sort(entry.getValue());
        }

        ITEM_CACHE.putAll(grouped);
        lastScanTime = now;

        return ITEM_CACHE;
    }

    public static String buildRegistryContext() {
        if (!ModConfig.ENABLE_REGISTRY_SCANNING.get()) {
            return "";
        }

        Map<String, List<String>> itemsByMod = getItemsByMod();
        if (itemsByMod.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("AVAILABLE ITEM REGISTRY (modded items included):\n");

        // Collect all unique item names for a compact view
        Set<String> allItems = new LinkedHashSet<>();
        Map<String, Integer> modCounts = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : itemsByMod.entrySet()) {
            String modId = entry.getKey();
            List<String> items = entry.getValue();
            modCounts.put(modId, items.size());

            for (String item : items) {
                allItems.add(item);
            }
        }

        // List mods and their item counts
        sb.append("Loaded mods with items:\n");
        for (Map.Entry<String, Integer> entry : modCounts.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" items\n");
        }

        // List all items grouped by mod (compact)
        sb.append("\nAll available items by mod:\n");
        for (Map.Entry<String, List<String>> entry : itemsByMod.entrySet()) {
            String modId = entry.getKey();
            List<String> items = entry.getValue();

            sb.append("\n[").append(modId).append("] (").append(items.size()).append(" items):\n");
            sb.append(String.join(", ", items));
            sb.append("\n");
        }

        sb.append("\nUse full item IDs (e.g., 'modname:item_name') in give_item actions.\n");

        return sb.toString();
    }

    public static int getTotalItemCount() {
        return getItemsByMod().values().stream().mapToInt(List::size).sum();
    }

    public static int getModCount() {
        return getItemsByMod().size();
    }
}

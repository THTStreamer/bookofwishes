package com.theforbiddenwishingbook.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WishActionRegistry extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, WishActionDefinition> REGISTERED_ACTIONS = new ConcurrentHashMap<>();

    public record WishActionDefinition(
            ResourceLocation id,
            String displayName,
            String description,
            List<String> requiredFields,
            String executorClass,
            int estimatedBaseValue,
            boolean adminOnly,
            String category
    ) {}

    public WishActionRegistry() {
        super(GSON, "wish_actions");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resourceLocations, ResourceManager resourceManager, ProfilerFiller profiler) {
        REGISTERED_ACTIONS.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resourceLocations.entrySet()) {
            try {
                JsonObject json = entry.getValue().getAsJsonObject();

                ResourceLocation id = entry.getKey();
                String displayName = json.has("display_name") ? json.get("display_name").getAsString() : id.getPath();
                String description = json.has("description") ? json.get("description").getAsString() : "";
                List<String> requiredFields = new ArrayList<>();
                if (json.has("required_fields")) {
                    for (JsonElement field : json.getAsJsonArray("required_fields")) {
                        requiredFields.add(field.getAsString());
                    }
                }
                String executorClass = json.has("executor") ? json.get("executor").getAsString() : "";
                int estimatedBaseValue = json.has("base_value") ? json.get("base_value").getAsInt() : 10;
                boolean adminOnly = json.has("admin_only") && json.get("admin_only").getAsBoolean();
                String category = json.has("category") ? json.get("category").getAsString() : "general";

                WishActionDefinition definition = new WishActionDefinition(
                        id, displayName, description, requiredFields,
                        executorClass, estimatedBaseValue, adminOnly, category
                );

                REGISTERED_ACTIONS.put(id, definition);
                TheForbiddenWishingBook.LOGGER.info("Registered wish action: {} ({})", id, displayName);
            } catch (Exception e) {
                TheForbiddenWishingBook.LOGGER.error("Failed to register wish action {}: {}", entry.getKey(), e.getMessage());
            }
        }

        TheForbiddenWishingBook.LOGGER.info("Loaded {} wish actions from datapacks", REGISTERED_ACTIONS.size());
    }

    public static WishActionDefinition getAction(ResourceLocation id) {
        return REGISTERED_ACTIONS.get(id);
    }

    public static Collection<WishActionDefinition> getAllActions() {
        return Collections.unmodifiableCollection(REGISTERED_ACTIONS.values());
    }

    public static List<WishActionDefinition> getActionsByCategory(String category) {
        return REGISTERED_ACTIONS.values().stream()
                .filter(a -> a.category().equals(category))
                .toList();
    }

    public static boolean isActionRegistered(ResourceLocation id) {
        return REGISTERED_ACTIONS.containsKey(id);
    }
}

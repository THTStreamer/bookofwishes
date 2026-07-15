package com.theforbiddenwishingbook.service;

import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class PaymentService {

    public record PaymentResult(boolean success, List<String> extracted, List<String> failed) {
        public static PaymentResult ok(List<String> extracted) {
            return new PaymentResult(true, extracted, List.of());
        }

        public static PaymentResult partial(List<String> extracted, List<String> failed) {
            return new PaymentResult(!failed.isEmpty(), extracted, failed);
        }
    }

    public PaymentResult extractPayment(ServerPlayer player, List<Map<String, Object>> payments) {
        List<String> extracted = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (Map<String, Object> payment : payments) {
            String type = (String) payment.get("type");
            if (type == null) {
                failed.add("Payment with no type");
                continue;
            }

            try {
                boolean success = switch (type) {
                    case "take_item" -> takeItem(player, payment);
                    case "take_xp" -> takeXP(player, payment);
                    case "take_from_nearby" -> takeFromNearby(player, payment);
                    case "take_named_item" -> takeNamedItem(player, payment);
                    default -> {
                        TheForbiddenWishingBook.LOGGER.warn("Unknown payment type: {}", type);
                        yield false;
                    }
                };

                String reason = payment.containsKey("reason") ? (String) payment.get("reason") : "Unknown";
                if (success) {
                    extracted.add(type + ": " + reason);
                } else {
                    failed.add(type + ": " + reason + " (insufficient)");
                }
            } catch (Exception e) {
                TheForbiddenWishingBook.LOGGER.error("Failed to extract payment {}: {}", type, e.getMessage());
                failed.add(type + ": execution error");
            }
        }

        return PaymentResult.partial(extracted, failed);
    }

    private boolean takeItem(ServerPlayer player, Map<String, Object> payment) {
        ResourceLocation itemId = ResourceLocation.parse((String) payment.get("item"));
        int count = payment.containsKey("count") ? ((Number) payment.get("count")).intValue() : 1;

        var item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null) return false;

        int remaining = count;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int toRemove = Math.min(stack.getCount(), remaining);
                stack.shrink(toRemove);
                remaining -= toRemove;
            }
        }

        return remaining == 0;
    }

    private boolean takeXP(ServerPlayer player, Map<String, Object> payment) {
        int levels = payment.containsKey("levels") ? ((Number) payment.get("levels")).intValue() : 1;

        if (player.experienceLevel < levels) return false;

        player.giveExperienceLevels(-levels);
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean takeFromNearby(ServerPlayer player, Map<String, Object> payment) {
        ResourceLocation itemId = ResourceLocation.parse((String) payment.get("item"));
        int count = payment.containsKey("count") ? ((Number) payment.get("count")).intValue() : 1;
        int radius = payment.containsKey("radius") ? ((Number) payment.get("radius")).intValue() : 16;

        var item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null) return false;

        int remaining = count;

        // Search nearby item entities using the entity predicate form
        List<ItemEntity> nearbyItems = player.level().getEntities(
                (Entity) null,
                player.getBoundingBox().inflate(radius),
                e -> e instanceof ItemEntity ie && ie.getItem().is(item)
        ).stream().filter(e -> e instanceof ItemEntity).map(e -> (ItemEntity) e).toList();

        for (ItemEntity itemEntity : nearbyItems) {
            if (remaining <= 0) break;
            ItemStack stack = itemEntity.getItem();
            int toRemove = Math.min(stack.getCount(), remaining);
            stack.shrink(toRemove);
            remaining -= toRemove;
            if (stack.isEmpty()) {
                itemEntity.discard();
            }
        }

        // Also check nearby player inventories (non-self players only)
        if (remaining > 0) {
            for (Player p : player.level().players()) {
                if (p.equals(player)) continue;
                if (player.distanceTo(p) > radius) continue;

                for (int i = 0; i < p.getInventory().getContainerSize() && remaining > 0; i++) {
                    ItemStack stack = p.getInventory().getItem(i);
                    if (stack.is(item)) {
                        int toRemove = Math.min(stack.getCount(), remaining);
                        stack.shrink(toRemove);
                        remaining -= toRemove;
                    }
                }
            }
        }

        return remaining == 0;
    }

    private boolean takeNamedItem(ServerPlayer player, Map<String, Object> payment) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && !stack.getHoverName().getString().isEmpty()) {
                player.getInventory().removeItem(i, 1);
                return true;
            }
        }
        return false;
    }
}

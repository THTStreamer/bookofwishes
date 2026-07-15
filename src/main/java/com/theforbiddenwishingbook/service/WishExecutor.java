package com.theforbiddenwishingbook.service;

import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import com.theforbiddenwishingbook.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

public class WishExecutor {

    public record ExecutionResult(boolean success, List<String> messages) {
        public static ExecutionResult ok(String... messages) {
            return new ExecutionResult(true, List.of(messages));
        }

        public static ExecutionResult fail(String reason) {
            return new ExecutionResult(false, List.of(reason));
        }
    }

    @SuppressWarnings("unchecked")
    public ExecutionResult execute(ServerPlayer player, Map<String, Object> action) {
        String type = (String) action.get("type");
        if (type == null) return ExecutionResult.fail("Action has no type");

        try {
            return switch (type) {
                case "give_item" -> executeGiveItem(player, action);
                case "give_block" -> executeGiveBlock(player, action);
                case "spawn_mob" -> executeSpawnMob(player, action);
                case "apply_effect" -> executeApplyEffect(player, action);
                case "remove_effect" -> executeRemoveEffect(player, action);
                case "set_weather" -> executeSetWeather(player, action);
                case "set_time" -> executeSetTime(player, action);
                case "teleport" -> executeTeleport(player, action);
                case "damage_player" -> executeDamagePlayer(player, action);
                case "kill_player" -> executeKillPlayer(player, action);
                case "steal_items" -> executeStealItems(player, action);
                case "destroy_area" -> executeDestroyArea(player, action);
                case "fill_area" -> executeFillArea(player, action);
                case "give_xp" -> executeGiveXP(player, action);
                case "set_health" -> executeSetHealth(player, action);
                case "set_food" -> executeSetFood(player, action);
                case "clear_inventory" -> executeClearInventory(player, action);
                case "spawn_boss" -> executeSpawnBoss(player, action);
                case "set_immortality" -> executeSetImmortality(player, action);
                case "teleport_to_structure" -> executeTeleportToStructure(player, action);
                case "find_nearest_structure" -> executeFindNearestStructure(player, action);
                case "locate_structure" -> executeLocateStructure(player, action);
                default -> {
                    TheForbiddenWishingBook.LOGGER.warn("Unknown wish action type: {}", type);
                    yield ExecutionResult.fail("Unknown action type: " + type);
                }
            };
        } catch (Exception e) {
            TheForbiddenWishingBook.LOGGER.error("Failed to execute wish action {}: {}", type, e.getMessage());
            return ExecutionResult.fail("Execution failed: " + e.getMessage());
        }
    }

    private ExecutionResult executeGiveItem(ServerPlayer player, Map<String, Object> action) {
        ResourceLocation itemId = ResourceLocation.parse((String) action.get("item"));
        int count = action.containsKey("count") ? ((Number) action.get("count")).intValue() : 1;

        var item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ExecutionResult.fail("Unknown item: " + itemId);

        ItemStack stack = new ItemStack(item, count);
        player.getInventory().add(stack);
        player.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 1.0F);

        return ExecutionResult.ok("Gave " + count + " " + itemId);
    }

    private ExecutionResult executeGiveBlock(ServerPlayer player, Map<String, Object> action) {
        ResourceLocation blockId = ResourceLocation.parse((String) action.get("block"));
        int count = action.containsKey("count") ? ((Number) action.get("count")).intValue() : 1;

        var block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == null || block == Blocks.AIR) return ExecutionResult.fail("Unknown block: " + blockId);

        ItemStack stack = new ItemStack(block, count);
        player.getInventory().add(stack);
        player.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 1.0F);

        return ExecutionResult.ok("Gave " + count + " " + blockId);
    }

    @SuppressWarnings("unchecked")
    private ExecutionResult executeSpawnMob(ServerPlayer player, Map<String, Object> action) {
        ResourceLocation mobId = ResourceLocation.parse((String) action.get("mob"));
        int count = action.containsKey("count") ? ((Number) action.get("count")).intValue() : 1;

        ServerLevel level = player.serverLevel();
        var entityType = BuiltInRegistries.ENTITY_TYPE.get(mobId);

        if (entityType == null || entityType == EntityType.PIG) return ExecutionResult.fail("Unknown entity type: " + mobId);

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Entity entity = entityType.create(level);
            if (entity != null) {
                double offsetX = (level.random.nextDouble() - 0.5) * 4;
                double offsetZ = (level.random.nextDouble() - 0.5) * 4;
                entity.moveTo(player.getX() + offsetX, player.getY(), player.getZ() + offsetZ);
                level.addFreshEntity(entity);
                spawned++;
            }
        }

        return ExecutionResult.ok("Spawned " + spawned + " " + mobId);
    }

    private ExecutionResult executeApplyEffect(ServerPlayer player, Map<String, Object> action) {
        ResourceLocation effectId = ResourceLocation.parse((String) action.get("effect"));
        int amplifier = action.containsKey("amplifier") ? ((Number) action.get("amplifier")).intValue() : 0;
        int duration = action.containsKey("duration") ? ((Number) action.get("duration")).intValue() : 60;

        var effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
        if (effect != null) {
            MobEffectInstance instance = new MobEffectInstance(net.minecraft.core.Holder.direct(effect), duration * 20, amplifier, false, true);
            player.addEffect(instance);
            player.playSound(SoundEvents.WITCH_DRINK, 1.0F, 1.0F);
            return ExecutionResult.ok("Applied " + effectId + " for " + duration + "s");
        }

        // Fallback for common effects
        return switch (effectId.getPath()) {
            case "regeneration" -> applyCommonEffect(player, MobEffects.REGENERATION, amplifier, duration);
            case "resistance" -> applyCommonEffect(player, MobEffects.DAMAGE_RESISTANCE, amplifier, duration);
            case "fire_resistance" -> applyCommonEffect(player, MobEffects.FIRE_RESISTANCE, amplifier, duration);
            case "absorption" -> applyCommonEffect(player, MobEffects.ABSORPTION, amplifier, duration);
            case "speed" -> applyCommonEffect(player, MobEffects.MOVEMENT_SPEED, amplifier, duration);
            case "strength" -> applyCommonEffect(player, MobEffects.DAMAGE_BOOST, amplifier, duration);
            case "invisibility" -> applyCommonEffect(player, MobEffects.INVISIBILITY, amplifier, duration);
            case "heal" -> {
                player.heal(amplifier * 2.0F + 2.0F);
                yield ExecutionResult.ok("Healed player");
            }
            case "haste" -> applyCommonEffect(player, MobEffects.DIG_SPEED, amplifier, duration);
            case "night_vision" -> applyCommonEffect(player, MobEffects.NIGHT_VISION, amplifier, duration);
            default -> ExecutionResult.fail("Unknown effect: " + effectId);
        };
    }

    private ExecutionResult applyCommonEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier, int duration) {
        MobEffectInstance instance = new MobEffectInstance(effect, duration * 20, amplifier, false, true);
        player.addEffect(instance);
        player.playSound(SoundEvents.WITCH_DRINK, 1.0F, 1.0F);
        return ExecutionResult.ok("Applied effect for " + duration + "s");
    }

    private ExecutionResult executeRemoveEffect(ServerPlayer player, Map<String, Object> action) {
        ResourceLocation effectId = ResourceLocation.parse((String) action.get("effect"));
        var effect = BuiltInRegistries.MOB_EFFECT.get(effectId);

        if (effect == null) return ExecutionResult.fail("Unknown effect: " + effectId);

        player.removeEffect(net.minecraft.core.Holder.direct(effect));
        return ExecutionResult.ok("Removed " + effectId);
    }

    private ExecutionResult executeSetWeather(ServerPlayer player, Map<String, Object> action) {
        String weather = (String) action.get("weather");
        int duration = action.containsKey("duration") ? ((Number) action.get("duration")).intValue() : 600;

        ServerLevel level = player.serverLevel();
        level.setWeatherParameters(0, duration * 20, weather.equals("rain"), weather.equals("thunder"));

        return ExecutionResult.ok("Set weather to " + weather);
    }

    private ExecutionResult executeSetTime(ServerPlayer player, Map<String, Object> action) {
        long time = action.containsKey("time") ? ((Number) action.get("time")).longValue() : 0;
        player.serverLevel().setDayTime(time);
        return ExecutionResult.ok("Set time to " + time);
    }

    private ExecutionResult executeTeleport(ServerPlayer player, Map<String, Object> action) {
        double x = ((Number) action.get("x")).doubleValue();
        double y = ((Number) action.get("y")).doubleValue();
        double z = ((Number) action.get("z")).doubleValue();

        String dimensionStr = action.containsKey("dimension") ? (String) action.get("dimension") : null;

        ServerLevel targetLevel = player.serverLevel();
        if (dimensionStr != null) {
            ResourceLocation dimId = ResourceLocation.parse(dimensionStr);
            var levelOpt = player.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimId));
            if (levelOpt != null) targetLevel = levelOpt;
        }

        player.teleportTo(targetLevel, x, y, z, player.getYRot(), player.getXRot());
        player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);

        return ExecutionResult.ok("Teleported to " + (int) x + ", " + (int) y + ", " + (int) z);
    }

    private ExecutionResult executeDamagePlayer(ServerPlayer player, Map<String, Object> action) {
        String targetName = (String) action.get("target");
        float amount = action.containsKey("amount") ? ((Number) action.get("amount")).floatValue() : 4.0F;

        Player target = findPlayer(player.serverLevel(), targetName);
        if (target == null) return ExecutionResult.fail("Player not found: " + targetName);

        target.hurt(player.damageSources().playerAttack(player), amount);
        return ExecutionResult.ok("Damaged " + targetName + " for " + amount + " HP");
    }

    private ExecutionResult executeKillPlayer(ServerPlayer player, Map<String, Object> action) {
        String targetName = (String) action.get("target");
        Player target = findPlayer(player.serverLevel(), targetName);
        if (target == null) return ExecutionResult.fail("Player not found: " + targetName);

        target.hurt(player.damageSources().playerAttack(player), 1000.0F);
        return ExecutionResult.ok("Killed " + targetName);
    }

    @SuppressWarnings("unchecked")
    private ExecutionResult executeStealItems(ServerPlayer player, Map<String, Object> action) {
        String targetName = (String) action.get("target");
        List<String> itemNames = (List<String>) action.get("items");

        Player target = findPlayer(player.serverLevel(), targetName);
        if (target == null) return ExecutionResult.fail("Player not found: " + targetName);

        int stolen = 0;
        for (String itemName : itemNames) {
            ResourceLocation itemId = ResourceLocation.parse(itemName);
            var item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null) continue;

            for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
                ItemStack stack = target.getInventory().getItem(i);
                if (stack.is(item)) {
                    player.getInventory().add(stack.copy());
                    target.getInventory().removeItem(i, stack.getCount());
                    stolen++;
                    break;
                }
            }
        }

        return ExecutionResult.ok("Stole " + stolen + " item types from " + targetName);
    }

    private ExecutionResult executeDestroyArea(ServerPlayer player, Map<String, Object> action) {
        int x1 = ((Number) action.get("x1")).intValue();
        int y1 = ((Number) action.get("y1")).intValue();
        int z1 = ((Number) action.get("z1")).intValue();
        int x2 = ((Number) action.get("x2")).intValue();
        int y2 = ((Number) action.get("y2")).intValue();
        int z2 = ((Number) action.get("z2")).intValue();
        String replaceStr = action.containsKey("replace") ? (String) action.get("replace") : "minecraft:air";

        // Enforce destruction radius limit
        int radius = Math.max(
                Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)),
                Math.abs(z2 - z1)
        ) / 2;
        int maxRadius = ModConfig.MAX_DESTRUCTION_RADIUS.get();
        if (radius > maxRadius) {
            return ExecutionResult.fail("Destruction radius too large: " + radius + " blocks (max: " + maxRadius + ")");
        }

        ResourceLocation replaceId = ResourceLocation.parse(replaceStr);
        var replaceBlock = BuiltInRegistries.BLOCK.get(replaceId);
        BlockState replaceState = replaceBlock != null ? replaceBlock.defaultBlockState() : Blocks.AIR.defaultBlockState();

        ServerLevel level = player.serverLevel();
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        int blocksChanged = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isLoaded(pos)) {
                        level.setBlockAndUpdate(pos, replaceState);
                        blocksChanged++;
                    }
                }
            }
        }

        level.playSound(null, new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0F, 0.5F);

        return ExecutionResult.ok("Destroyed " + blocksChanged + " blocks");
    }

    private ExecutionResult executeFillArea(ServerPlayer player, Map<String, Object> action) {
        int x1 = ((Number) action.get("x1")).intValue();
        int y1 = ((Number) action.get("y1")).intValue();
        int z1 = ((Number) action.get("z1")).intValue();
        int x2 = ((Number) action.get("x2")).intValue();
        int y2 = ((Number) action.get("y2")).intValue();
        int z2 = ((Number) action.get("z2")).intValue();
        String blockStr = (String) action.get("block");

        // Enforce destruction radius limit
        int radius = Math.max(
                Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)),
                Math.abs(z2 - z1)
        ) / 2;
        int maxRadius = ModConfig.MAX_DESTRUCTION_RADIUS.get();
        if (radius > maxRadius) {
            return ExecutionResult.fail("Fill radius too large: " + radius + " blocks (max: " + maxRadius + ")");
        }

        ResourceLocation blockId = ResourceLocation.parse(blockStr);
        var fillBlock = BuiltInRegistries.BLOCK.get(blockId);
        BlockState fillState = fillBlock != null ? fillBlock.defaultBlockState() : Blocks.STONE.defaultBlockState();

        ServerLevel level = player.serverLevel();
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        int blocksChanged = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isLoaded(pos)) {
                        level.setBlockAndUpdate(pos, fillState);
                        blocksChanged++;
                    }
                }
            }
        }

        return ExecutionResult.ok("Filled " + blocksChanged + " blocks with " + blockStr);
    }

    private ExecutionResult executeGiveXP(ServerPlayer player, Map<String, Object> action) {
        int levels = action.containsKey("levels") ? ((Number) action.get("levels")).intValue() : 1;
        player.giveExperienceLevels(levels);
        player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
        return ExecutionResult.ok("Gave " + levels + " XP levels");
    }

    private ExecutionResult executeSetHealth(ServerPlayer player, Map<String, Object> action) {
        float amount = action.containsKey("amount") ? ((Number) action.get("amount")).floatValue() : 20.0F;
        player.setHealth(Math.min(amount, player.getMaxHealth()));
        return ExecutionResult.ok("Set health to " + amount);
    }

    private ExecutionResult executeSetFood(ServerPlayer player, Map<String, Object> action) {
        int food = action.containsKey("food") ? ((Number) action.get("food")).intValue() : 20;
        float saturation = action.containsKey("saturation") ? ((Number) action.get("saturation")).floatValue() : 5.0F;
        player.getFoodData().setFoodLevel(food);
        player.getFoodData().setSaturation(saturation);
        return ExecutionResult.ok("Set food to " + food + " with " + saturation + " saturation");
    }

    private ExecutionResult executeClearInventory(ServerPlayer player, Map<String, Object> action) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        player.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 0.5F);
        return ExecutionResult.ok("Cleared inventory");
    }

    private ExecutionResult executeSpawnBoss(ServerPlayer player, Map<String, Object> action) {
        String bossStr = (String) action.get("boss");
        ServerLevel level = player.serverLevel();

        if (bossStr.contains("wither")) {
            WitherBoss boss = new WitherBoss(EntityType.WITHER, level);
            boss.moveTo(player.getX() + 5, player.getY() + 3, player.getZ());
            level.addFreshEntity(boss);
            level.playSound(null, boss.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.0F, 1.0F);
        } else if (bossStr.contains("ender_dragon")) {
            return ExecutionResult.fail("Ender Dragon spawning requires an End portal");
        } else {
            return ExecutionResult.fail("Unknown boss: " + bossStr);
        }

        return ExecutionResult.ok("Spawned " + bossStr);
    }

    private ExecutionResult executeSetImmortality(ServerPlayer player, Map<String, Object> action) {
        boolean enabled = action.containsKey("enabled") ? (Boolean) action.get("enabled") : true;
        int duration = action.containsKey("duration") ? ((Number) action.get("duration")).intValue() : 600;

        // Enforce immortality duration cap
        int maxDuration = ModConfig.MAX_IMMORTALITY_DURATION.get();
        if (duration > maxDuration) {
            duration = maxDuration;
        }

        if (enabled) {
            int ticks = duration * 20;
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 255, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 255, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ticks, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, 255, false, true));
            player.playSound(SoundEvents.TOTEM_USE, 1.0F, 1.0F);
            return ExecutionResult.ok("Immortality granted for " + duration + " seconds");
        } else {
            player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            player.removeEffect(MobEffects.REGENERATION);
            player.removeEffect(MobEffects.FIRE_RESISTANCE);
            player.removeEffect(MobEffects.ABSORPTION);
            return ExecutionResult.ok("Immortality removed");
        }
    }

    private ExecutionResult executeTeleportToStructure(ServerPlayer player, Map<String, Object> action) {
        String structureType = (String) action.get("structure");
        if (structureType == null) return ExecutionResult.fail("No structure type specified");

        StructureTracker tracker = new StructureTracker();
        List<StructureTracker.StructureInfo> structures = tracker.getNearbyStructures(player);

        for (StructureTracker.StructureInfo info : structures) {
            if (info.name().equals(structureType)) {
                BlockPos pos = info.pos();
                ServerLevel level = player.serverLevel();

                // Find the surface level at the structure's X/Z instead of using the raw Y
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());

                // Check if the surface block is safe to stand on (not inside a solid block)
                BlockPos surfacePos = new BlockPos(pos.getX(), surfaceY, pos.getZ());
                if (level.getBlockState(surfacePos).isSolidRender(level, surfacePos)) {
                    // Go one higher if we're inside a block
                    surfaceY++;
                }

                player.teleportTo(level, pos.getX() + 0.5, surfaceY, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
                player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
                return ExecutionResult.ok("Teleported to " + structureType + " at " + pos.getX() + ", " + surfaceY + ", " + pos.getZ());
            }
        }

        return ExecutionResult.fail("Structure not found: " + structureType);
    }

    private ExecutionResult executeFindNearestStructure(ServerPlayer player, Map<String, Object> action) {
        String structureType = (String) action.get("structure");
        if (structureType == null) return ExecutionResult.fail("No structure type specified");

        StructureTracker tracker = new StructureTracker();
        List<StructureTracker.StructureInfo> structures = tracker.getNearbyStructures(player);

        for (StructureTracker.StructureInfo info : structures) {
            if (info.name().equals(structureType)) {
                return ExecutionResult.ok("Nearest " + structureType + " found at " + info.pos().getX() + ", " + info.pos().getY() + ", " + info.pos().getZ() + " (" + info.distance() + " blocks away)");
            }
        }

        return ExecutionResult.fail("No " + structureType + " found within search range");
    }

    private ExecutionResult executeLocateStructure(ServerPlayer player, Map<String, Object> action) {
        String structureType = (String) action.get("structure");
        if (structureType == null) return ExecutionResult.fail("No structure type specified");

        StructureTracker tracker = new StructureTracker();
        List<StructureTracker.StructureInfo> structures = tracker.getNearbyStructures(player);

        StringBuilder result = new StringBuilder("Found structures:\n");
        boolean found = false;
        for (StructureTracker.StructureInfo info : structures) {
            if (structureType.equals("any") || info.name().equals(structureType)) {
                result.append("- ").append(info.name()).append(": ").append(info.pos().getX()).append(", ").append(info.pos().getY()).append(", ").append(info.pos().getZ()).append(" (").append(info.distance()).append(" blocks)\n");
                found = true;
            }
        }

        if (!found) return ExecutionResult.fail("No structures found");
        return ExecutionResult.ok(result.toString());
    }

    private Player findPlayer(ServerLevel level, String name) {
        for (Player p : level.players()) {
            if (p.getName().getString().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
}

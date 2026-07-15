package com.theforbiddenwishingbook.recipe;

import com.mojang.serialization.MapCodec;
import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import com.theforbiddenwishingbook.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ConfigCondition implements ICondition {
    public static final MapCodec<ConfigCondition> CODEC = MapCodec.unit(ConfigCondition::new);

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, TheForbiddenWishingBook.MOD_ID);

    public static final Supplier<MapCodec<ConfigCondition>> REGISTRY =
            CONDITION_CODECS.register("config", () -> CODEC);

    private ConfigCondition() {}

    @Override
    public boolean test(IContext context) {
        return ModConfig.ENABLE_CRAFTING_RECIPE.get();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    public static void register(IEventBus bus) {
        CONDITION_CODECS.register(bus);
    }
}

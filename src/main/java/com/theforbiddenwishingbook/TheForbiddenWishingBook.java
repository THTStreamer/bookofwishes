package com.theforbiddenwishingbook;

import com.theforbiddenwishingbook.config.ModConfig;
import com.theforbiddenwishingbook.network.ModNetwork;
import com.theforbiddenwishingbook.registry.ModDataComponents;
import com.theforbiddenwishingbook.registry.ModItems;
import com.theforbiddenwishingbook.reputation.ReputationService;
import com.theforbiddenwishingbook.service.EmbeddedLLMService;
import com.theforbiddenwishingbook.service.WishActionRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(TheForbiddenWishingBook.MOD_ID)
public class TheForbiddenWishingBook {
    public static final String MOD_ID = "thebookofwishes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TheForbiddenWishingBook(IEventBus modEventBus, ModContainer modContainer) {
        ModConfig.register(modContainer);

        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);

        ModNetwork.register(modEventBus);

        // Register reload listener for datapack wish actions
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);

        // Register player login/logout events for reputation persistence
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);

        // Register server stopping event to clean up embedded model
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        LOGGER.info("The Book of Wishes has been bound to this world.");
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new WishActionRegistry());
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ReputationService.loadReputation(player.getUUID());
            LOGGER.debug("Loaded reputation for {}", player.getName().getString());
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOGGER.debug("Player logged out: {}", player.getName().getString());
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        EmbeddedLLMService.shutdown();
        LOGGER.info("Embedded AI model cleaned up on server stop.");
    }
}

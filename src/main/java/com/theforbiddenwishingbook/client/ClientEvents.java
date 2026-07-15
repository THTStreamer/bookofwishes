package com.theforbiddenwishingbook.client;

import com.theforbiddenwishingbook.client.gui.BookOfWishesEditScreen;
import com.theforbiddenwishingbook.network.OpenBookScreenPayload;
import com.theforbiddenwishingbook.network.WishResponsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientEvents {
    private static final Map<Integer, List<WishResponsePayload.WishResult>> pendingResponses = new ConcurrentHashMap<>();

    public static void handleOpenBookScreen(OpenBookScreenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Player player = mc.player;
        ItemStack bookStack = player.getInventory().getItem(payload.inventorySlot());

        if (!bookStack.is(com.theforbiddenwishingbook.registry.ModItems.BOOK_OF_WISHES.get())) return;

        InteractionHand hand = payload.handOrdinal() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

        BookOfWishesEditScreen screen = new BookOfWishesEditScreen(player, bookStack, hand, payload.inventorySlot());
        screen.setBookContext(
                payload.reputationTitle(),
                payload.trustLevel(),
                payload.totalWishes(),
                payload.wishesGranted(),
                payload.wishesDenied(),
                payload.difficultyLabel(),
                payload.personalityName()
        );
        mc.setScreen(screen);
    }

    public static void handleWishResponse(WishResponsePayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        pendingResponses.put(payload.inventorySlot(), payload.results());

        if (mc.screen instanceof BookOfWishesEditScreen bookScreen) {
            bookScreen.onWishResponseReceived(payload.results());
        }
    }

    public static List<WishResponsePayload.WishResult> consumePendingResponse(int inventorySlot) {
        return pendingResponses.remove(inventorySlot);
    }
}

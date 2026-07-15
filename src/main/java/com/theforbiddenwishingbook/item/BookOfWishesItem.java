package com.theforbiddenwishingbook.item;

import com.theforbiddenwishingbook.data.WishBookData;
import com.theforbiddenwishingbook.personality.AIPersonality;
import com.theforbiddenwishingbook.personality.PersonalityManager;
import com.theforbiddenwishingbook.registry.ModDataComponents;
import com.theforbiddenwishingbook.reputation.ReputationService;
import com.theforbiddenwishingbook.reputation.WishMemoryService;
import com.theforbiddenwishingbook.service.WishDifficultyService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class BookOfWishesItem extends Item {
    public BookOfWishesItem(Properties properties) {
        super(properties.component(ModDataComponents.WISH_BOOK_DATA.get(), WishBookData.EMPTY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            int slot = -1;
            for (int i = 0; i < serverPlayer.getInventory().getContainerSize(); i++) {
                if (serverPlayer.getInventory().getItem(i) == itemStack) {
                    slot = i;
                    break;
                }
            }

            if (slot >= 0) {
                // Gather book context data for the client
                ReputationService.ReputationData rep = ReputationService.getReputation(serverPlayer);
                WishMemoryService.WishMemory memory = WishMemoryService.getMemory(serverPlayer);
                WishDifficultyService.DifficultyModifiers difficulty = WishDifficultyService.calculateDifficulty(serverPlayer);
                AIPersonality personality = PersonalityManager.getPersonality(serverPlayer);

                int totalWishes = memory.pastWishes().size();

                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.theforbiddenwishingbook.network.OpenBookScreenPayload(
                                slot,
                                usedHand.ordinal(),
                                rep.title(),
                                rep.trustLevel(),
                                totalWishes,
                                rep.wishesGranted(),
                                rep.wishesDenied(),
                                difficulty.difficultyLabel(),
                                personality.getDisplayName()
                        )
                );
            }
        }

        player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        WishBookData data = stack.getOrDefault(ModDataComponents.WISH_BOOK_DATA.get(), WishBookData.EMPTY);

        tooltipComponents.add(Component.literal(" ").withStyle(ChatFormatting.DARK_PURPLE));
        tooltipComponents.add(Component.translatable("item.thebookofwishes.book_of_wishes.tooltip.lore")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        if (!data.title().isEmpty()) {
            tooltipComponents.add(Component.literal("Title: " + data.title())
                    .withStyle(ChatFormatting.GOLD));
        }

        long wishCount = data.wishes().stream().filter(w -> w.status() == WishBookData.WishStatus.GRANTED).count();
        if (wishCount > 0) {
            tooltipComponents.add(Component.literal("Wishes Granted: " + wishCount)
                    .withStyle(ChatFormatting.AQUA));
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

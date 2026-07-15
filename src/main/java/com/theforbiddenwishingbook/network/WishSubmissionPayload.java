package com.theforbiddenwishingbook.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record WishSubmissionPayload(int inventorySlot, List<String> pages) implements CustomPacketPayload {
    public static final Type<WishSubmissionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("thebookofwishes", "submit_wishes"));

    public static final StreamCodec<FriendlyByteBuf, WishSubmissionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WishSubmissionPayload::inventorySlot,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), WishSubmissionPayload::pages,
            WishSubmissionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

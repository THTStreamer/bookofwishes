package com.theforbiddenwishingbook.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenBookScreenPayload(
        int inventorySlot,
        int handOrdinal,
        String reputationTitle,
        int trustLevel,
        int totalWishes,
        int wishesGranted,
        int wishesDenied,
        String difficultyLabel,
        String personalityName
) implements CustomPacketPayload {
    public static final Type<OpenBookScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("thebookofwishes", "open_book_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenBookScreenPayload> STREAM_CODEC = StreamCodec.of(
            OpenBookScreenPayload::encode,
            OpenBookScreenPayload::decode
    );

    private static void encode(FriendlyByteBuf buf, OpenBookScreenPayload payload) {
        buf.writeVarInt(payload.inventorySlot);
        buf.writeVarInt(payload.handOrdinal);
        buf.writeUtf(payload.reputationTitle);
        buf.writeVarInt(payload.trustLevel);
        buf.writeVarInt(payload.totalWishes);
        buf.writeVarInt(payload.wishesGranted);
        buf.writeVarInt(payload.wishesDenied);
        buf.writeUtf(payload.difficultyLabel);
        buf.writeUtf(payload.personalityName);
    }

    private static OpenBookScreenPayload decode(FriendlyByteBuf buf) {
        return new OpenBookScreenPayload(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

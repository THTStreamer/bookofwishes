package com.theforbiddenwishingbook.network;

import com.theforbiddenwishingbook.data.WishBookData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record WishResponsePayload(
        int inventorySlot,
        List<WishResult> results
) implements CustomPacketPayload {
    public static final Type<WishResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("thebookofwishes", "wish_response"));

    public static final StreamCodec<FriendlyByteBuf, WishResponsePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WishResponsePayload::inventorySlot,
            WishResult.STREAM_CODEC.apply(ByteBufCodecs.list()), WishResponsePayload::results,
            WishResponsePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record WishResult(
            int pageNumber,
            WishBookData.WishStatus status,
            String aiResponseText,
            List<String> paymentTaken
    ) {
        public static final StreamCodec<FriendlyByteBuf, WishResult> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WishResult::pageNumber,
                StreamCodec.of((buf, status) -> buf.writeEnum(status), buf -> buf.<WishBookData.WishStatus>readEnum(WishBookData.WishStatus.class)), WishResult::status,
                ByteBufCodecs.STRING_UTF8, WishResult::aiResponseText,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), WishResult::paymentTaken,
                WishResult::new
        );
    }
}

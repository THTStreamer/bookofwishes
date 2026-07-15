package com.theforbiddenwishingbook.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public record WishBookData(
        List<WishEntry> wishes,
        boolean signed,
        String title
) {
    public static final WishBookData EMPTY = new WishBookData(List.of(), false, "");

    public static final Codec<WishBookData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    WishEntry.CODEC.listOf().optionalFieldOf("wishes", List.of()).forGetter(WishBookData::wishes),
                    Codec.BOOL.optionalFieldOf("signed", false).forGetter(WishBookData::signed),
                    Codec.STRING.optionalFieldOf("title", "").forGetter(WishBookData::title)
            ).apply(instance, WishBookData::new)
    );

    public static final StreamCodec<FriendlyByteBuf, WishBookData> STREAM_CODEC = StreamCodec.composite(
            WishEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), WishBookData::wishes,
            ByteBufCodecs.BOOL, WishBookData::signed,
            ByteBufCodecs.STRING_UTF8, WishBookData::title,
            WishBookData::new
    );

    public WishBookData withWish(WishEntry entry) {
        List<WishEntry> newWishes = new ArrayList<>(wishes);
        newWishes.add(entry);
        return new WishBookData(newWishes, signed, title);
    }

    public WishBookData withUpdatedWish(int index, WishEntry entry) {
        List<WishEntry> newWishes = new ArrayList<>(wishes);
        if (index >= 0 && index < newWishes.size()) {
            newWishes.set(index, entry);
        }
        return new WishBookData(newWishes, signed, title);
    }

    public WishBookData withSigned(String title) {
        return new WishBookData(wishes, true, title);
    }

    public boolean isProcessed(int pageNumber) {
        return wishes.stream().anyMatch(w -> w.pageNumber() == pageNumber && w.status() != WishStatus.PENDING);
    }

    public record WishEntry(
            int pageNumber,
            String wishText,
            WishStatus status,
            String aiResponse,
            List<String> paymentTaken
    ) {
        public static final Codec<WishEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.fieldOf("page_number").forGetter(WishEntry::pageNumber),
                        Codec.STRING.fieldOf("wish_text").forGetter(WishEntry::wishText),
                        WishStatus.CODEC.optionalFieldOf("status", WishStatus.PENDING).forGetter(WishEntry::status),
                        Codec.STRING.optionalFieldOf("ai_response", "").forGetter(WishEntry::aiResponse),
                        Codec.STRING.listOf().optionalFieldOf("payment_taken", List.of()).forGetter(WishEntry::paymentTaken)
                ).apply(instance, WishEntry::new)
        );

        public static final StreamCodec<FriendlyByteBuf, WishEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WishEntry::pageNumber,
                ByteBufCodecs.STRING_UTF8, WishEntry::wishText,
                StreamCodec.of((buf, status) -> buf.writeEnum(status), buf -> buf.<WishStatus>readEnum(WishStatus.class)), WishEntry::status,
                ByteBufCodecs.STRING_UTF8, WishEntry::aiResponse,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), WishEntry::paymentTaken,
                WishEntry::new
        );
    }

    public enum WishStatus {
        PENDING, GRANTED, DENIED, FAILED;

        public static final Codec<WishStatus> CODEC = Codec.STRING.xmap(
                WishStatus::fromString,
                WishStatus::toString
        );

        private static WishStatus fromString(String s) {
            try {
                return valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return PENDING;
            }
        }

        private String toString(String unused) {
            return name().toLowerCase();
        }
    }
}

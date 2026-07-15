package com.theforbiddenwishingbook.registry;

import com.theforbiddenwishingbook.TheForbiddenWishingBook;
import com.theforbiddenwishingbook.data.WishBookData;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TheForbiddenWishingBook.MOD_ID);

    public static final Supplier<DataComponentType<WishBookData>> WISH_BOOK_DATA =
            DATA_COMPONENTS.registerComponentType("wish_book_data", builder ->
                    builder
                            .persistent(WishBookData.CODEC)
                            .networkSynchronized(WishBookData.STREAM_CODEC)
            );
}

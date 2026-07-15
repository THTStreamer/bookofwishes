package com.theforbiddenwishingbook.registry;

import com.theforbiddenwishingbook.data.WishBookData;
import com.theforbiddenwishingbook.item.BookOfWishesItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.theforbiddenwishingbook.TheForbiddenWishingBook;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(TheForbiddenWishingBook.MOD_ID);

    public static final DeferredItem<Item> BOOK_OF_WISHES = ITEMS.registerItem(
            "book_of_wishes",
            BookOfWishesItem::new,
            new Item.Properties()
                    .stacksTo(1)
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON)
    );
}

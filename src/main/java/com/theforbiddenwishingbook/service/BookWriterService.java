package com.theforbiddenwishingbook.service;

import com.theforbiddenwishingbook.data.WishBookData;
import com.theforbiddenwishingbook.network.WishResponsePayload;
import com.theforbiddenwishingbook.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BookWriterService {

    public record WriteResult(boolean success, String message) {
        public static WriteResult ok(String message) {
            return new WriteResult(true, message);
        }

        public static WriteResult fail(String message) {
            return new WriteResult(false, message);
        }
    }

    public WriteResult writeResponseToBook(
            ItemStack bookStack,
            int pageNumber,
            String wishText,
            WishBookData.WishStatus status,
            String aiResponseText,
            List<String> paymentTaken
    ) {
        WishBookData currentData = bookStack.getOrDefault(
                ModDataComponents.WISH_BOOK_DATA.get(),
                WishBookData.EMPTY
        );

        // Find existing entry for this page or create new
        WishBookData.WishEntry existingEntry = null;
        int entryIndex = -1;
        for (int i = 0; i < currentData.wishes().size(); i++) {
            WishBookData.WishEntry entry = currentData.wishes().get(i);
            if (entry.pageNumber() == pageNumber) {
                existingEntry = entry;
                entryIndex = i;
                break;
            }
        }

        WishBookData.WishEntry newEntry = new WishBookData.WishEntry(
                pageNumber,
                !wishText.isEmpty() ? wishText : (existingEntry != null ? existingEntry.wishText() : ""),
                status,
                aiResponseText,
                paymentTaken
        );

        WishBookData newData;
        if (entryIndex >= 0) {
            newData = currentData.withUpdatedWish(entryIndex, newEntry);
        } else {
            newData = currentData.withWish(newEntry);
        }

        bookStack.set(ModDataComponents.WISH_BOOK_DATA.get(), newData);
        return WriteResult.ok("Written response for page " + pageNumber);
    }

    public WriteResult writeMultipleResponses(
            ItemStack bookStack,
            List<WishResponsePayload.WishResult> results
    ) {
        for (WishResponsePayload.WishResult result : results) {
            WriteResult writeResult = writeResponseToBook(
                    bookStack,
                    result.pageNumber(),
                    "",
                    result.status(),
                    result.aiResponseText(),
                    result.paymentTaken()
            );

            if (!writeResult.success()) {
                return writeResult;
            }
        }

        return WriteResult.ok("Written " + results.size() + " responses");
    }

    public List<String> getPageContent(ItemStack bookStack, int pageNumber) {
        WishBookData data = bookStack.getOrDefault(
                ModDataComponents.WISH_BOOK_DATA.get(),
                WishBookData.EMPTY
        );

        for (WishBookData.WishEntry entry : data.wishes()) {
            if (entry.pageNumber() == pageNumber) {
                List<String> content = new ArrayList<>();
                content.add(entry.wishText());
                content.add("---");
                content.add("Status: " + entry.status().name());
                if (!entry.aiResponse().isEmpty()) {
                    content.add("");
                    content.add(entry.aiResponse());
                }
                if (!entry.paymentTaken().isEmpty()) {
                    content.add("");
                    content.add("Payment Taken:");
                    for (String payment : entry.paymentTaken()) {
                        content.add("- " + payment);
                    }
                }
                return content;
            }
        }

        return List.of();
    }
}

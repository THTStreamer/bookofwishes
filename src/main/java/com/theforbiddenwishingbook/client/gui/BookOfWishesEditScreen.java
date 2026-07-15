package com.theforbiddenwishingbook.client.gui;

import com.theforbiddenwishingbook.data.WishBookData;
import com.theforbiddenwishingbook.network.WishResponsePayload;
import com.theforbiddenwishingbook.network.WishSubmissionPayload;
import com.theforbiddenwishingbook.registry.ModDataComponents;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BookOfWishesEditScreen extends Screen {
    private static final ResourceLocation BOOK_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/book.png");

    private final Player owner;
    private final ItemStack bookStack;
    private final InteractionHand hand;
    private final int inventorySlot;

    private final List<PageData> pages = new ArrayList<>();
    private int currentPage = 0;
    private boolean processing = false;
    private String processingMessage = "";
    private List<WishResponsePayload.WishResult> lastResults = null;

    private Button grantButton;
    private Button prevButton;
    private Button nextPageButton;
    private Button addPageButton;
    private Button removePageButton;

    private String currentPageText = "";
    private int cursorPos = 0;
    private boolean focused = true;
    private int tickCount = 0;

    // Book context data from server
    private String reputationTitle = "Unknown Mortal";
    private int trustLevel = 0;
    private int totalWishes = 0;
    private int wishesGranted = 0;
    private int wishesDenied = 0;
    private String difficultyLabel = "Fair";
    private String personalityName = "Ancient";

    // Scroll offset for response display
    private int responseScrollOffset = 0;

    // Confirmation dialog state
    private boolean showConfirmation = false;
    private int pendingWishCount = 0;

    private static final int PAGE_WIDTH = 192;
    private static final int PAGE_HEIGHT = 192;
    private static final int TEXT_AREA_LEFT = 36;
    private static final int TEXT_AREA_TOP = 42;
    private static final int TEXT_AREA_WIDTH = 120;
    private static final int TEXT_AREA_HEIGHT = 128;
    private static final int LINE_HEIGHT = 10;
    private static final int CHARS_PER_LINE = 18;

    private record PageData(
            String wishText,
            WishBookData.WishStatus status,
            String aiResponse,
            List<String> paymentTaken
    ) {
        static PageData empty() {
            return new PageData("", WishBookData.WishStatus.PENDING, "", List.of());
        }

        PageData withText(String text) {
            return new PageData(text, status, aiResponse, paymentTaken);
        }

        boolean isProcessed() {
            return status != WishBookData.WishStatus.PENDING;
        }
    }

    public BookOfWishesEditScreen(Player owner, ItemStack bookStack, InteractionHand hand, int inventorySlot) {
        super(GameNarrator.NO_TITLE);
        this.owner = owner;
        this.bookStack = bookStack;
        this.hand = hand;
        this.inventorySlot = inventorySlot;

        reloadFromBook();
        loadPageContent();
    }

    public void setBookContext(String reputationTitle, int trustLevel, int totalWishes,
                               int wishesGranted, int wishesDenied, String difficultyLabel, String personalityName) {
        this.reputationTitle = reputationTitle;
        this.trustLevel = trustLevel;
        this.totalWishes = totalWishes;
        this.wishesGranted = wishesGranted;
        this.wishesDenied = wishesDenied;
        this.difficultyLabel = difficultyLabel;
        this.personalityName = personalityName;
    }

    private void reloadFromBook() {
        pages.clear();
        ItemStack currentStack = owner.getInventory().getItem(inventorySlot);
        WishBookData data = currentStack.getOrDefault(ModDataComponents.WISH_BOOK_DATA.get(), WishBookData.EMPTY);

        int maxPage = -1;
        for (WishBookData.WishEntry entry : data.wishes()) {
            maxPage = Math.max(maxPage, entry.pageNumber());
        }

        for (int i = 0; i <= maxPage; i++) {
            WishBookData.WishEntry entry = null;
            for (WishBookData.WishEntry e : data.wishes()) {
                if (e.pageNumber() == i) {
                    entry = e;
                    break;
                }
            }
            if (entry != null) {
                pages.add(new PageData(
                        entry.wishText(),
                        entry.status(),
                        entry.aiResponse(),
                        entry.paymentTaken()
                ));
            } else {
                pages.add(PageData.empty());
            }
        }

        if (pages.isEmpty()) {
            pages.add(PageData.empty());
        }
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int bottomY = this.height / 2 + PAGE_HEIGHT / 2 + 16;

        grantButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.thebookofwishes.grant_wishes"),
                button -> onGrantWishesClicked()
        ).bounds(centerX - 100, bottomY, 98, 20).build());

        addPageButton = addRenderableWidget(Button.builder(
                Component.literal("+"),
                button -> addPage()
        ).bounds(centerX + 100, bottomY, 20, 20).build());

        removePageButton = addRenderableWidget(Button.builder(
                Component.literal("-"),
                button -> removePage()
        ).bounds(centerX + 124, bottomY, 20, 20).build());

        prevButton = addRenderableWidget(Button.builder(
                Component.literal("<"),
                button -> turnPage(-1)
        ).bounds(centerX - PAGE_WIDTH / 2 - 20, this.height / 2, 16, 16).build());

        nextPageButton = addRenderableWidget(Button.builder(
                Component.literal(">"),
                button -> turnPage(1)
        ).bounds(centerX + PAGE_WIDTH / 2 + 4, this.height / 2, 16, 16).build());

        updateButtons();
    }

    private void updateButtons() {
        PageData page = pages.get(currentPage);
        if (grantButton != null) {
            grantButton.active = !processing && !page.isProcessed() && !currentPageText.isBlank();
        }
        if (prevButton != null) {
            prevButton.active = currentPage > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.active = currentPage < pages.size() - 1;
        }
        if (removePageButton != null) {
            removePageButton.active = pages.size() > 1 && !page.isProcessed();
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        tickCount++;

        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        guiGraphics.blit(BOOK_TEXTURE,
                centerX - PAGE_WIDTH / 2, centerY - PAGE_HEIGHT / 2,
                0, 0, PAGE_WIDTH, PAGE_HEIGHT);

        // Title
        String title = "The Book of Wishes";
        guiGraphics.drawString(this.font, title,
                centerX - this.font.width(title) / 2,
                centerY - PAGE_HEIGHT / 2 + 8,
                0x404040, false);

        // Book context header - reputation and difficulty
        String headerLine = reputationTitle + " | " + difficultyLabel;
        guiGraphics.drawString(this.font, headerLine,
                centerX - this.font.width(headerLine) / 2,
                centerY - PAGE_HEIGHT / 2 + 20,
                0xFF666666, false);

        // Wish count stats
        String statsLine = totalWishes + " wishes | " + wishesGranted + " granted | " + wishesDenied + " denied";
        guiGraphics.drawString(this.font, statsLine,
                centerX - this.font.width(statsLine) / 2,
                centerY - PAGE_HEIGHT / 2 + 30,
                0xFF888888, false);

        // Page info
        String pageInfo = "Page " + (currentPage + 1) + " of " + pages.size();
        guiGraphics.drawString(this.font, pageInfo,
                centerX - this.font.width(pageInfo) / 2,
                centerY + PAGE_HEIGHT / 2 - 18,
                0x404040, false);

        // Text area background
        int textLeft = centerX - PAGE_WIDTH / 2 + TEXT_AREA_LEFT;
        int textTop = centerY - PAGE_HEIGHT / 2 + TEXT_AREA_TOP;
        guiGraphics.fill(textLeft - 1, textTop - 1, textLeft + TEXT_AREA_WIDTH + 1, textTop + TEXT_AREA_HEIGHT + 1, 0xFFCCCCCC);
        guiGraphics.fill(textLeft, textTop, textLeft + TEXT_AREA_WIDTH, textTop + TEXT_AREA_HEIGHT, 0xFFFFFFFF);

        // Confirmation dialog
        if (showConfirmation) {
            renderConfirmationDialog(guiGraphics, centerX, centerY, mouseX, mouseY);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        if (processing) {
            renderProcessingState(guiGraphics, textLeft, textTop);
        } else if (lastResults != null && !lastResults.isEmpty()) {
            renderResults(guiGraphics, textLeft, textTop);
        } else {
            PageData page = pages.get(currentPage);
            if (page.isProcessed()) {
                renderProcessedPage(guiGraphics, textLeft, textTop, page);
            } else {
                renderEditingPage(guiGraphics, textLeft, textTop);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderConfirmationDialog(GuiGraphics guiGraphics, int centerX, int centerY, int mouseX, int mouseY) {
        // Darken background
        guiGraphics.fill(0, 0, this.width, this.height, 0xCC000000);

        // Dialog box
        int dialogWidth = 200;
        int dialogHeight = 120;
        int dialogLeft = centerX - dialogWidth / 2;
        int dialogTop = centerY - dialogHeight / 2;

        guiGraphics.fill(dialogLeft, dialogTop, dialogLeft + dialogWidth, dialogTop + dialogHeight, 0xFF1A1A2E);
        guiGraphics.fill(dialogLeft + 1, dialogTop + 1, dialogLeft + dialogWidth - 1, dialogTop + dialogHeight - 1, 0xFF2D2D44);

        // Title
        String confirmTitle = "Confirm Wish";
        guiGraphics.drawString(this.font, confirmTitle,
                centerX - this.font.width(confirmTitle) / 2,
                dialogTop + 10,
                0xFFFF6666, false);

        // Message
        String line1 = "You are about to submit";
        String line2 = pendingWishCount + " wish" + (pendingWishCount > 1 ? "s" : "") + " to the entity.";
        String line3 = "Payment will be extracted.";
        String line4 = "Proceed?";

        guiGraphics.drawString(this.font, line1, dialogLeft + 10, dialogTop + 30, 0xFFCCCCCC, false);
        guiGraphics.drawString(this.font, line2, dialogLeft + 10, dialogTop + 42, 0xFFCCCCCC, false);
        guiGraphics.drawString(this.font, line3, dialogLeft + 10, dialogTop + 54, 0xFFCC6600, false);
        guiGraphics.drawString(this.font, line4, dialogLeft + 10, dialogTop + 66, 0xFFCCCCCC, false);

        // Yes/No buttons
        String yesText = "Yes";
        String noText = "No";
        int yesWidth = this.font.width(yesText) + 16;
        int noWidth = this.font.width(noText) + 16;

        int yesX = centerX - yesWidth - 5;
        int noX = centerX + 5;
        int buttonY = dialogTop + dialogHeight - 30;

        boolean yesHover = mouseX >= yesX && mouseX <= yesX + yesWidth && mouseY >= buttonY && mouseY <= buttonY + 16;
        boolean noHover = mouseX >= noX && mouseX <= noX + noWidth && mouseY >= buttonY && mouseY <= buttonY + 16;

        guiGraphics.fill(yesX, buttonY, yesX + yesWidth, buttonY + 16, yesHover ? 0xFF228B22 : 0xFF1A6B1A);
        guiGraphics.drawString(this.font, yesText, yesX + 8, buttonY + 3, 0xFF00FF00, false);

        guiGraphics.fill(noX, buttonY, noX + noWidth, buttonY + 16, noHover ? 0xFFCC2222 : 0xFF991A1A);
        guiGraphics.drawString(this.font, noText, noX + 8, buttonY + 3, 0xFFFF6666, false);
    }

    private void renderProcessingState(GuiGraphics guiGraphics, int textLeft, int textTop) {
        // Animated dots
        int dots = (tickCount / 15) % 4;
        String dotsStr = ".".repeat(dots);

        guiGraphics.drawString(this.font, processingMessage,
                textLeft + 4, textTop + 4, 0xFF6666FF, false);
        guiGraphics.drawString(this.font, "Please wait" + dotsStr,
                textLeft + 4, textTop + 16, 0xFF999999, false);

        // Animated progress indicator
        int progressWidth = (int) ((tickCount % 120) / 120.0 * TEXT_AREA_WIDTH);
        guiGraphics.fill(textLeft, textTop + TEXT_AREA_HEIGHT - 4,
                textLeft + progressWidth, textTop + TEXT_AREA_HEIGHT, 0xFF6666FF);
    }

    private void renderResults(GuiGraphics guiGraphics, int textLeft, int textTop) {
        // Show all results with scrolling
        int lineY = textTop + 4;
        int maxLines = (TEXT_AREA_HEIGHT - 8) / LINE_HEIGHT;
        int linesDrawn = 0;

        for (int i = 0; i < lastResults.size(); i++) {
            WishResponsePayload.WishResult result = lastResults.get(i);

            if (linesDrawn >= maxLines) break;

            // Status line
            String statusText = result.status() == WishBookData.WishStatus.GRANTED
                    ? "GRANTED" : "DENIED";
            int statusColor = result.status() == WishBookData.WishStatus.GRANTED
                    ? 0xFF00AA00 : 0xFFCC0000;

            guiGraphics.drawString(this.font, "Wish " + (i + 1) + ": " + statusText,
                    textLeft + 4, lineY, statusColor, false);
            lineY += LINE_HEIGHT;
            linesDrawn++;

            // Response text
            List<String> wrappedLines = wrapText(result.aiResponseText(), CHARS_PER_LINE);
            for (String line : wrappedLines) {
                if (linesDrawn >= maxLines) break;
                guiGraphics.drawString(this.font, line, textLeft + 4, lineY, 0xFF333333, false);
                lineY += LINE_HEIGHT;
                linesDrawn++;
            }

            // Payment
            if (!result.paymentTaken().isEmpty()) {
                if (linesDrawn < maxLines) {
                    guiGraphics.drawString(this.font, "Payment:", textLeft + 4, lineY, 0xFFCC6600, false);
                    lineY += LINE_HEIGHT;
                    linesDrawn++;
                }
                for (String payment : result.paymentTaken()) {
                    if (linesDrawn >= maxLines) break;
                    guiGraphics.drawString(this.font, "- " + payment, textLeft + 4, lineY, 0xFF666666, false);
                    lineY += LINE_HEIGHT;
                    linesDrawn++;
                }
            }

            // Separator between results
            if (i < lastResults.size() - 1 && linesDrawn < maxLines) {
                guiGraphics.fill(textLeft + 4, lineY, textLeft + TEXT_AREA_WIDTH - 4, lineY + 1, 0xFFCCCCCC);
                lineY += 4;
                linesDrawn++;
            }
        }

        // Scroll indicator
        if (lastResults.size() > 1) {
            String scrollHint = "Press any key to continue";
            guiGraphics.drawString(this.font, scrollHint,
                    textLeft + 4, textTop + TEXT_AREA_HEIGHT - 14,
                    0xFF888888, false);
        }
    }

    private void renderProcessedPage(GuiGraphics guiGraphics, int textLeft, int textTop, PageData page) {
        String statusText = page.status() == WishBookData.WishStatus.GRANTED ? "GRANTED" : "DENIED";
        int statusColor = page.status() == WishBookData.WishStatus.GRANTED ? 0xFF00AA00 : 0xFFCC0000;
        guiGraphics.drawString(this.font, "Status: " + statusText, textLeft + 4, textTop + 4, statusColor, false);

        guiGraphics.drawString(this.font, "Wish:", textLeft + 4, textTop + 16, 0xFF666666, false);
        List<String> wishLines = wrapText(page.wishText(), CHARS_PER_LINE);
        int lineY = textTop + 26;
        for (String line : wishLines) {
            if (lineY + LINE_HEIGHT > textTop + TEXT_AREA_HEIGHT - 4) break;
            guiGraphics.drawString(this.font, line, textLeft + 4, lineY, 0xFF333333, false);
            lineY += LINE_HEIGHT;
        }

        if (!page.aiResponse().isEmpty()) {
            lineY += 2;
            guiGraphics.drawString(this.font, "AI:", textLeft + 4, lineY, 0xFF666666, false);
            lineY += LINE_HEIGHT;
            List<String> responseLines = wrapText(page.aiResponse(), CHARS_PER_LINE);
            for (String line : responseLines) {
                if (lineY + LINE_HEIGHT > textTop + TEXT_AREA_HEIGHT - 4) break;
                guiGraphics.drawString(this.font, line, textLeft + 4, lineY, 0xFF333333, false);
                lineY += LINE_HEIGHT;
            }
        }

        if (!page.paymentTaken().isEmpty()) {
            lineY += 2;
            guiGraphics.drawString(this.font, "Payment:", textLeft + 4, lineY, 0xFFCC6600, false);
            lineY += LINE_HEIGHT;
            for (String payment : page.paymentTaken()) {
                if (lineY + LINE_HEIGHT > textTop + TEXT_AREA_HEIGHT - 4) break;
                guiGraphics.drawString(this.font, "- " + payment, textLeft + 4, lineY, 0xFF666666, false);
                lineY += LINE_HEIGHT;
            }
        }
    }

    private void renderEditingPage(GuiGraphics guiGraphics, int textLeft, int textTop) {
        String wishLabel = "Write your wish:";
        guiGraphics.drawString(this.font, wishLabel,
                textLeft + 4, textTop + 4, 0xFF666666, false);

        List<String> wrappedLines = wrapText(currentPageText.isEmpty() ? "(empty)" : currentPageText, CHARS_PER_LINE);
        int lineY = textTop + 18;
        for (String line : wrappedLines) {
            if (lineY + LINE_HEIGHT > textTop + TEXT_AREA_HEIGHT - 4) break;
            int color = currentPageText.isEmpty() ? 0xFFAAAAAA : 0xFF333333;
            guiGraphics.drawString(this.font, line, textLeft + 4, lineY, color, false);
            lineY += LINE_HEIGHT;
        }

        // Blinking cursor
        if (focused && (tickCount / 20) % 2 == 0) {
            int cursorLine = currentPageText.substring(0, Math.min(cursorPos, currentPageText.length())).split("\n", -1).length - 1;
            int cursorX = textLeft + 4 + this.font.width(currentPageText.substring(
                    Math.max(0, currentPageText.lastIndexOf('\n', Math.min(cursorPos, currentPageText.length()) - 1) + 1),
                    Math.min(cursorPos, currentPageText.length())
            ));
            int cursorY = textTop + 18 + cursorLine * LINE_HEIGHT;
            guiGraphics.fill(cursorX, cursorY, cursorX + 1, cursorY + LINE_HEIGHT - 2, 0xFF000000);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showConfirmation) {
            return handleConfirmationKey(keyCode);
        }

        if (processing) return super.keyPressed(keyCode, scanCode, modifiers);

        if (lastResults != null) {
            if (keyCode == 256) { // Only dismiss on ESC
                lastResults = null;
                reloadFromBook();
                if (currentPage >= pages.size()) currentPage = pages.size() - 1;
                loadPageContent();
                this.onClose();
                return true;
            }
            return true; // consume all other keys while results shown
        }

        if (pages.get(currentPage).isProcessed()) return super.keyPressed(keyCode, scanCode, modifiers);
        if (!focused) return super.keyPressed(keyCode, scanCode, modifiers);

        if (keyCode == 259) { // Backspace
            if (cursorPos > 0 && cursorPos <= currentPageText.length()) {
                currentPageText = currentPageText.substring(0, cursorPos - 1) + currentPageText.substring(cursorPos);
                cursorPos--;
                savePageContent();
                updateButtons();
            }
            return true;
        } else if (keyCode == 32) { // Space
            return true;
        } else if (keyCode == 256) { // Escape
            if (!currentPageText.isEmpty()) {
                currentPageText = "";
                cursorPos = 0;
                savePageContent();
                return true;
            }
            this.onClose();
            return true;
        } else if (keyCode == 257 || keyCode == 335) { // Enter
            if (currentPageText.length() < 1024) {
                currentPageText = currentPageText.substring(0, cursorPos) + "\n" + currentPageText.substring(cursorPos);
                cursorPos++;
                savePageContent();
            }
            return true;
        } else if (keyCode == 262) { // Right arrow
            if (cursorPos < currentPageText.length()) cursorPos++;
            return true;
        } else if (keyCode == 263) { // Left arrow
            if (cursorPos > 0) cursorPos--;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleConfirmationKey(int keyCode) {
        if (keyCode == 257 || keyCode == 335) { // Enter = Yes
            showConfirmation = false;
            submitWishes();
            return true;
        } else if (keyCode == 256) { // Escape = No
            showConfirmation = false;
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (showConfirmation) return true;
        if (processing || lastResults != null) return super.charTyped(codePoint, modifiers);
        if (pages.get(currentPage).isProcessed()) return super.charTyped(codePoint, modifiers);
        if (!focused) return super.charTyped(codePoint, modifiers);

        if (codePoint >= 32 && codePoint < 127 && currentPageText.length() < 1024) {
            currentPageText = currentPageText.substring(0, cursorPos) + codePoint + currentPageText.substring(cursorPos);
            cursorPos++;
            savePageContent();
            updateButtons();
            return true;
        }

        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (showConfirmation) {
                return handleConfirmationClick(mouseX, mouseY);
            }

            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int textLeft = centerX - PAGE_WIDTH / 2 + TEXT_AREA_LEFT;
            int textTop = centerY - PAGE_HEIGHT / 2 + TEXT_AREA_TOP;

            focused = mouseX >= textLeft && mouseX <= textLeft + TEXT_AREA_WIDTH
                    && mouseY >= textTop && mouseY <= textTop + TEXT_AREA_HEIGHT;

            if (lastResults != null) {
                lastResults = null;
                reloadFromBook();
                if (currentPage >= pages.size()) currentPage = pages.size() - 1;
                loadPageContent();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleConfirmationClick(double mouseX, double mouseY) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int dialogWidth = 200;
        int dialogHeight = 120;
        int dialogLeft = centerX - dialogWidth / 2;
        int dialogTop = centerY - dialogHeight / 2;

        int buttonY = dialogTop + dialogHeight - 30;

        // Yes button
        String yesText = "Yes";
        int yesWidth = this.font.width(yesText) + 16;
        int yesX = centerX - yesWidth - 5;
        if (mouseX >= yesX && mouseX <= yesX + yesWidth && mouseY >= buttonY && mouseY <= buttonY + 16) {
            showConfirmation = false;
            submitWishes();
            return true;
        }

        // No button
        String noText = "No";
        int noWidth = this.font.width(noText) + 16;
        int noX = centerX + 5;
        if (mouseX >= noX && mouseX <= noX + noWidth && mouseY >= buttonY && mouseY <= buttonY + 16) {
            showConfirmation = false;
            return true;
        }

        return true;
    }

    private void onGrantWishesClicked() {
        if (processing) return;

        // Count unprocessed pages with wishes
        int wishCount = 0;
        for (PageData page : pages) {
            if (!page.isProcessed() && !page.wishText().isBlank()) {
                wishCount++;
            }
        }

        if (wishCount == 0) return;

        // Show confirmation dialog
        showConfirmation = true;
        pendingWishCount = wishCount;
        updateButtons();
    }

    private void submitWishes() {
        if (processing) return;

        processing = true;
        processingMessage = "The entity stirs...";
        updateButtons();

        List<String> allPages = new ArrayList<>();
        for (PageData page : pages) {
            allPages.add(page.wishText());
        }

        WishSubmissionPayload payload = new WishSubmissionPayload(inventorySlot, allPages);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
    }

    public void onWishResponseReceived(List<WishResponsePayload.WishResult> results) {
        this.processing = false;
        this.lastResults = results;
        updateButtons();
    }

    private void addPage() {
        pages.add(PageData.empty());
        currentPage = pages.size() - 1;
        loadPageContent();
        updateButtons();
    }

    private void removePage() {
        if (pages.size() > 1 && !pages.get(currentPage).isProcessed()) {
            pages.remove(currentPage);
            if (currentPage >= pages.size()) {
                currentPage = pages.size() - 1;
            }
            loadPageContent();
            updateButtons();
        }
    }

    private void turnPage(int direction) {
        int newPage = currentPage + direction;
        if (newPage >= 0 && newPage < pages.size()) {
            savePageContent();
            currentPage = newPage;
            loadPageContent();
            updateButtons();
        }
    }

    private void loadPageContent() {
        PageData page = pages.get(currentPage);
        if (page.isProcessed()) {
            currentPageText = "";
        } else {
            currentPageText = page.wishText();
        }
        cursorPos = currentPageText.length();
    }

    private void savePageContent() {
        pages.set(currentPage, pages.get(currentPage).withText(currentPageText));
    }

    @Override
    public void onClose() {
        savePageContent();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<String> wrapText(String text, int maxCharsPerLine) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        for (String paragraph : text.split("\n")) {
            if (paragraph.length() <= maxCharsPerLine) {
                lines.add(paragraph);
            } else {
                StringBuilder currentLine = new StringBuilder();
                for (String word : paragraph.split(" ")) {
                    if (currentLine.length() + word.length() + 1 > maxCharsPerLine) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        if (currentLine.length() > 0) currentLine.append(" ");
                        currentLine.append(word);
                    }
                }
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
            }
        }
        return lines;
    }
}

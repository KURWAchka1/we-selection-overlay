package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OverlayUiDesignPreviewScreen extends Screen {
    private static final int HEADER_HEIGHT = 54;
    private static final int BUTTON_HEIGHT = 20;
    private final Screen parent;
    private Design design = Design.WORKBENCH;

    public OverlayUiDesignPreviewScreen(Screen parent) {
        super(Component.translatable("screen.worldedit_selection_overlay.ui_preview.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.max(72, Math.min(112, (width - 52) / 3));
        int totalWidth = buttonWidth * 3 + 8;
        int startX = (width - totalWidth) / 2;
        int y = 28;
        for (Design value : Design.values()) {
            int x = startX + value.ordinal() * (buttonWidth + 4);
            addRenderableWidget(Button.builder(value.label(), button -> design = value)
                    .bounds(x, y, buttonWidth, BUTTON_HEIGHT)
                    .build());
        }
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.worldedit_selection_overlay.ui_preview.back"),
                        button -> onClose())
                .bounds(Math.max(8, width - 88), Math.max(HEADER_HEIGHT + 8, height - 28), 80, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xF0080B10);
        context.fill(0, 0, width, HEADER_HEIGHT, 0xFF101720);
        context.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0xFF334252);
        context.drawString(font, title, 10, 9, 0xFFF1F5F8, false);

        int previewX = 8;
        int previewY = HEADER_HEIGHT + 8;
        int previewWidth = Math.max(1, width - 16);
        int previewHeight = Math.max(1, height - previewY - 34);
        context.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF0B1017);
        outline(context, previewX, previewY, previewWidth, previewHeight, 0xFF334252);

        switch (design) {
            case CLASSIC -> drawClassic(context, previewX, previewY, previewWidth, previewHeight);
            case WORKBENCH -> drawWorkbench(context, previewX, previewY, previewWidth, previewHeight);
            case FOCUS -> drawFocus(context, previewX, previewY, previewWidth, previewHeight);
        }

        context.drawString(
                font,
                Component.translatable("screen.worldedit_selection_overlay.ui_preview.note"),
                10,
                Math.max(HEADER_HEIGHT + 8, height - 20),
                0xFF8E9CAB,
                false
        );
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawClassic(GuiGraphics context, int x, int y, int width, int height) {
        int panelWidth = Math.min(286, Math.max(190, width - 40));
        int panelHeight = Math.min(270, Math.max(120, height - 28));
        int px = x + (width - panelWidth) / 2;
        int py = y + (height - panelHeight) / 2;
        panel(context, px, py, panelWidth, panelHeight, 0xE0121721, 0xFF48586B);
        context.drawString(font, Component.translatable("screen.worldedit_selection_overlay.settings.title"), px + 12, py + 10, 0xFFF0F4F8, false);
        int rowY = py + 34;
        int rowWidth = panelWidth - 24;
        for (int i = 0; i < 6 && rowY + 19 < py + panelHeight - 28; i++) {
            row(context, px + 12 + (i == 1 ? 16 : 0), rowY, rowWidth - (i == 1 ? 16 : 0), 18, i == 0, 0xFF263445);
            rowY += 25;
        }
        row(context, px + 12, py + panelHeight - 28, rowWidth, 18, true, 0xFF34465C);
    }

    private void drawWorkbench(GuiGraphics context, int x, int y, int width, int height) {
        int panelWidth = Math.min(800, Math.max(240, width - 30));
        int panelHeight = Math.min(420, Math.max(130, height - 24));
        int px = x + (width - panelWidth) / 2;
        int py = y + (height - panelHeight) / 2;
        panel(context, px, py, panelWidth, panelHeight, 0xF010161F, 0xFF3B4B5E);
        int sidebarWidth = Math.min(180, Math.max(82, panelWidth / 4));
        context.fill(px, py, px + sidebarWidth, py + panelHeight, 0xFF151E29);
        context.fill(px + sidebarWidth, py, px + sidebarWidth + 1, py + panelHeight, 0xFF344557);
        context.drawString(font, Component.translatable("screen.worldedit_selection_overlay.ui_preview.workbench"), px + 12, py + 12, 0xFF63E7EE, false);
        String[] labels = {"overlay", "ruler", "geometry", "blocks"};
        int navY = py + 38;
        for (int i = 0; i < labels.length && navY + 22 < py + panelHeight; i++) {
            if (i == 0) {
                context.fill(px + 6, navY - 3, px + sidebarWidth - 6, navY + 17, 0xFF223247);
                context.fill(px + 6, navY - 3, px + 9, navY + 17, 0xFF63E7EE);
            }
            context.drawString(font, Component.translatable("screen.worldedit_selection_overlay.ui_preview.section." + labels[i]), px + 14, navY, i == 0 ? 0xFFF0F5F7 : 0xFF9AA8B6, false);
            navY += 27;
        }

        int contentX = px + sidebarWidth + 18;
        int contentWidth = panelWidth - sidebarWidth - 36;
        context.drawString(font, Component.translatable("screen.worldedit_selection_overlay.ui_preview.section.overlay"), contentX, py + 14, 0xFFF0F5F7, false);
        int rowY = py + 38;
        for (int i = 0; i < 5 && rowY + 31 < py + panelHeight; i++) {
            context.fill(contentX, rowY, contentX + contentWidth, rowY + 31, 0xFF18222E);
            context.fill(contentX, rowY + 30, contentX + contentWidth, rowY + 31, 0xFF2D3B4A);
            context.fill(contentX + 10, rowY + 10, contentX + Math.max(62, contentWidth / 2), rowY + 13, 0xFF6F7F90);
            toggle(context, contentX + contentWidth - 34, rowY + 8, i != 2);
            rowY += 36;
        }
        context.fill(contentX, py + panelHeight - 32, contentX + contentWidth, py + panelHeight - 31, 0xFF2F3D4C);
        context.drawString(font, Component.translatable("screen.worldedit_selection_overlay.ui_preview.note_short"), contentX, py + panelHeight - 20, 0xFF8191A1, false);
    }

    private void drawFocus(GuiGraphics context, int x, int y, int width, int height) {
        int panelWidth = Math.min(700, Math.max(220, width - 36));
        int panelHeight = Math.min(380, Math.max(128, height - 28));
        int px = x + (width - panelWidth) / 2;
        int py = y + (height - panelHeight) / 2;
        panel(context, px, py, panelWidth, panelHeight, 0xF00D131B, 0xFF3B4C5F);
        context.drawString(font, Component.translatable("screen.worldedit_selection_overlay.ui_preview.focus"), px + 14, py + 12, 0xFFF0F5F7, false);
        int tabWidth = Math.max(42, (panelWidth - 28) / 4);
        int tabX = px + 14;
        for (int i = 0; i < 4; i++) {
            context.fill(tabX + i * tabWidth, py + 31, tabX + (i + 1) * tabWidth - 3, py + 32, i == 0 ? 0xFF63E7EE : 0xFF344354);
        }
        int cardX = px + 14;
        int cardY = py + 48;
        int cardWidth = panelWidth - 28;
        int cardHeight = Math.max(48, panelHeight - 94);
        context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xFF151E28);
        context.drawString(font, Component.translatable("screen.worldedit_selection_overlay.ui_preview.visibility"), cardX + 14, cardY + 13, 0xFFF0F5F7, false);
        int itemY = cardY + 34;
        for (int i = 0; i < 4 && itemY + 25 < cardY + cardHeight; i++) {
            context.fill(cardX + 14, itemY + 21, cardX + cardWidth - 14, itemY + 22, 0xFF2A3949);
            context.fill(cardX + 14, itemY + 7, cardX + Math.max(72, cardWidth / 2), itemY + 10, 0xFF6E7F90);
            toggle(context, cardX + cardWidth - 48, itemY + 3, i != 1);
            itemY += 32;
        }
        context.fill(px + 14, py + panelHeight - 31, px + panelWidth - 104, py + panelHeight - 13, 0xFF1A2632);
        context.fill(px + panelWidth - 96, py + panelHeight - 31, px + panelWidth - 14, py + panelHeight - 13, 0xFF63E7EE);
    }

    private static void panel(GuiGraphics context, int x, int y, int width, int height, int fill, int border) {
        context.fill(x, y, x + width, y + height, fill);
        outline(context, x, y, width, height, border);
    }

    private static void row(GuiGraphics context, int x, int y, int width, int height, boolean active, int color) {
        context.fill(x, y, x + width, y + height, active ? color : 0xFF1B2531);
        outline(context, x, y, width, height, active ? 0xFF6B7D91 : 0xFF344354);
    }

    private static void toggle(GuiGraphics context, int x, int y, boolean enabled) {
        context.fill(x, y, x + 25, y + 12, enabled ? 0xFF3A8E79 : 0xFF3A4652);
        context.fill(enabled ? x + 14 : x + 2, y + 2, enabled ? x + 23 : x + 11, y + 10, enabled ? 0xFFBFF8DE : 0xFF909BA6);
    }

    private static void outline(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private enum Design {
        CLASSIC("screen.worldedit_selection_overlay.ui_preview.classic"),
        WORKBENCH("screen.worldedit_selection_overlay.ui_preview.workbench"),
        FOCUS("screen.worldedit_selection_overlay.ui_preview.focus");

        private final String translationKey;

        Design(String translationKey) {
            this.translationKey = translationKey;
        }

        private Component label() {
            return Component.translatable(translationKey);
        }
    }
}


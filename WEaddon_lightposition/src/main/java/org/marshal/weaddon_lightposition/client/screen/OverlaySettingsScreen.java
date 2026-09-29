package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.marshal.weaddon_lightposition.client.WorldEditPointOverlayClient;
import org.marshal.weaddon_lightposition.client.overlay.DiagonalMode;
import org.marshal.weaddon_lightposition.client.overlay.RenderMode;
import org.marshal.weaddon_lightposition.client.overlay.RulerTrackingMode;

public final class OverlaySettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 286;
    private static final int PANEL_HEIGHT = 304;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 6;
    private static final int GRID_SUBFUNCTION_INDENT = 18;
    private static final int AXIS_BUTTON_SIZE = 22;
    private static final int AXIS_BUTTON_GAP = 8;

    private final WorldEditPointOverlayClient overlayClient;
    private final Screen parent;

    private Button overlayButton;
    private Button gridButton;
    private Button clipboardPreviewButton;
    private Button rulerButton;
    private Button axisXButton;
    private Button axisYButton;
    private Button axisZButton;
    private Button renderModeButton;
    private Button diagonalModeButton;
    private Button trackingModeButton;
    private Button colorEditorButton;
    private Button doneButton;
    private int rulerAxesLabelY;

    public OverlaySettingsScreen(WorldEditPointOverlayClient overlayClient, Screen parent) {
        super(Component.translatable("screen.worldedit_selection_overlay.settings.title"));
        this.overlayClient = overlayClient;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();
        int buttonWidth = PANEL_WIDTH - 24;

        colorEditorButton = addRenderableWidget(
                Button.builder(
                        Component.translatable("screen.worldedit_selection_overlay.settings.colors_short"),
                        button -> {
                            if (minecraft != null) {
                                minecraft.setScreen(new OverlayColorSettingsScreen(overlayClient, this));
                            }
                        }
                ).bounds(x + PANEL_WIDTH - 36, y + 6, 24, BUTTON_HEIGHT).build()
        );

        overlayButton = addRenderableWidget(
                Button.builder(overlayButtonText(), button -> {
                    overlayClient.setOverlayEnabled(!overlayClient.overlayEnabled());
                    refreshControlsAfterVisibilityChange();
                }).bounds(x + 12, y + 32, buttonWidth, BUTTON_HEIGHT).build()
        );

        gridButton = addRenderableWidget(
                Button.builder(gridButtonText(), button -> {
                    overlayClient.setSelectionGridEnabled(!overlayClient.selectionGridEnabled());
                    button.setMessage(gridButtonText());
                }).bounds(x + 12 + GRID_SUBFUNCTION_INDENT, y + 58, buttonWidth - GRID_SUBFUNCTION_INDENT, BUTTON_HEIGHT).build()
        );

        clipboardPreviewButton = addRenderableWidget(
                Button.builder(clipboardPreviewButtonText(), button -> {
                    overlayClient.setClipboardPreviewEnabled(!overlayClient.clipboardPreviewEnabled());
                    button.setMessage(clipboardPreviewButtonText());
                }).bounds(x + 12, y + 84, buttonWidth, BUTTON_HEIGHT).build()
        );

        rulerButton = addRenderableWidget(
                Button.builder(rulerButtonText(), button -> {
                    overlayClient.setRulerEnabled(!overlayClient.rulerEnabled());
                    refreshControlsAfterVisibilityChange();
                }).bounds(x + 12, y + 110, buttonWidth, BUTTON_HEIGHT).build()
        );

        int axisButtonsWidth = AXIS_BUTTON_SIZE * 3 + AXIS_BUTTON_GAP * 2;
        int axisButtonsX = x + 12 + (buttonWidth - axisButtonsWidth) / 2;
        int axisButtonsY = y + 122;
        axisXButton = addRenderableWidget(
                Button.builder(axisButtonText('X', overlayClient.rulerAxisXEnabled()), button -> {
                    overlayClient.setRulerAxisXEnabled(!overlayClient.rulerAxisXEnabled());
                    updateRulerAxisButtons();
                }).bounds(axisButtonsX, axisButtonsY, AXIS_BUTTON_SIZE, AXIS_BUTTON_SIZE).build()
        );
        axisYButton = addRenderableWidget(
                Button.builder(axisButtonText('Y', overlayClient.rulerAxisYEnabled()), button -> {
                    overlayClient.setRulerAxisYEnabled(!overlayClient.rulerAxisYEnabled());
                    updateRulerAxisButtons();
                }).bounds(axisButtonsX + AXIS_BUTTON_SIZE + AXIS_BUTTON_GAP, axisButtonsY, AXIS_BUTTON_SIZE, AXIS_BUTTON_SIZE).build()
        );
        axisZButton = addRenderableWidget(
                Button.builder(axisButtonText('Z', overlayClient.rulerAxisZEnabled()), button -> {
                    overlayClient.setRulerAxisZEnabled(!overlayClient.rulerAxisZEnabled());
                    updateRulerAxisButtons();
                }).bounds(axisButtonsX + (AXIS_BUTTON_SIZE + AXIS_BUTTON_GAP) * 2, axisButtonsY, AXIS_BUTTON_SIZE, AXIS_BUTTON_SIZE).build()
        );

        renderModeButton = addRenderableWidget(
                Button.builder(renderModeButtonText(), button -> {
                    overlayClient.setRenderMode(overlayClient.renderMode().next());
                    button.setMessage(renderModeButtonText());
                }).bounds(x + 12, y + 150, buttonWidth, BUTTON_HEIGHT).build()
        );

        diagonalModeButton = addRenderableWidget(
                Button.builder(diagonalModeButtonText(), button -> {
                    overlayClient.setDiagonalMode(overlayClient.diagonalMode().next());
                    button.setMessage(diagonalModeButtonText());
                }).bounds(x + 12, y + 176, buttonWidth, BUTTON_HEIGHT).build()
        );

        trackingModeButton = addRenderableWidget(
                Button.builder(trackingModeButtonText(), button -> {
                    overlayClient.setRulerTrackingMode(overlayClient.rulerTrackingMode().next());
                    button.setMessage(trackingModeButtonText());
                }).bounds(x + 12, y + 202, buttonWidth, BUTTON_HEIGHT).build()
        );

        doneButton = addRenderableWidget(
                Button.builder(Component.translatable("screen.worldedit_selection_overlay.settings.done"), button -> onClose())
                        .bounds(x + 12, y + PANEL_HEIGHT - 32, buttonWidth, BUTTON_HEIGHT)
                        .build()
        );

        layoutControls();
        updateRulerAxisButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        syncControls();
        context.fill(0, 0, width, height, 0x68000000);

        int x = panelX();
        int y = panelY();
        context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xD0121721);
        drawOutline(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF3A465A);
        context.text(font, title, x + 12, y + 10, 0xFFEAF0FA, false);
        if (overlayClient.rulerEnabled()) {
            context.text(
                    font,
                    Component.translatable("screen.worldedit_selection_overlay.settings.ruler_axes"),
                    x + 12,
                    rulerAxesLabelY,
                    0xFFB4C0D3,
                    false
            );
        }
        context.text(
                font,
                Component.translatable("screen.worldedit_selection_overlay.settings.hint"),
                x + 12,
                y + PANEL_HEIGHT - 44,
                0xFFB4C0D3,
                false
        );

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (click.button() == 0 && overlayClient.rulerEnabled()) {
            // Allow re-enabling axis buttons even when they are visually disabled.
            if (tryEnableDisabledAxis(axisXButton, click.x(), click.y(), () -> overlayClient.setRulerAxisXEnabled(true))) {
                return true;
            }
            if (tryEnableDisabledAxis(axisYButton, click.x(), click.y(), () -> overlayClient.setRulerAxisYEnabled(true))) {
                return true;
            }
            if (tryEnableDisabledAxis(axisZButton, click.x(), click.y(), () -> overlayClient.setRulerAxisZEnabled(true))) {
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private Component overlayButtonText() {
        return Component.translatable(
                "screen.worldedit_selection_overlay.settings.overlay",
                toggleStateText(overlayClient.overlayEnabled())
        );
    }

    private Component gridButtonText() {
        return Component.translatable(
                "screen.worldedit_selection_overlay.settings.grid",
                toggleStateText(overlayClient.selectionGridEnabled())
        );
    }

    private Component rulerButtonText() {
        return Component.translatable(
                "screen.worldedit_selection_overlay.settings.ruler",
                toggleStateText(overlayClient.rulerEnabled())
        );
    }

    private Component clipboardPreviewButtonText() {
        if (!overlayClient.clipboardPreviewAvailable()) {
            return Component.translatable("screen.worldedit_selection_overlay.settings.clipboard_preview_unavailable");
        }
        return Component.translatable(
                "screen.worldedit_selection_overlay.settings.clipboard_preview",
                toggleStateText(overlayClient.clipboardPreviewEnabled())
        );
    }

    private Component renderModeButtonText() {
        RenderMode mode = overlayClient.renderMode();
        return Component.translatable(
                "screen.worldedit_selection_overlay.settings.render_mode",
                Component.translatable("message.worldedit_selection_overlay.render_mode." + mode.translationSuffix())
        );
    }

    private Component diagonalModeButtonText() {
        DiagonalMode mode = overlayClient.diagonalMode();
        return Component.translatable(
                "screen.worldedit_selection_overlay.settings.diagonal",
                Component.translatable("screen.worldedit_selection_overlay.settings.diagonal." + mode.translationSuffix())
        );
    }

    private Component trackingModeButtonText() {
        RulerTrackingMode mode = overlayClient.rulerTrackingMode();
        return Component.translatable(
                "screen.worldedit_selection_overlay.settings.ruler_tracking",
                Component.translatable("screen.worldedit_selection_overlay.settings.ruler_tracking." + mode.translationSuffix())
        );
    }

    private Component toggleStateText(boolean enabled) {
        return Component.translatable(
                enabled
                        ? "screen.worldedit_selection_overlay.settings.value.on"
                        : "screen.worldedit_selection_overlay.settings.value.off"
        );
    }

    private Component axisButtonText(char axis, boolean enabled) {
        return Component.literal(Character.toString(axis));
    }

    private int panelX() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private static void drawOutline(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void refreshControlsAfterVisibilityChange() {
        syncControls();
    }

    private void syncControls() {
        if (overlayButton != null) {
            overlayButton.setMessage(overlayButtonText());
        }
        if (gridButton != null) {
            gridButton.setMessage(gridButtonText());
        }
        if (rulerButton != null) {
            rulerButton.setMessage(rulerButtonText());
        }
        if (clipboardPreviewButton != null) {
            clipboardPreviewButton.setMessage(clipboardPreviewButtonText());
            clipboardPreviewButton.active = overlayClient.clipboardPreviewAvailable();
        }
        if (renderModeButton != null) {
            renderModeButton.setMessage(renderModeButtonText());
        }
        if (diagonalModeButton != null) {
            diagonalModeButton.setMessage(diagonalModeButtonText());
        }
        if (trackingModeButton != null) {
            trackingModeButton.setMessage(trackingModeButtonText());
        }

        layoutControls();
        updateRulerAxisButtons();
    }

    private void layoutControls() {
        if (overlayButton == null || gridButton == null || clipboardPreviewButton == null || rulerButton == null || renderModeButton == null
                || diagonalModeButton == null || trackingModeButton == null || doneButton == null) {
            return;
        }

        int x = panelX();
        int y = panelY();
        int buttonWidth = PANEL_WIDTH - 24;
        int rowY = y + 32;

        if (colorEditorButton != null) {
            position(colorEditorButton, x + PANEL_WIDTH - 36, y + 6, 24, BUTTON_HEIGHT);
        }

        position(overlayButton, x + 12, rowY, buttonWidth, BUTTON_HEIGHT);
        rowY += BUTTON_HEIGHT + BUTTON_SPACING;

        boolean overlayVisible = overlayClient.overlayEnabled();
        gridButton.visible = overlayVisible;
        gridButton.active = overlayVisible;
        if (overlayVisible) {
            position(
                    gridButton,
                    x + 12 + GRID_SUBFUNCTION_INDENT,
                    rowY,
                    buttonWidth - GRID_SUBFUNCTION_INDENT,
                    BUTTON_HEIGHT
            );
            rowY += BUTTON_HEIGHT + BUTTON_SPACING;
        }

        position(clipboardPreviewButton, x + 12, rowY, buttonWidth, BUTTON_HEIGHT);
        clipboardPreviewButton.active = overlayClient.clipboardPreviewAvailable();
        rowY += BUTTON_HEIGHT + BUTTON_SPACING;

        position(rulerButton, x + 12, rowY, buttonWidth, BUTTON_HEIGHT);
        rowY += BUTTON_HEIGHT + BUTTON_SPACING;

        if (overlayClient.rulerEnabled()) {
            rulerAxesLabelY = rowY + 2;
            int axisButtonsWidth = AXIS_BUTTON_SIZE * 3 + AXIS_BUTTON_GAP * 2;
            int axisButtonsX = x + 12 + (buttonWidth - axisButtonsWidth) / 2;
            int axisButtonsY = rowY + 12;
            position(axisXButton, axisButtonsX, axisButtonsY, AXIS_BUTTON_SIZE, AXIS_BUTTON_SIZE);
            position(axisYButton, axisButtonsX + AXIS_BUTTON_SIZE + AXIS_BUTTON_GAP, axisButtonsY, AXIS_BUTTON_SIZE, AXIS_BUTTON_SIZE);
            position(axisZButton, axisButtonsX + (AXIS_BUTTON_SIZE + AXIS_BUTTON_GAP) * 2, axisButtonsY, AXIS_BUTTON_SIZE, AXIS_BUTTON_SIZE);
            rowY += 40;
        }

        position(renderModeButton, x + 12, rowY, buttonWidth, BUTTON_HEIGHT);
        rowY += BUTTON_HEIGHT + BUTTON_SPACING;
        position(diagonalModeButton, x + 12, rowY, buttonWidth, BUTTON_HEIGHT);
        rowY += BUTTON_HEIGHT + BUTTON_SPACING;
        position(trackingModeButton, x + 12, rowY, buttonWidth, BUTTON_HEIGHT);
        position(doneButton, x + 12, y + PANEL_HEIGHT - 32, buttonWidth, BUTTON_HEIGHT);
    }

    private static void position(Button button, int x, int y, int width, int height) {
        if (button == null) {
            return;
        }
        button.setX(x);
        button.setY(y);
        button.setWidth(width);
        button.setHeight(height);
    }

    private void updateRulerAxisButtons() {
        if (axisXButton == null || axisYButton == null || axisZButton == null) {
            return;
        }
        boolean visible = overlayClient.rulerEnabled();
        axisXButton.visible = visible;
        axisYButton.visible = visible;
        axisZButton.visible = visible;
        axisXButton.active = visible && overlayClient.rulerAxisXEnabled();
        axisYButton.active = visible && overlayClient.rulerAxisYEnabled();
        axisZButton.active = visible && overlayClient.rulerAxisZEnabled();
        axisXButton.setMessage(axisButtonText('X', overlayClient.rulerAxisXEnabled()));
        axisYButton.setMessage(axisButtonText('Y', overlayClient.rulerAxisYEnabled()));
        axisZButton.setMessage(axisButtonText('Z', overlayClient.rulerAxisZEnabled()));
    }

    private boolean tryEnableDisabledAxis(Button axisButton, double mouseX, double mouseY, Runnable action) {
        if (axisButton == null || !axisButton.visible || axisButton.active) {
            return false;
        }
        if (!isInside(axisButton, mouseX, mouseY)) {
            return false;
        }
        action.run();
        updateRulerAxisButtons();
        if (minecraft != null) {
            axisButton.playDownSound(minecraft.getSoundManager());
        }
        return true;
    }

    private static boolean isInside(Button button, double mouseX, double mouseY) {
        return mouseX >= button.getX()
                && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY()
                && mouseY < button.getY() + button.getHeight();
    }
}

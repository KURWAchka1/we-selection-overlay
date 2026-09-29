package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
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

    private ButtonWidget overlayButton;
    private ButtonWidget gridButton;
    private ButtonWidget clipboardPreviewButton;
    private ButtonWidget rulerButton;
    private ButtonWidget axisXButton;
    private ButtonWidget axisYButton;
    private ButtonWidget axisZButton;
    private ButtonWidget renderModeButton;
    private ButtonWidget diagonalModeButton;
    private ButtonWidget trackingModeButton;
    private ButtonWidget uiPreviewButton;
    private ButtonWidget colorEditorButton;
    private ButtonWidget doneButton;
    private int rulerAxesLabelY;

    public OverlaySettingsScreen(WorldEditPointOverlayClient overlayClient, Screen parent) {
        super(Text.translatable("screen.worldedit_selection_overlay.settings.title"));
        this.overlayClient = overlayClient;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();
        int buttonWidth = PANEL_WIDTH - 24;

        colorEditorButton = addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable("screen.worldedit_selection_overlay.settings.colors_short"),
                        button -> {
                            if (client != null) {
                                client.setScreen(new OverlayColorSettingsScreen(overlayClient, this));
                            }
                        }
                ).dimensions(x + PANEL_WIDTH - 36, y + 6, 24, BUTTON_HEIGHT).build()
        );

        uiPreviewButton = addDrawableChild(
                ButtonWidget.builder(Text.literal("UI"), button -> {
                    if (client != null) {
                        client.setScreen(new OverlayUiDesignPreviewScreen(this));
                    }
                }).dimensions(x + PANEL_WIDTH - 66, y + 6, 24, BUTTON_HEIGHT).build()
        );

        overlayButton = addDrawableChild(
                ButtonWidget.builder(overlayButtonText(), button -> {
                    overlayClient.setOverlayEnabled(!overlayClient.overlayEnabled());
                    refreshControlsAfterVisibilityChange();
                }).dimensions(x + 12, y + 32, buttonWidth, BUTTON_HEIGHT).build()
        );

        gridButton = addDrawableChild(
                ButtonWidget.builder(gridButtonText(), button -> {
                    overlayClient.setSelectionGridEnabled(!overlayClient.selectionGridEnabled());
                    button.setMessage(gridButtonText());
                }).dimensions(x + 12 + GRID_SUBFUNCTION_INDENT, y + 58, buttonWidth - GRID_SUBFUNCTION_INDENT, BUTTON_HEIGHT).build()
        );

        clipboardPreviewButton = addDrawableChild(
                ButtonWidget.builder(clipboardPreviewButtonText(), button -> {
                    overlayClient.setClipboardPreviewEnabled(!overlayClient.clipboardPreviewEnabled());
                    button.setMessage(clipboardPreviewButtonText());
                }).dimensions(x + 12, y + 84, buttonWidth, BUTTON_HEIGHT).build()
        );

        rulerButton = addDrawableChild(
                ButtonWidget.builder(rulerButtonText(), button -> {
                    overlayClient.setRulerEnabled(!overlayClient.rulerEnabled());
                    refreshControlsAfterVisibilityChange();
                }).dimensions(x + 12, y + 110, buttonWidth, BUTTON_HEIGHT).build()
        );

        int axisButtonsWidth = AXIS_BUTTON_SIZE * 3 + AXIS_BUTTON_GAP * 2;
        int axisButtonsX = x + 12 + (buttonWidth - axisButtonsWidth) / 2;
        int axisButtonsY = y + 122;
        axisXButton = addDrawableChild(
                ButtonWidget.builder(axisButtonText('X', overlayClient.rulerAxisXEnabled()), button -> {
                    overlayClient.setRulerAxisXEnabled(!overlayClient.rulerAxisXEnabled());
                    updateRulerAxisButtons();
                }).dimensions(axisButtonsX, axisButtonsY, AXIS_BUTTON_SIZE, AXIS_BUTTON_SIZE).build()
        );
        axisYButton = addDrawableChild(
                ButtonWidget.builder(axisButtonText('Y', overlayClient.rulerAxisYEnabled()), button -> {
                    overlayClient.setRulerAxisYEnabled(!overlayClient.rulerAxisYEnabled());
                    updateRulerAxisButtons();
                }).dimensions(axisButtonsX + AXIS_BUTTON_SIZE + AXIS_BUTTON_GAP, axisButtonsY, AXIS_BUTTON_SIZE, AXIS_BUTTON_SIZE).build()
        );
        axisZButton = addDrawableChild(
                ButtonWidget.builder(axisButtonText('Z', overlayClient.rulerAxisZEnabled()), button -> {
                    overlayClient.setRulerAxisZEnabled(!overlayClient.rulerAxisZEnabled());
                    updateRulerAxisButtons();
                }).dimensions(axisButtonsX + (AXIS_BUTTON_SIZE + AXIS_BUTTON_GAP) * 2, axisButtonsY, AXIS_BUTTON_SIZE, AXIS_BUTTON_SIZE).build()
        );

        renderModeButton = addDrawableChild(
                ButtonWidget.builder(renderModeButtonText(), button -> {
                    overlayClient.setRenderMode(overlayClient.renderMode().next());
                    button.setMessage(renderModeButtonText());
                }).dimensions(x + 12, y + 150, buttonWidth, BUTTON_HEIGHT).build()
        );

        diagonalModeButton = addDrawableChild(
                ButtonWidget.builder(diagonalModeButtonText(), button -> {
                    overlayClient.setDiagonalMode(overlayClient.diagonalMode().next());
                    button.setMessage(diagonalModeButtonText());
                }).dimensions(x + 12, y + 176, buttonWidth, BUTTON_HEIGHT).build()
        );

        trackingModeButton = addDrawableChild(
                ButtonWidget.builder(trackingModeButtonText(), button -> {
                    overlayClient.setRulerTrackingMode(overlayClient.rulerTrackingMode().next());
                    button.setMessage(trackingModeButtonText());
                }).dimensions(x + 12, y + 202, buttonWidth, BUTTON_HEIGHT).build()
        );

        doneButton = addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.worldedit_selection_overlay.settings.done"), button -> close())
                        .dimensions(x + 12, y + PANEL_HEIGHT - 32, buttonWidth, BUTTON_HEIGHT)
                        .build()
        );

        layoutControls();
        updateRulerAxisButtons();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        syncControls();
        context.fill(0, 0, width, height, 0x68000000);

        int x = panelX();
        int y = panelY();
        context.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xD0121721);
        drawOutline(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF3A465A);
        context.drawText(textRenderer, title, x + 12, y + 10, 0xFFEAF0FA, false);
        if (overlayClient.rulerEnabled()) {
            context.drawText(
                    textRenderer,
                    Text.translatable("screen.worldedit_selection_overlay.settings.ruler_axes"),
                    x + 12,
                    rulerAxesLabelY,
                    0xFFB4C0D3,
                    false
            );
        }
        context.drawText(
                textRenderer,
                Text.translatable("screen.worldedit_selection_overlay.settings.hint"),
                x + 12,
                y + PANEL_HEIGHT - 44,
                0xFFB4C0D3,
                false
        );

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
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
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private Text overlayButtonText() {
        return Text.translatable(
                "screen.worldedit_selection_overlay.settings.overlay",
                toggleStateText(overlayClient.overlayEnabled())
        );
    }

    private Text gridButtonText() {
        return Text.translatable(
                "screen.worldedit_selection_overlay.settings.grid",
                toggleStateText(overlayClient.selectionGridEnabled())
        );
    }

    private Text rulerButtonText() {
        return Text.translatable(
                "screen.worldedit_selection_overlay.settings.ruler",
                toggleStateText(overlayClient.rulerEnabled())
        );
    }

    private Text clipboardPreviewButtonText() {
        if (!overlayClient.clipboardPreviewAvailable()) {
            return Text.translatable("screen.worldedit_selection_overlay.settings.clipboard_preview_unavailable");
        }
        return Text.translatable(
                "screen.worldedit_selection_overlay.settings.clipboard_preview",
                toggleStateText(overlayClient.clipboardPreviewEnabled())
        );
    }

    private Text renderModeButtonText() {
        RenderMode mode = overlayClient.renderMode();
        return Text.translatable(
                "screen.worldedit_selection_overlay.settings.render_mode",
                Text.translatable("message.worldedit_selection_overlay.render_mode." + mode.translationSuffix())
        );
    }

    private Text diagonalModeButtonText() {
        DiagonalMode mode = overlayClient.diagonalMode();
        return Text.translatable(
                "screen.worldedit_selection_overlay.settings.diagonal",
                Text.translatable("screen.worldedit_selection_overlay.settings.diagonal." + mode.translationSuffix())
        );
    }

    private Text trackingModeButtonText() {
        RulerTrackingMode mode = overlayClient.rulerTrackingMode();
        return Text.translatable(
                "screen.worldedit_selection_overlay.settings.ruler_tracking",
                Text.translatable("screen.worldedit_selection_overlay.settings.ruler_tracking." + mode.translationSuffix())
        );
    }

    private Text toggleStateText(boolean enabled) {
        return Text.translatable(
                enabled
                        ? "screen.worldedit_selection_overlay.settings.value.on"
                        : "screen.worldedit_selection_overlay.settings.value.off"
        );
    }

    private Text axisButtonText(char axis, boolean enabled) {
        return Text.literal(Character.toString(axis));
    }

    private int panelX() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private static void drawOutline(DrawContext context, int x, int y, int width, int height, int color) {
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
        if (uiPreviewButton != null) {
            position(uiPreviewButton, x + PANEL_WIDTH - 66, y + 6, 24, BUTTON_HEIGHT);
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

    private static void position(ButtonWidget button, int x, int y, int width, int height) {
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

    private boolean tryEnableDisabledAxis(ButtonWidget axisButton, double mouseX, double mouseY, Runnable action) {
        if (axisButton == null || !axisButton.visible || axisButton.active) {
            return false;
        }
        if (!isInside(axisButton, mouseX, mouseY)) {
            return false;
        }
        action.run();
        updateRulerAxisButtons();
        if (client != null) {
            axisButton.playDownSound(client.getSoundManager());
        }
        return true;
    }

    private static boolean isInside(ButtonWidget button, double mouseX, double mouseY) {
        return mouseX >= button.getX()
                && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY()
                && mouseY < button.getY() + button.getHeight();
    }
}

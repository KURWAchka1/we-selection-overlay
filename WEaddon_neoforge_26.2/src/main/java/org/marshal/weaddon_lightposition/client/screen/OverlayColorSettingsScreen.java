package org.marshal.weaddon_lightposition.client.screen;

import org.lwjgl.glfw.GLFW;
import org.marshal.weaddon_lightposition.client.WorldEditPointOverlayClient;
import org.marshal.weaddon_lightposition.client.overlay.OverlayColorProfile;
import org.marshal.weaddon_lightposition.client.overlay.OverlayColorSlot;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class OverlayColorSettingsScreen extends UiScreen {
    private static final int LEFT_WIDTH = 184;
    private static final int RIGHT_WIDTH = 304;
    private static final int COMPACT_LEFT_WIDTH = 184;
    private static final int COMPACT_RIGHT_WIDTH = 268;
    private static final int TOP_BAR_HEIGHT = 36;
    private static final int MARGIN = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPECTRUM_HEIGHT = 136;
    private static final int SPECTRUM_MIN_HEIGHT = 80;
    private static final int HUE_WIDTH = 16;
    private static final int ALPHA_HEIGHT = 14;
    private static final int COLOR_GRADIENT_STEP = 4;
    private static final int LINE_WIDTH_GROUP_GAP = 10;
    private static final int SLOT_ROW_HEIGHT = 34;

    private final WorldEditPointOverlayClient overlayClient;
    private final Screen parent;
    private final java.util.ArrayDeque<PaletteState> undoStates = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<PaletteState> redoStates = new java.util.ArrayDeque<>();
    private PaletteState paletteEditStart;
    private Object fieldEditTarget;
    private final ArrayList<Button> groupButtons = new ArrayList<>();
    private final UiMotion.Hover[] slotHovers = UiMotion.Hover.states(OverlayColorSlot.values().length);
    private final UiMotion.Hover[] slotResetHovers = UiMotion.Hover.states(OverlayColorSlot.values().length);
    private final UiMotion.Hover[] presetHovers = UiMotion.Hover.states(OverlayPreset.values().length);
    private final UiMotion.Hover[] resetHovers = UiMotion.Hover.states(ResetAction.values().length);
    private final UiMotion.Hover[] dialogHovers = UiMotion.Hover.states(2);

    private ColorGroup selectedGroup = ColorGroup.CUBOID;
    private OverlayColorSlot selectedSlot = OverlayColorSlot.POS1;
    private EditBox hexField;
    private EditBox redField;
    private EditBox greenField;
    private EditBox blueField;
    private EditBox alphaField;
    private Button templateMenuButton;
    private Button resetMenuButton;
    private Button doneButton;
    private float hue;
    private float saturation;
    private float value;
    private boolean updatingFields;
    private boolean draggingSpectrum;
    private boolean draggingHue;
    private boolean draggingAlpha;
    private boolean draggingRed;
    private boolean draggingGreen;
    private boolean draggingBlue;
    private boolean draggingAlphaChannel;
    private boolean draggingLineWidth;
    private boolean draggingGlobalLineWidth;
    private boolean draggingPreview;
    private boolean templateMenuOpen;
    private boolean resetMenuOpen;
    private OverlayPreset pendingPreset;
    private double scrollOffset;
    private double lastPreviewDragX;
    private double lastPreviewDragY;
    private float previewYaw = 35.0f;
    private float previewPitch = 25.0f;
    private float previewZoom = 1.0f;

    public OverlayColorSettingsScreen(WorldEditPointOverlayClient overlayClient, Screen parent) {
        super(Component.translatable("screen.worldedit_selection_overlay.colors.title"));
        this.overlayClient = overlayClient;
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearPaletteDrag();
        finishPaletteEdit();
        groupButtons.clear();
        int groupX = stackedLayout() ? MARGIN : slotRowX();
        int groupY = topBarHeight() + MARGIN;
        for (ColorGroup group : ColorGroup.values()) {
            Button button = addRenderableWidget(
                    UiButton.styled(groupButtonText(group), ignored -> {
                        finishPaletteEdit();
                        selectedGroup = group;
                        selectedSlot = group.slots()[0];
                        syncHsvFromSelectedColor();
                        syncFields();
                        syncGroupButtons();
                        clampScroll();
                        layoutWidgets();
                    }).quiet().selected(() -> selectedGroup == group).bounds(groupX, groupY, leftWidth() - MARGIN, BUTTON_HEIGHT).build()
            );
            groupButtons.add(button);
            groupY += BUTTON_HEIGHT + 6;
        }

        int fieldX = controlsX();
        int fieldY = fieldsY();
        hexField = new UiEditBox(
                font,
                fieldX + 34,
                fieldY,
                96,
                18,
                Component.translatable("screen.worldedit_selection_overlay.colors.hex")
        );
        hexField.setMaxLength(9);
        hexField.setResponder(this::onHexChanged);
        addRenderableWidget(hexField);

        redField = channelField(channelFieldX(0), fieldY + 28, this::onRgbFieldsChanged);
        greenField = channelField(channelFieldX(1), fieldY + 28, this::onRgbFieldsChanged);
        blueField = channelField(channelFieldX(2), fieldY + 28, this::onRgbFieldsChanged);
        alphaField = channelField(channelFieldX(3), fieldY + 28, this::onRgbFieldsChanged);
        addRenderableWidget(redField);
        addRenderableWidget(greenField);
        addRenderableWidget(blueField);
        addRenderableWidget(alphaField);

        templateMenuButton = addRenderableWidget(
                UiButton.styled(Component.translatable("screen.worldedit_selection_overlay.colors.templates_menu"), button -> {
                    templateMenuOpen = !templateMenuOpen;
                    resetMenuOpen = false;
                    setFocused(null);
                }).bounds(templateMenuButtonX(), 8, templateMenuButtonWidth(), BUTTON_HEIGHT).build()
        );
        resetMenuButton = addRenderableWidget(
                UiButton.styled(Component.translatable("screen.worldedit_selection_overlay.colors.reset_menu"), button -> {
                    resetMenuOpen = !resetMenuOpen;
                    templateMenuOpen = false;
                    setFocused(null);
                })
                        .bounds(resetMenuButtonX(), 8, resetMenuButtonWidth(), BUTTON_HEIGHT)
                        .build()
        );
        doneButton = addRenderableWidget(
                UiButton.styled(Component.translatable("screen.worldedit_selection_overlay.colors.done"), button -> onClose())
                        .bounds(width - 96, 8, 84, BUTTON_HEIGHT)
                        .build()
        );

        syncHsvFromSelectedColor();
        syncFields();
        syncGroupButtons();
        layoutWidgets();
    }

    @Override
    protected void drawScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int bodyMouseX = popupBlocksWidgetHover() ? Integer.MIN_VALUE : mouseX;
        int bodyMouseY = popupBlocksWidgetHover() ? Integer.MIN_VALUE : mouseY;
        drawBackdrop(context);
        drawPanel(context);
        if (stackedLayout()) {
            context.enableScissor(0, topBarHeight(), width, height);
            try {
                drawSlotList(context, bodyMouseX, bodyMouseY);
                drawSpectrum(context);
                drawHueSlider(context);
                drawAlphaSlider(context);
                drawChannelSliders(context, bodyMouseX, bodyMouseY);
                drawLineWidthSlider(context, bodyMouseX, bodyMouseY);
                drawPreview(context);
                drawFieldLabels(context);
            } finally {
                context.disableScissor();
            }
        } else {
            drawSlotList(context, bodyMouseX, bodyMouseY);
            drawSpectrum(context);
            drawHueSlider(context);
            drawAlphaSlider(context);
            drawChannelSliders(context, bodyMouseX, bodyMouseY);
            drawLineWidthSlider(context, bodyMouseX, bodyMouseY);
            drawPreview(context);
            drawFieldLabels(context);
        }
        drawGlobalLineWidthSlider(context, bodyMouseX, bodyMouseY);
        int widgetMouseX = popupBlocksWidgetHover() ? Integer.MIN_VALUE : mouseX;
        int widgetMouseY = popupBlocksWidgetHover() ? Integer.MIN_VALUE : mouseY;
        renderWidgets(context, widgetMouseX, widgetMouseY, delta);
        drawTemplateMenu(context, mouseX, mouseY);
        drawResetMenu(context, mouseX, mouseY);
        drawPresetConfirmation(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (isClosing()) return true;
        if (!paletteDragging()) finishPaletteEdit();
        if (click.button() == 0) {
            if (pendingPreset != null) {
                return clickPresetConfirmation(click.x(), click.y());
            }
            if (tryClickTemplateMenu(click.x(), click.y())) {
                return true;
            }
            if (isInsideTemplateMenuButton(click.x(), click.y())) {
                templateMenuOpen = !templateMenuOpen;
                resetMenuOpen = false;
                setFocused(null);
                return true;
            }
            if (templateMenuOpen) {
                templateMenuOpen = false;
                return true;
            }
            if (tryClickResetMenu(click.x(), click.y())) {
                return true;
            }
            if (isInsideResetMenuButton(click.x(), click.y())) {
                resetMenuOpen = !resetMenuOpen;
                templateMenuOpen = false;
                setFocused(null);
                return true;
            }
            if (resetMenuOpen) {
                resetMenuOpen = false;
                return true;
            }
            if (isGlobalLineWidthEnabled() && isInsideGlobalLineWidthSlider(click.x(), click.y())) {
                draggingGlobalLineWidth = true;
                updateGlobalLineWidthScale(click.x(), false);
                return true;
            }
            if (tryResetSlot(click.x(), click.y())) {
                return true;
            }
            if (trySelectSlot(click.x(), click.y())) {
                return true;
            }
            if (isInsidePreview(click.x(), click.y())) {
                draggingPreview = true;
                lastPreviewDragX = click.x();
                lastPreviewDragY = click.y();
                return true;
            }
            if (isInsideSpectrum(click.x(), click.y())) {
                draggingSpectrum = true;
                updateSpectrum(click.x(), click.y(), false);
                return true;
            }
            if (isInsideHue(click.x(), click.y())) {
                draggingHue = true;
                updateHue(click.y(), false);
                return true;
            }
            if (isInsideAlpha(click.x(), click.y())) {
                draggingAlpha = true;
                updateAlpha(click.x(), false);
                return true;
            }
            if (isInsideChannelSlider(redSliderX(), redSliderY(), click.x(), click.y())) {
                draggingRed = true;
                updateChannel(redSliderX(), click.x(), Channel.RED, false);
                return true;
            }
            if (isInsideChannelSlider(redSliderX(), redSliderY() + 24, click.x(), click.y())) {
                draggingGreen = true;
                updateChannel(redSliderX(), click.x(), Channel.GREEN, false);
                return true;
            }
            if (isInsideChannelSlider(redSliderX(), redSliderY() + 48, click.x(), click.y())) {
                draggingBlue = true;
                updateChannel(redSliderX(), click.x(), Channel.BLUE, false);
                return true;
            }
            if (isInsideChannelSlider(redSliderX(), redSliderY() + 72, click.x(), click.y())) {
                draggingAlphaChannel = true;
                updateChannel(redSliderX(), click.x(), Channel.ALPHA, false);
                return true;
            }
            if (selectedSlot.supportsLineWidth() && isInsideLineWidthSlider(click.x(), click.y())) {
                draggingLineWidth = true;
                updateLineWidthScale(click.x(), false);
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (isClosing()) return true;
        if (!popupBlocksWidgetHover() && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                && (input.modifiers() & GLFW.GLFW_MOD_ALT) == 0
                && (input.key() == GLFW.GLFW_KEY_Z || input.key() == GLFW.GLFW_KEY_Y)) {
            replayPaletteEdit(input.key() == GLFW.GLFW_KEY_Y || (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_TAB || input.key() == GLFW.GLFW_KEY_ENTER) finishPaletteEdit();
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (pendingPreset != null) {
                pendingPreset = null;
                return true;
            }
            if (templateMenuOpen) {
                templateMenuOpen = false;
                return true;
            }
            if (resetMenuOpen) {
                resetMenuOpen = false;
                return true;
            }
        }
        return popupBlocksWidgetHover() || super.keyPressed(input);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return pendingPreset == null && !templateMenuOpen && !resetMenuOpen && super.shouldCloseOnEsc();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (click.button() == 0) {
            if (draggingPreview) {
                double deltaX = click.x() - lastPreviewDragX;
                double deltaY = click.y() - lastPreviewDragY;
                lastPreviewDragX = click.x();
                lastPreviewDragY = click.y();
                previewYaw = normalizeYaw(previewYaw - (float) deltaX * 0.38f);
                previewPitch = Math.max(-65.0f, Math.min(65.0f, previewPitch + (float) deltaY * 0.38f));
                return true;
            }
            if (draggingSpectrum) {
                updateSpectrum(click.x(), click.y(), false);
                return true;
            }
            if (draggingHue) {
                updateHue(click.y(), false);
                return true;
            }
            if (draggingAlpha) {
                updateAlpha(click.x(), false);
                return true;
            }
            if (draggingRed) {
                updateChannel(redSliderX(), click.x(), Channel.RED, false);
                return true;
            }
            if (draggingGreen) {
                updateChannel(redSliderX(), click.x(), Channel.GREEN, false);
                return true;
            }
            if (draggingBlue) {
                updateChannel(redSliderX(), click.x(), Channel.BLUE, false);
                return true;
            }
            if (draggingAlphaChannel) {
                updateChannel(redSliderX(), click.x(), Channel.ALPHA, false);
                return true;
            }
            if (draggingLineWidth) {
                updateLineWidthScale(click.x(), false);
                return true;
            }
            if (draggingGlobalLineWidth) {
                updateGlobalLineWidthScale(click.x(), false);
                return true;
            }
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() != 0) return super.mouseReleased(click);
        if (paletteDragging()) {
            clearPaletteDrag();
            finishPaletteEdit();
            return true;
        }
        if (draggingPreview) {
            draggingPreview = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isClosing()) return true;
        if (popupBlocksWidgetHover()) return true;
        if (stackedLayout() && maxScrollOffset() > 0.0 && pendingPreset == null && !templateMenuOpen && !resetMenuOpen) {
            scrollOffset = Math.max(0.0, Math.min(maxScrollOffset(), scrollOffset - verticalAmount * 28.0));
            layoutWidgets();
            return true;
        }
        if (isInsidePreview(mouseX, mouseY)) {
            float direction = verticalAmount > 0.0 ? 1.0f : -1.0f;
            previewZoom = Math.max(0.62f, Math.min(1.85f, previewZoom + direction * 0.08f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        clearPaletteDrag();
        finishPaletteEdit();
        closeAnimated(() -> { if (minecraft != null) minecraft.gui.setScreen(parent); });
    }

    private record PaletteState(List<Integer> colors, List<Float> widths, float globalWidth) { }

    private PaletteState paletteState() {
        ArrayList<Integer> colors = new ArrayList<>();
        ArrayList<Float> widths = new ArrayList<>();
        for (OverlayColorSlot slot : OverlayColorSlot.values()) {
            colors.add(overlayClient.overlayColor(slot));
            widths.add(overlayClient.overlayLineWidthScale(slot));
        }
        return new PaletteState(List.copyOf(colors), List.copyOf(widths), overlayClient.lineWidthScale());
    }

    private void beginPaletteEdit() {
        if (paletteEditStart == null) paletteEditStart = paletteState();
    }

    private void beginFieldEdit() {
        if (paletteEditStart != null && fieldEditTarget != getFocused()) finishPaletteEdit();
        fieldEditTarget = getFocused();
        beginPaletteEdit();
    }

    private void finishPaletteEdit() {
        if (paletteEditStart == null) return;
        PaletteState current = paletteState();
        if (!current.equals(paletteEditStart)) {
            undoStates.addLast(paletteEditStart);
            if (undoStates.size() > 128) undoStates.removeFirst();
            redoStates.clear();
            applyPaletteState(current);
        }
        paletteEditStart = null;
        fieldEditTarget = null;
    }

    private void applyPaletteState(PaletteState state) {
        OverlayColorProfile profile = OverlayColorProfile.defaults();
        OverlayColorSlot[] slots = OverlayColorSlot.values();
        for (int i = 0; i < slots.length; i++) {
            profile.setColor(slots[i], state.colors().get(i));
            profile.setLineWidthScale(slots[i], state.widths().get(i));
        }
        overlayClient.applyOverlayColorProfile(profile, state.globalWidth());
        syncHsvFromSelectedColor();
        syncFields();
    }

    private boolean paletteDragging() {
        return draggingSpectrum || draggingHue || draggingAlpha || draggingRed || draggingGreen || draggingBlue
                || draggingAlphaChannel || draggingLineWidth || draggingGlobalLineWidth;
    }

    private void clearPaletteDrag() {
        draggingSpectrum = draggingHue = draggingAlpha = draggingRed = draggingGreen = draggingBlue =
                draggingAlphaChannel = draggingLineWidth = draggingGlobalLineWidth = false;
    }

    private void replayPaletteEdit(boolean redo) {
        clearPaletteDrag();
        finishPaletteEdit();
        java.util.ArrayDeque<PaletteState> from = redo ? redoStates : undoStates;
        java.util.ArrayDeque<PaletteState> to = redo ? undoStates : redoStates;
        if (!from.isEmpty()) {
            to.addLast(paletteState());
            applyPaletteState(from.removeLast());
        } else {
            syncFields();
        }
    }

    @Override
    public void removed() {
        clearPaletteDrag();
        finishPaletteEdit();
        super.removed();
    }

    private EditBox channelField(int x, int y, java.util.function.Consumer<String> listener) {
        EditBox field = new UiEditBox(font, x, y, channelFieldWidth(), 18, Component.empty());
        field.setMaxLength(3);
        field.setResponder(listener);
        return field;
    }

    private void layoutWidgets() {
        int groupButtonWidth = groupButtonWidth();
        for (int i = 0; i < groupButtons.size() && i < ColorGroup.values().length; i++) {
            Button button = groupButtons.get(i);
            int x;
            int y;
            if (stackedLayout()) {
                int columns = stackedGroupColumns();
                x = MARGIN + (i % columns) * (groupButtonWidth + 6);
                y = stackedGroupY() + (i / columns) * (BUTTON_HEIGHT + 6);
            } else {
                x = slotRowX();
                y = topBarHeight() + MARGIN + i * (BUTTON_HEIGHT + 6);
            }
            button.setRectangle(groupButtonWidth, BUTTON_HEIGHT, x, y);
            button.visible = !stackedLayout() || isWidgetVisibleInContent(y, BUTTON_HEIGHT);
        }

        int fieldY = fieldsY();
        hexField.setRectangle(Math.max(70, controlsWidth() - 34), 18, controlsX() + 34, fieldY);
        redField.setRectangle(channelFieldWidth(), 18, channelFieldX(0), fieldY + 28);
        greenField.setRectangle(channelFieldWidth(), 18, channelFieldX(1), fieldY + 28);
        blueField.setRectangle(channelFieldWidth(), 18, channelFieldX(2), fieldY + 28);
        alphaField.setRectangle(channelFieldWidth(), 18, channelFieldX(3), fieldY + 28);
        hexField.visible = !stackedLayout() || isWidgetVisibleInContent(fieldY, 18);
        boolean channelsVisible = !stackedLayout() || isWidgetVisibleInContent(fieldY + 28, 18);
        redField.visible = channelsVisible;
        greenField.visible = channelsVisible;
        blueField.visible = channelsVisible;
        alphaField.visible = channelsVisible;

        templateMenuButton.setRectangle(templateMenuButtonWidth(), BUTTON_HEIGHT, templateMenuButtonX(), topButtonY());
        resetMenuButton.setRectangle(resetMenuButtonWidth(), BUTTON_HEIGHT, resetMenuButtonX(), topButtonY());
        doneButton.setRectangle(doneButtonWidth(), BUTTON_HEIGHT, doneButtonX(), topButtonY());
        templateMenuButton.visible = true;
        resetMenuButton.visible = true;
        doneButton.visible = true;
    }

    private boolean isWidgetVisibleInContent(int y, int widgetHeight) {
        return y >= topBarHeight() && y + widgetHeight <= height;
    }

    private void drawBackdrop(GuiGraphicsExtractor context) {
        UiChrome.fill(context, 0, 0, width, height, 0x78000000);
    }

    private void drawPanel(GuiGraphicsExtractor context) {
        UiStyle style = UiStyle.current();
        int leftWidth = leftWidth();
        int rightWidth = rightWidth();
        UiChrome.fill(context, 0, 0, width, height, style.surface);
        if (stackedLayout()) {
            UiChrome.fill(context, 0, 0, width, topBarHeight(), style.raised);
            if (style == UiStyle.LEGACY) drawOutline(context, 0, 0, width, topBarHeight(), style.border);
            UiChrome.text(context, font, title, MARGIN, 11, style.text, false);
            drawScrollHint(context);
            return;
        }

        UiChrome.fill(context, 0, 0, width, topBarHeight(), style.raised);
        if (style == UiStyle.LEGACY) drawOutline(context, 0, 0, width, topBarHeight(), style.border);
        UiChrome.fill(context, 0, topBarHeight(), leftWidth, height, style.surface);
        UiChrome.fill(context, width - rightWidth, topBarHeight(), width, height, style.surface);
        UiChrome.text(context, font, title, MARGIN, 11, style.text, false);
        int subtitleX = Math.max(leftWidth + MARGIN, templateMenuButtonX() + templateMenuButtonWidth() + 10);
        int subtitleMaxWidth = resetMenuButtonX() - subtitleX - 10;
        if (subtitleMaxWidth >= 88) {
            drawTrimmedText(
                    context,
                    Component.translatable("screen.worldedit_selection_overlay.colors.subtitle"),
                    subtitleX,
                    13,
                    subtitleMaxWidth,
                    style.secondaryText
            );
        }
    }

    private void drawSlotList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int containerX = slotListContainerX();
        int containerWidth = slotListContainerWidth();
        int rowX = slotRowX();
        int rowWidth = slotRowWidth();
        int x = containerX + 8;
        int y = slotListY();
        UiChrome.text(context,
                font,
                Component.translatable("screen.worldedit_selection_overlay.colors.elements"),
                containerX + 6,
                y - 16,
                0xFFB4C0D3,
                false
        );
        for (OverlayColorSlot slot : selectedGroup.slots()) {
            boolean selected = slot == selectedSlot;
            boolean hovered = isInside(rowX, y - 3, rowWidth, SLOT_ROW_HEIGHT, mouseX, mouseY);
            boolean resetHovered = isInsideSlotResetButton(y, mouseX, mouseY);
            float rowHover = slotHovers[slot.ordinal()].value(hovered);
            int rowColor = selected ? UiStyle.current().raised : UiChrome.blend(0x0035403F, 0xAA35403F, rowHover);
            UiChrome.fillRounded(context, rowX, y - 3, rowWidth, SLOT_ROW_HEIGHT, rowColor);
            if (selected) {
                UiChrome.fill(context, rowX, y - 3, rowX + 3, y + SLOT_ROW_HEIGHT - 3, UiStyle.current().accent);
            }
            drawSwatch(context, x + 2, y + 7, 12, 12, overlayClient.overlayColor(slot));
            int resetX = slotResetButtonX();
            int resetWidth = slotResetButtonWidth();
            drawWrappedSlotText(context, Component.translatable(slot.translationKey()), x + 20, y, resetX - x - 26, 0xFFE4EAF5);
            int resetFill = UiChrome.blend(UiStyle.current().raised, UiStyle.current().accent, slotResetHovers[slot.ordinal()].value(resetHovered) * 0.22f);
            int resetY = y + 6;
            UiChrome.fillRounded(context, resetX, resetY, resetWidth, 18, resetFill);
            drawCenteredTrimmedText(context, Component.translatable("screen.worldedit_selection_overlay.colors.reset_element"), resetX, resetY + 5, resetWidth, 0xFFE4EAF5);
            y += SLOT_ROW_HEIGHT;
        }
    }

    private void drawSpectrum(GuiGraphicsExtractor context) {
        int x = spectrumX();
        int y = spectrumY();
        int spectrumWidth = spectrumWidth();
        int spectrumHeight = spectrumHeight();
        int alpha = alpha(selectedColor());
        UiChrome.spectrum(context, x, y, spectrumWidth, spectrumHeight, hsvToArgb(hue, 1, 1, 255));
        int thumbX = x + Math.round(saturation * (spectrumWidth - 1));
        int thumbY = y + Math.round((1.0f - value) * (spectrumHeight - 1));
        drawCrosshair(context, thumbX, thumbY, alpha < 120 ? 0xFFFFFFFF : 0xFF0B0E14);
    }

    private void drawHueSlider(GuiGraphicsExtractor context) {
        int x = hueX();
        int y = spectrumY();
        int spectrumHeight = spectrumHeight();
        int hueWidth = hueWidth();
        for (int py = 0; py < spectrumHeight; py += COLOR_GRADIENT_STEP) {
            float localHue = (float) py / (float) (spectrumHeight - 1);
            UiChrome.fill(context, x, y + py, x + hueWidth, y + Math.min(spectrumHeight, py + COLOR_GRADIENT_STEP), hsvToArgb(localHue, 1.0f, 1.0f, 255));
        }
        int markerY = y + 1 + Math.round(hue * (spectrumHeight - 3));
        UiChrome.fill(context, x, markerY - 1, x + hueWidth, markerY + 2, 0xFFFFFFFF);
        UiChrome.fill(context, x + 1, markerY, x + hueWidth - 1, markerY + 1, 0xFF0B0E14);
    }

    private void drawAlphaSlider(GuiGraphicsExtractor context) {
        int x = spectrumX();
        int y = alphaY();
        int color = selectedColor();
        int sliderWidth = alphaSliderWidth();
        drawCheckerboard(context, x, y, sliderWidth, ALPHA_HEIGHT, 4);
        int rgb = color & 0x00FFFFFF;
        for (int px = 0; px < sliderWidth; px += COLOR_GRADIENT_STEP) {
            int a = Math.round((float) px / (float) (sliderWidth - 1) * 255.0f);
            UiChrome.fill(context, x + px, y, x + Math.min(sliderWidth, px + COLOR_GRADIENT_STEP), y + ALPHA_HEIGHT, (a << 24) | rgb);
        }
        int markerX = x + 1 + Math.round((float) alpha(color) / 255.0f * (sliderWidth - 3));
        UiChrome.fill(context, markerX - 1, y, markerX + 2, y + ALPHA_HEIGHT, 0xFFFFFFFF);
        UiChrome.fill(context, markerX, y + 1, markerX + 1, y + ALPHA_HEIGHT - 1, 0xFF0B0E14);
    }

    private void drawChannelSliders(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int color = selectedColor();
        drawChannelSlider(context, "R", redSliderX(), redSliderY(), red(color), 0xFFFF5C6A, mouseX, mouseY);
        drawChannelSlider(context, "G", redSliderX(), redSliderY() + 24, green(color), 0xFF5DDB8F, mouseX, mouseY);
        drawChannelSlider(context, "B", redSliderX(), redSliderY() + 48, blue(color), 0xFF65A7FF, mouseX, mouseY);
        drawChannelSlider(context, "A", redSliderX(), redSliderY() + 72, alpha(color), 0xFFE7EDF8, mouseX, mouseY);
    }

    private void drawPreview(GuiGraphicsExtractor context) {
        int x = previewX();
        int y = previewY();
        int previewWidth = previewWidth();
        int previewHeight = previewHeight();
        OverlaySelectionPreviewRenderer.render(
                context,
                selectedGroup,
                x,
                y,
                previewWidth,
                previewHeight,
                previewYaw,
                previewPitch,
                previewZoom,
                overlayClient.renderMode()
        );

        int swatchX = controlsX();
        int swatchY = selectedInfoY();
        drawCheckerboard(context, swatchX, swatchY, 48, 48, 6);
        drawSwatch(context, swatchX, swatchY, 48, 48, selectedColor());
        int textX = swatchX + 60;
        int maxTextWidth = Math.max(60, controlsWidth() - 64);
        drawTrimmedText(context, Component.translatable(selectedGroup.translationKey()), textX, swatchY + 4, maxTextWidth, 0xFFEAF0FA);
        drawTrimmedText(context, Component.translatable(selectedSlot.translationKey()), textX, swatchY + 18, maxTextWidth, 0xFFE4EAF5);
        drawTrimmedText(context, Component.literal(OverlayColorProfile.formatColor(selectedColor())), textX, swatchY + 32, maxTextWidth, 0xFFB4C0D3);
    }

    private void drawFieldLabels(GuiGraphicsExtractor context) {
        int x = controlsX();
        int y = fieldsY();
        if (hexField.visible) UiChrome.text(context, font, Component.translatable("screen.worldedit_selection_overlay.colors.hex"), x, y + 5, 0xFFB4C0D3, false);
        if (!redField.visible) return;
        UiChrome.text(context, font, "R", channelLabelX(0), y + 33, 0xFFFFA3AD, false);
        UiChrome.text(context, font, "G", channelLabelX(1), y + 33, 0xFF9BEFBC, false);
        UiChrome.text(context, font, "B", channelLabelX(2), y + 33, 0xFFA6C8FF, false);
        UiChrome.text(context, font, "A", channelLabelX(3), y + 33, 0xFFE7EDF8, false);
    }

    private void drawChannelSlider(GuiGraphicsExtractor context, String label, int x, int y, int value, int accent, int mouseX, int mouseY) {
        int labelColor = isInsideChannelSlider(x, y, mouseX, mouseY) ? 0xFFFFFFFF : 0xFFB4C0D3;
        UiChrome.text(context, font, label, x, y + 2, labelColor, false);
        int trackX = x + 18;
        int trackWidth = Math.max(80, controlsWidth() - 18);
        UiChrome.slider(context, trackX, y, trackWidth, value / 255.0f, accent, true);
    }

    private void drawLineWidthSlider(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!selectedSlot.supportsLineWidth()) {
            return;
        }
        int x = redSliderX();
        int y = lineWidthSliderY();
        int trackX = x + 18;
        int trackWidth = Math.max(80, controlsWidth() - 18);
        float scale = overlayClient.overlayLineWidthScale(selectedSlot);
        int fillWidth = Math.round(trackWidth * ((scale - 0.5f) / 1.5f));
        int labelColor = isInsideLineWidthSlider(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFB4C0D3;
        UiChrome.text(context,
                font,
                Component.translatable("screen.worldedit_selection_overlay.colors.line_width", Math.round(scale * 100.0f)),
                x,
                y - 10,
                labelColor,
                false
        );
        UiChrome.slider(context, trackX, y, trackWidth, (scale - 0.5f) / 1.5f, UiStyle.current().accent, true);
    }

    private void drawGlobalLineWidthSlider(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int x = globalLineWidthSliderX();
        int y = globalLineWidthSliderY();
        int trackWidth = globalLineWidthSliderWidth();
        boolean enabled = isGlobalLineWidthEnabled();
        float scale = overlayClient.commonOverlayLineWidthScale();
        int fillWidth = Math.round(trackWidth * ((scale - 0.5f) / 1.5f));
        int textColor = enabled ? 0xFFB4C0D3 : 0xFF667184;
        int trackColor = enabled ? 0xFF222B39 : 0xFF151B25;
        int fillColor = enabled ? 0xFF8AB4FF : 0xFF3D4A5F;
        int thumbOuter = enabled ? 0xFFEAF0FA : 0xFF718097;
        int thumbInner = enabled ? 0xFF0B0E14 : 0xFF1E2633;

        drawTrimmedText(
                context,
                enabled
                        ? Component.translatable("screen.worldedit_selection_overlay.colors.global_line_width", Math.round(scale * 100.0f))
                        : Component.translatable("screen.worldedit_selection_overlay.colors.global_line_width_mixed"),
                x,
                stackedLayout() ? y - 9 : 6,
                trackWidth,
                textColor
        );
        UiChrome.slider(context, x, y + 8, trackWidth, (scale - 0.5f) / 1.5f, UiStyle.current().accent, enabled);
    }

    private void drawResetMenu(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (resetMenuOpen) {
            drawOutline(context, resetMenuButtonX(), topButtonY(), resetMenuButtonWidth(), BUTTON_HEIGHT, UiStyle.current().accent);
            int x = resetMenuX();
            int y = resetMenuY();
            int width = resetMenuWidth();
            UiChrome.popup(context, x, y, width, resetMenuHeight(), UiStyle.current());
            drawOutline(context, x, y, width, resetMenuHeight(), UiStyle.current().accent);
            drawResetMenuItem(context, ResetAction.COLORS, mouseX, mouseY);
            drawResetMenuItem(context, ResetAction.WIDTHS, mouseX, mouseY);
            drawResetMenuItem(context, ResetAction.ALL, mouseX, mouseY);
        }
    }

    private void drawResetMenuItem(GuiGraphicsExtractor context, ResetAction action, int mouseX, int mouseY) {
        int x = resetMenuX();
        int y = resetMenuItemY(action);
        int width = resetMenuWidth();
        boolean hovered = isInside(x, y, width, resetMenuItemHeight(), mouseX, mouseY);
        UiChrome.fillRounded(context, x + 4, y + 1, width - 8, resetMenuItemHeight() - 2, UiChrome.blend(0x0035403F, 0xAA35403F, resetHovers[action.ordinal()].value(hovered)));
        drawTrimmedText(context, Component.translatable(action.translationKey()), x + 8, y + 6, width - 16, 0xFFE4EAF5);
    }

    private void drawTemplateMenu(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (templateMenuOpen) {
            drawOutline(context, templateMenuButtonX(), topButtonY(), templateMenuButtonWidth(), BUTTON_HEIGHT, UiStyle.current().accent);
            int x = templateMenuX();
            int y = templateMenuY();
            int width = templateMenuWidth();
            UiChrome.popup(context, x, y, width, templateMenuHeight(), UiStyle.current());
            drawOutline(context, x, y, width, templateMenuHeight(), UiStyle.current().accent);
            for (OverlayPreset preset : OverlayPreset.values()) {
                drawTemplateMenuItem(context, preset, mouseX, mouseY);
            }
        }
    }

    private void drawTemplateMenuItem(GuiGraphicsExtractor context, OverlayPreset preset, int mouseX, int mouseY) {
        int x = templateMenuX();
        int y = templateMenuItemY(preset);
        int width = templateMenuWidth();
        boolean hovered = isInside(x, y, width, templateMenuItemHeight(), mouseX, mouseY);
        UiChrome.fillRounded(context, x + 4, y + 1, width - 8, templateMenuItemHeight() - 2, UiChrome.blend(0x0035403F, 0xAA35403F, presetHovers[preset.ordinal()].value(hovered)));
        drawPresetSwatches(context, preset, x + 8, y + 8);
        drawTrimmedText(context, preset.nameText(), x + 48, y + 5, width - 56, 0xFFE4EAF5);
        drawTrimmedText(context, preset.descriptionText(), x + 48, y + 17, width - 56, 0xFF8E9AAF);
    }

    private void drawPresetSwatches(GuiGraphicsExtractor context, OverlayPreset preset, int x, int y) {
        OverlayColorProfile profile = preset.profile();
        OverlayColorSlot[] slots = {
                OverlayColorSlot.CUBOID_EDGE,
                OverlayColorSlot.CUBOID_GRID,
                OverlayColorSlot.POS1,
                OverlayColorSlot.POS2,
                OverlayColorSlot.RULER_LINE
        };
        for (int i = 0; i < slots.length; i++) {
            int swatchX = x + i * 7;
            UiChrome.fillRounded(context, swatchX, y, 6, 16, profile.color(slots[i]));
        }
    }

    private void drawPresetConfirmation(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (pendingPreset == null) {
            return;
        }
        UiChrome.fill(context, 0, 0, width, height, 0x99000000);
        int x = confirmDialogX();
        int y = confirmDialogY();
        int dialogWidth = confirmDialogWidth();
        int dialogHeight = confirmDialogHeight();
        UiChrome.popup(context, x, y, dialogWidth, dialogHeight, UiStyle.current());
        drawOutline(context, x, y, dialogWidth, dialogHeight, UiStyle.current().accent);
        drawTrimmedText(
                context,
                Component.translatable("screen.worldedit_selection_overlay.colors.template_confirm_title"),
                x + 16,
                y + 14,
                dialogWidth - 32,
                0xFFEAF0FA
        );
        drawWrappedDialogText(
                context,
                Component.translatable("screen.worldedit_selection_overlay.colors.template_confirm_body", pendingPreset.nameText()),
                x + 16,
                y + 36,
                dialogWidth - 32,
                0xFFB4C0D3
        );
        drawDialogButton(context, confirmApplyX(), confirmButtonsY(), confirmButtonWidth(), confirmButtonHeight(), Component.translatable("screen.worldedit_selection_overlay.colors.template_apply"), 0xFF22324A, isInsideConfirmApply(mouseX, mouseY));
        drawDialogButton(context, confirmCancelX(), confirmButtonsY(), confirmButtonWidth(), confirmButtonHeight(), Component.translatable("screen.worldedit_selection_overlay.colors.template_cancel"), 0xFF172130, isInsideConfirmCancel(mouseX, mouseY));
    }

    private void drawDialogButton(GuiGraphicsExtractor context, int x, int y, int width, int height, Component text, int fill, boolean hovered) {
        float progress = dialogHovers[x == confirmApplyX() ? 0 : 1].value(hovered);
        UiChrome.fillRounded(context, x, y, width, height, UiChrome.blend(fill, UiStyle.current().accent, progress * 0.22f));
        drawCenteredTrimmedText(context, text, x, y + 7, width, 0xFFE4EAF5);
    }

    private void drawScrollHint(GuiGraphicsExtractor context) {
        double maxScroll = maxScrollOffset();
        if (maxScroll <= 0.0) {
            return;
        }
        int trackX = width - 5;
        int trackY = topBarHeight() + 4;
        int trackHeight = Math.max(24, height - topBarHeight() - 8);
        int thumbHeight = Math.max(18, (int) Math.round(trackHeight * Math.min(1.0, (height - topBarHeight()) / (double) (stackedContentBottom() - topBarHeight()))));
        int thumbY = trackY + (int) Math.round((trackHeight - thumbHeight) * (scrollOffset / maxScroll));
        UiChrome.fill(context, trackX, trackY, trackX + 2, trackY + trackHeight, 0x55384555);
        UiChrome.fill(context, trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, UiStyle.current().accent);
    }

    private boolean trySelectSlot(double mouseX, double mouseY) {
        int y = slotListY();
        for (OverlayColorSlot slot : selectedGroup.slots()) {
            if (isInside(slotRowX(), y - 3, slotRowWidth(), SLOT_ROW_HEIGHT, mouseX, mouseY)) {
                selectedSlot = slot;
                syncHsvFromSelectedColor();
                syncFields();
                return true;
            }
            y += SLOT_ROW_HEIGHT;
        }
        return false;
    }

    private boolean tryResetSlot(double mouseX, double mouseY) {
        int y = slotListY();
        for (OverlayColorSlot slot : selectedGroup.slots()) {
            if (isInsideSlotResetButton(y, mouseX, mouseY)) {
                beginPaletteEdit();
                overlayClient.resetOverlayColor(slot);
                finishPaletteEdit();
                if (slot == selectedSlot) {
                    syncHsvFromSelectedColor();
                    syncFields();
                }
                return true;
            }
            y += SLOT_ROW_HEIGHT;
        }
        return false;
    }

    private boolean tryClickResetMenu(double mouseX, double mouseY) {
        if (!resetMenuOpen) {
            return false;
        }
        for (ResetAction action : ResetAction.values()) {
            if (isInside(resetMenuX(), resetMenuItemY(action), resetMenuWidth(), resetMenuItemHeight(), mouseX, mouseY)) {
                beginPaletteEdit();
                switch (action) {
                    case COLORS -> overlayClient.resetAllOverlayColors();
                    case WIDTHS -> overlayClient.resetAllOverlayLineWidths();
                    case ALL -> overlayClient.resetAllOverlayElementSettings();
                }
                finishPaletteEdit();
                resetMenuOpen = false;
                syncHsvFromSelectedColor();
                syncFields();
                return true;
            }
        }
        return false;
    }

    private boolean tryClickTemplateMenu(double mouseX, double mouseY) {
        if (!templateMenuOpen) {
            return false;
        }
        for (OverlayPreset preset : OverlayPreset.values()) {
            if (isInside(templateMenuX(), templateMenuItemY(preset), templateMenuWidth(), templateMenuItemHeight(), mouseX, mouseY)) {
                pendingPreset = preset;
                templateMenuOpen = false;
                resetMenuOpen = false;
                return true;
            }
        }
        return false;
    }

    private boolean clickPresetConfirmation(double mouseX, double mouseY) {
        if (isInsideConfirmApply(mouseX, mouseY)) {
            beginPaletteEdit();
            overlayClient.applyOverlayColorProfile(pendingPreset.profile());
            finishPaletteEdit();
            pendingPreset = null;
            syncHsvFromSelectedColor();
            syncFields();
            return true;
        }
        if (isInsideConfirmCancel(mouseX, mouseY)) {
            pendingPreset = null;
            return true;
        }
        return true;
    }

    private void updateSpectrum(double mouseX, double mouseY, boolean save) {
        saturation = clamp01((float) ((mouseX - spectrumX()) / (double) (spectrumWidth() - 1)));
        value = clamp01(1.0f - (float) ((mouseY - spectrumY()) / (double) (spectrumHeight() - 1)));
        applySelectedColor(hsvToArgb(hue, saturation, value, alpha(selectedColor())), save);
    }

    private void updateHue(double mouseY, boolean save) {
        hue = clamp01((float) ((mouseY - spectrumY()) / (double) (spectrumHeight() - 1)));
        applySelectedColor(hsvToArgb(hue, saturation, value, alpha(selectedColor())), save);
    }

    private void updateAlpha(double mouseX, boolean save) {
        int sliderWidth = alphaSliderWidth();
        int alpha = Math.round(clamp01((float) ((mouseX - spectrumX()) / (double) (sliderWidth - 1))) * 255.0f);
        int color = selectedColor();
        applySelectedColor((alpha << 24) | (color & 0x00FFFFFF), save);
    }

    private void updateChannel(int sliderX, double mouseX, Channel channel, boolean save) {
        int sliderWidth = Math.max(80, controlsWidth() - 18);
        int value = Math.round(clamp01((float) ((mouseX - (sliderX + 18)) / (double) sliderWidth)) * 255.0f);
        int color = selectedColor();
        int next = switch (channel) {
            case RED -> argb(alpha(color), value, green(color), blue(color));
            case GREEN -> argb(alpha(color), red(color), value, blue(color));
            case BLUE -> argb(alpha(color), red(color), green(color), value);
            case ALPHA -> argb(value, red(color), green(color), blue(color));
        };
        applySelectedColor(next, save);
    }

    private void updateLineWidthScale(double mouseX, boolean save) {
        beginPaletteEdit();
        int sliderWidth = Math.max(80, controlsWidth() - 18);
        float value = clamp01((float) ((mouseX - (redSliderX() + 18)) / (double) sliderWidth));
        float scale = 0.5f + value * 1.5f;
        if (save) {
            overlayClient.setOverlayLineWidthScale(selectedSlot, scale);
        } else {
            overlayClient.previewOverlayLineWidthScale(selectedSlot, scale);
        }
    }

    private void updateGlobalLineWidthScale(double mouseX, boolean save) {
        beginPaletteEdit();
        int sliderWidth = globalLineWidthSliderWidth();
        float value = clamp01((float) ((mouseX - globalLineWidthSliderX()) / (double) sliderWidth));
        float scale = 0.5f + value * 1.5f;
        if (save) {
            overlayClient.setAllOverlayLineWidthScale(scale);
        } else {
            overlayClient.previewAllOverlayLineWidthScale(scale);
        }
    }

    private void applySelectedColor(int color, boolean save) {
        beginPaletteEdit();
        if (save) {
            overlayClient.setOverlayColor(selectedSlot, color);
        } else {
            overlayClient.previewOverlayColor(selectedSlot, color);
        }
        syncHsvFromColor(color);
        syncFields();
    }

    private void onHexChanged(String value) {
        if (updatingFields) {
            return;
        }
        beginFieldEdit();
        Integer color = OverlayColorProfile.parseColor(value);
        if (color != null) {
            applySelectedColor(color, false);
        }
    }

    private void onRgbFieldsChanged(String ignored) {
        if (updatingFields) {
            return;
        }
        beginFieldEdit();
        Integer r = parseChannel(redField.getValue());
        Integer g = parseChannel(greenField.getValue());
        Integer b = parseChannel(blueField.getValue());
        Integer a = parseChannel(alphaField.getValue());
        if (r == null || g == null || b == null || a == null) {
            return;
        }
        applySelectedColor(argb(a, r, g, b), false);
    }

    private void syncFields() {
        if (hexField == null) {
            return;
        }
        int color = selectedColor();
        updatingFields = true;
        hexField.setValue(OverlayColorProfile.formatColor(color));
        redField.setValue(Integer.toString(red(color)));
        greenField.setValue(Integer.toString(green(color)));
        blueField.setValue(Integer.toString(blue(color)));
        alphaField.setValue(Integer.toString(alpha(color)));
        updatingFields = false;
    }

    private void syncGroupButtons() {
        for (int i = 0; i < groupButtons.size() && i < ColorGroup.values().length; i++) {
            ColorGroup group = ColorGroup.values()[i];
            Button button = groupButtons.get(i);
            button.setMessage(groupButtonText(group));
            button.active = UiStyle.current() != UiStyle.LEGACY || group != selectedGroup;
        }
    }

    private Component groupButtonText(ColorGroup group) {
        Component label = Component.translatable(group.translationKey());
        return UiStyle.current() == UiStyle.LEGACY && group == selectedGroup ? Component.literal("> ").append(label) : label;
    }

    private void syncHsvFromSelectedColor() {
        syncHsvFromColor(selectedColor());
    }

    private void syncHsvFromColor(int color) {
        float[] hsv = rgbToHsv(red(color), green(color), blue(color));
        hue = hsv[1] <= 0.0001f ? hue : hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    private int selectedColor() {
        return overlayClient.overlayColor(selectedSlot);
    }

    private int scrollY(int virtualY) {
        return stackedLayout() ? (int) Math.round(virtualY - scrollOffset) : virtualY;
    }

    private void clampScroll() {
        if (!stackedLayout()) {
            scrollOffset = 0.0;
            return;
        }
        scrollOffset = Math.max(0.0, Math.min(maxScrollOffset(), scrollOffset));
    }

    private double maxScrollOffset() {
        if (!stackedLayout()) {
            return 0.0;
        }
        return Math.max(0, stackedContentBottom() - height + MARGIN);
    }

    private int stackedContentBottom() {
        int controlsY = stackedControlsVirtualY();
        int spectrumBottom = controlsY + 66 + spectrumHeight() + 10 + ALPHA_HEIGHT;
        int fieldsBottom = spectrumBottom + 22 + 28 + 18;
        int slidersBottom = spectrumBottom + 22 + 58 + 96 + LINE_WIDTH_GROUP_GAP + 42;
        return Math.max(fieldsBottom, slidersBottom);
    }

    private int stackedGroupColumns() {
        return width < 390 ? 1 : 2;
    }

    private int groupButtonWidth() {
        if (!stackedLayout()) {
            return slotRowWidth();
        }
        int columns = stackedGroupColumns();
        return Math.max(92, (width - MARGIN * 2 - (columns - 1) * 6) / columns);
    }

    private int stackedGroupY() {
        return scrollY(topBarHeight() + 8 + previewHeight() + 8);
    }

    private int stackedGroupHeight() {
        int rows = (ColorGroup.values().length + stackedGroupColumns() - 1) / stackedGroupColumns();
        return rows * BUTTON_HEIGHT + Math.max(0, rows - 1) * 6;
    }

    private int stackedGroupVirtualBottom() {
        return topBarHeight() + 8 + previewHeight() + 8 + stackedGroupHeight();
    }

    private int stackedSlotListY() {
        return scrollY(stackedGroupVirtualBottom() + 28);
    }

    private int stackedControlsVirtualY() {
        return stackedGroupVirtualBottom() + 28 + selectedGroup.slots().length * SLOT_ROW_HEIGHT + 30;
    }

    private int stackedControlsY() {
        return scrollY(stackedControlsVirtualY());
    }

    private int slotListY() {
        if (stackedLayout()) {
            return stackedSlotListY();
        }
        int groupBottom = topBarHeight() + MARGIN + ColorGroup.values().length * (BUTTON_HEIGHT + 6);
        int desired = Math.max(groupBottom + 18, topBarHeight() + 168);
        int maxY = Math.max(topBarHeight() + 70, height - selectedGroup.slots().length * SLOT_ROW_HEIGHT - 22);
        return Math.min(desired, maxY);
    }

    private int spectrumX() {
        return controlsX();
    }

    private int spectrumY() {
        return selectedInfoY() + 66;
    }

    private int hueX() {
        return spectrumX() + spectrumWidth() + 10;
    }

    private int alphaY() {
        return spectrumY() + spectrumHeight() + 10;
    }

    private int previewX() {
        if (stackedLayout()) {
            return MARGIN;
        }
        return leftWidth() + MARGIN;
    }

    private int previewY() {
        if (stackedLayout()) {
            return scrollY(topBarHeight() + 8);
        }
        return topBarHeight() + MARGIN;
    }

    private int previewWidth() {
        if (stackedLayout()) {
            return Math.max(120, width - MARGIN * 2);
        }
        return Math.max(120, width - leftWidth() - rightWidth() - MARGIN * 2);
    }

    private int previewHeight() {
        if (stackedLayout()) {
            return Math.max(82, Math.min(128, height / 3 + 2));
        }
        return Math.max(150, height - topBarHeight() - MARGIN * 2);
    }

    private int topControlGroupWidth() {
        int maxWidth = Math.max(resetMenuButtonWidth(), previewWidth() - 12);
        int desiredWidth = resetMenuButtonWidth() + 12 + globalLineWidthDesiredWidth();
        return Math.min(maxWidth, desiredWidth);
    }

    private int resetMenuButtonX() {
        if (stackedLayout()) {
            return templateMenuButtonX() + templateMenuButtonWidth() + 6;
        }
        return previewX() + Math.max(6, (previewWidth() - topControlGroupWidth()) / 2);
    }

    private int topButtonY() {
        return stackedLayout() ? 34 : 8;
    }

    private int doneButtonWidth() {
        return stackedLayout() ? 52 : 84;
    }

    private int doneButtonX() {
        return width - doneButtonWidth() - MARGIN;
    }

    private int templateMenuButtonX() {
        if (stackedLayout()) {
            return MARGIN;
        }
        return MARGIN + UiChrome.width(font, title) + 14;
    }

    private int templateMenuButtonWidth() {
        if (stackedLayout()) {
            int reserved = resetMenuButtonWidth() + doneButtonWidth() + 64 + MARGIN * 2 + 28;
            return Math.max(62, Math.min(86, width - reserved));
        }
        int available = resetMenuButtonX() - templateMenuButtonX() - 8;
        int desired = compactLayout() ? 82 : 106;
        return Math.max(64, Math.min(desired, available));
    }

    private int resetMenuButtonWidth() {
        if (stackedLayout()) {
            return 84;
        }
        return compactLayout() ? 96 : 112;
    }

    private int globalLineWidthDesiredWidth() {
        return compactLayout() ? 138 : 190;
    }

    private int globalLineWidthSliderX() {
        if (stackedLayout()) {
            return resetMenuButtonX() + resetMenuButtonWidth() + 8;
        }
        return resetMenuButtonX() + resetMenuButtonWidth() + 12;
    }

    private int globalLineWidthSliderY() {
        return stackedLayout() ? 34 : 8;
    }

    private int globalLineWidthSliderWidth() {
        if (stackedLayout()) {
            return Math.max(54, doneButtonX() - globalLineWidthSliderX() - 8);
        }
        int available = topControlGroupWidth() - resetMenuButtonWidth() - 12;
        return Math.max(70, available);
    }

    private int resetMenuX() {
        return Math.min(resetMenuButtonX(), width - resetMenuWidth() - MARGIN);
    }

    private int resetMenuY() {
        return topBarHeight() - 1;
    }

    private int resetMenuWidth() {
        return compactLayout() ? 184 : 218;
    }

    private int resetMenuHeight() {
        return resetMenuItemHeight() * ResetAction.values().length + 2;
    }

    private int resetMenuItemHeight() {
        return 24;
    }

    private int resetMenuItemY(ResetAction action) {
        return resetMenuY() + 1 + action.ordinal() * resetMenuItemHeight();
    }

    private int templateMenuX() {
        return Math.min(templateMenuButtonX(), width - templateMenuWidth() - MARGIN);
    }

    private int templateMenuY() {
        return topBarHeight() - 1;
    }

    private int templateMenuWidth() {
        return compactLayout() ? 210 : 240;
    }

    private int templateMenuHeight() {
        return templateMenuItemHeight() * OverlayPreset.values().length + 2;
    }

    private int templateMenuItemHeight() {
        return 38;
    }

    private int templateMenuItemY(OverlayPreset preset) {
        return templateMenuY() + 1 + preset.ordinal() * templateMenuItemHeight();
    }

    private int confirmDialogWidth() {
        return Math.min(360, Math.max(260, width - 48));
    }

    private int confirmDialogHeight() {
        return 122;
    }

    private int confirmDialogX() {
        return (width - confirmDialogWidth()) / 2;
    }

    private int confirmDialogY() {
        return (height - confirmDialogHeight()) / 2;
    }

    private int confirmButtonWidth() {
        return 112;
    }

    private int confirmButtonHeight() {
        return 26;
    }

    private int confirmButtonsY() {
        return confirmDialogY() + confirmDialogHeight() - confirmButtonHeight() - 14;
    }

    private int confirmApplyX() {
        return confirmDialogX() + confirmDialogWidth() / 2 - confirmButtonWidth() - 6;
    }

    private int confirmCancelX() {
        return confirmDialogX() + confirmDialogWidth() / 2 + 6;
    }

    private boolean isInsideResetMenuButton(double x, double y) {
        return isInside(resetMenuButtonX(), topButtonY(), resetMenuButtonWidth(), BUTTON_HEIGHT, x, y);
    }

    private boolean isInsideTemplateMenuButton(double x, double y) {
        return isInside(templateMenuButtonX(), topButtonY(), templateMenuButtonWidth(), BUTTON_HEIGHT, x, y);
    }

    private boolean isInsideConfirmApply(double x, double y) {
        return isInside(confirmApplyX(), confirmButtonsY(), confirmButtonWidth(), confirmButtonHeight(), x, y);
    }

    private boolean isInsideConfirmCancel(double x, double y) {
        return isInside(confirmCancelX(), confirmButtonsY(), confirmButtonWidth(), confirmButtonHeight(), x, y);
    }

    private boolean isGlobalLineWidthEnabled() {
        return overlayClient.allOverlayLineWidthsSame();
    }

    private boolean popupBlocksWidgetHover() {
        return templateMenuOpen || resetMenuOpen || pendingPreset != null;
    }

    private boolean isInsideGlobalLineWidthSlider(double x, double y) {
        return isInside(globalLineWidthSliderX(), globalLineWidthSliderY() + 7, globalLineWidthSliderWidth(), 20, x, y);
    }

    private int controlsX() {
        if (stackedLayout()) {
            return MARGIN;
        }
        return width - rightWidth() + MARGIN;
    }

    private int controlsWidth() {
        if (stackedLayout()) {
            return width - MARGIN * 2;
        }
        return rightWidth() - MARGIN * 2;
    }

    private int redSliderX() {
        return controlsX();
    }

    private int redSliderY() {
        return fieldsY() + 58;
    }

    private int lineWidthSliderY() {
        return redSliderY() + 96 + LINE_WIDTH_GROUP_GAP;
    }

    private int selectedInfoY() {
        if (stackedLayout()) {
            return stackedControlsY();
        }
        return topBarHeight() + MARGIN;
    }

    private int fieldsY() {
        return alphaY() + ALPHA_HEIGHT + 22;
    }

    private int leftWidth() {
        if (stackedLayout()) {
            return width;
        }
        return compactLayout() ? COMPACT_LEFT_WIDTH : LEFT_WIDTH;
    }

    private int rightWidth() {
        if (!compactLayout()) {
            return RIGHT_WIDTH;
        }
        return Math.max(236, Math.min(COMPACT_RIGHT_WIDTH, width / 3 + 28));
    }

    private boolean compactLayout() {
        return width < 820 || height < 520;
    }

    private boolean stackedLayout() {
        return width < 700 || height < 420;
    }

    private int topBarHeight() {
        return stackedLayout() ? 62 : TOP_BAR_HEIGHT;
    }

    private int spectrumWidth() {
        return Math.max(80, controlsWidth() - hueWidth() - 10);
    }

    private int spectrumHeight() {
        if (stackedLayout()) {
            return Math.max(68, Math.min(112, height / 3));
        }
        return Math.max(SPECTRUM_MIN_HEIGHT, Math.min(SPECTRUM_HEIGHT, height - 390));
    }

    private int hueWidth() {
        return compactLayout() ? 14 : HUE_WIDTH;
    }

    private int alphaSliderWidth() {
        return spectrumWidth() + hueWidth() + 10;
    }

    private int channelFieldWidth() {
        return Math.max(24, channelStep() - 22);
    }

    private int channelStep() {
        return Math.max(42, (controlsWidth() + 8) / 4);
    }

    private int channelLabelX(int index) {
        return controlsX() + index * channelStep();
    }

    private int channelFieldX(int index) {
        return channelLabelX(index) + 14;
    }

    private int slotListContainerX() {
        return MARGIN;
    }

    private int slotListContainerWidth() {
        if (stackedLayout()) {
            return width - MARGIN * 2;
        }
        return leftWidth() - MARGIN * 2;
    }

    private int slotRowX() {
        return slotListContainerX() + 4;
    }

    private int slotRowWidth() {
        return slotListContainerWidth() - 8;
    }

    private int slotResetButtonWidth() {
        return compactLayout() ? 50 : 56;
    }

    private int slotResetButtonX() {
        return slotListContainerX() + slotListContainerWidth() - slotResetButtonWidth() - 10;
    }

    private boolean isInsideSlotResetButton(int rowY, double mouseX, double mouseY) {
        return isInside(slotResetButtonX(), rowY + 6, slotResetButtonWidth(), 18, mouseX, mouseY);
    }

    private boolean isInsideSpectrum(double x, double y) {
        return isInside(spectrumX(), spectrumY(), spectrumWidth(), spectrumHeight(), x, y);
    }

    private boolean isInsideHue(double x, double y) {
        return isInside(hueX(), spectrumY(), hueWidth(), spectrumHeight(), x, y);
    }

    private boolean isInsideAlpha(double x, double y) {
        return isInside(spectrumX(), alphaY(), alphaSliderWidth(), ALPHA_HEIGHT, x, y);
    }

    private boolean isInsideChannelSlider(int x, int y, double mouseX, double mouseY) {
        return isInside(x + 18, y, Math.max(80, controlsWidth() - 18), 16, mouseX, mouseY);
    }

    private boolean isInsideLineWidthSlider(double mouseX, double mouseY) {
        return isInside(redSliderX() + 18, lineWidthSliderY(), Math.max(80, controlsWidth() - 18), 16, mouseX, mouseY);
    }

    private boolean isInsidePreview(double x, double y) {
        return isInside(previewX(), previewY(), previewWidth(), previewHeight(), x, y);
    }

    private static boolean isInside(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawSection(GuiGraphicsExtractor context, int x, int y, int width, int height, int fill, int outline) {
        UiChrome.fill(context, x, y, x + width, y + height, fill);
        drawOutline(context, x, y, width, height, outline);
    }

    private static void drawOutline(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        UiChrome.outline(context, x, y, width, height, color);
    }

    private static void drawSwatch(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        drawCheckerboard(context, x, y, width, height, 4);
        UiChrome.fill(context, x, y, x + width, y + height, color);
    }

    private void drawTrimmedText(GuiGraphicsExtractor context, Component text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) {
            return;
        }
        String raw = text.getString();
        String value = raw;
        if (UiChrome.width(font, raw) > maxWidth) {
            String suffix = "...";
            int suffixWidth = UiChrome.width(font, suffix);
            value = UiChrome.trim(font, raw, Math.max(1, maxWidth - suffixWidth)) + suffix;
        }
        UiChrome.text(context, font, value, x, y, color, false);
    }

    private void drawWrappedDialogText(GuiGraphicsExtractor context, Component text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) {
            return;
        }
        String raw = text.getString();
        if (UiChrome.width(font, raw) <= maxWidth) {
            UiChrome.text(context, font, raw, x, y, color, false);
            return;
        }
        List<String> lines = splitIntoTwoLines(raw, maxWidth);
        int lineY = y;
        for (String line : lines) {
            drawTrimmedText(context, Component.literal(line), x, lineY, maxWidth, color);
            lineY += 11;
        }
    }

    private void drawWrappedSlotText(GuiGraphicsExtractor context, Component text, int x, int rowY, int maxWidth, int color) {
        if (maxWidth <= 0) {
            return;
        }
        String raw = text.getString();
        if (UiChrome.width(font, raw) <= maxWidth) {
            UiChrome.text(context, font, raw, x, rowY + 10, color, false);
            return;
        }

        List<String> lines = splitIntoTwoLines(raw, maxWidth);
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, UiChrome.width(font, line));
        }
        float scale = widest <= 0 ? 1.0f : Math.min(1.0f, maxWidth / (float) widest);
        float totalHeight = lines.size() == 1 ? 9.0f * scale : 20.0f * scale;
        float y = rowY + (SLOT_ROW_HEIGHT - totalHeight) * 0.5f;
        for (String line : lines) {
            drawScaledText(context, line, x, y, scale, color);
            y += 11.0f * scale;
        }
    }

    private List<String> splitIntoTwoLines(String raw, int maxWidth) {
        String[] words = raw.split(" ");
        if (words.length <= 1) {
            return List.of(raw);
        }

        String bestFirst = words[0];
        String bestSecond = raw.substring(words[0].length()).trim();
        int bestWidth = Integer.MAX_VALUE;
        for (int split = 1; split < words.length; split++) {
            String first = joinWords(words, 0, split);
            String second = joinWords(words, split, words.length);
            int width = Math.max(UiChrome.width(font, first), UiChrome.width(font, second));
            if (width < bestWidth) {
                bestWidth = width;
                bestFirst = first;
                bestSecond = second;
            }
            if (width <= maxWidth) {
                break;
            }
        }
        return List.of(bestFirst, bestSecond);
    }

    private static String joinWords(String[] words, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(words[i]);
        }
        return builder.toString();
    }

    private void drawScaledText(GuiGraphicsExtractor context, String text, int x, float y, float scale, int color) {
        var matrices = context.pose();
        matrices.pushMatrix();
        try {
            matrices.translate(x, y);
            matrices.scale(scale, scale);
            UiChrome.text(context, font, text, 0, 0, color, false);
        } finally {
            matrices.popMatrix();
        }
    }

    private void drawCenteredTrimmedText(GuiGraphicsExtractor context, Component text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) {
            return;
        }
        String raw = text.getString();
        String value = raw;
        if (UiChrome.width(font, raw) > maxWidth) {
            String suffix = "...";
            int suffixWidth = UiChrome.width(font, suffix);
            value = UiChrome.trim(font, raw, Math.max(1, maxWidth - suffixWidth)) + suffix;
        }
        int textX = x + Math.max(0, (maxWidth - UiChrome.width(font, value)) / 2);
        UiChrome.text(context, font, value, textX, y, color, false);
    }

    private static void drawCheckerboard(GuiGraphicsExtractor context, int x, int y, int width, int height, int cell) {
        for (int py = 0; py < height; py += cell) {
            for (int px = 0; px < width; px += cell) {
                boolean light = ((px / cell) + (py / cell)) % 2 == 0;
                UiChrome.fill(context, x + px, y + py, x + Math.min(width, px + cell), y + Math.min(height, py + cell), light ? 0xFFCED6E2 : 0xFF7D8795);
            }
        }
    }

    private static void drawCrosshair(GuiGraphicsExtractor context, int x, int y, int color) {
        UiChrome.fill(context, x - 5, y, x - 1, y + 1, color);
        UiChrome.fill(context, x + 2, y, x + 6, y + 1, color);
        UiChrome.fill(context, x, y - 5, x + 1, y - 1, color);
        UiChrome.fill(context, x, y + 2, x + 1, y + 6, color);
        UiChrome.fill(context, x - 1, y - 1, x + 2, y + 2, color ^ 0x00FFFFFF);
    }

    private static Integer parseChannel(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0 || parsed > 255) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float normalizeYaw(float value) {
        float normalized = value % 360.0f;
        return normalized < 0.0f ? normalized + 360.0f : normalized;
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | (blue & 255);
    }

    private static int alpha(int color) {
        return color >>> 24 & 255;
    }

    private static int red(int color) {
        return color >>> 16 & 255;
    }

    private static int green(int color) {
        return color >>> 8 & 255;
    }

    private static int blue(int color) {
        return color & 255;
    }

    private static int hsvToArgb(float hue, float saturation, float value, int alpha) {
        float h = (hue - (float) Math.floor(hue)) * 6.0f;
        int sector = (int) Math.floor(h);
        float f = h - sector;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - f * saturation);
        float t = value * (1.0f - (1.0f - f) * saturation);
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = value;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = value;
                b = p;
            }
            case 2 -> {
                r = p;
                g = value;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = value;
            }
            case 4 -> {
                r = t;
                g = p;
                b = value;
            }
            default -> {
                r = value;
                g = p;
                b = q;
            }
        }
        return argb(alpha, Math.round(r * 255.0f), Math.round(g * 255.0f), Math.round(b * 255.0f));
    }

    private static float[] rgbToHsv(int red, int green, int blue) {
        float r = red / 255.0f;
        float g = green / 255.0f;
        float b = blue / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta <= 0.0001f) {
            hue = 0.0f;
        } else if (max == r) {
            hue = ((g - b) / delta) / 6.0f;
        } else if (max == g) {
            hue = (2.0f + (b - r) / delta) / 6.0f;
        } else {
            hue = (4.0f + (r - g) / delta) / 6.0f;
        }
        if (hue < 0.0f) {
            hue += 1.0f;
        }
        float saturation = max <= 0.0001f ? 0.0f : delta / max;
        return new float[]{hue, saturation, max};
    }

    private enum Channel {
        RED,
        GREEN,
        BLUE,
        ALPHA
    }

    private enum OverlayPreset {
        BALANCED("balanced"),
        HIGH_CONTRAST("high_contrast"),
        SUBTLE("subtle"),
        PRECISION("precision"),
        LARGE_SELECTION("large_selection");

        private final String key;

        OverlayPreset(String key) {
            this.key = key;
        }

        private Component nameText() {
            return Component.translatable("screen.worldedit_selection_overlay.colors.template." + key);
        }

        private Component descriptionText() {
            return Component.translatable("screen.worldedit_selection_overlay.colors.template." + key + ".desc");
        }

        private OverlayColorProfile profile() {
            OverlayColorProfile profile = OverlayColorProfile.defaults();
            switch (this) {
                case BALANCED -> applyBalanced(profile);
                case HIGH_CONTRAST -> applyHighContrast(profile);
                case SUBTLE -> applySubtle(profile);
                case PRECISION -> applyPrecision(profile);
                case LARGE_SELECTION -> applyLargeSelection(profile);
            }
            return profile;
        }

        private static void applyBalanced(OverlayColorProfile profile) {
            profile.setColor(OverlayColorSlot.POS1, 0xFFFFB000);
            profile.setColor(OverlayColorSlot.POS2, 0xFF78A9FF);
            profile.setColor(OverlayColorSlot.CUBOID_EDGE, 0xFF33B1FF);
            profile.setColor(OverlayColorSlot.CUBOID_GRID, 0x8A33B1FF);
            profile.setColor(OverlayColorSlot.CUBOID_CONNECTOR, 0xFFE5EEF8);
            profile.setColor(OverlayColorSlot.POLYGON_EDGE, 0xFF42BE65);
            profile.setColor(OverlayColorSlot.POLYGON_GRID, 0x8042BE65);
            profile.setColor(OverlayColorSlot.POLYGON_POINT, 0xFFEE5396);
            profile.setColor(OverlayColorSlot.ELLIPSOID_EDGE, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.ELLIPSOID_CENTER, 0xFFFFB000);
            profile.setColor(OverlayColorSlot.CYLINDER_EDGE, 0xFFBE95FF);
            profile.setColor(OverlayColorSlot.CYLINDER_GRID, 0x82BE95FF);
            profile.setColor(OverlayColorSlot.CYLINDER_CENTER, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.POLYHEDRON_EDGE, 0xFF33B1FF);
            profile.setColor(OverlayColorSlot.POLYHEDRON_POINT, 0xFFEE5396);
            profile.setColor(OverlayColorSlot.RULER_LINE, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.RULER_ARROW, 0xFFEAF0FA);
            profile.setColor(OverlayColorSlot.RULER_TEXT, 0xFFFFFFFF);
            profile.setColor(OverlayColorSlot.RULER_LABEL_BACKGROUND, 0xD8000000);
            profile.setColor(OverlayColorSlot.RULER_LABEL_BORDER, 0xA0FFD166);
            setAllWidths(profile, 1.0f);
            setWidth(profile, 0.76f, OverlayColorSlot.CUBOID_GRID, OverlayColorSlot.POLYGON_GRID, OverlayColorSlot.CYLINDER_GRID);
            setWidth(profile, 1.18f, OverlayColorSlot.POS1, OverlayColorSlot.POS2, OverlayColorSlot.POLYGON_POINT, OverlayColorSlot.ELLIPSOID_CENTER, OverlayColorSlot.CYLINDER_CENTER, OverlayColorSlot.POLYHEDRON_POINT);
        }

        private static void applyHighContrast(OverlayColorProfile profile) {
            profile.setColor(OverlayColorSlot.POS1, 0xFFFFB000);
            profile.setColor(OverlayColorSlot.POS2, 0xFF78A9FF);
            profile.setColor(OverlayColorSlot.CUBOID_EDGE, 0xFFFFFFFF);
            profile.setColor(OverlayColorSlot.CUBOID_GRID, 0xC8FFFF66);
            profile.setColor(OverlayColorSlot.CUBOID_CONNECTOR, 0xFFFF7AE5);
            profile.setColor(OverlayColorSlot.POLYGON_EDGE, 0xFFFFF1A6);
            profile.setColor(OverlayColorSlot.POLYGON_GRID, 0xB8FFFFFF);
            profile.setColor(OverlayColorSlot.POLYGON_POINT, 0xFFFF7AE5);
            profile.setColor(OverlayColorSlot.ELLIPSOID_EDGE, 0xFFFFEA70);
            profile.setColor(OverlayColorSlot.ELLIPSOID_CENTER, 0xFFFFB000);
            profile.setColor(OverlayColorSlot.CYLINDER_EDGE, 0xFF78F7FF);
            profile.setColor(OverlayColorSlot.CYLINDER_GRID, 0xB8FFFFFF);
            profile.setColor(OverlayColorSlot.CYLINDER_CENTER, 0xFFFFB000);
            profile.setColor(OverlayColorSlot.POLYHEDRON_EDGE, 0xFFFFFFFF);
            profile.setColor(OverlayColorSlot.POLYHEDRON_POINT, 0xFFFF7AE5);
            profile.setColor(OverlayColorSlot.RULER_LINE, 0xFFFFEA70);
            profile.setColor(OverlayColorSlot.RULER_ARROW, 0xFFFFFFFF);
            profile.setColor(OverlayColorSlot.RULER_TEXT, 0xFFFFFFFF);
            profile.setColor(OverlayColorSlot.RULER_LABEL_BACKGROUND, 0xE0000000);
            profile.setColor(OverlayColorSlot.RULER_LABEL_BORDER, 0xD8FFFFFF);
            setAllWidths(profile, 1.22f);
            setWidth(profile, 0.95f, OverlayColorSlot.CUBOID_GRID, OverlayColorSlot.POLYGON_GRID, OverlayColorSlot.CYLINDER_GRID);
            setWidth(profile, 1.45f, OverlayColorSlot.POS1, OverlayColorSlot.POS2, OverlayColorSlot.POLYGON_POINT, OverlayColorSlot.ELLIPSOID_CENTER, OverlayColorSlot.CYLINDER_CENTER, OverlayColorSlot.POLYHEDRON_POINT);
        }

        private static void applySubtle(OverlayColorProfile profile) {
            profile.setColor(OverlayColorSlot.POS1, 0xD8FF6A78);
            profile.setColor(OverlayColorSlot.POS2, 0xD86E91FF);
            profile.setColor(OverlayColorSlot.CUBOID_EDGE, 0xB06FCFFF);
            profile.setColor(OverlayColorSlot.CUBOID_GRID, 0x526FCFFF);
            profile.setColor(OverlayColorSlot.CUBOID_CONNECTOR, 0x96DCE8F5);
            profile.setColor(OverlayColorSlot.POLYGON_EDGE, 0xA86DDDB2);
            profile.setColor(OverlayColorSlot.POLYGON_GRID, 0x4F6DDDB2);
            profile.setColor(OverlayColorSlot.POLYGON_POINT, 0xC882F2C8);
            profile.setColor(OverlayColorSlot.ELLIPSOID_EDGE, 0xB8E5C86A);
            profile.setColor(OverlayColorSlot.ELLIPSOID_CENTER, 0xC8FFE487);
            profile.setColor(OverlayColorSlot.CYLINDER_EDGE, 0xAFC58CFF);
            profile.setColor(OverlayColorSlot.CYLINDER_GRID, 0x55B78CDE);
            profile.setColor(OverlayColorSlot.CYLINDER_CENTER, 0xCBE9C7FF);
            profile.setColor(OverlayColorSlot.POLYHEDRON_EDGE, 0xB078D8FF);
            profile.setColor(OverlayColorSlot.POLYHEDRON_POINT, 0xC88EE7FF);
            profile.setColor(OverlayColorSlot.RULER_LINE, 0xCCEAD06A);
            profile.setColor(OverlayColorSlot.RULER_ARROW, 0xD8FFF1AA);
            profile.setColor(OverlayColorSlot.RULER_TEXT, 0xEAF8FAFF);
            profile.setColor(OverlayColorSlot.RULER_LABEL_BACKGROUND, 0xB0000000);
            profile.setColor(OverlayColorSlot.RULER_LABEL_BORDER, 0x705B6678);
            setAllWidths(profile, 0.78f);
            setWidth(profile, 0.62f, OverlayColorSlot.CUBOID_GRID, OverlayColorSlot.POLYGON_GRID, OverlayColorSlot.CYLINDER_GRID);
            setWidth(profile, 0.95f, OverlayColorSlot.POS1, OverlayColorSlot.POS2, OverlayColorSlot.POLYGON_POINT, OverlayColorSlot.ELLIPSOID_CENTER, OverlayColorSlot.CYLINDER_CENTER, OverlayColorSlot.POLYHEDRON_POINT);
        }

        private static void applyPrecision(OverlayColorProfile profile) {
            profile.setColor(OverlayColorSlot.POS1, 0xFFFF4B4B);
            profile.setColor(OverlayColorSlot.POS2, 0xFF4B7DFF);
            profile.setColor(OverlayColorSlot.CUBOID_EDGE, 0xFF00D6C9);
            profile.setColor(OverlayColorSlot.CUBOID_GRID, 0x9048A7FF);
            profile.setColor(OverlayColorSlot.CUBOID_CONNECTOR, 0xFFEAF0FA);
            profile.setColor(OverlayColorSlot.POLYGON_EDGE, 0xFF38E0A8);
            profile.setColor(OverlayColorSlot.POLYGON_GRID, 0x8048D9A8);
            profile.setColor(OverlayColorSlot.POLYGON_POINT, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.ELLIPSOID_EDGE, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.ELLIPSOID_CENTER, 0xFFFF5F7E);
            profile.setColor(OverlayColorSlot.CYLINDER_EDGE, 0xFFB78CFF);
            profile.setColor(OverlayColorSlot.CYLINDER_GRID, 0x885FA8FF);
            profile.setColor(OverlayColorSlot.CYLINDER_CENTER, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.POLYHEDRON_EDGE, 0xFF5FE1FF);
            profile.setColor(OverlayColorSlot.POLYHEDRON_POINT, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.RULER_LINE, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.RULER_ARROW, 0xFFEAF0FA);
            profile.setColor(OverlayColorSlot.RULER_TEXT, 0xFFFFFFFF);
            profile.setColor(OverlayColorSlot.RULER_LABEL_BACKGROUND, 0xD0000000);
            profile.setColor(OverlayColorSlot.RULER_LABEL_BORDER, 0xA0FFD166);
            setAllWidths(profile, 0.86f);
            setWidth(profile, 0.62f, OverlayColorSlot.CUBOID_GRID, OverlayColorSlot.POLYGON_GRID, OverlayColorSlot.CYLINDER_GRID);
            setWidth(profile, 1.05f, OverlayColorSlot.POS1, OverlayColorSlot.POS2, OverlayColorSlot.POLYGON_POINT, OverlayColorSlot.ELLIPSOID_CENTER, OverlayColorSlot.CYLINDER_CENTER, OverlayColorSlot.POLYHEDRON_POINT);
        }

        private static void applyLargeSelection(OverlayColorProfile profile) {
            profile.setColor(OverlayColorSlot.POS1, 0xFFFF4F63);
            profile.setColor(OverlayColorSlot.POS2, 0xFF3FA7FF);
            profile.setColor(OverlayColorSlot.CUBOID_EDGE, 0xFF5FF0FF);
            profile.setColor(OverlayColorSlot.CUBOID_GRID, 0x665FF0FF);
            profile.setColor(OverlayColorSlot.CUBOID_CONNECTOR, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.POLYGON_EDGE, 0xFF73F7AD);
            profile.setColor(OverlayColorSlot.POLYGON_GRID, 0x5573F7AD);
            profile.setColor(OverlayColorSlot.POLYGON_POINT, 0xFFFF6BD6);
            profile.setColor(OverlayColorSlot.ELLIPSOID_EDGE, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.ELLIPSOID_CENTER, 0xFFFF6B6B);
            profile.setColor(OverlayColorSlot.CYLINDER_EDGE, 0xFFD28BFF);
            profile.setColor(OverlayColorSlot.CYLINDER_GRID, 0x66D28BFF);
            profile.setColor(OverlayColorSlot.CYLINDER_CENTER, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.POLYHEDRON_EDGE, 0xFF6BE7FF);
            profile.setColor(OverlayColorSlot.POLYHEDRON_POINT, 0xFFFF6BD6);
            profile.setColor(OverlayColorSlot.RULER_LINE, 0xFFFFD166);
            profile.setColor(OverlayColorSlot.RULER_ARROW, 0xFFFFFFFF);
            profile.setColor(OverlayColorSlot.RULER_TEXT, 0xFFFFFFFF);
            profile.setColor(OverlayColorSlot.RULER_LABEL_BACKGROUND, 0xD8000000);
            profile.setColor(OverlayColorSlot.RULER_LABEL_BORDER, 0xB8FFD166);
            setAllWidths(profile, 1.35f);
            setWidth(profile, 0.78f, OverlayColorSlot.CUBOID_GRID, OverlayColorSlot.POLYGON_GRID, OverlayColorSlot.CYLINDER_GRID);
            setWidth(profile, 1.65f, OverlayColorSlot.POS1, OverlayColorSlot.POS2, OverlayColorSlot.POLYGON_POINT, OverlayColorSlot.ELLIPSOID_CENTER, OverlayColorSlot.CYLINDER_CENTER, OverlayColorSlot.POLYHEDRON_POINT);
        }

        private static void setAllWidths(OverlayColorProfile profile, float scale) {
            for (OverlayColorSlot slot : OverlayColorSlot.values()) {
                if (slot.supportsLineWidth()) {
                    profile.setLineWidthScale(slot, scale);
                }
            }
        }

        private static void setWidth(OverlayColorProfile profile, float scale, OverlayColorSlot... slots) {
            for (OverlayColorSlot slot : slots) {
                profile.setLineWidthScale(slot, scale);
            }
        }
    }

    private enum ResetAction {
        COLORS("reset_all_colors"),
        WIDTHS("reset_all_widths"),
        ALL("reset_all_settings");

        private final String key;

        ResetAction(String key) {
            this.key = key;
        }

        private String translationKey() {
            return "screen.worldedit_selection_overlay.colors." + key;
        }
    }

    enum ColorGroup {
        CUBOID("cuboid", OverlayColorSlot.POS1, OverlayColorSlot.POS2, OverlayColorSlot.CUBOID_EDGE, OverlayColorSlot.CUBOID_GRID, OverlayColorSlot.CUBOID_CONNECTOR),
        POLYGON("polygon", OverlayColorSlot.POLYGON_EDGE, OverlayColorSlot.POLYGON_GRID, OverlayColorSlot.POLYGON_POINT),
        ELLIPSOID("ellipsoid", OverlayColorSlot.ELLIPSOID_EDGE, OverlayColorSlot.ELLIPSOID_CENTER),
        CYLINDER("cylinder", OverlayColorSlot.CYLINDER_EDGE, OverlayColorSlot.CYLINDER_GRID, OverlayColorSlot.CYLINDER_CENTER),
        POLYHEDRON("polyhedron", OverlayColorSlot.POS1, OverlayColorSlot.POLYHEDRON_EDGE, OverlayColorSlot.POLYHEDRON_POINT),
        RULER("ruler", OverlayColorSlot.RULER_LINE, OverlayColorSlot.RULER_ARROW, OverlayColorSlot.RULER_TEXT, OverlayColorSlot.RULER_LABEL_BACKGROUND, OverlayColorSlot.RULER_LABEL_BORDER);

        private final String key;
        private final OverlayColorSlot[] slots;

        ColorGroup(String key, OverlayColorSlot... slots) {
            this.key = key;
            this.slots = slots;
        }

        private String translationKey() {
            return "screen.worldedit_selection_overlay.colors.group." + key;
        }

        private OverlayColorSlot[] slots() {
            return slots;
        }
    }
}

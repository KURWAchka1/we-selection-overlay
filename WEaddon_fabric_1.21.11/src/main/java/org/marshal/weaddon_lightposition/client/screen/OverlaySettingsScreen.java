package org.marshal.weaddon_lightposition.client.screen;

import java.util.ArrayList;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
import org.marshal.weaddon_lightposition.client.WorldEditPointOverlayClient;

public final class OverlaySettingsScreen extends UiScreen {
    private final WorldEditPointOverlayClient overlayClient;
    private final Screen parent;
    private final ArrayList<ButtonWidget> content = new ArrayList<>();
    private final ArrayList<ButtonWidget> menuControls = new ArrayList<>();
    private ButtonWidget overlayButton, gridButton, clipboardPreviewButton, rulerButton;
    private ButtonWidget axisXButton, axisYButton, axisZButton;
    private ButtonWidget renderModeButton, diagonalModeButton, trackingModeButton, doneButton;
    private ButtonWidget colorEditorButton, themeButton;
    private ButtonWidget commandPreviewButton;
    private ButtonWidget selectionEditButton, selectionEditOptionsButton;
    private boolean menuOpen;
    private int scroll;
    private int contentHeight;

    public OverlaySettingsScreen(WorldEditPointOverlayClient overlayClient, Screen parent) {
        super(Text.translatable("screen.worldedit_selection_overlay.settings.title"));
        this.overlayClient = overlayClient;
        this.parent = parent;
    }

    private static Text key(String suffix) {
        return Text.translatable("screen.worldedit_selection_overlay.settings." + suffix);
    }

    @Override
    protected void init() {
        content.clear();
        menuControls.clear();
        colorEditorButton = addDrawableChild(UiButton.styled(key("colors_short"), button -> {
            if (client != null) client.setScreen(new OverlayColorSettingsScreen(overlayClient, this));
        }).icon("pencil").tooltip(key("colors_short")).dimensions(panelX() + panelWidth() - 68, panelY() + 9, 24, 24).build());
        themeButton = addDrawableChild(UiButton.styled(key("ui_options"), button -> setMenuOpen(!menuOpen))
                .icon("sliders-horizontal").selected(() -> menuOpen).tooltip(key("ui_options"))
                .dimensions(panelX() + panelWidth() - 38, panelY() + 9, 24, 24).build());
        overlayButton = row("overlay", () -> {
            overlayClient.setOverlayEnabled(!overlayClient.overlayEnabled());
            layoutControls();
        }, overlayClient::overlayEnabled);
        gridButton = row("grid", () -> overlayClient.setSelectionGridEnabled(!overlayClient.selectionGridEnabled()),
                overlayClient::selectionGridEnabled);
        clipboardPreviewButton = row("clipboard_preview", () ->
                overlayClient.setClipboardPreviewEnabled(!overlayClient.clipboardPreviewEnabled()), overlayClient::clipboardPreviewEnabled);
        commandPreviewButton = row("command_preview", () -> overlayClient.commandPreviewController()
                .setEnabled(!overlayClient.commandPreviewController().options().enabled()),
                () -> overlayClient.commandPreviewController().options().enabled());
        var editing = org.marshal.weaddon_lightposition.client.selection.SelectionEditController.instance();
        selectionEditButton = row("selection_edit", () -> editing.configure(!editing.options().enabled(), editing.options().step(), editing.options().fastStep()),
                () -> editing.options().enabled());
        selectionEditOptionsButton = command(() -> key("selection_edit_options"), () -> {
            if (client != null) client.setScreen(new SelectionEditSettingsScreen(this));
        });
        rulerButton = row("ruler", () -> {
            overlayClient.setRulerEnabled(!overlayClient.rulerEnabled());
            layoutControls();
        }, overlayClient::rulerEnabled);
        axisXButton = axis('X', overlayClient::rulerAxisXEnabled, () -> overlayClient.setRulerAxisXEnabled(!overlayClient.rulerAxisXEnabled()));
        axisYButton = axis('Y', overlayClient::rulerAxisYEnabled, () -> overlayClient.setRulerAxisYEnabled(!overlayClient.rulerAxisYEnabled()));
        axisZButton = axis('Z', overlayClient::rulerAxisZEnabled, () -> overlayClient.setRulerAxisZEnabled(!overlayClient.rulerAxisZEnabled()));
        renderModeButton = command(() -> Text.translatable("screen.worldedit_selection_overlay.settings.render_mode",
                Text.translatable("message.worldedit_selection_overlay.render_mode." + overlayClient.renderMode().translationSuffix())),
                () -> overlayClient.setRenderMode(overlayClient.renderMode().next()));
        diagonalModeButton = command(() -> Text.translatable("screen.worldedit_selection_overlay.settings.diagonal",
                key("diagonal." + overlayClient.diagonalMode().translationSuffix())),
                () -> overlayClient.setDiagonalMode(overlayClient.diagonalMode().next()));
        trackingModeButton = command(() -> Text.translatable("screen.worldedit_selection_overlay.settings.ruler_tracking",
                key("ruler_tracking." + overlayClient.rulerTrackingMode().translationSuffix())),
                () -> overlayClient.setRulerTrackingMode(overlayClient.rulerTrackingMode().next()));
        doneButton = addDrawableChild(UiButton.styled(key("done"), button -> close())
                .dimensions(panelX() + 12, panelY() + panelHeight() - 32, panelWidth() - 24, 22).build());

        int mx = menuX() + 8, my = menuY() + 25;
        for (UiStyle style : UiStyle.values()) {
            ButtonWidget choice = addDrawableChild(UiButton.styled(key("style." + style.name().toLowerCase(java.util.Locale.ROOT)), button -> {
                overlayClient.setUiStyle(style);
                if (client != null) client.setScreen(new OverlaySettingsScreen(overlayClient, parent));
            }).selected(() -> UiStyle.current() == style).dimensions(mx, my, menuWidth() - 16, 20).build());
            menuControls.add(choice);
            my += 23;
        }
        menuControls.add(addDrawableChild(UiButton.styled(key("reduced_motion"), button ->
                overlayClient.setReducedMotion(!overlayClient.reducedMotion()))
                .toggle(overlayClient::reducedMotion, key("reduced_motion")).dimensions(mx, my + 3, menuWidth() - 16, 22).build()));
        menuControls.add(addDrawableChild(UiButton.styled(key("blur"), button ->
                overlayClient.setUiBlur(!overlayClient.uiBlur()))
                .toggle(overlayClient::uiBlur, key("blur")).dimensions(mx, my + 27, menuWidth() - 16, 22).build()));
        layoutControls();
        setMenuOpen(false);
    }

    private ButtonWidget row(String name, Runnable action, java.util.function.BooleanSupplier value) {
        ButtonWidget button = addDrawableChild(UiButton.styled(
                Text.translatable("screen.worldedit_selection_overlay.settings." + name, state(value.getAsBoolean())),
                ignored -> { action.run(); layoutControls(); })
                .toggle(value, key(name + "_label")).quiet().dimensions(0, 0, 100, 24).build());
        content.add(button);
        return button;
    }

    private ButtonWidget command(java.util.function.Supplier<Text> title, Runnable action) {
        ButtonWidget button = addDrawableChild(UiButton.styled(title.get(), ignored -> {
            action.run();
            syncLabels();
        }).quiet().trailingIcon("chevron-down").dimensions(0, 0, 100, 22).build());
        content.add(button);
        return button;
    }

    private ButtonWidget axis(char name, java.util.function.BooleanSupplier value, Runnable action) {
        ButtonWidget button = addDrawableChild(UiButton.styled(Text.literal(Character.toString(name)), ignored -> action.run())
                .selected(value).dimensions(0, 0, 22, 20).build());
        content.add(button);
        return button;
    }

    private static Text state(boolean value) { return key("value." + (value ? "on" : "off")); }

    private void syncLabels() {
        selectionEditButton.setMessage(Text.translatable("screen.worldedit_selection_overlay.settings.selection_edit",
                state(org.marshal.weaddon_lightposition.client.selection.SelectionEditController.instance().options().enabled())));
        overlayButton.setMessage(Text.translatable("screen.worldedit_selection_overlay.settings.overlay", state(overlayClient.overlayEnabled())));
        gridButton.setMessage(Text.translatable("screen.worldedit_selection_overlay.settings.grid", state(overlayClient.selectionGridEnabled())));
        commandPreviewButton.setMessage(Text.translatable("screen.worldedit_selection_overlay.settings.command_preview",
                state(overlayClient.commandPreviewController().options().enabled())));
        rulerButton.setMessage(Text.translatable("screen.worldedit_selection_overlay.settings.ruler", state(overlayClient.rulerEnabled())));
        clipboardPreviewButton.setMessage(overlayClient.clipboardPreviewAvailable()
                ? Text.translatable("screen.worldedit_selection_overlay.settings.clipboard_preview", state(overlayClient.clipboardPreviewEnabled()))
                : key("clipboard_preview_unavailable"));
        renderModeButton.setMessage(Text.translatable("screen.worldedit_selection_overlay.settings.render_mode",
                Text.translatable("message.worldedit_selection_overlay.render_mode." + overlayClient.renderMode().translationSuffix())));
        diagonalModeButton.setMessage(Text.translatable("screen.worldedit_selection_overlay.settings.diagonal",
                key("diagonal." + overlayClient.diagonalMode().translationSuffix())));
        trackingModeButton.setMessage(Text.translatable("screen.worldedit_selection_overlay.settings.ruler_tracking",
                key("ruler_tracking." + overlayClient.rulerTrackingMode().translationSuffix())));
    }

    private void layoutControls() {
        if (overlayButton == null) return;
        int y = panelY() + 43 - scroll, x = panelX() + 12, w = panelWidth() - 24;
        position(overlayButton, x, y, w, 24); y += 27;
        gridButton.visible = false;
        if (overlayClient.overlayEnabled()) { position(gridButton, x + 16, y, w - 16, 22); y += 25; }
        position(clipboardPreviewButton, x, y, w, 24); y += 27;
        position(commandPreviewButton, x, y, w, 24); y += 27;
        position(selectionEditButton, x, y, w, 24); y += 27;
        position(selectionEditOptionsButton, x + 16, y, w - 16, 22); y += 25;
        position(rulerButton, x, y, w, 24); y += 27;
        axisXButton.visible = axisYButton.visible = axisZButton.visible = false;
        if (overlayClient.rulerEnabled()) {
            int ax = x + w - 80;
            position(axisXButton, ax, y, 22, 20);
            position(axisYButton, ax + 28, y, 22, 20);
            position(axisZButton, ax + 56, y, 22, 20);
            y += 24;
        }
        position(renderModeButton, x, y, w, 22); y += 25;
        position(diagonalModeButton, x, y, w, 22); y += 25;
        position(trackingModeButton, x, y, w, 22); y += 25;
        contentHeight = y + scroll - (panelY() + 43);
        int maxScroll = Math.max(0, contentHeight - (panelHeight() - 83));
        if (scroll > maxScroll) { scroll = maxScroll; layoutControls(); return; }
        for (ButtonWidget button : content) button.active = !menuOpen;
        clipboardPreviewButton.active = !menuOpen && overlayClient.clipboardPreviewAvailable();
        selectionEditButton.active = !menuOpen && org.marshal.weaddon_lightposition.client.selection.SelectionEditController.instance().available();
        syncLabels();
    }

    private void position(ButtonWidget button, int x, int y, int width, int height) {
        button.setX(x); button.setY(y); button.setWidth(width); button.setDimensionsAndPosition(width, height, x, y);
        button.visible = y >= panelY() + 41 && y + height <= panelY() + panelHeight() - 39;
    }

    private void setMenuOpen(boolean value) {
        menuOpen = value;
        for (ButtonWidget button : menuControls) { button.visible = value; button.active = value; }
        colorEditorButton.active = doneButton.active = !value;
        layoutControls();
    }

    @Override
    public void tick() {
        super.tick();
        if (clipboardPreviewButton != null) clipboardPreviewButton.active = !menuOpen && overlayClient.clipboardPreviewAvailable();
        if (selectionEditButton != null) selectionEditButton.active = !menuOpen && org.marshal.weaddon_lightposition.client.selection.SelectionEditController.instance().available();
    }

    @Override
    protected void drawScreen(DrawContext graphics, int mouseX, int mouseY, float delta) {
        UiStyle style = UiStyle.current();
        UiChrome.fill(graphics, 0, 0, width, height, 0x38000000);
        UiChrome.panel(graphics, panelX(), panelY(), panelWidth(), panelHeight(), style);
        UiChrome.text(graphics, font, key("compact_title"), panelX() + 20, panelY() + 16, style.text, false);
        if (overlayClient.rulerEnabled() && axisXButton.visible) {
            UiChrome.text(graphics, font, key("ruler_axes"), panelX() + 20, axisXButton.getY() + 5, style.secondaryText, false);
        }
        for (ButtonWidget button : menuControls) button.visible = false;
        renderWidgets(graphics, menuOpen ? Integer.MIN_VALUE : mouseX, menuOpen ? Integer.MIN_VALUE : mouseY, delta);
        for (ButtonWidget button : menuControls) button.visible = menuOpen;
        if (menuOpen) {
            UiChrome.popup(graphics, menuX(), menuY(), menuWidth(), 155, style);
            UiChrome.text(graphics, font, key("ui_options"), menuX() + 10, menuY() + 10, style.text, false);
            for (ButtonWidget button : menuControls) button.render(graphics, mouseX, mouseY, delta);
        }
        int availableHeight = panelHeight() - 83;
        if (contentHeight > availableHeight) {
            int thumb = Math.max(12, availableHeight * availableHeight / contentHeight);
            int thumbY = panelY() + 42 + scroll * (availableHeight - thumb) / (contentHeight - availableHeight);
            UiChrome.fillRounded(graphics, panelX() + panelWidth() - 5, thumbY, 3, thumb, style.border);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (isClosing()) return true;
        if (menuOpen) {
            for (ButtonWidget button : menuControls) if (button.mouseClicked(click, doubleClick)) return true;
            if (themeButton.mouseClicked(click, doubleClick)) return true;
            if (click.x() < menuX() || click.x() >= menuX() + menuWidth() || click.y() < menuY() || click.y() >= menuY() + 155) setMenuOpen(false);
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (isClosing() || menuOpen) return true;
        if (x >= panelX() && x < panelX() + panelWidth()) {
            scroll = Math.max(0, Math.min(Math.max(0, contentHeight - (panelHeight() - 83)), scroll - (int) (vertical * 24)));
            layoutControls();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public void close() {
        if (menuOpen) { setMenuOpen(false); return; }
        closeAnimated(() -> { if (client != null) client.setScreen(parent); });
    }

    @Override
    public boolean shouldPause() { return false; }

    private int panelWidth() { return Math.min(286, Math.max(160, width - 16)); }
    private int panelHeight() { return Math.min(304, Math.max(120, height - 16)); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }
    private int menuWidth() { return Math.min(182, panelWidth() - 16); }
    private int menuX() { return panelX() + panelWidth() - menuWidth() - 8; }
    private int menuY() { return Math.max(4, Math.min(panelY() + 39, height - 159)); }
}

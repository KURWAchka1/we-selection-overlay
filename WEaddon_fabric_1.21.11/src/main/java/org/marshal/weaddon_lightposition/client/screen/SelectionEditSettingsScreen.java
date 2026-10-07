package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.marshal.weaddon_lightposition.client.selection.SelectionEditController;

public final class SelectionEditSettingsScreen extends UiScreen {
    private final Screen parent;
    private final SelectionEditController editing = SelectionEditController.instance();
    private int capture;
    private ButtonWidget editBinding, fastBinding;
    public SelectionEditSettingsScreen(Screen parent) { super(key("title")); this.parent = parent; }
    private static Text key(String name, Object... args) { return Text.translatable("selection_edit.worldedit_selection_overlay." + name, args); }

    @Override protected void init() {
        int x = (width - panelWidth()) / 2 + 12, y = panelY() + 35, w = panelWidth() - 24;
        editBinding = addDrawableChild(UiButton.styled(binding(false), button -> { capture = 1; refresh(); }).quiet().dimensions(x, y, w, 22).build());
        fastBinding = addDrawableChild(UiButton.styled(binding(true), button -> { capture = 2; refresh(); }).quiet().dimensions(x, y + 27, w, 22).build());
        addDrawableChild(new StepSlider(x, y + 54, w, false));
        addDrawableChild(new StepSlider(x, y + 81, w, true));
        addDrawableChild(UiButton.styled(Text.translatable("screen.worldedit_selection_overlay.settings.done"), button -> close())
                .dimensions(x, y + 110, w, 22).build());
    }
    private Text binding(boolean fast) { return key(fast ? "fast_binding" : "binding", capture == (fast ? 2 : 1) ? key("binding_wait") : editing.bindingName(fast)); }
    private void refresh() { editBinding.setMessage(binding(false)); fastBinding.setMessage(binding(true)); }
    @Override public boolean keyPressed(KeyInput event) {
        if (capture != 0) {
            if (event.key() != GLFW.GLFW_KEY_ESCAPE) editing.bind(capture == 2, event.key() == GLFW.GLFW_KEY_BACKSPACE ? null : event);
            capture = 0; refresh(); return true;
        }
        return super.keyPressed(event);
    }
    @Override protected void drawScreen(DrawContext graphics, int mouseX, int mouseY, float delta) {
        UiStyle style = UiStyle.current();
        UiChrome.fill(graphics, 0, 0, width, height, 0x38000000);
        UiChrome.panel(graphics, (width - panelWidth()) / 2, panelY(), panelWidth(), panelHeight(), style);
        UiChrome.text(graphics, font, title, (width - panelWidth()) / 2 + 20, panelY() + 13, style.text, false);
        renderWidgets(graphics, mouseX, mouseY, delta);
    }
    @Override public void close() { closeAnimated(() -> { if (client != null) client.setScreen(parent); }); }
    @Override public boolean shouldPause() { return false; }
    private int panelWidth() { return Math.min(286, Math.max(150, width - 16)); }
    private int panelHeight() { return Math.min(182, height - 8); }
    private int panelY() { return Math.max(4, (height - panelHeight()) / 2); }
    private final class StepSlider extends SliderWidget {
        private final boolean fast;
        StepSlider(int x, int y, int width, boolean fast) {
            super(x, y, width, 24, Text.empty(), ((fast ? editing.options().fastStep() : editing.options().step()) - 1.0) / (fast ? 511 : 63));
            this.fast = fast; updateMessage();
        }
        private int step() { return 1 + (int) Math.round(value * (fast ? 511 : 63)); }
        @Override protected void updateMessage() { setMessage(key(fast ? "fast_step" : "step", step())); }
        @Override protected void applyValue() { editing.configure(editing.options().enabled(), fast ? editing.options().step() : step(), fast ? step() : editing.options().fastStep()); }
        @Override public void renderWidget(DrawContext graphics, int mouseX, int mouseY, float delta) {
            UiChrome.text(graphics, font, getMessage(), getX() + 8, getY() + 1, UiStyle.current().text, false);
            UiChrome.slider(graphics, getX() + 8, getY() + 10, getWidth() - 16, (float) value, UiStyle.current().accent, active);
        }
    }
}

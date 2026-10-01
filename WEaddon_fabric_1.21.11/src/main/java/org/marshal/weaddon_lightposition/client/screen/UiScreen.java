package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class UiScreen extends Screen {
    protected final net.minecraft.client.font.TextRenderer font = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
    private final UiMotion motion = new UiMotion();

    protected UiScreen(Text title) { super(title); }

    @Override
    public final void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        long started = UiPerformance.enabled() ? System.nanoTime() : 0;
        float opacity = motion.alpha();
        UiStyle.setFrameAlpha(opacity);
        setWidgetAlpha(opacity);
        try {
            drawScreen(graphics, motion.closing() ? Integer.MIN_VALUE : mouseX, motion.closing() ? Integer.MIN_VALUE : mouseY, delta);
        } finally {
            UiStyle.setFrameAlpha(1.0f);
        }
        motion.finishIfReady();
        if (started != 0) UiPerformance.record(System.nanoTime() - started);
    }

    protected abstract void drawScreen(DrawContext graphics, int mouseX, int mouseY, float delta);

    protected final void renderWidgets(DrawContext graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext graphics, int mouseX, int mouseY, float delta) {
        if (UiStyle.blur()) graphics.applyBlur();
    }

    protected final boolean isClosing() { return motion.closing(); }
    protected final void closeAnimated(Runnable action) { motion.close(action); }

    @Override
    public void close() { closeAnimated(super::close); }

    @Override
    public void tick() {
        super.tick();
        motion.finishIfReady();
    }
}

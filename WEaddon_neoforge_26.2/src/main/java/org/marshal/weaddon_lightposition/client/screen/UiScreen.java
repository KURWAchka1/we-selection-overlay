package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class UiScreen extends Screen {
    private final UiMotion motion = new UiMotion();

    protected UiScreen(Component title) { super(title); }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long started = UiPerformance.enabled() ? System.nanoTime() : 0;
        float opacity = motion.alpha();
        UiStyle.setFrameAlpha(opacity);
        fadeWidgets(opacity);
        try {
            drawScreen(graphics, motion.closing() ? Integer.MIN_VALUE : mouseX, motion.closing() ? Integer.MIN_VALUE : mouseY, delta);
        } finally {
            UiStyle.setFrameAlpha(1.0f);
        }
        motion.finishIfReady();
        if (started != 0) UiPerformance.record(System.nanoTime() - started);
    }

    protected abstract void drawScreen(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);

    protected final void renderWidgets(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (UiStyle.blur()) extractBlurredBackground(graphics);
    }

    protected final boolean isClosing() { return motion.closing(); }
    protected final void closeAnimated(Runnable action) { motion.close(action); }

    @Override
    public void onClose() { closeAnimated(super::onClose); }

    @Override
    public void tick() {
        super.tick();
        motion.finishIfReady();
    }
}

package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Native editing, selection, IME and narration, with only the background changed. */
public final class UiEditBox extends EditBox {
    public UiEditBox(Font font, int x, int y, int width, int height, Component title) {
        super(font, x, y, width, height, title);
        setBordered(UiStyle.current() == UiStyle.LEGACY);
        setTextShadow(false);
    }

    @Override
    public int getInnerWidth() { return UiStyle.current() == UiStyle.LEGACY ? super.getInnerWidth() : Math.max(1, getWidth() - 8); }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        super.onClick(adjust(event), doubleClick);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double x, double y) {
        super.onDrag(adjust(event), x, y);
    }

    private MouseButtonEvent adjust(MouseButtonEvent event) {
        return UiStyle.current() == UiStyle.LEGACY ? event : new MouseButtonEvent(event.x() - 4, event.y(), event.buttonInfo());
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (UiStyle.current() == UiStyle.LEGACY) { super.extractWidgetRenderState(graphics, mouseX, mouseY, delta); return; }
        UiChrome.fillRounded(graphics, getX(), getY(), getWidth(), getHeight(), 0x603A4043);
        if (isFocused()) UiChrome.outline(graphics, getX(), getY(), getWidth(), getHeight(), UiStyle.current().accent);
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(4, (getHeight() - 9) / 2.0f);
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        } finally { pose.popMatrix(); }
    }
}

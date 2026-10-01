package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;

/** Native editing, selection, IME and narration, with only the background changed. */
public final class UiEditBox extends TextFieldWidget {
    public UiEditBox(TextRenderer font, int x, int y, int width, int height, Text title) {
        super(font, x, y, width, height, title);
        setDrawsBackground(UiStyle.current() == UiStyle.LEGACY);
        setTextShadow(false);
    }

    @Override
    public int getInnerWidth() { return UiStyle.current() == UiStyle.LEGACY ? super.getInnerWidth() : Math.max(1, getWidth() - 8); }

    @Override
    public void onClick(Click event, boolean doubleClick) {
        super.onClick(adjust(event), doubleClick);
    }

    @Override
    protected void onDrag(Click event, double x, double y) {
        super.onDrag(adjust(event), x, y);
    }

    private Click adjust(Click event) {
        return UiStyle.current() == UiStyle.LEGACY ? event : new Click(event.x() - 4, event.y(), event.buttonInfo());
    }

    @Override
    public void renderWidget(DrawContext graphics, int mouseX, int mouseY, float delta) {
        if (UiStyle.current() == UiStyle.LEGACY) { super.renderWidget(graphics, mouseX, mouseY, delta); return; }
        UiChrome.fillRounded(graphics, getX(), getY(), getWidth(), getHeight(), 0x603A4043);
        if (isFocused()) UiChrome.outline(graphics, getX(), getY(), getWidth(), getHeight(), UiStyle.current().accent);
        var pose = graphics.getMatrices();
        pose.pushMatrix();
        try {
            pose.translate(4, (getHeight() - 9) / 2.0f);
            super.renderWidget(graphics, mouseX, mouseY, delta);
        } finally { pose.popMatrix(); }
    }
}

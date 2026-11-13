package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Native editing, selection, IME and narration, with only the background changed. */
public final class UiEditBox extends TextFieldWidget {
    public UiEditBox(TextRenderer font, int x, int y, int width, int height, Text title) {
        super(font, x, y, width, height, title);
        setDrawsBackground(UiStyle.current() == UiStyle.LEGACY);
    }

    @Override
    public int getInnerWidth() { return UiStyle.current() == UiStyle.LEGACY ? super.getInnerWidth() : Math.max(1, getWidth() - 8); }

    @Override
    public void onClick(double x, double y) {
        super.onClick(UiStyle.current() == UiStyle.LEGACY ? x : x - 4, y);
    }

    @Override
    public void renderWidget(DrawContext graphics, int mouseX, int mouseY, float delta) {
        if (UiStyle.current() == UiStyle.LEGACY) { super.renderWidget(graphics, mouseX, mouseY, delta); return; }
        UiChrome.fillRounded(graphics, getX(), getY(), getWidth(), getHeight(), 0x603A4043);
        if (isFocused()) UiChrome.outline(graphics, getX(), getY(), getWidth(), getHeight(), UiStyle.current().accent);
        var pose = graphics.getMatrices();
        pose.push();
        try {
            pose.translate(4, (getHeight() - 9) / 2.0f, 0);
            super.renderWidget(graphics, mouseX, mouseY, delta);
        } finally { pose.pop(); }
    }
}

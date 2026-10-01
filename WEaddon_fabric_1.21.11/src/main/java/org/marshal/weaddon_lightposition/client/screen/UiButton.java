package org.marshal.weaddon_lightposition.client.screen;

import java.util.function.BooleanSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

public final class UiButton extends ButtonWidget {
    private long lastFrameNanos = System.nanoTime();
    private float hoverProgress;
    private final UiMotion.Hover hover = new UiMotion.Hover();
    private float checkedProgress;
    private BooleanSupplier checked;
    private BooleanSupplier selected;
    private net.minecraft.text.Text visualLabel;
    private String icon;
    private String trailingIcon;
    private boolean quiet;
    private net.minecraft.text.Text cachedSource;
    private net.minecraft.text.Text cachedLabel;
    private int cachedAvailable = -1;
    private int cachedWidth;

    private UiButton(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.message, builder.onPress, DEFAULT_NARRATION_SUPPLIER);
        checked = builder.checked;
        selected = builder.selected;
        visualLabel = builder.visualLabel;
        icon = builder.icon;
        trailingIcon = builder.trailingIcon;
        quiet = builder.quiet;
        checkedProgress = checked != null && checked.getAsBoolean() ? 1.0f : 0.0f;
        if (builder.tooltip != null) setTooltip(Tooltip.of(builder.tooltip));
    }

    public static Builder styled(net.minecraft.text.Text message, PressAction action) { return new Builder(message, action); }

    @Override
    protected void drawIcon(DrawContext graphics, int mouseX, int mouseY, float delta) {
        if (UiStyle.current() == UiStyle.LEGACY && icon != null) {
            UiChrome.nativeButton(graphics, getX(), getY(), getWidth(), getHeight(), active, isSelected());
            UiChrome.icon(graphics, icon, getX() + (getWidth() - 14) / 2, getY() + (getHeight() - 14) / 2, 14, active ? 0xFFFFFFFF : 0xFFA0A0A0);
            return;
        }
        long now = System.nanoTime();
        float seconds = Math.min(0.1f, Math.max(0.0f, (now - lastFrameNanos) / 1_000_000_000.0f));
        lastFrameNanos = now;
        float blend = UiStyle.reducedMotion() ? 1.0f : 1.0f - (float) Math.exp(-seconds * 18.0f);
        hoverProgress = hover.value(isSelected() && active);
        if (checked != null) checkedProgress += ((checked.getAsBoolean() ? 1.0f : 0.0f) - checkedProgress) * blend;
        UiStyle style = UiStyle.current();
        boolean isSelected = selected != null && selected.getAsBoolean();
        int fill = checked != null || quiet ? UiChrome.blend(0x002F3436, style.raised, hoverProgress) : style.raised;
        if (isSelected) fill = UiChrome.blend(style.raised, style.accent, 0.24f);
        else if (checked == null) fill = UiChrome.blend(fill, style.accent, hoverProgress * 0.12f);
        if (!active && checked == null && !quiet) fill = UiChrome.blend(fill, style.surface, 0.4f);
        UiChrome.fillRounded(graphics, getX(), getY(), getWidth(), getHeight(), fill);
        if (isFocused()) UiChrome.outline(graphics, getX(), getY(), getWidth(), getHeight(), style.accent);
        var font = MinecraftClient.getInstance().textRenderer;
        int textColor = active ? style.text : style.secondaryText;
        int labelLeft = getX() + 8;
        int labelRight = getX() + getWidth() - 8;
        if (checked != null) {
            int tx = getX() + getWidth() - 31;
            int ty = getY() + (getHeight() - 14) / 2;
            UiChrome.fillRounded(graphics, tx, ty, 26, 14, UiChrome.blend(0xFF565D60, style.accent, checkedProgress));
            UiChrome.fillRounded(graphics, tx + 2 + Math.round(12 * checkedProgress), ty + 2, 10, 10, 0xFFF4F6F4);
            labelRight = tx - 6;
        }
        if (icon != null) {
            int ix = visualLabel == null ? getX() + (getWidth() - 14) / 2 : labelLeft;
            UiChrome.icon(graphics, icon, ix, getY() + (getHeight() - 14) / 2, 14, textColor);
            if (visualLabel == null) return;
            labelLeft += 20;
        }
        if (trailingIcon != null) {
            UiChrome.icon(graphics, trailingIcon, labelRight - 14, getY() + (getHeight() - 14) / 2, 14, textColor);
            labelRight -= 22;
        }
        net.minecraft.text.Text source = visualLabel == null ? getMessage() : visualLabel;
        int available = Math.max(1, labelRight - labelLeft);
        if (source != cachedSource || cachedAvailable != available) {
            cachedSource = source;
            cachedAvailable = available;
            cachedLabel = UiChrome.label(source);
            cachedWidth = font.getWidth(cachedLabel);
        }
        net.minecraft.text.Text text = cachedLabel;
        graphics.enableScissor(labelLeft, getY(), labelRight, getY() + getHeight());
        try {
            int textWidth = cachedWidth;
            int textX = checked != null || quiet ? labelLeft : labelLeft + Math.max(0, (available - textWidth) / 2);
            if (textWidth <= available) {
                graphics.drawText(font, text, textX, getY() + (getHeight() - 9) / 2, UiStyle.color(textColor), false);
            } else {
                float scale = available / (float) textWidth;
                var pose = graphics.getMatrices();
                pose.pushMatrix();
                try {
                    pose.translate(labelLeft, getY() + (getHeight() - 9 * scale) / 2);
                    pose.scale(scale, scale);
                    graphics.drawText(font, text, 0, 0, UiStyle.color(textColor), false);
                } finally { pose.popMatrix(); }
            }
        } finally { graphics.disableScissor(); }
    }

    public static final class Builder {
        private final net.minecraft.text.Text message;
        private final PressAction onPress;
        private int x, y, width = 100, height = 20;
        private BooleanSupplier checked, selected;
        private net.minecraft.text.Text visualLabel, tooltip;
        private String icon;
        private String trailingIcon;
        private boolean quiet;

        private Builder(net.minecraft.text.Text message, PressAction action) { this.message = message; onPress = action; }
        public Builder dimensions(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; return this; }
        public Builder toggle(BooleanSupplier value, net.minecraft.text.Text label) { checked = value; visualLabel = label; return this; }
        public Builder selected(BooleanSupplier value) { selected = value; return this; }
        public Builder icon(String name) { icon = name; return this; }
        public Builder trailingIcon(String name) { trailingIcon = name; return this; }
        public Builder tooltip(net.minecraft.text.Text text) { tooltip = text; return this; }
        public Builder quiet() { quiet = true; return this; }
        public ButtonWidget build() {
            if (UiStyle.current() == UiStyle.LEGACY && icon == null) {
                ButtonWidget result = ButtonWidget.builder(message, onPress).dimensions(x, y, width, height).build();
                if (tooltip != null) result.setTooltip(Tooltip.of(tooltip));
                return result;
            }
            return new UiButton(this);
        }
    }
}

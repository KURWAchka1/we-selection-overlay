package org.marshal.weaddon_lightposition.client.screen;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public final class UiChrome {
    private static final Identifier SURFACE = id("textures/gui/ui/surface.png");
    private static final FontDescription UI_FONT = new FontDescription.Resource(id("ui"));
    private static final RenderPipeline FILL = pipeline(false);
    private static final RenderPipeline EDGE = pipeline(true);
    private static final RenderPipeline SPECTRUM = pipeline("core/ui_spectrum", false);

    private UiChrome() { }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("worldedit_selection_overlay", path);
    }

    private static RenderPipeline pipeline(boolean outline) {
        return pipeline("core/ui_surface", outline);
    }

    private static RenderPipeline pipeline(String fragment, boolean outline) {
        RenderPipeline source = RenderPipelines.GUI_TEXTURED;
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id(outline ? "pipeline/ui_edge" : fragment.equals("core/ui_spectrum") ? "pipeline/ui_spectrum" : "pipeline/ui_surface"))
                .withVertexShader(source.getVertexShader())
                .withFragmentShader(id(fragment))
                .withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode())
                .withDepthTestFunction(source.getDepthTestFunction()).withDepthWrite(false)
                .withCull(false);
        source.getBlendFunction().ifPresent(builder::withBlend);
        for (var uniform : source.getUniforms()) builder.withUniform(uniform.name(), uniform.type());
        for (String sampler : source.getSamplers()) builder.withSampler(sampler);
        if (outline) builder.withShaderDefine("SURFACE_EDGE");
        return builder.build();
    }

    public static Component label(Component value) {
        return UiStyle.current() == UiStyle.LEGACY ? value : value.copy().withStyle(style -> style.withFont(UI_FONT));
    }

    public static int width(Font font, Component value) { return font.width(label(value)); }
    public static int width(Font font, String value) { return width(font, Component.literal(value)); }
    public static int width(Font font, FormattedCharSequence value) { return font.width(value); }

    public static String trim(Font font, String value, int width) {
        int low = 0, high = value.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (width(font, value.substring(0, middle)) <= width) low = middle;
            else high = middle - 1;
        }
        if (low > 0 && low < value.length() && Character.isHighSurrogate(value.charAt(low - 1))) low--;
        return value.substring(0, low);
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height, UiStyle style) {
        fillRounded(graphics, x, y, width, height, style.surface);
        outline(graphics, x, y, width, height, style.border);
    }

    public static void popup(GuiGraphics graphics, int x, int y, int width, int height, UiStyle style) {
        fillRounded(graphics, x, y, width, height, 0xFF252A2D);
        outline(graphics, x, y, width, height, style.border);
    }

    public static void slider(GuiGraphics graphics, int x, int y, int width, float progress, int accent, boolean enabled) {
        progress = Math.max(0, Math.min(1, progress));
        fillRounded(graphics, x, y + 5, width, 4, enabled ? 0x804D5559 : 0x404D5559);
        int center = x + 4 + Math.round(Math.max(0, width - 8) * progress);
        fillRounded(graphics, x, y + 5, Math.max(4, center - x), 4, enabled ? accent : 0x605C6569);
        fillRounded(graphics, center - 4, y + 1, 8, 12, enabled ? 0xFFF1F4F2 : 0xFF747D80);
    }

    public static void spectrum(GuiGraphics graphics, int x, int y, int width, int height, int hue) {
        graphics.blit(SPECTRUM, SURFACE, x, y, 0, 0, width, height, 32, 32, 32, 32, UiStyle.color(hue));
    }

    public static void fillRounded(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (UiStyle.current() == UiStyle.LEGACY) {
            fill(graphics, x, y, x + width, y + height, color);
        } else {
            surface(graphics, FILL, x, y, width, height, color);
        }
    }

    public static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (UiStyle.current() == UiStyle.LEGACY) {
            graphics.renderOutline(x, y, width, height, color);
        } else {
            surface(graphics, EDGE, x, y, width, height, color);
        }
    }

    public static void squareOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.renderOutline(x, y, width, height, UiStyle.color(color));
    }

    public static void nativeButton(GuiGraphics graphics, int x, int y, int width, int height, boolean active, boolean hovered) {
        String sprite = active ? hovered ? "widget/button_highlighted" : "widget/button" : "widget/button_disabled";
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("minecraft", sprite), x, y, width, height, UiStyle.color(0xFFFFFFFF));
    }

    private static void surface(GuiGraphics graphics, RenderPipeline pipeline, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
        int corner = Math.min(8, Math.min(width, height) / 2);
        int tint = UiStyle.color(color);
        // Submit nine textured quads to Minecraft's normal GUI batch. No per-panel GPU allocation.
        for (int row = 0; row < 3; row++) {
            int sy = row == 0 ? 0 : row == 1 ? 8 : 24;
            int sh = row == 1 ? 16 : 8;
            int dy = row == 0 ? y : row == 1 ? y + corner : y + height - corner;
            int dh = row == 1 ? height - corner * 2 : corner;
            for (int column = 0; column < 3; column++) {
                int sx = column == 0 ? 0 : column == 1 ? 8 : 24;
                int sw = column == 1 ? 16 : 8;
                int dx = column == 0 ? x : column == 1 ? x + corner : x + width - corner;
                int dw = column == 1 ? width - corner * 2 : corner;
                if (dw > 0 && dh > 0) graphics.blit(pipeline, SURFACE, dx, dy, sx, sy, dw, dh, sw, sh, 32, 32, tint);
            }
        }
    }

    public static void icon(GuiGraphics graphics, String name, int x, int y, int size, int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, id("textures/gui/ui/" + name + ".png"), x, y, 0, 0, size, size, 48, 48, 48, 48, UiStyle.color(color));
    }

    public static void fill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, UiStyle.color(color));
    }

    public static void text(GuiGraphics graphics, Font font, Component value, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, label(value), x, y, UiStyle.color(color), shadow);
    }

    public static void text(GuiGraphics graphics, Font font, String value, int x, int y, int color, boolean shadow) {
        text(graphics, font, Component.literal(value), x, y, color, shadow);
    }

    public static void text(GuiGraphics graphics, Font font, FormattedCharSequence value, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, value, x, y, UiStyle.color(color), shadow);
    }

    public static void centeredText(GuiGraphics graphics, Font font, Component value, int x, int y, int color) {
        Component styled = label(value);
        graphics.drawString(font, styled, x - font.width(styled) / 2, y, UiStyle.color(color), false);
    }

    public static int blend(int from, int to, float amount) {
        amount = Math.max(0.0f, Math.min(1.0f, amount));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            result |= Math.round(((from >>> shift) & 255) + (((to >>> shift) & 255) - ((from >>> shift) & 255)) * amount) << shift;
        }
        return result;
    }
}

package org.marshal.weaddon_lightposition.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.text.OrderedText;

public final class UiChrome {
    private static final Identifier SURFACE = id("textures/gui/ui/surface.png");
    private static final Identifier UI_FONT = id("ui");
    private static final BufferBuilder QUADS = new BufferBuilder(2048);
    private static ShaderProgram fillShader, edgeShader, spectrumShader;

    private UiChrome() { }

    private static Identifier id(String path) {
        return new Identifier("worldedit_selection_overlay", path);
    }

    public static void initialize() {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(id("ui_surface"), VertexFormats.POSITION_TEXTURE_COLOR, shader -> fillShader = shader);
            context.register(id("ui_edge"), VertexFormats.POSITION_TEXTURE_COLOR, shader -> edgeShader = shader);
            context.register(id("ui_spectrum"), VertexFormats.POSITION_TEXTURE_COLOR, shader -> spectrumShader = shader);
        });
    }

    public static Text label(Text value) {
        return UiStyle.current() == UiStyle.LEGACY ? value : value.copy().styled(style -> style.withFont(UI_FONT));
    }

    public static int width(TextRenderer font, Text value) { return font.getWidth(label(value)); }
    public static int width(TextRenderer font, String value) { return width(font, Text.literal(value)); }
    public static int width(TextRenderer font, OrderedText value) { return font.getWidth(value); }

    public static String trim(TextRenderer font, String value, int width) {
        int low = 0, high = value.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (width(font, value.substring(0, middle)) <= width) low = middle;
            else high = middle - 1;
        }
        if (low > 0 && low < value.length() && Character.isHighSurrogate(value.charAt(low - 1))) low--;
        return value.substring(0, low);
    }

    public static void panel(DrawContext graphics, int x, int y, int width, int height, UiStyle style) {
        fillRounded(graphics, x, y, width, height, style.surface);
        outline(graphics, x, y, width, height, style.border);
    }

    public static void popup(DrawContext graphics, int x, int y, int width, int height, UiStyle style) {
        fillRounded(graphics, x, y, width, height, 0xFF252A2D);
        outline(graphics, x, y, width, height, style.border);
    }

    public static void slider(DrawContext graphics, int x, int y, int width, float progress, int accent, boolean enabled) {
        progress = Math.max(0, Math.min(1, progress));
        fillRounded(graphics, x, y + 5, width, 4, enabled ? 0x804D5559 : 0x404D5559);
        int center = x + 4 + Math.round(Math.max(0, width - 8) * progress);
        fillRounded(graphics, x, y + 5, Math.max(4, center - x), 4, enabled ? accent : 0x605C6569);
        fillRounded(graphics, center - 4, y + 1, 8, 12, enabled ? 0xFFF1F4F2 : 0xFF747D80);
    }

    public static void spectrum(DrawContext graphics, int x, int y, int width, int height, int hue) {
        begin(graphics, spectrumShader, SURFACE);
        quad(graphics, x, y, width, height, 0, 0, 1, 1, UiStyle.color(hue));
        end();
    }

    public static void fillRounded(DrawContext graphics, int x, int y, int width, int height, int color) {
        if (UiStyle.current() == UiStyle.LEGACY) {
            fill(graphics, x, y, x + width, y + height, color);
        } else {
            surface(graphics, fillShader, x, y, width, height, color);
        }
    }

    public static void outline(DrawContext graphics, int x, int y, int width, int height, int color) {
        if (UiStyle.current() == UiStyle.LEGACY) {
            graphics.drawBorder(x, y, width, height, UiStyle.color(color));
        } else {
            surface(graphics, edgeShader, x, y, width, height, color);
        }
    }

    public static void squareOutline(DrawContext graphics, int x, int y, int width, int height, int color) {
        graphics.drawBorder(x, y, width, height, UiStyle.color(color));
    }

    public static void nativeButton(DrawContext graphics, int x, int y, int width, int height, boolean active, boolean hovered) {
        String sprite = active ? hovered ? "widget/button_highlighted" : "widget/button" : "widget/button_disabled";
        graphics.setShaderColor(1, 1, 1, (UiStyle.color(0xFFFFFFFF) >>> 24) / 255f);
        try { graphics.drawGuiTexture(new Identifier("minecraft", sprite), x, y, width, height); }
        finally { graphics.setShaderColor(1, 1, 1, 1); }
    }

    private static void surface(DrawContext graphics, ShaderProgram shader, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
        int corner = Math.min(8, Math.min(width, height) / 2);
        int tint = UiStyle.color(color);
        begin(graphics, shader, SURFACE);
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
                if (dw > 0 && dh > 0) quad(graphics, dx, dy, dw, dh, sx / 32f, sy / 32f, (sx + sw) / 32f, (sy + sh) / 32f, tint);
            }
        }
        end();
    }

    public static void icon(DrawContext graphics, String name, int x, int y, int size, int color) {
        begin(graphics, GameRenderer.getPositionTexColorProgram(), id("textures/gui/ui/" + name + ".png"));
        quad(graphics, x, y, size, size, 0, 0, 1, 1, UiStyle.color(color));
        end();
    }

    private static void begin(DrawContext graphics, ShaderProgram shader, Identifier texture) {
        graphics.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
        QUADS.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
    }

    private static void quad(DrawContext graphics, int x, int y, int w, int h, float u0, float v0, float u1, float v1, int color) {
        var matrix = graphics.getMatrices().peek().getPositionMatrix();
        QUADS.vertex(matrix, x, y, 0).texture(u0, v0).color(color).next();
        QUADS.vertex(matrix, x, y + h, 0).texture(u0, v1).color(color).next();
        QUADS.vertex(matrix, x + w, y + h, 0).texture(u1, v1).color(color).next();
        QUADS.vertex(matrix, x + w, y, 0).texture(u1, v0).color(color).next();
    }

    private static void end() {
        try { BufferRenderer.drawWithGlobalProgram(QUADS.end()); }
        finally { RenderSystem.disableBlend(); }
    }

    public static void fill(DrawContext graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, UiStyle.color(color));
    }

    public static void text(DrawContext graphics, TextRenderer font, Text value, int x, int y, int color, boolean shadow) {
        int tint = UiStyle.color(color);
        if (tint >>> 24 > 3) graphics.drawText(font, label(value), x, y, tint, shadow);
    }

    public static void text(DrawContext graphics, TextRenderer font, String value, int x, int y, int color, boolean shadow) {
        text(graphics, font, Text.literal(value), x, y, color, shadow);
    }

    public static void text(DrawContext graphics, TextRenderer font, OrderedText value, int x, int y, int color, boolean shadow) {
        graphics.drawText(font, value, x, y, UiStyle.color(color), shadow);
    }

    public static void centeredText(DrawContext graphics, TextRenderer font, Text value, int x, int y, int color) {
        Text styled = label(value);
        graphics.drawText(font, styled, x - font.getWidth(styled) / 2, y, UiStyle.color(color), false);
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

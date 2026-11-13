package org.marshal.weaddon_lightposition.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Frame-local, camera-relative geometry for the pre-Gizmo world renderer. */
public final class LegacyOverlay {
    public record Style(int stroke, float width, int fill) {
        public static Style stroked(int color, float width) { return new Style(color, width, 0); }
        public static Style filled(int color) { return new Style(0, 0, color); }
        public static Style filledAndStroked(int stroke, float width, int fill) { return new Style(stroke, width, fill); }
    }

    private static final BufferBuilder FILLS = new BufferBuilder(4096);
    private static final Map<Float, BufferBuilder> LINES = new LinkedHashMap<>();
    private static Vec3d camera;
    private static Matrix4f matrix;
    private static boolean emitted;

    private LegacyOverlay() { }

    public static void render(WorldRenderContext context, Runnable draw) {
        if (context.matrixStack() == null) return;
        camera = context.camera().getPos();
        matrix = context.matrixStack().peek().getPositionMatrix();
        emitted = false;
        try {
            draw.run();
        } finally {
            try { if (emitted) flush(); }
            finally {
                camera = null;
                matrix = null;
            }
        }
    }

    private static void flush() {
        float width = RenderSystem.getShaderLineWidth();
        var shader = RenderSystem.getShader();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            if (FILLS.isBuilding()) submit(FILLS);
            RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
            for (var entry : LINES.entrySet()) {
                var buffer = entry.getValue();
                if (!buffer.isBuilding()) continue;
                RenderSystem.lineWidth(entry.getKey());
                submit(buffer);
            }
        } finally {
            RenderSystem.lineWidth(width);
            RenderSystem.setShader(() -> shader);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
        if (LINES.size() > 48) {
            LINES.values().forEach(BufferBuilder::close);
            LINES.clear();
        }
    }

    private static void submit(BufferBuilder buffer) {
        var built = buffer.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }

    public static void line(Vec3d from, Vec3d to, int color, float width) {
        if (camera == null || color >>> 24 == 0 || width <= 0 || from.squaredDistanceTo(to) < 1e-14) return;
        emitted = true;
        Vec3d normal = to.subtract(from).normalize();
        BufferBuilder buffer = LINES.computeIfAbsent(width, ignored -> new BufferBuilder(4096));
        if (!buffer.isBuilding()) buffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        lineVertex(buffer, from, normal, color);
        lineVertex(buffer, to, normal, color);
    }

    private static net.minecraft.client.render.VertexConsumer vertex(BufferBuilder buffer, Vec3d point, int color) {
        return buffer.vertex(matrix, (float) (point.x - camera.x),
                (float) (point.y - camera.y), (float) (point.z - camera.z)).color(color);
    }


    private static void lineVertex(BufferBuilder buffer, Vec3d point, Vec3d normal, int color) {
        var direction = matrix.transformDirection((float) normal.x, (float) normal.y, (float) normal.z, new org.joml.Vector3f());
        vertex(buffer, point, color).normal(direction.x, direction.y, direction.z).next();
    }

    public static void arrow(Vec3d from, Vec3d tip, int color, float width) {
        if (camera == null) return;
        line(from, tip, color, width);
        Vec3d axis = tip.subtract(from);
        Vec3d side = axis.crossProduct(camera.subtract(tip)).normalize().multiply(axis.length() * 0.28);
        Vec3d base = from.lerp(tip, 0.35);
        line(tip, base.add(side), color, width);
        line(tip, base.subtract(side), color, width);
    }

    public static void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, Style style) {
        if (camera == null) return;
        if (style.fill >>> 24 != 0) {
            emitted = true;
            if (!FILLS.isBuilding()) FILLS.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            vertex(FILLS, a, style.fill).next();
            vertex(FILLS, b, style.fill).next();
            vertex(FILLS, c, style.fill).next();
            vertex(FILLS, d, style.fill).next();
        }
        line(a, b, style.stroke, style.width);
        line(b, c, style.stroke, style.width);
        line(c, d, style.stroke, style.width);
        line(d, a, style.stroke, style.width);
    }

    public static void box(Box box, Style style) {
        Vec3d a = new Vec3d(box.minX, box.minY, box.minZ);
        Vec3d b = new Vec3d(box.maxX, box.minY, box.minZ);
        Vec3d c = new Vec3d(box.maxX, box.minY, box.maxZ);
        Vec3d d = new Vec3d(box.minX, box.minY, box.maxZ);
        double dy = box.maxY - box.minY;
        Vec3d e = a.add(0, dy, 0), f = b.add(0, dy, 0), g = c.add(0, dy, 0), h = d.add(0, dy, 0);
        quad(a, b, c, d, style);
        quad(e, f, g, h, style);
        Style fill = Style.filled(style.fill);
        quad(a, b, f, e, fill);
        quad(b, c, g, f, fill);
        quad(c, d, h, g, fill);
        quad(d, a, e, h, fill);
        line(a, e, style.stroke, style.width);
        line(b, f, style.stroke, style.width);
        line(c, g, style.stroke, style.width);
        line(d, h, style.stroke, style.width);
    }
}

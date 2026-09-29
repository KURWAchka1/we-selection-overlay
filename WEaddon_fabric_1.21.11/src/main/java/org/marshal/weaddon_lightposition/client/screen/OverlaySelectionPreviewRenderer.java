package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.marshal.weaddon_lightposition.client.overlay.LineSegment;
import org.marshal.weaddon_lightposition.client.overlay.OverlayColorSlot;
import org.marshal.weaddon_lightposition.client.overlay.OverlayRenderStyle;
import org.marshal.weaddon_lightposition.client.overlay.Point2i;
import org.marshal.weaddon_lightposition.client.overlay.RegionShape;
import org.marshal.weaddon_lightposition.client.overlay.RenderMode;
import org.marshal.weaddon_lightposition.client.overlay.SelectionRegion;

import java.util.ArrayList;
import java.util.List;

final class OverlaySelectionPreviewRenderer {
    private static final int MAX_PREVIEW_SEGMENTS = 1800;
    private static final SelectionRegion CUBOID_REGION = createCuboid();
    private static final SelectionRegion POLYGON_REGION = createPolygon();
    private static final SelectionRegion ELLIPSOID_REGION = createEllipsoid();
    private static final SelectionRegion CYLINDER_REGION = createCylinder();
    private static final SelectionRegion POLYHEDRON_REGION = createPolyhedron();

    private OverlaySelectionPreviewRenderer() {
    }

    static void render(
            DrawContext context,
            OverlayColorSettingsScreen.ColorGroup group,
            int x,
            int y,
            int width,
            int height,
            float yaw,
            float pitch,
            float zoom,
            RenderMode renderMode
    ) {
        drawViewportBackground(context, x, y, width, height);
        SelectionRegion region = regionForGroup(group);
        if (region != null) {
            renderRegion(context, region, x, y, width, height, yaw, pitch, zoom, renderMode);
        }
        if (group == OverlayColorSettingsScreen.ColorGroup.RULER) {
            renderRulerPreview(context, x, y, width, height);
        }
        drawOutline(context, x, y, width, height, 0xFF2E3B4D);
    }

    private static void renderRegion(
            DrawContext context,
            SelectionRegion region,
            int x,
            int y,
            int width,
            int height,
            float yaw,
            float pitch,
            float zoom,
            RenderMode renderMode
    ) {
        List<LineSegment> segments = region.previewSegments(renderMode == null ? RenderMode.BLOCKY : renderMode, false);
        if (segments.isEmpty()) {
            return;
        }

        Bounds bounds = bounds(segments);
        Vec3d center = bounds.center();
        double maxSpan = Math.max(1.0, bounds.maxSpan());
        double scale = Math.min(width, height) * 0.50 * zoom / maxSpan;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);

        int step = Math.max(1, (segments.size() + MAX_PREVIEW_SEGMENTS - 1) / MAX_PREVIEW_SEGMENTS);
        ArrayList<ProjectedSegment> projected = new ArrayList<>(Math.min(segments.size(), MAX_PREVIEW_SEGMENTS));
        for (int i = 0; i < segments.size(); i += step) {
            LineSegment segment = segments.get(i);
            ProjectedPoint from = project(segment.from(), center, cosYaw, sinYaw, cosPitch, sinPitch, x + width * 0.5, y + height * 0.52, scale);
            ProjectedPoint to = project(segment.to(), center, cosYaw, sinYaw, cosPitch, sinPitch, x + width * 0.5, y + height * 0.52, scale);
            if (!from.visible() || !to.visible()) {
                continue;
            }
            projected.add(new ProjectedSegment(from, to, segment.color(), Math.max(1.0f, segment.width()), segment.slot(), (from.depth() + to.depth()) * 0.5));
        }

        context.enableScissor(x, y, x + width, y + height);
        try {
            for (ProjectedSegment segment : projected) {
                drawLineClipped(
                        context,
                        segment.from().x(),
                        segment.from().y(),
                        segment.to().x(),
                        segment.to().y(),
                        segment.color(),
                        previewGuiLineWidth(OverlayRenderStyle.lineWidth(segment.width(), segment.slot())),
                        x,
                        y,
                        width,
                        height
                );
            }
        } finally {
            context.disableScissor();
        }
    }

    private static ProjectedPoint project(
            Vec3d point,
            Vec3d center,
            double cosYaw,
            double sinYaw,
            double cosPitch,
            double sinPitch,
            double centerX,
            double centerY,
            double scale
    ) {
        double x = point.getX() - center.getX();
        double y = point.getY() - center.getY();
        double z = point.getZ() - center.getZ();

        double yawX = x * cosYaw - z * sinYaw;
        double yawZ = x * sinYaw + z * cosYaw;
        double pitchY = y * cosPitch - yawZ * sinPitch;
        double pitchZ = y * sinPitch + yawZ * cosPitch;
        double screenX = centerX + yawX * scale;
        double screenY = centerY - pitchY * scale;
        return new ProjectedPoint(screenX, screenY, pitchZ, Double.isFinite(screenX) && Double.isFinite(screenY));
    }

    private static void renderRulerPreview(DrawContext context, int x, int y, int width, int height) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        int lineColor = OverlayRenderStyle.color(OverlayColorSlot.RULER_LINE);
        int arrowColor = OverlayRenderStyle.color(OverlayColorSlot.RULER_ARROW);
        int textColor = OverlayRenderStyle.color(OverlayColorSlot.RULER_TEXT);
        int bgColor = OverlayRenderStyle.color(OverlayColorSlot.RULER_LABEL_BACKGROUND);
        int borderColor = OverlayRenderStyle.color(OverlayColorSlot.RULER_LABEL_BORDER);
        int length = Math.min(width - 160, 420);
        int startX = centerX - length / 2;
        int endX = centerX + length / 2;
        int yLine = centerY + 24;
        drawLine(context, startX, yLine, endX, yLine, lineColor, previewGuiLineWidth(OverlayRenderStyle.lineWidth(2.0f, OverlayColorSlot.RULER_LINE)));
        drawLine(context, startX, yLine, startX + 18, yLine - 12, arrowColor, previewGuiLineWidth(OverlayRenderStyle.lineWidth(2.0f, OverlayColorSlot.RULER_ARROW)));
        drawLine(context, startX, yLine, startX + 18, yLine + 12, arrowColor, previewGuiLineWidth(OverlayRenderStyle.lineWidth(2.0f, OverlayColorSlot.RULER_ARROW)));
        drawLine(context, endX, yLine, endX - 18, yLine - 12, arrowColor, previewGuiLineWidth(OverlayRenderStyle.lineWidth(2.0f, OverlayColorSlot.RULER_ARROW)));
        drawLine(context, endX, yLine, endX - 18, yLine + 12, arrowColor, previewGuiLineWidth(OverlayRenderStyle.lineWidth(2.0f, OverlayColorSlot.RULER_ARROW)));

        int labelWidth = 70;
        int labelHeight = 26;
        context.fill(centerX - labelWidth / 2, yLine - 48, centerX + labelWidth / 2, yLine - 48 + labelHeight, bgColor);
        drawOutline(context, centerX - labelWidth / 2, yLine - 48, labelWidth, labelHeight, borderColor);
        context.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, "128", centerX - 10, yLine - 39, textColor, false);
    }

    private static void drawViewportBackground(DrawContext context, int x, int y, int width, int height) {
        context.fillGradient(x, y, x + width, y + height, 0xFF111927, 0xFF070A0F);
    }

    private static Bounds bounds(List<LineSegment> segments) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (LineSegment segment : segments) {
            Vec3d[] points = {segment.from(), segment.to()};
            for (Vec3d point : points) {
                minX = Math.min(minX, point.getX());
                minY = Math.min(minY, point.getY());
                minZ = Math.min(minZ, point.getZ());
                maxX = Math.max(maxX, point.getX());
                maxY = Math.max(maxY, point.getY());
                maxZ = Math.max(maxZ, point.getZ());
            }
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static SelectionRegion regionForGroup(OverlayColorSettingsScreen.ColorGroup group) {
        return switch (group) {
            case CUBOID -> CUBOID_REGION;
            case POLYGON -> POLYGON_REGION;
            case ELLIPSOID -> ELLIPSOID_REGION;
            case CYLINDER -> CYLINDER_REGION;
            case POLYHEDRON -> POLYHEDRON_REGION;
            case RULER -> null;
        };
    }

    private static SelectionRegion createCuboid() {
        SelectionRegion region = new SelectionRegion(RegionShape.CUBOID);
        region.applyPoint3D(0, new Vec3d(0, 0, 0));
        region.applyPoint3D(1, new Vec3d(6, 4, 5));
        region.applyGrid(1.0, false);
        return region;
    }

    private static SelectionRegion createPolygon() {
        SelectionRegion region = new SelectionRegion(RegionShape.POLYGON_2D);
        List<Point2i> points = List.of(
                new Point2i(-4, -2),
                new Point2i(-1, -5),
                new Point2i(4, -4),
                new Point2i(6, 1),
                new Point2i(1, 4),
                new Point2i(-5, 2)
        );
        for (int i = 0; i < points.size(); i++) {
            Point2i point = points.get(i);
            region.applyPoint2D(i, point.x(), point.z());
        }
        region.applyMinMax(0, 4);
        return region;
    }

    private static SelectionRegion createEllipsoid() {
        SelectionRegion region = new SelectionRegion(RegionShape.ELLIPSOID);
        region.applyEllipsoidCenter(new Vec3d(0, 2, 0));
        region.applyEllipsoidRadii(new Vec3d(4.5, 2.8, 3.5));
        return region;
    }

    private static SelectionRegion createCylinder() {
        SelectionRegion region = new SelectionRegion(RegionShape.CYLINDER);
        region.applyCylinderCenter(new Vec3d(0, 0, 0));
        region.applyCylinderRadii(4.0, 3.0);
        region.applyMinMax(0, 5);
        return region;
    }

    private static SelectionRegion createPolyhedron() {
        SelectionRegion region = new SelectionRegion(RegionShape.POLYHEDRON);
        region.applyPoint3D(0, new Vec3d(-3, 0, -2));
        region.applyPoint3D(1, new Vec3d(4, 0, -2));
        region.applyPoint3D(2, new Vec3d(5, 0, 3));
        region.applyPoint3D(3, new Vec3d(-2, 0, 4));
        region.applyPoint3D(4, new Vec3d(0, 5, 0));
        region.applyPolygonFace(new int[]{0, 1, 2, 3});
        region.applyPolygonFace(new int[]{0, 1, 4});
        region.applyPolygonFace(new int[]{1, 2, 4});
        region.applyPolygonFace(new int[]{2, 3, 4});
        region.applyPolygonFace(new int[]{3, 0, 4});
        return region;
    }

    private static void drawLine(DrawContext context, double x1, double y1, double x2, double y2, int color, float width) {
        drawLineClipped(context, x1, y1, x2, y2, color, width, -1_000_000, -1_000_000, 2_000_000, 2_000_000);
    }

    private static void drawLineClipped(
            DrawContext context,
            double x1,
            double y1,
            double x2,
            double y2,
            int color,
            float width,
            int clipX,
            int clipY,
            int clipWidth,
            int clipHeight
    ) {
        if ((color >>> 24) == 0) {
            return;
        }
        float normalizedWidth = Math.max(minGuiLineWidth(), width);
        int margin = Math.max(4, (int) Math.ceil(normalizedWidth) + 3);
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        if (maxX < clipX - margin || minX > clipX + clipWidth + margin || maxY < clipY - margin || minY > clipY + clipHeight + margin) {
            return;
        }

        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (!Double.isFinite(length) || length < 0.25) {
            drawLineBody(context, x1 - 0.5, y1, x1 + 0.5, y1, color, normalizedWidth);
            return;
        }

        drawLineBody(context, x1, y1, x2, y2, color, normalizedWidth);
    }

    private static void drawLineBody(DrawContext context, double x1, double y1, double x2, double y2, int color, float width) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (!Double.isFinite(length) || length < 0.25) {
            return;
        }
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate((float) x1, (float) y1);
            matrices.rotate((float) Math.atan2(dy, dx));
            matrices.translate(0.0f, -width * 0.5f);
            matrices.scale(1.0f, width);
            context.fill(0, 0, (int) Math.ceil(length), 1, color);
        } finally {
            matrices.popMatrix();
        }
    }

    private static float previewGuiLineWidth(float worldLineWidth) {
        return Math.max(minGuiLineWidth(), worldLineWidth / guiScaleFactor());
    }

    private static float minGuiLineWidth() {
        return 1.0f / guiScaleFactor();
    }

    private static float guiScaleFactor() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return 1.0f;
        }
        return Math.max(1.0f, client.getWindow().getScaleFactor());
    }

    private static void drawOutline(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private record ProjectedPoint(double x, double y, double depth, boolean visible) {
    }

    private record ProjectedSegment(ProjectedPoint from, ProjectedPoint to, int color, float width, OverlayColorSlot slot, double depth) {
    }

    private record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private Vec3d center() {
            return new Vec3d((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
        }

        private double maxSpan() {
            return Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        }
    }
}

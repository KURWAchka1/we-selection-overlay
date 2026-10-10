package org.marshal.weaddon_lightposition.client.selection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.marshal.weaddon_lightposition.client.overlay.OverlayColorSlot;
import org.marshal.weaddon_lightposition.client.overlay.OverlayRenderStyle;
import org.marshal.weaddon_lightposition.client.overlay.SelectionEditStyle;
import org.marshal.weaddon_lightposition.client.screen.UiChrome;
import org.marshal.weaddon_lightposition.client.screen.UiStyle;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Corner;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;
import org.marshal.weaddon_lightposition.selection.SelectionEditSnapshot;
import org.marshal.weaddon_lightposition.selection.SelectionEditMode;
import org.marshal.weaddon_lightposition.selection.SelectionEditOutline;
import org.marshal.weaddon_lightposition.selection.SelectionEditOutline.Edge;
import java.util.List;

final class SelectionEditRenderer {
    private static final Face[] FACES = Face.values();
    private static final Corner[] CORNERS = Corner.values();
    private final float[] menuHover = new float[2];
    private long menuStart, menuFrame;
    private CuboidDraft outlineOriginal, outlineDraft;
    private Corner outlineCorner;
    private Face outlineFace;
    private List<Edge> outlineBoundary = List.of(), outlineGhost = List.of(), outlineActive = List.of();
    private final int[] compassOrder = {0, 1, 2};
    private final double[] compassX = new double[3], compassY = new double[3], compassDepth = new double[3];
    private static final Text[] AXIS_LABELS = {Text.literal("X"), Text.literal("Y"), Text.literal("Z")};
    private static final Text[] ACTIVE_AXIS_LABELS = {
            Text.literal("X").styled(style -> style.withBold(true)),
            Text.literal("Y").styled(style -> style.withBold(true)),
            Text.literal("Z").styled(style -> style.withBold(true))};
    record View(Point eye, Point forward, Point right, Point up, double tangent, double aspect, int pixelsHigh) {
        Point ray(double x, double y) { return forward.add(right.scale((x * 2 - 1) * tangent * aspect)).add(up.scale((1 - y * 2) * tangent)).normalized(); }
        double radius(Point p, double pixels) { return p.subtract(eye).dot(forward) * 2 * tangent * pixels / pixelsHigh; }
        double hitDepth(Point center, Point ray, double pixels) {
            Point offset = center.subtract(eye);
            double depth = offset.dot(forward), rayDepth = ray.dot(forward);
            if (depth <= 0.05 || rayDepth <= 0) return Double.POSITIVE_INFINITY;
            double focal = pixelsHigh / (2 * tangent);
            double x = (offset.dot(right) / depth - ray.dot(right) / rayDepth) * focal;
            double y = (offset.dot(up) / depth - ray.dot(up) / rayDepth) * focal;
            return x * x + y * y <= pixels * pixels ? depth : Double.POSITIVE_INFINITY;
        }
    }
    View view(MinecraftClient client) {
        var camera = client.gameRenderer.getCamera();
        var rotation = camera.getRotation();
        return new View(point(camera.getCameraPos()), point(new Vector3f(0, 0, -1).rotate(rotation)),
                point(new Vector3f(1, 0, 0).rotate(rotation)), point(new Vector3f(0, 1, 0).rotate(rotation)),
                Math.tan(Math.toRadians(((org.marshal.weaddon_lightposition.mixin.SelectionEditFovAccessor) client.gameRenderer).weso$editFov(camera, 1.0f, true)) / 2),
                client.getWindow().getWidth() / (double) client.getWindow().getHeight(), client.getWindow().getHeight());
    }
    Face handleHit(CuboidDraft bounds, View view) {
        return handleHit(bounds, view, view.forward);
    }
    Face handleHit(CuboidDraft bounds, View view, Point ray) {
        Face hit = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Face face : FACES) {
            double depth = view.hitDepth(bounds.center(face), ray, 12);
            if (depth < nearest) { hit = face; nearest = depth; }
        }
        return hit;
    }
    Corner cornerHit(CuboidDraft bounds, View view) {
        return cornerHit(bounds, view, view.forward);
    }
    Corner cornerHit(CuboidDraft bounds, View view, Point ray) {
        Corner hit = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Corner corner : CORNERS) {
            double depth = view.hitDepth(bounds.corner(corner), ray, 10);
            if (depth < nearest) { hit = corner; nearest = depth; }
        }
        return hit;
    }
    private static Point center(CuboidDraft bounds) {
        return new Point((bounds.minX() + (double) bounds.maxX() + 1) / 2,
                (bounds.minY() + (double) bounds.maxY() + 1) / 2, (bounds.minZ() + (double) bounds.maxZ() + 1) / 2);
    }
    Point moveHit(CuboidDraft bounds, View view) {
        Point center = center(bounds);
        if (Double.isFinite(view.hitDepth(center, view.forward(), 12))) return center;
        Face face = bounds.intersect(view.eye(), view.forward());
        if (face == null) return null;
        double distance = (bounds.center(face).at(face.axis) - view.eye().at(face.axis)) / view.forward().at(face.axis);
        return view.eye().add(view.forward().scale(Math.max(0, distance)));
    }
    void moveGrip(CuboidDraft bounds, View view, Point anchor, Face wheelAxis, long appeared) {
        Point hover = anchor == null ? moveHit(bounds, view) : null;
        Point point = anchor != null ? anchor : hover != null ? hover : center(bounds);
        if (point.subtract(view.eye()).dot(view.forward()) <= 0.05) return;
        double radius = view.radius(point, 5);
        int color = tint(OverlayRenderStyle.color(OverlayColorSlot.EDIT_BOUNDARY), fade(appeared) * (anchor != null || hover != null ? 1 : 0.65f));
        GizmoDrawing.box(new Box(point.x() - radius, point.y() - radius, point.z() - radius,
                point.x() + radius, point.y() + radius, point.z() + radius), DrawStyle.stroked(color,
                OverlayRenderStyle.lineWidth(SelectionEditStyle.HANDLE_WIDTH, OverlayColorSlot.EDIT_BOUNDARY))).ignoreOcclusion();
        if (wheelAxis != null) {
            Point direction = new Point(wheelAxis.axis == 0 ? 1 : 0, wheelAxis.axis == 1 ? 1 : 0, wheelAxis.axis == 2 ? 1 : 0);
            OverlayColorSlot slot = SelectionEditStyle.axisSlot(wheelAxis.axis);
            GizmoDrawing.line(vec(point.subtract(direction.scale(radius * 3))), vec(point.add(direction.scale(radius * 3))),
                    tint(OverlayRenderStyle.color(slot), fade(appeared)), OverlayRenderStyle.lineWidth(SelectionEditStyle.HANDLE_WIDTH, slot)).ignoreOcclusion();
        }
    }
    void corners(CuboidDraft bounds, Corner target, View view, long appeared, boolean pending) {
        float alpha = fade(appeared) * (pending ? 0.45f : 1);
        for (Corner corner : CORNERS) {
            Point center = bounds.corner(corner);
            if (center.subtract(view.eye).dot(view.forward) <= 0.05) continue;
            double radius = view.radius(center, corner == target ? 8 : 6);
            Point r = view.right.scale(radius), u = view.up.scale(radius);
            GizmoDrawing.quad(vec(center.subtract(u)), vec(center.add(r)), vec(center.add(u)), vec(center.subtract(r)),
                    DrawStyle.filled(tint(OverlayRenderStyle.color(OverlayColorSlot.EDIT_BOUNDARY),
                            alpha * (corner == target ? 1 : 0.65f)))).ignoreOcclusion();
        }
    }
    void world(CuboidDraft original, CuboidDraft draft, Face target, Corner corner, View view, long appeared, boolean pending, boolean moving) {
        float alpha = fade(appeared);
        int boundary = OverlayRenderStyle.color(OverlayColorSlot.EDIT_BOUNDARY);
        float width = OverlayRenderStyle.lineWidth(SelectionEditStyle.BOUNDARY_WIDTH, OverlayColorSlot.EDIT_BOUNDARY);
        Face activeFace = corner == null ? target : null;
        if (moving || corner == null && activeFace == null) {
            if (!original.equals(draft)) GizmoDrawing.box(box(original), DrawStyle.stroked(tint(boundary, alpha * 0.28f), width)).ignoreOcclusion();
            GizmoDrawing.box(box(draft), DrawStyle.stroked(tint(boundary, alpha * 0.65f), width)).ignoreOcclusion();
        } else {
            outline(original, draft, corner, activeFace);
            for (Edge edge : outlineGhost) line(edge, tint(boundary, alpha * 0.28f), width);
            for (Edge edge : outlineBoundary) line(edge, tint(boundary, alpha * 0.65f), width);
            for (Edge edge : outlineActive) {
                OverlayColorSlot slot = SelectionEditStyle.axisSlot(corner != null ? edge.axis() : activeFace.axis);
                line(edge, tint(OverlayRenderStyle.color(slot), alpha * (pending ? 0.45f : 1)),
                        OverlayRenderStyle.lineWidth(SelectionEditStyle.ACTIVE_WIDTH, slot));
            }
        }
        if (moving) return;
        for (Face f : FACES) {
            Point center = draft.center(f);
            if (center.subtract(view.eye).dot(view.forward) <= 0.05) continue;
            double radius = view.radius(center, f == target ? 4.5 : 3.5);
            Point r = view.right.scale(radius), u = view.up.scale(radius);
            OverlayColorSlot slot = f == target ? SelectionEditStyle.axisSlot(f.axis) : OverlayColorSlot.EDIT_BOUNDARY;
            int color = tint(OverlayRenderStyle.color(slot), alpha * (pending ? 0.45f : f == target ? 1 : 0.65f));
            GizmoDrawing.quad(vec(center.subtract(r).subtract(u)), vec(center.add(r).subtract(u)), vec(center.add(r).add(u)),
                    vec(center.subtract(r).add(u)), DrawStyle.filledAndStroked(color, OverlayRenderStyle.lineWidth(SelectionEditStyle.BOUNDARY_WIDTH, slot), color)).ignoreOcclusion();
        }
        corners(draft, corner != null ? corner : cornerHit(draft, view), view, appeared, pending);
    }
    private void outline(CuboidDraft original, CuboidDraft draft, Corner corner, Face face) {
        if (original.equals(outlineOriginal) && draft.equals(outlineDraft) && corner == outlineCorner && face == outlineFace) return;
        outlineActive = SelectionEditOutline.active(draft, corner, face);
        outlineBoundary = SelectionEditOutline.boundary(draft, outlineActive);
        outlineGhost = original.equals(draft) ? List.of() : SelectionEditOutline.boundary(original, outlineActive);
        outlineOriginal = original; outlineDraft = draft; outlineCorner = corner; outlineFace = face;
    }
    private static void line(Edge edge, int color, float width) {
        GizmoDrawing.line(vec(edge.from()), vec(edge.to()), color, width).ignoreOcclusion();
    }

    void hud(DrawContext graphics, CuboidDraft draft, Face face, Corner corner, SelectionEditSnapshot origin, String reason, boolean pending, long appeared, boolean moving, Face wheelAxis) {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth(), screenHeight = client.getWindow().getScaledHeight();
        if (screenWidth < 100 || screenHeight < 100) return;
        UiStyle style = UiStyle.current();
        Text title = draft == null ? Text.translatable("selection_edit.worldedit_selection_overlay." + (reason.isEmpty() ? "reading" : reason))
                : Text.translatable("selection_edit.worldedit_selection_overlay." + (moving ? "move" : "resize"))
                .append("   " + draft.size(0) + " x " + draft.size(1) + " x " + draft.size(2));
        if (moving && wheelAxis != null) title = title.copy().append("   " + wheelAxis.label());
        long offset = draft != null && face != null ? ((long) draft.boundary(face) - origin.bounds().boundary(face)) * face.sign : 0;
        Text detail = pending ? Text.translatable("selection_edit.worldedit_selection_overlay.applying")
                : draft == null ? Text.empty() : Text.translatable("selection_edit.worldedit_selection_overlay.volume", draft.volume().toString())
                .append(moving ? "   X: " + signed((long) draft.minX() - origin.bounds().minX())
                        + " Y: " + signed((long) draft.minY() - origin.bounds().minY())
                        + " Z: " + signed((long) draft.minZ() - origin.bounds().minZ())
                        : corner == null ? face == null ? "" : "   " + face.label() + ": " + signed(offset)
                        : "   X: " + signed((long) draft.boundary(corner.face(0)) - origin.bounds().boundary(corner.face(0)))
                        + " Y: " + signed((long) draft.boundary(corner.face(1)) - origin.bounds().boundary(corner.face(1)))
                        + " Z: " + signed((long) draft.boundary(corner.face(2)) - origin.bounds().boundary(corner.face(2))));
        int width = Math.min(screenWidth - 16, Math.max(120, Math.max(client.textRenderer.getWidth(UiChrome.label(title)), client.textRenderer.getWidth(UiChrome.label(detail))) + 20));
        var titleLines = client.textRenderer.wrapLines(UiChrome.label(title), width - 20);
        var detailLines = client.textRenderer.wrapLines(UiChrome.label(detail), width - 20);
        int height = 14 + 11 * (titleLines.size() + detailLines.size()), x = (screenWidth - width) / 2, y = Math.max(8, screenHeight - 62 - height);
        float alpha = fade(appeared);
        UiChrome.fillRounded(graphics, x, y, width, height, tint(style.surface | 0xFF000000, alpha));
        graphics.enableScissor(x, y, x + width, y + height);
        int lineY = y + 7;
        for (var line : titleLines) { UiChrome.text(graphics, client.textRenderer, line, x + 10, lineY, tint(style.text, alpha), false); lineY += 11; }
        for (var line : detailLines) { UiChrome.text(graphics, client.textRenderer, line, x + 10, lineY, tint(style.secondaryText, alpha), false); lineY += 11; }
        graphics.disableScissor();
        if (moving && wheelAxis != null && !pending) axisCompass(graphics, wheelAxis, appeared);
    }
    private void axisCompass(DrawContext graphics, Face wheelAxis, long appeared) {
        MinecraftClient client = MinecraftClient.getInstance();
        View view = view(client);
        int screenWidth = client.getWindow().getScaledWidth(), screenHeight = client.getWindow().getScaledHeight();
        int cx = screenWidth - 42, cy = screenHeight - 66;
        float alpha = fade(appeared);
        // Project the grip's fixed world axes, not a new look-at direction toward the selection.
        for (int axis = 0; axis < 3; axis++) {
            compassX[axis] = view.right().at(axis) * 23;
            compassY[axis] = -view.up().at(axis) * 23;
            compassDepth[axis] = view.forward().at(axis);
            compassOrder[axis] = axis;
        }
        for (int i = 1; i < 3; i++) {
            int next = compassOrder[i], j = i;
            while (j > 0 && compassDepth[compassOrder[j - 1]] < compassDepth[next]) {
                compassOrder[j] = compassOrder[j - 1]; j--;
            }
            compassOrder[j] = next;
        }
        graphics.enableScissor(screenWidth - 84, screenHeight - 108, screenWidth, screenHeight - 24);
        try {
            for (int index : compassOrder) {
                int color = tint(OverlayRenderStyle.color(SelectionEditStyle.axisSlot(index)), alpha * 0.65f);
                double dx = Math.round(compassX[index]), dy = Math.round(compassY[index]), length = Math.hypot(dx, dy);
                if (length <= 7) continue;
                var matrices = graphics.getMatrices();
                matrices.pushMatrix();
                try {
                    matrices.translate(cx, cy);
                    matrices.rotate((float) Math.atan2(dy, dx));
                    // Center the one-pixel stroke/caps on the same origin and endpoint as the label.
                    matrices.translate(-0.5f, -0.5f);
                    UiChrome.fillRounded(graphics, 0, 0, (int) Math.round(length - 7) + 1, 1, color);
                } finally { matrices.popMatrix(); }
            }
            for (int index : compassOrder) {
                int x = cx + (int) Math.round(compassX[index]), y = cy + (int) Math.round(compassY[index]);
                int color = tint(OverlayRenderStyle.color(SelectionEditStyle.axisSlot(index)), alpha);
                Text label = UiChrome.label((index == wheelAxis.axis ? ACTIVE_AXIS_LABELS : AXIS_LABELS)[index]);
                var matrices = graphics.getMatrices();
                matrices.pushMatrix();
                try {
                    // Center uppercase ink, excluding two pixels of line leading; retain half-pixel precision.
                    matrices.translate(x - client.textRenderer.getWidth(label) / 2.0f, y - (client.textRenderer.fontHeight - 2) / 2.0f);
                    UiChrome.text(graphics, client.textRenderer, label, 0, 0, color, true);
                } finally { matrices.popMatrix(); }
            }
        } finally { graphics.disableScissor(); }
    }
    void menu(DrawContext graphics, double dx, double dy, int radius, SelectionEditMode current, long appeared) {
        MinecraftClient client = MinecraftClient.getInstance();
        int cx = client.getWindow().getScaledWidth() / 2, cy = client.getWindow().getScaledHeight() / 2;
        SelectionEditMode hovered = SelectionEditMode.pick(dx, dy, radius);
        UiStyle style = UiStyle.current();
        float alpha = fade(appeared);
        long now = System.nanoTime();
        if (menuStart != appeared) { menuStart = appeared; menuFrame = now; java.util.Arrays.fill(menuHover, 0); }
        float transition = UiStyle.reducedMotion() ? 1 : (float) (1 - Math.exp(-(now - menuFrame) / 50_000_000.0));
        menuFrame = now;
        for (SelectionEditMode entry : SelectionEditMode.values()) {
            boolean selected = entry == hovered;
            int index = entry.ordinal();
            menuHover[index] += ((selected ? 1 : 0) - menuHover[index]) * transition;
            UiChrome.icon(graphics, entry == SelectionEditMode.RESIZE ? "radial-left" : "radial-right", cx - radius, cy - radius,
                    radius * 2, tint(UiChrome.blend(style.surface, style.accent, 0.38f * menuHover[index]) | 0xFF000000, alpha));
            int x = cx + (entry == SelectionEditMode.RESIZE ? -1 : 1) * Math.round(radius * 0.63f);
            int iconSize = Math.min(22, Math.max(12, radius / 4));
            int color = UiChrome.blend(entry == current ? style.text : style.secondaryText, style.accent, menuHover[index]);
            UiChrome.icon(graphics, entry.icon, x - iconSize / 2, cy - iconSize / 2, iconSize, tint(color, alpha));
        }
        Text hint = UiChrome.label(Text.translatable(hovered == null ? "gui.cancel"
                : "selection_edit.worldedit_selection_overlay." + hovered.key));
        int hintWidth = client.textRenderer.getWidth(hint);
        float hintScale = Math.min(1.0f, Math.max(1, radius * 0.72f - 8) / Math.max(1, hintWidth));
        var matrices = graphics.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(cx, cy);
            matrices.scale(hintScale, hintScale);
            UiChrome.text(graphics, client.textRenderer, hint, -hintWidth / 2, -client.textRenderer.fontHeight / 2,
                    tint(hovered == null ? style.secondaryText : style.text, alpha), true);
        } finally { matrices.popMatrix(); }
        double length = Math.hypot(dx, dy), scale = length > radius - 5 ? (radius - 5) / length : 1;
        // The cancel hint replaces the pointer inside the center so neither obscures the other.
        if (length >= radius * 0.36) {
            UiChrome.icon(graphics, "radial-dot", cx + (int) Math.round(dx * scale) - 3, cy + (int) Math.round(dy * scale) - 3,
                    6, tint(hovered == null ? style.text : style.accent, alpha));
        }
    }
    private static float fade(long start) { return UiStyle.reducedMotion() ? 1 : Math.min(1, (System.nanoTime() - start) / 120_000_000f); }
    private static String signed(long value) { return (value >= 0 ? "+" : "") + value; }
    private static int tint(int color, float alpha) { return color & 0xFFFFFF | Math.round((color >>> 24) * alpha) << 24; }
    private static Point point(Vec3d v) { return new Point(v.x, v.y, v.z); }
    private static Point point(Vector3f v) { return new Point(v.x(), v.y(), v.z()); }
    private static Vec3d vec(Point p) { return new Vec3d(p.x(), p.y(), p.z()); }
    private static Box box(CuboidDraft b) { return new Box(b.minX(), b.minY(), b.minZ(), b.maxX() + 1.0, b.maxY() + 1.0, b.maxZ() + 1.0); }
}

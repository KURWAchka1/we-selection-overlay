package org.marshal.weaddon_lightposition.client.selection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.marshal.weaddon_lightposition.client.screen.UiChrome;
import org.marshal.weaddon_lightposition.client.screen.UiStyle;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Corner;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;
import org.marshal.weaddon_lightposition.selection.SelectionEditSnapshot;

final class SelectionEditRenderer {
    private static final Face[] FACES = Face.values();
    private static final Corner[] CORNERS = Corner.values();
    record View(Point eye, Point forward, Point right, Point up, double tangent, double aspect, int pixelsHigh) {
        Point ray(double x, double y) { return forward.add(right.scale((x * 2 - 1) * tangent * aspect)).add(up.scale((1 - y * 2) * tangent)).normalized(); }
        double radius(Point p, double pixels) { return Math.max(0.01, p.subtract(eye).dot(forward) * 2 * tangent * pixels / pixelsHigh); }
    }
    View view(Minecraft client) {
        var camera = client.gameRenderer.getMainCamera();
        var rotation = camera.rotation();
        return new View(point(camera.position()), point(new Vector3f(0, 0, -1).rotate(rotation)),
                point(new Vector3f(1, 0, 0).rotate(rotation)), point(new Vector3f(0, 1, 0).rotate(rotation)),
                Math.tan(Math.toRadians(((org.marshal.weaddon_lightposition.mixin.SelectionEditFovAccessor) client.gameRenderer).weso$editFov(camera, 1.0f, true)) / 2),
                client.getWindow().getScreenWidth() / (double) client.getWindow().getScreenHeight(), client.getWindow().getScreenHeight());
    }
    Face handleHit(CuboidDraft bounds, View view) {
        Face hit = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Face face : FACES) {
            Point center = bounds.center(face), offset = center.subtract(view.eye);
            double depth = offset.dot(view.forward);
            if (depth <= 0.05) continue;
            double distance = offset.subtract(view.forward.scale(depth)).length();
            if (distance <= view.radius(center, 12) && depth < nearest) { hit = face; nearest = depth; }
        }
        return hit;
    }
    Corner cornerHit(CuboidDraft bounds, View view) {
        Corner hit = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Corner corner : CORNERS) {
            Point center = bounds.corner(corner), offset = center.subtract(view.eye);
            double depth = offset.dot(view.forward);
            if (depth <= 0.05) continue;
            double distance = offset.subtract(view.forward.scale(depth)).length();
            if (distance <= view.radius(center, 10) && depth < nearest) { hit = corner; nearest = depth; }
        }
        return hit;
    }
    void corners(CuboidDraft bounds, Corner target, View view, long appeared, boolean pending) {
        UiStyle style = UiStyle.current();
        float alpha = fade(appeared) * (pending ? 0.45f : 1);
        for (Corner corner : CORNERS) {
            Point center = bounds.corner(corner);
            if (center.subtract(view.eye).dot(view.forward) <= 0.05) continue;
            double radius = view.radius(center, corner == target ? 8 : 6);
            Point r = view.right.scale(radius), u = view.up.scale(radius);
            Gizmos.rect(vec(center.subtract(u)), vec(center.add(r)), vec(center.add(u)), vec(center.subtract(r)),
                    GizmoStyle.strokeAndFill(tint(corner == target ? style.accent : style.text, alpha), 1.5f,
                            tint(style.surface, alpha * 0.95f))).setAlwaysOnTop();
        }
    }
    void world(CuboidDraft original, CuboidDraft draft, Face target, Corner corner, View view, long appeared, boolean pending) {
        float alpha = fade(appeared);
        UiStyle style = UiStyle.current();
        if (!original.equals(draft)) Gizmos.cuboid(box(original), GizmoStyle.stroke(tint(style.secondaryText, alpha * 0.28f), 1)).setAlwaysOnTop();
        Gizmos.cuboid(box(draft), GizmoStyle.stroke(tint(style.secondaryText, alpha * 0.65f), 1)).setAlwaysOnTop();
        for (Face f : FACES) {
            Point center = draft.center(f);
            if (center.subtract(view.eye).dot(view.forward) <= 0.05) continue;
            double radius = view.radius(center, f == target ? 4.5 : 3.5);
            Point r = view.right.scale(radius), u = view.up.scale(radius);
            int color = tint(f == target ? style.accent : style.text, alpha * (pending ? 0.45f : f == target ? 1 : 0.65f));
            Gizmos.rect(vec(center.subtract(r).subtract(u)), vec(center.add(r).subtract(u)), vec(center.add(r).add(u)),
                    vec(center.subtract(r).add(u)), GizmoStyle.strokeAndFill(color, 1, color)).setAlwaysOnTop();
        }
        if (target != null) face(draft, target, tint(style.accent, alpha));
        corners(draft, corner != null ? corner : cornerHit(draft, view), view, appeared, pending);
    }
    private void face(CuboidDraft b, Face f, int color) {
        int a = (f.axis + 1) % 3, c = (f.axis + 2) % 3;
        Point[] corners = new Point[4];
        for (int i = 0; i < 4; i++) {
            double[] p = {0, 0, 0}; p[f.axis] = b.center(f).at(f.axis);
            p[a] = (i == 1 || i == 2) ? b.max(a) + 1.0 : b.min(a);
            p[c] = i >= 2 ? b.max(c) + 1.0 : b.min(c);
            corners[i] = new Point(p[0], p[1], p[2]);
        }
        for (int i = 0; i < 4; i++) Gizmos.line(vec(corners[i]), vec(corners[(i + 1) % 4]), color, 1.75f).setAlwaysOnTop();
    }

    void hud(GuiGraphics graphics, CuboidDraft draft, Face face, Corner corner, SelectionEditSnapshot origin, String reason, boolean pending, long appeared) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth(), screenHeight = client.getWindow().getGuiScaledHeight();
        if (screenWidth < 100 || screenHeight < 100) return;
        UiStyle style = UiStyle.current();
        Component title = draft == null ? Component.translatable("selection_edit.worldedit_selection_overlay." + (reason.isEmpty() ? "reading" : reason))
                : Component.literal(draft.size(0) + " x " + draft.size(1) + " x " + draft.size(2));
        long offset = draft != null && face != null ? ((long) draft.boundary(face) - origin.bounds().boundary(face)) * face.sign : 0;
        Component detail = pending ? Component.translatable("selection_edit.worldedit_selection_overlay.applying")
                : draft == null ? Component.empty() : Component.translatable("selection_edit.worldedit_selection_overlay.volume", draft.volume().toString())
                .append(corner == null ? face == null ? "" : "   " + face.label() + ": " + signed(offset)
                        : "   X: " + signed((long) draft.boundary(corner.face(0)) - origin.bounds().boundary(corner.face(0)))
                        + " Y: " + signed((long) draft.boundary(corner.face(1)) - origin.bounds().boundary(corner.face(1)))
                        + " Z: " + signed((long) draft.boundary(corner.face(2)) - origin.bounds().boundary(corner.face(2))));
        int width = Math.min(screenWidth - 16, Math.max(120, Math.max(client.font.width(UiChrome.label(title)), client.font.width(UiChrome.label(detail))) + 20));
        var titleLines = client.font.split(UiChrome.label(title), width - 20);
        var detailLines = client.font.split(UiChrome.label(detail), width - 20);
        int height = 14 + 11 * (titleLines.size() + detailLines.size()), x = (screenWidth - width) / 2, y = Math.max(8, screenHeight - 62 - height);
        float alpha = fade(appeared);
        UiChrome.fillRounded(graphics, x, y, width, height, tint(style.surface, alpha));
        graphics.enableScissor(x, y, x + width, y + height);
        int lineY = y + 7;
        for (var line : titleLines) { UiChrome.text(graphics, client.font, line, x + 10, lineY, tint(style.text, alpha), false); lineY += 11; }
        for (var line : detailLines) { UiChrome.text(graphics, client.font, line, x + 10, lineY, tint(style.secondaryText, alpha), false); lineY += 11; }
        graphics.disableScissor();
    }
    private static float fade(long start) { return UiStyle.reducedMotion() ? 1 : Math.min(1, (System.nanoTime() - start) / 120_000_000f); }
    private static String signed(long value) { return (value >= 0 ? "+" : "") + value; }
    private static int tint(int color, float alpha) { return color & 0xFFFFFF | Math.round((color >>> 24) * alpha) << 24; }
    private static Point point(Vec3 v) { return new Point(v.x(), v.y(), v.z()); }
    private static Point point(Vector3f v) { return new Point(v.x(), v.y(), v.z()); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static AABB box(CuboidDraft b) { return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1.0, b.maxY() + 1.0, b.maxZ() + 1.0); }
}

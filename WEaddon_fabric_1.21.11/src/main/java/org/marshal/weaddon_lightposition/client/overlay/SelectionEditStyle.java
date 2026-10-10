package org.marshal.weaddon_lightposition.client.overlay;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.Vec3d;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Corner;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;

/** Shared stroke roles for the world editor and its palette preview. */
public final class SelectionEditStyle {
    public static final float BOUNDARY_WIDTH = 1.0f;
    public static final float HANDLE_WIDTH = 1.5f;
    public static final float ACTIVE_WIDTH = 1.75f;
    private static final CuboidDraft SAMPLE = new CuboidDraft(0, 0, 0, 6, 4, 5);
    private static long previewVersion = -1;
    private static List<LineSegment> preview = List.of();

    private SelectionEditStyle() { }

    public static OverlayColorSlot axisSlot(int axis) {
        return switch (axis) {
            case 0 -> OverlayColorSlot.EDIT_AXIS_X;
            case 1 -> OverlayColorSlot.EDIT_AXIS_Y;
            case 2 -> OverlayColorSlot.EDIT_AXIS_Z;
            default -> throw new IllegalArgumentException("Invalid editing axis");
        };
    }

    public static List<LineSegment> previewSegments() {
        long version = OverlayRenderStyle.colorVersion();
        if (previewVersion == version) return preview;
        ArrayList<LineSegment> lines = new ArrayList<>(15);
        Corner selected = Corner.values()[0];
        Point anchor = SAMPLE.corner(selected);
        for (Corner corner : Corner.values()) {
            Point from = SAMPLE.corner(corner);
            for (int axis = 0; axis < 3; axis++) {
                if (corner.face(axis).sign > 0) continue;
                Point to = axisEnd(SAMPLE, corner, axis);
                add(lines, from, to, OverlayColorSlot.EDIT_BOUNDARY, BOUNDARY_WIDTH, 0.65f);
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            Point end = axisEnd(SAMPLE, selected, axis);
            add(lines, anchor, end, axisSlot(axis), ACTIVE_WIDTH, 1);
        }
        preview = List.copyOf(lines);
        previewVersion = version;
        return preview;
    }

    public static Point axisEnd(CuboidDraft bounds, Corner corner, int axis) {
        Point start = bounds.corner(corner);
        double end = corner.face(axis).sign > 0 ? bounds.min(axis) : bounds.max(axis) + 1.0;
        return new Point(axis == 0 ? end : start.x(), axis == 1 ? end : start.y(), axis == 2 ? end : start.z());
    }

    private static void add(List<LineSegment> lines, Point from, Point to, OverlayColorSlot slot, float width, float alpha) {
        int color = OverlayRenderStyle.color(slot);
        color = color & 0xFFFFFF | Math.round((color >>> 24) * alpha) << 24;
        lines.add(new LineSegment(new Vec3d(from.x(), from.y(), from.z()), new Vec3d(to.x(), to.y(), to.z()), color, width, slot));
    }
}

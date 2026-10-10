package org.marshal.weaddon_lightposition.client.overlay;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Corner;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;
import org.marshal.weaddon_lightposition.selection.SelectionEditOutline;

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
        ArrayList<LineSegment> lines = new ArrayList<>(12);
        Corner selected = Corner.values()[0];
        var active = SelectionEditOutline.active(SAMPLE, selected, null);
        for (var edge : SelectionEditOutline.boundary(SAMPLE, active))
            add(lines, edge.from(), edge.to(), OverlayColorSlot.EDIT_BOUNDARY, BOUNDARY_WIDTH, 0.65f);
        for (var edge : active)
            add(lines, edge.from(), edge.to(), axisSlot(edge.axis()), ACTIVE_WIDTH, 1);
        preview = List.copyOf(lines);
        previewVersion = version;
        return preview;
    }

    public static Point axisEnd(CuboidDraft bounds, Corner corner, int axis) {
        return SelectionEditOutline.axisEnd(bounds, corner, axis);
    }

    private static void add(List<LineSegment> lines, Point from, Point to, OverlayColorSlot slot, float width, float alpha) {
        int color = OverlayRenderStyle.color(slot);
        color = color & 0xFFFFFF | Math.round((color >>> 24) * alpha) << 24;
        lines.add(new LineSegment(new Vec3(from.x(), from.y(), from.z()), new Vec3(to.x(), to.y(), to.z()), color, width, slot));
    }
}

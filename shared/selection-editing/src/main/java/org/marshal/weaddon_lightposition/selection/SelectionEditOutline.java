package org.marshal.weaddon_lightposition.selection;

import java.util.ArrayList;
import java.util.List;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Corner;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;

/** Partition cuboid strokes so active axes never have a boundary-colored underlay. */
public final class SelectionEditOutline {
    private static final Corner[] CORNERS = Corner.values();
    public record Edge(Point from, Point to, int axis) { }

    private SelectionEditOutline() { }

    public static Point axisEnd(CuboidDraft bounds, Corner corner, int axis) {
        return bounds.corner(CORNERS[corner.ordinal() ^ (1 << axis)]);
    }

    public static List<Edge> active(CuboidDraft bounds, Corner corner, Face face) {
        if (corner != null) {
            Point start = bounds.corner(corner);
            ArrayList<Edge> result = new ArrayList<>(3);
            for (int axis = 0; axis < 3; axis++) result.add(new Edge(start, axisEnd(bounds, corner, axis), axis));
            return List.copyOf(result);
        }
        if (face == null) return List.of();
        double coordinate = bounds.center(face).at(face.axis);
        return edges(bounds).stream().filter(edge -> edge.axis != face.axis && edge.from.at(face.axis) == coordinate).toList();
    }

    public static List<Edge> boundary(CuboidDraft bounds, List<Edge> active) {
        ArrayList<Edge> result = new ArrayList<>(12);
        for (Edge edge : edges(bounds)) {
            Edge remainder = edge;
            for (Edge cut : active) {
                if (remainder == null) break;
                if (!collinear(remainder, cut)) continue;
                int axis = edge.axis;
                double low = remainder.from.at(axis), high = remainder.to.at(axis);
                double cutLow = Math.max(low, Math.min(cut.from.at(axis), cut.to.at(axis)));
                double cutHigh = Math.min(high, Math.max(cut.from.at(axis), cut.to.at(axis)));
                if (cutLow >= cutHigh) continue;
                if (low < cutLow) result.add(new Edge(remainder.from, at(remainder.from, axis, cutLow), axis));
                remainder = cutHigh < high ? new Edge(at(remainder.from, axis, cutHigh), remainder.to, axis) : null;
            }
            if (remainder != null) result.add(remainder);
        }
        return List.copyOf(result);
    }

    private static List<Edge> edges(CuboidDraft bounds) {
        ArrayList<Edge> result = new ArrayList<>(12);
        for (Corner corner : CORNERS) for (int axis = 0; axis < 3; axis++) {
            if (corner.face(axis).sign < 0) result.add(new Edge(bounds.corner(corner), axisEnd(bounds, corner, axis), axis));
        }
        return result;
    }

    private static boolean collinear(Edge edge, Edge cut) {
        if (edge.axis != cut.axis) return false;
        for (int axis = 0; axis < 3; axis++) if (axis != edge.axis && edge.from.at(axis) != cut.from.at(axis)) return false;
        return true;
    }

    private static Point at(Point point, int axis, double value) {
        return new Point(axis == 0 ? value : point.x(), axis == 1 ? value : point.y(), axis == 2 ? value : point.z());
    }
}

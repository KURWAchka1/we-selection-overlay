package org.marshal.weaddon_lightposition.selection;

import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;

/** Independent wheel displacement. Player motion and camera turns are not transform inputs. */
public final class SelectionCarry {
    private final CuboidDraft origin;
    private final int minY, maxY;
    private Face wheelAxis;
    private Point displacement = new Point(0, 0, 0);
    private CuboidDraft draft;

    public SelectionCarry(CuboidDraft origin, Point forward, int minY, int maxY) {
        if (origin == null || !finite(forward) || forward.length() < 1e-12
                || origin.minX() < -30_000_000 || origin.maxX() > 29_999_999
                || origin.minZ() < -30_000_000 || origin.maxZ() > 29_999_999
                || origin.minY() < minY || origin.maxY() > maxY) throw new IllegalArgumentException("Invalid carry origin");
        this.origin = this.draft = origin;
        this.minY = minY; this.maxY = maxY;
        int axis = Math.abs(forward.x()) >= Math.abs(forward.y()) && Math.abs(forward.x()) >= Math.abs(forward.z()) ? 0
                : Math.abs(forward.y()) >= Math.abs(forward.z()) ? 1 : 2;
        wheelAxis = axis == 0 ? forward.x() > 0 ? Face.EAST : Face.WEST
                : axis == 1 ? forward.y() > 0 ? Face.UP : Face.DOWN : forward.z() > 0 ? Face.SOUTH : Face.NORTH;
    }

    public Face wheelAxis() { return wheelAxis; }

    public void selectAxis(int axis) {
        wheelAxis = switch (axis) {
            case 0 -> Face.EAST;
            case 1 -> Face.UP;
            case 2 -> Face.SOUTH;
            default -> throw new IllegalArgumentException("Invalid move axis");
        };
    }

    public CuboidDraft scroll(double amount, int step) {
        if (step <= 0) throw new IllegalArgumentException("Invalid carry step");
        if (!Double.isFinite(amount) || amount == 0) return draft;
        double distance = amount * step * wheelAxis.sign;
        return advance(new Point(wheelAxis.axis == 0 ? distance : 0, wheelAxis.axis == 1 ? distance : 0,
                wheelAxis.axis == 2 ? distance : 0));
    }

    private CuboidDraft advance(Point change) {
        // Reject only overshoot, per axis; keep sub-block travel without rounding each update.
        displacement = new Point(constrain(0, displacement.x() + change.x()),
                constrain(1, displacement.y() + change.y()), constrain(2, displacement.z() + change.z()));
        CuboidDraft next = origin.translate(displacement, 1, minY, maxY);
        if (!next.equals(draft)) draft = next;
        return draft;
    }

    private double constrain(int axis, double wanted) {
        long low = axis == 1 ? minY : -30_000_000, high = axis == 1 ? maxY : 29_999_999;
        return Math.max(low - origin.min(axis), Math.min(high - origin.max(axis), wanted));
    }

    private static boolean finite(Point p) {
        return p != null && Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z());
    }
}

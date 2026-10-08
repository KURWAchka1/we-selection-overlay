package org.marshal.weaddon_lightposition.selection;

import java.math.BigInteger;

/** Inclusive block bounds. All drafting and hit tests are independent of world volume. */
public record CuboidDraft(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public enum Face {
        WEST(0, -1), EAST(0, 1), DOWN(1, -1), UP(1, 1), NORTH(2, -1), SOUTH(2, 1);
        public final int axis, sign;
        Face(int axis, int sign) { this.axis = axis; this.sign = sign; }
        public String label() { return (sign > 0 ? "+" : "-") + "XYZ".charAt(axis); }
    }

    public enum Corner {
        WEST_DOWN_NORTH, EAST_DOWN_NORTH, WEST_UP_NORTH, EAST_UP_NORTH,
        WEST_DOWN_SOUTH, EAST_DOWN_SOUTH, WEST_UP_SOUTH, EAST_UP_SOUTH;
        public Face face(int axis) {
            boolean positive = (ordinal() & (1 << axis)) != 0;
            return axis == 0 ? positive ? Face.EAST : Face.WEST : axis == 1 ? positive ? Face.UP : Face.DOWN : positive ? Face.SOUTH : Face.NORTH;
        }
        public Corner opposite() { return values()[ordinal() ^ 7]; }
    }

    public record Point(double x, double y, double z) {
        public double at(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
        public Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        public Point scale(double k) { return new Point(x * k, y * k, z * k); }
        public double dot(Point p) { return x * p.x + y * p.y + z * p.z; }
        public Point subtract(Point p) { return add(p.scale(-1)); }
        public double length() { return Math.sqrt(dot(this)); }
        public Point normalized() { double n = length(); return n < 1e-12 ? this : scale(1 / n); }
    }

    public CuboidDraft {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Inverted cuboid");
    }
    public int min(int axis) { return axis == 0 ? minX : axis == 1 ? minY : minZ; }
    public int max(int axis) { return axis == 0 ? maxX : axis == 1 ? maxY : maxZ; }
    public long size(int axis) { return (long) max(axis) - min(axis) + 1; }
    public BigInteger volume() { return BigInteger.valueOf(size(0)).multiply(BigInteger.valueOf(size(1))).multiply(BigInteger.valueOf(size(2))); }
    public int boundary(Face f) { return f.sign > 0 ? max(f.axis) : min(f.axis); }
    public Point center(Face f) {
        double[] c = {(minX + (double) maxX + 1) / 2, (minY + (double) maxY + 1) / 2, (minZ + (double) maxZ + 1) / 2};
        c[f.axis] = boundary(f) + (f.sign > 0 ? 1.0 : 0.0);
        return new Point(c[0], c[1], c[2]);
    }
    public Point corner(Corner corner) {
        return new Point(boundary(corner.face(0)) + (corner.face(0).sign > 0 ? 1.0 : 0.0),
                boundary(corner.face(1)) + (corner.face(1).sign > 0 ? 1.0 : 0.0),
                boundary(corner.face(2)) + (corner.face(2).sign > 0 ? 1.0 : 0.0));
    }
    public CuboidDraft moveCorner(Corner corner, Point movement, int step, int worldMinY, int worldMaxY) {
        CuboidDraft result = this;
        for (int axis = 0; axis < 3; axis++) {
            Face face = corner.face(axis);
            long delta = snappedOffset(movement.at(axis), step, face.sign);
            result = result.resize(face, delta, worldMinY, worldMaxY);
        }
        return result;
    }
    public static long snappedOffset(double distance, int step, int sign) {
        if (step <= 0 || !Double.isFinite(distance)) throw new IllegalArgumentException("Invalid drag offset");
        double snapped = Math.rint(distance / step) * step * sign;
        return (long) Math.max(Long.MIN_VALUE, Math.min(Long.MAX_VALUE, snapped));
    }

    /** A grabbed marker follows the live view ray at its original depth, without a view-plane singularity. */
    public static Point rayPoint(Point eye, Point direction, double distance) {
        double length = direction.length();
        if (!Double.isFinite(distance) || distance <= 0 || !Double.isFinite(length) || length < 1e-12) return null;
        Point result = eye.add(direction.scale(distance / length));
        return Double.isFinite(result.x) && Double.isFinite(result.y) && Double.isFinite(result.z) ? result : null;
    }

    /** Captured view-plane dragging keeps depth stable and never guesses from camera yaw. */
    public static Point planePoint(Point eye, Point direction, Point planeOrigin, Point normal) {
        double denominator = direction.dot(normal);
        if (denominator < 0.05) return null;
        double distance = planeOrigin.subtract(eye).dot(normal) / denominator;
        if (!Double.isFinite(distance) || distance <= 0) return null;
        return eye.add(direction.scale(distance));
    }

    public CuboidDraft resize(Face face, long outward, int worldMinY, int worldMaxY) {
        int a = face.axis;
        long low = a == 1 ? worldMinY : -30_000_000, high = a == 1 ? worldMaxY : 29_999_999;
        long wanted;
        try { wanted = Math.addExact(boundary(face), Math.multiplyExact(outward, face.sign)); }
        catch (ArithmeticException ex) { wanted = (outward > 0) == (face.sign > 0) ? high : low; }
        int value = (int) Math.max(face.sign > 0 ? min(a) : low, Math.min(face.sign > 0 ? high : max(a), wanted));
        return new CuboidDraft(a == 0 && face.sign < 0 ? value : minX, a == 1 && face.sign < 0 ? value : minY,
                a == 2 && face.sign < 0 ? value : minZ, a == 0 && face.sign > 0 ? value : maxX,
                a == 1 && face.sign > 0 ? value : maxY, a == 2 && face.sign > 0 ? value : maxZ);
    }

    public Face intersect(Point eye, Point direction) {
        double nearest = Double.POSITIVE_INFINITY;
        Face hit = null;
        for (Face f : Face.values()) {
            double d = direction.at(f.axis);
            if (Math.abs(d) < 1e-10) continue;
            double t = (center(f).at(f.axis) - eye.at(f.axis)) / d;
            if (t < 1e-6 || t >= nearest) continue;
            Point p = eye.add(direction.scale(t));
            boolean inside = true;
            for (int axis = 0; axis < 3; axis++) if (axis != f.axis && (p.at(axis) < min(axis) - 1e-7 || p.at(axis) > max(axis) + 1.0 + 1e-7)) inside = false;
            if (inside) { hit = f; nearest = t; }
        }
        return hit;
    }

    public static double axisParameter(Point eye, Point direction, Point origin, Face f) {
        double c = direction.at(f.axis), denominator = 1 - c * c;
        if (denominator < 0.02) return Double.NaN;
        Point offset = eye.subtract(origin);
        return (offset.at(f.axis) - c * direction.dot(offset)) / denominator;
    }
}

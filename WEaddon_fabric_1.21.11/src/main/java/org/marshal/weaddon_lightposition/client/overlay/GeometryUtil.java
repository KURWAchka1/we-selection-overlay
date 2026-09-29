package org.marshal.weaddon_lightposition.client.overlay;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;

public final class GeometryUtil {
    @FunctionalInterface
    public interface VoxelPredicate {
        boolean test(int x, int y, int z);
    }

    private record EdgeKey(int ax, int ay, int az, int bx, int by, int bz) {
        private static EdgeKey of(int x1, int y1, int z1, int x2, int y2, int z2) {
            if (x1 < x2) {
                return new EdgeKey(x1, y1, z1, x2, y2, z2);
            }
            if (x1 > x2) {
                return new EdgeKey(x2, y2, z2, x1, y1, z1);
            }
            if (y1 < y2) {
                return new EdgeKey(x1, y1, z1, x2, y2, z2);
            }
            if (y1 > y2) {
                return new EdgeKey(x2, y2, z2, x1, y1, z1);
            }
            if (z1 <= z2) {
                return new EdgeKey(x1, y1, z1, x2, y2, z2);
            }
            return new EdgeKey(x2, y2, z2, x1, y1, z1);
        }
    }

    private GeometryUtil() {
    }

    public static BlockPos toBlockPos(Vec3d point) {
        return new BlockPos((int) Math.floor(point.getX()), (int) Math.floor(point.getY()), (int) Math.floor(point.getZ()));
    }

    public static Vec3d toBlockCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public static Vec3d toBlockCenter(Vec3d point) {
        return new Vec3d(point.getX() + 0.5, point.getY() + 0.5, point.getZ() + 0.5);
    }

    public static int toGridSpacing(double spacing) {
        if (!Double.isFinite(spacing) || spacing <= 0.0) {
            return 1;
        }
        return Math.max(1, (int) Math.round(spacing));
    }

    public static int estimateGridLineSegments(int sizeX, int sizeY, int sizeZ, int spacing) {
        int xInterior = Math.max(0, (sizeX - 1) / spacing);
        int yInterior = Math.max(0, (sizeY - 1) / spacing);
        int zInterior = Math.max(0, (sizeZ - 1) / spacing);
        return 12 + 4 * (xInterior + yInterior + zInterior);
    }

    public static int ellipsoidAxisSamples(double radius) {
        if (radius < 1.5) {
            return 0;
        }
        if (radius < 3.5) {
            return 1;
        }
        if (radius < 6.5) {
            return 2;
        }
        return 3;
    }

    public static double ellipsoidFactor(double offset, double radius) {
        if (radius <= 0.0) {
            return 0.0;
        }
        double ratio = offset / radius;
        double value = 1.0 - ratio * ratio;
        if (value <= 0.0) {
            return 0.0;
        }
        return Math.sqrt(value);
    }

    public static int cylinderRadialSegments(double radius) {
        int computed = (int) Math.ceil(radius * 8.0);
        return Math.max(24, Math.min(OverlayRenderStyle.CYLINDER_MAX_SEGMENTS, computed));
    }

    public static void addLine(List<LineSegment> segments, Vec3d from, Vec3d to, int color, float width) {
        segments.add(new LineSegment(from, to, color, width, null));
    }

    public static void addLine(List<LineSegment> segments, Vec3d from, Vec3d to, OverlayColorSlot slot, float width) {
        segments.add(new LineSegment(from, to, OverlayRenderStyle.color(slot), width, slot));
    }

    public static void addVoxelShellSegments(
            List<LineSegment> segments,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            VoxelPredicate inside,
            OverlayColorSlot slot,
            float width
    ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return;
        }

        HashSet<EdgeKey> edges = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!inside.test(x, y, z)) {
                        continue;
                    }

                    if (!inside.test(x + 1, y, z)) {
                        addFaceXPlus(edges, x, y, z);
                    }
                    if (!inside.test(x - 1, y, z)) {
                        addFaceXMinus(edges, x, y, z);
                    }
                    if (!inside.test(x, y + 1, z)) {
                        addFaceYPlus(edges, x, y, z);
                    }
                    if (!inside.test(x, y - 1, z)) {
                        addFaceYMinus(edges, x, y, z);
                    }
                    if (!inside.test(x, y, z + 1)) {
                        addFaceZPlus(edges, x, y, z);
                    }
                    if (!inside.test(x, y, z - 1)) {
                        addFaceZMinus(edges, x, y, z);
                    }
                }
            }
        }

        for (EdgeKey edge : edges) {
            addLine(
                    segments,
                    new Vec3d(edge.ax(), edge.ay(), edge.az()),
                    new Vec3d(edge.bx(), edge.by(), edge.bz()),
                    slot,
                    width
            );
        }
    }

    private static void addFaceXPlus(HashSet<EdgeKey> edges, int x, int y, int z) {
        int fx = x + 1;
        addEdge(edges, fx, y, z, fx, y + 1, z);
        addEdge(edges, fx, y + 1, z, fx, y + 1, z + 1);
        addEdge(edges, fx, y + 1, z + 1, fx, y, z + 1);
        addEdge(edges, fx, y, z + 1, fx, y, z);
    }

    private static void addFaceXMinus(HashSet<EdgeKey> edges, int x, int y, int z) {
        int fx = x;
        addEdge(edges, fx, y, z, fx, y + 1, z);
        addEdge(edges, fx, y + 1, z, fx, y + 1, z + 1);
        addEdge(edges, fx, y + 1, z + 1, fx, y, z + 1);
        addEdge(edges, fx, y, z + 1, fx, y, z);
    }

    private static void addFaceYPlus(HashSet<EdgeKey> edges, int x, int y, int z) {
        int fy = y + 1;
        addEdge(edges, x, fy, z, x + 1, fy, z);
        addEdge(edges, x + 1, fy, z, x + 1, fy, z + 1);
        addEdge(edges, x + 1, fy, z + 1, x, fy, z + 1);
        addEdge(edges, x, fy, z + 1, x, fy, z);
    }

    private static void addFaceYMinus(HashSet<EdgeKey> edges, int x, int y, int z) {
        int fy = y;
        addEdge(edges, x, fy, z, x + 1, fy, z);
        addEdge(edges, x + 1, fy, z, x + 1, fy, z + 1);
        addEdge(edges, x + 1, fy, z + 1, x, fy, z + 1);
        addEdge(edges, x, fy, z + 1, x, fy, z);
    }

    private static void addFaceZPlus(HashSet<EdgeKey> edges, int x, int y, int z) {
        int fz = z + 1;
        addEdge(edges, x, y, fz, x + 1, y, fz);
        addEdge(edges, x + 1, y, fz, x + 1, y + 1, fz);
        addEdge(edges, x + 1, y + 1, fz, x, y + 1, fz);
        addEdge(edges, x, y + 1, fz, x, y, fz);
    }

    private static void addFaceZMinus(HashSet<EdgeKey> edges, int x, int y, int z) {
        int fz = z;
        addEdge(edges, x, y, fz, x + 1, y, fz);
        addEdge(edges, x + 1, y, fz, x + 1, y + 1, fz);
        addEdge(edges, x + 1, y + 1, fz, x, y + 1, fz);
        addEdge(edges, x, y + 1, fz, x, y, fz);
    }

    private static void addEdge(HashSet<EdgeKey> edges, int x1, int y1, int z1, int x2, int y2, int z2) {
        edges.add(EdgeKey.of(x1, y1, z1, x2, y2, z2));
    }

    public static void addBoxEdges(List<LineSegment> segments, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color, float width) {
        addBoxEdges(segments, minX, minY, minZ, maxX, maxY, maxZ, null, color, width);
    }

    public static void addBoxEdges(List<LineSegment> segments, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, OverlayColorSlot slot, float width) {
        addBoxEdges(segments, minX, minY, minZ, maxX, maxY, maxZ, slot, OverlayRenderStyle.color(slot), width);
    }

    private static void addBoxEdges(List<LineSegment> segments, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, OverlayColorSlot slot, int color, float width) {
        Vec3d p000 = new Vec3d(minX, minY, minZ);
        Vec3d p001 = new Vec3d(minX, minY, maxZ);
        Vec3d p010 = new Vec3d(minX, maxY, minZ);
        Vec3d p011 = new Vec3d(minX, maxY, maxZ);
        Vec3d p100 = new Vec3d(maxX, minY, minZ);
        Vec3d p101 = new Vec3d(maxX, minY, maxZ);
        Vec3d p110 = new Vec3d(maxX, maxY, minZ);
        Vec3d p111 = new Vec3d(maxX, maxY, maxZ);

        addLineWithOptionalSlot(segments, p000, p100, slot, color, width);
        addLineWithOptionalSlot(segments, p100, p101, slot, color, width);
        addLineWithOptionalSlot(segments, p101, p001, slot, color, width);
        addLineWithOptionalSlot(segments, p001, p000, slot, color, width);

        addLineWithOptionalSlot(segments, p010, p110, slot, color, width);
        addLineWithOptionalSlot(segments, p110, p111, slot, color, width);
        addLineWithOptionalSlot(segments, p111, p011, slot, color, width);
        addLineWithOptionalSlot(segments, p011, p010, slot, color, width);

        addLineWithOptionalSlot(segments, p000, p010, slot, color, width);
        addLineWithOptionalSlot(segments, p100, p110, slot, color, width);
        addLineWithOptionalSlot(segments, p101, p111, slot, color, width);
        addLineWithOptionalSlot(segments, p001, p011, slot, color, width);
    }

    public static void addSurfaceGrid(List<LineSegment> segments, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int spacing, int color, float width) {
        addSurfaceGrid(segments, minX, minY, minZ, maxX, maxY, maxZ, spacing, null, color, width);
    }

    public static void addSurfaceGrid(List<LineSegment> segments, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int spacing, OverlayColorSlot slot, float width) {
        addSurfaceGrid(segments, minX, minY, minZ, maxX, maxY, maxZ, spacing, slot, OverlayRenderStyle.color(slot), width);
    }

    private static void addSurfaceGrid(List<LineSegment> segments, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int spacing, OverlayColorSlot slot, int color, float width) {
        for (int y = minY + spacing; y < maxY; y += spacing) {
            addLineWithOptionalSlot(segments, new Vec3d(minX, y, minZ), new Vec3d(maxX, y, minZ), slot, color, width);
            addLineWithOptionalSlot(segments, new Vec3d(minX, y, maxZ), new Vec3d(maxX, y, maxZ), slot, color, width);
            addLineWithOptionalSlot(segments, new Vec3d(minX, y, minZ), new Vec3d(minX, y, maxZ), slot, color, width);
            addLineWithOptionalSlot(segments, new Vec3d(maxX, y, minZ), new Vec3d(maxX, y, maxZ), slot, color, width);
        }

        for (int x = minX + spacing; x < maxX; x += spacing) {
            addLineWithOptionalSlot(segments, new Vec3d(x, minY, minZ), new Vec3d(x, maxY, minZ), slot, color, width);
            addLineWithOptionalSlot(segments, new Vec3d(x, minY, maxZ), new Vec3d(x, maxY, maxZ), slot, color, width);
            addLineWithOptionalSlot(segments, new Vec3d(x, minY, minZ), new Vec3d(x, minY, maxZ), slot, color, width);
            addLineWithOptionalSlot(segments, new Vec3d(x, maxY, minZ), new Vec3d(x, maxY, maxZ), slot, color, width);
        }

        for (int z = minZ + spacing; z < maxZ; z += spacing) {
            addLineWithOptionalSlot(segments, new Vec3d(minX, minY, z), new Vec3d(maxX, minY, z), slot, color, width);
            addLineWithOptionalSlot(segments, new Vec3d(minX, maxY, z), new Vec3d(maxX, maxY, z), slot, color, width);
            addLineWithOptionalSlot(segments, new Vec3d(minX, minY, z), new Vec3d(minX, maxY, z), slot, color, width);
            addLineWithOptionalSlot(segments, new Vec3d(maxX, minY, z), new Vec3d(maxX, maxY, z), slot, color, width);
        }
    }

    public static void addPointCubeSegments(List<LineSegment> segments, Vec3d blockCorner, int color, float width) {
        addPointCubeSegments(segments, blockCorner, null, color, width);
    }

    public static void addPointCubeSegments(List<LineSegment> segments, Vec3d blockCorner, OverlayColorSlot slot, float width) {
        addPointCubeSegments(segments, blockCorner, slot, OverlayRenderStyle.color(slot), width);
    }

    private static void addPointCubeSegments(List<LineSegment> segments, Vec3d blockCorner, OverlayColorSlot slot, int color, float width) {
        double minX = blockCorner.getX() + OverlayRenderStyle.POINT_PADDING;
        double minY = blockCorner.getY() + OverlayRenderStyle.POINT_PADDING;
        double minZ = blockCorner.getZ() + OverlayRenderStyle.POINT_PADDING;
        double maxX = blockCorner.getX() + 1.0 - OverlayRenderStyle.POINT_PADDING;
        double maxY = blockCorner.getY() + 1.0 - OverlayRenderStyle.POINT_PADDING;
        double maxZ = blockCorner.getZ() + 1.0 - OverlayRenderStyle.POINT_PADDING;
        addBoxEdges(segments, minX, minY, minZ, maxX, maxY, maxZ, slot, color, width);
    }

    public static void addEllipseLoopXZ(List<LineSegment> segments, double centerX, double y, double centerZ, double radiusX, double radiusZ, int color, float width, int segmentCount) {
        addEllipseLoopXZ(segments, centerX, y, centerZ, radiusX, radiusZ, null, color, width, segmentCount);
    }

    public static void addEllipseLoopXZ(List<LineSegment> segments, double centerX, double y, double centerZ, double radiusX, double radiusZ, OverlayColorSlot slot, float width, int segmentCount) {
        addEllipseLoopXZ(segments, centerX, y, centerZ, radiusX, radiusZ, slot, OverlayRenderStyle.color(slot), width, segmentCount);
    }

    private static void addEllipseLoopXZ(List<LineSegment> segments, double centerX, double y, double centerZ, double radiusX, double radiusZ, OverlayColorSlot slot, int color, float width, int segmentCount) {
        if (radiusX <= 0.0 || radiusZ <= 0.0) {
            return;
        }

        for (int i = 0; i < segmentCount; i++) {
            double angleA = i * (Math.PI * 2.0) / segmentCount;
            double angleB = (i + 1) * (Math.PI * 2.0) / segmentCount;
            Vec3d from = new Vec3d(centerX + radiusX * Math.cos(angleA), y, centerZ + radiusZ * Math.sin(angleA));
            Vec3d to = new Vec3d(centerX + radiusX * Math.cos(angleB), y, centerZ + radiusZ * Math.sin(angleB));
            addLineWithOptionalSlot(segments, from, to, slot, color, width);
        }
    }

    public static void addEllipseLoopYZ(List<LineSegment> segments, double x, double centerY, double centerZ, double radiusY, double radiusZ, int color, float width, int segmentCount) {
        addEllipseLoopYZ(segments, x, centerY, centerZ, radiusY, radiusZ, null, color, width, segmentCount);
    }

    public static void addEllipseLoopYZ(List<LineSegment> segments, double x, double centerY, double centerZ, double radiusY, double radiusZ, OverlayColorSlot slot, float width, int segmentCount) {
        addEllipseLoopYZ(segments, x, centerY, centerZ, radiusY, radiusZ, slot, OverlayRenderStyle.color(slot), width, segmentCount);
    }

    private static void addEllipseLoopYZ(List<LineSegment> segments, double x, double centerY, double centerZ, double radiusY, double radiusZ, OverlayColorSlot slot, int color, float width, int segmentCount) {
        if (radiusY <= 0.0 || radiusZ <= 0.0) {
            return;
        }

        for (int i = 0; i < segmentCount; i++) {
            double angleA = i * (Math.PI * 2.0) / segmentCount;
            double angleB = (i + 1) * (Math.PI * 2.0) / segmentCount;
            Vec3d from = new Vec3d(x, centerY + radiusY * Math.cos(angleA), centerZ + radiusZ * Math.sin(angleA));
            Vec3d to = new Vec3d(x, centerY + radiusY * Math.cos(angleB), centerZ + radiusZ * Math.sin(angleB));
            addLineWithOptionalSlot(segments, from, to, slot, color, width);
        }
    }

    public static void addEllipseLoopXY(List<LineSegment> segments, double centerX, double centerY, double z, double radiusX, double radiusY, int color, float width, int segmentCount) {
        addEllipseLoopXY(segments, centerX, centerY, z, radiusX, radiusY, null, color, width, segmentCount);
    }

    public static void addEllipseLoopXY(List<LineSegment> segments, double centerX, double centerY, double z, double radiusX, double radiusY, OverlayColorSlot slot, float width, int segmentCount) {
        addEllipseLoopXY(segments, centerX, centerY, z, radiusX, radiusY, slot, OverlayRenderStyle.color(slot), width, segmentCount);
    }

    private static void addEllipseLoopXY(List<LineSegment> segments, double centerX, double centerY, double z, double radiusX, double radiusY, OverlayColorSlot slot, int color, float width, int segmentCount) {
        if (radiusX <= 0.0 || radiusY <= 0.0) {
            return;
        }

        for (int i = 0; i < segmentCount; i++) {
            double angleA = i * (Math.PI * 2.0) / segmentCount;
            double angleB = (i + 1) * (Math.PI * 2.0) / segmentCount;
            Vec3d from = new Vec3d(centerX + radiusX * Math.cos(angleA), centerY + radiusY * Math.sin(angleA), z);
            Vec3d to = new Vec3d(centerX + radiusX * Math.cos(angleB), centerY + radiusY * Math.sin(angleB), z);
            addLineWithOptionalSlot(segments, from, to, slot, color, width);
        }
    }

    public static void addPolygonLoop(List<LineSegment> segments, List<Point2i> points, double y, int color, float width) {
        addPolygonLoop(segments, points, y, null, color, width);
    }

    public static void addPolygonLoop(List<LineSegment> segments, List<Point2i> points, double y, OverlayColorSlot slot, float width) {
        addPolygonLoop(segments, points, y, slot, OverlayRenderStyle.color(slot), width);
    }

    private static void addPolygonLoop(List<LineSegment> segments, List<Point2i> points, double y, OverlayColorSlot slot, int color, float width) {
        if (points.size() < 2) {
            return;
        }

        for (int i = 0; i < points.size(); i++) {
            Point2i a = points.get(i);
            Point2i b = points.get((i + 1) % points.size());
            Vec3d from = new Vec3d(a.x() + 0.5, y, a.z() + 0.5);
            Vec3d to = new Vec3d(b.x() + 0.5, y, b.z() + 0.5);
            addLineWithOptionalSlot(segments, from, to, slot, color, width);
        }
    }

    private static void addLineWithOptionalSlot(List<LineSegment> segments, Vec3d from, Vec3d to, OverlayColorSlot slot, int color, float width) {
        segments.add(new LineSegment(from, to, color, width, slot));
    }

    public static long edgeKey(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return ((long) min << 32) ^ (max & 0xffffffffL);
    }
}

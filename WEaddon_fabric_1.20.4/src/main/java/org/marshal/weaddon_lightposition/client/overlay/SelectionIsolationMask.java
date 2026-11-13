package org.marshal.weaddon_lightposition.client.overlay;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SelectionIsolationMask {
    private final RegionShape shape;
    private final VoxelBounds bounds;
    private final BlockPos cuboidPos1;
    private final BlockPos cuboidPos2;
    private final List<Point2i> polygonPoints;
    private final int polygonMinY;
    private final int polygonMaxY;
    private final boolean polygonHasMinMax;
    private final Vec3d ellipsoidCenter;
    private final Vec3d ellipsoidRadii;
    private final Vec3d cylinderCenter;
    private final double cylinderRadiusX;
    private final double cylinderRadiusZ;
    private final Integer cylinderMinY;
    private final Integer cylinderMaxY;
    private final Map<Integer, Vec3d> polyhedronVertices;
    private final List<PolyPlane> polyhedronPlanes;

    private SelectionIsolationMask(
            RegionShape shape,
            VoxelBounds bounds,
            BlockPos cuboidPos1,
            BlockPos cuboidPos2,
            List<Point2i> polygonPoints,
            int polygonMinY,
            int polygonMaxY,
            boolean polygonHasMinMax,
            Vec3d ellipsoidCenter,
            Vec3d ellipsoidRadii,
            Vec3d cylinderCenter,
            double cylinderRadiusX,
            double cylinderRadiusZ,
            Integer cylinderMinY,
            Integer cylinderMaxY,
            Map<Integer, Vec3d> polyhedronVertices,
            List<PolyPlane> polyhedronPlanes
    ) {
        this.shape = shape;
        this.bounds = bounds;
        this.cuboidPos1 = cuboidPos1;
        this.cuboidPos2 = cuboidPos2;
        this.polygonPoints = polygonPoints;
        this.polygonMinY = polygonMinY;
        this.polygonMaxY = polygonMaxY;
        this.polygonHasMinMax = polygonHasMinMax;
        this.ellipsoidCenter = ellipsoidCenter;
        this.ellipsoidRadii = ellipsoidRadii;
        this.cylinderCenter = cylinderCenter;
        this.cylinderRadiusX = cylinderRadiusX;
        this.cylinderRadiusZ = cylinderRadiusZ;
        this.cylinderMinY = cylinderMinY;
        this.cylinderMaxY = cylinderMaxY;
        this.polyhedronVertices = polyhedronVertices;
        this.polyhedronPlanes = polyhedronPlanes;
    }

    static SelectionIsolationMask create(
            RegionShape shape,
            BlockPos cuboidPos1,
            BlockPos cuboidPos2,
            List<Point2i> polygonPoints,
            int polygonMinY,
            int polygonMaxY,
            boolean polygonHasMinMax,
            Vec3d ellipsoidCenter,
            Vec3d ellipsoidRadii,
            Vec3d cylinderCenter,
            double cylinderRadiusX,
            double cylinderRadiusZ,
            Integer cylinderMinY,
            Integer cylinderMaxY,
            Map<Integer, Vec3d> polyhedronVertices,
            List<int[]> polyhedronFaces
    ) {
        if (shape == null) {
            return null;
        }

        List<Point2i> polygonCopy = polygonPoints == null ? List.of() : List.copyOf(polygonPoints);
        Map<Integer, Vec3d> vertexCopy = polyhedronVertices == null ? Map.of() : Map.copyOf(polyhedronVertices);
        List<PolyPlane> planes = shape == RegionShape.POLYHEDRON
                ? buildPolyhedronPlanes(vertexCopy, polyhedronFaces)
                : List.of();
        VoxelBounds bounds = buildBounds(
                shape,
                cuboidPos1,
                cuboidPos2,
                polygonCopy,
                polygonMinY,
                polygonMaxY,
                polygonHasMinMax,
                ellipsoidCenter,
                ellipsoidRadii,
                cylinderCenter,
                cylinderRadiusX,
                cylinderRadiusZ,
                cylinderMinY,
                cylinderMaxY,
                vertexCopy
        );
        if (bounds == null || bounds.volume() <= 0L) {
            return null;
        }

        return new SelectionIsolationMask(
                shape,
                bounds,
                cuboidPos1 == null ? null : cuboidPos1.toImmutable(),
                cuboidPos2 == null ? null : cuboidPos2.toImmutable(),
                polygonCopy,
                polygonMinY,
                polygonMaxY,
                polygonHasMinMax,
                ellipsoidCenter,
                ellipsoidRadii,
                cylinderCenter,
                cylinderRadiusX,
                cylinderRadiusZ,
                cylinderMinY,
                cylinderMaxY,
                vertexCopy,
                planes
        );
    }

    public VoxelBounds bounds() {
        return bounds;
    }

    public boolean containsBlock(BlockPos pos) {
        return pos != null && containsBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean containsBlock(int x, int y, int z) {
        if (bounds == null || x < bounds.minX() || x > bounds.maxX() || y < bounds.minY() || y > bounds.maxY() || z < bounds.minZ() || z > bounds.maxZ()) {
            return false;
        }

        return switch (shape) {
            case CUBOID -> true;
            case POLYGON_2D -> containsPolygon(x, y, z);
            case ELLIPSOID -> containsEllipsoid(x, y, z);
            case CYLINDER -> containsCylinder(x, y, z);
            case POLYHEDRON -> containsPolyhedron(x, y, z);
        };
    }

    private boolean containsPolygon(int x, int y, int z) {
        if (polygonPoints.isEmpty()) {
            return false;
        }

        int minY = polygonHasMinMax ? polygonMinY : 0;
        int maxY = polygonHasMinMax ? polygonMaxY : 0;
        if (maxY < minY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }
        return y >= minY && y <= maxY && polygonContainsXZ(polygonPoints, x, z);
    }

    private boolean containsEllipsoid(int x, int y, int z) {
        if (ellipsoidCenter == null || ellipsoidRadii == null) {
            return false;
        }

        double radiusX = Math.abs(ellipsoidRadii.getX());
        double radiusY = Math.abs(ellipsoidRadii.getY());
        double radiusZ = Math.abs(ellipsoidRadii.getZ());
        if (radiusX < 0.01 || radiusY < 0.01 || radiusZ < 0.01) {
            return false;
        }

        double effectiveRadiusX = radiusX + 0.5;
        double effectiveRadiusY = radiusY + 0.5;
        double effectiveRadiusZ = radiusZ + 0.5;
        double dx = x - ellipsoidCenter.getX();
        double dy = y - ellipsoidCenter.getY();
        double dz = z - ellipsoidCenter.getZ();
        double distance = dx * dx / (effectiveRadiusX * effectiveRadiusX)
                + dy * dy / (effectiveRadiusY * effectiveRadiusY)
                + dz * dz / (effectiveRadiusZ * effectiveRadiusZ);
        return distance <= 1.0 + 1.0e-9;
    }

    private boolean containsCylinder(int x, int y, int z) {
        if (cylinderCenter == null || cylinderRadiusX <= 0.0 || cylinderRadiusZ <= 0.0) {
            return false;
        }

        int centerY = (int) Math.floor(cylinderCenter.getY());
        int minY = cylinderMinY != null ? cylinderMinY : centerY;
        int maxY = cylinderMaxY != null ? cylinderMaxY : centerY;
        if (maxY < minY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }
        if (y < minY || y > maxY) {
            return false;
        }

        double effectiveRadiusX = cylinderRadiusX + 0.5;
        double effectiveRadiusZ = cylinderRadiusZ + 0.5;
        double dx = x - cylinderCenter.getX();
        double dz = z - cylinderCenter.getZ();
        double distance = dx * dx / (effectiveRadiusX * effectiveRadiusX)
                + dz * dz / (effectiveRadiusZ * effectiveRadiusZ);
        return distance <= 1.0 + 1.0e-9;
    }

    private boolean containsPolyhedron(int x, int y, int z) {
        if (polyhedronPlanes.isEmpty()) {
            return false;
        }
        for (PolyPlane plane : polyhedronPlanes) {
            if (plane.above(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private static VoxelBounds buildBounds(
            RegionShape shape,
            BlockPos cuboidPos1,
            BlockPos cuboidPos2,
            List<Point2i> polygonPoints,
            int polygonMinY,
            int polygonMaxY,
            boolean polygonHasMinMax,
            Vec3d ellipsoidCenter,
            Vec3d ellipsoidRadii,
            Vec3d cylinderCenter,
            double cylinderRadiusX,
            double cylinderRadiusZ,
            Integer cylinderMinY,
            Integer cylinderMaxY,
            Map<Integer, Vec3d> polyhedronVertices
    ) {
        return switch (shape) {
            case CUBOID -> boundsForCuboid(cuboidPos1, cuboidPos2);
            case POLYGON_2D -> boundsForPolygon(polygonPoints, polygonMinY, polygonMaxY, polygonHasMinMax);
            case ELLIPSOID -> boundsForEllipsoid(ellipsoidCenter, ellipsoidRadii);
            case CYLINDER -> boundsForCylinder(cylinderCenter, cylinderRadiusX, cylinderRadiusZ, cylinderMinY, cylinderMaxY);
            case POLYHEDRON -> boundsForPolyhedron(polyhedronVertices);
        };
    }

    private static VoxelBounds boundsForCuboid(BlockPos pos1, BlockPos pos2) {
        if (pos1 == null || pos2 == null) {
            return null;
        }
        return new VoxelBounds(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()),
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );
    }

    private static VoxelBounds boundsForPolygon(List<Point2i> points, int polygonMinY, int polygonMaxY, boolean polygonHasMinMax) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Point2i point : points) {
            minX = Math.min(minX, point.x());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxZ = Math.max(maxZ, point.z());
        }

        int minY = polygonHasMinMax ? polygonMinY : 0;
        int maxY = polygonHasMinMax ? polygonMaxY : 0;
        if (maxY < minY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }
        return new VoxelBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static VoxelBounds boundsForEllipsoid(Vec3d center, Vec3d radii) {
        if (center == null || radii == null) {
            return null;
        }

        double radiusX = Math.abs(radii.getX());
        double radiusY = Math.abs(radii.getY());
        double radiusZ = Math.abs(radii.getZ());
        if (radiusX < 0.01 || radiusY < 0.01 || radiusZ < 0.01) {
            return null;
        }

        return new VoxelBounds(
                (int) Math.floor(center.getX() - radiusX),
                (int) Math.floor(center.getY() - radiusY),
                (int) Math.floor(center.getZ() - radiusZ),
                (int) Math.ceil(center.getX() + radiusX),
                (int) Math.ceil(center.getY() + radiusY),
                (int) Math.ceil(center.getZ() + radiusZ)
        );
    }

    private static VoxelBounds boundsForCylinder(Vec3d center, double radiusX, double radiusZ, Integer minYValue, Integer maxYValue) {
        if (center == null || radiusX <= 0.0 || radiusZ <= 0.0) {
            return null;
        }

        int centerY = (int) Math.floor(center.getY());
        int minY = minYValue != null ? minYValue : centerY;
        int maxY = maxYValue != null ? maxYValue : centerY;
        if (maxY < minY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }

        return new VoxelBounds(
                (int) Math.floor(center.getX() - radiusX),
                minY,
                (int) Math.floor(center.getZ() - radiusZ),
                (int) Math.ceil(center.getX() + radiusX),
                maxY,
                (int) Math.ceil(center.getZ() + radiusZ)
        );
    }

    private static VoxelBounds boundsForPolyhedron(Map<Integer, Vec3d> vertices) {
        if (vertices == null || vertices.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Vec3d vertex : vertices.values()) {
            minX = Math.min(minX, (int) Math.floor(vertex.getX()));
            minY = Math.min(minY, (int) Math.floor(vertex.getY()));
            minZ = Math.min(minZ, (int) Math.floor(vertex.getZ()));
            maxX = Math.max(maxX, (int) Math.ceil(vertex.getX()));
            maxY = Math.max(maxY, (int) Math.ceil(vertex.getY()));
            maxZ = Math.max(maxZ, (int) Math.ceil(vertex.getZ()));
        }
        return new VoxelBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<PolyPlane> buildPolyhedronPlanes(Map<Integer, Vec3d> vertices, List<int[]> faces) {
        if (vertices == null || vertices.size() < 4 || faces == null || faces.isEmpty()) {
            return List.of();
        }

        Vec3d centerAccum = Vec3d.ZERO;
        for (Vec3d vertex : vertices.values()) {
            centerAccum = centerAccum.add(vertex);
        }
        Vec3d centroid = centerAccum.multiply(1.0 / vertices.size());

        ArrayList<PolyPlane> planes = new ArrayList<>();
        for (int[] face : faces) {
            if (face == null || face.length < 3) {
                continue;
            }

            Vec3d v0 = vertices.get(face[0]);
            Vec3d v1 = vertices.get(face[1]);
            Vec3d v2 = vertices.get(face[2]);
            if (v0 == null || v1 == null || v2 == null) {
                continue;
            }

            Vec3d normal = v1.subtract(v0).crossProduct(v2.subtract(v0));
            if (normal.lengthSquared() < 1.0e-10) {
                continue;
            }
            normal = normal.normalize();

            double maxDot = Math.max(normal.dotProduct(v0), Math.max(normal.dotProduct(v1), normal.dotProduct(v2)));
            if (normal.dotProduct(centroid) > maxDot) {
                normal = normal.multiply(-1.0);
                maxDot = Math.max(normal.dotProduct(v0), Math.max(normal.dotProduct(v1), normal.dotProduct(v2)));
            }

            planes.add(new PolyPlane(normal, maxDot));
        }
        return planes.isEmpty() ? List.of() : List.copyOf(planes);
    }

    private static boolean polygonContainsXZ(List<Point2i> points, int targetX, int targetZ) {
        if (points.size() < 3) {
            return false;
        }

        double x = targetX + 0.5;
        double z = targetZ + 0.5;
        boolean inside = false;
        int previous = points.size() - 1;
        for (int current = 0; current < points.size(); current++) {
            Point2i currentPoint = points.get(current);
            Point2i previousPoint = points.get(previous);
            double currentZ = currentPoint.z() + 0.5;
            double previousZ = previousPoint.z() + 0.5;
            boolean crosses = (currentZ > z) != (previousZ > z);
            if (crosses) {
                double currentX = currentPoint.x() + 0.5;
                double previousX = previousPoint.x() + 0.5;
                double intersectionX = (previousX - currentX) * (z - currentZ) / (previousZ - currentZ) + currentX;
                if (x < intersectionX) {
                    inside = !inside;
                }
            }
            previous = current;
        }
        return inside;
    }

    private record PolyPlane(Vec3d normal, double maxDot) {
        private boolean above(int x, int y, int z) {
            double dot = normal.x * x + normal.y * y + normal.z * z;
            return dot > maxDot + 1.0e-9;
        }
    }
}

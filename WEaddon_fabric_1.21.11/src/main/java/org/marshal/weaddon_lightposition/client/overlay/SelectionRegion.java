package org.marshal.weaddon_lightposition.client.overlay;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.client.render.DrawStyle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;

public final class SelectionRegion {
    private final RegionShape shape;

    private BlockPos cuboidPos1;
    private BlockPos cuboidPos2;
    private double gridSpacing = 1.0;

    private final TreeMap<Integer, Point2i> polygonPoints = new TreeMap<>();
    private int polygonMinY;
    private int polygonMaxY;
    private boolean polygonHasMinMax;

    private Vec3d ellipsoidCenter;
    private Vec3d ellipsoidRadii;

    private Vec3d cylinderCenter;
    private double cylinderRadiusX;
    private double cylinderRadiusZ;
    private Integer cylinderMinY;
    private Integer cylinderMaxY;

    private final TreeMap<Integer, Vec3d> polyhedronVertices = new TreeMap<>();
    private final ArrayList<int[]> polyhedronFaces = new ArrayList<>();

    private long version;
    private long cachedVersion = Long.MIN_VALUE;
    private long cachedStyleVersion = Long.MIN_VALUE;
    private RenderMode cachedMode = null;
    private List<LineSegment> cachedSegments = List.of();
    private long cachedOutlineVersion = Long.MIN_VALUE;
    private long cachedOutlineStyleVersion = Long.MIN_VALUE;
    private List<LineSegment> cachedOutlineSegments = List.of();
    private long polygonOrderedCacheVersion = Long.MIN_VALUE;
    private List<Point2i> polygonOrderedCache = List.of();
    private long polyhedronPlanesCacheVersion = Long.MIN_VALUE;
    private List<PolyPlane> polyhedronPlanesCache = List.of();
    private RulerAnchor fixedRulerAnchor;
    private static final long MAX_BLOCKY_CELLS = 1_500_000L;
    private static final double SIDE_SWITCH_MARGIN = 0.55;
    private static final double SIDE_SIGN_SWITCH_MARGIN = 0.20;

    public SelectionRegion(RegionShape shape) {
        this.shape = shape;
    }

    public RegionShape shape() {
        return shape;
    }

    public long version() {
        return version;
    }

    public void applyPoint3D(int pointId, Vec3d point) {
        switch (shape) {
            case CUBOID -> {
                if (pointId == 0) {
                    BlockPos newPos = GeometryUtil.toBlockPos(point);
                    if (!newPos.equals(cuboidPos1)) {
                        cuboidPos1 = newPos;
                        markDirty();
                    }
                } else if (pointId == 1) {
                    BlockPos newPos = GeometryUtil.toBlockPos(point);
                    if (!newPos.equals(cuboidPos2)) {
                        cuboidPos2 = newPos;
                        markDirty();
                    }
                }
            }
            case POLYHEDRON -> {
                // Convex/polyhedron updates are sent by WorldEdit as a full snapshot.
                // Reaching point #0 means a new snapshot starts, so stale faces must be dropped first.
                if (pointId == 0 && (!polyhedronVertices.isEmpty() || !polyhedronFaces.isEmpty())) {
                    polyhedronVertices.clear();
                    polyhedronFaces.clear();
                    markDirty();
                }

                Vec3d existing = polyhedronVertices.put(pointId, point);
                if (!point.equals(existing)) {
                    markDirty();
                }
            }
            default -> {
                // Not applicable for this shape.
            }
        }
    }

    public void applyPoint2D(int pointId, int x, int z) {
        if (shape != RegionShape.POLYGON_2D) {
            return;
        }

        // Polygon snapshots may be resent from point #0; clear stale points before replaying.
        if (pointId == 0 && !polygonPoints.isEmpty()) {
            polygonPoints.clear();
            markDirty();
        }

        Point2i point = new Point2i(x, z);
        Point2i existing = polygonPoints.put(pointId, point);
        if (!point.equals(existing)) {
            markDirty();
        }
    }

    public void applyEllipsoidCenter(Vec3d center) {
        if (shape != RegionShape.ELLIPSOID) {
            return;
        }
        if (!center.equals(ellipsoidCenter)) {
            ellipsoidCenter = center;
            markDirty();
        }
    }

    public void applyEllipsoidRadii(Vec3d radii) {
        if (shape != RegionShape.ELLIPSOID) {
            return;
        }
        if (!radii.equals(ellipsoidRadii)) {
            ellipsoidRadii = radii;
            markDirty();
        }
    }

    public void applyCylinderCenter(Vec3d center) {
        if (shape != RegionShape.CYLINDER) {
            return;
        }
        if (!center.equals(cylinderCenter)) {
            cylinderCenter = center;
            markDirty();
        }
    }

    public void applyCylinderRadii(double radiusX, double radiusZ) {
        if (shape != RegionShape.CYLINDER) {
            return;
        }
        if (Double.compare(radiusX, cylinderRadiusX) != 0 || Double.compare(radiusZ, cylinderRadiusZ) != 0) {
            cylinderRadiusX = radiusX;
            cylinderRadiusZ = radiusZ;
            markDirty();
        }
    }

    public void applyMinMax(int min, int max) {
        if (shape == RegionShape.POLYGON_2D) {
            if (!polygonHasMinMax || polygonMinY != min || polygonMaxY != max) {
                polygonMinY = min;
                polygonMaxY = max;
                polygonHasMinMax = true;
                markDirty();
            }
        } else if (shape == RegionShape.CYLINDER) {
            if (cylinderMinY == null || cylinderMaxY == null || cylinderMinY != min || cylinderMaxY != max) {
                cylinderMinY = min;
                cylinderMaxY = max;
                markDirty();
            }
        }
    }

    public void applyPolygonFace(int[] vertexIds) {
        if (shape != RegionShape.POLYHEDRON || vertexIds.length < 3) {
            return;
        }
        polyhedronFaces.add(vertexIds);
        markDirty();
    }

    public void applyGrid(double spacing, boolean cull) {
        if (shape != RegionShape.CUBOID) {
            return;
        }
        if (Double.compare(spacing, gridSpacing) != 0) {
            gridSpacing = spacing;
            markDirty();
        }
    }

    public VoxelBounds boundsForAnalysis() {
        return switch (shape) {
            case CUBOID -> boundsForCuboid();
            case POLYGON_2D -> boundsForPolygon();
            case ELLIPSOID -> boundsForEllipsoid();
            case CYLINDER -> boundsForCylinder();
            case POLYHEDRON -> boundsForPolyhedron();
        };
    }

    public boolean containsBlock(int x, int y, int z) {
        return switch (shape) {
            case CUBOID -> containsCuboid(x, y, z);
            case POLYGON_2D -> containsPolygon(x, y, z);
            case ELLIPSOID -> containsEllipsoid(x, y, z);
            case CYLINDER -> containsCylinder(x, y, z);
            case POLYHEDRON -> containsPolyhedron(x, y, z);
        };
    }

    public SelectionIsolationMask toIsolationMask() {
        ArrayList<int[]> copiedFaces = new ArrayList<>(polyhedronFaces.size());
        for (int[] face : polyhedronFaces) {
            copiedFaces.add(face.clone());
        }
        return SelectionIsolationMask.create(
                shape,
                cuboidPos1,
                cuboidPos2,
                List.copyOf(polygonPoints.values()),
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
                new TreeMap<>(polyhedronVertices),
                copiedFaces
        );
    }

    public void render(RenderMode mode) {
        rebuildIfNeeded(mode);
        renderSegments(cachedSegments);
    }

    public void renderOutline() {
        rebuildOutlineIfNeeded();
        renderSegments(cachedOutlineSegments);
    }

    public List<LineSegment> previewSegments(RenderMode mode, boolean outlineOnly) {
        if (outlineOnly) {
            rebuildOutlineIfNeeded();
            return cachedOutlineSegments;
        }
        rebuildIfNeeded(mode);
        return cachedSegments;
    }

    private void renderSegments(List<LineSegment> segments) {
        if (segments.isEmpty()) {
            return;
        }
        for (LineSegment segment : segments) {
            GizmoDrawing.line(segment.from(), segment.to(), segment.color(), OverlayRenderStyle.lineWidth(segment.width(), segment.slot()))
                    .ignoreOcclusion()
                    .withLifespan(OverlayRenderStyle.GIZMO_LIFESPAN_MS);
        }
    }

    public void renderRuler(
            Vec3d cameraPos,
            Vec3d cameraRight,
            Vec3d cameraUp,
            RulerTrackingMode trackingMode,
            boolean showAxisX,
            boolean showAxisY,
            boolean showAxisZ
    ) {
        if (cameraPos == null || cameraRight == null || cameraUp == null) {
            return;
        }

        VoxelBounds bounds = boundsForAnalysis();
        if (bounds == null) {
            return;
        }

        List<RulerLayout> layouts = buildRulerLayouts(bounds, cameraPos, DiagonalMode.NONE, trackingMode);
        if (layouts.isEmpty()) {
            return;
        }

        for (RulerLayout layout : layouts) {
            if (!axisEnabled(layout.axis(), showAxisX, showAxisY, showAxisZ)) {
                continue;
            }
            renderRulerLayout(layout, cameraRight, cameraUp);
        }
    }

    public void renderDiagonals(Vec3d cameraPos, Vec3d cameraRight, Vec3d cameraUp, DiagonalMode diagonalMode, RulerTrackingMode trackingMode) {
        if (cameraPos == null || cameraRight == null || cameraUp == null || diagonalMode == null || diagonalMode == DiagonalMode.NONE || shape != RegionShape.CUBOID) {
            return;
        }

        VoxelBounds bounds = boundsForAnalysis();
        if (bounds == null) {
            return;
        }

        List<RulerLayout> layouts = buildRulerLayouts(bounds, cameraPos, diagonalMode, trackingMode);
        if (layouts.size() <= 3) {
            return;
        }

        for (int i = 3; i < layouts.size(); i++) {
            renderRulerLayout(layouts.get(i), cameraRight, cameraUp);
        }
    }

    private void markDirty() {
        version++;
        fixedRulerAnchor = null;
    }

    private void rebuildIfNeeded(RenderMode mode) {
        long styleVersion = OverlayRenderStyle.colorVersion();
        if (cachedVersion == version && cachedMode == mode && cachedStyleVersion == styleVersion) {
            return;
        }

        cachedVersion = version;
        cachedStyleVersion = styleVersion;
        cachedMode = mode;
        ArrayList<LineSegment> segments = new ArrayList<>();
        switch (shape) {
            case CUBOID -> buildCuboid(segments);
            case POLYGON_2D -> {
                if (mode == RenderMode.SMOOTH) {
                    buildPolygon2DSmooth(segments);
                } else {
                    buildPolygon2DBlocky(segments);
                }
            }
            case ELLIPSOID -> {
                if (mode == RenderMode.SMOOTH) {
                    buildEllipsoidSmooth(segments);
                } else {
                    buildEllipsoidBlocky(segments);
                }
            }
            case CYLINDER -> {
                if (mode == RenderMode.SMOOTH) {
                    buildCylinderSmooth(segments);
                } else {
                    buildCylinderBlocky(segments);
                }
            }
            case POLYHEDRON -> {
                if (mode == RenderMode.SMOOTH) {
                    buildPolyhedronSmooth(segments);
                } else {
                    buildPolyhedronBlocky(segments);
                }
            }
        }
        cachedSegments = segments.isEmpty() ? List.of() : List.copyOf(segments);
    }

    private void rebuildOutlineIfNeeded() {
        long styleVersion = OverlayRenderStyle.colorVersion();
        if (cachedOutlineVersion == version && cachedOutlineStyleVersion == styleVersion) {
            return;
        }

        cachedOutlineVersion = version;
        cachedOutlineStyleVersion = styleVersion;
        ArrayList<LineSegment> segments = new ArrayList<>();
        switch (shape) {
            case CUBOID -> buildCuboidOutline(segments);
            case POLYGON_2D -> buildPolygon2DOutline(segments);
            case ELLIPSOID -> buildEllipsoidOutline(segments);
            case CYLINDER -> buildCylinderOutline(segments);
            case POLYHEDRON -> buildPolyhedronOutline(segments);
        }
        cachedOutlineSegments = segments.isEmpty() ? List.of() : List.copyOf(segments);
    }

    private VoxelBounds boundsForCuboid() {
        if (cuboidPos1 == null || cuboidPos2 == null) {
            return null;
        }
        return new VoxelBounds(
                Math.min(cuboidPos1.getX(), cuboidPos2.getX()),
                Math.min(cuboidPos1.getY(), cuboidPos2.getY()),
                Math.min(cuboidPos1.getZ(), cuboidPos2.getZ()),
                Math.max(cuboidPos1.getX(), cuboidPos2.getX()),
                Math.max(cuboidPos1.getY(), cuboidPos2.getY()),
                Math.max(cuboidPos1.getZ(), cuboidPos2.getZ())
        );
    }

    private boolean containsCuboid(int x, int y, int z) {
        if (cuboidPos1 == null || cuboidPos2 == null) {
            return false;
        }

        int minX = Math.min(cuboidPos1.getX(), cuboidPos2.getX());
        int minY = Math.min(cuboidPos1.getY(), cuboidPos2.getY());
        int minZ = Math.min(cuboidPos1.getZ(), cuboidPos2.getZ());
        int maxX = Math.max(cuboidPos1.getX(), cuboidPos2.getX());
        int maxY = Math.max(cuboidPos1.getY(), cuboidPos2.getY());
        int maxZ = Math.max(cuboidPos1.getZ(), cuboidPos2.getZ());
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    private VoxelBounds boundsForPolygon() {
        if (polygonPoints.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Point2i point : polygonPoints.values()) {
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
        if (y < minY || y > maxY) {
            return false;
        }
        return polygonContainsXZ(getOrderedPolygonPoints(), x, z);
    }

    private VoxelBounds boundsForEllipsoid() {
        if (ellipsoidCenter == null || ellipsoidRadii == null) {
            return null;
        }

        double radiusX = Math.abs(ellipsoidRadii.getX());
        double radiusY = Math.abs(ellipsoidRadii.getY());
        double radiusZ = Math.abs(ellipsoidRadii.getZ());
        if (radiusX < 0.01 || radiusY < 0.01 || radiusZ < 0.01) {
            return null;
        }

        int minX = (int) Math.floor(ellipsoidCenter.getX() - radiusX);
        int minY = (int) Math.floor(ellipsoidCenter.getY() - radiusY);
        int minZ = (int) Math.floor(ellipsoidCenter.getZ() - radiusZ);
        int maxX = (int) Math.ceil(ellipsoidCenter.getX() + radiusX);
        int maxY = (int) Math.ceil(ellipsoidCenter.getY() + radiusY);
        int maxZ = (int) Math.ceil(ellipsoidCenter.getZ() + radiusZ);
        return new VoxelBounds(minX, minY, minZ, maxX, maxY, maxZ);
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
        double invRadiusX2 = 1.0 / (effectiveRadiusX * effectiveRadiusX);
        double invRadiusY2 = 1.0 / (effectiveRadiusY * effectiveRadiusY);
        double invRadiusZ2 = 1.0 / (effectiveRadiusZ * effectiveRadiusZ);

        double dx = x - ellipsoidCenter.getX();
        double dy = y - ellipsoidCenter.getY();
        double dz = z - ellipsoidCenter.getZ();
        double distance = dx * dx * invRadiusX2 + dy * dy * invRadiusY2 + dz * dz * invRadiusZ2;
        return distance <= 1.0 + 1.0e-9;
    }

    private VoxelBounds boundsForCylinder() {
        if (cylinderCenter == null || cylinderRadiusX <= 0.0 || cylinderRadiusZ <= 0.0) {
            return null;
        }

        int centerY = (int) Math.floor(cylinderCenter.getY());
        int minY = cylinderMinY != null ? cylinderMinY : centerY;
        int maxY = cylinderMaxY != null ? cylinderMaxY : centerY;
        if (maxY < minY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }

        int minX = (int) Math.floor(cylinderCenter.getX() - cylinderRadiusX);
        int maxX = (int) Math.ceil(cylinderCenter.getX() + cylinderRadiusX);
        int minZ = (int) Math.floor(cylinderCenter.getZ() - cylinderRadiusZ);
        int maxZ = (int) Math.ceil(cylinderCenter.getZ() + cylinderRadiusZ);
        return new VoxelBounds(minX, minY, minZ, maxX, maxY, maxZ);
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
        double invRadiusX2 = 1.0 / (effectiveRadiusX * effectiveRadiusX);
        double invRadiusZ2 = 1.0 / (effectiveRadiusZ * effectiveRadiusZ);
        double dx = x - cylinderCenter.getX();
        double dz = z - cylinderCenter.getZ();
        double distance = dx * dx * invRadiusX2 + dz * dz * invRadiusZ2;
        return distance <= 1.0 + 1.0e-9;
    }

    private VoxelBounds boundsForPolyhedron() {
        if (polyhedronVertices.isEmpty() || polyhedronFaces.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Vec3d vertex : polyhedronVertices.values()) {
            minX = Math.min(minX, (int) Math.floor(vertex.getX()));
            minY = Math.min(minY, (int) Math.floor(vertex.getY()));
            minZ = Math.min(minZ, (int) Math.floor(vertex.getZ()));
            maxX = Math.max(maxX, (int) Math.ceil(vertex.getX()));
            maxY = Math.max(maxY, (int) Math.ceil(vertex.getY()));
            maxZ = Math.max(maxZ, (int) Math.ceil(vertex.getZ()));
        }
        return new VoxelBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private boolean containsPolyhedron(int x, int y, int z) {
        List<PolyPlane> planes = getPolyhedronPlanes();
        if (planes.isEmpty()) {
            return false;
        }
        for (PolyPlane plane : planes) {
            if (plane.above(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private List<PolyPlane> getPolyhedronPlanes() {
        if (polyhedronPlanesCacheVersion == version) {
            return polyhedronPlanesCache;
        }

        polyhedronPlanesCacheVersion = version;
        if (polyhedronVertices.size() < 4 || polyhedronFaces.isEmpty()) {
            polyhedronPlanesCache = List.of();
            return polyhedronPlanesCache;
        }

        Vec3d centerAccum = Vec3d.ZERO;
        for (Vec3d vertex : polyhedronVertices.values()) {
            centerAccum = centerAccum.add(vertex);
        }
        Vec3d centroid = centerAccum.multiply(1.0 / polyhedronVertices.size());

        ArrayList<PolyPlane> planes = new ArrayList<>();
        for (int[] face : polyhedronFaces) {
            if (face.length < 3) {
                continue;
            }

            Vec3d v0 = polyhedronVertices.get(face[0]);
            Vec3d v1 = polyhedronVertices.get(face[1]);
            Vec3d v2 = polyhedronVertices.get(face[2]);
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

        polyhedronPlanesCache = planes.isEmpty() ? List.of() : List.copyOf(planes);
        return polyhedronPlanesCache;
    }

    private List<Point2i> getOrderedPolygonPoints() {
        if (polygonOrderedCacheVersion == version) {
            return polygonOrderedCache;
        }
        polygonOrderedCacheVersion = version;
        polygonOrderedCache = polygonPoints.isEmpty() ? List.of() : List.copyOf(polygonPoints.values());
        return polygonOrderedCache;
    }

    private void buildCuboid(ArrayList<LineSegment> segments) {
        if (cuboidPos1 != null) {
            GeometryUtil.addPointCubeSegments(segments, new Vec3d(cuboidPos1.getX(), cuboidPos1.getY(), cuboidPos1.getZ()), OverlayColorSlot.POS1, OverlayRenderStyle.POINT_WIDTH);
        }
        if (cuboidPos2 != null) {
            GeometryUtil.addPointCubeSegments(segments, new Vec3d(cuboidPos2.getX(), cuboidPos2.getY(), cuboidPos2.getZ()), OverlayColorSlot.POS2, OverlayRenderStyle.POINT_WIDTH);
        }
        if (cuboidPos1 == null || cuboidPos2 == null) {
            return;
        }

        int minX = Math.min(cuboidPos1.getX(), cuboidPos2.getX());
        int minY = Math.min(cuboidPos1.getY(), cuboidPos2.getY());
        int minZ = Math.min(cuboidPos1.getZ(), cuboidPos2.getZ());
        int maxX = Math.max(cuboidPos1.getX(), cuboidPos2.getX()) + 1;
        int maxY = Math.max(cuboidPos1.getY(), cuboidPos2.getY()) + 1;
        int maxZ = Math.max(cuboidPos1.getZ(), cuboidPos2.getZ()) + 1;

        int sizeX = maxX - minX;
        int sizeY = maxY - minY;
        int sizeZ = maxZ - minZ;

        int spacing = GeometryUtil.toGridSpacing(gridSpacing);
        while (spacing < OverlayRenderStyle.MAX_GRID_SPACING && GeometryUtil.estimateGridLineSegments(sizeX, sizeY, sizeZ, spacing) > OverlayRenderStyle.MAX_GRID_LINE_SEGMENTS) {
            spacing <<= 1;
        }

        GeometryUtil.addBoxEdges(segments, minX, minY, minZ, maxX, maxY, maxZ, OverlayColorSlot.CUBOID_EDGE, OverlayRenderStyle.BOX_WIDTH);
        GeometryUtil.addSurfaceGrid(segments, minX, minY, minZ, maxX, maxY, maxZ, spacing, OverlayColorSlot.CUBOID_GRID, OverlayRenderStyle.GRID_WIDTH);
        GeometryUtil.addLine(segments, GeometryUtil.toBlockCenter(cuboidPos1), GeometryUtil.toBlockCenter(cuboidPos2), OverlayColorSlot.CUBOID_CONNECTOR, OverlayRenderStyle.CONNECTOR_WIDTH);
    }

    private void buildCuboidOutline(ArrayList<LineSegment> segments) {
        if (cuboidPos1 == null || cuboidPos2 == null) {
            return;
        }

        int minX = Math.min(cuboidPos1.getX(), cuboidPos2.getX());
        int minY = Math.min(cuboidPos1.getY(), cuboidPos2.getY());
        int minZ = Math.min(cuboidPos1.getZ(), cuboidPos2.getZ());
        int maxX = Math.max(cuboidPos1.getX(), cuboidPos2.getX()) + 1;
        int maxY = Math.max(cuboidPos1.getY(), cuboidPos2.getY()) + 1;
        int maxZ = Math.max(cuboidPos1.getZ(), cuboidPos2.getZ()) + 1;

        GeometryUtil.addBoxEdges(segments, minX, minY, minZ, maxX, maxY, maxZ, OverlayColorSlot.CUBOID_EDGE, OverlayRenderStyle.BOX_WIDTH);
    }

    private void buildPolygon2DBlocky(ArrayList<LineSegment> segments) {
        if (polygonPoints.isEmpty()) {
            return;
        }

        int min = polygonHasMinMax ? polygonMinY : 0;
        int max = polygonHasMinMax ? polygonMaxY : 0;
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }

        ArrayList<Point2i> ordered = new ArrayList<>(polygonPoints.values());
        addPolygonPointMarkers(segments, ordered, min);
        if (ordered.size() < 3) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Point2i point : ordered) {
            minX = Math.min(minX, point.x());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxZ = Math.max(maxZ, point.z());
        }

        long cells = (long) (maxX - minX + 1) * (max - min + 1L) * (maxZ - minZ + 1L);
        if (cells > MAX_BLOCKY_CELLS) {
            buildPolygon2DSmooth(segments);
            return;
        }

        final int minY = min;
        final int maxY = max;
        GeometryUtil.addVoxelShellSegments(
                segments,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                (x, y, z) -> y >= minY && y <= maxY && polygonContainsXZ(ordered, x, z),
                OverlayColorSlot.POLYGON_EDGE,
                OverlayRenderStyle.GRID_WIDTH
        );
    }

    private void buildPolygon2DSmooth(ArrayList<LineSegment> segments) {
        if (polygonPoints.isEmpty()) {
            return;
        }

        int min = polygonHasMinMax ? polygonMinY : 0;
        int max = polygonHasMinMax ? polygonMaxY : 0;
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }

        ArrayList<Point2i> ordered = new ArrayList<>(polygonPoints.values());
        addPolygonPointMarkers(segments, ordered, min);

        int topY = max + 1;
        int yStep = 1;
        int levels = Math.max(1, topY - min + 1);
        while (((levels + yStep - 1) / yStep) * ordered.size() > OverlayRenderStyle.MAX_GRID_LINE_SEGMENTS && yStep < OverlayRenderStyle.MAX_GRID_SPACING) {
            yStep <<= 1;
        }

        int y = min;
        boolean topDrawn = false;
        while (y <= topY) {
            boolean border = y == min || y == topY;
            GeometryUtil.addPolygonLoop(
                    segments,
                    ordered,
                    y + OverlayRenderStyle.POLYGON_Y_OFFSET,
                    border ? OverlayColorSlot.POLYGON_EDGE : OverlayColorSlot.POLYGON_GRID,
                    border ? OverlayRenderStyle.BOX_WIDTH : OverlayRenderStyle.GRID_WIDTH
            );
            topDrawn = y == topY;
            y += yStep;
        }
        if (!topDrawn) {
            GeometryUtil.addPolygonLoop(segments, ordered, topY + OverlayRenderStyle.POLYGON_Y_OFFSET, OverlayColorSlot.POLYGON_EDGE, OverlayRenderStyle.BOX_WIDTH);
        }

        double bottomY = min + OverlayRenderStyle.POLYGON_Y_OFFSET;
        double topYWithOffset = topY + OverlayRenderStyle.POLYGON_Y_OFFSET;
        for (Point2i point : ordered) {
            Vec3d bottom = new Vec3d(point.x() + 0.5, bottomY, point.z() + 0.5);
            Vec3d top = new Vec3d(point.x() + 0.5, topYWithOffset, point.z() + 0.5);
            GeometryUtil.addLine(segments, bottom, top, OverlayColorSlot.POLYGON_EDGE, OverlayRenderStyle.GRID_WIDTH);
        }
    }

    private void buildPolygon2DOutline(ArrayList<LineSegment> segments) {
        if (polygonPoints.isEmpty()) {
            return;
        }

        int min = polygonHasMinMax ? polygonMinY : 0;
        int max = polygonHasMinMax ? polygonMaxY : 0;
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }

        ArrayList<Point2i> ordered = new ArrayList<>(polygonPoints.values());
        if (ordered.size() < 3) {
            return;
        }

        double bottomY = min + OverlayRenderStyle.POLYGON_Y_OFFSET;
        double topY = max + 1.0 + OverlayRenderStyle.POLYGON_Y_OFFSET;
        GeometryUtil.addPolygonLoop(segments, ordered, bottomY, OverlayColorSlot.POLYGON_EDGE, OverlayRenderStyle.BOX_WIDTH);
        GeometryUtil.addPolygonLoop(segments, ordered, topY, OverlayColorSlot.POLYGON_EDGE, OverlayRenderStyle.BOX_WIDTH);
        for (Point2i point : ordered) {
            Vec3d bottom = new Vec3d(point.x() + 0.5, bottomY, point.z() + 0.5);
            Vec3d top = new Vec3d(point.x() + 0.5, topY, point.z() + 0.5);
            GeometryUtil.addLine(segments, bottom, top, OverlayColorSlot.POLYGON_EDGE, OverlayRenderStyle.BOX_WIDTH);
        }
    }

    private void addPolygonPointMarkers(ArrayList<LineSegment> segments, List<Point2i> ordered, int minY) {
        for (Point2i point : ordered) {
            GeometryUtil.addPointCubeSegments(segments, new Vec3d(point.x(), minY, point.z()), OverlayColorSlot.POLYGON_POINT, OverlayRenderStyle.POINT_WIDTH);
        }
    }

    private static boolean polygonContainsXZ(List<Point2i> points, int targetX, int targetZ) {
        if (points.size() < 3) {
            return false;
        }

        boolean inside = false;
        int npoints = points.size();
        int xOld = points.get(npoints - 1).x();
        int zOld = points.get(npoints - 1).z();

        for (int i = 0; i < npoints; ++i) {
            int xNew = points.get(i).x();
            int zNew = points.get(i).z();

            if (xNew == targetX && zNew == targetZ) {
                return true;
            }

            int x1;
            int x2;
            int z1;
            int z2;
            if (xNew > xOld) {
                x1 = xOld;
                x2 = xNew;
                z1 = zOld;
                z2 = zNew;
            } else {
                x1 = xNew;
                x2 = xOld;
                z1 = zNew;
                z2 = zOld;
            }

            if (x1 <= targetX && targetX <= x2) {
                long crossProduct = ((long) targetZ - z1) * (x2 - x1) - ((long) z2 - z1) * (targetX - x1);
                if (crossProduct == 0) {
                    if ((z1 <= targetZ) == (targetZ <= z2)) {
                        return true;
                    }
                } else if (crossProduct < 0 && (x1 != targetX)) {
                    inside = !inside;
                }
            }

            xOld = xNew;
            zOld = zNew;
        }

        return inside;
    }

    private void buildEllipsoidBlocky(ArrayList<LineSegment> segments) {
        if (ellipsoidCenter == null) {
            return;
        }

        GeometryUtil.addPointCubeSegments(segments, ellipsoidCenter, OverlayColorSlot.ELLIPSOID_CENTER, OverlayRenderStyle.POINT_WIDTH);
        if (ellipsoidRadii == null) {
            return;
        }

        double radiusX = Math.abs(ellipsoidRadii.getX());
        double radiusY = Math.abs(ellipsoidRadii.getY());
        double radiusZ = Math.abs(ellipsoidRadii.getZ());
        if (radiusX < 0.01 || radiusY < 0.01 || radiusZ < 0.01) {
            return;
        }
        double centerX = ellipsoidCenter.getX();
        double centerY = ellipsoidCenter.getY();
        double centerZ = ellipsoidCenter.getZ();

        int minX = (int) Math.floor(centerX - radiusX);
        int maxX = (int) Math.ceil(centerX + radiusX);
        int minY = (int) Math.floor(centerY - radiusY);
        int maxY = (int) Math.ceil(centerY + radiusY);
        int minZ = (int) Math.floor(centerZ - radiusZ);
        int maxZ = (int) Math.ceil(centerZ + radiusZ);

        double effectiveRadiusX = radiusX + 0.5;
        double effectiveRadiusY = radiusY + 0.5;
        double effectiveRadiusZ = radiusZ + 0.5;
        double invRadiusX2 = 1.0 / (effectiveRadiusX * effectiveRadiusX);
        double invRadiusY2 = 1.0 / (effectiveRadiusY * effectiveRadiusY);
        double invRadiusZ2 = 1.0 / (effectiveRadiusZ * effectiveRadiusZ);

        GeometryUtil.addVoxelShellSegments(
                segments,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                (x, y, z) -> {
                    double dx = x - centerX;
                    double dy = y - centerY;
                    double dz = z - centerZ;
                    double distance = dx * dx * invRadiusX2 + dy * dy * invRadiusY2 + dz * dz * invRadiusZ2;
                    return distance <= 1.0 + 1.0e-9;
                },
                OverlayColorSlot.ELLIPSOID_EDGE,
                OverlayRenderStyle.GRID_WIDTH
        );
    }

    private void buildEllipsoidSmooth(ArrayList<LineSegment> segments) {
        if (ellipsoidCenter == null) {
            return;
        }

        GeometryUtil.addPointCubeSegments(segments, ellipsoidCenter, OverlayColorSlot.ELLIPSOID_CENTER, OverlayRenderStyle.POINT_WIDTH);
        if (ellipsoidRadii == null) {
            return;
        }

        double radiusX = Math.abs(ellipsoidRadii.getX());
        double radiusY = Math.abs(ellipsoidRadii.getY());
        double radiusZ = Math.abs(ellipsoidRadii.getZ());
        if (radiusX < 0.01 || radiusY < 0.01 || radiusZ < 0.01) {
            return;
        }

        Vec3d center = GeometryUtil.toBlockCenter(ellipsoidCenter);
        double maxRadius = Math.max(radiusX, Math.max(radiusY, radiusZ));
        int latitudeBands = clampInt((int) Math.ceil(maxRadius * 2.0) + 4, 6, 18);
        int longitudeSegments = clampInt((int) Math.ceil(maxRadius * 8.0), 24, OverlayRenderStyle.CYLINDER_MAX_SEGMENTS);
        int meridianCount = clampInt(longitudeSegments / 2, 8, 32);

        while (((latitudeBands - 1) * longitudeSegments + meridianCount * latitudeBands) > OverlayRenderStyle.MAX_GRID_LINE_SEGMENTS) {
            if (longitudeSegments > 16) {
                longitudeSegments -= 4;
                meridianCount = clampInt(longitudeSegments / 2, 8, 32);
            } else if (latitudeBands > 6) {
                latitudeBands--;
            } else {
                break;
            }
        }

        int equatorBand = latitudeBands / 2;
        for (int lat = 1; lat < latitudeBands; lat++) {
            double phi = -Math.PI / 2.0 + Math.PI * lat / latitudeBands;
            double cosPhi = Math.cos(phi);
            double sinPhi = Math.sin(phi);
            double y = center.getY() + radiusY * sinPhi;
            double loopRadiusX = radiusX * cosPhi;
            double loopRadiusZ = radiusZ * cosPhi;
            if (loopRadiusX <= 0.0 || loopRadiusZ <= 0.0) {
                continue;
            }

            boolean highlight = lat == equatorBand;
            GeometryUtil.addEllipseLoopXZ(
                    segments,
                    center.getX(),
                    y,
                    center.getZ(),
                    loopRadiusX,
                    loopRadiusZ,
                    OverlayColorSlot.ELLIPSOID_EDGE,
                    highlight ? OverlayRenderStyle.BOX_WIDTH : OverlayRenderStyle.GRID_WIDTH,
                    longitudeSegments
            );
        }

        for (int meridian = 0; meridian < meridianCount; meridian++) {
            double theta = (Math.PI * 2.0) * meridian / meridianCount;
            double cosTheta = Math.cos(theta);
            double sinTheta = Math.sin(theta);

            Vec3d prev = null;
            for (int lat = 0; lat <= latitudeBands; lat++) {
                double phi = -Math.PI / 2.0 + Math.PI * lat / latitudeBands;
                double cosPhi = Math.cos(phi);
                double sinPhi = Math.sin(phi);
                Vec3d current = new Vec3d(
                        center.getX() + radiusX * cosPhi * cosTheta,
                        center.getY() + radiusY * sinPhi,
                        center.getZ() + radiusZ * cosPhi * sinTheta
                );
                if (prev != null) {
                    GeometryUtil.addLine(segments, prev, current, OverlayColorSlot.ELLIPSOID_EDGE, OverlayRenderStyle.GRID_WIDTH);
                }
                prev = current;
            }
        }
    }

    private void buildEllipsoidOutline(ArrayList<LineSegment> segments) {
        if (ellipsoidCenter == null || ellipsoidRadii == null) {
            return;
        }

        double radiusX = Math.abs(ellipsoidRadii.getX());
        double radiusY = Math.abs(ellipsoidRadii.getY());
        double radiusZ = Math.abs(ellipsoidRadii.getZ());
        if (radiusX < 0.01 || radiusY < 0.01 || radiusZ < 0.01) {
            return;
        }

        Vec3d center = GeometryUtil.toBlockCenter(ellipsoidCenter);
        int segmentsCount = GeometryUtil.cylinderRadialSegments(Math.max(radiusX, Math.max(radiusY, radiusZ)));
        GeometryUtil.addEllipseLoopXZ(segments, center.getX(), center.getY(), center.getZ(), radiusX, radiusZ, OverlayColorSlot.ELLIPSOID_EDGE, OverlayRenderStyle.BOX_WIDTH, segmentsCount);
        GeometryUtil.addEllipseLoopYZ(segments, center.getX(), center.getY(), center.getZ(), radiusY, radiusZ, OverlayColorSlot.ELLIPSOID_EDGE, OverlayRenderStyle.BOX_WIDTH, segmentsCount);
        GeometryUtil.addEllipseLoopXY(segments, center.getX(), center.getY(), center.getZ(), radiusX, radiusY, OverlayColorSlot.ELLIPSOID_EDGE, OverlayRenderStyle.BOX_WIDTH, segmentsCount);
    }

    private void buildCylinderBlocky(ArrayList<LineSegment> segments) {
        if (cylinderCenter == null) {
            return;
        }

        GeometryUtil.addPointCubeSegments(segments, cylinderCenter, OverlayColorSlot.CYLINDER_CENTER, OverlayRenderStyle.POINT_WIDTH);
        if (cylinderRadiusX <= 0.0 || cylinderRadiusZ <= 0.0) {
            return;
        }

        int centerY = (int) Math.floor(cylinderCenter.getY());
        int minY = cylinderMinY != null ? cylinderMinY : centerY;
        int maxY = cylinderMaxY != null ? cylinderMaxY : centerY;
        if (maxY < minY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }
        final int finalMinY = minY;
        final int finalMaxY = maxY;

        double centerX = cylinderCenter.getX();
        double centerZ = cylinderCenter.getZ();
        int minX = (int) Math.floor(centerX - cylinderRadiusX);
        int maxX = (int) Math.ceil(centerX + cylinderRadiusX);
        int minZ = (int) Math.floor(centerZ - cylinderRadiusZ);
        int maxZ = (int) Math.ceil(centerZ + cylinderRadiusZ);

        double effectiveRadiusX = cylinderRadiusX + 0.5;
        double effectiveRadiusZ = cylinderRadiusZ + 0.5;
        double invRadiusX2 = 1.0 / (effectiveRadiusX * effectiveRadiusX);
        double invRadiusZ2 = 1.0 / (effectiveRadiusZ * effectiveRadiusZ);

        GeometryUtil.addVoxelShellSegments(
                segments,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                (x, y, z) -> {
                    if (y < finalMinY || y > finalMaxY) {
                        return false;
                    }
                    double dx = x - centerX;
                    double dz = z - centerZ;
                    double distance = dx * dx * invRadiusX2 + dz * dz * invRadiusZ2;
                    return distance <= 1.0 + 1.0e-9;
                },
                OverlayColorSlot.CYLINDER_EDGE,
                OverlayRenderStyle.GRID_WIDTH
        );
    }

    private void buildCylinderSmooth(ArrayList<LineSegment> segments) {
        if (cylinderCenter == null) {
            return;
        }

        GeometryUtil.addPointCubeSegments(segments, cylinderCenter, OverlayColorSlot.CYLINDER_CENTER, OverlayRenderStyle.POINT_WIDTH);
        if (cylinderRadiusX <= 0.0 || cylinderRadiusZ <= 0.0) {
            return;
        }

        int centerY = (int) Math.floor(cylinderCenter.getY());
        int minY = cylinderMinY != null ? cylinderMinY : centerY;
        int maxY = cylinderMaxY != null ? cylinderMaxY : centerY;
        if (maxY < minY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }

        double centerX = cylinderCenter.getX() + 0.5;
        double centerZ = cylinderCenter.getZ() + 0.5;
        double bottom = minY;
        double top = maxY + 1.0;

        double maxRadius = Math.max(cylinderRadiusX, cylinderRadiusZ);
        int radialSegments = GeometryUtil.cylinderRadialSegments(maxRadius);
        int capRings = clampInt((int) Math.ceil(maxRadius / 2.0), 2, 6);
        int verticalLines = clampInt(radialSegments / 2, 12, 48);
        int capSpokes = clampInt(radialSegments / 2, 8, 32);

        int interiorLevels = Math.max(0, maxY - minY);
        int yStep = 1;
        while (true) {
            int interiorLoops = (interiorLevels + yStep - 1) / yStep;
            int estimatedSegments = 2 * radialSegments
                    + interiorLoops * radialSegments
                    + verticalLines
                    + 2 * (capRings - 1) * radialSegments
                    + 2 * capSpokes;
            if (estimatedSegments <= OverlayRenderStyle.MAX_GRID_LINE_SEGMENTS) {
                break;
            }

            if (radialSegments > 20) {
                radialSegments -= 4;
                verticalLines = clampInt(radialSegments / 2, 10, 40);
                capSpokes = clampInt(radialSegments / 2, 8, 28);
            } else if (yStep < OverlayRenderStyle.MAX_GRID_SPACING) {
                yStep <<= 1;
            } else if (capRings > 2) {
                capRings--;
            } else {
                break;
            }
        }

        GeometryUtil.addEllipseLoopXZ(segments, centerX, bottom, centerZ, cylinderRadiusX, cylinderRadiusZ, OverlayColorSlot.CYLINDER_EDGE, OverlayRenderStyle.BOX_WIDTH, radialSegments);
        GeometryUtil.addEllipseLoopXZ(segments, centerX, top, centerZ, cylinderRadiusX, cylinderRadiusZ, OverlayColorSlot.CYLINDER_EDGE, OverlayRenderStyle.BOX_WIDTH, radialSegments);

        for (int y = minY + 1; y <= maxY; y += yStep) {
            GeometryUtil.addEllipseLoopXZ(segments, centerX, y, centerZ, cylinderRadiusX, cylinderRadiusZ, OverlayColorSlot.CYLINDER_GRID, OverlayRenderStyle.GRID_WIDTH, radialSegments);
        }

        for (int i = 0; i < verticalLines; i++) {
            double angle = i * (Math.PI * 2.0) / verticalLines;
            double x = centerX + cylinderRadiusX * Math.cos(angle);
            double z = centerZ + cylinderRadiusZ * Math.sin(angle);
            GeometryUtil.addLine(segments, new Vec3d(x, bottom, z), new Vec3d(x, top, z), OverlayColorSlot.CYLINDER_GRID, OverlayRenderStyle.GRID_WIDTH);
        }

        for (int ring = 1; ring < capRings; ring++) {
            double factor = (double) ring / capRings;
            double ringRadiusX = cylinderRadiusX * factor;
            double ringRadiusZ = cylinderRadiusZ * factor;
            GeometryUtil.addEllipseLoopXZ(segments, centerX, bottom, centerZ, ringRadiusX, ringRadiusZ, OverlayColorSlot.CYLINDER_GRID, OverlayRenderStyle.GRID_WIDTH, radialSegments);
            GeometryUtil.addEllipseLoopXZ(segments, centerX, top, centerZ, ringRadiusX, ringRadiusZ, OverlayColorSlot.CYLINDER_GRID, OverlayRenderStyle.GRID_WIDTH, radialSegments);
        }

        Vec3d bottomCenter = new Vec3d(centerX, bottom, centerZ);
        Vec3d topCenter = new Vec3d(centerX, top, centerZ);
        for (int i = 0; i < capSpokes; i++) {
            double angle = i * (Math.PI * 2.0) / capSpokes;
            double x = centerX + cylinderRadiusX * Math.cos(angle);
            double z = centerZ + cylinderRadiusZ * Math.sin(angle);
            GeometryUtil.addLine(segments, bottomCenter, new Vec3d(x, bottom, z), OverlayColorSlot.CYLINDER_GRID, OverlayRenderStyle.GRID_WIDTH);
            GeometryUtil.addLine(segments, topCenter, new Vec3d(x, top, z), OverlayColorSlot.CYLINDER_GRID, OverlayRenderStyle.GRID_WIDTH);
        }
    }

    private void buildCylinderOutline(ArrayList<LineSegment> segments) {
        if (cylinderCenter == null || cylinderRadiusX <= 0.0 || cylinderRadiusZ <= 0.0) {
            return;
        }

        int centerY = (int) Math.floor(cylinderCenter.getY());
        int minY = cylinderMinY != null ? cylinderMinY : centerY;
        int maxY = cylinderMaxY != null ? cylinderMaxY : centerY;
        if (maxY < minY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }

        double centerX = cylinderCenter.getX() + 0.5;
        double centerZ = cylinderCenter.getZ() + 0.5;
        double bottom = minY;
        double top = maxY + 1.0;
        double maxRadius = Math.max(cylinderRadiusX, cylinderRadiusZ);
        int radialSegments = GeometryUtil.cylinderRadialSegments(maxRadius);
        int verticalLines = clampInt(radialSegments / 2, 12, 32);

        GeometryUtil.addEllipseLoopXZ(segments, centerX, bottom, centerZ, cylinderRadiusX, cylinderRadiusZ, OverlayColorSlot.CYLINDER_EDGE, OverlayRenderStyle.BOX_WIDTH, radialSegments);
        GeometryUtil.addEllipseLoopXZ(segments, centerX, top, centerZ, cylinderRadiusX, cylinderRadiusZ, OverlayColorSlot.CYLINDER_EDGE, OverlayRenderStyle.BOX_WIDTH, radialSegments);
        for (int i = 0; i < verticalLines; i++) {
            double angle = i * (Math.PI * 2.0) / verticalLines;
            double x = centerX + cylinderRadiusX * Math.cos(angle);
            double z = centerZ + cylinderRadiusZ * Math.sin(angle);
            GeometryUtil.addLine(segments, new Vec3d(x, bottom, z), new Vec3d(x, top, z), OverlayColorSlot.CYLINDER_EDGE, OverlayRenderStyle.BOX_WIDTH);
        }
    }

    private void buildPolyhedronBlocky(ArrayList<LineSegment> segments) {
        if (polyhedronVertices.isEmpty()) {
            return;
        }

        addPolyhedronPointMarkers(segments);
        if (polyhedronFaces.isEmpty() || polyhedronVertices.size() < 4) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Vec3d vertex : polyhedronVertices.values()) {
            minX = Math.min(minX, (int) Math.floor(vertex.getX()));
            minY = Math.min(minY, (int) Math.floor(vertex.getY()));
            minZ = Math.min(minZ, (int) Math.floor(vertex.getZ()));
            maxX = Math.max(maxX, (int) Math.ceil(vertex.getX()));
            maxY = Math.max(maxY, (int) Math.ceil(vertex.getY()));
            maxZ = Math.max(maxZ, (int) Math.ceil(vertex.getZ()));
        }

        long cells = (long) (maxX - minX + 1) * (maxY - minY + 1L) * (maxZ - minZ + 1L);
        if (cells > MAX_BLOCKY_CELLS) {
            buildPolyhedronSmooth(segments);
            return;
        }

        List<PolyPlane> planes = getPolyhedronPlanes();

        if (planes.isEmpty()) {
            buildPolyhedronSmooth(segments);
            return;
        }

        GeometryUtil.addVoxelShellSegments(
                segments,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                (x, y, z) -> {
                    for (PolyPlane plane : planes) {
                        if (plane.above(x, y, z)) {
                            return false;
                        }
                    }
                    return true;
                },
                OverlayColorSlot.POLYHEDRON_EDGE,
                OverlayRenderStyle.GRID_WIDTH
        );
    }

    private void buildPolyhedronSmooth(ArrayList<LineSegment> segments) {
        if (polyhedronVertices.isEmpty()) {
            return;
        }

        addPolyhedronPointMarkers(segments);

        if (polyhedronFaces.isEmpty()) {
            return;
        }

        HashSet<Long> seenEdges = new HashSet<>();
        for (int[] face : polyhedronFaces) {
            if (face.length < 2) {
                continue;
            }
            for (int i = 0; i < face.length; i++) {
                int aId = face[i];
                int bId = face[(i + 1) % face.length];
                Vec3d a = polyhedronVertices.get(aId);
                Vec3d b = polyhedronVertices.get(bId);
                if (a == null || b == null) {
                    continue;
                }

                long key = GeometryUtil.edgeKey(aId, bId);
                if (!seenEdges.add(key)) {
                    continue;
                }

                GeometryUtil.addLine(
                        segments,
                        GeometryUtil.toBlockCenter(a),
                        GeometryUtil.toBlockCenter(b),
                        OverlayColorSlot.POLYHEDRON_EDGE,
                        OverlayRenderStyle.GRID_WIDTH
                );
            }
        }
    }

    private void buildPolyhedronOutline(ArrayList<LineSegment> segments) {
        if (polyhedronVertices.isEmpty() || polyhedronFaces.isEmpty()) {
            return;
        }

        HashSet<Long> seenEdges = new HashSet<>();
        for (int[] face : polyhedronFaces) {
            if (face.length < 2) {
                continue;
            }
            for (int i = 0; i < face.length; i++) {
                int aId = face[i];
                int bId = face[(i + 1) % face.length];
                Vec3d a = polyhedronVertices.get(aId);
                Vec3d b = polyhedronVertices.get(bId);
                if (a == null || b == null) {
                    continue;
                }

                long key = GeometryUtil.edgeKey(aId, bId);
                if (!seenEdges.add(key)) {
                    continue;
                }

                GeometryUtil.addLine(
                        segments,
                        GeometryUtil.toBlockCenter(a),
                        GeometryUtil.toBlockCenter(b),
                        OverlayColorSlot.POLYHEDRON_EDGE,
                        OverlayRenderStyle.BOX_WIDTH
                );
            }
        }
    }

    private void addPolyhedronPointMarkers(ArrayList<LineSegment> segments) {
        for (var entry : polyhedronVertices.entrySet()) {
            OverlayColorSlot slot = entry.getKey() == 0 ? OverlayColorSlot.POS1 : OverlayColorSlot.POLYHEDRON_POINT;
            GeometryUtil.addPointCubeSegments(segments, entry.getValue(), slot, OverlayRenderStyle.POINT_WIDTH);
        }
    }

    private List<RulerLayout> buildRulerLayouts(
            VoxelBounds bounds,
            Vec3d cameraPos,
            DiagonalMode diagonalMode,
            RulerTrackingMode trackingMode
    ) {
        double[] min = {bounds.minX(), bounds.minY(), bounds.minZ()};
        double[] max = {bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0};
        int[] size = {bounds.sizeX(), bounds.sizeY(), bounds.sizeZ()};
        double[] center = {
                (min[0] + max[0]) * 0.5,
                (min[1] + max[1]) * 0.5,
                (min[2] + max[2]) * 0.5
        };

        RulerAnchor anchor = resolveRulerAnchor(cameraPos, center, trackingMode);
        Side side = anchor.side();
        int normalAxis = side.axis();
        int normalSign = side.sign();
        int[] planeAxes = orderedPlaneAxes(normalAxis, size);
        int majorAxis = planeAxes[0];
        int minorAxis = planeAxes[1];

        int[] nearSign = anchor.nearSignCopy();
        double[] nearEdge = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            nearEdge[axis] = nearSign[axis] > 0 ? max[axis] : min[axis];
        }

        double frontEdge = normalSign > 0 ? max[normalAxis] : min[normalAxis];
        Vec3d normalVec = axisVector(normalAxis, normalSign);
        Vec3d majorOut = axisVector(majorAxis, nearSign[majorAxis]);
        Vec3d minorOut = axisVector(minorAxis, nearSign[minorAxis]);

        ArrayList<RulerLayout> layouts = new ArrayList<>(3);

        {
            double[] anchorStart = new double[3];
            double[] anchorEnd = new double[3];
            anchorStart[normalAxis] = frontEdge;
            anchorEnd[normalAxis] = frontEdge;
            anchorStart[minorAxis] = nearEdge[minorAxis];
            anchorEnd[minorAxis] = nearEdge[minorAxis];
            anchorStart[majorAxis] = min[majorAxis];
            anchorEnd[majorAxis] = max[majorAxis];
            Vec3d offset = normalVec.multiply(OverlayRenderStyle.RULER_SURFACE_OFFSET)
                    .add(minorOut.multiply(OverlayRenderStyle.RULER_EDGE_OFFSET));
            layouts.add(makeRulerLayout(dimensionLabel(size[majorAxis]), majorAxis, anchorStart, anchorEnd, offset, normalVec));
        }

        {
            double[] anchorStart = new double[3];
            double[] anchorEnd = new double[3];
            anchorStart[normalAxis] = frontEdge;
            anchorEnd[normalAxis] = frontEdge;
            anchorStart[majorAxis] = nearEdge[majorAxis];
            anchorEnd[majorAxis] = nearEdge[majorAxis];
            anchorStart[minorAxis] = min[minorAxis];
            anchorEnd[minorAxis] = max[minorAxis];
            Vec3d offset = normalVec.multiply(OverlayRenderStyle.RULER_SURFACE_OFFSET)
                    .add(majorOut.multiply(OverlayRenderStyle.RULER_EDGE_OFFSET));
            layouts.add(makeRulerLayout(dimensionLabel(size[minorAxis]), minorAxis, anchorStart, anchorEnd, offset, normalVec));
        }

        {
            double[] anchorStart = new double[3];
            double[] anchorEnd = new double[3];
            anchorStart[majorAxis] = nearEdge[majorAxis];
            anchorEnd[majorAxis] = nearEdge[majorAxis];
            anchorStart[minorAxis] = nearEdge[minorAxis];
            anchorEnd[minorAxis] = nearEdge[minorAxis];
            anchorStart[normalAxis] = min[normalAxis];
            anchorEnd[normalAxis] = max[normalAxis];
            Vec3d offset = normalVec.multiply(OverlayRenderStyle.RULER_SURFACE_OFFSET)
                    .add(majorOut.multiply(OverlayRenderStyle.RULER_CORNER_OFFSET))
                    .add(minorOut.multiply(OverlayRenderStyle.RULER_CORNER_OFFSET));
            layouts.add(makeRulerLayout(dimensionLabel(size[normalAxis]), normalAxis, anchorStart, anchorEnd, offset, normalVec));
        }

        if (shape == RegionShape.CUBOID && diagonalMode != null && diagonalMode != DiagonalMode.NONE) {
            addCuboidFaceDiagonals(layouts, diagonalMode, min, max, majorAxis, minorAxis, normalAxis, frontEdge, normalVec);
        }

        return layouts;
    }

    private void renderRulerLayout(RulerLayout layout, Vec3d cameraRight, Vec3d cameraUp) {
        Vec3d line = layout.dimensionEnd().subtract(layout.dimensionStart());
        double length = line.length();
        if (length < 1.0e-6) {
            return;
        }

        Vec3d direction = line.multiply(1.0 / length);
        double arrowLength = Math.min(
                OverlayRenderStyle.RULER_ARROW_MAX_LENGTH,
                Math.max(OverlayRenderStyle.RULER_ARROW_MIN_LENGTH, length * 0.18)
        );
        arrowLength = Math.min(arrowLength, length * 0.5);

        GizmoDrawing.line(layout.anchorStart(), layout.dimensionStart(), OverlayRenderStyle.color(OverlayColorSlot.RULER_LINE), OverlayRenderStyle.lineWidth(OverlayRenderStyle.RULER_EXTENSION_WIDTH, OverlayColorSlot.RULER_LINE))
                .ignoreOcclusion()
                .withLifespan(OverlayRenderStyle.GIZMO_LIFESPAN_MS);
        GizmoDrawing.line(layout.anchorEnd(), layout.dimensionEnd(), OverlayRenderStyle.color(OverlayColorSlot.RULER_LINE), OverlayRenderStyle.lineWidth(OverlayRenderStyle.RULER_EXTENSION_WIDTH, OverlayColorSlot.RULER_LINE))
                .ignoreOcclusion()
                .withLifespan(OverlayRenderStyle.GIZMO_LIFESPAN_MS);

        GizmoDrawing.line(layout.dimensionStart(), layout.dimensionEnd(), OverlayRenderStyle.color(OverlayColorSlot.RULER_LINE), OverlayRenderStyle.lineWidth(OverlayRenderStyle.RULER_LINE_WIDTH, OverlayColorSlot.RULER_LINE))
                .ignoreOcclusion()
                .withLifespan(OverlayRenderStyle.GIZMO_LIFESPAN_MS);
        GizmoDrawing.arrow(layout.dimensionStart(), layout.dimensionStart().add(direction.multiply(arrowLength)), OverlayRenderStyle.color(OverlayColorSlot.RULER_ARROW), OverlayRenderStyle.lineWidth(OverlayRenderStyle.RULER_ARROW_WIDTH, OverlayColorSlot.RULER_ARROW))
                .ignoreOcclusion()
                .withLifespan(OverlayRenderStyle.GIZMO_LIFESPAN_MS);
        GizmoDrawing.arrow(layout.dimensionEnd(), layout.dimensionEnd().subtract(direction.multiply(arrowLength)), OverlayRenderStyle.color(OverlayColorSlot.RULER_ARROW), OverlayRenderStyle.lineWidth(OverlayRenderStyle.RULER_ARROW_WIDTH, OverlayColorSlot.RULER_ARROW))
                .ignoreOcclusion()
                .withLifespan(OverlayRenderStyle.GIZMO_LIFESPAN_MS);

        Vec3d midpoint = layout.dimensionStart().add(line.multiply(0.5));
        Vec3d labelPos = midpoint.add(layout.normal().multiply(OverlayRenderStyle.RULER_TEXT_OFFSET));
        drawVectorLabel(layout.label(), labelPos, layout.outwardHint(), length, cameraRight, cameraUp);
    }

    private static RulerLayout makeRulerLayout(String label, int axis, double[] anchorStart, double[] anchorEnd, Vec3d offset, Vec3d normal) {
        Vec3d anchorStartVec = new Vec3d(anchorStart[0], anchorStart[1], anchorStart[2]);
        Vec3d anchorEndVec = new Vec3d(anchorEnd[0], anchorEnd[1], anchorEnd[2]);
        return new RulerLayout(
                label,
                axis,
                anchorStartVec,
                anchorEndVec,
                anchorStartVec.add(offset),
                anchorEndVec.add(offset),
                normal,
                offset
        );
    }

    private static Side resolveFacingSide(Vec3d cameraPos, double[] center) {
        double dx = cameraPos.getX() - center[0];
        double dy = cameraPos.getY() - center[1];
        double dz = cameraPos.getZ() - center[2];
        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);

        if (absX >= absY && absX >= absZ) {
            return dx >= 0.0 ? Side.X_POS : Side.X_NEG;
        }
        if (absY >= absX && absY >= absZ) {
            return dy >= 0.0 ? Side.Y_POS : Side.Y_NEG;
        }
        return dz >= 0.0 ? Side.Z_POS : Side.Z_NEG;
    }

    private static int[] orderedPlaneAxes(int normalAxis, int[] sizes) {
        int first = (normalAxis + 1) % 3;
        int second = (normalAxis + 2) % 3;
        if (sizes[second] > sizes[first]) {
            int tmp = first;
            first = second;
            second = tmp;
        }
        return new int[]{first, second};
    }

    private static Vec3d axisVector(int axis, double value) {
        return switch (axis) {
            case 0 -> new Vec3d(value, 0.0, 0.0);
            case 1 -> new Vec3d(0.0, value, 0.0);
            case 2 -> new Vec3d(0.0, 0.0, value);
            default -> Vec3d.ZERO;
        };
    }

    private static double cameraComponent(Vec3d cameraPos, int axis) {
        return switch (axis) {
            case 0 -> cameraPos.getX();
            case 1 -> cameraPos.getY();
            case 2 -> cameraPos.getZ();
            default -> 0.0;
        };
    }

    private RulerAnchor resolveRulerAnchor(Vec3d cameraPos, double[] center, RulerTrackingMode trackingMode) {
        RulerTrackingMode mode = trackingMode == null ? RulerTrackingMode.FOLLOW_CAMERA : trackingMode;
        if (mode != RulerTrackingMode.FIXED_SIDE) {
            fixedRulerAnchor = null;
            return createAnchor(resolveFacingSide(cameraPos, center), cameraPos, center);
        }

        Side desiredSide = resolveFacingSide(cameraPos, center);
        if (fixedRulerAnchor == null) {
            fixedRulerAnchor = createAnchor(desiredSide, cameraPos, center);
            return fixedRulerAnchor;
        }

        Side stableSide = resolveStableSide(cameraPos, center, fixedRulerAnchor.side(), desiredSide);
        if (stableSide != fixedRulerAnchor.side()) {
            fixedRulerAnchor = createAnchor(stableSide, cameraPos, center);
        }
        return fixedRulerAnchor;
    }

    private static Side resolveStableSide(Vec3d cameraPos, double[] center, Side current, Side desired) {
        if (current == null) {
            return desired;
        }
        if (desired == null) {
            return current;
        }
        if (current == desired) {
            return current;
        }

        double[] absDelta = {
                Math.abs(cameraPos.getX() - center[0]),
                Math.abs(cameraPos.getY() - center[1]),
                Math.abs(cameraPos.getZ() - center[2])
        };
        double currentAbs = absDelta[current.axis()];
        double desiredAbs = absDelta[desired.axis()];
        if (desired.axis() != current.axis()) {
            return desiredAbs > currentAbs + SIDE_SWITCH_MARGIN ? desired : current;
        }

        double currentDelta = cameraComponent(cameraPos, current.axis()) - center[current.axis()];
        double desiredDelta = cameraComponent(cameraPos, desired.axis()) - center[desired.axis()];
        if (Math.abs(desiredDelta) <= SIDE_SIGN_SWITCH_MARGIN) {
            return current;
        }
        if (Math.abs(currentDelta) > SIDE_SIGN_SWITCH_MARGIN && Math.signum(currentDelta) == current.sign()) {
            return current;
        }
        return desired;
    }

    private static RulerAnchor createAnchor(Side side, Vec3d cameraPos, double[] center) {
        int[] signs = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            signs[axis] = cameraComponent(cameraPos, axis) >= center[axis] ? 1 : -1;
        }
        return new RulerAnchor(side, signs[0], signs[1], signs[2]);
    }

    private static String dimensionLabel(int length) {
        return Integer.toString(length);
    }

    private static boolean axisEnabled(int axis, boolean showAxisX, boolean showAxisY, boolean showAxisZ) {
        return switch (axis) {
            case 0 -> showAxisX;
            case 1 -> showAxisY;
            case 2 -> showAxisZ;
            default -> true;
        };
    }

    private static void addCuboidFaceDiagonals(
            ArrayList<RulerLayout> layouts,
            DiagonalMode diagonalMode,
            double[] min,
            double[] max,
            int majorAxis,
            int minorAxis,
            int normalAxis,
            double frontEdge,
            Vec3d normalVec
    ) {
        double[] d1Start = new double[3];
        double[] d1End = new double[3];
        d1Start[normalAxis] = frontEdge;
        d1End[normalAxis] = frontEdge;
        d1Start[majorAxis] = min[majorAxis];
        d1End[majorAxis] = max[majorAxis];
        d1Start[minorAxis] = min[minorAxis];
        d1End[minorAxis] = max[minorAxis];

        // Keep diagonals centered on the face: offset only along face normal
        // so their crossing point stays stable while the camera moves.
        Vec3d diagonalOffset = normalVec.multiply(OverlayRenderStyle.RULER_SURFACE_OFFSET);

        layouts.add(makeRulerLayout("", -1, d1Start, d1End, diagonalOffset, normalVec));

        if (diagonalMode == DiagonalMode.TWO) {
            double[] d2Start = new double[3];
            double[] d2End = new double[3];
            d2Start[normalAxis] = frontEdge;
            d2End[normalAxis] = frontEdge;
            d2Start[majorAxis] = min[majorAxis];
            d2End[majorAxis] = max[majorAxis];
            d2Start[minorAxis] = max[minorAxis];
            d2End[minorAxis] = min[minorAxis];
            layouts.add(makeRulerLayout("", -1, d2Start, d2End, diagonalOffset, normalVec));
        }
    }

    private static void drawVectorLabel(
            String text,
            Vec3d center,
            Vec3d outwardHint,
            double rulerLength,
            Vec3d cameraRight,
            Vec3d cameraUp
    ) {
        if (text == null || text.isEmpty()) {
            return;
        }

        Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
        Vec3d tangent = cameraRight;
        if (tangent == null || tangent.lengthSquared() < 1.0e-8) {
            tangent = new Vec3d(1.0, 0.0, 0.0);
        } else {
            tangent = tangent.normalize();
        }

        Vec3d up = cameraUp;
        if (up == null || up.lengthSquared() < 1.0e-8) {
            up = worldUp;
        } else {
            up = up.normalize();
        }
        up = up.subtract(tangent.multiply(up.dotProduct(tangent)));
        if (up.lengthSquared() < 1.0e-8) {
            up = worldUp.subtract(tangent.multiply(worldUp.dotProduct(tangent)));
        }
        if (up.lengthSquared() < 1.0e-8) {
            up = new Vec3d(0.0, 1.0, 0.0);
        } else {
            up = up.normalize();
        }

        double charHeight = clampDouble(
                rulerLength * OverlayRenderStyle.RULER_LABEL_HEIGHT_FACTOR,
                OverlayRenderStyle.RULER_LABEL_MIN_HEIGHT,
                OverlayRenderStyle.RULER_LABEL_MAX_HEIGHT
        );
        double spacing = charHeight * OverlayRenderStyle.RULER_LABEL_CHAR_SPACING;
        double labelLift = Math.max(
                charHeight * OverlayRenderStyle.RULER_LABEL_LIFT_FACTOR,
                OverlayRenderStyle.RULER_LABEL_MIN_CLEARANCE
        );

        Vec3d outward = outwardHint;
        if (outward == null || outward.lengthSquared() < 1.0e-8) {
            outward = up;
        }
        if (outward.lengthSquared() < 1.0e-8) {
            return;
        }
        Vec3d placementDir = outward.normalize();
        if (placementDir.lengthSquared() < 1.0e-8) {
            return;
        }

        Vec3d liftedCenter = center.add(placementDir.multiply(labelLift));

        GlyphLayout glyphLayout = computeGlyphLayout(text, charHeight, spacing);
        double centerX = (glyphLayout.tightMin() + glyphLayout.tightMax()) * 0.5;
        Vec3d origin = liftedCenter
                .subtract(tangent.multiply(centerX))
                .subtract(up.multiply(charHeight * 0.5));

        drawLabelBackdrop(origin, tangent, up, glyphLayout.tightMin(), glyphLayout.tightMax(), charHeight);

        double cursor = 0.0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            GlyphMetrics metrics = glyphMetrics(ch);
            double advance = metrics.advance() * charHeight;
            drawGlyph(ch, origin.add(tangent.multiply(cursor)), tangent, up, charHeight);
            cursor += advance + spacing;
        }
    }

    private static GlyphLayout computeGlyphLayout(String text, double charHeight, double spacing) {
        double cursor = 0.0;
        double tightMin = Double.POSITIVE_INFINITY;
        double tightMax = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < text.length(); i++) {
            GlyphMetrics metrics = glyphMetrics(text.charAt(i));
            if (metrics.hasStroke()) {
                double glyphMin = cursor + metrics.minX() * charHeight;
                double glyphMax = cursor + metrics.maxX() * charHeight;
                tightMin = Math.min(tightMin, glyphMin);
                tightMax = Math.max(tightMax, glyphMax);
            }

            cursor += metrics.advance() * charHeight;
            if (i + 1 < text.length()) {
                cursor += spacing;
            }
        }

        if (!Double.isFinite(tightMin) || !Double.isFinite(tightMax) || tightMax <= tightMin) {
            tightMin = 0.0;
            tightMax = Math.max(cursor, charHeight);
        }

        return new GlyphLayout(cursor, tightMin, tightMax);
    }

    private static GlyphMetrics glyphMetrics(char ch) {
        return switch (ch) {
            case '1' -> new GlyphMetrics(0.65, 0.25, 0.50, true);
            case ' ' -> new GlyphMetrics(0.50, 0.0, 0.0, false);
            case ':' -> new GlyphMetrics(0.45, 0.50, 0.50, true);
            default -> new GlyphMetrics(1.0, 0.0, 1.0, true);
        };
    }

    private static void drawGlyph(char ch, Vec3d origin, Vec3d tangent, Vec3d up, double scale) {
        switch (ch) {
            case '0' -> {
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 1.0, 1.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 1.0, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 0.0, 0.0, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.0, 0.0, 1.0);
            }
            case '1' -> {
                drawGlyphSegment(origin, tangent, up, scale, 0.5, 1.0, 0.5, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 0.25, 0.75, 0.5, 1.0);
            }
            case '2' -> {
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 1.0, 1.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 1.0, 0.5);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 0.5, 0.0, 0.5);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.5, 0.0, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.0, 1.0, 0.0);
            }
            case '3' -> {
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 1.0, 1.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 1.0, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.5, 1.0, 0.5);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.0, 1.0, 0.0);
            }
            case '4' -> {
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 0.0, 0.5);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.5, 1.0, 0.5);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 1.0, 0.0);
            }
            case '5' -> {
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 0.0, 1.0);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 0.0, 0.5);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.5, 1.0, 0.5);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 0.5, 1.0, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 0.0, 0.0, 0.0);
            }
            case '6' -> {
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 0.0, 1.0);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 0.0, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.5, 1.0, 0.5);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 0.5, 1.0, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 0.0, 0.0, 0.0);
            }
            case '7' -> {
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 1.0, 1.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 0.0, 0.0);
            }
            case '8' -> {
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 1.0, 1.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 1.0, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 0.0, 0.0, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.0, 0.0, 1.0);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.5, 1.0, 0.5);
            }
            case '9' -> {
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 0.0, 1.0, 1.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 0.0, 1.0);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 0.0, 0.5);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.5, 1.0, 0.5);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 0.0, 0.0, 0.0);
            }
            case 'X' -> {
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 1.0, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 0.0, 0.0);
            }
            case 'Y' -> {
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 0.5, 0.55);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 0.5, 0.55);
                drawGlyphSegment(origin, tangent, up, scale, 0.5, 0.55, 0.5, 0.0);
            }
            case 'Z' -> {
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 1.0, 1.0, 1.0);
                drawGlyphSegment(origin, tangent, up, scale, 1.0, 1.0, 0.0, 0.0);
                drawGlyphSegment(origin, tangent, up, scale, 0.0, 0.0, 1.0, 0.0);
            }
            case ':' -> {
                drawGlyphSegment(origin, tangent, up, scale, 0.5, 0.72, 0.5, 0.78);
                drawGlyphSegment(origin, tangent, up, scale, 0.5, 0.22, 0.5, 0.28);
            }
            default -> {
                // Unsupported glyph: skip rendering.
            }
        }
    }

    private static void drawGlyphSegment(Vec3d origin, Vec3d tangent, Vec3d up, double scale, double x1, double y1, double x2, double y2) {
        Vec3d from = origin
                .add(tangent.multiply(x1 * scale))
                .add(up.multiply(y1 * scale));
        Vec3d to = origin
                .add(tangent.multiply(x2 * scale))
                .add(up.multiply(y2 * scale));

        GizmoDrawing.line(from, to, OverlayRenderStyle.color(OverlayColorSlot.RULER_TEXT), OverlayRenderStyle.lineWidth(OverlayRenderStyle.RULER_LABEL_STROKE_WIDTH, OverlayColorSlot.RULER_TEXT))
                .ignoreOcclusion();
    }

    private static void drawLabelBackdrop(
            Vec3d origin,
            Vec3d tangent,
            Vec3d up,
            double tightMinX,
            double tightMaxX,
            double charHeight
    ) {
        if (tightMaxX <= tightMinX || charHeight <= 0.0) {
            return;
        }

        double padX = Math.max(
                OverlayRenderStyle.RULER_LABEL_BG_MIN_PADDING,
                charHeight * OverlayRenderStyle.RULER_LABEL_BG_PADDING_X_FACTOR
        );
        double padY = Math.max(
                OverlayRenderStyle.RULER_LABEL_BG_MIN_PADDING,
                charHeight * OverlayRenderStyle.RULER_LABEL_BG_PADDING_Y_FACTOR
        );

        Vec3d left = origin.add(tangent.multiply(tightMinX - padX));
        Vec3d right = origin.add(tangent.multiply(tightMaxX + padX));

        double minY = -padY;
        double maxY = charHeight + padY;

        Vec3d p0 = left.add(up.multiply(minY));
        Vec3d p1 = right.add(up.multiply(minY));
        Vec3d p2 = right.add(up.multiply(maxY));
        Vec3d p3 = left.add(up.multiply(maxY));

        DrawStyle style = DrawStyle.filledAndStroked(
                OverlayRenderStyle.color(OverlayColorSlot.RULER_LABEL_BORDER),
                OverlayRenderStyle.lineWidth(OverlayRenderStyle.RULER_LABEL_BG_BORDER_WIDTH, OverlayColorSlot.RULER_LABEL_BORDER),
                OverlayRenderStyle.color(OverlayColorSlot.RULER_LABEL_BACKGROUND)
        );

        // Draw both windings to keep the background visible regardless of face culling.
        GizmoDrawing.quad(p0, p1, p2, p3, style)
                .ignoreOcclusion();
        GizmoDrawing.quad(p3, p2, p1, p0, style)
                .ignoreOcclusion();
    }

    private record GlyphMetrics(double advance, double minX, double maxX, boolean hasStroke) {
    }

    private record GlyphLayout(double advanceWidth, double tightMin, double tightMax) {
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Side {
        X_NEG(0, -1),
        X_POS(0, 1),
        Y_NEG(1, -1),
        Y_POS(1, 1),
        Z_NEG(2, -1),
        Z_POS(2, 1);

        private final int axis;
        private final int sign;

        Side(int axis, int sign) {
            this.axis = axis;
            this.sign = sign;
        }

        private int axis() {
            return axis;
        }

        private int sign() {
            return sign;
        }
    }

    private record RulerAnchor(Side side, int nearSignX, int nearSignY, int nearSignZ) {
        private int[] nearSignCopy() {
            return new int[]{nearSignX, nearSignY, nearSignZ};
        }
    }

    private record RulerLayout(
            String label,
            int axis,
            Vec3d anchorStart,
            Vec3d anchorEnd,
            Vec3d dimensionStart,
            Vec3d dimensionEnd,
            Vec3d normal,
            Vec3d outwardHint
    ) {
    }

    private record PolyPlane(Vec3d normal, double maxDot) {
        private boolean above(int x, int y, int z) {
            double dot = normal.x * x + normal.y * y + normal.z * z;
            return dot > maxDot + 1.0e-9;
        }
    }
}


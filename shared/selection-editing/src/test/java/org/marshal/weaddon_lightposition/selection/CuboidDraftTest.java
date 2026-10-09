package org.marshal.weaddon_lightposition.selection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Corner;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;

class CuboidDraftTest {
    private final CuboidDraft bounds = new CuboidDraft(-20, -10, -30, -5, 15, -1);
    @ParameterizedTest @EnumSource(Face.class)
    void expansionAndContractionKeepOppositeFaceAndOtherAxes(Face face) {
        var larger = bounds.resize(face, 8, -64, 319);
        assertEquals(8, (larger.boundary(face) - bounds.boundary(face)) * face.sign);
        assertEquals(bounds, larger.resize(face, -8, -64, 319));
        for (int a = 0; a < 3; a++) if (a != face.axis) {
            assertEquals(bounds.min(a), larger.min(a)); assertEquals(bounds.max(a), larger.max(a));
        }
        assertEquals(face.sign > 0 ? bounds.min(face.axis) : bounds.max(face.axis), face.sign > 0 ? larger.min(face.axis) : larger.max(face.axis));
        assertEquals(1, bounds.resize(face, Long.MIN_VALUE, -64, 319).size(face.axis));
    }
    @ParameterizedTest @EnumSource(Face.class)
    void intersectionOutsideAndInside(Face face) {
        Point normal = new Point(face.axis == 0 ? face.sign : 0, face.axis == 1 ? face.sign : 0, face.axis == 2 ? face.sign : 0);
        assertEquals(face, bounds.intersect(bounds.center(face).add(normal.scale(12)), normal.scale(-1)));
        Point center = new Point(-12, 0, -15);
        assertEquals(face, bounds.intersect(center, normal));
    }
    @ParameterizedTest @EnumSource(Face.class)
    void intersectionAtOrExtremelyCloseToEveryFace(Face face) {
        Point normal = new Point(face.axis == 0 ? face.sign : 0, face.axis == 1 ? face.sign : 0, face.axis == 2 ? face.sign : 0);
        Point center = bounds.center(face);
        assertEquals(face, bounds.intersect(center, normal));
        assertEquals(face, bounds.intersect(center, normal.scale(-1)));
        assertEquals(face, bounds.intersect(center.add(normal.scale(1e-9)), normal.scale(-1)));
        assertEquals(face, bounds.intersect(center.subtract(normal.scale(1e-9)), normal));
        assertNull(bounds.intersect(center.add(normal.scale(0.01)), normal));
    }
    @Test void translationKeepsDimensionsAndClampsTheWholeBox() {
        var shifted = bounds.translate(new Point(13, -4, 21), 1, -64, 319);
        assertEquals(new CuboidDraft(-7, -14, -9, 8, 11, 20), shifted);
        for (int axis = 0; axis < 3; axis++) assertEquals(bounds.size(axis), shifted.size(axis));
        assertEquals(bounds.volume(), shifted.volume());
        assertEquals(bounds, shifted.translate(new Point(-13, 4, -21), 1, -64, 319));
        var clamped = bounds.translate(new Point(Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE), 8, -64, 319);
        assertEquals(29_999_999, clamped.maxX()); assertEquals(319, clamped.maxY()); assertEquals(-30_000_000, clamped.minZ());
        for (int axis = 0; axis < 3; axis++) assertEquals(bounds.size(axis), clamped.size(axis));
        assertEquals(bounds.translate(new Point(8, -8, 0), 1, -64, 319), bounds.translate(new Point(5.2, -5.2, 0), 8, -64, 319));
    }
    @ParameterizedTest @EnumSource(Face.class)
    void dragWorldAxisNeverDependsOnCameraSide(Face face) {
        Point axis = new Point(face.axis == 0 ? 1 : 0, face.axis == 1 ? 1 : 0, face.axis == 2 ? 1 : 0);
        Point other = new Point(face.axis != 0 ? 10 : 0, face.axis == 0 ? 10 : 0, 0);
        Point origin = bounds.center(face);
        for (int side : new int[]{-1, 1}) {
            Point eye = origin.add(other.scale(side));
            Point ray = origin.add(axis.scale(-3)).subtract(eye).normalized();
            assertEquals(-3, CuboidDraft.axisParameter(eye, ray, origin, face), 1e-7);
            assertTrue(Double.isNaN(CuboidDraft.axisParameter(eye, axis, origin, face)));
        }
    }
    @Test void heightWorldLimitsOverflowAndSingleCell() {
        assertEquals(319, bounds.resize(Face.UP, Long.MAX_VALUE, -64, 319).maxY());
        assertEquals(-64, bounds.resize(Face.DOWN, Long.MAX_VALUE, -64, 319).minY());
        assertEquals(29_999_999, bounds.resize(Face.EAST, Long.MAX_VALUE, -64, 319).maxX());
        assertEquals(-30_000_000, bounds.resize(Face.WEST, Long.MAX_VALUE, -64, 319).minX());
        var huge = new CuboidDraft(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals("79228162514264337593543950336", huge.volume().toString());
        var cell = new CuboidDraft(-1, 0, -1, -1, 0, -1);
        for (Face f : Face.values()) assertEquals(cell, cell.resize(f, -100, -64, 319));
        assertThrows(IllegalArgumentException.class, () -> new CuboidDraft(2, 0, 0, 1, 0, 0));
    }
    @Test void malformedOptionsHaveBoundedSafeDefaults() {
        var values = new java.util.Properties(); values.setProperty("step", "bad"); values.setProperty("fastStep", "99999");
        assertEquals(new SelectionEditOptions(false, 1, 512), SelectionEditOptions.load(values));
        assertEquals(SelectionEditOptions.defaults(), SelectionEditOptions.load(SelectionEditOptions.defaults().properties()));
    }
    @ParameterizedTest @EnumSource(Corner.class)
    void cornerDragKeepsOppositeAndSnapsAllAxes(Corner corner) {
        Point opposite = bounds.corner(corner.opposite());
        Point movement = new Point(3.2 * corner.face(0).sign, -4.2 * corner.face(1).sign, 5.2 * corner.face(2).sign);
        CuboidDraft draft = bounds.moveCorner(corner, movement, 2, -64, 319);
        assertEquals(opposite, draft.corner(corner.opposite()));
        assertEquals(4, (draft.boundary(corner.face(0)) - bounds.boundary(corner.face(0))) * corner.face(0).sign);
        assertEquals(-4, (draft.boundary(corner.face(1)) - bounds.boundary(corner.face(1))) * corner.face(1).sign);
        assertEquals(6, (draft.boundary(corner.face(2)) - bounds.boundary(corner.face(2))) * corner.face(2).sign);
        Point towardsOpposite = new Point(-1000 * corner.face(0).sign, -1000 * corner.face(1).sign, -1000 * corner.face(2).sign);
        CuboidDraft cell = bounds.moveCorner(corner, towardsOpposite, 1, -64, 319);
        for (int axis = 0; axis < 3; axis++) assertEquals(1, cell.size(axis));
        assertEquals(opposite, cell.corner(corner.opposite()));
    }
    @Test void capturedPlaneDoesNotFlipOrMoveDepth() {
        Point eye = new Point(2, 3, 4), forward = new Point(1, 2, 3).normalized();
        Point center = eye.add(forward.scale(12));
        Point side = new Point(-2, 1, 0).normalized().scale(3);
        for (int sign : new int[]{-1, 1}) {
            Point wanted = center.add(side.scale(sign));
            assertEquals(wanted.x(), CuboidDraft.planePoint(eye, wanted.subtract(eye).normalized(), center, forward).x(), 1e-7);
            assertEquals(wanted.y(), CuboidDraft.planePoint(eye, wanted.subtract(eye).normalized(), center, forward).y(), 1e-7);
            assertEquals(wanted.z(), CuboidDraft.planePoint(eye, wanted.subtract(eye).normalized(), center, forward).z(), 1e-7);
        }
        assertNull(CuboidDraft.planePoint(eye, side.normalized(), center, forward));
        assertNull(CuboidDraft.planePoint(eye, forward.scale(-1), center, forward));
        assertNull(CuboidDraft.planePoint(eye, forward, eye.subtract(forward), forward));
    }
    @Test void hugeCornerOffsetsClampWithoutOverflow() {
        var huge = bounds.moveCorner(Corner.WEST_UP_SOUTH, new Point(-Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE), 512, -64, 319);
        assertEquals(-30_000_000, huge.minX()); assertEquals(319, huge.maxY()); assertEquals(29_999_999, huge.maxZ());
        assertThrows(IllegalArgumentException.class, () -> CuboidDraft.snappedOffset(Double.NaN, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> CuboidDraft.snappedOffset(1, 0, 1));
    }
    @ParameterizedTest @EnumSource(Corner.class)
    void liveCameraTranslationMovesCapturedCornerWithoutMovingOpposite(Corner corner) {
        Point target = bounds.corner(corner), ray = new Point(1, -2, 3).normalized();
        Point eye = target.subtract(ray.scale(20)), movement = new Point(4, 2, -3);
        Point start = CuboidDraft.rayPoint(eye, ray, 20);
        Point moved = CuboidDraft.rayPoint(eye.add(movement), ray, 20);
        CuboidDraft draft = bounds.moveCorner(corner, moved.subtract(start), 1, -64, 319);
        assertEquals(bounds.corner(corner.opposite()), draft.corner(corner.opposite()));
        for (int axis = 0; axis < 3; axis++) assertEquals(movement.at(axis),
                draft.boundary(corner.face(axis)) - bounds.boundary(corner.face(axis)), 1e-7);
    }
    @Test void liveCameraCanTurnThroughEveryAxisWithoutParallelPlaneFailure() {
        Point eye = new Point(2, 3, 4);
        for (Face face : Face.values()) {
            Point ray = new Point(face.axis == 0 ? face.sign * 2 : 0,
                    face.axis == 1 ? face.sign * 2 : 0, face.axis == 2 ? face.sign * 2 : 0);
            Point position = CuboidDraft.rayPoint(eye, ray, 12);
            assertNotNull(position);
            assertEquals(12, position.subtract(eye).length(), 1e-7);
            assertEquals(face.sign * 12, position.subtract(eye).at(face.axis), 1e-7);
        }
    }
    @Test void liveCameraCapturePreservesOffCenterGrabWithoutInitialJump() {
        Point eye = new Point(-5, 6, 7), ray = new Point(1, 0, 0);
        Point corner = new Point(15, 6.2, 7.1);
        double distance = corner.subtract(eye).dot(ray);
        Point start = CuboidDraft.rayPoint(eye, ray, distance);
        assertEquals(new Point(0, 0, 0), CuboidDraft.rayPoint(eye, ray, distance).subtract(start));
        assertEquals(new Point(0, 2, -3), CuboidDraft.rayPoint(eye.add(new Point(0, 2, -3)), ray, distance).subtract(start));
    }
    @Test void liveCameraRejectsNonfiniteOrEmptyRays() {
        Point eye = new Point(2, 3, 4), ray = new Point(0, 0, 1);
        for (double distance : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertNull(CuboidDraft.rayPoint(eye, ray, distance));
        assertNull(CuboidDraft.rayPoint(eye, new Point(0, 0, 0), 12));
        assertNull(CuboidDraft.rayPoint(eye, new Point(Double.NaN, 0, 0), 12));
        assertNull(CuboidDraft.rayPoint(new Point(Double.POSITIVE_INFINITY, 0, 0), ray, 12));
    }
}

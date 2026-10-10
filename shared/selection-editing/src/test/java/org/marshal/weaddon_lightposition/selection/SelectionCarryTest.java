package org.marshal.weaddon_lightposition.selection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;
import static org.junit.jupiter.api.Assertions.*;

class SelectionCarryTest {
    private final CuboidDraft box = new CuboidDraft(-20, 60, -30, -5, 75, -1);
    private SelectionCarry grip(CuboidDraft origin) { return new SelectionCarry(origin, new Point(0, 0, 1), -64, 319); }

    @ParameterizedTest @EnumSource(Face.class)
    void wheelInitiallyUsesOneCapturedSignedCardinalAxis(Face face) {
        Point direction = new Point(face.axis == 0 ? face.sign : 0.1, face.axis == 1 ? face.sign : 0.1,
                face.axis == 2 ? face.sign : 0.1);
        var grip = new SelectionCarry(box, direction, -64, 319);
        assertEquals(face, grip.wheelAxis());
        Point delta = new Point(face.axis == 0 ? face.sign * 3 : 0, face.axis == 1 ? face.sign * 3 : 0,
                face.axis == 2 ? face.sign * 3 : 0);
        assertEquals(box.translate(delta, 1, -64, 319), grip.scroll(1, 3));
        assertEquals(box, grip.scroll(-1, 3));
    }
    @Test void axisChangesDoNotMoveOrRotateAccumulatedFractions() {
        var grip = grip(box);
        assertEquals(box, grip.scroll(0.25, 1));
        grip.selectAxis(1);
        assertEquals(Face.UP, grip.wheelAxis());
        assertEquals(box, grip.scroll(0, 1));
        assertEquals(box.translate(new Point(0, -3, 0), 1, -64, 319), grip.scroll(-3, 1));
        grip.selectAxis(0);
        assertEquals(box.translate(new Point(2, -3, 0), 1, -64, 319), grip.scroll(2, 1));
        grip.selectAxis(2);
        assertEquals(box.translate(new Point(2, -3, 1), 1, -64, 319), grip.scroll(0.75, 1));
        assertEquals(box.translate(new Point(2, -3, 0), 1, -64, 319), grip.scroll(-1, 1));
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void explicitAxesAlwaysUseWorldPositiveDirectionsAndPreserveDimensions(int axis) {
        var grip = new SelectionCarry(box, new Point(-1, -1, -1), -64, 319);
        grip.selectAxis(axis);
        assertEquals(1, grip.wheelAxis().sign);
        var moved = grip.scroll(1, 4);
        for (int a = 0; a < 3; a++) {
            assertEquals(box.size(a), moved.size(a));
            assertEquals(box.min(a) + (a == axis ? 4 : 0), moved.min(a));
        }
        assertEquals(box, grip.scroll(-1, 4));
    }
    @Test void aNewGripStartsAtTheRetainedDraftWithoutAJump() {
        var moved = grip(box).scroll(12, 1);
        var next = new SelectionCarry(moved, new Point(-1, 0, 0), -64, 319);
        assertEquals(moved, next.scroll(0, 1));
        assertEquals(moved.translate(new Point(-1, 0, 0), 1, -64, 319), next.scroll(1, 1));
    }
    @ParameterizedTest @EnumSource(Face.class)
    void coordinateAndHeightLimitsDoNotStoreDeadReverseTravel(Face face) {
        var grip = grip(box);
        grip.selectAxis(face.axis);
        var limit = grip.scroll(face.sign * 1e9, 1);
        assertEquals(face.axis == 1 ? face.sign > 0 ? 319 : -64 : face.sign > 0 ? 29_999_999 : -30_000_000, limit.boundary(face));
        Point reverse = new Point(face.axis == 0 ? -face.sign : 0, face.axis == 1 ? -face.sign : 0, face.axis == 2 ? -face.sign : 0);
        assertEquals(limit.translate(reverse, 1, -64, 319), grip.scroll(-face.sign, 1));
        for (int axis = 0; axis < 3; axis++) assertEquals(box.size(axis), limit.size(axis));
    }
    @Test void saturatingOneAxisDoesNotBlockAnother() {
        var grip = grip(box);
        grip.selectAxis(0);
        var limit = grip.scroll(Double.MAX_VALUE, 512);
        assertEquals(29_999_999, limit.maxX());
        grip.selectAxis(1);
        assertEquals(limit.translate(new Point(0, 2, 0), 1, -64, 319), grip.scroll(2, 1));
        grip.selectAxis(0);
        assertEquals(limit.translate(new Point(-1, 2, 0), 1, -64, 319), grip.scroll(-1, 1));
    }
    @Test void invalidEventsCannotPoisonTheNextValidSample() {
        var grip = grip(box);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) assertEquals(box, grip.scroll(invalid, 1));
        assertThrows(IllegalArgumentException.class, () -> grip.selectAxis(-1));
        assertThrows(IllegalArgumentException.class, () -> grip.selectAxis(3));
        assertEquals(Face.SOUTH, grip.wheelAxis());
        assertEquals(box.translate(new Point(0, 0, 3), 1, -64, 319), grip.scroll(3, 1));
        assertThrows(IllegalArgumentException.class, () -> grip.scroll(1, 0));
    }
    @Test void hugeSelectionsAreOnlyCoordinateTransforms() {
        var huge = new CuboidDraft(-20_000_000, -64, -20_000_000, 20_000_000, 319, 20_000_000);
        var grip = grip(huge); grip.selectAxis(0);
        var moved = grip.scroll(20, 1);
        assertEquals(huge.translate(new Point(20, 0, 0), 1, -64, 319), moved);
        assertEquals(huge.volume(), moved.volume());
    }
    @Test void invalidOriginIsRejectedBeforeAnInteractiveGrip() {
        assertThrows(IllegalArgumentException.class, () -> grip(new CuboidDraft(0, -65, 0, 1, 10, 1)));
        assertThrows(IllegalArgumentException.class, () -> new SelectionCarry(box, new Point(0, 0, 0), -64, 319));
        assertThrows(IllegalArgumentException.class, () -> new SelectionCarry(box, new Point(Double.NaN, 0, 0), -64, 319));
    }
}

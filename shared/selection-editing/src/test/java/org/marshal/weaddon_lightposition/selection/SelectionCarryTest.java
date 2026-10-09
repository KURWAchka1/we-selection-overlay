package org.marshal.weaddon_lightposition.selection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;
import static org.junit.jupiter.api.Assertions.*;

class SelectionCarryTest {
    private final CuboidDraft box = new CuboidDraft(-20, 60, -30, -5, 75, -1);
    private final Point root = new Point(-10.2, 80, 3.9), forward = new Point(0, 0, 1);
    private SelectionCarry carry(CuboidDraft origin) { return new SelectionCarry(origin, root, forward, -64, 319); }

    @Test void pickupAndRepeatedStationarySamplesNeverChangeTheBox() {
        var grip = carry(box);
        for (int i = 0; i < 1000; i++) assertEquals(box, grip.follow(root));
    }
    @Test void actualRootTranslationPreservesDimensionsAndIsReversible() {
        var grip = carry(box);
        Point offset = new Point(10000, 3, -20000);
        var moved = grip.follow(root.add(offset));
        assertEquals(box.translate(offset, 1, -64, 319), moved);
        for (int axis = 0; axis < 3; axis++) assertEquals(box.size(axis), moved.size(axis));
        assertEquals(box, grip.follow(root));
    }
    @Test void subBlockMotionDoesNotRoundAwayAtEveryFrame() {
        var grip = carry(box);
        for (int i = 1; i <= 1000; i++) grip.follow(root.add(new Point(i / 1000.0, 0, 0)));
        assertEquals(box.translate(new Point(1, 0, 0), 1, -64, 319), grip.follow(root.add(new Point(1, 0, 0))));
    }
    @ParameterizedTest @EnumSource(Face.class)
    void wheelUsesOneCapturedSignedCardinalAxis(Face face) {
        Point direction = new Point(face.axis == 0 ? face.sign : 0.1, face.axis == 1 ? face.sign : 0.1,
                face.axis == 2 ? face.sign : 0.1);
        var grip = new SelectionCarry(box, root, direction, -64, 319);
        assertEquals(face, grip.wheelAxis());
        Point delta = new Point(face.axis == 0 ? face.sign * 3 : 0, face.axis == 1 ? face.sign * 3 : 0,
                face.axis == 2 ? face.sign * 3 : 0);
        assertEquals(box.translate(delta, 1, -64, 319), grip.scroll(1, 3));
        assertEquals(box, grip.scroll(-1, 3));
    }
    @Test void fractionalWheelAndPlayerMotionComposeWithoutRotatingOrRoundingLoss() {
        var grip = carry(box);
        assertEquals(box, grip.scroll(0.2, 1));
        grip.follow(root.add(new Point(4, 2, 0)));
        grip.scroll(0.2, 1); grip.scroll(0.2, 1); grip.scroll(0.2, 1);
        assertEquals(box.translate(new Point(4, 2, 1), 1, -64, 319), grip.scroll(0.2, 1));
        assertEquals(box.translate(new Point(4, 2, 0), 1, -64, 319), grip.scroll(-1, 1));
    }
    @Test void aNewGripStartsAtTheRetainedDraftWithoutAJump() {
        var first = carry(box);
        Point nextRoot = root.add(new Point(12, 3, -9));
        var moved = first.follow(nextRoot);
        var second = new SelectionCarry(moved, nextRoot, new Point(-1, 0, 0), -64, 319);
        assertEquals(moved, second.follow(nextRoot));
        assertEquals(moved.translate(new Point(-1, 0, 0), 1, -64, 319), second.scroll(1, 1));
    }
    @ParameterizedTest @EnumSource(Face.class)
    void coordinateAndHeightLimitsDoNotStoreDeadReverseTravel(Face face) {
        var grip = carry(box);
        Point overshoot = new Point(face.axis == 0 ? face.sign * 1e9 : 0,
                face.axis == 1 ? face.sign * 1e9 : 0, face.axis == 2 ? face.sign * 1e9 : 0);
        var limit = grip.follow(root.add(overshoot));
        assertEquals(face.axis == 1 ? face.sign > 0 ? 319 : -64 : face.sign > 0 ? 29_999_999 : -30_000_000, limit.boundary(face));
        Point reverse = new Point(face.axis == 0 ? -face.sign : 0, face.axis == 1 ? -face.sign : 0, face.axis == 2 ? -face.sign : 0);
        var reversed = grip.follow(root.add(overshoot).add(reverse));
        assertEquals(limit.translate(reverse, 1, -64, 319), reversed);
        for (int axis = 0; axis < 3; axis++) assertEquals(box.size(axis), reversed.size(axis));
    }
    @Test void saturationOnOneAxisDoesNotDiscardValidMovementOnAnother() {
        var grip = carry(box);
        var moved = grip.follow(root.add(new Point(1e9, 2, 3)));
        assertEquals(29_999_999, moved.maxX());
        assertEquals(box.minY() + 2, moved.minY()); assertEquals(box.minZ() + 3, moved.minZ());
        var reversed = grip.follow(root.add(new Point(1e9 - 1, 3, 4)));
        assertEquals(moved.minX() - 1, reversed.minX()); assertEquals(moved.minY() + 1, reversed.minY());
    }
    @Test void wheelSaturationReversesImmediatelyAndCannotOverflow() {
        var grip = carry(box);
        var limit = grip.scroll(Double.MAX_VALUE, 512);
        assertEquals(29_999_999, limit.maxZ());
        assertEquals(limit.translate(new Point(0, 0, -1), 1, -64, 319), grip.scroll(-1, 1));
    }
    @Test void nonfiniteEventsCannotPoisonTheNextValidSample() {
        var grip = carry(box);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(box, grip.follow(new Point(invalid, 0, 0)));
            assertEquals(box, grip.scroll(invalid, 1));
        }
        assertEquals(box.translate(new Point(3, 0, 0), 1, -64, 319), grip.follow(root.add(new Point(3, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> grip.scroll(1, 0));
    }
    @Test void hugeSelectionsAreOnlyCoordinateTransforms() {
        var huge = new CuboidDraft(-20_000_000, -64, -20_000_000, 20_000_000, 319, 20_000_000);
        var moved = carry(huge).follow(root.add(new Point(20, 10, -30)));
        assertEquals(huge.translate(new Point(20, 0, -30), 1, -64, 319), moved);
        assertEquals(huge.volume(), moved.volume());
    }
    @Test void invalidOriginIsRejectedBeforeAnInteractiveGrip() {
        assertThrows(IllegalArgumentException.class, () -> carry(new CuboidDraft(0, -65, 0, 1, 10, 1)));
        assertThrows(IllegalArgumentException.class, () -> new SelectionCarry(box, root, new Point(0, 0, 0), -64, 319));
        assertThrows(IllegalArgumentException.class, () -> new SelectionCarry(box, new Point(Double.NaN, 0, 0), forward, -64, 319));
    }
}

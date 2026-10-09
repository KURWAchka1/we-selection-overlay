package org.marshal.weaddon_lightposition.selection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;
import static org.junit.jupiter.api.Assertions.*;
import static org.marshal.weaddon_lightposition.selection.SelectionEditKey.Action.*;

class SelectionGestureTest {
    @Test void tapsNeverOpenMenuAndHoldsNeverToggle() {
        var key = new SelectionEditKey();
        assertEquals(NONE, key.update(true, 1));
        assertEquals(NONE, key.update(true, 350_000_001));
        assertEquals(TOGGLE, key.update(false, 350_000_002));
        assertEquals(NONE, key.update(false, 360_000_000));
        assertEquals(NONE, key.update(true, 500_000_000));
        assertEquals(OPEN_MENU, key.update(true, 900_000_000));
        assertEquals(NONE, key.update(true, 1_000_000_000));
        assertEquals(CLOSE_MENU, key.update(false, 1_100_000_000));
        key.update(true, 2_000_000_000);
        assertEquals(NONE, key.update(false, 3_000_000_000L), "Missed frames must not accidentally apply");
    }
    @Test void escapeRequiresReleaseAndDoesNotReopenWhileHeld() {
        var key = new SelectionEditKey(); key.update(true, 10); key.cancel(true);
        assertEquals(NONE, key.update(true, 1_000_000_000));
        assertEquals(NONE, key.update(false, 2_000_000_000));
        key.update(true, 3_000_000_000L);
        assertEquals(TOGGLE, key.update(false, 3_100_000_000L));
    }
    @Test void radialCenterSeamsOutsidePointerAndMalformedInput() {
        for (int radius : new int[]{50, 80, 92}) {
            assertNull(SelectionEditMode.pick(0, 0, radius));
            assertNull(SelectionEditMode.pick(radius * 0.1, 0, radius));
            assertNull(SelectionEditMode.pick(0, radius, radius));
            assertEquals(SelectionEditMode.RESIZE, SelectionEditMode.pick(-radius * 0.6, 0, radius));
            assertEquals(SelectionEditMode.MOVE, SelectionEditMode.pick(radius * 4, radius * 4, radius));
        }
        assertNull(SelectionEditMode.pick(Double.NaN, 2, 80));
        assertNull(SelectionEditMode.pick(4, 2, 0));
        assertEquals(SelectionEditMode.RESIZE, SelectionEditMode.load("invalid"));
        assertEquals(SelectionEditMode.MOVE, SelectionEditMode.load("move"));
    }
    @ParameterizedTest @EnumSource(Face.class)
    void headOnDraggingRemainsFiniteFromInsideAndOutside(Face face) {
        Point axis = new Point(face.axis == 0 ? face.sign : 0, face.axis == 1 ? face.sign : 0, face.axis == 2 ? face.sign : 0);
        Point up = new Point(face.axis == 1 ? 1 : 0, face.axis != 1 ? 1 : 0, 0);
        Point right = face.axis == 2 ? new Point(1, 0, 0) : new Point(0, 0, 1);
        Point anchor = new Point(-20, 70, -30);
        for (int side : new int[]{-1, 1}) for (double distance : new double[]{0, 0.01, 0.5, 9, 100}) {
            Point eye = anchor.add(axis.scale(distance * side));
            var drag = FaceDrag.capture(eye, axis.scale(-side), right, up, anchor, face, 0.7, 720, 300, 200);
            assertEquals(0, drag.outward(300, 200));
            assertTrue(drag.outward(300, 104) >= 2 - 1e-9);
            assertEquals(-drag.outward(300, 104), drag.outward(300, 296), 1e-9);
            assertFalse(drag.moved(302, 200)); assertTrue(drag.moved(305, 200));
            assertTrue(Double.isFinite(drag.outward(1000, -1000)));
        }
    }
    @Test void obliqueProjectionKeepsItsAxisAndIgnoresPerpendicularMotion() {
        Point forward = new Point(1, 0, 1).normalized(), right = new Point(1, 0, -1).normalized(), up = new Point(0, 1, 0);
        Point eye = new Point(-12, 70, -12), anchor = eye.add(forward.scale(9));
        var drag = FaceDrag.capture(eye, forward, right, up, anchor, Face.EAST, 0.7, 720, 300, 200);
        assertTrue(drag.outward(380, 200) > 0);
        assertEquals(0, drag.outward(300, 280), 1e-9);
        assertEquals(-drag.outward(380, 200), drag.outward(220, 200), 1e-9);
        var huge = FaceDrag.capture(eye.add(new Point(29_000_000, 0, 29_000_000)), forward, right, up,
                anchor.add(new Point(29_000_000, 0, 29_000_000)), Face.EAST, 0.7, 720, 300, 200);
        assertEquals(drag.outward(380, 200), huge.outward(380, 200), 1e-7);
    }
}

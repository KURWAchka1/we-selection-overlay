package org.marshal.weaddon_lightposition.selection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
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
}

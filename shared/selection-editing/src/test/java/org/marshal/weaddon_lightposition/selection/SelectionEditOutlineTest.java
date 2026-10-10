package org.marshal.weaddon_lightposition.selection;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Corner;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;
import org.marshal.weaddon_lightposition.selection.SelectionEditOutline.Edge;
import static org.junit.jupiter.api.Assertions.*;

class SelectionEditOutlineTest {
    private final CuboidDraft box = new CuboidDraft(-8, 66, -8, -3, 71, -3);

    @ParameterizedTest @EnumSource(Corner.class)
    void eachCornerOwnsExactlyThreeEdgesWithoutBoundaryUnderlay(Corner corner) {
        var active = SelectionEditOutline.active(box, corner, Face.UP);
        var boundary = SelectionEditOutline.boundary(box, active);
        assertEquals(3, active.size());
        assertEquals(9, boundary.size());
        assertEquals(3 * (box.size(0) + box.size(1) + box.size(2)), length(boundary));
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(axis, active.get(axis).axis());
            assertEquals(box.corner(corner), active.get(axis).from());
            assertEquals(box.size(axis), length(List.of(active.get(axis))));
        }
        disjoint(boundary, active);
    }

    @ParameterizedTest @EnumSource(Corner.class)
    void originalGhostCannotShowThroughAnUnchangedAxis(Corner corner) {
        for (int resizedAxis = 0; resizedAxis < 3; resizedAxis++) for (long delta : new long[]{-2, 3}) {
            CuboidDraft draft = box.resize(corner.face(resizedAxis), delta, -64, 319);
            var active = SelectionEditOutline.active(draft, corner, null);
            var ghost = SelectionEditOutline.boundary(box, active);
            disjoint(ghost, active);
            double clipped = delta < 0 ? box.size(resizedAxis) + delta : box.size(resizedAxis);
            assertEquals(4 * (box.size(0) + box.size(1) + box.size(2)) - clipped, length(ghost));
            disjoint(SelectionEditOutline.boundary(draft, active), active);
        }
    }

    @ParameterizedTest @EnumSource(Face.class)
    void selectedFaceOwnsItsFourEdges(Face face) {
        var active = SelectionEditOutline.active(box, null, face);
        var boundary = SelectionEditOutline.boundary(box, active);
        assertEquals(4, active.size());
        assertEquals(8, boundary.size());
        disjoint(boundary, active);
        for (Edge edge : active) {
            assertNotEquals(face.axis, edge.axis());
            assertEquals(box.center(face).at(face.axis), edge.from().at(face.axis));
            assertEquals(edge.from().at(face.axis), edge.to().at(face.axis));
        }
    }

    @Test
    void inactiveAndDisplacedContoursKeepAllTwelveEdges() {
        assertTrue(SelectionEditOutline.active(box, null, null).isEmpty());
        assertEquals(12, SelectionEditOutline.boundary(box, List.of()).size());
        CuboidDraft shifted = box.translate(new Point(20, 20, 20), 1, -64, 319);
        assertEquals(12, SelectionEditOutline.boundary(box, SelectionEditOutline.active(shifted, Corner.EAST_UP_SOUTH, null)).size());
    }

    @Test
    void singleCellAndWorldBorderCoordinatesDoNotCollapseOrRoundEdges() {
        for (CuboidDraft bounds : List.of(new CuboidDraft(0, 0, 0, 0, 0, 0),
                new CuboidDraft(-30_000_000, -64, 29_999_998, -29_999_999, 319, 29_999_999))) {
            for (Corner corner : Corner.values()) {
                var active = SelectionEditOutline.active(bounds, corner, null);
                var boundary = SelectionEditOutline.boundary(bounds, active);
                assertEquals(9, boundary.size());
                disjoint(boundary, active);
                assertEquals(4 * (bounds.size(0) + bounds.size(1) + bounds.size(2)), length(boundary) + length(active));
            }
        }
    }

    private static double length(List<Edge> edges) {
        return edges.stream().mapToDouble(edge -> Math.abs(edge.to().at(edge.axis()) - edge.from().at(edge.axis()))).sum();
    }

    private static void disjoint(List<Edge> boundary, List<Edge> active) {
        for (Edge edge : boundary) {
            assertTrue(edge.to().at(edge.axis()) > edge.from().at(edge.axis()), "Collapsed or reversed boundary stroke");
            for (Edge axis : active) {
                if (edge.axis() != axis.axis()) continue;
                boolean collinear = true;
                for (int coordinate = 0; coordinate < 3; coordinate++) if (coordinate != edge.axis()
                        && edge.from().at(coordinate) != axis.from().at(coordinate)) collinear = false;
                if (!collinear) continue;
                double low = Math.max(edge.from().at(edge.axis()), Math.min(axis.from().at(axis.axis()), axis.to().at(axis.axis())));
                double high = Math.min(edge.to().at(edge.axis()), Math.max(axis.from().at(axis.axis()), axis.to().at(axis.axis())));
                assertTrue(low >= high, "Boundary/axis overlap: " + edge + " and " + axis);
            }
        }
    }
}

package org.marshal.weaddon_lightposition.selection;

import org.marshal.weaddon_lightposition.selection.CuboidDraft.Face;
import org.marshal.weaddon_lightposition.selection.CuboidDraft.Point;

/** Fixed screen-space constraint: no ray/axis singularity or sign switch during a gesture. */
public record FaceDrag(double startX, double startY, double xAxis, double yAxis, double unitsPerPixel) {
    public static FaceDrag capture(Point eye, Point forward, Point right, Point up, Point anchor, Face face,
                                   double tangent, int height, double mouseX, double mouseY) {
        Point offset = anchor.subtract(eye);
        double depth = Math.max(1, offset.dot(forward));
        double x = right.at(face.axis) - offset.dot(right) / depth * forward.at(face.axis);
        double y = -up.at(face.axis) + offset.dot(up) / depth * forward.at(face.axis);
        double length = Math.hypot(x, y);
        double scale = 2 * tangent * depth / Math.max(1, height);
        // A view-aligned axis has no usable screen direction. Up always expands, down contracts.
        if (length < 0.15) return new FaceDrag(mouseX, mouseY, 0, -1, Math.max(1.0 / 48, scale));
        return new FaceDrag(mouseX, mouseY, x / length * face.sign, y / length * face.sign,
                Math.max(1.0 / 48, scale / length));
    }

    public double outward(double x, double y) {
        return ((x - startX) * xAxis + (y - startY) * yAxis) * unitsPerPixel;
    }

    public boolean moved(double x, double y) { return Math.hypot(x - startX, y - startY) >= 4; }
}

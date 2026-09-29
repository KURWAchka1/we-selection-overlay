package org.marshal.weaddon_lightposition.client.overlay;

public final class OverlayRenderStyle {
    private static final OverlayColorProfile COLORS = OverlayColorProfile.defaults();
    private static long colorVersion = 1L;
    private static float lineWidthScale = 1.0f;

    private OverlayRenderStyle() {
    }

    public static final int POS1_COLOR = OverlayColorSlot.POS1.defaultColor();
    public static final int POS2_COLOR = OverlayColorSlot.POS2.defaultColor();
    public static final int BOX_COLOR = OverlayColorSlot.CUBOID_EDGE.defaultColor();
    public static final int GRID_COLOR = OverlayColorSlot.CUBOID_GRID.defaultColor();
    public static final int CONNECTOR_COLOR = OverlayColorSlot.CUBOID_CONNECTOR.defaultColor();
    public static final int ELLIPSOID_COLOR = OverlayColorSlot.ELLIPSOID_EDGE.defaultColor();
    public static final int ELLIPSOID_CENTER_COLOR = OverlayColorSlot.ELLIPSOID_CENTER.defaultColor();
    public static final int CYLINDER_EDGE_COLOR = OverlayColorSlot.CYLINDER_EDGE.defaultColor();
    public static final int CYLINDER_GRID_COLOR = OverlayColorSlot.CYLINDER_GRID.defaultColor();
    public static final int CYLINDER_CENTER_COLOR = OverlayColorSlot.CYLINDER_CENTER.defaultColor();
    public static final int POLYGON_EDGE_COLOR = OverlayColorSlot.POLYGON_EDGE.defaultColor();
    public static final int POLYGON_GRID_COLOR = OverlayColorSlot.POLYGON_GRID.defaultColor();
    public static final int POLYGON_POINT_COLOR = OverlayColorSlot.POLYGON_POINT.defaultColor();
    public static final int POLYHEDRON_EDGE_COLOR = OverlayColorSlot.POLYHEDRON_EDGE.defaultColor();
    public static final int POLYHEDRON_POINT_COLOR = OverlayColorSlot.POLYHEDRON_POINT.defaultColor();
    public static final int RULER_LINE_COLOR = OverlayColorSlot.RULER_LINE.defaultColor();
    public static final int RULER_ARROW_COLOR = OverlayColorSlot.RULER_ARROW.defaultColor();
    public static final int RULER_TEXT_COLOR = OverlayColorSlot.RULER_TEXT.defaultColor();
    public static final int RULER_LABEL_BG_COLOR = OverlayColorSlot.RULER_LABEL_BACKGROUND.defaultColor();
    public static final int RULER_LABEL_BG_BORDER_COLOR = OverlayColorSlot.RULER_LABEL_BORDER.defaultColor();

    public static final float POINT_WIDTH = 2.0f;
    public static final float BOX_WIDTH = 2.0f;
    public static final float GRID_WIDTH = 1.0f;
    public static final float CONNECTOR_WIDTH = 2.0f;
    public static final float RULER_LINE_WIDTH = 2.0f;
    public static final float RULER_ARROW_WIDTH = 2.0f;
    public static final float RULER_EXTENSION_WIDTH = 1.0f;
    public static final float RULER_LABEL_STROKE_WIDTH = 1.35f;
    public static final float RULER_LABEL_BG_BORDER_WIDTH = 0.9f;

    public static final int GIZMO_LIFESPAN_MS = 120;
    public static final int MAX_GRID_LINE_SEGMENTS = 2048;
    public static final int MAX_GRID_SPACING = 1024;
    public static final int ELLIPSE_SEGMENTS = 40;
    public static final int CYLINDER_MAX_SEGMENTS = 72;
    public static final double POINT_PADDING = 0.03;
    public static final double POLYGON_Y_OFFSET = 0.03;
    public static final double RULER_SURFACE_OFFSET = 0.32;
    public static final double RULER_TEXT_OFFSET = 0.12;
    public static final double RULER_EDGE_OFFSET = 0.82;
    public static final double RULER_CORNER_OFFSET = 1.12;
    public static final double RULER_ARROW_MIN_LENGTH = 0.45;
    public static final double RULER_ARROW_MAX_LENGTH = 2.2;
    public static final double RULER_LABEL_HEIGHT_FACTOR = 0.12;
    public static final double RULER_LABEL_MIN_HEIGHT = 0.55;
    public static final double RULER_LABEL_MAX_HEIGHT = 1.8;
    public static final double RULER_LABEL_CHAR_SPACING = 0.18;
    public static final double RULER_LABEL_LIFT_FACTOR = 1.35;
    public static final double RULER_LABEL_MIN_CLEARANCE = 0.68;
    public static final double RULER_LABEL_BG_PADDING_X_FACTOR = 0.18;
    public static final double RULER_LABEL_BG_PADDING_Y_FACTOR = 0.14;
    public static final double RULER_LABEL_BG_MIN_PADDING = 0.10;

    public static int color(OverlayColorSlot slot) {
        return COLORS.color(slot);
    }

    public static float lineWidth(float baseWidth) {
        return Math.max(0.35f, Math.min(6.0f, baseWidth * lineWidthScale));
    }

    public static float lineWidth(float baseWidth, OverlayColorSlot slot) {
        return Math.max(0.35f, Math.min(6.0f, baseWidth * lineWidthScale * COLORS.lineWidthScale(slot)));
    }

    public static float lineWidthScale(OverlayColorSlot slot) {
        return COLORS.lineWidthScale(slot);
    }

    public static float lineWidthScale() {
        return lineWidthScale;
    }

    public static void setLineWidthScale(float scale) {
        float normalized = Math.max(0.5f, Math.min(2.0f, scale));
        if (Math.abs(lineWidthScale - normalized) < 0.001f) {
            return;
        }
        lineWidthScale = normalized;
    }

    public static long colorVersion() {
        return colorVersion;
    }

    public static void setColors(OverlayColorProfile profile) {
        COLORS.copyFrom(profile);
        colorVersion++;
    }

    public static void setColor(OverlayColorSlot slot, int color) {
        if (COLORS.setColor(slot, color)) {
            colorVersion++;
        }
    }

    public static void setLineWidthScale(OverlayColorSlot slot, float scale) {
        COLORS.setLineWidthScale(slot, scale);
    }
}

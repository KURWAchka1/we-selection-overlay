package org.marshal.weaddon_lightposition.client.overlay;

public enum OverlayColorSlot {
    POS1("pos1", 0xFFFF4040),
    POS2("pos2", 0xFF4060FF),
    CUBOID_EDGE("cuboidEdge", 0xFF59D8FF),
    CUBOID_GRID("cuboidGrid", 0xB05AC4FF),
    CUBOID_CONNECTOR("cuboidConnector", 0xFFE8F1FF),
    POLYGON_EDGE("polygonEdge", 0xFF59D8A6),
    POLYGON_GRID("polygonGrid", 0xAA59D8A6),
    POLYGON_POINT("polygonPoint", 0xFF7EEFC6),
    ELLIPSOID_EDGE("ellipsoidEdge", 0xFFEECC55),
    ELLIPSOID_CENTER("ellipsoidCenter", 0xFFFFE37A),
    CYLINDER_EDGE("cylinderEdge", 0xFFC57AFF),
    CYLINDER_GRID("cylinderGrid", 0xAAAE72E6),
    CYLINDER_CENTER("cylinderCenter", 0xFFF0BEFF),
    POLYHEDRON_EDGE("polyhedronEdge", 0xFF67D6FF),
    POLYHEDRON_POINT("polyhedronPoint", 0xFF8EE7FF),
    RULER_LINE("rulerLine", 0xFFF5D56A),
    RULER_ARROW("rulerArrow", 0xFFFFEAA8),
    RULER_TEXT("rulerText", 0xFFFFFFFF),
    RULER_LABEL_BACKGROUND("rulerLabelBackground", 0xD0000000),
    RULER_LABEL_BORDER("rulerLabelBorder", 0x90383838);

    private final String configKey;
    private final int defaultColor;

    OverlayColorSlot(String configKey, int defaultColor) {
        this.configKey = configKey;
        this.defaultColor = defaultColor;
    }

    public String configKey() {
        return configKey;
    }

    public int defaultColor() {
        return defaultColor;
    }

    public String translationKey() {
        return "screen.worldedit_selection_overlay.colors.slot." + configKey;
    }

    public boolean supportsLineWidth() {
        return this != RULER_LABEL_BACKGROUND;
    }

    public static OverlayColorSlot fromConfigKey(String key) {
        if (key == null) {
            return null;
        }
        for (OverlayColorSlot slot : values()) {
            if (slot.configKey.equals(key)) {
                return slot;
            }
        }
        return null;
    }
}

package org.marshal.weaddon_lightposition.selection;

public enum SelectionEditMode {
    RESIZE("resize", "scaling"), MOVE("move", "move");
    public final String key, icon;
    SelectionEditMode(String key, String icon) { this.key = key; this.icon = icon; }

    public static SelectionEditMode load(String value) {
        for (var mode : values()) if (mode.key.equals(value)) return mode;
        return RESIZE;
    }

    /** Two halves, a center dead zone and a small seam; pointing beyond the ring still selects. */
    public static SelectionEditMode pick(double x, double y, double radius) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || radius <= 0
                || Math.hypot(x, y) < radius * 0.36 || Math.abs(x) < radius * 0.06) return null;
        return x < 0 ? RESIZE : MOVE;
    }
}

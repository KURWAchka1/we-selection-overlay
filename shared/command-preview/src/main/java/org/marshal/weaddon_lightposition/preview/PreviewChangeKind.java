package org.marshal.weaddon_lightposition.preview;

public enum PreviewChangeKind {
    ADDED(0x78DCAF), REPLACED(0xFFD275), REMOVED(0xFF7B88);
    private final int color;
    PreviewChangeKind(int color) { this.color = color; }
    public int color() { return color; }
}

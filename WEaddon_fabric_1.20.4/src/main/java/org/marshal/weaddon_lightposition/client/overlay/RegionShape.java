package org.marshal.weaddon_lightposition.client.overlay;

public enum RegionShape {
    CUBOID("cuboid"),
    POLYGON_2D("polygon2d"),
    ELLIPSOID("ellipsoid"),
    CYLINDER("cylinder"),
    POLYHEDRON("polyhedron");

    private final String key;

    RegionShape(String key) {
        this.key = key;
    }

    public static RegionShape fromProtocolKey(String key) {
        for (RegionShape shape : values()) {
            if (shape.key.equals(key)) {
                return shape;
            }
        }
        return null;
    }
}

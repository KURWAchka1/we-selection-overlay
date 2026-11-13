package org.marshal.weaddon_lightposition.client.overlay;

public record VoxelBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }
}

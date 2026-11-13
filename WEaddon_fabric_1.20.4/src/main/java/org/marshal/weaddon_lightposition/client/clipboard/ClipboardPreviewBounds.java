package org.marshal.weaddon_lightposition.client.clipboard;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public record ClipboardPreviewBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Box toWorldBox(BlockPos placement) {
        return new Box(
                placement.getX() + minX,
                placement.getY() + minY,
                placement.getZ() + minZ,
                placement.getX() + maxX + 1.0,
                placement.getY() + maxY + 1.0,
                placement.getZ() + maxZ + 1.0
        );
    }
}
